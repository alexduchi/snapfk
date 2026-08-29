package com.neo.tracepilot;
import android.content.*;import android.os.Build;
public final class BootReceiver extends BroadcastReceiver{public void onReceive(Context c,Intent i){try{if(!c.getSharedPreferences("tracepilot",Context.MODE_PRIVATE).getBoolean("tracking_enabled",false))return;Intent s=new Intent(c,TrackingService.class).setAction(TrackingService.START);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);}catch(Throwable ignored){}}}
