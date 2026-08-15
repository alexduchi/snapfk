package com.neo.aircontrol;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.Landmark;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaPipeHandTracker implements AutoCloseable {
    public interface Listener {
        void onHands(float[][] x,float[][] y,float[][] z,long timestampMs,long inferenceMs);
        void onNoHand(long timestampMs,long inferenceMs);
        void onError(String message);
    }

    private static final int MAX_HANDS=2;
    private static final long SLOT_MEMORY_MS=650;
    private static final long FILTER_RESET_GAP_MS=220;

    private static final class Detection {
        final float[] x=new float[21],y=new float[21],z=new float[21];
        float anchorX,anchorY,palm;
        String handed="";
        float handedScore;
        float worldShape;
    }

    private static final class Slot {
        final float[] x=new float[21],y=new float[21],z=new float[21];
        boolean valid;
        long lastSeen;
        float anchorX,anchorY;
        String handed="";
        float handedScore;
        float worldShape;
    }

    private final Listener listener;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final Slot[] slots={new Slot(),new Slot()};
    private HandLandmarker landmarker;
    private MPImage inFlightImage;
    private long lastSubmit,lastDeliveredTimestamp;

    public MediaPipeHandTracker(Context context,Listener listener){this.listener=listener;setup(context);}

    private void setup(Context context){
        try{
            BaseOptions base=BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(Delegate.CPU).build();
            HandLandmarker.HandLandmarkerOptions options=HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(2)
                    // Intentionally unchanged from the validated AirControl tuning.
                    .setMinHandDetectionConfidence(0.20f)
                    .setMinHandPresenceConfidence(0.18f)
                    .setMinTrackingConfidence(0.20f)
                    .setResultListener((result,input)->{
                        try{handleResult(result);}finally{releaseInput(input);busy.set(false);}
                    })
                    .setErrorListener(error->{
                        releaseInput(null);busy.set(false);
                        listener.onError(error.getMessage()==null?"MediaPipe error":error.getMessage());
                    })
                    .build();
            landmarker=HandLandmarker.createFromOptions(context,options);
        }catch(RuntimeException e){listener.onError("MediaPipe init: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
    }

    public boolean submit(Bitmap bitmap){
        if(landmarker==null||bitmap==null||!busy.compareAndSet(false,true))return false;
        long ts=SystemClock.uptimeMillis();if(ts<=lastSubmit)ts=lastSubmit+1;lastSubmit=ts;
        MPImage image=null;
        try{
            image=new BitmapImageBuilder(bitmap).build();
            inFlightImage=image;
            landmarker.detectAsync(image,ts);
            return true;
        }catch(RuntimeException e){
            if(image!=null)try{image.close();}catch(Exception ignored){}
            if(inFlightImage==image)inFlightImage=null;
            busy.set(false);
            listener.onError("MediaPipe detect: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
            return false;
        }
    }

    private void releaseInput(MPImage input){
        MPImage pending=inFlightImage;inFlightImage=null;
        if(input!=null)try{input.close();}catch(Exception ignored){}
        if(pending!=null&&pending!=input)try{pending.close();}catch(Exception ignored){}
    }

    private void handleResult(HandLandmarkerResult result){
        long now=SystemClock.uptimeMillis();
        long ts=result.timestampMs();
        long inference=Math.max(0,now-ts);
        if(ts<=lastDeliveredTimestamp)return;
        lastDeliveredTimestamp=ts;

        List<List<NormalizedLandmark>> hands=result.landmarks();
        List<List<Landmark>> worldHands=result.worldLandmarks();
        List<List<Category>> handedness=result.handedness();
        if(hands==null||hands.isEmpty()){
            expireSlots(ts);
            listener.onNoHand(ts,inference);
            return;
        }

        Detection[] detections=new Detection[MAX_HANDS];
        int valid=0;
        int n=Math.min(MAX_HANDS,hands.size());
        for(int h=0;h<n;h++){
            List<NormalizedLandmark> lm=hands.get(h);
            if(lm==null||lm.size()<21)continue;
            Detection d=new Detection();
            for(int i=0;i<21;i++){
                NormalizedLandmark p=lm.get(i);
                d.x[i]=p.x();d.y[i]=p.y();d.z[i]=p.z();
            }
            d.anchorX=(d.x[0]+d.x[5]+d.x[9]+d.x[17])*.25f;
            d.anchorY=(d.y[0]+d.y[5]+d.y[9]+d.y[17])*.25f;
            d.palm=Math.max(.035f,dist(d.x[5],d.y[5],d.x[17],d.y[17]));

            if(worldHands!=null&&h<worldHands.size()){
                List<Landmark> world=worldHands.get(h);
                if(world!=null&&world.size()>=21){
                    float palmW=dist3(world.get(5),world.get(17));
                    float palmL=dist3(world.get(0),world.get(9));
                    float indexL=dist3(world.get(5),world.get(8));
                    if(palmW>1e-4f)d.worldShape=(palmL+indexL*.45f)/palmW;
                }
            }

            if(handedness!=null&&h<handedness.size()){
                List<Category> cats=handedness.get(h);
                if(cats!=null&&!cats.isEmpty()&&cats.get(0)!=null){
                    Category c=cats.get(0);
                    d.handed=c.categoryName()==null?"":c.categoryName();
                    d.handedScore=c.score();
                }
            }
            detections[valid++]=d;
        }

        if(valid==0){
            expireSlots(ts);
            listener.onNoHand(ts,inference);
            return;
        }

        expireSlots(ts);
        int[] detForSlot=assignDetections(detections,valid);
        float[][] xs=new float[valid][21],ys=new float[valid][21],zs=new float[valid][21];
        int out=0;
        for(int s=0;s<MAX_HANDS;s++){
            int d=detForSlot[s];
            if(d<0)continue;
            updateSlot(slots[s],detections[d],ts);
            System.arraycopy(slots[s].x,0,xs[out],0,21);
            System.arraycopy(slots[s].y,0,ys[out],0,21);
            System.arraycopy(slots[s].z,0,zs[out],0,21);
            out++;
        }
        listener.onHands(xs,ys,zs,ts,inference);
    }

    private void expireSlots(long ts){
        for(Slot s:slots)if(s.valid&&ts-s.lastSeen>SLOT_MEMORY_MS)s.valid=false;
    }

    private int[] assignDetections(Detection[] d,int count){
        int[] result={-1,-1};
        boolean a=slots[0].valid,b=slots[1].valid;
        if(count==1){
            if(a&&b)result[cost(slots[0],d[0])<=cost(slots[1],d[0])?0:1]=0;
            else if(a)result[0]=0;
            else if(b)result[1]=0;
            else result[preferredFreshSlot(d[0])]=0;
            return result;
        }

        if(a&&b){
            float straight=cost(slots[0],d[0])+cost(slots[1],d[1]);
            float crossed=cost(slots[0],d[1])+cost(slots[1],d[0]);
            if(straight<=crossed){result[0]=0;result[1]=1;}else{result[0]=1;result[1]=0;}
        }else if(a||b){
            int known=a?0:1,other=1-known;
            float c0=cost(slots[known],d[0]),c1=cost(slots[known],d[1]);
            if(c0<=c1){result[known]=0;result[other]=1;}else{result[known]=1;result[other]=0;}
        }else{
            int first=preferredFreshSlot(d[0]);
            int second=preferredFreshSlot(d[1]);
            if(first!=second){result[first]=0;result[second]=1;}
            else if(d[0].anchorX<=d[1].anchorX){result[0]=0;result[1]=1;}
            else{result[0]=1;result[1]=0;}
        }
        return result;
    }

    private static int preferredFreshSlot(Detection d){
        if("Left".equalsIgnoreCase(d.handed)&&d.handedScore>=.55f)return 0;
        if("Right".equalsIgnoreCase(d.handed)&&d.handedScore>=.55f)return 1;
        return d.anchorX<.5f?0:1;
    }

    private static float cost(Slot s,Detection d){
        float c=dist(s.anchorX,s.anchorY,d.anchorX,d.anchorY);
        if(!s.handed.isEmpty()&&!d.handed.isEmpty()&&!s.handed.equalsIgnoreCase(d.handed)
                &&Math.min(s.handedScore,d.handedScore)>=.55f)c+=.24f;
        if(s.worldShape>0f&&d.worldShape>0f){
            float ratio=Math.max(s.worldShape,d.worldShape)/Math.max(.001f,Math.min(s.worldShape,d.worldShape));
            c+=Math.min(.11f,(ratio-1f)*.16f);
        }
        return c;
    }

    private static void updateSlot(Slot s,Detection d,long ts){
        long gap=s.valid?Math.max(0,ts-s.lastSeen):Long.MAX_VALUE;
        if(!s.valid||gap>FILTER_RESET_GAP_MS){
            System.arraycopy(d.x,0,s.x,0,21);System.arraycopy(d.y,0,s.y,0,21);System.arraycopy(d.z,0,s.z,0,21);
            s.anchorX=d.anchorX;s.anchorY=d.anchorY;s.valid=true;s.lastSeen=ts;s.worldShape=d.worldShape;
            updateHandedness(s,d);
            return;
        }

        float anchorMove=dist(s.anchorX,s.anchorY,d.anchorX,d.anchorY);
        boolean wholeHandJump=anchorMove>Math.max(.22f,d.palm*2.8f);
        if(wholeHandJump){
            // Likely reacquisition or identity change: re-anchor instead of smearing old coordinates across the screen.
            System.arraycopy(d.x,0,s.x,0,21);System.arraycopy(d.y,0,s.y,0,21);System.arraycopy(d.z,0,s.z,0,21);
        }else{
            float outlierLimit=Math.max(.075f,d.palm*1.45f+anchorMove*1.25f);
            for(int i=0;i<21;i++){
                float dx=d.x[i]-s.x[i],dy=d.y[i]-s.y[i];
                float pointMove=(float)Math.sqrt(dx*dx+dy*dy);
                if(pointMove>outlierLimit&&anchorMove<Math.max(.055f,d.palm*.75f)){
                    float scale=outlierLimit/Math.max(1e-5f,pointMove);
                    dx*=scale;dy*=scale;
                }
                float alpha=pointMove<.004f?.55f:(pointMove<.012f?.70f:(pointMove<.035f?.84f:.94f));
                s.x[i]+=dx*alpha;s.y[i]+=dy*alpha;
                float dz=d.z[i]-s.z[i];
                float zAlpha=Math.abs(dz)<.015f?.68f:.88f;
                s.z[i]+=dz*zAlpha;
            }
        }
        s.anchorX=(s.x[0]+s.x[5]+s.x[9]+s.x[17])*.25f;
        s.anchorY=(s.y[0]+s.y[5]+s.y[9]+s.y[17])*.25f;
        if(d.worldShape>0f)s.worldShape=s.worldShape<=0f?d.worldShape:(s.worldShape*.82f+d.worldShape*.18f);
        s.valid=true;s.lastSeen=ts;
        updateHandedness(s,d);
    }

    private static void updateHandedness(Slot s,Detection d){
        if(d.handed.isEmpty()||d.handedScore<.50f)return;
        if(s.handed.isEmpty()||s.handed.equalsIgnoreCase(d.handed)||d.handedScore>s.handedScore+.12f){
            s.handed=d.handed;s.handedScore=d.handedScore;
        }
    }

    private static float dist(float ax,float ay,float bx,float by){float dx=ax-bx,dy=ay-by;return(float)Math.sqrt(dx*dx+dy*dy);}
    private static float dist3(Landmark a,Landmark b){float dx=a.x()-b.x(),dy=a.y()-b.y(),dz=a.z()-b.z();return(float)Math.sqrt(dx*dx+dy*dy+dz*dz);}

    public boolean ready(){return landmarker!=null;}
    public boolean busy(){return busy.get();}
    @Override public void close(){
        releaseInput(null);
        if(landmarker!=null){try{landmarker.close();}catch(Exception ignored){}landmarker=null;}
        for(Slot s:slots)s.valid=false;
        busy.set(false);
    }
}
