package com.neo.aircontrol;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class MotionGestureDetector {
    private static final int GW=64, GH=48, N=GW*GH;
    private static final int DIFF=22, GATE_FRAMES=4;
    private static final float MIN_AREA=.018f, MAX_AREA=.48f, MIN_DISP=.14f, MIN_SPEED=.42f, DOM=1.55f;
    private static final long GRACE=320, COOLDOWN=430;
    private final int[] bg=new int[N], cur=new int[N], queue=new int[N], top=new int[GW], smooth=new int[GW];
    private final boolean[] mask=new boolean[N];
    private final ArrayDeque<S> hist=new ArrayDeque<>();
    private boolean calibrated, armed; private int gate; private long lastGate,cooldown,anchorT; private float ax,ay;
    public static final class DebugState { public boolean calibrated,twoFingerGate,armed; public float x,y,areaRatio; public int peaks; public String state="WAIT_CALIBRATION"; }
    private final DebugState debug=new DebugState();
    private static final class S { float x,y; long t; S(float x,float y,long t){this.x=x;this.y=y;this.t=t;} }
    private static final class C { int count,minX,maxX,minY,maxY; float cx,cy; }
    private static final class P { boolean valid; int count; float x,y; }
    public DebugState debug(){return debug;}
    public void calibrate(byte[] nv21,int w,int h){ sample(nv21,w,h,bg);calibrated=true;armed=false;gate=0;hist.clear();debug.calibrated=true;debug.state="IDLE"; }
    public GestureCommand process(byte[] nv21,int w,int h,long now){
        if(!calibrated)return GestureCommand.NONE; sample(nv21,w,h,cur);
        for(int i=0;i<N;i++)mask[i]=Math.abs(cur[i]-bg[i])>=DIFF;
        C c=largest(); if(c==null){gate=0;if(armed&&now-lastGate>GRACE)disarm();debug.twoFingerGate=false;debug.armed=armed;debug.state=armed?"ARMED_GRACE":"IDLE";return GestureCommand.NONE;}
        float ar=c.count/(float)N; debug.areaRatio=ar; if(ar<MIN_AREA||ar>MAX_AREA){gate=0;if(armed&&now-lastGate>GRACE)disarm();debug.state="REJECT_AREA";return GestureCommand.NONE;}
        P p=peaks(c); debug.peaks=p.count; debug.twoFingerGate=p.valid; float x=p.valid?p.x:c.cx,y=p.valid?p.y:c.cy; debug.x=x;debug.y=y;
        if(p.valid){lastGate=now;gate++;if(!armed&&gate>=GATE_FRAMES&&now>=cooldown){armed=true;ax=x;ay=y;anchorT=now;hist.clear();hist.add(new S(x,y,now));}}
        else {gate=0;if(!armed||now-lastGate>GRACE){if(armed)disarm();debug.armed=false;debug.state="IDLE";return GestureCommand.NONE;}}
        if(!armed){debug.armed=false;debug.state="GATE_"+gate+"/"+GATE_FRAMES;return GestureCommand.NONE;}
        debug.armed=true;debug.state="ARMED";hist.addLast(new S(x,y,now));while(hist.size()>10||(!hist.isEmpty()&&now-hist.peekFirst().t>550))hist.removeFirst();
        long dt=Math.max(1,now-anchorT);float dx=x-ax,dy=y-ay,adx=Math.abs(dx),ady=Math.abs(dy),dist=Math.max(adx,ady),speed=dist/(dt/1000f);
        if(dt>260&&speed<.22f){ax=ax*.75f+x*.25f;ay=ay*.75f+y*.25f;anchorT=now;return GestureCommand.NONE;}
        if(dist<MIN_DISP||speed<MIN_SPEED)return GestureCommand.NONE; GestureCommand cmd=GestureCommand.NONE;
        if(adx>ady*DOM)cmd=dx>0?GestureCommand.RIGHT:GestureCommand.LEFT;else if(ady>adx*DOM)cmd=dy>0?GestureCommand.DOWN:GestureCommand.UP;
        if(cmd!=GestureCommand.NONE&&coherent(cmd)){cooldown=now+COOLDOWN;disarm();debug.state="FIRED_"+cmd.name();return cmd;}return GestureCommand.NONE;
    }
    private boolean coherent(GestureCommand cmd){if(hist.size()<3)return false;S prev=null;float good=0,bad=0;for(S s:hist){if(prev!=null){float d=0;switch(cmd){case RIGHT:d=s.x-prev.x;break;case LEFT:d=prev.x-s.x;break;case DOWN:d=s.y-prev.y;break;case UP:d=prev.y-s.y;break;default:}if(d>=0)good+=d;else bad+=-d;}prev=s;}return good>.06f&&good>bad*2.3f;}
    private void disarm(){armed=false;gate=0;hist.clear();}
    private void sample(byte[] a,int w,int h,int[] out){for(int gy=0;gy<GH;gy++){int sy=gy*h/GH,row=sy*w;for(int gx=0;gx<GW;gx++){int sx=gx*w/GW;out[gy*GW+gx]=a[row+sx]&255;}}}
    private C largest(){boolean[] seen=new boolean[N];C best=null;int bestN=0;for(int i=0;i<N;i++){if(!mask[i]||seen[i])continue;int qh=0,qt=0,count=0,sx=0,sy=0,minx=GW,miny=GH,maxx=0,maxy=0;queue[qt++]=i;seen[i]=true;while(qh<qt){int v=queue[qh++],x=v%GW,y=v/GW;count++;sx+=x;sy+=y;if(x<minx)minx=x;if(x>maxx)maxx=x;if(y<miny)miny=y;if(y>maxy)maxy=y;int n;if(x>0){n=v-1;if(mask[n]&&!seen[n]){seen[n]=true;queue[qt++]=n;}}if(x+1<GW){n=v+1;if(mask[n]&&!seen[n]){seen[n]=true;queue[qt++]=n;}}if(y>0){n=v-GW;if(mask[n]&&!seen[n]){seen[n]=true;queue[qt++]=n;}}if(y+1<GH){n=v+GW;if(mask[n]&&!seen[n]){seen[n]=true;queue[qt++]=n;}}}if(count>bestN){bestN=count;best=new C();best.count=count;best.minX=minx;best.maxX=maxx;best.minY=miny;best.maxY=maxy;best.cx=(sx/(float)count)/(GW-1);best.cy=(sy/(float)count)/(GH-1);}}return best;}
    private P peaks(C c){Arrays.fill(top,GH);for(int x=c.minX;x<=c.maxX;x++)for(int y=c.minY;y<=c.maxY;y++)if(mask[y*GW+x]){top[x]=y;break;}for(int x=c.minX;x<=c.maxX;x++){int sum=0,n=0;for(int k=-2;k<=2;k++){int xx=x+k;if(xx>=c.minX&&xx<=c.maxX&&top[xx]<GH){sum+=top[xx];n++;}}smooth[x]=n==0?GH:sum/n;}int b1=-1,b2=-1,p1=0,p2=0,count=0;for(int x=c.minX+3;x<=c.maxX-3;x++){int y=smooth[x];if(y>=GH)continue;int prom=Math.min(smooth[x-3],smooth[x+3])-y;if(prom>=2&&y<=smooth[x-1]&&y<=smooth[x+1]){count++;if(prom>p1){b2=b1;p2=p1;b1=x;p1=prom;}else if(prom>p2){b2=x;p2=prom;}}}P r=new P();r.count=count;if(b1>=0&&b2>=0){int sep=Math.abs(b1-b2),bw=Math.max(1,c.maxX-c.minX+1),y1=smooth[b1],y2=smooth[b2];boolean sepOk=sep>=Math.max(5,bw/6)&&sep<=Math.max(8,(bw*3)/4),heightOk=Math.abs(y1-y2)<=Math.max(5,(c.maxY-c.minY)/3),upper=y1<c.minY+(c.maxY-c.minY)*.55f&&y2<c.minY+(c.maxY-c.minY)*.55f;if(sepOk&&heightOk&&upper){r.valid=true;r.x=((b1+b2)*.5f)/(GW-1);r.y=((y1+y2)*.5f)/(GH-1);}}return r;}
}
