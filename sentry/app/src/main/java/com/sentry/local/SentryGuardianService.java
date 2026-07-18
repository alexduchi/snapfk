package com.sentry.local;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Local, defensive foreground watcher. It does not inspect encrypted traffic. */
public class SentryGuardianService extends Service {
    public static final String ACTION_START="com.sentry.local.GUARDIAN_START";
    public static final String ACTION_STOP="com.sentry.local.GUARDIAN_STOP";
    private static final String CHANNEL="sentry_guardian";
    private static volatile boolean running;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String previous="";

    public static boolean isRunning(){return running;}

    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("sentry_v23",MODE_PRIVATE);channel();running=true;previous=prefs.getString("network_baseline","");startForeground(2300,foreground("Surveillance locale active"));ui.post(loop);}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&ACTION_STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}running=true;ui.removeCallbacks(loop);ui.post(loop);return START_STICKY;}
    @Override public void onDestroy(){running=false;ui.removeCallbacksAndMessages(null);stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}

    private final Runnable loop=new Runnable(){@Override public void run(){try{audit();}catch(Exception ignored){}ui.postDelayed(this,15000L);}};

    private void audit(){
        State s=state();String signature=s.signature();
        if(!signature.isEmpty()){
            if(!previous.isEmpty()&&!previous.equals(signature)){record("Réseau modifié",previous+" -> "+signature);alert("Changement de réseau détecté",s.summary());}
            previous=signature;prefs.edit().putString("network_baseline",signature).apply();
        }
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);boolean secure=km!=null&&km.isDeviceSecure();int adb=global(Settings.Global.ADB_ENABLED);
        if(!secure&&!prefs.getBoolean("warn_lock",false)){prefs.edit().putBoolean("warn_lock",true).apply();record("Protection faible","Aucun verrouillage sécurisé");alert("Verrouillage recommandé","Active un code ou une biométrie dans Android.");}
        if(adb==1&&!prefs.getBoolean("warn_adb",false)){prefs.edit().putBoolean("warn_adb",true).apply();record("Débogage USB actif","ADB activé");alert("Débogage USB actif","Désactive ADB quand tu ne l'utilises pas.");}
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(2300,foreground("Réseau "+(s.connected?"actif":"hors ligne")+" · risque "+risk(s,secure,adb)));
    }

    private String risk(State s,boolean secure,int adb){int n=0;if(!secure)n+=2;if(adb==1)n+=2;if(s.connected&&!s.validated)n++;if(s.captive)n++;return n>=3?"élevé":n>=1?"modéré":"faible";}
    private Notification foreground(String text){Intent open=new Intent(this,V23Activity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent op=PendingIntent.getActivity(this,2300,open,PendingIntent.FLAG_UPDATE_CURRENT|immutable());Intent stop=new Intent(this,SentryGuardianService.class).setAction(ACTION_STOP);PendingIntent sp=PendingIntent.getService(this,2301,stop,PendingIntent.FLAG_UPDATE_CURRENT|immutable());Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("Sentry v23 · Gardien").setContentText(text).setColor(Color.rgb(61,235,211)).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(op).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Arrêter",sp).build();}
    private void alert(String title,String message){Intent open=new Intent(this,V23Activity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent p=PendingIntent.getActivity(this,title.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|immutable());Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);Notification n=b.setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setColor(Color.rgb(245,98,124)).setAutoCancel(true).setContentIntent(p).build();NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify((int)(System.currentTimeMillis()&0x7fffffff),n);}
    private void record(String title,String detail){String line=new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.FRANCE).format(new Date())+" · "+title+" · "+detail;String old=prefs.getString("guardian_log","");String all=line+(old.isEmpty()?"":"\n"+old);if(all.length()>30000)all=all.substring(0,30000);prefs.edit().putString("guardian_log",all).apply();}

    private State state(){State s=new State();try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);Network n=cm==null?null:cm.getActiveNetwork();NetworkCapabilities c=cm==null||n==null?null:cm.getNetworkCapabilities(n);s.connected=c!=null;s.validated=c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);s.captive=c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);s.transport=c==null?"hors ligne":c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"Cellulaire":c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"VPN":"Autre";WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);WifiInfo wi=wm==null?null:wm.getConnectionInfo();if(wi!=null){s.ssid=clean(wi.getSSID());s.bssid=clean(wi.getBSSID());}}catch(Exception ignored){}return s;}
    private int global(String k){try{return Settings.Global.getInt(getContentResolver(),k,0);}catch(Exception e){return 0;}}
    private String clean(String v){return v==null?"":v.replace("\"","").replace("<unknown ssid>","inconnu");}
    private int immutable(){return Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0;}
    private void channel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Gardien Sentry",NotificationManager.IMPORTANCE_DEFAULT);c.setDescription("Alertes défensives locales de Sentry v23");NotificationManager n=getSystemService(NotificationManager.class);if(n!=null)n.createNotificationChannel(c);}}
    private static final class State{boolean connected,validated,captive;String transport="hors ligne",ssid="",bssid="";String signature(){return connected?transport+"|"+ssid+"|"+bssid:"";}String summary(){return transport+" · "+(ssid.isEmpty()?"réseau inconnu":ssid)+" · Internet "+(validated?"validé":"non validé");}}
}
