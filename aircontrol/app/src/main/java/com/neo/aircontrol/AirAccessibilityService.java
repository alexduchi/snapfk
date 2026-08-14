package com.neo.aircontrol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public final class AirAccessibilityService extends AccessibilityService {
    private static volatile AirAccessibilityService instance;
    @Override public void onServiceConnected(){instance=this;}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
    public static boolean isReady(){return instance!=null;}
    public static boolean perform(GestureCommand c){
        AirAccessibilityService s=instance;if(s==null||c==GestureCommand.NONE)return false;
        WindowManager wm=(WindowManager)s.getSystemService(WINDOW_SERVICE);DisplayMetrics dm=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);
        float cx=dm.widthPixels*.5f,cy=dm.heightPixels*.52f,dx=dm.widthPixels*.30f,dy=dm.heightPixels*.25f,x1=cx,y1=cy,x2=cx,y2=cy;
        switch(c){case LEFT:x1=cx+dx;x2=cx-dx;break;case RIGHT:x1=cx-dx;x2=cx+dx;break;case UP:y1=cy+dy;y2=cy-dy;break;case DOWN:y1=cy-dy;y2=cy+dy;break;default:return false;}
        Path p=new Path();p.moveTo(x1,y1);p.lineTo(x2,y2);GestureDescription.Builder b=new GestureDescription.Builder();b.addStroke(new GestureDescription.StrokeDescription(p,0,230));return s.dispatchGesture(b.build(),null,null);
    }
}
