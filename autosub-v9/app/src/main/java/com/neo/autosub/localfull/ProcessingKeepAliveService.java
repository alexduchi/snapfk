package com.neo.autosub.localfull;

import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class ProcessingKeepAliveService extends Service {
    public static final String CHANNEL_ID = "autosub_processing";
    public static final int NOTIFICATION_ID = 9011;
    public static final String ACTION_UPDATE = "com.neo.autosub.localfull.UPDATE_PROCESSING";
    public static final String EXTRA_TEXT = "text";

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Traitement AutoSub",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Garde le téléchargement, Whisper, la traduction et l'export actifs en arrière-plan.");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String text = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
        if (text == null || text.isEmpty()) text = "Traitement AutoSub en cours…";
        Notification n = buildNotification(text);
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(NOTIFICATION_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
        return START_STICKY;
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("AutoSub Local V11")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
