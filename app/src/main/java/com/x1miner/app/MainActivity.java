package com.x1miner.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 11, 30);
    private static final int CARD = Color.rgb(30, 22, 49);
    private static final int INPUT = Color.rgb(22, 16, 37);
    private static final int ORANGE = Color.rgb(255, 122, 26);
    private static final int PURPLE = Color.rgb(139, 92, 246);
    private static final int WHITE = Color.rgb(248, 245, 255);
    private static final int MUTED = Color.rgb(184, 173, 201);

    private int cores;
    private int maxThreads;
    private int threads;
    private EditText poolField, walletField, workerField, passwordField, engineField;
    private CheckBox tlsCheck;
    private SeekBar threadSeek;
    private TextView threadValue, statusValue, temperatureValue;
    private Button startButton, stopButton;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver guardReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (ThermalGuardService.ACTION_TEMP_UPDATE.equals(action)) {
                double temperature = intent.getDoubleExtra("temperature", -1.0);
                if (temperature > 0) {
                    temperatureValue.setText(String.format(Locale.US, "%.1f °C · bateria", temperature));
                    temperatureValue.setTextColor(temperature >= 42.0 ? ORANGE : WHITE);
                } else {
                    temperatureValue.setText("Leitura indisponível");
                    temperatureValue.setTextColor(MUTED);
                }
            } else if (ThermalGuardService.ACTION_THERMAL_STOP.equals(action)) {
                statusValue.setText("PARADO POR SEGURANÇA");
                statusValue.setTextColor(ORANGE);
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                String reason = intent.getStringExtra("reason");
                temperatureValue.setText(reason == null ? "Proteção térmica acionada" : reason);
                Toast.makeText(MainActivity.this, "Mineração interrompida por proteção térmica.", Toast.LENGTH_LONG).show();
            } else if (ThermalGuardService.ACTION_GUARD_STATUS.equals(action)) {
                String status = intent.getStringExtra("status");
                if (status != null) temperatureValue.setText(status);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        maxThreads = Math.max(1, cores / 2);
        threads = Math.max(1, maxThreads / 2);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildInterface();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 31);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ThermalGuardService.ACTION_TEMP_UPDATE);
            filter.addAction(ThermalGuardService.ACTION_THERMAL_STOP);
            filter.addAction(ThermalGuardService.ACTION_GUARD_STATUS);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(guardReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(guardReceiver, filter);
            receiverRegistered = true;
        }
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            try { unregisterReceiver(guardReceiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.x1miner.app.R.drawable.x1_logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(14), 0, 0, 0);
        TextView title = text("X1 MINER", 26, WHITE, true);
        title.setLetterSpacing(0.04f);
        names.addView(title);
        TextView subtitle = text("CPU MINING · RANDOMX", 11, MUTED, true);
        subtitle.setLetterSpacing(0.10f);
        names.addView(subtitle);
        header.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(header);

        TextView intro = text("Painel de controle do XMRig via Termux", 14, MUTED, false);
        intro.setPadding(0, dp(14), 0, dp(18));
        page.addView(intro);

        LinearLayout stateCard = card();
        TextView section = text("STATUS DO MINER", 11, PURPLE, true);
        section.setLetterSpacing(0.12f);
        stateCard.addView(section);
        statusValue = text("PARADO", 19, WHITE, true);
        statusValue.setPadding(0, dp(8), 0, dp(2));
        stateCard.addView(statusValue);
        TextView engineNote = text("Motor externo: XMRig ARM64 · Termux", 12, MUTED, false);
        stateCard.addView(engineNote);
        temperatureValue = text("Temperatura: aguardando leitura", 13, WHITE, true);
        temperatureValue.setPadding(0, dp(15), 0, 0);
        stateCard.addView(temperatureValue);
        page.addView(stateCard, marginBottom(14));

        LinearLayout configCard = card();
        TextView configTitle = text("CONFIGURAÇÃO", 11, PURPLE, true);
        configTitle.setLetterSpacing(0.12f);
        configCard.addView(configTitle, marginBottom(12));

        poolField = addField(configCard, "POOL (host:porta)", "pool.exemplo.com:3333", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        walletField = addField(configCard, "CARTEIRA XMR", "Cole seu endereço Monero", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        workerField = addField(configCard, "NOME DO WORKER", "x1-mobile", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        workerField.setText("x1-mobile");
        passwordField = addField(configCard, "SENHA DA POOL", "x", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setText("x");
        engineField = addField(configCard, "CAMINHO DO XMRIG NO TERMUX", "~/downloads/xmrig/build/xmrig", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        engineField.setText("~/downloads/xmrig/build/xmrig");

        tlsCheck = new CheckBox(this);
        tlsCheck.setText("Usar TLS/SSL (se a pool exigir)");
        tlsCheck.setTextColor(WHITE);
        tlsCheck.setButtonTintList(android.content.res.ColorStateList.valueOf(ORANGE));
        tlsCheck.setPadding(0, dp(5), 0, dp(8));
        configCard.addView(tlsCheck);
        page.addView(configCard, marginBottom(14));

        LinearLayout cpuCard = card();
        TextView cpuTitle = text("USO DA CPU", 11, PURPLE, true);
        cpuTitle.setLetterSpacing(0.12f);
        cpuCard.addView(cpuTitle);
        threadValue = text("Threads: " + threads + "/" + cores, 18, WHITE, true);
        threadValue.setPadding(0, dp(8), 0, dp(3));
        cpuCard.addView(threadValue);
        TextView cpuHint = text("Por segurança, o app limita a no máximo metade dos núcleos lógicos. A carga real varia, e o Android pode reduzir a frequência por temperatura.", 12, MUTED, false);
        cpuHint.setPadding(0, 0, 0, dp(5));
        cpuCard.addView(cpuHint);
        threadSeek = new SeekBar(this);
        threadSeek.setMax(Math.max(0, maxThreads - 1));
        threadSeek.setProgress(Math.min(maxThreads - 1, threads - 1));
        threadSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(ORANGE));
        threadSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(ORANGE));
        threadSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                threads = progress + 1;
                int percent = Math.round((threads * 100f) / cores);
                threadValue.setText("Threads: " + threads + "/" + cores + " · limite de " + percent + "% dos núcleos");
                if (threads == maxThreads) threadValue.setTextColor(ORANGE); else threadValue.setTextColor(WHITE);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        cpuCard.addView(threadSeek);
        page.addView(cpuCard, marginBottom(14));

        TextView safety = text("PROTEÇÃO TÉRMICA", 11, PURPLE, true);
        safety.setLetterSpacing(0.12f);
        page.addView(safety, marginBottom(8));
        TextView safetyText = text("O app não inicia se não conseguir ler a temperatura da bateria ou se ela estiver em 42 °C ou mais. Em uso, ao atingir 45 °C, o X1 Miner envia um pedido para parar o XMRig. O script do Termux também monitora a temperatura e para se não conseguir lê-la; o Android também interrompe em estado térmico SEVERE.", 13, MUTED, false);
        safetyText.setLineSpacing(dp(3), 1f);
        page.addView(safetyText, marginBottom(14));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        startButton = button("INICIAR MINERAÇÃO", ORANGE, Color.BLACK);
        stopButton = button("PARAR", CARD, WHITE);
        stopButton.setEnabled(false);
        buttons.addView(startButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        View gap = new View(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        buttons.addView(stopButton, new LinearLayout.LayoutParams(dp(96), dp(54)));
        page.addView(buttons, marginBottom(10));

        Button logButton = button("VER LOG REAL DO XMRIG NO TERMUX", PURPLE, Color.WHITE);
        page.addView(logButton, marginBottom(14));
        logButton.setOnClickListener(v -> showMinerLog());

        TextView logHint = text("Log do XMRig: ~/.x1miner/miner.log\nO app não inventa hashrate nem confirma conexão sozinho. No log, procure conexão com a pool e shares aceitas.", 12, MUTED, false);
        logHint.setLineSpacing(dp(3), 1f);
        page.addView(logHint);

        startButton.setOnClickListener(v -> confirmAndStart());
        stopButton.setOnClickListener(v -> stopMining(false));
        setContentView(scroll);
        updateThreadLabel();
    }

    private void showMinerLog() {
        if (!isTermuxInstalled()) {
            new AlertDialog.Builder(this)
                .setTitle("Termux não encontrado")
                .setMessage("Instale o Termux para consultar o log do XMRig.")
                .setPositiveButton("OK", null).show();
            return;
        }
        String script = "LOG=\"$HOME/.x1miner/miner.log\"; if [ -f \"$LOG\" ]; then tail -n 100 \"$LOG\"; else echo 'Ainda não existe log. Inicie o X1 Miner primeiro.'; fi; echo; read -r -p 'Pressione Enter para fechar esta tela...' _";
        if (!sendTermuxCommand(new String[]{"-lc", script}, false, "X1 Miner — ver log")) {
            Toast.makeText(this, "Não foi possível abrir o Termux. Confira a permissão RUN_COMMAND.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateThreadLabel() {
        int percent = Math.round((threads * 100f) / cores);
        threadValue.setText("Threads: " + threads + "/" + cores + " · limite de " + percent + "% dos núcleos");
        threadValue.setTextColor(threads == maxThreads ? ORANGE : WHITE);
    }

    private void confirmAndStart() {
        final String pool = poolField.getText().toString().trim();
        final String wallet = walletField.getText().toString().trim();
        final String worker = workerField.getText().toString().trim();
        final String password = passwordField.getText().toString().trim();
        final String engine = engineField.getText().toString().trim();
        if (!Pattern.matches("[A-Za-z0-9.-]+:[0-9]{1,5}", pool)) {
            poolField.setError("Use host:porta, por exemplo pool.exemplo.com:3333"); return;
        }
        try {
            int port = Integer.parseInt(pool.substring(pool.lastIndexOf(':') + 1));
            if (port < 1 || port > 65535) { poolField.setError("Porta inválida"); return; }
        } catch (Exception e) { poolField.setError("Porta inválida"); return; }
        if (!Pattern.matches("[A-Za-z0-9._-]{20,150}", wallet)) {
            walletField.setError("Confira o endereço da carteira"); return;
        }
        if (!Pattern.matches("[A-Za-z0-9._-]{1,32}", worker)) {
            workerField.setError("Use apenas letras, números, ponto, hífen e underline"); return;
        }
        if (!Pattern.matches("[A-Za-z0-9._-]{1,64}", password)) {
            passwordField.setError("Senha inválida para este app"); return;
        }
        if (!Pattern.matches("[A-Za-z0-9_./~-]+", engine)) {
            engineField.setError("Caminho inválido"); return;
        }
        double batteryTemp = readCurrentBatteryTemperature();
        if (batteryTemp <= 0) {
            new AlertDialog.Builder(this)
                .setTitle("Temperatura indisponível")
                .setMessage("O Android não conseguiu ler a temperatura da bateria. Por segurança, o X1 Miner não vai iniciar. Confira o Termux:API e tente novamente quando a leitura estiver disponível.")
                .setPositiveButton("OK", null).show();
            return;
        }
        if (batteryTemp >= 42.0) {
            new AlertDialog.Builder(this)
                .setTitle("Espere o aparelho esfriar")
                .setMessage(String.format(Locale.US, "A bateria está a %.1f °C. O X1 Miner só permite iniciar abaixo de 42 °C e para a mineração ao atingir 45 °C.", batteryTemp))
                .setPositiveButton("OK", null).show();
            return;
        }
        startMining(pool, wallet, worker, password, engine);
    }

    private double readCurrentBatteryTemperature() {
        try {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return -1.0;
            int temperature = battery.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1);
            if (temperature <= 0) return -1.0;
            return temperature / 10.0;
        } catch (Exception ignored) {
            return -1.0;
        }
    }

    private void startMining(String pool, String wallet, String worker, String password, String engine) {
        if (!isTermuxInstalled()) {
            new AlertDialog.Builder(this)
                .setTitle("Termux não encontrado")
                .setMessage("O X1 Miner é um painel e usa o XMRig instalado no Termux. Instale/configure o Termux e compile o XMRig para ARM64 antes de iniciar; o app não inclui o motor de mineração.")
                .setPositiveButton("OK", null).show();
            return;
        }
        if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                .setTitle("Permissão do Termux necessária")
                .setMessage("Em Configurações > Apps > X1 Miner > Permissões, permita 'Executar comandos no Termux'. No Termux, também é preciso habilitar allow-external-apps. Faça isso apenas se confiar nesta cópia do app.")
                .setPositiveButton("Abrir configurações", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton("Cancelar", null).show();
            return;
        }

        String[] args = new String[]{"-lc", MINER_SCRIPT, "x1miner", engine, pool, wallet, worker,
                String.valueOf(threads), password, tlsCheck.isChecked() ? "1" : "0"};
        if (!sendTermuxCommand(args, true, "X1 Miner — iniciar")) {
            new AlertDialog.Builder(this)
                .setTitle("Não foi possível iniciar")
                .setMessage("O Termux recusou o comando. Confira a permissão RUN_COMMAND e a opção allow-external-apps no Termux.")
                .setPositiveButton("OK", null).show();
            return;
        }

        try {
            Intent service = new Intent(this, ThermalGuardService.class);
            service.setAction(ThermalGuardService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        } catch (Exception e) {
            Toast.makeText(this, "Monitor térmico não iniciou: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        statusValue.setText("COMANDO ENVIADO AO TERMUX");
        statusValue.setTextColor(ORANGE);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        Toast.makeText(this, "Comando enviado. Confirme o resultado em ~/.x1miner/miner.log no Termux.", Toast.LENGTH_LONG).show();
    }

    private void stopMining(boolean thermal) {
        sendTermuxCommand(new String[]{"-lc", STOP_SCRIPT}, true, thermal ? "X1 Miner — proteção térmica" : "X1 Miner — parar");
        stopService(new Intent(this, ThermalGuardService.class));
        statusValue.setText(thermal ? "PARADO POR SEGURANÇA" : "PARADO");
        statusValue.setTextColor(thermal ? ORANGE : WHITE);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        if (!thermal) Toast.makeText(this, "Comando de parada enviado ao Termux.", Toast.LENGTH_SHORT).show();
    }

    private boolean sendTermuxCommand(String[] arguments, boolean background, String label) {
        try {
            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments);
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background);
            intent.putExtra("com.termux.RUN_COMMAND_LABEL", label);
            intent.putExtra("com.termux.RUN_COMMAND_DESCRIPTION", "Comando solicitado pelo X1 Miner");
            intent.putExtra("com.termux.RUN_COMMAND_HELP", "https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent");
            startService(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTermuxInstalled() {
        try { getPackageManager().getPackageInfo("com.termux", 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(18));
        layout.setBackground(bg);
        return layout;
    }

    private EditText addField(LinearLayout parent, String label, String hint, int inputType) {
        TextView labelView = text(label, 10, MUTED, true);
        labelView.setLetterSpacing(0.07f);
        parent.addView(labelView, marginBottom(5));
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(14);
        field.setTextColor(WHITE);
        field.setHintTextColor(Color.rgb(126, 115, 147));
        field.setHint(hint);
        field.setInputType(inputType);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(INPUT);
        shape.setCornerRadius(dp(11));
        shape.setStroke(dp(1), Color.rgb(58, 45, 82));
        field.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.bottomMargin = dp(13);
        parent.addView(field, lp);
        return field;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value, int background, int textColor) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(15));
        b.setBackground(shape);
        if (Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams marginBottom(int dpValue) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(dpValue);
        return params;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final String STOP_SCRIPT = """
            PID_FILE="$HOME/.x1miner/miner.pid"
            if [ -f "$PID_FILE" ]; then
              P="$(cat "$PID_FILE" 2>/dev/null || true)"
              if [ -n "$P" ]; then kill -TERM "$P" 2>/dev/null || true; fi
            fi
            echo "X1 Miner: pedido de parada enviado" >> "$HOME/.x1miner/miner.log" 2>/dev/null || true
            """;

    private static final String MINER_SCRIPT = """
            set -u
            ENGINE="$1"
            POOL="$2"
            WALLET="$3"
            WORKER="$4"
            THREADS="$5"
            PASSWORD="$6"
            USE_TLS="$7"
            case "$ENGINE" in "~/"*) ENGINE="$HOME/${ENGINE#~/}" ;; esac
            LOG_DIR="$HOME/.x1miner"
            LOG_FILE="$LOG_DIR/miner.log"
            PID_FILE="$LOG_DIR/miner.pid"
            mkdir -p "$LOG_DIR"
            if [ ! -x "$ENGINE" ]; then
              echo "ERRO: XMRig não encontrado/executável em: $ENGINE" >> "$LOG_FILE"
              exit 2
            fi
            if ! command -v termux-battery-status >/dev/null 2>&1; then
              echo "ERRO: proteção térmica independente exige Termux:API e o comando termux-battery-status. Instale-os antes de minerar." >> "$LOG_FILE"
              exit 3
            fi
            if [ -f "$PID_FILE" ]; then
              OLD_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
              if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
                echo "ERRO: já existe um miner ativo (PID $OLD_PID). Pare-o primeiro." >> "$LOG_FILE"
                exit 4
              fi
              rm -f "$PID_FILE"
            fi
            ARGS=(-o "$POOL" -u "$WALLET" -p "$PASSWORD" --rig-id="$WORKER" -a rx/0 --threads="$THREADS" --no-color --print-time=10)
            if [ "$USE_TLS" = "1" ]; then ARGS+=(--tls); fi
            echo "X1 Miner: iniciando XMRig em $(date)" >> "$LOG_FILE"
            "$ENGINE" "${ARGS[@]}" >> "$LOG_FILE" 2>&1 &
            MINER_PID=$!
            printf '%s\\n' "$MINER_PID" > "$PID_FILE"
            STOP_REASON=""
            while kill -0 "$MINER_PID" 2>/dev/null; do
              JSON="$(termux-battery-status 2>/dev/null || true)"
              TEMP="$(printf '%s' "$JSON" | grep -o '"temperature"[[:space:]]*:[[:space:]]*[-0-9.]*' | head -n 1 | cut -d: -f2 | tr -d ' ' || true)"
              if [ -z "$TEMP" ] || ! awk -v t="$TEMP" 'BEGIN { exit !(t > 0 && t < 45) }'; then
                if [ -n "$TEMP" ] && awk -v t="$TEMP" 'BEGIN { exit !(t >= 45) }'; then
                  STOP_REASON="temperatura da bateria atingiu 45 °C ($TEMP °C)"
                else
                  STOP_REASON="não foi possível ler a temperatura da bateria; parada segura"
                fi
                echo "X1 Miner: $STOP_REASON em $(date)" >> "$LOG_FILE"
                kill -TERM "$MINER_PID" 2>/dev/null || true
                sleep 2
                kill -KILL "$MINER_PID" 2>/dev/null || true
                break
              fi
              sleep 3
            done
            wait "$MINER_PID" 2>/dev/null || true
            rm -f "$PID_FILE"
            if [ -n "$STOP_REASON" ]; then echo "X1 Miner finalizado: $STOP_REASON" >> "$LOG_FILE"; fi
            echo "X1 Miner: monitor encerrado em $(date)" >> "$LOG_FILE"
            """;
}
