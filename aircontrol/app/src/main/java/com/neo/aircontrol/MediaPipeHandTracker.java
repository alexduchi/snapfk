package com.neo.aircontrol;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Thin MediaPipe wrapper. All intent filtering/gesture logic stays outside the ML model. */
public final class MediaPipeHandTracker implements AutoCloseable {
    public interface Listener {
        void onLandmarks(float[] x,float[] y,float[] z,long timestampMs,long inferenceMs);
        void onNoHand(long timestampMs,long inferenceMs);
        void onError(String message);
    }

    private final Listener listener;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private HandLandmarker landmarker;
    private long lastSubmit;

    public MediaPipeHandTracker(Context context,Listener listener){
        this.listener=listener;
        setup(context);
    }

    private void setup(Context context){
        try{
            BaseOptions base=BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(Delegate.CPU)
                    .build();
            HandLandmarker.HandLandmarkerOptions options=HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.35f)
                    .setMinHandPresenceConfidence(0.35f)
                    .setMinTrackingConfidence(0.35f)
                    .setResultListener((result,input)->handleResult(result))
                    .setErrorListener(error->{busy.set(false);listener.onError(error.getMessage()==null?"MediaPipe error":error.getMessage());})
                    .build();
            landmarker=HandLandmarker.createFromOptions(context,options);
        }catch(RuntimeException e){
            listener.onError("MediaPipe init: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
        }
    }

    public boolean submit(Bitmap bitmap){
        if(landmarker==null||bitmap==null||!busy.compareAndSet(false,true))return false;
        long ts=SystemClock.uptimeMillis();
        if(ts<=lastSubmit)ts=lastSubmit+1;
        lastSubmit=ts;
        try{
            MPImage image=new BitmapImageBuilder(bitmap).build();
            landmarker.detectAsync(image,ts);
            return true;
        }catch(RuntimeException e){
            busy.set(false);listener.onError("MediaPipe detect: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));return false;
        }
    }

    private void handleResult(HandLandmarkerResult result){
        busy.set(false);
        long now=SystemClock.uptimeMillis();
        long inference=Math.max(0,now-result.timestampMs());
        List<List<NormalizedLandmark>> hands=result.landmarks();
        if(hands==null||hands.isEmpty()||hands.get(0).size()<21){listener.onNoHand(result.timestampMs(),inference);return;}
        List<NormalizedLandmark> lm=hands.get(0);
        float[] x=new float[21],y=new float[21],z=new float[21];
        for(int i=0;i<21;i++){NormalizedLandmark p=lm.get(i);x[i]=p.x();y[i]=p.y();z[i]=p.z();}
        listener.onLandmarks(x,y,z,result.timestampMs(),inference);
    }

    public boolean ready(){return landmarker!=null;}
    public boolean busy(){return busy.get();}

    @Override public void close(){if(landmarker!=null){try{landmarker.close();}catch(Exception ignored){}landmarker=null;}busy.set(false);}
}
