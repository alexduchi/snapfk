package com.neo.aircontrol;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public final class AirControlService extends Service implements Camera.PreviewCallback, MediaPipeHandTracker.Listener {
    public static final String ACTION_STOP="com.neo.aircontrol.STOP";
    public static final String ACTION_RESET="com.neo.aircontrol.RESET";
    public static final String ACTION_CALIBRATE=ACTION_RESET;
    private static final String CHANNEL="aircontrol_camera";
    private static final long FRAME_INTERVAL_MS=48;
    private static final long SWIPE_ACTION_LOCK_MS=900;
    private static final String PREFS="aircontrol_settings";
    private static final String PREF_POINTER_SENS="pointer_sensitivity";

    private final Handler ui=new Handler(Looper.getMainLooper());
    private Camera camera;
    private int fw,fh,cameraId=-1,rotation;
    private SurfaceTexture texture;
    private MediaPipeHandTracker tracker;
    private final LandmarkGestureEngine engine=new LandmarkGestureEngine();
    private final RemotePointerEngine pointerEngine=new RemotePointerEngine();
    private final VolumeGestureEngine volumeEngine=new VolumeGestureEngine();
    private AudioManager audio;
    private Bitmap frameBitmap;
    private int[] rgb;
    private int outW,outH;
    private long lastFrameSubmit,lastUi,fpsWindow,lastSwipeActionMs;
    private int resultFrames;
    private float mlFps;
    private volatile long inferenceMs;
    private volatile String mlError;
    private volatile String cursorError="CURSOR_READY";
    private WindowManager wm;
    private LinearLayout panel;
    private TextView bubble,status,sensitivityLabel;
    private SeekBar sensitivityBar;
    private View cursor,cursorInner,cursorDot;
    private WindowManager.LayoutParams cursorLp;
    private boolean cursorShown;

    @Override public void onCreate(){
        super.onCreate();
        float saved=getSharedPreferences(PREFS,MODE_PRIVATE).getFloat(PREF_POINTER_SENS,1.15f);
        pointerEngine.setSensitivity(saved);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        createChannel();startForeground(42,notification("AirControl V7.3 · inverted mouse + click + volume"));
        tracker=new MediaPipeHandTracker(this,this);startCamera();
        if(Settings.canDrawOverlays(this)){showOverlay();createCursor();}
        fpsWindow=SystemClock.elapsedRealtime();
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null){
            if(ACTION_STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
            if(ACTION_RESET.equals(i.getAction())){engine.reset();pointerEngine.reset();volumeEngine.reset();hideCursor();setStatus("Tracking réinitialisé");}
        }
        return START_STICKY;
    }

    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"AirControl camera",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("AirControl V7.3").setContentText(text).setOngoing(true).setContentIntent(pi).build();}

    private int front(){Camera.CameraInfo info=new Camera.CameraInfo();for(int i=0;i<Camera.getNumberOfCameras();i++){Camera.getCameraInfo(i,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT)return i;}return -1;}
    private int displayDegrees(){int r=((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation();switch(r){case Surface.ROTATION_90:return 90;case Surface.ROTATION_180:return 180;case Surface.ROTATION_270:return 270;default:return 0;}}
    private int analysisRotationForFront(int id){Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(id,info);int result=(info.orientation+displayDegrees())%360;return (360-result)%360;}

    private void startCamera(){cameraId=front();if(cameraId<0){mlError="aucune caméra avant";stopSelf();return;}try{camera=Camera.open(cameraId);Camera.Parameters p=camera.getParameters();Camera.Size chosen=chooseSize(p.getSupportedPreviewSizes());if(chosen!=null)p.setPreviewSize(chosen.width,chosen.height);p.setPreviewFormat(android.graphics.ImageFormat.NV21);List<String> focus=p.getSupportedFocusModes();if(focus!=null&&focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);camera.setParameters(p);Camera.Size a=camera.getParameters().getPreviewSize();fw=a.width;fh=a.height;rotation=analysisRotationForFront(cameraId);if(rotation==90||rotation==270){outW=270;outH=360;}else{outW=360;outH=270;}rgb=new int[outW*outH];frameBitmap=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);texture=new SurfaceTexture(0);camera.setPreviewTexture(texture);camera.setPreviewCallbackWithBuffer(this);int bufferSize=fw*fh*android.graphics.ImageFormat.getBitsPerPixel(android.graphics.ImageFormat.NV21)/8;camera.addCallbackBuffer(new byte[bufferSize]);camera.addCallbackBuffer(new byte[bufferSize]);camera.addCallbackBuffer(new byte[bufferSize]);camera.startPreview();}catch(RuntimeException|IOException e){mlError="caméra: "+e.getClass().getSimpleName();stopSelf();}}
    private Camera.Size chooseSize(List<Camera.Size> sizes){if(sizes==null||sizes.isEmpty())return null;Camera.Size best=null;long bestScore=Long.MAX_VALUE;for(Camera.Size s:sizes){long area=(long)s.width*s.height;long score=Math.abs(s.width-640L)*3+Math.abs(s.height-480L)*3+Math.abs(area-307200L)/200;if(score<bestScore){bestScore=score;best=s;}}return best;}

    @Override public void onPreviewFrame(byte[] data,Camera c){try{if(data==null||fw<=0||tracker==null||!tracker.ready()||tracker.busy())return;long now=SystemClock.uptimeMillis();if(now-lastFrameSubmit<FRAME_INTERVAL_MS)return;lastFrameSubmit=now;nv21ToUprightRgb(data,fw,fh);frameBitmap.setPixels(rgb,0,outW,0,0,outW,outH);tracker.submit(frameBitmap);}finally{if(c!=null&&data!=null)try{c.addCallbackBuffer(data);}catch(Exception ignored){}}}
    private void nv21ToUprightRgb(byte[] a,int w,int h){final int frameSize=w*h;for(int oy=0;oy<outH;oy++){float v=(oy+.5f)/outH;for(int ox=0;ox<outW;ox++){float u=(ox+.5f)/outW;u=1f-u;float rx,ry;switch(rotation){case 90:rx=v;ry=1f-u;break;case 180:rx=1f-u;ry=1f-v;break;case 270:rx=1f-v;ry=u;break;default:rx=u;ry=v;}int sx=clamp((int)(rx*w),0,w-1),sy=clamp((int)(ry*h),0,h-1);int yv=a[sy*w+sx]&255;int uv=frameSize+(sy>>1)*w+(sx&~1);int vv=(uv<a.length?a[uv]&255:128)-128,uu=(uv+1<a.length?a[uv+1]&255:128)-128;int cc=Math.max(0,yv-16);int r=clamp((298*cc+409*vv+128)>>8,0,255),g=clamp((298*cc-100*uu-208*vv+128)>>8,0,255),b=clamp((298*cc+516*uu+128)>>8,0,255);rgb[oy*outW+ox]=0xFF000000|(r<<16)|(g<<8)|b;}}}
    private static int clamp(int x,int lo,int hi){return x<lo?lo:x>hi?hi:x;}

    @Override public void onHands(float[][] xs,float[][] ys,float[][] zs,long timestampMs,long inference){inferenceMs=inference;tickFps();RemotePointerEngine.Result pr=pointerEngine.process(xs,ys,zs,timestampMs);if(pr.active||pr.blockSwipes){volumeEngine.reset();engine.reset();handlePointer(pr);updatePointerDebug(pr,xs==null?0:xs.length);return;}VolumeGestureEngine.Result vr=volumeEngine.process(xs,ys,zs,timestampMs);if(vr.active||vr.blockOther){pointerEngine.reset();engine.reset();hideCursor();handleVolume(vr);updateVolumeDebug(vr);return;}hideCursor();if(xs==null||xs.length==0){engine.onNoHand(timestampMs);updateDebug();return;}GestureCommand cmd=engine.process(xs[0],ys[0],zs[0],timestampMs);if(cmd!=GestureCommand.NONE){long now=SystemClock.uptimeMillis();if(now-lastSwipeActionMs<SWIPE_ACTION_LOCK_MS){setStatus("SWIPE ignoré · délai sécurité\n"+(SWIPE_ACTION_LOCK_MS-(now-lastSwipeActionMs))+" ms");return;}lastSwipeActionMs=now;boolean ok=AirAccessibilityService.perform(cmd);bubbleState(ok?2:4);setStatus((ok?"SWIPE ":"ACCESSIBILITÉ OFF · ")+cmd.name()+"\nverrou "+SWIPE_ACTION_LOCK_MS+" ms · "+AirAccessibilityService.lastGestureStatus());}else updateDebug();}

    private void handlePointer(RemotePointerEngine.Result pr){if(!pr.active){hideCursor();return;}showCursor(pr.x,pr.y,pr.pinching);if(pr.tap){AirAccessibilityService.tapNormalized(pr.x,pr.y);bubbleState(7);}if(pr.dragStart){AirAccessibilityService.dragStartNormalized(pr.x,pr.y);bubbleState(8);}else if(pr.dragMove){AirAccessibilityService.dragMoveNormalized(pr.x,pr.y);bubbleState(8);}if(pr.dragEnd){AirAccessibilityService.dragEndNormalized(pr.x,pr.y);bubbleState(7);}}
    private void handleVolume(VolumeGestureEngine.Result vr){if(audio==null)return;if(vr.volumeUp){audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,AudioManager.FLAG_SHOW_UI);bubbleState(9);}else if(vr.volumeDown){audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);bubbleState(9);}}

    @Override public void onNoHand(long timestampMs,long inference){inferenceMs=inference;tickFps();RemotePointerEngine.Result pr=pointerEngine.onNoHands(timestampMs);if(pr.active||pr.blockSwipes){volumeEngine.reset();handlePointer(pr);updatePointerDebug(pr,0);return;}VolumeGestureEngine.Result vr=volumeEngine.onNoHands(timestampMs);if(vr.active||vr.blockOther){hideCursor();handleVolume(vr);updateVolumeDebug(vr);return;}hideCursor();engine.onNoHand(timestampMs);updateDebug();}
    @Override public void onError(String message){mlError=message;setStatus("ML erreur\n"+message);bubbleState(3);}
    private void tickFps(){long now=SystemClock.uptimeMillis();resultFrames++;if(now-fpsWindow>=1000){mlFps=resultFrames*1000f/Math.max(1,now-fpsWindow);resultFrames=0;fpsWindow=now;}}

    private void updatePointerDebug(RemotePointerEngine.Result pr,int hands){long now=SystemClock.uptimeMillis();if(now-lastUi<100)return;bubbleState(pr.dragging?8:(pr.active?6:1));String text=String.format(Locale.US,"MODE SOURIS · %s\nMains %d · paire %s · pinch %.2f\nCurseur %.2f / %.2f · sens %.2fx\n%s · %s · ACC %s",pr.state,hands,pr.pairFound?"oui":"non",pr.pinchRatio,pr.x,pr.y,pointerEngine.getSensitivity(),cursorError,pr.dragging?"DRAG":(pr.pinching?"CLICK ARMÉ":"MOVE"),AirAccessibilityService.lastGestureStatus());setStatus(text);}
    private void updateVolumeDebug(VolumeGestureEngine.Result vr){long now=SystemClock.uptimeMillis();if(now-lastUi<100)return;bubbleState(9);String text=String.format(Locale.US,"MODE VOLUME · %s\n3 doigts %s · y %.2f · delta %.3f\nmain vers haut = volume +\nmain vers bas = volume -",vr.state,vr.poseFound?"oui":"mémoire",vr.handY,vr.deltaY);setStatus(text);}
    private void updateDebug(){long now=SystemClock.uptimeMillis();if(now-lastUi<140)return;LandmarkGestureEngine.DebugState d=engine.debug();boolean acc=AirAccessibilityService.isReady();int s=!acc?4:(d.handFound?1:(d.grace?5:0));bubbleState(s);String ml=mlError!=null?"ERR":"OK";long lock=Math.max(0,SWIPE_ACTION_LOCK_MS-(now-lastSwipeActionMs));String text=String.format(Locale.US,"MediaPipe %s · %.1f fps · %d ms\nMAIN %s · tracking %s%s\n%s · v %.2f · d %.2f · coh %.0f%%\nlock %d ms · sens souris %.2fx\nACC %s · %s",ml,mlFps,inferenceMs,d.handFound?"oui":"non",d.tracking?"oui":"non",d.grace?" · mémoire":"",d.state,d.speed,d.displacement,d.coherence*100f,lock,pointerEngine.getSensitivity(),AirAccessibilityService.state(this),AirAccessibilityService.lastGestureStatus());setStatus(text);}

    private GradientDrawable circle(int color){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color);return g;}
    private void createCursor(){if(Looper.myLooper()!=Looper.getMainLooper()){ui.post(this::createCursor);return;}if(cursor!=null)return;if(wm==null)wm=(WindowManager)getSystemService(WINDOW_SERVICE);FrameLayout holder=new FrameLayout(this);holder.setBackground(circle(0xFF101418));holder.setPadding(dp(3),dp(3),dp(3),dp(3));cursorInner=new View(this);GradientDrawable inner=new GradientDrawable();inner.setShape(GradientDrawable.OVAL);inner.setColor(0xFFFFFFFF);inner.setStroke(dp(3),0xFF19A7FF);cursorInner.setBackground(inner);FrameLayout.LayoutParams innerLp=new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.CENTER);holder.addView(cursorInner,innerLp);cursorDot=new View(this);cursorDot.setBackground(circle(0xFF005BFF));FrameLayout.LayoutParams dotLp=new FrameLayout.LayoutParams(dp(10),dp(10),Gravity.CENTER);holder.addView(cursorDot,dotLp);cursor=holder;cursorLp=new WindowManager.LayoutParams(dp(42),dp(42),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);cursorLp.gravity=Gravity.TOP|Gravity.LEFT;cursorLp.x=0;cursorLp.y=0;cursorError="CURSOR_CREATED";}
    private void showCursor(float nx,float ny,boolean pressed){final float x=nx,y=ny;final boolean down=pressed;ui.post(()->{try{if(!Settings.canDrawOverlays(this)){cursorError="CURSOR_NO_PERMISSION";return;}if(cursor==null||cursorLp==null)createCursor();if(cursor==null||cursorLp==null||wm==null){cursorError="CURSOR_NOT_CREATED";return;}DisplayMetrics dm=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);int size=dp(42),half=size/2;int px=(int)(x*dm.widthPixels),py=(int)(y*dm.heightPixels);cursorLp.x=clamp(px-half,0,Math.max(0,dm.widthPixels-size));cursorLp.y=clamp(py-half,0,Math.max(0,dm.heightPixels-size));if(cursorInner!=null){GradientDrawable inner=new GradientDrawable();inner.setShape(GradientDrawable.OVAL);inner.setColor(down?0xFFFFF4CC:0xFFFFFFFF);inner.setStroke(dp(3),down?0xFFFF8A00:0xFF19A7FF);cursorInner.setBackground(inner);}if(cursorDot!=null)cursorDot.setBackground(circle(down?0xFFFF3D00:0xFF005BFF));cursor.setAlpha(1f);cursor.setVisibility(View.VISIBLE);if(!cursorShown){wm.addView(cursor,cursorLp);cursorShown=true;cursorError="CURSOR_VISIBLE";}else{wm.updateViewLayout(cursor,cursorLp);cursorError="CURSOR_VISIBLE";}}catch(Exception e){cursorShown=false;cursorError="CURSOR_ERR_"+e.getClass().getSimpleName();}});}
    private void hideCursor(){ui.post(()->{if(cursorShown&&wm!=null&&cursor!=null){try{wm.removeView(cursor);}catch(Exception ignored){}cursorShown=false;cursorError="CURSOR_HIDDEN";}});}

    private void showOverlay(){wm=(WindowManager)getSystemService(WINDOW_SERVICE);WindowManager.LayoutParams p=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.END;p.x=6;p.y=180;panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(8),dp(8),dp(8),dp(8));panel.setBackgroundColor(0xE6111318);bubble=new TextView(this);bubble.setText("AC\nAUTO");bubble.setTextColor(Color.WHITE);bubble.setTextSize(11);bubble.setGravity(Gravity.CENTER);bubble.setBackgroundColor(0xFF343840);panel.addView(bubble,new LinearLayout.LayoutParams(dp(56),dp(56)));status=new TextView(this);status.setText("Fast tracking + souris + volume...");status.setTextColor(Color.WHITE);status.setTextSize(12);status.setPadding(0,dp(7),0,dp(5));status.setVisibility(View.GONE);panel.addView(status,new LinearLayout.LayoutParams(dp(300),-2));sensitivityLabel=new TextView(this);sensitivityLabel.setTextColor(Color.WHITE);sensitivityLabel.setTextSize(12);sensitivityLabel.setPadding(0,dp(4),0,0);sensitivityLabel.setVisibility(View.GONE);panel.addView(sensitivityLabel,new LinearLayout.LayoutParams(dp(300),dp(30)));sensitivityBar=new SeekBar(this);sensitivityBar.setMax(120);sensitivityBar.setProgress(Math.round((pointerEngine.getSensitivity()-.60f)*100f));sensitivityBar.setVisibility(View.GONE);panel.addView(sensitivityBar,new LinearLayout.LayoutParams(dp(300),dp(44)));updateSensitivityLabel();sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){@Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser){float v=.60f+progress/100f;pointerEngine.setSensitivity(v);updateSensitivityLabel();if(fromUser)getSharedPreferences(PREFS,MODE_PRIVATE).edit().putFloat(PREF_POINTER_SENS,pointerEngine.getSensitivity()).apply();}@Override public void onStartTrackingTouch(SeekBar b){}@Override public void onStopTrackingTouch(SeekBar b){}});Button resetSens=new Button(this);resetSens.setText("Sensibilité souris par défaut");resetSens.setVisibility(View.GONE);panel.addView(resetSens,new LinearLayout.LayoutParams(dp(300),dp(46)));Button reset=new Button(this);reset.setText("Réinitialiser tracking");reset.setVisibility(View.GONE);panel.addView(reset,new LinearLayout.LayoutParams(dp(300),dp(46)));Button test=new Button(this);test.setText("Test swipe bas");test.setVisibility(View.GONE);panel.addView(test,new LinearLayout.LayoutParams(dp(300),dp(46)));Button access=new Button(this);access.setText("Ouvrir Accessibilité");access.setVisibility(View.GONE);panel.addView(access,new LinearLayout.LayoutParams(dp(300),dp(46)));Button off=new Button(this);off.setText("OFF");off.setVisibility(View.GONE);panel.addView(off,new LinearLayout.LayoutParams(dp(300),dp(46)));bubble.setOnClickListener(v->{boolean open=status.getVisibility()!=View.VISIBLE;int vis=open?View.VISIBLE:View.GONE;status.setVisibility(vis);sensitivityLabel.setVisibility(vis);sensitivityBar.setVisibility(vis);resetSens.setVisibility(vis);reset.setVisibility(vis);test.setVisibility(vis);access.setVisibility(vis);off.setVisibility(vis);});resetSens.setOnClickListener(v->{pointerEngine.setSensitivity(1.15f);sensitivityBar.setProgress(55);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putFloat(PREF_POINTER_SENS,1.15f).apply();updateSensitivityLabel();setStatus("Sensibilité souris remise à 1.15x");});reset.setOnClickListener(v->{engine.reset();pointerEngine.reset();volumeEngine.reset();hideCursor();setStatus("Tracking réinitialisé");});test.setOnClickListener(v->{long now=SystemClock.uptimeMillis();if(now-lastSwipeActionMs<SWIPE_ACTION_LOCK_MS){setStatus("Test bloqué par délai sécurité");return;}lastSwipeActionMs=now;boolean ok=AirAccessibilityService.perform(GestureCommand.DOWN);setStatus((ok?"Test DOWN demandé":"ACCESSIBILITÉ OFF")+"\n"+AirAccessibilityService.lastGestureStatus());bubbleState(ok?2:4);});access.setOnClickListener(v->{Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);});off.setOnClickListener(v->stopSelf());wm.addView(panel,p);}

    private void updateSensitivityLabel(){if(sensitivityLabel!=null)sensitivityLabel.setText(String.format(Locale.US,"Sensibilité souris : %.2fx",pointerEngine.getSensitivity()));}
    private void bubbleState(int s){if(bubble==null)return;final int bg;final String t;switch(s){case 1:bg=0xFF246B3B;t="AC\nTRACK";break;case 2:bg=0xFF176B77;t="AC\nSWIPE";break;case 3:bg=0xFF7A2525;t="AC\nML ERR";break;case 4:bg=0xFF8B1E1E;t="AC\nACC OFF";break;case 5:bg=0xFF675C20;t="AC\nHOLD";break;case 6:bg=0xFF384E8A;t="AC\nMOUSE";break;case 7:bg=0xFF5A3F8B;t="AC\nCLICK";break;case 8:bg=0xFF7A3C88;t="AC\nDRAG";break;case 9:bg=0xFF305D77;t="AC\nVOL";break;default:bg=0xFF343840;t="AC\nAUTO";}bubble.post(()->{bubble.setText(t);bubble.setBackgroundColor(bg);});}
    private void setStatus(String s){lastUi=SystemClock.uptimeMillis();if(status!=null)status.post(()->status.setText(s));}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onDestroy(){if(Looper.myLooper()==Looper.getMainLooper()){if(cursorShown&&wm!=null&&cursor!=null)try{wm.removeView(cursor);}catch(Exception ignored){}}if(camera!=null){try{camera.setPreviewCallbackWithBuffer(null);camera.stopPreview();camera.release();}catch(Exception ignored){}camera=null;}if(texture!=null)try{texture.release();}catch(Exception ignored){}if(tracker!=null)try{tracker.close();}catch(Exception ignored){}if(wm!=null&&panel!=null)try{wm.removeView(panel);}catch(Exception ignored){}if(frameBitmap!=null&&!frameBitmap.isRecycled())frameBitmap.recycle();stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
