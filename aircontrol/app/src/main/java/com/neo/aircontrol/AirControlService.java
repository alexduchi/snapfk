package com.neo.aircontrol;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.IOException;

@SuppressWarnings("deprecation")
public final class AirControlService extends Service implements Camera.PreviewCallback {
    public static final String ACTION_STOP="com.neo.aircontrol.STOP", ACTION_CALIBRATE="com.neo.aircontrol.CALIBRATE";
    private static final String CHANNEL="aircontrol_camera";
    private Camera camera; private int fw,fh; private SurfaceTexture tex; private WindowManager wm; private LinearLayout panel; private TextView bubble,status; private WindowManager.LayoutParams params;
    private final MotionGestureDetector detector=new MotionGestureDetector(); private volatile boolean calibrateNext=true; private long lastUi;
    @Override public void onCreate(){super.onCreate();createChannel();startForeground(42,notification("AirControl actif — caméra locale"));startCamera();if(Settings.canDrawOverlays(this))showOverlay();}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null){if(ACTION_STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}if(ACTION_CALIBRATE.equals(i.getAction()))calibrateNext=true;}return START_STICKY;}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"AirControl camera",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("AirControl").setContentText(text).setOngoing(true).setContentIntent(pi).build();}
    private int front(){Camera.CameraInfo info=new Camera.CameraInfo();for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT)return i;}return-1;}
    private void startCamera(){int id=front();if(id<0){stopSelf();return;}try{camera=Camera.open(id);Camera.Parameters p=camera.getParameters();Camera.Size chosen=null;for(Camera.Size s:p.getSupportedPreviewSizes())if(chosen==null||Math.abs(s.width-640)+Math.abs(s.height-480)<Math.abs(chosen.width-640)+Math.abs(chosen.height-480))chosen=s;if(chosen!=null)p.setPreviewSize(chosen.width,chosen.height);p.setPreviewFormat(android.graphics.ImageFormat.NV21);camera.setParameters(p);Camera.Size a=camera.getParameters().getPreviewSize();fw=a.width;fh=a.height;tex=new SurfaceTexture(0);camera.setPreviewTexture(tex);camera.setPreviewCallback(this);camera.startPreview();}catch(RuntimeException|IOException e){stopSelf();}}
    @Override public void onPreviewFrame(byte[] data,Camera c){if(data==null||fw<=0)return;long now=SystemClock.elapsedRealtime();if(calibrateNext){detector.calibrate(data,fw,fh);calibrateNext=false;setStatus("Calibré · montre ✌ puis bouge");return;}GestureCommand cmd=detector.process(data,fw,fh,now);if(cmd!=GestureCommand.NONE){boolean ok=AirAccessibilityService.perform(cmd);setStatus((ok?"Geste : ":"Accessibilité inactive · ")+cmd.name());}else if(now-lastUi>220){MotionGestureDetector.DebugState d=detector.debug();setStatus(d.armed?"Armé · bouge franchement":(d.twoFingerGate?"✌ détecté · verrouillage":"En attente de ✌"));}}
    private void showOverlay(){wm=(WindowManager)getSystemService(WINDOW_SERVICE);params=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.END;params.x=6;params.y=180;panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(8),dp(8),dp(8),dp(8));panel.setBackgroundColor(0xDD111318);bubble=new TextView(this);bubble.setText("AC");bubble.setTextColor(Color.WHITE);bubble.setTextSize(15);bubble.setGravity(Gravity.CENTER);bubble.setBackgroundColor(0xFF2A2E36);panel.addView(bubble,new LinearLayout.LayoutParams(dp(46),dp(46)));status=new TextView(this);status.setText("AirControl ON");status.setTextColor(Color.WHITE);status.setTextSize(12);status.setPadding(0,dp(7),0,dp(5));status.setVisibility(View.GONE);panel.addView(status,new LinearLayout.LayoutParams(dp(190),-2));Button cal=new Button(this);cal.setText("Calibrer");cal.setVisibility(View.GONE);panel.addView(cal,new LinearLayout.LayoutParams(dp(190),dp(46)));Button off=new Button(this);off.setText("OFF");off.setVisibility(View.GONE);panel.addView(off,new LinearLayout.LayoutParams(dp(190),dp(46)));bubble.setOnClickListener(v->{boolean o=status.getVisibility()!=View.VISIBLE;status.setVisibility(o?View.VISIBLE:View.GONE);cal.setVisibility(o?View.VISIBLE:View.GONE);off.setVisibility(o?View.VISIBLE:View.GONE);});cal.setOnClickListener(v->{calibrateNext=true;setStatus("Retire ta main…");});off.setOnClickListener(v->stopSelf());wm.addView(panel,params);}
    private void setStatus(String s){lastUi=SystemClock.elapsedRealtime();if(status!=null)status.post(()->status.setText(s));}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onDestroy(){if(camera!=null){try{camera.setPreviewCallback(null);camera.stopPreview();camera.release();}catch(Exception ignored){}camera=null;}if(tex!=null)tex.release();if(wm!=null&&panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
