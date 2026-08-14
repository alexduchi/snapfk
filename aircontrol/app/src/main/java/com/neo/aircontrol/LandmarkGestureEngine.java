package com.neo.aircontrol;

import java.util.ArrayDeque;

public final class LandmarkGestureEngine {
    public static final class DebugState {
        public boolean handFound, tracking, grace;
        public int samples;
        public float palmScale, x, y, speed, displacement, coherence;
        public String state="NO_HAND";
        public GestureCommand lastCommand=GestureCommand.NONE;
    }

    private static final int WRIST=0, INDEX_MCP=5, MIDDLE_MCP=9, RING_MCP=13, PINKY_MCP=17;
    private static final long SOFT_LOST_MS=240, HARD_LOST_MS=700, COOLDOWN_MS=300, ACQUIRE_MS=120, HISTORY_MS=340;
    private static final float MIN_DISP=.068f, MIN_SPEED=.30f, DOMINANCE=1.28f, MAX_SINGLE_STEP=.20f;
    private final DebugState d=new DebugState();
    private final ArrayDeque<Sample> history=new ArrayDeque<>();
    private long lastHandMs, firstHandMs, cooldownUntil;
    private boolean haveFiltered;
    private float fx,fy;

    private static final class Sample {
        final float x,y; final long t;
        Sample(float x,float y,long t){this.x=x;this.y=y;this.t=t;}
    }

    public DebugState debug(){return d;}

    public void reset(){
        history.clear();haveFiltered=false;lastHandMs=firstHandMs=cooldownUntil=0;
        d.handFound=d.tracking=d.grace=false;d.samples=0;d.speed=d.displacement=d.coherence=0;d.state="RESET";d.lastCommand=GestureCommand.NONE;
    }

    public GestureCommand onNoHand(long now){
        d.handFound=false;
        long gap=lastHandMs==0?Long.MAX_VALUE:now-lastHandMs;
        if(gap<=HARD_LOST_MS){
            d.grace=true;d.tracking=!history.isEmpty();d.state=gap<=SOFT_LOST_MS?"GRACE":"SEARCHING";
            return GestureCommand.NONE;
        }
        history.clear();haveFiltered=false;firstHandMs=0;d.grace=false;d.tracking=false;d.samples=0;d.state="NO_HAND";
        return GestureCommand.NONE;
    }

    public GestureCommand process(float[] x,float[] y,float[] z,long now){
        if(x==null||y==null||z==null||x.length<21||y.length<21||z.length<21)return onNoHand(now);

        float palmX=(x[WRIST]+x[INDEX_MCP]+x[MIDDLE_MCP]+x[RING_MCP]+x[PINKY_MCP])/5f;
        float palmY=(y[WRIST]+y[INDEX_MCP]+y[MIDDLE_MCP]+y[RING_MCP]+y[PINKY_MCP])/5f;
        float palmScale=Math.max(.001f,dist3(x,y,z,INDEX_MCP,PINKY_MCP));
        d.palmScale=palmScale;d.handFound=true;d.grace=false;

        long gap=lastHandMs==0?Long.MAX_VALUE:now-lastHandMs;
        lastHandMs=now;
        if(firstHandMs==0||gap>HARD_LOST_MS){firstHandMs=now;history.clear();haveFiltered=false;}

        if(!haveFiltered){fx=palmX;fy=palmY;haveFiltered=true;}
        else {
            float dx=palmX-fx,dy=palmY-fy;
            float jump=(float)Math.sqrt(dx*dx+dy*dy);
            if(jump>MAX_SINGLE_STEP){
                fx=palmX;fy=palmY;history.clear();firstHandMs=now;d.state="REACQUIRE";
            }else{
                float alpha=gap>SOFT_LOST_MS?.65f:.55f;
                fx+=dx*alpha;fy+=dy*alpha;
            }
        }

        d.x=fx;d.y=fy;
        history.addLast(new Sample(fx,fy,now));
        while(!history.isEmpty()&&(history.size()>18||now-history.peekFirst().t>HISTORY_MS))history.removeFirst();
        d.samples=history.size();d.tracking=true;

        if(now<cooldownUntil){
            while(history.size()>3)history.removeFirst();
            d.state="COOLDOWN";return GestureCommand.NONE;
        }
        if(now-firstHandMs<ACQUIRE_MS||history.size()<4){d.state="TRACKING";return GestureCommand.NONE;}

        Sample first=history.peekFirst(),last=history.peekLast();
        long dtMs=effectiveDurationMs();
        float dx=last.x-first.x,dy=last.y-first.y,adx=Math.abs(dx),ady=Math.abs(dy);
        float disp=Math.max(adx,ady),speed=disp/(dtMs/1000f);
        d.displacement=disp;d.speed=speed;

        GestureCommand candidate=GestureCommand.NONE;
        if(adx>ady*DOMINANCE)candidate=dx>0?GestureCommand.RIGHT:GestureCommand.LEFT;
        else if(ady>adx*DOMINANCE)candidate=dy>0?GestureCommand.DOWN:GestureCommand.UP;
        if(candidate==GestureCommand.NONE||disp<MIN_DISP||speed<MIN_SPEED){d.coherence=0;d.state="TRACKING";return GestureCommand.NONE;}

        float coherence=coherence(candidate);d.coherence=coherence;
        if(coherence<.70f){d.state="TRACKING";return GestureCommand.NONE;}

        d.lastCommand=candidate;d.state="FIRED_"+candidate.name();cooldownUntil=now+COOLDOWN_MS;
        history.clear();history.add(new Sample(fx,fy,now));firstHandMs=now-ACQUIRE_MS;
        return candidate;
    }

    private long effectiveDurationMs(){
        if(history.size()<2)return 1;
        Sample p=null;long total=0;
        for(Sample s:history){if(p!=null)total+=Math.min(90,Math.max(1,s.t-p.t));p=s;}
        return Math.max(1,total);
    }

    private float coherence(GestureCommand cmd){
        if(history.size()<3)return 0;
        Sample p=null;float along=0,against=0,cross=0,total=0;
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
                if(q>=0)along+=q;else against+=-q;cross+=c;total+=Math.abs(q)+c;
            }
            p=s;
        }
        if(total<1e-5f)return 0;
        float direction=along/(along+against+.0001f);
        float straight=along/(along+cross+.0001f);
        return direction*.6f+straight*.4f;
    }

    private static float dist3(float[] x,float[] y,float[] z,int a,int b){
        float dx=x[a]-x[b],dy=y[a]-y[b],dz=z[a]-z[b];return(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
}
