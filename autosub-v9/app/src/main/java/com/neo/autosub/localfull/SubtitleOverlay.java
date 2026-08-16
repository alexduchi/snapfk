package com.neo.autosub.localfull;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.CanvasOverlay;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
final class SubtitleOverlay extends CanvasOverlay {
    static final class Segment {
        final double start,end; final String text;
        Segment(double s,double e,String t){start=s;end=e;text=t;}
    }
    private final List<Segment> segments;
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);
    SubtitleOverlay(List<Segment> segments){
        super(true); this.segments=segments;
        text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER); text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setShadowLayer(5,0,2,Color.BLACK); bg.setColor(0xB0000000);
    }
    @Override public synchronized void onDraw(Canvas canvas,long presentationTimeUs){
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
        double t=presentationTimeUs/1_000_000.0; String s=null;
        for(Segment g:segments){ if(t>=g.start && t<=g.end){s=g.text;break;} }
        if(s==null||s.trim().isEmpty()) return;
        float size=Math.max(28f,canvas.getWidth()*0.045f); text.setTextSize(size);
        float max=canvas.getWidth()*0.86f;
        java.util.ArrayList<String> lines=new java.util.ArrayList<>();
        StringBuilder line=new StringBuilder();
        for(String w:s.trim().split("\\s+")){
            String test=line.length()==0?w:line+" "+w;
            if(text.measureText(test)>max && line.length()>0){lines.add(line.toString());line=new StringBuilder(w);} else {line=new StringBuilder(test);}
        }
        if(line.length()>0) lines.add(line.toString());
        float lineH=size*1.22f; float bottom=canvas.getHeight()*0.91f; float top=bottom-lines.size()*lineH-size*0.25f;
        canvas.drawRoundRect(new RectF(canvas.getWidth()*0.05f,top-size*0.3f,canvas.getWidth()*0.95f,bottom+size*0.25f),size*0.22f,size*0.22f,bg);
        for(int i=0;i<lines.size();i++) canvas.drawText(lines.get(i),canvas.getWidth()/2f,top+(i+1)*lineH,text);
    }
}
