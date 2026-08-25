package com.devicelab.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Locale;

public final class BubbleLevelView extends View {
    private float ax=Float.NaN, ay=Float.NaN, az=Float.NaN;
    private float zeroX=0,zeroY=0;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG), cross=new Paint(Paint.ANTI_ALIAS_FLAG);
    public BubbleLevelView(Context c){super(c);p.setStyle(Paint.Style.FILL);text.setTextAlign(Paint.Align.CENTER);text.setColor(Ui.TEXT);text.setTextSize(Ui.dp(c,19));text.setFakeBoldText(true);cross.setStyle(Paint.Style.STROKE);cross.setStrokeWidth(Ui.dp(c,1));cross.setColor(android.graphics.Color.argb(150,200,225,235));}
    public void update(float x,float y,float z){ax=x;ay=y;az=z;invalidate();}
    public void calibrate(){if(Float.isFinite(ax)&&Float.isFinite(ay)&&Float.isFinite(az)){float[] a=angles();zeroX=a[0];zeroY=a[1];invalidate();}}
    public float roll(){float[] a=angles();return a[0]-zeroX;}
    public float pitch(){float[] a=angles();return a[1]-zeroY;}
    private float[] angles(){if(!Float.isFinite(ax)||!Float.isFinite(ay)||!Float.isFinite(az))return new float[]{Float.NaN,Float.NaN};float roll=(float)Math.toDegrees(Math.atan2(ay,az));float pitch=(float)Math.toDegrees(Math.atan2(-ax,Math.sqrt(ay*ay+az*az)));return new float[]{roll,pitch};}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Ui.BG);int w=getWidth(),h=getHeight();float cx=w/2f,cy=h*.46f;float radius=Math.min(w*.42f,h*.32f);p.setColor(android.graphics.Color.rgb(20,43,38));c.drawCircle(cx,cy,radius,p);cross.setColor(android.graphics.Color.argb(180,91,224,145));for(int i=1;i<=3;i++)c.drawCircle(cx,cy,radius*i/3f,cross);c.drawLine(cx-radius,cy,cx+radius,cy,cross);c.drawLine(cx,cy-radius,cx,cy+radius,cross);float r=roll(),q=pitch();if(Float.isFinite(r)&&Float.isFinite(q)){float limit=12f;float bx=cx+Math.max(-1,Math.min(1,r/limit))*radius*.72f;float by=cy+Math.max(-1,Math.min(1,q/limit))*radius*.72f;float br=Ui.dp(getContext(),22);p.setColor(Math.abs(r)<.5f&&Math.abs(q)<.5f?Ui.GOOD:android.graphics.Color.rgb(203,235,85));c.drawCircle(bx,by,br,p);text.setTextSize(Ui.dp(getContext(),20));c.drawText(String.format(Locale.ROOT,"Roll  %.2f°    Pitch  %.2f°",r,q),cx,cy+radius+Ui.dp(getContext(),42),text);}else{c.drawText("Accéléromètre indisponible",cx,cy+radius+Ui.dp(getContext(),42),text);}}
}
