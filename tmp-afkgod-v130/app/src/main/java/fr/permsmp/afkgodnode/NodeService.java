package fr.permsmp.afkgodnode;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

public class NodeService extends Service {
    private volatile boolean running=true; private Thread worker; private PowerManager.WakeLock wake;
    private static final String CH="afk_god_node";
    @Override public void onCreate(){super.onCreate();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CH,"AFK God Node",NotificationManager.IMPORTANCE_LOW));
        Notification n=new Notification.Builder(this,CH).setContentTitle("AFK God Node").setContentText("Standby host actif").setSmallIcon(android.R.drawable.stat_notify_sync).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(110,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE); else startForeground(110,n);
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE); wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AFKGodNode:heartbeat"); wake.setReferenceCounted(false); wake.acquire();
        worker=new Thread(this::loop,"afk-node-heartbeat");worker.start();
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){return START_STICKY;}
    private void loop(){while(running){try{heartbeat();}catch(Exception ignored){}try{Thread.sleep(5000);}catch(InterruptedException ignored){}}}
    private void heartbeat()throws Exception{
        NodeConfig cfg=NodeConfig.load(this); boolean discord=Net.probe("https://discord.com/api/v10/gateway");
        IntentFilter f=new IntentFilter(Intent.ACTION_BATTERY_CHANGED); Intent b=registerReceiver(null,f); int level=-1;boolean power=false;
        if(b!=null){int l=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),s=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1);if(l>=0&&s>0)level=Math.round(l*100f/s);int p=b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);power=p!=0;}
        boolean central=Net.probe(cfg.server+"/healthz");int score=central?78:0;if(central&&discord)score+=12;if(power)score+=8;else if(level>=0&&level<20)score-=18;if(score>100)score=100;if(score<0)score=0;
        Net.post(cfg.server+"/api/v1/nodes/heartbeat",Json.heartbeat(score,discord,power,level),cfg.nodeId+"."+cfg.secret);
    }
    @Override public void onDestroy(){running=false;if(worker!=null)worker.interrupt();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
