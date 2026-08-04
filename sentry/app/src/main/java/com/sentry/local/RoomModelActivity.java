package com.sentry.local;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Lightweight interactive perspective viewer for the latest spatial scan. */
public class RoomModelActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        String raw = getSharedPreferences("sentry_spatial_scans", MODE_PRIVATE).getString("latest", null);
        if (raw == null) {
            Toast.makeText(this, "Aucun scan disponible", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        try {
            setContentView(new RoomView(parse(raw)));
        } catch (Exception e) {
            Toast.makeText(this, "Modèle 3D illisible", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private Model parse(String raw) throws Exception {
        JSONObject o = new JSONObject(raw);
        JSONArray pts = o.getJSONArray("floor_points");
        Model m = new Model();
        m.height = (float) o.optDouble("height_m", 2.5);
        m.area = (float) o.optDouble("area_m2", 0);
        m.volume = (float) o.optDouble("volume_m3", 0);
        for (int i = 0; i < pts.length(); i++) {
            JSONArray p = pts.getJSONArray(i);
            m.points.add(new P((float)p.getDouble(0), (float)p.getDouble(2)));
        }
        return m;
    }

    private static final class P { float x,z; P(float x,float z){this.x=x;this.z=z;} }
    private static final class Model { final List<P> points = new ArrayList<>(); float height,area,volume; }

    private final class RoomView extends View {
        final Model model;
        final Paint line = new Paint(3), fill = new Paint(3), text = new Paint(3);
        float yaw = -28f, pitch = 22f, zoom = 1f, lastX, lastY, lastDist;
        boolean pinching;

        RoomView(Model m) {
            super(RoomModelActivity.this);
            model = m;
            line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(4f); line.setColor(Color.rgb(82,235,214));
            fill.setStyle(Paint.Style.FILL); fill.setColor(Color.argb(72,72,166,255));
            text.setColor(Color.WHITE); text.setTextSize(38f); text.setAntiAlias(true);
            setBackgroundColor(Color.rgb(2,8,14));
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (model.points.size() < 3) return;
            float cx = 0, cz = 0;
            for (P p : model.points) { cx += p.x; cz += p.z; }
            cx /= model.points.size(); cz /= model.points.size();
            float max = .1f;
            for (P p : model.points) max = Math.max(max, (float)Math.hypot(p.x-cx,p.z-cz));
            float scale = Math.min(getWidth(), getHeight()) * .32f / max * zoom;
            float y0 = getHeight() * .62f, y1 = y0 - model.height * scale * .34f;
            float rad = (float)Math.toRadians(yaw), cp = (float)Math.cos(Math.toRadians(pitch));
            float[] bx = new float[model.points.size()], by = new float[model.points.size()];
            float[] tx = new float[model.points.size()], ty = new float[model.points.size()];
            for (int i=0;i<model.points.size();i++) {
                P p=model.points.get(i); float x=p.x-cx,z=p.z-cz;
                float rx=x*(float)Math.cos(rad)-z*(float)Math.sin(rad);
                float rz=x*(float)Math.sin(rad)+z*(float)Math.cos(rad);
                bx[i]=getWidth()/2f+rx*scale; by[i]=y0+rz*scale*.42f*cp;
                tx[i]=bx[i]; ty[i]=y1+rz*scale*.20f*cp;
            }
            Path floor=new Path(); floor.moveTo(bx[0],by[0]); for(int i=1;i<bx.length;i++) floor.lineTo(bx[i],by[i]); floor.close(); c.drawPath(floor,fill); c.drawPath(floor,line);
            Path roof=new Path(); roof.moveTo(tx[0],ty[0]); for(int i=1;i<tx.length;i++) roof.lineTo(tx[i],ty[i]); roof.close(); c.drawPath(roof,line);
            for(int i=0;i<bx.length;i++){int j=(i+1)%bx.length;Path wall=new Path();wall.moveTo(bx[i],by[i]);wall.lineTo(bx[j],by[j]);wall.lineTo(tx[j],ty[j]);wall.lineTo(tx[i],ty[i]);wall.close();c.drawPath(wall,fill);c.drawPath(wall,line);}
            text.setTextSize(42f); c.drawText("MODÈLE 3D",32,60,text);
            text.setTextSize(27f); text.setColor(Color.LTGRAY);
            c.drawText(String.format(java.util.Locale.FRANCE,"%.2f m²  ·  %.2f m³",model.area,model.volume),32,98,text);
            c.drawText("Glisse pour tourner · pince pour zoomer · double tap vue du dessus",32,getHeight()-38,text);
            text.setColor(Color.WHITE);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getPointerCount() == 2) {
                float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1), d=(float)Math.hypot(dx,dy);
                if (!pinching) { pinching=true; lastDist=d; }
                else if (lastDist>0) { zoom=Math.max(.5f,Math.min(3f,zoom*d/lastDist)); lastDist=d; invalidate(); }
                return true;
            }
            if (e.getAction()==MotionEvent.ACTION_DOWN){pinching=false;lastX=e.getX();lastY=e.getY();return true;}
            if (e.getAction()==MotionEvent.ACTION_MOVE){yaw+=(e.getX()-lastX)*.22f;pitch=Math.max(0,Math.min(70,pitch-(e.getY()-lastY)*.15f));lastX=e.getX();lastY=e.getY();invalidate();return true;}
            if (e.getAction()==MotionEvent.ACTION_UP && e.getEventTime()-e.getDownTime()<220){performClick();}
            return true;
        }

        @Override public boolean performClick(){super.performClick();pitch=70f;invalidate();return true;}
    }
}
