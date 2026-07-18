package com.sentry.local;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class OpticalScannerActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Camera camera;
    private SurfaceView preview;
    private Overlay overlay;
    private TextView status;
    private final List<FramePoint> history = new ArrayList<>();
    private long lastAnalysis;
    private int frameWidth;
    private int frameHeight;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        root.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = new Overlay();
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(14));
        panel.setBackgroundColor(Color.argb(195, 2, 8, 12));
        TextView title = label("SCANNER OPTIQUE", 18, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        status = label("Recherche de points lumineux et de scintillement…", 12, Color.rgb(210,235,240), false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(5), 0, dp(8));
        panel.addView(status);
        TextView close = label("FERMER", 13, Color.rgb(2,24,22), true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(10), dp(12), dp(10), dp(12));
        close.setBackgroundColor(Color.rgb(61,235,211));
        close.setOnClickListener(v -> finish());
        panel.addView(close);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        pp.setMargins(dp(10), dp(10), dp(10), dp(10));
        root.addView(panel, pp);
        setContentView(root);
    }

    @Override protected void onPause() { stopCamera(); super.onPause(); }
    @Override public void surfaceCreated(SurfaceHolder holder) { startCamera(holder); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { restartPreview(holder); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { stopCamera(); }

    private void startCamera(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            Camera.Parameters p = camera.getParameters();
            Camera.Size chosen = chooseSize(p.getSupportedPreviewSizes());
            if (chosen != null) p.setPreviewSize(chosen.width, chosen.height);
            List<String> focus = p.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            p.setPreviewFormat(android.graphics.ImageFormat.NV21);
            camera.setParameters(p);
            Camera.Size actual = camera.getParameters().getPreviewSize();
            frameWidth = actual.width;
            frameHeight = actual.height;
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "Caméra indisponible : " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void restartPreview(SurfaceHolder holder) {
        if (camera == null) return;
        try { camera.stopPreview(); camera.setPreviewDisplay(holder); camera.setPreviewCallback(this); camera.startPreview(); } catch (Exception ignored) { }
    }

    private void stopCamera() {
        try { if (camera != null) { camera.setPreviewCallback(null); camera.stopPreview(); camera.release(); } } catch (Exception ignored) { }
        camera = null;
    }

    private Camera.Size chooseSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;
        Camera.Size best = sizes.get(0);
        for (Camera.Size s : sizes) {
            int pixels = s.width * s.height;
            int bestPixels = best.width * best.height;
            if (pixels <= 1280 * 720 && pixels > bestPixels) best = s;
        }
        return best;
    }

    @Override public void onPreviewFrame(byte[] data, Camera camera) {
        long now = System.nanoTime();
        if (now - lastAnalysis < 33_000_000L || frameWidth <= 0 || frameHeight <= 0) return;
        lastAnalysis = now;
        int step = 8;
        long sum = 0;
        int count = 0;
        int max = -1;
        int maxX = frameWidth / 2;
        int maxY = frameHeight / 2;
        long weightedX = 0;
        long weightedY = 0;
        long weighted = 0;
        for (int y = 0; y < frameHeight; y += step) {
            int row = y * frameWidth;
            for (int x = 0; x < frameWidth; x += step) {
                int lum = data[row + x] & 255;
                sum += lum;
                count++;
                if (lum > max) { max = lum; maxX = x; maxY = y; }
                if (lum >= 225) {
                    int weight = lum - 220;
                    weightedX += (long)x * weight;
                    weightedY += (long)y * weight;
                    weighted += weight;
                }
            }
        }
        float avg = count == 0 ? 0 : sum / (float)count;
        if (weighted > 0) { maxX = (int)(weightedX / weighted); maxY = (int)(weightedY / weighted); }
        history.add(new FramePoint(now, avg));
        while (!history.isEmpty() && now - history.get(0).time > 2_000_000_000L) history.remove(0);
        Flicker flicker = analyseFlicker(history);
        float nx = maxX / (float)frameWidth;
        float ny = maxY / (float)frameHeight;
        boolean hotspot = max >= 238 && max > avg + 45;
        overlay.update(nx, ny, hotspot, max, avg, flicker.frequency, flicker.confidence);

        final int shownMax = max;
        final boolean shownHotspot = hotspot;
        final float shownFrequency = flicker.frequency;
        final int shownConfidence = flicker.confidence;
        ui.post(() -> {
            String point = shownHotspot ? "Point lumineux détecté" : "Aucun point lumineux net";
            String freq = shownFrequency > 0 ? String.format(Locale.FRANCE, " · scintillement ≈ %.1f Hz", shownFrequency) : "";
            status.setText(point + " · pic " + shownMax + "/255" + freq + "\nConfiance scintillement " + shownConfidence + "% · résultat dépendant de la caméra");
        });
    }

    private Flicker analyseFlicker(List<FramePoint> points) {
        if (points.size() < 24) return new Flicker(0, 0);
        int n = points.size();
        double duration = (points.get(n - 1).time - points.get(0).time) / 1_000_000_000.0;
        if (duration < .8) return new Flicker(0, 0);
        double rate = (n - 1) / duration;
        double mean = 0;
        for (FramePoint p : points) mean += p.value;
        mean /= n;
        double variance = 0;
        for (FramePoint p : points) { double d = p.value - mean; variance += d * d; }
        variance /= n;
        if (variance < .8) return new Flicker(0, 3);
        double bestPower = 0;
        double bestFreq = 0;
        double totalPower = 1;
        double maxFreq = Math.min(15, rate / 2 - .5);
        for (double f = 1; f <= maxFreq; f += .25) {
            double re = 0, im = 0;
            for (int i = 0; i < n; i++) {
                double value = points.get(i).value - mean;
                double t = (points.get(i).time - points.get(0).time) / 1_000_000_000.0;
                double a = 2 * Math.PI * f * t;
                re += value * Math.cos(a);
                im -= value * Math.sin(a);
            }
            double power = re * re + im * im;
            totalPower += power;
            if (power > bestPower) { bestPower = power; bestFreq = f; }
        }
        int confidence = (int)Math.max(0, Math.min(90, bestPower / totalPower * 900));
        return confidence >= 18 ? new Flicker((float)bestFreq, confidence) : new Flicker(0, confidence);
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private final class Overlay extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float nx = .5f, ny = .5f, avg, frequency;
        int peak, confidence;
        boolean hotspot;
        Overlay() { super(OpticalScannerActivity.this); setWillNotDraw(false); }
        void update(float x, float y, boolean hot, int peak, float avg, float frequency, int confidence) {
            nx = x; ny = y; hotspot = hot; this.peak = peak; this.avg = avg; this.frequency = frequency; this.confidence = confidence; invalidate();
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float x = getWidth() * (1f - ny);
            float y = getHeight() * nx;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(3));
            p.setColor(hotspot ? Color.rgb(83,228,144) : Color.rgb(247,190,80));
            float pulse = dp(24) + dp(8) * (float)Math.sin(System.currentTimeMillis() / 110.0);
            c.drawCircle(x, y, pulse, p);
            c.drawLine(x - dp(36), y, x + dp(36), y, p);
            c.drawLine(x, y - dp(36), x, y + dp(36), p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(dp(12));
            p.setColor(Color.WHITE);
            c.drawText(hotspot ? "SOURCE PROBABLE" : "ZONE LA PLUS CLAIRE", x, y + dp(54), p);
            if (frequency > 0) c.drawText(String.format(Locale.FRANCE, "≈ %.1f Hz", frequency), x, y + dp(72), p);
            postInvalidateDelayed(32);
        }
    }

    private static final class FramePoint { final long time; final float value; FramePoint(long t, float v) { time = t; value = v; } }
    private static final class Flicker { final float frequency; final int confidence; Flicker(float f, int c) { frequency = f; confidence = c; } }
}