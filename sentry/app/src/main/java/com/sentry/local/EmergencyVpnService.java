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
    public static final String ACTION_STOP="com.sentry.local.VPN_STOP";
    private static volatile boolean running;
    private ParcelFileDescriptor tunnel;
    public static boolean isRunning(){return running;}

    @Override public void onCreate(){super.onCreate();createChannel();startForeground(2020,notification());}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(tunnel==null){try{Builder b=new Builder().setSession("Sentry - Confinement réseau").setMtu(1500).addAddress("10.20.0.1",32).addRoute("0.0.0.0",0).addAddress("fd00:23::1",128).addRoute("::",0);if(Build.VERSION.SDK_INT>=29)b.setMetered(false);tunnel=b.establish();running=tunnel!=null;if(!running)stopSelf();}catch(Exception e){running=false;stopSelf();}}
        return START_STICKY;
    }
    @Override public void onDestroy(){running=false;try{if(tunnel!=null)tunnel.close();}catch(Exception ignored){}tunnel=null;stopForeground(true);super.onDestroy();}

    private Notification notification(){Intent open=new Intent(this,V23Activity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent op=PendingIntent.getActivity(this,20,open,PendingIntent.FLAG_UPDATE_CURRENT|immutable());Intent stop=new Intent(this,EmergencyVpnService.class).setAction(ACTION_STOP);PendingIntent sp=PendingIntent.getService(this,21,stop,PendingIntent.FLAG_UPDATE_CURRENT|immutable());Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"sentry_lockdown"):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("Sentry : confinement réseau actif").setContentText("IPv4 et IPv6 sont bloqués sur ce téléphone.").setOngoing(true).setOnlyAlertOnce(true).setContentIntent(op).setColor(Color.rgb(245,98,124)).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Désactiver",sp).build();}
    private int immutable(){return Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0;}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("sentry_lockdown","Confinement réseau Sentry",NotificationManager.IMPORTANCE_LOW);c.setDescription("Notification persistante du bouclier réseau local.");NotificationManager n=getSystemService(NotificationManager.class);if(n!=null)n.createNotificationChannel(c);}}
}
