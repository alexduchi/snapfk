package com.sentry.local;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

/** Emergency local kill-switch. No packet is forwarded or inspected. */
public class EmergencyVpnService extends VpnService {
    public static final String ACTION_START = "com.sentry.local.VPN_START";
    public static final String ACTION_STOP = "com.sentry.local.VPN_STOP";
    private static final String PREFS = "sentry_vpn_state";
    private static final String KEY_REQUESTED = "requested";
    private static volatile boolean running;
    private ParcelFileDescriptor tunnel;
    private boolean shuttingDown;

    public static boolean isRunning() { return running; }
    public static boolean isRequested(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REQUESTED, false);
    }

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_REQUESTED, true).apply();
        Intent service = new Intent(app, EmergencyVpnService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(service); else app.startService(service);
    }

    public static void stop(Context context) {
        Context app = context.getApplicationContext();
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_REQUESTED, false).apply();
        Intent command = new Intent(app, EmergencyVpnService.class).setAction(ACTION_STOP);
        try { app.startService(command); } catch (Exception ignored) { }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { app.stopService(new Intent(app, EmergencyVpnService.class)); } catch (Exception ignored) { }
        }, 350L);
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(2020, notification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action) || !isRequested(this)) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            shutdown();
            return START_NOT_STICKY;
        }
        establishTunnel();
        return START_NOT_STICKY;
    }

    private void establishTunnel() {
        if (tunnel != null || shuttingDown) return;
        try {
            Builder builder = new Builder()
                    .setSession("Sentry - Confinement réseau")
                    .setMtu(1500)
                    .addAddress("10.24.0.1", 32)
                    .addRoute("0.0.0.0", 0)
                    .addAddress("fd00:24::1", 128)
                    .addRoute("::", 0);
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false);
            tunnel = builder.establish();
            running = tunnel != null;
            if (!running) shutdown();
        } catch (Exception e) {
            shutdown();
        }
    }

    private synchronized void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_REQUESTED, false).commit();
        running = false;
        ParcelFileDescriptor old = tunnel;
        tunnel = null;
        if (old != null) try { old.close(); } catch (Exception ignored) { }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(2020);
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    @Override public void onRevoke() {
        shutdown();
        super.onRevoke();
    }

    @Override public void onDestroy() {
        if (!shuttingDown) shutdown();
        running = false;
        super.onDestroy();
    }

    private Notification notification() {
        Intent open = new Intent(this, V24Activity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 20, open, PendingIntent.FLAG_UPDATE_CURRENT | immutable());
        Intent stop = new Intent(this, EmergencyVpnService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 21, stop, PendingIntent.FLAG_UPDATE_CURRENT | immutable());
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "sentry_lockdown") : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Sentry : confinement réseau actif")
                .setContentText("IPv4 et IPv6 sont bloqués. Appuie sur Désactiver pour libérer le réseau.")
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openPi)
                .setColor(Color.rgb(245, 98, 124))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Désactiver", stopPi)
                .build();
    }

    private int immutable() { return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("sentry_lockdown", "Confinement réseau Sentry", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("État du bouclier réseau local et bouton d'arrêt.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
