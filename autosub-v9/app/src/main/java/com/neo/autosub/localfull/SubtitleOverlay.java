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

    private static final int CANVAS_W = 1280;
    private static final int CANVAS_H = 320;

    private final List<Segment> segments;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OverlaySettings settings;

    SubtitleOverlay(List<Segment> segments) {
        super(false);
        setCanvasSize(CANVAS_W, CANVAS_H);
        this.segments = segments;

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setShadowLayer(7f, 0f, 2f, Color.BLACK);
        bgPaint.setColor(0xD5000000);

        settings = new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, -0.78f)
                .setOverlayFrameAnchor(0f, 0f)
                .setScale(0.92f, 0.92f)
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

        double time = presentationTimeUs / 1_000_000.0;
        String value = null;
        for (Segment segment : segments) {
            if (time >= segment.start && time < segment.end) {
                value = segment.text;
                break;
            }
        }
        if (value == null || value.trim().isEmpty()) return;

        value = value.trim();
        final float fontSize = 58f;
        textPaint.setTextSize(fontSize);

        ArrayList<String> lines = wrap(value, CANVAS_W * 0.82f);
        if (lines.size() > 3) {
            ArrayList<String> compact = new ArrayList<>();
            compact.add(lines.get(0));
            compact.add(lines.get(1));
            StringBuilder rest = new StringBuilder(lines.get(2));
            for (int i = 3; i < lines.size(); i++) rest.append(' ').append(lines.get(i));
            compact.add(rest.toString());
            lines = compact;
        }

        float lineHeight = fontSize * 1.22f;
        float centerX = CANVAS_W / 2f;
        float blockHeight = lines.size() * lineHeight;
        float firstBaseline = CANVAS_H / 2f - blockHeight / 2f + lineHeight - 8f;

        float maxLineWidth = 0f;
        for (String line : lines) maxLineWidth = Math.max(maxLineWidth, textPaint.measureText(line));
        float padX = 34f;
        float padY = 22f;
        float left = Math.max(22f, centerX - maxLineWidth / 2f - padX);
        float right = Math.min(CANVAS_W - 22f, centerX + maxLineWidth / 2f + padX);
        float top = firstBaseline - fontSize - padY;
        float bottom = firstBaseline + (lines.size() - 1) * lineHeight + 18f + padY;

        canvas.drawRoundRect(new RectF(left, top, right, bottom), 24f, 24f, bgPaint);
        for (int i = 0; i < lines.size(); i++) {
            canvas.drawText(lines.get(i), centerX, firstBaseline + i * lineHeight, textPaint);
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
