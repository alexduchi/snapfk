package com.sentry.local;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

public class EmergencyVpnService extends VpnService {
    private static volatile boolean running;
    private ParcelFileDescriptor tunnel;

    public static boolean isRunning() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Intent open = new Intent(this, V20Activity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 20, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "sentry_lockdown") : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("Sentry : confinement réseau actif")
                .setContentText("Le trafic Internet du téléphone est bloqué.").setOngoing(true).setContentIntent(pi).setColor(Color.rgb(245, 98, 124));
        startForeground(2020, b.build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (tunnel == null) {
            try {
                Builder builder = new Builder().setSession("Sentry - Confinement réseau").setMtu(1500)
                        .addAddress("10.20.0.1", 32).addRoute("0.0.0.0", 0);
                tunnel = builder.establish();
                running = tunnel != null;
            } catch (Exception e) {
                running = false;
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        try { if (tunnel != null) tunnel.close(); } catch (Exception ignored) { }
        tunnel = null;
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel("sentry_lockdown", "Confinement réseau Sentry", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Notification persistante du bouclier réseau local.");
            NotificationManager n = getSystemService(NotificationManager.class); if (n != null) n.createNotificationChannel(c);
        }
    }
}