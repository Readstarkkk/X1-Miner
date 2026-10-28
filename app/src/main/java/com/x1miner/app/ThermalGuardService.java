package com.x1miner.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.Locale;

public class ThermalGuardService extends Service {
    public static final String ACTION_START = "com.x1miner.app.START_GUARD";
    public static final String ACTION_TEMP_UPDATE = "com.x1miner.app.TEMP_UPDATE";
    public static final String ACTION_THERMAL_STOP = "com.x1miner.app.THERMAL_STOP";
    public static final String ACTION_GUARD_STATUS = "com.x1miner.app.GUARD_STATUS";
    private static final String CHANNEL_ID = "x1miner_thermal_guard";
    private static final int NOTIFICATION_ID = 8124;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean stoppedForThermal = false;
    private PowerManager powerManager;
    private PowerManager.OnThermalStatusChangedListener thermalListener;

    private final Runnable monitorRunnable = new Runnable() {
        @Override public void run() {
            if (stoppedForThermal) return;
            double temp = readBatteryTemperature();
            Intent update = new Intent(ACTION_TEMP_UPDATE).setPackage(getPackageName());
            update.putExtra("temperature", temp);
            sendBroadcast(update);

            if (temp <= 0) {
                stopForThermal("Leitura de temperatura indisponível; parada segura");
                return;
            }
            if (temp >= 45.0) {
                stopForThermal(String.format(Locale.US, "bateria a %.1f °C", temp));
                return;
            }
            if (Build.VERSION.SDK_INT >= 29 && powerManager != null) {
                try {
                    if (powerManager.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE) {
                        stopForThermal("Android informou estado térmico SEVERE");
                        return;
                    }
                } catch (Exception ignored) { }
            }
            handler.postDelayed(this, 3000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 29 && powerManager != null) {
            thermalListener = status -> {
                if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
                    handler.post(() -> stopForThermal("Android informou estado térmico SEVERE"));
                }
            };
            try { powerManager.addThermalStatusListener(thermalListener); } catch (Exception ignored) { }
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Proteção térmica ativa"));
        if (!stoppedForThermal) {
            handler.removeCallbacks(monitorRunnable);
            handler.post(monitorRunnable);
        }
        return START_STICKY;
    }

    private double readBatteryTemperature() {
        try {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return -1;
            int deciC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (deciC <= 0) return -1;
            return deciC / 10.0;
        } catch (Exception e) { return -1; }
    }

    private void stopForThermal(String reason) {
        if (stoppedForThermal) return;
        stoppedForThermal = true;
        handler.removeCallbacks(monitorRunnable);
        stopTermuxMiner();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification("Miner parado por segurança térmica"));
        Intent event = new Intent(ACTION_THERMAL_STOP).setPackage(getPackageName());
        event.putExtra("reason", reason);
        sendBroadcast(event);
        stopForeground(false);
        stopSelf();
    }

    private void stopTermuxMiner() {
        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", MainStopScript.VALUE});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            intent.putExtra("com.termux.RUN_COMMAND_LABEL", "X1 Miner — parada térmica");
            startService(intent);
        } catch (Exception ignored) {
            // O monitor dentro do Termux também para o miner ao ler 45 °C ou quando a leitura falha.
        }
    }

    private Notification buildNotification(String text) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("X1 Miner")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setOngoing(!stoppedForThermal)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle("X1 Miner")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(!stoppedForThermal)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "X1 Miner — proteção térmica", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notificação persistente enquanto a proteção térmica monitora a mineração");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(monitorRunnable);
        if (Build.VERSION.SDK_INT >= 29 && powerManager != null && thermalListener != null) {
            try { powerManager.removeThermalStatusListener(thermalListener); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class MainStopScript {
        static final String VALUE = "PID_FILE=\"$HOME/.x1miner/miner.pid\"; if [ -f \"$PID_FILE\" ]; then P=\"$(cat \"$PID_FILE\" 2>/dev/null || true)\"; [ -z \"$P\" ] || kill -TERM \"$P\" 2>/dev/null || true; fi";
    }
}
