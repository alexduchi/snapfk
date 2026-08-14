package com.neo.aircontrol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public final class AirAccessibilityService extends AccessibilityService {
    private static volatile AirAccessibilityService instance;
    private static volatile String lastGestureStatus="OFF";
    private static volatile long connectedAt;

    private Handler main;
    private boolean gestureBusy;

    @Override public void onServiceConnected(){
        super.onServiceConnected();
        main=new Handler(Looper.getMainLooper());
        instance=this;
        connectedAt=SystemClock.uptimeMillis();
        lastGestureStatus="READY";
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){lastGestureStatus="INTERRUPTED";}

    @Override public boolean onUnbind(Intent intent){
        if(instance==this)instance=null;
        lastGestureStatus="UNBOUND";
        return super.onUnbind(intent);
    }

    @Override public void onDestroy(){
        if(instance==this)instance=null;
        lastGestureStatus="DESTROYED";
        super.onDestroy();
    }

    public static boolean isReady(){return instance!=null;}
    public static long connectedAt(){return connectedAt;}
    public static String lastGestureStatus(){return lastGestureStatus;}

    public static boolean isEnabled(Context context){
        if(context==null)return false;
        try{
            int enabled=Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0);
            if(enabled!=1)return false;
            String value=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(value==null)return false;
            ComponentName wanted=new ComponentName(context,AirAccessibilityService.class);
            TextUtils.SimpleStringSplitter splitter=new TextUtils.SimpleStringSplitter(':');
            splitter.setString(value);
            while(splitter.hasNext()){
                ComponentName c=ComponentName.unflattenFromString(splitter.next());
                if(wanted.equals(c))return true;
            }
        }catch(Exception ignored){}
        return false;
    }

    public static String state(Context context){
        if(instance!=null)return "READY";
        return isEnabled(context)?"ENABLED_WAIT":"OFF";
    }

    public static boolean perform(GestureCommand command){
        AirAccessibilityService service=instance;
        if(service==null||command==null||command==GestureCommand.NONE){lastGestureStatus="SERVICE_OFF";return false;}
        Handler h=service.main;
        if(h==null){lastGestureStatus="NO_HANDLER";return false;}
        h.post(()->service.dispatchInternal(command,0));
        return true;
    }

    private void dispatchInternal(GestureCommand command,int attempt){
        if(instance!=this){lastGestureStatus="SERVICE_LOST";return;}
        if(gestureBusy){
            if(attempt<2)main.postDelayed(()->dispatchInternal(command,attempt+1),90);
            else lastGestureStatus="BUSY";
            return;
        }

        DisplayMetrics dm=new DisplayMetrics();
        WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(dm);
        float cx=dm.widthPixels*.50f,cy=dm.heightPixels*.54f;
        float dx=dm.widthPixels*.36f,dy=dm.heightPixels*.31f;
        float x1=cx,y1=cy,x2=cx,y2=cy;
        switch(command){
            case LEFT:x1=cx+dx;x2=cx-dx;break;
            case RIGHT:x1=cx-dx;x2=cx+dx;break;
            case UP:y1=cy+dy;y2=cy-dy;break;
            case DOWN:y1=cy-dy;y2=cy+dy;break;
            default:return;
        }

        Path path=new Path();path.moveTo(x1,y1);path.lineTo(x2,y2);
        GestureDescription gesture=new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path,0,300))
                .build();

        gestureBusy=true;
        lastGestureStatus="SENDING_"+command.name();
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gd){
                gestureBusy=false;
                lastGestureStatus="OK_"+command.name();
            }
            @Override public void onCancelled(GestureDescription gd){
                gestureBusy=false;
                lastGestureStatus="CANCEL_"+command.name();
                if(attempt<1)main.postDelayed(()->dispatchInternal(command,attempt+1),120);
            }
        },main);
        if(!accepted){
            gestureBusy=false;
            lastGestureStatus="REJECTED_"+command.name();
            if(attempt<1)main.postDelayed(()->dispatchInternal(command,attempt+1),120);
        }
    }
}
