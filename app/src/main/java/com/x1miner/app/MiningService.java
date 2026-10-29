package com.x1miner.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native ARM64 XMRig process manager. This engine currently supports Monero/RandomX only. */
public final class MiningService extends Service {
    public static final String ACTION_START = "com.x1miner.app.MINER_START";
    public static final String ACTION_STOP = "com.x1miner.app.MINER_STOP";
    public static final String ACTION_STATS = "com.x1miner.app.MINER_STATS";
    public static final String EXTRA_WALLET = "wallet";
    public static final String EXTRA_POOL = "pool";
    public static final String EXTRA_COIN_INDEX = "coin_index";

    private static final String PREFS = "x1_miner_preferences";
    private static final String CHANNEL_ID = "x1_mining_engine";
    private static final int NOTIFICATION_ID = 8130;
    private static final Pattern SHARE_PATTERN = Pattern.compile("accepted\\s+\\((\\d+)/(\\d+)\\)");
    private static final Pattern SPEED_PATTERN = Pattern.compile(
            "speed\\s+\\S+\\s+([0-9]+(?:\\.[0-9]+)?)\\s+([0-9]+(?:\\.[0-9]+)?)\\s+(?:[0-9]+(?:\\.[0-9]+)?|n/a)\\s+([kMGT]?H/s)");
    private static final Pattern CPUMINER_THREAD_RATE_PATTERN = Pattern.compile(
            "(?i)Thread\\s+(\\d+),\\s*CPU\\s+\\d+:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([kMGT]?)h/s");
    private static final Pattern CPUMINER_HASH_RATE_PATTERN = Pattern.compile(
            "(?i)Hash rate\\s+.*?\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([kMGT]?)h/s\\s*\\)");
    private static final Pattern CPUMINER_ACCEPT_PATTERN = Pattern.compile("(?i)\\bAccepted\\s+(\\d+)\\b|\\bA(\\d+)\\b");
    private static final Pattern CPUMINER_REJECT_PATTERN = Pattern.compile("(?i)\\bRejected\\s+(\\d+)\\b|\\bR(\\d+)\\b");

    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;
    private final Handler safetyHandler = new Handler(Looper.getMainLooper());
    private final Runnable safetyMonitor = new Runnable() {
        @Override public void run() {
            if (!running || stopping) return;
            BatteryInfo battery = readBatteryInfo();
            if (prefs.getBoolean("protect_battery", true) && (battery.level < 0 || battery.level < 20)) {
                stopEngine(true, "Proteção automática: bateria abaixo de 20% ou leitura indisponível.");
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return;
            }
            if (prefs.getBoolean("protect_temp", true) && (battery.temperatureC < 0 || battery.temperatureC >= 44.0)) {
                stopEngine(true, "Proteção automática: temperatura da bateria em 44 °C ou leitura indisponível.");
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return;
            }
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                    if (power != null && power.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE) {
                        stopEngine(true, "Proteção automática: Android reportou estado térmico severo.");
                        stopForeground(STOP_FOREGROUND_REMOVE);
                        stopSelf();
                        return;
                    }
                } catch (Exception ignored) { }
            }
            safetyHandler.postDelayed(this, 15_000L);
        }
    };
    private volatile Process process;
    private volatile boolean running;
    private volatile boolean stopping;
    private volatile int activeCoinIndex = 0;
    private volatile int activeThreadCount = 1;
    private volatile String activeCoinName = "Monero";
    private final Map<Integer, Double> perThreadHashrates = new HashMap<>();
    private long sessionStartedAt;
    private long lastNotificationUpdate;
    private int acceptedShares;
    private int rejectedShares;
    private double hashrateValue;
    private String hashrate = "0 H/s";

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "X1 Miner", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Status do motor de mineração iniciado pelo usuário");
            manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopEngine(true, "Sessão parada pelo usuário");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        startAsForeground("Preparando motor nativo…");
        stopEngine(false, "Reiniciando sessão");
        prefs.edit().putString("miner_engine_logs", "").apply();
        appendEngineLog("Solicitação de início recebida.");
        stopping = false;
        startEngine(intent, startId);
        return START_NOT_STICKY;
    }

    private void startEngine(Intent intent, int startId) {
        try {
            int coinIndex = intent.getIntExtra(EXTRA_COIN_INDEX, 0);
            if (coinIndex < 0 || coinIndex > 2) {
                failStart(startId, "Bitcoin (SHA-256d) ainda não possui motor integrado. Esta versão suporta Monero, Dogecoin e Litecoin.");
                return;
            }
            activeCoinIndex = coinIndex;
            activeCoinName = coinIndex == 0 ? "Monero" : (coinIndex == 1 ? "Dogecoin" : "Litecoin");
            String wallet = intent.getStringExtra(EXTRA_WALLET);
            String poolInput = intent.getStringExtra(EXTRA_POOL);
            if (wallet == null || wallet.trim().isEmpty()) {
                failStart(startId, "Informe o endereço público da carteira Monero.");
                return;
            }
            PoolAddress pool = PoolAddress.parse(poolInput);
            if (pool == null) {
                failStart(startId, "Pool inválida. Use host:porta, stratum+tcp://host:porta ou stratum+ssl://host:porta.");
                return;
            }
            if (coinIndex != 0 && pool.tls) {
                failStart(startId, "O motor Scrypt experimental desta versão aceita apenas Stratum TCP sem TLS. Use host:porta ou stratum+tcp://host:porta.");
                return;
            }

            BatteryInfo battery = readBatteryInfo();
            if (prefs.getBoolean("protect_battery", true) && (battery.level < 0 || battery.level < 20)) {
                failStart(startId, "Proteção ativada: bateria indisponível ou abaixo de 20%.");
                return;
            }
            if (prefs.getBoolean("protect_temp", true) && (battery.temperatureC < 0 || battery.temperatureC >= 44.0)) {
                failStart(startId, "Proteção ativada: temperatura indisponível ou em 44 °C ou mais.");
                return;
            }

            int threads = chooseThreads();
            File executable;
            ProcessBuilder builder;
            if (coinIndex == 0) {
                executable = new File(getApplicationInfo().nativeLibraryDir, "libxmrig.so");
                if (!executable.isFile() || !executable.canExecute()) {
                    failStart(startId, "Motor ARM64 XMRig ausente. Confira o workflow de build do GitHub Actions.");
                    return;
                }
                File config = writeConfig(pool, wallet.trim(), threads);
                builder = new ProcessBuilder(
                        executable.getAbsolutePath(), "-c", config.getAbsolutePath(),
                        "-t", Integer.toString(threads), "--randomx-mode=light", "--no-color", "--print-time=5");
            } else {
                executable = new File(getApplicationInfo().nativeLibraryDir, "libcpuminer.so");
                if (!executable.isFile() || !executable.canExecute()) {
                    failStart(startId, "Motor Scrypt ARM64 ausente. O build experimental cpuminer-opt não foi empacotado.");
                    return;
                }
                String poolUrl = "stratum+tcp://" + pool.host + ":" + pool.port;
                builder = new ProcessBuilder(executable.getAbsolutePath(), "-a", "scrypt", "-o", poolUrl,
                        "-u", wallet.trim(), "-p", "x", "-t", Integer.toString(threads), "--hash-meter");
            }
            if (coinIndex != 0) {
                String nativeDir = getApplicationInfo().nativeLibraryDir;
                File runtime = new File(nativeDir, "libc++_shared.so");
                if (!runtime.isFile()) {
                    failStart(startId,
                        "libc++_shared.so ausente no APK. Recompile pelo GitHub Actions.");
                    return;
                }

                Map<String, String> env = builder.environment();
                String oldPath = env.get("LD_LIBRARY_PATH");
                String libraryPath = nativeDir;
                if (oldPath != null && !oldPath.trim().isEmpty()) {
                    libraryPath += File.pathSeparator + oldPath;
                }
                env.put("LD_LIBRARY_PATH", libraryPath);
            }

            builder.redirectErrorStream(true);
            acquireWakeLock();
            process = builder.start();
            synchronized (this) { perThreadHashrates.clear(); }
            activeThreadCount = threads;
            prefs.edit().putInt("miner_active_coin_index", coinIndex).apply();
            appendEngineLog("Motor " + activeCoinName + " / " + (coinIndex == 0 ? "RandomX" : "Scrypt")
                    + " iniciado; threads configuradas: " + threads + ".");
            running = true;
            stopping = false;
            safetyHandler.removeCallbacks(safetyMonitor);
            safetyHandler.postDelayed(safetyMonitor, 15_000L);
            sessionStartedAt = System.currentTimeMillis();
            acceptedShares = 0;
            rejectedShares = 0;
            hashrateValue = 0.0;
            hashrate = "0 H/s";
            prefs.edit().putString("miner_samples", "[]").apply();
            saveStats();
            storeState("connecting", "Conectando à pool " + activeCoinName + "…");
            updateNotification("Conectando à pool " + activeCoinName + "…");
            broadcastStats();

            final Process started = process;
            Thread output = new Thread(() -> readOutput(started), "X1-Miner-Output");
            output.setDaemon(true);
            output.start();
        } catch (Exception e) {
            failStart(startId, "Não foi possível iniciar o motor: " + safeMessage(e));
        }
    }

    private File writeConfig(PoolAddress pool, String wallet, int threads) throws Exception {
        File folder = new File(getFilesDir(), "xmrig");
        if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Não foi possível criar a pasta de configuração.");
        JSONObject poolConfig = new JSONObject();
        poolConfig.put("url", pool.host + ":" + pool.port);
        poolConfig.put("user", wallet);
        poolConfig.put("pass", "x");
        poolConfig.put("coin", "monero");
        poolConfig.put("keepalive", true);
        poolConfig.put("tls", pool.tls);

        JSONObject cpu = new JSONObject();
        cpu.put("enabled", true);
        cpu.put("yield", true);
        cpu.put("huge-pages", false);
        cpu.put("max-threads-hint", Math.min(100, Math.max(10,
                threads * 100 / Math.max(1, Runtime.getRuntime().availableProcessors()))));

        JSONObject randomx = new JSONObject();
        randomx.put("mode", "light");
        randomx.put("1gb-pages", false);

        JSONObject config = new JSONObject();
        config.put("autosave", false);
        config.put("cpu", cpu);
        config.put("randomx", randomx);
        config.put("pools", new JSONArray().put(poolConfig));
        config.put("donate-level", 1);
        config.put("print-time", 5);
        File file = new File(folder, "config.json");
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(config.toString(2).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        return file;
    }

    private int chooseThreads() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int explicit = prefs.getInt("threads_index", 0);
        int profile = prefs.getInt("profile_index", 1);
        int wanted;
        if (explicit > 0) wanted = explicit;
        else if (profile == 0) wanted = Math.max(1, cores / 4);       // econômico
        else if (profile == 2) wanted = Math.max(1, (cores * 3) / 4); // alto desempenho
        else wanted = Math.max(1, cores / 2);                         // equilibrado/personalizado
        return Math.max(1, Math.min(cores, wanted));
    }

    private void readOutput(Process started) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (stopping || started != process) return;
                parseOutputLine(line);
            }
            int exitCode = started.waitFor();
            if (started == process && running && !stopping) {
                running = false;
                releaseWakeLock();
                storeState("error", "O motor encerrou (código " + exitCode + "). Verifique a pool e a carteira.");
                updateNotification("Motor encerrado");
                broadcastStats();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (started == process && running && !stopping) {
                running = false;
                releaseWakeLock();
                storeState("error", "Falha ao ler o motor: " + safeMessage(e));
                broadcastStats();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    }

    private synchronized void parseOutputLine(String line) {
        String clean = line == null ? "" : line.replaceAll("\\u001B\\[[;\\d]*m", "");
        appendEngineLog(clean);
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.contains("connection refused") || lower.contains("no active pools")
                || lower.contains("failed to connect") || lower.contains("connection error")) {
            storeState("connecting", "Pool indisponível; o motor tentará reconectar.");
        } else if (activeCoinIndex == 0 && (lower.contains("use pool") || lower.contains("new job"))) {
            storeState("running", "Motor RandomX ativo; recebeu trabalho da pool.");
        } else if (activeCoinIndex != 0 && (lower.contains("stratum connection established")
                || lower.contains("new block") || lower.contains("stratum difficulty")
                || lower.contains("accepted "))) {
            storeState("running", "Motor Scrypt ativo; recebeu/validou trabalho da pool.");
        }

        if (activeCoinIndex == 0) {
            Matcher share = SHARE_PATTERN.matcher(clean);
            if (share.find()) {
                try {
                    acceptedShares = Integer.parseInt(share.group(1));
                    rejectedShares = Integer.parseInt(share.group(2));
                } catch (NumberFormatException ignored) { }
                saveStats();
            }
            Matcher speed = SPEED_PATTERN.matcher(clean);
            if (speed.find()) {
                try {
                    hashrateValue = Double.parseDouble(speed.group(1)) * unitMultiplier(speed.group(3));
                    hashrate = formatHashrate(hashrateValue);
                    appendHashrateSample(hashrateValue);
                    saveStats();
                } catch (NumberFormatException ignored) {
                    appendEngineLog("Não foi possível interpretar a taxa de hash: " + clean);
                }
            }
        } else {
            Matcher accepted = CPUMINER_ACCEPT_PATTERN.matcher(clean);
            if (accepted.find()) {
                try {
                    String count = accepted.group(1) != null ? accepted.group(1) : accepted.group(2);
                    acceptedShares = Integer.parseInt(count);
                    storeState("running", "Motor Scrypt ativo; a pool reportou shares aceitas.");
                    saveStats();
                } catch (NumberFormatException ignored) { }
            }
            Matcher rejected = CPUMINER_REJECT_PATTERN.matcher(clean);
            if (rejected.find()) {
                try {
                    String count = rejected.group(1) != null ? rejected.group(1) : rejected.group(2);
                    rejectedShares = Integer.parseInt(count);
                    saveStats();
                } catch (NumberFormatException ignored) { }
            }

            Matcher threadRate = CPUMINER_THREAD_RATE_PATTERN.matcher(clean);
            if (threadRate.find()) {
                try {
                    int threadId = Integer.parseInt(threadRate.group(1));
                    double rate = Double.parseDouble(threadRate.group(2)) * prefixMultiplier(threadRate.group(3));
                    perThreadHashrates.put(threadId, rate);
                    double total = 0.0;
                    for (double threadValue : perThreadHashrates.values()) total += threadValue;
                    if (total >= 0.0 && !perThreadHashrates.isEmpty()) {
                        hashrateValue = total;
                        hashrate = formatHashrate(total);
                        if (perThreadHashrates.size() >= activeThreadCount) appendHashrateSample(total);
                        saveStats();
                    }
                } catch (NumberFormatException ignored) { }
            }
            Matcher totalRate = CPUMINER_HASH_RATE_PATTERN.matcher(clean);
            if (totalRate.find()) {
                try {
                    hashrateValue = Double.parseDouble(totalRate.group(1)) * prefixMultiplier(totalRate.group(2));
                    hashrate = formatHashrate(hashrateValue);
                    appendHashrateSample(hashrateValue);
                    saveStats();
                } catch (NumberFormatException ignored) { }
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastNotificationUpdate >= 5000L) {
            lastNotificationUpdate = now;
            updateNotification(hashrate + " · " + acceptedShares + " aceitas");
            broadcastStats();
        }
    }

    private double prefixMultiplier(String prefix) {
        if (prefix == null || prefix.isEmpty()) return 1.0;
        switch (prefix.toLowerCase(Locale.ROOT)) {
            case "k": return 1_000.0;
            case "m": return 1_000_000.0;
            case "g": return 1_000_000_000.0;
            case "t": return 1_000_000_000_000.0;
            default: return 1.0;
        }
    }

    private double unitMultiplier(String unit) {
        if ("kH/s".equals(unit)) return 1_000.0;
        if ("MH/s".equals(unit)) return 1_000_000.0;
        if ("GH/s".equals(unit)) return 1_000_000_000.0;
        if ("TH/s".equals(unit)) return 1_000_000_000_000.0;
        return 1.0;
    }

    /** Format the actual rate in H/s, kH/s, MH/s, GH/s or TH/s based on magnitude. */
    private String formatHashrate(double value) {
        if (!Double.isFinite(value) || value < 0.0) value = 0.0;
        String unit = "H/s";
        double scaled = value;
        if (value >= 1_000_000_000_000.0) {
            unit = "TH/s"; scaled = value / 1_000_000_000_000.0;
        } else if (value >= 1_000_000_000.0) {
            unit = "GH/s"; scaled = value / 1_000_000_000.0;
        } else if (value >= 1_000_000.0) {
            unit = "MH/s"; scaled = value / 1_000_000.0;
        } else if (value >= 1_000.0) {
            unit = "kH/s"; scaled = value / 1_000.0;
        }
        String number = String.format(Locale.US, "%.2f", scaled);
        while (number.contains(".") && number.endsWith("0")) number = number.substring(0, number.length() - 1);
        if (number.endsWith(".")) number = number.substring(0, number.length() - 1);
        return number + " " + unit;
    }

    /** Keep a bounded, timestamped tail of the real stdout/stderr lines from XMRig. */
    private synchronized void appendEngineLog(String message) {
        if (prefs == null) return;
        String line = message == null ? "" : message.replace("\r", "").trim();
        if (line.isEmpty()) return;
        if (line.length() > 1200) line = line.substring(0, 1200) + " … [linha truncada]";
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
                .format(new java.util.Date());
        String old = prefs.getString("miner_engine_logs", "");
        String[] rows = old == null || old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder out = new StringBuilder();
        int first = Math.max(0, rows.length - 199);
        for (int i = first; i < rows.length; i++) {
            if (out.length() > 0) out.append('\n');
            out.append(rows[i]);
        }
        if (out.length() > 0) out.append('\n');
        out.append(timestamp).append(" ").append(line);
        while (out.length() > 48_000) {
            int nextLine = out.indexOf("\n");
            if (nextLine < 0) { out.delete(0, out.length() - 48_000); break; }
            out.delete(0, nextLine + 1);
        }
        prefs.edit().putString("miner_engine_logs", out.toString()).apply();
    }

    private synchronized void appendHashrateSample(double value) {
        try {
            JSONArray samples = new JSONArray(prefs.getString("miner_samples", "[]"));
            samples.put(new JSONObject().put("t", System.currentTimeMillis()).put("h", value));
            while (samples.length() > 60) samples.remove(0);
            prefs.edit().putString("miner_samples", samples.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void saveStats() {
        prefs.edit().putString("miner_hashrate", hashrate)
                .putString("miner_hashrate_hps", Double.toString(hashrateValue))
                .putInt("miner_active_coin_index", activeCoinIndex)
                .putInt("miner_accepted", acceptedShares)
                .putInt("miner_rejected", rejectedShares)
                .putLong("miner_started_ms", sessionStartedAt).apply();
    }

    private void storeState(String state, String message) {
        prefs.edit().putString("miner_state", state).putString("miner_status", message).apply();
    }

    private void broadcastStats() {
        sendBroadcast(new Intent(ACTION_STATS).setPackage(getPackageName()));
    }

    private void startAsForeground(String message) {
        Notification notification = buildNotification(message);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String message) {
        return new Notification.Builder(this, CHANNEL_ID).setContentTitle("X1 Miner · " + activeCoinName)
                .setContentText(message).setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true).setOnlyAlertOnce(true).build();
    }

    private void updateNotification(String message) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(message));
    }

    private void acquireWakeLock() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        if (manager == null) return;
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "X1Miner::Mining");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
        catch (Exception ignored) { }
        wakeLock = null;
    }

    private void stopEngine(boolean byUser, String reason) {
        safetyHandler.removeCallbacks(safetyMonitor);
        stopping = true;
        running = false;
        Process old = process;
        process = null;
        if (old != null) {
            old.destroy();
            try {
                if (!old.waitFor(1200L, TimeUnit.MILLISECONDS)) old.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                old.destroyForcibly();
            } catch (Exception ignored) { old.destroyForcibly(); }
        }
        releaseWakeLock();
        if (byUser) {
            appendEngineLog(reason);
            storeState("stopped", reason);
            prefs.edit().putLong("miner_started_ms", 0L).apply();
            updateNotification(reason);
            broadcastStats();
        }
    }

    private void failStart(int startId, String message) {
        appendEngineLog("ERRO: " + message);
        safetyHandler.removeCallbacks(safetyMonitor);
        running = false;
        stopping = true;
        releaseWakeLock();
        storeState("error", message);
        prefs.edit().putLong("miner_started_ms", 0L).apply();
        broadcastStats();
        updateNotification(message);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelfResult(startId);
    }

    private BatteryInfo readBatteryInfo() {
        BatteryInfo info = new BatteryInfo();
        try {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) info.level = Math.round(level * 100f / scale);
                int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (temp >= 0) info.temperatureC = temp / 10.0;
            }
        } catch (Exception ignored) { }
        return info;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    @Override public void onDestroy() {
        stopEngine(false, "Serviço encerrado");
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class BatteryInfo {
        int level = -1;
        double temperatureC = -1.0;
    }

    private static final class PoolAddress {
        final String host;
        final int port;
        final boolean tls;
        PoolAddress(String host, int port, boolean tls) { this.host = host; this.port = port; this.tls = tls; }

        static PoolAddress parse(String input) {
            if (input == null || input.trim().isEmpty()) return null;
            try {
                String value = input.trim();
                if (!value.contains("://")) value = "stratum+tcp://" + value;
                URI uri = URI.create(value);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                boolean tls;
                if ("stratum+tcp".equals(scheme) || "tcp".equals(scheme)) tls = false;
                else if ("stratum+ssl".equals(scheme) || "ssl".equals(scheme)) tls = true;
                else return null;
                String host = uri.getHost();
                int port = uri.getPort();
                if (host == null || host.trim().isEmpty() || port < 1 || port > 65535) return null;
                if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) return null;
                String path = uri.getPath();
                if (path != null && !path.isEmpty() && !"/".equals(path)) return null;
                return new PoolAddress(host, port, tls);
            } catch (Exception ignored) { return null; }
        }
    }
}
