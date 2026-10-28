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
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** A standalone, safety-focused battery and Android thermal monitor. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 11, 30);
    private static final int CARD = Color.rgb(30, 22, 49);
    private static final int ORANGE = Color.rgb(255, 122, 26);
    private static final int PURPLE = Color.rgb(139, 92, 246);
    private static final int WHITE = Color.rgb(248, 245, 255);
    private static final int MUTED = Color.rgb(184, 173, 201);
    private static final String DEFAULT_GUIDANCE = "As temperaturas podem não estar disponíveis em todos os aparelhos. A temperatura da bateria não é a temperatura exata da CPU. Se o aparelho estiver muito quente, pare as tarefas pesadas, desconecte-o da carga se for seguro e deixe-o esfriar naturalmente. Não coloque o telefone em geladeira ou freezer.";

    private TextView monitorStatus;
    private TextView batteryTemperature;
    private TextView androidThermalStatus;
    private TextView guidance;
    private Button startButton;
    private Button stopButton;
    private boolean receiverRegistered;

    private final BroadcastReceiver monitorReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (ThermalGuardService.ACTION_UPDATE.equals(action)) {
                renderReadings(
                        intent.getDoubleExtra(ThermalGuardService.EXTRA_TEMPERATURE, -1),
                        intent.getIntExtra(ThermalGuardService.EXTRA_THERMAL_STATUS, -1),
                        intent.getBooleanExtra(ThermalGuardService.EXTRA_MONITORING, false));
            } else if (ThermalGuardService.ACTION_MONITOR_STOPPED.equals(action)) {
                monitorStatus.setText("MONITORAMENTO PARADO");
                monitorStatus.setTextColor(WHITE);
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
            }
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildInterface();
        refreshReadings();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 31);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(ThermalGuardService.ACTION_UPDATE);
        filter.addAction(ThermalGuardService.ACTION_MONITOR_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(monitorReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(monitorReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            try { unregisterReceiver(monitorReceiver); }
            catch (IllegalArgumentException ignored) { }
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
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.x1_logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        TextView title = text("X1 MONITOR", 25, WHITE, true);
        title.setLetterSpacing(0.035f);
        titles.addView(title);
        TextView subtitle = text("BATERIA · ESTADO TÉRMICO", 10, MUTED, true);
        subtitle.setLetterSpacing(0.08f);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(header);

        TextView intro = text("Monitore as leituras do Android antes de usar tarefas que exigem muito do aparelho.",
                14, MUTED, false);
        intro.setLineSpacing(dp(3), 1f);
        intro.setPadding(0, dp(14), 0, dp(18));
        page.addView(intro);

        LinearLayout readingsCard = card();
        readingsCard.addView(sectionTitle("LEITURAS DO DISPOSITIVO"));
        monitorStatus = text("MONITORAMENTO PARADO", 18, WHITE, true);
        monitorStatus.setPadding(0, dp(12), 0, dp(12));
        readingsCard.addView(monitorStatus);
        batteryTemperature = text("Bateria: lendo…", 22, WHITE, true);
        readingsCard.addView(batteryTemperature);
        androidThermalStatus = text("Estado térmico do Android: verificando…", 14, MUTED, true);
        androidThermalStatus.setPadding(0, dp(10), 0, 0);
        readingsCard.addView(androidThermalStatus);
        page.addView(readingsCard, marginBottom(14));

        LinearLayout guidanceCard = card();
        guidanceCard.addView(sectionTitle("COMO INTERPRETAR"));
        guidance = text(
                DEFAULT_GUIDANCE,
                13, MUTED, false);
        guidance.setLineSpacing(dp(4), 1f);
        guidance.setPadding(0, dp(10), 0, 0);
        guidanceCard.addView(guidance);
        page.addView(guidanceCard, marginBottom(14));

        Button refreshButton = button("ATUALIZAR LEITURAS", PURPLE, Color.WHITE);
        page.addView(refreshButton, marginBottom(10));
        refreshButton.setOnClickListener(v -> refreshReadings());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        startButton = button("INICIAR MONITOR", ORANGE, Color.BLACK);
        stopButton = button("PARAR", CARD, WHITE);
        stopButton.setEnabled(false);
        controls.addView(startButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        View gap = new View(this);
        controls.addView(gap, new LinearLayout.LayoutParams(dp(10), 1));
        controls.addView(stopButton, new LinearLayout.LayoutParams(dp(100), dp(52)));
        page.addView(controls, marginBottom(14));

        TextView disclaimer = text(
                "Esta versão é apenas um monitor térmico: não inicia, configura nem controla mineração ou outros processos.",
                12, MUTED, false);
        disclaimer.setLineSpacing(dp(3), 1f);
        page.addView(disclaimer);

        startButton.setOnClickListener(v -> startMonitor());
        stopButton.setOnClickListener(v -> stopMonitor());
        setContentView(scroll);
    }

    private void startMonitor() {
        try {
            Intent intent = new Intent(this, ThermalGuardService.class);
            intent.setAction(ThermalGuardService.ACTION_START);
            startForegroundService(intent);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            monitorStatus.setText("MONITORAMENTO ATIVO");
            monitorStatus.setTextColor(ORANGE);
            Toast.makeText(this, "Monitor térmico iniciado.", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            new AlertDialog.Builder(this)
                    .setTitle("Não foi possível iniciar")
                    .setMessage("O Android não permitiu iniciar o monitor. Confira as permissões de notificação e tente novamente. " + safeMessage(ex))
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void stopMonitor() {
        stopService(new Intent(this, ThermalGuardService.class));
        monitorStatus.setText("MONITORAMENTO PARADO");
        monitorStatus.setTextColor(WHITE);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void refreshReadings() {
        double temp = readBatteryTemperature();
        int thermal = readAndroidThermalStatus();
        renderReadings(temp, thermal, !startButton.isEnabled());
    }

    private void renderReadings(double temperature, int thermalStatus, boolean monitoring) {
        if (temperature > 0) {
            batteryTemperature.setText(String.format(Locale.US, "Bateria: %.1f °C", temperature));
            if (temperature >= 45.0) {
                batteryTemperature.setTextColor(Color.RED);
                guidance.setText("ALERTA: a bateria está a 45 °C ou mais. Pare as tarefas pesadas e deixe o telefone esfriar naturalmente, longe de fontes de calor. A leitura pode estar atrasada e não cobre todos os componentes.");
            } else if (temperature >= 42.0) {
                batteryTemperature.setTextColor(ORANGE);
                guidance.setText("A bateria está quente. Evite tarefas pesadas e acompanhe a temperatura. A leitura da bateria não é a temperatura exata da CPU.");
            } else {
                batteryTemperature.setTextColor(WHITE);
                guidance.setText(DEFAULT_GUIDANCE);
            }
        } else {
            batteryTemperature.setText("Bateria: leitura indisponível");
            batteryTemperature.setTextColor(ORANGE);
        }

        androidThermalStatus.setText("Estado térmico do Android: " + thermalStatusLabel(thermalStatus));
        if (thermalStatus >= 4) {
            androidThermalStatus.setTextColor(Color.RED);
            guidance.setText("ALERTA: o Android reportou estado térmico severo ou crítico. Pare as tarefas pesadas e deixe o telefone esfriar naturalmente.");
        } else {
            androidThermalStatus.setTextColor(MUTED);
        }
        if (monitoring) {
            monitorStatus.setText("MONITORAMENTO ATIVO");
            monitorStatus.setTextColor(ORANGE);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        } else {
            monitorStatus.setText("MONITORAMENTO PARADO");
            monitorStatus.setTextColor(WHITE);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
    }

    private double readBatteryTemperature() {
        try {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return -1.0;
            int value = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            return value > 0 ? value / 10.0 : -1.0;
        } catch (Exception ignored) {
            return -1.0;
        }
    }

    private int readAndroidThermalStatus() {
        if (Build.VERSION.SDK_INT < 29) return -1;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm == null ? -1 : pm.getCurrentThermalStatus();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String thermalStatusLabel(int status) {
        switch (status) {
            case -1: return "indisponível neste Android/aparelho";
            case PowerManager.THERMAL_STATUS_NONE: return "normal";
            case PowerManager.THERMAL_STATUS_LIGHT: return "leve";
            case PowerManager.THERMAL_STATUS_MODERATE: return "moderado";
            case PowerManager.THERMAL_STATUS_SEVERE: return "severo — reduza a carga";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "crítico — pare tarefas pesadas";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "emergência";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "desligamento térmico iminente";
            default: return "desconhecido (" + status + ")";
        }
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(CARD);
        shape.setCornerRadius(dp(18));
        layout.setBackground(shape);
        return layout;
    }

    private TextView sectionTitle(String label) {
        TextView view = text(label, 11, PURPLE, true);
        view.setLetterSpacing(0.12f);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String label, int background, int textColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(15));
        button.setBackground(shape);
        button.setStateListAnimator(null);
        return button;
    }

    private LinearLayout.LayoutParams marginBottom(int valueDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(valueDp);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
