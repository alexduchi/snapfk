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
    private GestureDescription.StrokeDescription dragStroke;
    private float dragX,dragY,pendingDragX,pendingDragY;
    private boolean dragDispatching,dragEndRequested,hasPendingDrag;

    @Override public void onServiceConnected(){super.onServiceConnected();main=new Handler(Looper.getMainLooper());instance=this;connectedAt=SystemClock.uptimeMillis();lastGestureStatus="READY";}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){lastGestureStatus="INTERRUPTED";}
    @Override public boolean onUnbind(Intent intent){if(instance==this)instance=null;lastGestureStatus="UNBOUND";return super.onUnbind(intent);}
    @Override public void onDestroy(){if(instance==this)instance=null;lastGestureStatus="DESTROYED";super.onDestroy();}

    public static boolean isReady(){return instance!=null;}
    public static long connectedAt(){return connectedAt;}
    public static String lastGestureStatus(){return lastGestureStatus;}

    public static boolean isEnabled(Context context){
        if(context==null)return false;
        try{
            int enabled=Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0);if(enabled!=1)return false;
            String value=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(value==null)return false;
            ComponentName wanted=new ComponentName(context,AirAccessibilityService.class);TextUtils.SimpleStringSplitter splitter=new TextUtils.SimpleStringSplitter(':');splitter.setString(value);
            while(splitter.hasNext()){ComponentName c=ComponentName.unflattenFromString(splitter.next());if(wanted.equals(c))return true;}
        }catch(Exception ignored){}
        return false;
    }
    public static String state(Context context){if(instance!=null)return "READY";return isEnabled(context)?"ENABLED_WAIT":"OFF";}

    public static boolean perform(GestureCommand command){
        AirAccessibilityService service=instance;
        if(service==null||command==null||command==GestureCommand.NONE){lastGestureStatus="SERVICE_OFF";return false;}
        Handler h=service.main;if(h==null){lastGestureStatus="NO_HANDLER";return false;}
        h.post(()->service.dispatchInternal(command,0));return true;
    }

    public static boolean tapNormalized(float nx,float ny){
        AirAccessibilityService s=instance;if(s==null||s.main==null){lastGestureStatus="SERVICE_OFF";return false;}
        s.main.post(()->s.tapInternal(nx,ny));return true;
    }
    public static boolean dragStartNormalized(float nx,float ny){
        AirAccessibilityService s=instance;if(s==null||s.main==null){lastGestureStatus="SERVICE_OFF";return false;}
        s.main.post(()->s.dragStartInternal(nx,ny));return true;
    }
    public static boolean dragMoveNormalized(float nx,float ny){
        AirAccessibilityService s=instance;if(s==null||s.main==null){lastGestureStatus="SERVICE_OFF";return false;}
        s.main.post(()->s.dragMoveInternal(nx,ny));return true;
    }
    public static boolean dragEndNormalized(float nx,float ny){
        AirAccessibilityService s=instance;if(s==null||s.main==null){lastGestureStatus="SERVICE_OFF";return false;}
        s.main.post(()->s.dragEndInternal(nx,ny));return true;
    }

    private float[] screen(float nx,float ny){
        DisplayMetrics dm=new DisplayMetrics();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        return new float[]{clamp(nx,0f,1f)*dm.widthPixels,clamp(ny,0f,1f)*dm.heightPixels};
    }

    private void tapInternal(float nx,float ny){
        if(gestureBusy||dragStroke!=null){lastGestureStatus="POINTER_BUSY";return;}
        float[] p=screen(nx,ny);Path path=new Path();path.moveTo(p[0],p[1]);
        GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,70)).build();
        gestureBusy=true;lastGestureStatus="CLICK_SENDING";
        boolean ok=dispatchGesture(g,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription d){gestureBusy=false;lastGestureStatus="CLICK_OK";}
            @Override public void onCancelled(GestureDescription d){gestureBusy=false;lastGestureStatus="CLICK_CANCEL";}
        },main);
        if(!ok){gestureBusy=false;lastGestureStatus="CLICK_REJECTED";}
    }

    private void dragStartInternal(float nx,float ny){
        if(gestureBusy||dragStroke!=null)return;
        float[] p=screen(nx,ny);dragX=p[0];dragY=p[1];pendingDragX=dragX;pendingDragY=dragY;dragEndRequested=false;hasPendingDrag=false;
        Path path=new Path();path.moveTo(dragX,dragY);path.lineTo(dragX,dragY);
        dragStroke=new GestureDescription.StrokeDescription(path,0,90,true);
        dispatchDragStroke(dragStroke,"DRAG_DOWN");
    }

    private void dragMoveInternal(float nx,float ny){
        if(dragStroke==null)return;float[] p=screen(nx,ny);pendingDragX=p[0];pendingDragY=p[1];hasPendingDrag=true;
        if(!dragDispatching)continueDrag(false);
    }

    private void dragEndInternal(float nx,float ny){
        if(dragStroke==null)return;float[] p=screen(nx,ny);pendingDragX=p[0];pendingDragY=p[1];hasPendingDrag=true;dragEndRequested=true;
        if(!dragDispatching)continueDrag(true);
    }

    private void continueDrag(boolean finish){
        if(dragStroke==null)return;
        float tx=hasPendingDrag?pendingDragX:dragX,ty=hasPendingDrag?pendingDragY:dragY;hasPendingDrag=false;
        Path path=new Path();path.moveTo(dragX,dragY);path.lineTo(tx,ty);
        try{
            GestureDescription.StrokeDescription next=dragStroke.continueStroke(path,0,70,!finish);
            dragX=tx;dragY=ty;dragStroke=next;dispatchDragStroke(next,finish?"DRAG_UP":"DRAG_MOVE");
        }catch(Exception e){dragStroke=null;dragDispatching=false;dragEndRequested=false;lastGestureStatus="DRAG_ERROR";}
    }

    private void dispatchDragStroke(GestureDescription.StrokeDescription stroke,String label){
        GestureDescription g=new GestureDescription.Builder().addStroke(stroke).build();dragDispatching=true;lastGestureStatus=label;
        boolean ok=dispatchGesture(g,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription d){
                dragDispatching=false;
                boolean finished=!stroke.willContinue();
                if(finished){dragStroke=null;dragEndRequested=false;hasPendingDrag=false;lastGestureStatus="DRAG_OK";}
                else if(dragEndRequested)continueDrag(true);
                else if(hasPendingDrag)continueDrag(false);
            }
            @Override public void onCancelled(GestureDescription d){dragDispatching=false;dragStroke=null;dragEndRequested=false;hasPendingDrag=false;lastGestureStatus="DRAG_CANCEL";}
        },main);
        if(!ok){dragDispatching=false;dragStroke=null;dragEndRequested=false;hasPendingDrag=false;lastGestureStatus="DRAG_REJECTED";}
    }

    private void dispatchInternal(GestureCommand command,int attempt){
        if(instance!=this){lastGestureStatus="SERVICE_LOST";return;}
        if(gestureBusy||dragStroke!=null){if(attempt<2)main.postDelayed(()->dispatchInternal(command,attempt+1),90);else lastGestureStatus="BUSY";return;}
        DisplayMetrics dm=new DisplayMetrics();WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);wm.getDefaultDisplay().getRealMetrics(dm);
        float cx=dm.widthPixels*.50f,cy=dm.heightPixels*.54f,dx=dm.widthPixels*.36f,dy=dm.heightPixels*.31f,x1=cx,y1=cy,x2=cx,y2=cy;
        switch(command){case LEFT:x1=cx+dx;x2=cx-dx;break;case RIGHT:x1=cx-dx;x2=cx+dx;break;case UP:y1=cy+dy;y2=cy-dy;break;case DOWN:y1=cy-dy;y2=cy+dy;break;default:return;}
        Path path=new Path();path.moveTo(x1,y1);path.lineTo(x2,y2);
        GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,300)).build();
        gestureBusy=true;lastGestureStatus="SENDING_"+command.name();
        boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gd){gestureBusy=false;lastGestureStatus="OK_"+command.name();}
            @Override public void onCancelled(GestureDescription gd){gestureBusy=false;lastGestureStatus="CANCEL_"+command.name();if(attempt<1)main.postDelayed(()->dispatchInternal(command,attempt+1),120);}
        },main);
        if(!accepted){gestureBusy=false;lastGestureStatus="REJECTED_"+command.name();if(attempt<1)main.postDelayed(()->dispatchInternal(command,attempt+1),120);}
    }

    private static float clamp(float v,float lo,float hi){return v<lo?lo:(v>hi?hi:v);}
}
