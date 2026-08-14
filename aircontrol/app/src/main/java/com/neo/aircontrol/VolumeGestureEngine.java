package com.neo.aircontrol;

public final class VolumeGestureEngine {
    public static final class Result {
        public boolean poseFound, active, blockOther, volumeUp, volumeDown;
        public float handY=.5f, deltaY=0f;
        public String state="OFF";
    }

    private static final int WRIST=0;
    private static final int INDEX_MCP=5, INDEX_PIP=6, INDEX_TIP=8;
    private static final int MIDDLE_MCP=9, MIDDLE_PIP=10, MIDDLE_TIP=12;
    private static final int RING_MCP=13, RING_PIP=14, RING_TIP=16;
    private static final int PINKY_MCP=17, PINKY_PIP=18, PINKY_TIP=20;

    private static final int ACTIVATE_FRAMES=4;
    private static final long HOLD_AFTER_LOSS_MS=260;
    private static final long STEP_COOLDOWN_MS=260;
    private static final float MOVE_THRESHOLD=.075f;
    private static final float REARM_THRESHOLD=.030f;

    private final Result out=new Result();
    private int poseFrames;
    private boolean active, armed=true, haveBaseline;
    private long lastPoseMs,lastStepMs;
    private float baselineY=.5f,filteredY=.5f;

    public Result process(float[][] xs,float[][] ys,float[][] zs,long now){
        clearEvents();
        int hand=findThreeFingerHand(xs,ys,zs);
        boolean pose=hand>=0;
        out.poseFound=pose;

        if(pose){
            lastPoseMs=now;
            poseFrames=Math.min(ACTIVATE_FRAMES,poseFrames+1);
            float y=handCenterY(ys[hand]);
            if(!active&&poseFrames>=ACTIVATE_FRAMES){
                active=true;haveBaseline=true;baselineY=y;filteredY=y;armed=true;
            }
            if(active){
                filteredY += (y-filteredY)*.34f;
                if(!haveBaseline){baselineY=filteredY;haveBaseline=true;}
                float dy=filteredY-baselineY;
                out.handY=filteredY;out.deltaY=dy;

                if(!armed){
                    if(Math.abs(dy)<REARM_THRESHOLD){armed=true;baselineY=filteredY;}
                }else if(now-lastStepMs>=STEP_COOLDOWN_MS){
                    if(dy<=-MOVE_THRESHOLD){
                        out.volumeUp=true;lastStepMs=now;armed=false;baselineY=filteredY;
                    }else if(dy>=MOVE_THRESHOLD){
                        out.volumeDown=true;lastStepMs=now;armed=false;baselineY=filteredY;
                    }
                }
            }
        }else{
            poseFrames=0;
            if(active&&now-lastPoseMs>HOLD_AFTER_LOSS_MS){
                active=false;haveBaseline=false;armed=true;
            }
        }

        out.active=active;
        out.blockOther=active||pose||(lastPoseMs>0&&now-lastPoseMs<HOLD_AFTER_LOSS_MS);
        if(out.volumeUp)out.state="VOL_UP";
        else if(out.volumeDown)out.state="VOL_DOWN";
        else if(active)out.state="VOLUME";
        else if(pose)out.state="ARMING";
        else out.state="OFF";
        return out;
    }

    public Result onNoHands(long now){return process(null,null,null,now);}

    public void reset(){
        poseFrames=0;active=false;armed=true;haveBaseline=false;
        lastPoseMs=lastStepMs=0;baselineY=filteredY=.5f;clearEvents();out.state="OFF";
    }

    private void clearEvents(){out.volumeUp=out.volumeDown=false;out.poseFound=false;out.deltaY=0f;}

    private static int findThreeFingerHand(float[][] xs,float[][] ys,float[][] zs){
        if(xs==null||ys==null||zs==null)return -1;
        int n=Math.min(xs.length,Math.min(ys.length,zs.length));
        for(int i=0;i<n;i++){
            if(valid(xs[i],ys[i],zs[i])&&isThreeFingerPose(xs[i],ys[i],zs[i]))return i;
        }
        return -1;
    }

    private static boolean isThreeFingerPose(float[] x,float[] y,float[] z){
        boolean index=fingerExtended(x,y,z,INDEX_MCP,INDEX_PIP,INDEX_TIP);
        boolean middle=fingerExtended(x,y,z,MIDDLE_MCP,MIDDLE_PIP,MIDDLE_TIP);
        boolean ring=fingerExtended(x,y,z,RING_MCP,RING_PIP,RING_TIP);
        boolean pinky=fingerExtended(x,y,z,PINKY_MCP,PINKY_PIP,PINKY_TIP);
        if(!index||!middle||!ring||pinky)return false;

        float ix=x[INDEX_TIP]-x[INDEX_MCP], iy=y[INDEX_TIP]-y[INDEX_MCP];
        float mx=x[MIDDLE_TIP]-x[MIDDLE_MCP], my=y[MIDDLE_TIP]-y[MIDDLE_MCP];
        float rx=x[RING_TIP]-x[RING_MCP], ry=y[RING_TIP]-y[RING_MCP];
        float in=norm2(ix,iy),mn=norm2(mx,my),rn=norm2(rx,ry);
        if(in<.04f||mn<.04f||rn<.04f)return false;
        float im=(ix*mx+iy*my)/(in*mn);
        float mr=(mx*rx+my*ry)/(mn*rn);
        if(im<.86f||mr<.86f)return false;

        float tipSpread=Math.max(Math.abs(y[INDEX_TIP]-y[MIDDLE_TIP]),Math.abs(y[MIDDLE_TIP]-y[RING_TIP]));
        float palm=Math.max(.04f,dist2(x,y,INDEX_MCP,PINKY_MCP));
        return tipSpread < palm*.62f;
    }

    private static float handCenterY(float[] y){
        return (y[INDEX_MCP]+y[MIDDLE_MCP]+y[RING_MCP]+y[WRIST])*.25f;
    }

    private static boolean fingerExtended(float[] x,float[] y,float[] z,int mcp,int pip,int tip){
        float ax=x[mcp]-x[pip],ay=y[mcp]-y[pip],az=z[mcp]-z[pip];
        float bx=x[tip]-x[pip],by=y[tip]-y[pip],bz=z[tip]-z[pip];
        float an=(float)Math.sqrt(ax*ax+ay*ay+az*az),bn=(float)Math.sqrt(bx*bx+by*by+bz*bz);
        if(an<1e-4f||bn<1e-4f)return false;
        float cos=(ax*bx+ay*by+az*bz)/(an*bn);
        float wristTip=dist3(x,y,z,WRIST,tip),wristPip=dist3(x,y,z,WRIST,pip);
        return cos<-.46f&&wristTip>wristPip*1.04f;
    }

    private static boolean valid(float[] x,float[] y,float[] z){return x!=null&&y!=null&&z!=null&&x.length>=21&&y.length>=21&&z.length>=21;}
    private static float norm2(float x,float y){return(float)Math.sqrt(x*x+y*y);}
    private static float dist2(float[] x,float[] y,int a,int b){float dx=x[a]-x[b],dy=y[a]-y[b];return(float)Math.sqrt(dx*dx+dy*dy);}
    private static float dist3(float[] x,float[] y,float[] z,int a,int b){float dx=x[a]-x[b],dy=y[a]-y[b],dz=z[a]-z[b];return(float)Math.sqrt(dx*dx+dy*dy+dz*dz);}
}
