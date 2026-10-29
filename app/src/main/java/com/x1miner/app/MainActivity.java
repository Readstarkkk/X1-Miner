package com.x1miner.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.graphics.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * X1 Miner - UI-first preview.
 * This build deliberately does not pretend to mine: a real Stratum/native mining engine and
 * authenticated Node backend must be integrated before a pool connection or real hashrate exists.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "x1_miner_preferences";
    private static final int BLACK = 0xFF050505;
    private static final int SURFACE = 0xFF101010;
    private static final int SURFACE2 = 0xFF171717;
    private static final int BORDER = 0xFF34302A;
    private static final int GOLD = 0xFFF4B64F;
    private static final int ORANGE = 0xFFF29435;
    private static final int WHITE = 0xFFF6F4EF;
    private static final int MUTED = 0xFFAAA49A;
    private static final int RED = 0xFFF05B5B;
    private static final int GREEN = 0xFF43C88A;
    private static final int REQUEST_BACKGROUND_IMAGE = 701;
    private static final String PREF_BACKGROUND_URI = "custom_background_uri";

    private final String[] tabNames = {"Minerar", "Gráfico", "Configurações", "Node"};
    private final String[] tabGlyphs = {"⛏", "▥", "⚙", "⌘"};
    private final String[] coins = {"Monero (XMR)", "Dogecoin (DOGE)", "Litecoin (LTC)", "Bitcoin (BTC)"};
    private final String[] coinKeys = {"xmr", "doge", "ltc", "btc"};
    private final String[] algorithms = {"RandomX", "Scrypt", "Scrypt", "SHA-256d"};
    private final String[] units = {"H/s", "kH/s", "MH/s", "GH/s", "TH/s"};

    private SharedPreferences prefs;
    private LinearLayout root;
    private LinearLayout navBar;
    private FrameLayout contentHost;
    private int currentTab = 0;

    private final BroadcastReceiver miningReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if ((currentTab == 0 || currentTab == 1) && contentHost != null) showTab(currentTab);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        IntentFilter minerFilter = new IntentFilter(MiningService.ACTION_STATS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(miningReceiver, minerFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(miningReceiver, minerFilter);
        }
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
        showSplashScreen();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            buildShell();
            applySavedBackground();
            showTab(prefs.getInt("last_tab", 0));
        }, 850L);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(miningReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    /** Simple splash: existing X1 Miner cover on black, without extra artwork. */
    private void showSplashScreen() {
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(BLACK);
        LinearLayout center = new LinearLayout(this);
        center.setGravity(Gravity.CENTER);
        center.setOrientation(LinearLayout.VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.x1_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        center.addView(logo, new LinearLayout.LayoutParams(dp(216), dp(216)));
        TextView status = text("Iniciando X1 Miner...", 13, GOLD, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(16);
        center.addView(status, statusLp);
        splash.addView(center, new FrameLayout.LayoutParams(-1, -1));
        setContentView(splash);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BLACK);
        root.setPadding(dp(14), dp(7), dp(14), dp(7));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.x1_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        logoLp.rightMargin = dp(10);
        header.addView(logo, logoLp);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("X1 Miner", 23, GOLD, true);
        title.setTypeface(Typeface.SERIF, Typeface.BOLD);
        titleColumn.addView(title);
        titleColumn.addView(text("MINERAÇÃO • MONITORAMENTO", 9, MUTED, true));
        header.addView(titleColumn, new LinearLayout.LayoutParams(0, -2, 1));

        TextView version = text("v0.1", 11, MUTED, true);
        version.setPadding(dp(9), dp(6), dp(9), dp(6));
        version.setBackground(roundRect(SURFACE2, BORDER, 1, 12));
        header.addView(version);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(59)));

        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setPadding(dp(3), dp(3), dp(3), dp(3));
        navBar.setBackground(roundRect(SURFACE, 0xFF181818, 1, 16));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, dp(66));
        navLp.topMargin = dp(5);
        navLp.bottomMargin = dp(8);
        root.addView(navBar, navLp);

        contentHost = new FrameLayout(this);
        root.addView(contentHost, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView foot = text("X1 MINER   •   INTERFACE DE PRÉVIA", 9, 0xFF79736B, true);
        foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, dp(20));
        footLp.topMargin = dp(3);
        root.addView(foot, footLp);
        renderNavigation();
    }

    private void renderNavigation() {
        navBar.removeAllViews();
        for (int i = 0; i < tabNames.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(2), dp(5), dp(2), dp(4));
            item.setBackground(roundRect(currentTab == i ? 0xFF2B2113 : SURFACE, 0, 0, 12));
            TextView glyph = text(tabGlyphs[i], 20, currentTab == i ? GOLD : MUTED, true);
            TextView label = text(tabNames[i], 10, currentTab == i ? GOLD : MUTED, currentTab == i);
            item.addView(glyph);
            item.addView(label);
            item.setOnClickListener(v -> showTab(index));
            navBar.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        }
    }

    private void showTab(int tab) {
        currentTab = Math.max(0, Math.min(tab, tabNames.length - 1));
        prefs.edit().putInt("last_tab", currentTab).apply();
        renderNavigation();
        contentHost.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(1), dp(2), dp(1), dp(14));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        if (currentTab == 0) renderMiner(content);
        else if (currentTab == 1) renderGraph(content);
        else if (currentTab == 2) renderSettings(content);
        else renderNode(content);
        contentHost.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private void renderMiner(LinearLayout content) {
        sectionHeading(content, "Painel de mineração", "Acompanhe o motor, a moeda e as proteções do dispositivo.");

        LinearLayout status = card(content);
        String engineState = prefs.getString("miner_state", "stopped");
        String stateLabel = "running".equals(engineState) ? "MINERANDO"
                : "connecting".equals(engineState) ? "CONECTANDO"
                : "error".equals(engineState) ? "ERRO" : "PARADO";
        int stateColor = "running".equals(engineState) ? GREEN
                : "error".equals(engineState) ? RED
                : "connecting".equals(engineState) ? GOLD : MUTED;
        rowTitle(status, "STATUS DO MOTOR", stateLabel, stateColor);
        TextView engine = text("Motor nativo · Monero / RandomX", 16, WHITE, true);
        LinearLayout.LayoutParams engineLp = new LinearLayout.LayoutParams(-1, -2);
        engineLp.topMargin = dp(6);
        status.addView(engine, engineLp);
        status.addView(text(prefs.getString("miner_status", "Sessão parada"), 12, MUTED, false));
        divider(status);
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        String currentHashrate = prefs.getString("miner_hashrate", "0 H/s");
        int currentAccepted = prefs.getInt("miner_accepted", 0);
        int currentRejected = prefs.getInt("miner_rejected", 0);
        long currentStarted = prefs.getLong("miner_started_ms", 0L);
        stats.addView(metric("HASHRATE", currentHashrate, "taxa informada pelo motor", true), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(metric("SHARES ACEITAS", String.valueOf(currentAccepted), currentRejected + " rejeitadas", false), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(metric("TEMPO", formatElapsed(currentStarted), currentStarted > 0 ? "sessão atual" : "sem sessão ativa", false), new LinearLayout.LayoutParams(0, -2, 1));
        status.addView(stats);

        LinearLayout config = card(content);
        cardHeading(config, "Configurar mineração", "Cada moeda mantém sua própria carteira e pool.");
        TextView coinLabel = label("Moeda");
        config.addView(coinLabel);
        Spinner coinSpinner = spinner(coins);
        int selectedCoin = Math.max(0, Math.min(prefs.getInt("selected_coin", 0), coins.length - 1));
        coinSpinner.setSelection(selectedCoin);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(48));
        inputLp.topMargin = dp(5);
        inputLp.bottomMargin = dp(10);
        config.addView(coinSpinner, inputLp);

        TextView algorithmLabel = label("Algoritmo");
        config.addView(algorithmLabel);
        TextView algorithmValue = text(algorithms[selectedCoin], 14, GOLD, true);
        algorithmValue.setPadding(dp(12), dp(10), dp(12), dp(10));
        algorithmValue.setBackground(roundRect(SURFACE2, BORDER, 1, 10));
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(-1, -2);
        valueLp.topMargin = dp(5);
        valueLp.bottomMargin = dp(10);
        config.addView(algorithmValue, valueLp);

        TextView walletLabel = label("Carteira de recebimento");
        config.addView(walletLabel);
        EditText wallet = edit("Endereço público da carteira", false);
        wallet.setSingleLine(true);
        wallet.setText(prefs.getString(walletKey(selectedCoin), ""));
        config.addView(wallet, editParams());
        TextView poolLabel = label("Pool e porta");
        LinearLayout.LayoutParams poolLabelLp = new LinearLayout.LayoutParams(-1, -2);
        poolLabelLp.topMargin = dp(10);
        config.addView(poolLabel, poolLabelLp);
        EditText pool = edit("stratum+tcp://servidor:porta", false);
        pool.setSingleLine(true);
        pool.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        pool.setText(prefs.getString(poolKey(selectedCoin), ""));
        config.addView(pool, editParams());

        final int[] activeCoin = {selectedCoin};
        wallet.addTextChangedListener(simpleWatcher(s -> prefs.edit().putString(walletKey(activeCoin[0]), s).apply()));
        pool.addTextChangedListener(simpleWatcher(s -> prefs.edit().putString(poolKey(activeCoin[0]), s).apply()));
        coinSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == activeCoin[0]) return;
                prefs.edit().putInt("selected_coin", position).apply();
                hideKeyboard(wallet);
                showTab(0);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        note(config, "Confira se a pool aceita exatamente esta moeda e algoritmo antes de usar o motor real.");

        LinearLayout guard = card(content);
        cardHeading(guard, "Proteção do dispositivo", "Limites previstos para a futura sessão de mineração.");
        BatteryInfo battery = getBatteryInfo();
        LinearLayout guardRow = new LinearLayout(this);
        guardRow.setOrientation(LinearLayout.HORIZONTAL);
        guardRow.addView(metric("BATERIA", battery.level < 0 ? "N/D" : battery.level + "%", "mínimo: 20%", true), new LinearLayout.LayoutParams(0, -2, 1));
        String temp = battery.temperatureC < 0 ? "N/D" : String.format(Locale.getDefault(), "%.1f °C", battery.temperatureC);
        guardRow.addView(metric("TEMP. BATERIA", temp, "limite configurado: 44 °C", false), new LinearLayout.LayoutParams(0, -2, 1));
        guard.addView(guardRow);
        note(guard, "A temperatura apresentada é a do sensor de bateria, quando disponível; ela não representa necessariamente a temperatura da CPU.");

        Button start = primaryButton("▶  Iniciar mineração");
        start.setOnClickListener(v -> {
            if (coinSpinner.getSelectedItemPosition() != 0) {
                toast("O motor nativo disponível nesta versão é somente para Monero (RandomX).");
                return;
            }
            BatteryInfo current = getBatteryInfo();
            if (current.level < 0) {
                toast("Bloqueado: não foi possível ler o nível da bateria.");
                return;
            }
            if (current.level < 20) {
                toast("Bloqueado: a bateria precisa estar em pelo menos 20%.");
                return;
            }
            if (current.temperatureC < 0) {
                toast("Bloqueado: não foi possível ler a temperatura da bateria.");
                return;
            }
            if (current.temperatureC >= 44.0) {
                toast("Bloqueado: temperatura da bateria em 44 °C ou mais.");
                return;
            }
            if (wallet.getText().toString().trim().isEmpty()) {
                toast("Informe a carteira da moeda selecionada primeiro.");
                return;
            }
            if (pool.getText().toString().trim().isEmpty()) {
                toast("Informe a pool e a porta primeiro.");
                return;
            }
            Runnable launchMiner = () -> {
                Intent service = new Intent(MainActivity.this, MiningService.class);
                service.setAction(MiningService.ACTION_START);
                service.putExtra(MiningService.EXTRA_WALLET, wallet.getText().toString().trim());
                service.putExtra(MiningService.EXTRA_POOL, pool.getText().toString().trim());
                service.putExtra(MiningService.EXTRA_COIN_INDEX, coinSpinner.getSelectedItemPosition());
                try {
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                    else startService(service);
                    toast("Solicitação enviada. Confira o estado do motor e a notificação do X1 Miner.");
                } catch (Exception e) {
                    toast("Não foi possível iniciar o serviço: " + e.getClass().getSimpleName());
                }
            };
            if (prefs.getBoolean("confirm_start", true)) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Iniciar mineração Monero?")
                        .setMessage("A mineração mantém a CPU ocupada e pode aquecer o aparelho. O motor começa limitado a uma fração dos núcleos.")
                        .setNegativeButton("Cancelar", (dialog, which) -> { })
                        .setPositiveButton("Iniciar", (dialog, which) -> launchMiner.run())
                        .show();
            } else {
                launchMiner.run();
            }
        });
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, dp(52));
        startLp.topMargin = dp(2);
        startLp.bottomMargin = dp(10);
        content.addView(start, startLp);
        Button stop = secondaryButton("■  Parar mineração");
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-1, dp(46));
        stopLp.bottomMargin = dp(8);
        content.addView(stop, stopLp);
        stop.setOnClickListener(v -> {
            Intent service = new Intent(MainActivity.this, MiningService.class);
            service.setAction(MiningService.ACTION_STOP);
            try { startService(service); toast("Comando de parada enviado."); }
            catch (Exception e) { toast("Não foi possível enviar a parada: " + e.getClass().getSimpleName()); }
        });
        note(content, "Hashrate e shares são lidos do motor nativo. Ganhos/pagamentos não são estimados pelo aplicativo.");
    }

    private void renderGraph(LinearLayout content) {
        sectionHeading(content, "Gráfico", "Estatísticas por moeda, com dados reais quando houver um motor ativo.");
        LinearLayout top = card(content);
        long graphStarted = prefs.getLong("miner_started_ms", 0L);
        cardHeading(top, "Estatísticas da sessão", prefs.getString("miner_status", "Sem sessão ativa"));
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(metric("TEMPO DA SESSÃO", formatElapsed(graphStarted), graphStarted > 0 ? "sessão atual" : "sem sessão", true), new LinearLayout.LayoutParams(0, -2, 1));
        row1.addView(metric("HASHRATE", prefs.getString("miner_hashrate", "0 H/s"), "informado pelo motor", false), new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(row1);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(metric("SHARES ACEITAS", String.valueOf(prefs.getInt("miner_accepted", 0)), prefs.getInt("miner_rejected", 0) + " rejeitadas", false), new LinearLayout.LayoutParams(0, -2, 1));
        row2.addView(metric("GANHOS", "N/D", "sem dados de pool", false), new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(row2);

        LinearLayout chart = card(content);
        cardHeading(chart, "Taxa de hash ao longo do tempo", "Amostras reais informadas pelo motor nativo.");
        chart.addView(new MiningGraphView(this, prefs), new LinearLayout.LayoutParams(-1, dp(190)));
        LinearLayout perCoin = card(content);
        cardHeading(perCoin, "Resumo por moeda", "Os algoritmos não são comparados entre si.");
        for (int i = 0; i < coins.length; i++) {
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setOrientation(LinearLayout.HORIZONTAL);
            TextView c = text(coins[i], 13, WHITE, true);
            line.addView(c, new LinearLayout.LayoutParams(0, dp(40), 1));
            String coinShares = i == 0 ? prefs.getInt("miner_accepted", 0) + " shares aceitas" : "motor não instalado";
            line.addView(text(coinShares, 11, MUTED, false));
            perCoin.addView(line);
            if (i < coins.length - 1) divider(perCoin);
        }
        LinearLayout logsCard = card(content);
        cardHeading(logsCard, "Logs do motor", "Saída real do processo nativo; útil para diagnosticar pool, conexão e shares.");
        TextView logsPreview = text(getEngineLogsPreview(), 10, MUTED, false);
        logsPreview.setTypeface(Typeface.MONOSPACE);
        logsPreview.setMaxLines(8);
        logsPreview.setTextIsSelectable(true);
        logsPreview.setPadding(dp(8), dp(8), dp(8), dp(8));
        logsPreview.setBackground(roundRect(SURFACE2, BORDER, 1, 8));
        logsCard.addView(logsPreview, new LinearLayout.LayoutParams(-1, -2));
        Button openLogs = secondaryButton("Ver logs completos / copiar");
        LinearLayout.LayoutParams openLogsLp = new LinearLayout.LayoutParams(-1, dp(44));
        openLogsLp.topMargin = dp(8);
        logsCard.addView(openLogs, openLogsLp);
        openLogs.setOnClickListener(v -> showEngineLogs());

        note(content, "Ganhos e pagamentos só poderão ser exibidos quando forem recebidos da pool. Nenhum valor financeiro é estimado nesta prévia.");
    }

    private void renderSettings(LinearLayout content) {
        sectionHeading(content, "Configurações", "Preferências salvas localmente neste dispositivo.");
        LinearLayout performance = card(content);
        cardHeading(performance, "Desempenho", "Threads e perfil controlam o motor; o teto de hashrate ainda não é aplicado.");
        TextView threadLabel = label("Threads da CPU");
        performance.addView(threadLabel);
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        String[] threadOptions = new String[cores + 1];
        threadOptions[0] = "Automático";
        for (int i = 1; i <= cores; i++) threadOptions[i] = String.valueOf(i);
        Spinner threads = spinner(threadOptions);
        int threadSelected = prefs.getInt("threads_index", 0);
        threads.setSelection(Math.min(threadSelected, threadOptions.length - 1));
        performance.addView(threads, new LinearLayout.LayoutParams(-1, dp(46)));
        threads.setOnItemSelectedListener(new SimpleSpinnerListener(position -> prefs.edit().putInt("threads_index", position).apply()));

        TextView profileLabel = label("Perfil de mineração");
        LinearLayout.LayoutParams labLp = new LinearLayout.LayoutParams(-1, -2);
        labLp.topMargin = dp(12);
        performance.addView(profileLabel, labLp);
        String[] profiles = {"Econômico", "Equilibrado", "Alto desempenho", "Personalizado"};
        Spinner profile = spinner(profiles);
        int profileIndex = prefs.getInt("profile_index", 1);
        profile.setSelection(Math.min(profileIndex, profiles.length - 1));
        performance.addView(profile, new LinearLayout.LayoutParams(-1, dp(46)));
        profile.setOnItemSelectedListener(new SimpleSpinnerListener(position -> prefs.edit().putInt("profile_index", position).apply()));

        TextView hashLimitLabel = label("Limite de hashrate (opcional; teto de 1 TH/s)");
        LinearLayout.LayoutParams hashLabLp = new LinearLayout.LayoutParams(-1, -2);
        hashLabLp.topMargin = dp(12);
        performance.addView(hashLimitLabel, hashLabLp);
        LinearLayout hashRow = new LinearLayout(this);
        hashRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText hashValue = edit("Valor", false);
        hashValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hashValue.setText(prefs.getString("hash_limit_value", "500"));
        hashValue.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        hashRow.addView(hashValue, new LinearLayout.LayoutParams(0, dp(48), 1));
        Spinner unit = spinner(units);
        unit.setSelection(Math.max(0, Math.min(4, prefs.getInt("hash_limit_unit", 0))));
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(dp(112), dp(48));
        unitLp.leftMargin = dp(8);
        hashRow.addView(unit, unitLp);
        performance.addView(hashRow);
        hashValue.addTextChangedListener(simpleWatcher(s -> prefs.edit().putString("hash_limit_value", s).apply()));
        unit.setOnItemSelectedListener(new SimpleSpinnerListener(position -> prefs.edit().putInt("hash_limit_unit", position).apply()));
        Button validateLimit = secondaryButton("Validar limite");
        validateLimit.setOnClickListener(v -> validateHashLimit(hashValue.getText().toString(), unit.getSelectedItemPosition()));
        LinearLayout.LayoutParams validLp = new LinearLayout.LayoutParams(-1, dp(42));
        validLp.topMargin = dp(8);
        performance.addView(validateLimit, validLp);

        LinearLayout automation = card(content);
        cardHeading(automation, "Automação", "Estes toggles ainda não estão ligados ao serviço nativo.");
        addPreference(automation, "Execução em segundo plano", "background", false);
        addPreference(automation, "Reconectar à pool automaticamente", "auto_reconnect", true);
        addPreference(automation, "Minerar somente no Wi-Fi", "wifi_only", false);
        addPreference(automation, "Abrir ao ligar o celular", "start_on_boot", false);

        LinearLayout protect = card(content);
        cardHeading(protect, "Proteção térmica e bateria", "Os limites abaixo serão obrigatórios para uma sessão real.");
        addPreference(protect, "Bloquear abaixo de 20% de bateria", "protect_battery", true);
        addPreference(protect, "Bloquear com temperatura da bateria ≥ 44 °C", "protect_temp", true);
        addPreference(protect, "Exigir confirmação antes de iniciar", "confirm_start", true);

        LinearLayout notifications = card(content);
        cardHeading(notifications, "Notificações", "A notificação do serviço é obrigatória; estes toggles ainda não filtram os avisos.");
        addPreference(notifications, "Alertas de sessão", "notify_session", true);
        addPreference(notifications, "Alertas de temperatura", "notify_temp", true);
        addPreference(notifications, "Alertas de bateria", "notify_battery", true);
        addPreference(notifications, "Alertas de desconexão", "notify_disconnect", true);

        LinearLayout appearance = card(content);
        cardHeading(appearance, "Aparência", "Escolha uma imagem da galeria para usar como background.");
        Button chooseBackground = secondaryButton("Escolher imagem da galeria");
        appearance.addView(chooseBackground, new LinearLayout.LayoutParams(-1, dp(46)));
        chooseBackground.setOnClickListener(v -> openBackgroundPicker());
        Button resetBackground = secondaryButton("Voltar ao background preto");
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(-1, dp(46));
        resetLp.topMargin = dp(8);
        appearance.addView(resetBackground, resetLp);
        resetBackground.setOnClickListener(v -> {
            prefs.edit().remove(PREF_BACKGROUND_URI).apply();
            root.setBackgroundColor(BLACK);
            toast("Background padrão restaurado.");
        });
        note(appearance, "A imagem é salva neste dispositivo. Os cartões mantêm o fundo escuro para preservar a leitura.");

        note(content, "A leitura térmica depende dos sensores disponíveis no aparelho. A proteção de 44 °C precisa ser validada com o sensor escolhido na integração do motor.");
    }

    private String getEngineLogs() {
        String logs = prefs.getString("miner_engine_logs", "");
        return logs == null || logs.trim().isEmpty()
                ? "Nenhum log recebido do motor nesta sessão. Inicie o motor ou confira se o processo nativo está empacotado."
                : logs;
    }

    private String getEngineLogsPreview() {
        String logs = getEngineLogs();
        String[] lines = logs.split("\n");
        StringBuilder preview = new StringBuilder();
        int start = Math.max(0, lines.length - 8);
        for (int i = start; i < lines.length; i++) {
            if (preview.length() > 0) preview.append('\n');
            preview.append(lines[i]);
        }
        return preview.toString();
    }

    private void showEngineLogs() {
        final String logs = getEngineLogs();
        ScrollView logScroll = new ScrollView(this);
        logScroll.setFillViewport(false);
        TextView logText = text(logs, 11, WHITE, false);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(8), dp(8), dp(8), dp(8));
        logScroll.addView(logText, new ScrollView.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Logs do motor X1")
                .setView(logScroll, dp(12), dp(4), dp(12), dp(4))
                .setPositiveButton("Copiar logs", (dialog, which) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("X1 Miner logs", logs));
                        toast("Logs copiados. Você pode colá-los aqui para análise.");
                    } else {
                        toast("Não foi possível acessar a área de transferência.");
                    }
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void openBackgroundPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_BACKGROUND_IMAGE);
        } catch (Exception e) {
            toast("Não foi possível abrir o seletor de imagens.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_BACKGROUND_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        Bitmap previewBitmap = decodeBackgroundBitmap(uri);
        if (previewBitmap == null) {
            toast("Não foi possível ler essa imagem. Escolha outra.");
            return;
        }
        ImageView preview = new ImageView(this);
        preview.setImageBitmap(previewBitmap);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(BLACK);
        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setPadding(dp(8), dp(8), dp(8), dp(8));
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, dp(260)));
        new AlertDialog.Builder(this)
                .setTitle("Prévia do background")
                .setView(previewFrame)
                .setNegativeButton("Cancelar", (dialog, which) -> { })
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                        // Providers that do not support persistable grants are handled by the fallback below.
                    }
                    prefs.edit().putString(PREF_BACKGROUND_URI, uri.toString()).apply();
                    if (applyBackground(uri)) toast("Background aplicado e salvo.");
                    else {
                        prefs.edit().remove(PREF_BACKGROUND_URI).apply();
                        toast("Não foi possível aplicar essa imagem.");
                    }
                })
                .show();
    }

    private void applySavedBackground() {
        String saved = prefs.getString(PREF_BACKGROUND_URI, null);
        if (saved == null || saved.trim().isEmpty()) {
            root.setBackgroundColor(BLACK);
            return;
        }
        try {
            if (!applyBackground(Uri.parse(saved))) {
                prefs.edit().remove(PREF_BACKGROUND_URI).apply();
                root.setBackgroundColor(BLACK);
            }
        } catch (Exception e) {
            prefs.edit().remove(PREF_BACKGROUND_URI).apply();
            root.setBackgroundColor(BLACK);
        }
    }

    private boolean applyBackground(Uri uri) {
        Bitmap bitmap = decodeBackgroundBitmap(uri);
        if (bitmap == null) return false;
        BitmapDrawable background = new BitmapDrawable(getResources(), bitmap);
        background.setAlpha(145);
        root.setBackground(background);
        return true;
    }

    /** Decode a downsampled image so large gallery photos are less likely to exhaust memory. */
    private Bitmap decodeBackgroundBitmap(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) return null;
                BitmapFactory.decodeStream(stream, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try (java.io.InputStream stream = getContentResolver().openInputStream(uri)) {
                return stream == null ? null : BitmapFactory.decodeStream(stream, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void renderNode(LinearLayout content) {
        sectionHeading(content, "Node", "Organize vários dispositivos por meio de uma conexão autenticada.");
        LinearLayout status = card(content);
        rowTitle(status, "ESTADO DA NODE", "OFFLINE", RED);
        TextView state = text("Backend Node não configurado", 17, WHITE, true);
        LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(-1, -2);
        stateLp.topMargin = dp(7);
        status.addView(state, stateLp);
        status.addView(text("Nenhum dispositivo remoto está conectado nesta versão.", 12, MUTED, false));

        LinearLayout create = card(content);
        cardHeading(create, "Criar Node", "Defina um nome e uma senha para preparar a configuração.");
        EditText createName = edit("Nome da Node", false);
        createName.setSingleLine(true);
        create.addView(createName, editParams());
        EditText createPassword = edit("Senha (mínimo 8 caracteres)", true);
        createPassword.setSingleLine(true);
        LinearLayout.LayoutParams passLp = editParams();
        passLp.topMargin = dp(8);
        create.addView(createPassword, passLp);
        Button createButton = primaryButton("Criar Node");
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(-1, dp(47));
        cbLp.topMargin = dp(10);
        create.addView(createButton, cbLp);
        createButton.setOnClickListener(v -> {
            String name = createName.getText().toString().trim();
            String password = createPassword.getText().toString();
            if (name.isEmpty()) { toast("Digite o nome da Node."); return; }
            if (!validNodePassword(password)) { toast("Use uma senha com 8+ caracteres, sem espaços ou emojis."); return; }
            prefs.edit().putString("node_name_draft", name).apply();
            toast("Dados validados. A Node não foi criada: falta integrar o backend seguro.");
        });

        LinearLayout join = card(content);
        cardHeading(join, "Entrar em uma Node", "A conexão remota exige um servidor e autenticação real.");
        EditText nodeId = edit("ID ou endereço da Node", false);
        nodeId.setSingleLine(true);
        join.addView(nodeId, editParams());
        EditText joinPassword = edit("Senha da Node", true);
        joinPassword.setSingleLine(true);
        LinearLayout.LayoutParams jp = editParams();
        jp.topMargin = dp(8);
        join.addView(joinPassword, jp);
        Button joinButton = secondaryButton("Entrar na Node");
        LinearLayout.LayoutParams jb = new LinearLayout.LayoutParams(-1, dp(47));
        jb.topMargin = dp(10);
        join.addView(joinButton, jb);
        joinButton.setOnClickListener(v -> {
            if (nodeId.getText().toString().trim().isEmpty()) { toast("Informe o ID ou endereço da Node."); return; }
            if (!validNodePassword(joinPassword.getText().toString())) { toast("A senha deve ter 8+ caracteres, sem espaços ou emojis."); return; }
            toast("Dados validados. Nenhuma conexão foi feita porque o backend não está configurado.");
        });

        LinearLayout devices = card(content);
        cardHeading(devices, "Dispositivos", "Os dados só aparecem após conexão real.");
        TextView empty = text("Nenhum dispositivo conectado", 14, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(20), 0, dp(20));
        devices.addView(empty);
        note(content, "Não digite senhas de carteira aqui. O backend futuro deve usar conexão criptografada, permissões explícitas e proteções locais que não possam ser desativadas pela Node.");
    }

    private void addPreference(LinearLayout parent, String title, String key, boolean defaultValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView label = text(title, 13, WHITE, false);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        CheckBox check = new CheckBox(this);
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(GOLD));
        check.setChecked(prefs.getBoolean(key, defaultValue));
        check.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean(key, isChecked).apply());
        row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(42)));
        parent.addView(row);
        divider(parent);
    }

    private void validateHashLimit(String raw, int unitIndex) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value <= 0) throw new NumberFormatException();
            double multiplier = new double[]{1d, 1_000d, 1_000_000d, 1_000_000_000d, 1_000_000_000_000d}[Math.max(0, Math.min(4, unitIndex))];
            if (value * multiplier > 1_000_000_000_000d) {
                toast("O limite máximo permitido na interface é 1 TH/s.");
            } else {
                toast("Limite válido. É um teto configurado, não uma promessa de desempenho.");
            }
        } catch (NumberFormatException ex) {
            toast("Digite um valor numérico maior que zero.");
        }
    }

    private boolean validNodePassword(String password) {
        if (password == null || password.length() < 8) return false;
        for (int offset = 0; offset < password.length();) {
            int cp = password.codePointAt(offset);
            if (Character.isWhitespace(cp) || isEmojiCodePoint(cp)) return false;
            offset += Character.charCount(cp);
        }
        return true;
    }

    private boolean isEmojiCodePoint(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF)
                || (cp >= 0x2600 && cp <= 0x27BF)
                || (cp >= 0xFE00 && cp <= 0xFE0F)
                || (cp >= 0x1F1E6 && cp <= 0x1F1FF);
    }

    private String walletKey(int coin) { return "wallet_" + coinKeys[Math.max(0, Math.min(coin, coinKeys.length - 1))]; }
    private String poolKey(int coin) { return "pool_" + coinKeys[Math.max(0, Math.min(coin, coinKeys.length - 1))]; }

    private BatteryInfo getBatteryInfo() {
        BatteryInfo info = new BatteryInfo();
        try {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) info.level = Math.round(level * 100f / scale);
                int tempTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (tempTenths >= 0) info.temperatureC = tempTenths / 10.0;
            }
        } catch (Exception ignored) {
            // Leave sensor values unavailable rather than inventing measurements.
        }
        return info;
    }

    private class BatteryInfo {
        int level = -1;
        double temperatureC = -1;
    }

    private TextWatcher simpleWatcher(java.util.function.Consumer<String> consumer) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { consumer.accept(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        };
    }

    private interface IntConsumer { void accept(int value); }
    private class SimpleSpinnerListener implements AdapterView.OnItemSelectedListener {
        private final IntConsumer consumer;
        SimpleSpinnerListener(IntConsumer consumer) { this.consumer = consumer; }
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { consumer.accept(position); }
        @Override public void onNothingSelected(AdapterView<?> parent) { }
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        card.setBackground(roundRect(SURFACE, BORDER, 1, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        parent.addView(card, lp);
        return card;
    }

    private void sectionHeading(LinearLayout parent, String title, String subtitle) {
        TextView t = text(title, 20, WHITE, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.leftMargin = dp(2);
        parent.addView(t, tlp);
        TextView sub = text(subtitle, 12, MUTED, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.leftMargin = dp(2);
        slp.topMargin = dp(3);
        slp.bottomMargin = dp(12);
        parent.addView(sub, slp);
    }

    private void cardHeading(LinearLayout parent, String title, String subtitle) {
        parent.addView(text(title, 15, GOLD, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 11, MUTED, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(3);
            lp.bottomMargin = dp(10);
            parent.addView(sub, lp);
        } else {
            space(parent, 7);
        }
    }

    private void rowTitle(LinearLayout parent, String left, String right, int rightColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(left, 10, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text("● " + right, 10, rightColor, true));
        parent.addView(row);
    }

    private LinearLayout metric(String title, String value, String sub, boolean accent) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setPadding(dp(7), dp(8), dp(4), dp(7));
        TextView t = text(title, 9, MUTED, true);
        metric.addView(t);
        TextView v = text(value, value.length() > 10 ? 13 : 16, accent ? GOLD : WHITE, true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.topMargin = dp(4);
        metric.addView(v, vlp);
        TextView s = text(sub, 9, MUTED, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(2);
        metric.addView(s, slp);
        return metric;
    }

    private TextView label(String value) {
        TextView v = text(value, 12, MUTED, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(3);
        v.setLayoutParams(lp);
        return v;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(WHITE);
                v.setTextSize(14);
                v.setGravity(Gravity.CENTER_VERTICAL);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(WHITE);
                v.setTextSize(14);
                v.setPadding(dp(12), dp(10), dp(12), dp(10));
                v.setBackgroundColor(SURFACE2);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(7), 0, dp(5), 0);
        spinner.setBackground(roundRect(SURFACE2, BORDER, 1, 10));
        return spinner;
    }

    private EditText edit(String hint, boolean password) {
        EditText edit = new EditText(this);
        edit.setTextColor(WHITE);
        edit.setHintTextColor(0xFF77716A);
        edit.setTextSize(13);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setPadding(dp(11), dp(5), dp(11), dp(5));
        edit.setBackground(roundRect(SURFACE2, BORDER, 1, 10));
        if (password) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return edit;
    }

    private LinearLayout.LayoutParams editParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(47));
        lp.topMargin = dp(5);
        return lp;
    }

    private Button primaryButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(0xFF181008);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(roundRect(ORANGE, GOLD, 1, 13));
        button.setElevation(dp(2));
        return button;
    }

    private Button secondaryButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(GOLD);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(roundRect(SURFACE2, GOLD, 1, 12));
        return button;
    }

    private void note(LinearLayout parent, String text) {
        TextView note = text(text, 11, MUTED, false);
        note.setPadding(dp(3), dp(2), dp(3), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(1);
        parent.addView(note, lp);
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(0xFF29251F);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.topMargin = dp(5);
        lp.bottomMargin = dp(5);
        parent.addView(line, lp);
    }

    private void space(LinearLayout parent, int heightDp) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable roundRect(int color, int strokeColor, int strokeWidth, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), strokeColor);
        return d;
    }

    private void hideKeyboard(View view) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    private String formatElapsed(long startedAt) {
        if (startedAt <= 0L) return "00:00:00";
        long seconds = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remaining = seconds % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remaining);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class MiningGraphView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final SharedPreferences prefs;
        MiningGraphView(Context context, SharedPreferences prefs) { super(context); this.prefs = prefs; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float left = 30 * density, right = getWidth() - 10 * density;
            float top = 14 * density, bottom = getHeight() - 27 * density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1 * density);
            paint.setColor(0xFF29251F);
            for (int i = 0; i < 5; i++) {
                float y = top + (bottom - top) * i / 4f;
                canvas.drawLine(left, y, right, y, paint);
            }
            for (int i = 0; i < 5; i++) {
                float x = left + (right - left) * i / 4f;
                canvas.drawLine(x, top, x, bottom, paint);
            }
            JSONArray samples;
            try { samples = new JSONArray(prefs.getString("miner_samples", "[]")); }
            catch (Exception ignored) { samples = new JSONArray(); }
            if (samples.length() == 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(MUTED);
                paint.setTextSize(12 * density);
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Aguardando dados reais da pool", (left + right) / 2f, (top + bottom) / 2f, paint);
                return;
            }
            double max = 0.0;
            double[] values = new double[samples.length()];
            for (int i = 0; i < samples.length(); i++) {
                try { values[i] = Math.max(0.0, samples.getJSONObject(i).optDouble("h", 0.0)); }
                catch (Exception ignored) { values[i] = 0.0; }
                max = Math.max(max, values[i]);
            }
            if (max <= 0.0) max = 1.0;
            Path path = new Path();
            for (int i = 0; i < values.length; i++) {
                float x = values.length == 1 ? (left + right) / 2f : left + (right - left) * i / (values.length - 1f);
                float y = bottom - (float) (values[i] / (max * 1.15)) * (bottom - top);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setColor(GOLD);
            paint.setStrokeWidth(2.2f * density);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(MUTED);
            paint.setTextSize(9 * density);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("máx. observado: " + formatHashrate(max), left, getHeight() - 5 * density, paint);
        }

        private static String formatHashrate(double value) {
            if (value >= 1_000_000_000_000.0) return String.format(Locale.US, "%.2f TH/s", value / 1_000_000_000_000.0);
            if (value >= 1_000_000_000.0) return String.format(Locale.US, "%.2f GH/s", value / 1_000_000_000.0);
            if (value >= 1_000_000.0) return String.format(Locale.US, "%.2f MH/s", value / 1_000_000.0);
            if (value >= 1_000.0) return String.format(Locale.US, "%.2f kH/s", value / 1_000.0);
            return String.format(Locale.US, "%.2f H/s", value);
        }
    }

}
