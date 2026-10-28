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

/** Foreground-only thermal monitor; it does not start or control other processes. */
public final class ThermalGuardService extends Service {
    public static final String ACTION_START = "com.x1miner.app.MONITOR_START";
    public static final String ACTION_UPDATE = "com.x1miner.app.MONITOR_UPDATE";
    public static final String ACTION_MONITOR_STOPPED = "com.x1miner.app.MONITOR_STOPPED";
    public static final String EXTRA_TEMPERATURE = "temperature";
    public static final String EXTRA_THERMAL_STATUS = "thermal_status";
    public static final String EXTRA_MONITORING = "monitoring";

    private static final String CHANNEL_ID = "x1_monitor_thermal";
    private static final int NOTIFICATION_ID = 8124;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager powerManager;
    private PowerManager.OnThermalStatusChangedListener thermalListener;
    private boolean isMonitoring;

    private final Runnable monitorRunnable = new Runnable() {
        @Override public void run() {
            if (!isMonitoring) return;
            double temperature = readBatteryTemperature();
            int thermal = readAndroidThermalStatus();
            broadcastReadings(temperature, thermal);
            updateNotification(temperature, thermal);
            handler.postDelayed(this, 5000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 29 && powerManager != null) {
            thermalListener = status -> handler.post(() -> {
                double temperature = readBatteryTemperature();
                broadcastReadings(temperature, status);
                updateNotification(temperature, status);
            });
            try { powerManager.addThermalStatusListener(getMainExecutor(), thermalListener); }
            catch (Exception ignored) { thermalListener = null; }
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Lendo temperatura do dispositivo"));
        isMonitoring = true;
        handler.removeCallbacks(monitorRunnable);
        handler.post(monitorRunnable);
        return START_NOT_STICKY;
    }

    private double readBatteryTemperature() {
        try {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return -1.0;
            int deciC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            return deciC > 0 ? deciC / 10.0 : -1.0;
        } catch (Exception ignored) {
            return -1.0;
        }
    }

    private int readAndroidThermalStatus() {
        if (Build.VERSION.SDK_INT < 29 || powerManager == null) return -1;
        try { return powerManager.getCurrentThermalStatus(); }
        catch (Exception ignored) { return -1; }
    }

    private void broadcastReadings(double temperature, int thermalStatus) {
        Intent update = new Intent(ACTION_UPDATE).setPackage(getPackageName());
        update.putExtra(EXTRA_TEMPERATURE, temperature);
        update.putExtra(EXTRA_THERMAL_STATUS, thermalStatus);
        update.putExtra(EXTRA_MONITORING, isMonitoring);
        sendBroadcast(update);
    }

    private void updateNotification(double temperature, int status) {
        String line;
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            line = "ALERTA térmico do Android — reduza a carga";
        } else if (temperature >= 45.0) {
            line = String.format(Locale.US, "Bateria a %.1f °C — pare tarefas pesadas", temperature);
        } else if (temperature >= 42.0) {
            line = String.format(Locale.US, "Bateria quente: %.1f °C", temperature);
        } else if (temperature > 0) {
            line = String.format(Locale.US, "Monitor ativo · bateria %.1f °C", temperature);
        } else {
            line = "Monitor ativo · temperatura indisponível";
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(line));
    }

    private Notification buildNotification(String message) {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("X1 Monitor térmico")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "X1 Monitor térmico", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Leituras da bateria e do estado térmico do Android");
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    @Override public void onDestroy() {
        isMonitoring = false;
        handler.removeCallbacks(monitorRunnable);
        if (Build.VERSION.SDK_INT >= 29 && powerManager != null && thermalListener != null) {
            try { powerManager.removeThermalStatusChangedListener(thermalListener); }
            catch (Exception ignored) { }
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        sendBroadcast(new Intent(ACTION_MONITOR_STOPPED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
