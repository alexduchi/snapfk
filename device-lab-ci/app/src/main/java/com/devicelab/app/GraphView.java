package com.devicelab.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.Locale;

public final class GraphView extends View {
    public interface Source {
        int size();
        float xAt(int i);
        float yAt(int i);
        float zAt(int i);
        boolean scalar();
    }

    private Source source;
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint px = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint py = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pz = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GraphView(Context c) {
        super(c);
        setMinimumHeight(Ui.dp(c, 160));
        grid.setStrokeWidth(Ui.dp(c, 1));
        grid.setColor(Ui.BORDER);
        text.setTextSize(Ui.dp(c, 10));
        text.setColor(Ui.MUTED);
        px.setStyle(Paint.Style.STROKE); px.setStrokeWidth(Ui.dp(c, 1.6f)); px.setColor(android.graphics.Color.rgb(255, 105, 104));
        py.setStyle(Paint.Style.STROKE); py.setStrokeWidth(Ui.dp(c, 1.6f)); py.setColor(android.graphics.Color.rgb(93, 224, 145));
        pz.setStyle(Paint.Style.STROKE); pz.setStrokeWidth(Ui.dp(c, 1.6f)); pz.setColor(Ui.ACCENT);
    }

    public void setSource(Source s) { source = s; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Ui.SURFACE);
        int w=getWidth(), h=getHeight();
        float l=Ui.dp(getContext(), 38), r=w-Ui.dp(getContext(), 8), t=Ui.dp(getContext(), 10), b=h-Ui.dp(getContext(), 20);
        for(int i=0;i<=4;i++){
            float y=t+(b-t)*i/4f; c.drawLine(l,y,r,y,grid);
            float x=l+(r-l)*i/4f; c.drawLine(x,t,x,b,grid);
        }
        if(source==null || source.size()<2){ c.drawText("En attente de mesures…", l, t+Ui.dp(getContext(),24), text); return; }
        int n=source.size();
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(int i=0;i<n;i++){
            float a=source.xAt(i); if(Float.isFinite(a)){min=Math.min(min,a);max=Math.max(max,a);}
            if(!source.scalar()){
                float y=source.yAt(i),z=source.zAt(i); if(Float.isFinite(y)){min=Math.min(min,y);max=Math.max(max,y);} if(Float.isFinite(z)){min=Math.min(min,z);max=Math.max(max,z);}
            }
        }
        if(!Float.isFinite(min)||!Float.isFinite(max)){ c.drawText("Mesure indisponible",l,t+Ui.dp(getContext(),24),text); return; }
        if(Math.abs(max-min)<1e-5f){min-=1;max+=1;} float pad=(max-min)*.08f;min-=pad;max+=pad;
        drawSeries(c, px, 0, n, l,r,t,b,min,max);
        if(!source.scalar()){drawSeries(c,py,1,n,l,r,t,b,min,max);drawSeries(c,pz,2,n,l,r,t,b,min,max);}
        c.drawText(String.format(Locale.ROOT,"%.2f",max),2,t+Ui.dp(getContext(),8),text);
        c.drawText(String.format(Locale.ROOT,"%.2f",min),2,b,text);
        c.drawText("récent",r-Ui.dp(getContext(),34),h-Ui.dp(getContext(),4),text);
    }

    private void drawSeries(Canvas c, Paint p, int axis, int n, float l,float r,float t,float b,float min,float max){
        Path path=new Path(); boolean started=false;
        for(int i=0;i<n;i++){
            float v=axis==0?source.xAt(i):axis==1?source.yAt(i):source.zAt(i);
            if(!Float.isFinite(v)) continue;
            float x=l+(r-l)*(n<=1?0:(i/(float)(n-1)));
            float y=b-(v-min)*(b-t)/(max-min);
            if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);
        }
        if(started)c.drawPath(path,p);
    }
}
