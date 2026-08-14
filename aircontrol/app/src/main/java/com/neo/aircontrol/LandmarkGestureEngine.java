package com.neo.aircontrol;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;

public final class LandmarkGestureEngine {
    public static final class DebugState {
        public boolean handFound, tracking, grace;
        public int samples, candidateFrames;
        public float palmScale, x, y, speed, displacement, coherence, dynamicThreshold, jitter;
        public String state="NO_HAND";
        public GestureCommand lastCommand=GestureCommand.NONE;
    }

    private static final int WRIST=0, INDEX_MCP=5, MIDDLE_MCP=9, RING_MCP=13, PINKY_MCP=17;
    private static final long SOFT_LOST_MS=260, HARD_LOST_MS=900, COOLDOWN_MS=330, ACQUIRE_MS=120, HISTORY_MS=430;
    private static final float BASE_MIN_DISP=.050f, BASE_MIN_SPEED=.22f, DOMINANCE=1.24f;

    private final DebugState d=new DebugState();
    private final ArrayDeque<Sample> history=new ArrayDeque<>();
    private long lastHandMs, firstHandMs, cooldownUntil;
    private boolean haveFiltered;
    private float fx,fy,jitterEma;
    private GestureCommand pending=GestureCommand.NONE;
    private int pendingFrames;

    private static final class Sample {
        final float x,y; final long t;
        Sample(float x,float y,long t){this.x=x;this.y=y;this.t=t;}
    }

    private static final class MotionStats {
        float coherence, medianSpeed, goodRatio;
    }

    public DebugState debug(){return d;}

    public void reset(){
        history.clear();haveFiltered=false;lastHandMs=firstHandMs=cooldownUntil=0;jitterEma=0;
        pending=GestureCommand.NONE;pendingFrames=0;
        d.handFound=d.tracking=d.grace=false;d.samples=d.candidateFrames=0;
        d.speed=d.displacement=d.coherence=d.dynamicThreshold=d.jitter=0;
        d.state="RESET";d.lastCommand=GestureCommand.NONE;
    }

    public GestureCommand onNoHand(long now){
        d.handFound=false;
        long gap=lastHandMs==0?Long.MAX_VALUE:now-lastHandMs;
        if(gap<=SOFT_LOST_MS){
            d.grace=true;d.tracking=!history.isEmpty();d.state="GRACE";
            clearCandidate();
            return GestureCommand.NONE;
        }
        if(gap<=HARD_LOST_MS){
            d.grace=true;d.tracking=false;d.state="SEARCHING";
            history.clear();clearCandidate();firstHandMs=0;
            return GestureCommand.NONE;
        }
        history.clear();haveFiltered=false;firstHandMs=0;clearCandidate();
        d.grace=false;d.tracking=false;d.samples=0;d.state="NO_HAND";
        return GestureCommand.NONE;
    }

    public GestureCommand process(float[] x,float[] y,float[] z,long now){
        if(!validInput(x,y,z))return onNoHand(now);

        float palmX=(x[WRIST]+x[INDEX_MCP]+x[MIDDLE_MCP]+x[RING_MCP]+x[PINKY_MCP])/5f;
        float palmY=(y[WRIST]+y[INDEX_MCP]+y[MIDDLE_MCP]+y[RING_MCP]+y[PINKY_MCP])/5f;
        float palmScale=Math.max(.001f,dist3(x,y,z,INDEX_MCP,PINKY_MCP));
        float palmLength=dist3(x,y,z,WRIST,MIDDLE_MCP);
        if(!saneGeometry(x,y,palmScale,palmLength))return onNoHand(now);

        long gap=lastHandMs==0?Long.MAX_VALUE:now-lastHandMs;
        lastHandMs=now;
        d.handFound=true;d.grace=false;d.palmScale=palmScale;

        if(firstHandMs==0||gap>SOFT_LOST_MS){
            firstHandMs=now;history.clear();haveFiltered=false;clearCandidate();
        }

        if(!haveFiltered){
            fx=palmX;fy=palmY;haveFiltered=true;
        }else{
            float rdx=palmX-fx,rdy=palmY-fy;
            float jump=(float)Math.sqrt(rdx*rdx+rdy*rdy);
            float maxJump=clamp(.075f+palmScale*.55f,.11f,.19f);
            if(jump>maxJump){
                fx=palmX;fy=palmY;history.clear();firstHandMs=now;clearCandidate();d.state="REACQUIRE";
            }else{
                if(jump<.030f)jitterEma=jitterEma*.90f+jump*.10f;
                else jitterEma=jitterEma*.97f+Math.min(jump,.055f)*.03f;
                float alpha=jump<.012f?.30f:(jump<.035f?.50f:.72f);
                if(gap>160)alpha=Math.max(alpha,.68f);
                fx+=rdx*alpha;fy+=rdy*alpha;
            }
        }

        d.x=fx;d.y=fy;d.jitter=jitterEma;
        history.addLast(new Sample(fx,fy,now));
        while(!history.isEmpty()&&(history.size()>20||now-history.peekFirst().t>HISTORY_MS))history.removeFirst();
        d.samples=history.size();d.tracking=true;

        if(now<cooldownUntil){
            while(history.size()>3)history.removeFirst();
            clearCandidate();d.state="COOLDOWN";return GestureCommand.NONE;
        }
        if(now-firstHandMs<ACQUIRE_MS||history.size()<4){
            clearCandidate();d.state="TRACKING";return GestureCommand.NONE;
        }

        float[] edge=edgeDisplacement();
        float dx=edge[0],dy=edge[1],adx=Math.abs(dx),ady=Math.abs(dy);
        float disp=Math.max(adx,ady);
        long dtMs=effectiveDurationMs();
        float speed=disp/(dtMs/1000f);
        float minDisp=clamp(BASE_MIN_DISP+palmScale*.10f+jitterEma*2.2f,.058f,.100f);
        float minSpeed=clamp(BASE_MIN_SPEED+jitterEma*3.6f,.22f,.36f);
        d.displacement=disp;d.speed=speed;d.dynamicThreshold=minDisp;

        GestureCommand candidate=GestureCommand.NONE;
        if(adx>ady*DOMINANCE)candidate=dx>0?GestureCommand.RIGHT:GestureCommand.LEFT;
        else if(ady>adx*DOMINANCE)candidate=dy>0?GestureCommand.DOWN:GestureCommand.UP;

        if(candidate==GestureCommand.NONE||disp<minDisp||speed<minSpeed){
            clearCandidate();d.coherence=0;d.state="TRACKING";return GestureCommand.NONE;
        }

        MotionStats stats=motionStats(candidate);
        d.coherence=stats.coherence;
        if(stats.coherence<.74f||stats.goodRatio<.64f||stats.medianSpeed<minSpeed*.72f){
            clearCandidate();d.state="FILTERING";return GestureCommand.NONE;
        }

        if(candidate==pending)pendingFrames++;
        else{pending=candidate;pendingFrames=1;}
        d.candidateFrames=pendingFrames;
        d.state="CONFIRM_"+candidate.name()+"_"+pendingFrames;
        if(pendingFrames<2)return GestureCommand.NONE;

        d.lastCommand=candidate;d.state="FIRED_"+candidate.name();
        cooldownUntil=now+COOLDOWN_MS;
        history.clear();history.add(new Sample(fx,fy,now));firstHandMs=now-ACQUIRE_MS;
        clearCandidate();
        return candidate;
    }

    private boolean validInput(float[] x,float[] y,float[] z){
        if(x==null||y==null||z==null||x.length<21||y.length<21||z.length<21)return false;
        for(int i=0;i<21;i++){
            if(Float.isNaN(x[i])||Float.isNaN(y[i])||Float.isNaN(z[i])||Float.isInfinite(x[i])||Float.isInfinite(y[i])||Float.isInfinite(z[i]))return false;
            if(x[i]<-.35f||x[i]>1.35f||y[i]<-.35f||y[i]>1.35f)return false;
        }
        return true;
    }

    private boolean saneGeometry(float[] x,float[] y,float scale,float palmLength){
        if(scale<.025f||scale>.55f)return false;
        float ratio=palmLength/scale;
        if(ratio<.30f||ratio>3.8f)return false;
        float minX=10,maxX=-10,minY=10,maxY=-10;
        for(int i=0;i<21;i++){minX=Math.min(minX,x[i]);maxX=Math.max(maxX,x[i]);minY=Math.min(minY,y[i]);maxY=Math.max(maxY,y[i]);}
        float span=Math.max(maxX-minX,maxY-minY);
        return span>.035f&&span<1.20f;
    }

    private float[] edgeDisplacement(){
        float sx=0,sy=0,ex=0,ey=0;int sn=0,en=0;
        Iterator<Sample> it=history.iterator();
        while(it.hasNext()&&sn<2){Sample s=it.next();sx+=s.x;sy+=s.y;sn++;}
        Iterator<Sample> rit=history.descendingIterator();
        while(rit.hasNext()&&en<2){Sample s=rit.next();ex+=s.x;ey+=s.y;en++;}
        sx/=Math.max(1,sn);sy/=Math.max(1,sn);ex/=Math.max(1,en);ey/=Math.max(1,en);
        return new float[]{ex-sx,ey-sy};
    }

    private long effectiveDurationMs(){
        if(history.size()<2)return 1;
        Sample p=null;long total=0;
        for(Sample s:history){if(p!=null)total+=Math.min(100,Math.max(1,s.t-p.t));p=s;}
        return Math.max(1,total);
    }

    private MotionStats motionStats(GestureCommand cmd){
        MotionStats out=new MotionStats();
        if(history.size()<3)return out;
        float along=0,against=0,cross=0;
        float[] velocities=new float[Math.max(1,history.size()-1)];int vn=0,good=0,moving=0;
        Sample p=null;
        for(Sample s:history){
            if(p!=null){
                float dx=s.x-p.x,dy=s.y-p.y,q,c;
                switch(cmd){
                    case RIGHT:q=dx;c=Math.abs(dy);break;
                    case LEFT:q=-dx;c=Math.abs(dy);break;
                    case DOWN:q=dy;c=Math.abs(dx);break;
                    case UP:q=-dy;c=Math.abs(dx);break;
                    default:q=0;c=0;
                }
                long dt=Math.max(1,Math.min(100,s.t-p.t));
                float v=q/(dt/1000f);
                if(Math.abs(q)+c>.0025f){moving++;if(q>0&&q>c*.48f)good++;}
                if(q>=0)along+=q;else against+=-q;
                cross+=c;
                if(q>0)velocities[vn++]=v;
            }
            p=s;
        }
        float direction=along/(along+against+.0001f);
        float straight=along/(along+cross+.0001f);
        out.coherence=direction*.62f+straight*.38f;
        out.goodRatio=moving==0?0:(float)good/moving;
        if(vn>0){Arrays.sort(velocities,0,vn);out.medianSpeed=velocities[vn/2];}
        return out;
    }

    private void clearCandidate(){pending=GestureCommand.NONE;pendingFrames=0;d.candidateFrames=0;}
    private static float clamp(float v,float lo,float hi){return v<lo?lo:(v>hi?hi:v);}
    private static float dist3(float[] x,float[] y,float[] z,int a,int b){float dx=x[a]-x[b],dy=y[a]-y[b],dz=z[a]-z[b];return(float)Math.sqrt(dx*dx+dy*dy+dz*dz);}
}
