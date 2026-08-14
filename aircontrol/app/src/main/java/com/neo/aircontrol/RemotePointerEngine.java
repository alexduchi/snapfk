package com.neo.aircontrol;

public final class RemotePointerEngine {
    public static final class Result {
        public boolean active, blockSwipes, pairFound, pinching, dragging;
        public boolean tap, dragStart, dragMove, dragEnd;
        public float x=.5f,y=.5f,pinchRatio=9f;
        public String state="OFF";
    }

    private static final int WRIST=0,THUMB_TIP=4;
    private static final int INDEX_MCP=5,INDEX_PIP=6,INDEX_TIP=8;
    private static final int MIDDLE_MCP=9,MIDDLE_PIP=10,MIDDLE_TIP=12;
    private static final int RING_MCP=13,RING_PIP=14,RING_TIP=16;
    private static final int PINKY_MCP=17,PINKY_PIP=18,PINKY_TIP=20;

    private static final int ACTIVATE_FRAMES=3;
    private static final long PAIR_HOLD_MS=420;
    private static final long DRAG_HOLD_MS=380;
    private static final long CLICK_COOLDOWN_MS=260;
    private static final float PINCH_ON=.43f, PINCH_OFF=.58f;
    private static final float CURSOR_MIN=.035f, CURSOR_MAX=.965f;
    private static final float BASE_GAIN=1.55f;
    private static final float POINTER_DEADZONE=.0022f;
    private static final float MAX_FRAME_DELTA=.085f;

    private final Result out=new Result();
    private boolean active,pinching,dragging,haveFingerReference;
    private int activationFrames,pinchOnFrames,pinchOffFrames;
    private long lastPairMs,pinchStartMs,clickCooldownUntil;
    private float cx=.5f,cy=.5f,lastFingerX,lastFingerY;
    private float sensitivity=1.15f;

    public void setSensitivity(float value){sensitivity=clamp(value,.60f,1.80f);}
    public float getSensitivity(){return sensitivity;}

    public Result process(float[][] xs,float[][] ys,float[][] zs,long now){
        clearEvents();
        int count=xs==null?0:xs.length;
        int fist=-1,pointer=-1;

        for(int i=0;i<count;i++){
            if(valid(xs,ys,zs,i)&&isFist(xs[i],ys[i],zs[i])){fist=i;break;}
        }
        if(fist>=0){
            for(int i=0;i<count;i++){
                if(i==fist||!valid(xs,ys,zs,i))continue;
                float pinch=pinchRatio(xs[i],ys[i],zs[i]);
                if(isPointer(xs[i],ys[i],zs[i])||(active&&pinch<PINCH_OFF+.15f)){
                    pointer=i;break;
                }
            }
        }

        boolean pair=fist>=0&&pointer>=0;
        out.pairFound=pair;
        if(pair){
            lastPairMs=now;
            activationFrames=Math.min(ACTIVATE_FRAMES,activationFrames+1);
            if(!active&&activationFrames>=ACTIVATE_FRAMES){
                active=true;haveFingerReference=false;pinching=false;dragging=false;pinchOnFrames=pinchOffFrames=0;
            }
        }else{
            activationFrames=0;
        }

        if(active&&now-lastPairMs>PAIR_HOLD_MS){
            if(dragging){out.dragEnd=true;out.x=cx;out.y=cy;}
            active=false;pinching=false;dragging=false;haveFingerReference=false;
            pinchOnFrames=pinchOffFrames=0;
        }

        out.active=active;
        out.blockSwipes=active||pair||(lastPairMs>0&&now-lastPairMs<PAIR_HOLD_MS);

        if(!active){
            out.x=cx;out.y=cy;out.state=pair?"ARMING":"OFF";
            return out;
        }

        if(!pair){
            haveFingerReference=false;
            out.x=cx;out.y=cy;out.pinching=pinching;out.dragging=dragging;out.state="MOUSE_HOLD";
            return out;
        }

        float[] x=xs[pointer],y=ys[pointer],z=zs[pointer];
        float fingerX=x[INDEX_TIP],fingerY=y[INDEX_TIP];
        if(!haveFingerReference){
            lastFingerX=fingerX;lastFingerY=fingerY;haveFingerReference=true;
        }else{
            float dx=clamp(fingerX-lastFingerX,-MAX_FRAME_DELTA,MAX_FRAME_DELTA);
            float dy=clamp(fingerY-lastFingerY,-MAX_FRAME_DELTA,MAX_FRAME_DELTA);
            lastFingerX=fingerX;lastFingerY=fingerY;

            float dist=(float)Math.sqrt(dx*dx+dy*dy);
            if(dist>=POINTER_DEADZONE){
                float acceleration=dist>.045f?1.18f:(dist>.018f?1.05f:.88f);
                float gain=BASE_GAIN*sensitivity*acceleration;
                cx=clamp(cx+dx*gain,CURSOR_MIN,CURSOR_MAX);
                cy=clamp(cy+dy*gain,CURSOR_MIN,CURSOR_MAX);
            }
        }

        float ratio=pinchRatio(x,y,z);
        out.pinchRatio=ratio;out.x=cx;out.y=cy;
        boolean pinchCandidate=pinching?ratio<PINCH_OFF:ratio<PINCH_ON;

        if(!pinching){
            pinchOffFrames=0;
            if(pinchCandidate&&now>=clickCooldownUntil){
                pinchOnFrames++;
                if(pinchOnFrames>=2){
                    pinching=true;pinchStartMs=now;pinchOnFrames=0;
                }
            }else pinchOnFrames=0;
        }else{
            pinchOnFrames=0;
            if(!pinchCandidate){
                pinchOffFrames++;
                if(pinchOffFrames>=2){
                    long held=now-pinchStartMs;
                    if(dragging){out.dragEnd=true;dragging=false;}
                    else if(held>=55&&now>=clickCooldownUntil){out.tap=true;clickCooldownUntil=now+CLICK_COOLDOWN_MS;}
                    pinching=false;pinchOffFrames=0;
                }
            }else{
                pinchOffFrames=0;
                if(!dragging&&now-pinchStartMs>=DRAG_HOLD_MS){dragging=true;out.dragStart=true;}
                else if(dragging)out.dragMove=true;
            }
        }

        out.pinching=pinching;out.dragging=dragging;
        if(out.tap)out.state="CLICK";
        else if(out.dragStart)out.state="DRAG_START";
        else if(dragging)out.state="DRAG";
        else if(pinching)out.state="PINCH";
        else out.state="MOUSE";
        return out;
    }

    public Result onNoHands(long now){return process(null,null,null,now);}
    public boolean isActive(){return active;}

    public void reset(){
        active=pinching=dragging=haveFingerReference=false;activationFrames=pinchOnFrames=pinchOffFrames=0;
        lastPairMs=pinchStartMs=clickCooldownUntil=0;cx=cy=.5f;clearEvents();out.state="OFF";
    }

    private void clearEvents(){
        out.tap=out.dragStart=out.dragMove=out.dragEnd=false;
        out.pairFound=false;out.pinchRatio=9f;
    }

    private static boolean valid(float[][] xs,float[][] ys,float[][] zs,int i){
        return ys!=null&&zs!=null&&i>=0&&i<xs.length&&i<ys.length&&i<zs.length
                &&xs[i]!=null&&ys[i]!=null&&zs[i]!=null&&xs[i].length>=21&&ys[i].length>=21&&zs[i].length>=21;
    }

    private static boolean isFist(float[] x,float[] y,float[] z){
        int extended=0;
        if(fingerExtended(x,y,z,INDEX_MCP,INDEX_PIP,INDEX_TIP))extended++;
        if(fingerExtended(x,y,z,MIDDLE_MCP,MIDDLE_PIP,MIDDLE_TIP))extended++;
        if(fingerExtended(x,y,z,RING_MCP,RING_PIP,RING_TIP))extended++;
        if(fingerExtended(x,y,z,PINKY_MCP,PINKY_PIP,PINKY_TIP))extended++;
        return extended==0;
    }

    private static boolean isPointer(float[] x,float[] y,float[] z){
        if(!fingerExtended(x,y,z,INDEX_MCP,INDEX_PIP,INDEX_TIP))return false;
        int folded=0;
        if(!fingerExtended(x,y,z,MIDDLE_MCP,MIDDLE_PIP,MIDDLE_TIP))folded++;
        if(!fingerExtended(x,y,z,RING_MCP,RING_PIP,RING_TIP))folded++;
        if(!fingerExtended(x,y,z,PINKY_MCP,PINKY_PIP,PINKY_TIP))folded++;
        return folded>=2;
    }

    private static boolean fingerExtended(float[] x,float[] y,float[] z,int mcp,int pip,int tip){
        float ax=x[mcp]-x[pip],ay=y[mcp]-y[pip],az=z[mcp]-z[pip];
        float bx=x[tip]-x[pip],by=y[tip]-y[pip],bz=z[tip]-z[pip];
        float an=(float)Math.sqrt(ax*ax+ay*ay+az*az),bn=(float)Math.sqrt(bx*bx+by*by+bz*bz);
        if(an<1e-4f||bn<1e-4f)return false;
        float cos=(ax*bx+ay*by+az*bz)/(an*bn);
        float wristTip=dist3(x,y,z,WRIST,tip),wristPip=dist3(x,y,z,WRIST,pip);
        return cos<-.48f&&wristTip>wristPip*1.05f;
    }

    private static float pinchRatio(float[] x,float[] y,float[] z){
        float palm=Math.max(.025f,dist3(x,y,z,INDEX_MCP,PINKY_MCP));
        return dist3(x,y,z,THUMB_TIP,INDEX_TIP)/palm;
    }

    private static float dist3(float[] x,float[] y,float[] z,int a,int b){
        float dx=x[a]-x[b],dy=y[a]-y[b],dz=z[a]-z[b];
        return(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    private static float clamp(float v,float lo,float hi){return v<lo?lo:(v>hi?hi:v);}
}
