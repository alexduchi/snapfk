package com.neo.autosub.localfull;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.annotation.OptIn;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.CanvasOverlay;
import androidx.media3.effect.StaticOverlaySettings;

import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
final class SubtitleOverlay extends CanvasOverlay {
    static final class Segment {
        final double start, end;
        final String text;
        Segment(double s, double e, String t) { start = s; end = e; text = t; }
    }

    private final List<Segment> segments;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OverlaySettings settings;

    SubtitleOverlay(List<Segment> segments) {
        super(true);
        this.segments = segments;
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setShadowLayer(6f, 0f, 2f, Color.BLACK);
        bgPaint.setColor(0xCC000000);
        settings = new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, 0f)
                .setOverlayFrameAnchor(0f, 0f)
                .setAlphaScale(1f)
                .build();
    }

    @Override
    public OverlaySettings getOverlaySettings(long presentationTimeUs) {
        return settings;
    }

    @Override
    public synchronized void onDraw(Canvas canvas, long presentationTimeUs) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        double t = presentationTimeUs / 1_000_000.0;
        String value = null;
        for (Segment s : segments) {
            if (t >= s.start && t < s.end) {
                value = s.text;
                break;
            }
        }
        if (value == null || value.trim().isEmpty()) return;

        value = value.trim();
        float width = canvas.getWidth();
        float height = canvas.getHeight();
        float size = Math.max(34f, Math.min(82f, width * 0.05f));
        textPaint.setTextSize(size);

        ArrayList<String> lines = wrap(value, width * 0.84f);
        float lineHeight = size * 1.22f;
        float bottom = height * 0.90f;
        float firstBaseline = bottom - (lines.size() - 1) * lineHeight;

        float maxLine = 0f;
        for (String line : lines) maxLine = Math.max(maxLine, textPaint.measureText(line));
        float padX = size * 0.55f;
        float padY = size * 0.34f;
        float left = Math.max(width * 0.04f, width / 2f - maxLine / 2f - padX);
        float right = Math.min(width * 0.96f, width / 2f + maxLine / 2f + padX);
        float top = firstBaseline - size - padY;
        float bgBottom = firstBaseline + (lines.size() - 1) * lineHeight + size * 0.30f + padY;

        canvas.drawRoundRect(new RectF(left, top, right, bgBottom), size * 0.22f, size * 0.22f, bgPaint);
        for (int i = 0; i < lines.size(); i++) {
            canvas.drawText(lines.get(i), width / 2f, firstBaseline + i * lineHeight, textPaint);
        }
    }

    private ArrayList<String> wrap(String input, float maxWidth) {
        ArrayList<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : input.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (textPaint.measureText(candidate) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
