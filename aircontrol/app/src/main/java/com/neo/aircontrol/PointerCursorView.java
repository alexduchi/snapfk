package com.neo.aircontrol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public final class PointerCursorView extends View {
    private final Paint halo=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outer=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean pressed;

    public PointerCursorView(Context context){
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        halo.setStyle(Paint.Style.FILL);
        outer.setStyle(Paint.Style.FILL);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(dp(2.4f));
        dot.setStyle(Paint.Style.FILL);
    }

    public void setPressed(boolean value){if(pressed!=value){pressed=value;invalidate();}}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float r=Math.min(getWidth(),getHeight())*.31f;
        halo.setColor(pressed?0x553B82F6:0x443B82F6);
        halo.setShadowLayer(dp(7),0,0,pressed?0xAA2563EB:0x883B82F6);
        c.drawCircle(cx,cy,r+dp(4),halo);
        halo.clearShadowLayer();
        outer.setColor(pressed?0xFF2563EB:0xFFF8FBFF);
        c.drawCircle(cx,cy,r,outer);
        inner.setColor(pressed?0xFFFFFFFF:0xFF2563EB);
        c.drawCircle(cx,cy,r-dp(1.5f),inner);
        dot.setColor(pressed?0xFFFFFFFF:0xFF0F172A);
        c.drawCircle(cx,cy,dp(3.2f),dot);
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
