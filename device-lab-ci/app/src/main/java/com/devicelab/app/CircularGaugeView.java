package com.devicelab.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public final class CircularGaugeView extends View {
    private float value = Float.NaN;
    private String label = "—";
    private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG), arc=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG), sub=new Paint(Paint.ANTI_ALIAS_FLAG);
    public CircularGaugeView(Context c){
        super(c); setMinimumHeight(Ui.dp(c,190));
        track.setStyle(Paint.Style.STROKE); track.setStrokeWidth(Ui.dp(c,15)); track.setStrokeCap(Paint.Cap.ROUND); track.setColor(Ui.BORDER);
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeWidth(Ui.dp(c,15)); arc.setStrokeCap(Paint.Cap.ROUND); arc.setColor(Ui.GOOD);
        text.setTextAlign(Paint.Align.CENTER);text.setColor(Ui.TEXT);text.setTextSize(Ui.dp(c,34));text.setFakeBoldText(true);
        sub.setTextAlign(Paint.Align.CENTER);sub.setColor(Ui.MUTED);sub.setTextSize(Ui.dp(c,12));
    }
    public void setValue(float v,String l){value=v;label=l==null?"—":l;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();float s=Math.min(w,h)-Ui.dp(getContext(),34);RectF r=new RectF((w-s)/2,(h-s)/2,(w+s)/2,(h+s)/2);c.drawArc(r,-220,260,false,track);if(Float.isFinite(value)){float cl=Math.max(0,Math.min(100,value));arc.setColor(cl<20?Ui.BAD:cl<40?Ui.WARN:Ui.GOOD);c.drawArc(r,-220,260*cl/100f,false,arc);}c.drawText(label,w/2f,h/2f+Ui.dp(getContext(),8),text);c.drawText("BATTERIE",w/2f,h/2f+Ui.dp(getContext(),34),sub);}
}
