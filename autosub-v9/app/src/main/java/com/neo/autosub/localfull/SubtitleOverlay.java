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
    private static final int CANVAS_H = 360;

    private final List<Segment> segments;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OverlaySettings settings;
    private final boolean portrait;
    private final float userScale;
    private final float baseFontSize;
    private final float maxTextWidth;

    SubtitleOverlay(List<Segment> segments) {
        this(segments, 1920, 1080, 1.0f, 50, 84);
    }

    SubtitleOverlay(List<Segment> segments, int videoWidth, int videoHeight, float userScale) {
        this(segments, videoWidth, videoHeight, userScale, 50, 84);
    }

    SubtitleOverlay(List<Segment> segments, int videoWidth, int videoHeight, float userScale, int xPercent, int yPercent) {
        super(false);
        setCanvasSize(CANVAS_W, CANVAS_H);
        this.segments = segments;
        this.userScale = Math.max(0.60f, Math.min(1.50f, userScale));

        int w = Math.max(1, videoWidth);
        int h = Math.max(1, videoHeight);
        float aspect = w / (float) h;
        portrait = h > w;

        float overlayScale;
        if (aspect < 0.68f) {
            baseFontSize = 38f;
            maxTextWidth = CANVAS_W * 0.76f;
            overlayScale = 0.78f;
        } else if (aspect < 1.0f) {
            baseFontSize = 44f;
            maxTextWidth = CANVAS_W * 0.80f;
            overlayScale = 0.84f;
        } else {
            baseFontSize = 56f;
            maxTextWidth = CANVAS_W * 0.84f;
            overlayScale = 0.92f;
        }

        float xAnchor = clamp((xPercent - 50) / 50f, -0.94f, 0.94f);
        float yAnchor = clamp(1f - (Math.max(0, Math.min(100, yPercent)) / 50f), -0.94f, 0.94f);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setShadowLayer(7f, 0f, 2f, Color.BLACK);
        bgPaint.setColor(0xD5000000);

        settings = new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(xAnchor, yAnchor)
                .setOverlayFrameAnchor(0f, 0f)
                .setScale(overlayScale, overlayScale)
                .setAlphaScale(1f)
                .build();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
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

        float minFont = portrait ? 27f : 32f;
        float fontSize = Math.max(minFont, baseFontSize * userScale);
        ArrayList<String> lines;
        int preferredLines = portrait ? 4 : 3;

        while (true) {
            textPaint.setTextSize(fontSize);
            lines = wrap(value, maxTextWidth);
            if (lines.size() <= preferredLines || fontSize <= minFont) break;
            fontSize -= 2f;
        }

        float lineHeight = fontSize * 1.18f;
        float centerX = CANVAS_W / 2f;
        float blockHeight = Math.max(lineHeight, lines.size() * lineHeight);
        float firstBaseline = CANVAS_H / 2f - blockHeight / 2f + lineHeight - fontSize * 0.12f;

        float maxLineWidth = 0f;
        for (String line : lines) maxLineWidth = Math.max(maxLineWidth, textPaint.measureText(line));
        float padX = Math.max(24f, fontSize * 0.58f);
        float padY = Math.max(16f, fontSize * 0.34f);
        float left = Math.max(18f, centerX - maxLineWidth / 2f - padX);
        float right = Math.min(CANVAS_W - 18f, centerX + maxLineWidth / 2f + padX);
        float top = Math.max(8f, firstBaseline - fontSize - padY);
        float bottom = Math.min(CANVAS_H - 8f, firstBaseline + (lines.size() - 1) * lineHeight + fontSize * 0.30f + padY);

        canvas.drawRoundRect(new RectF(left, top, right, bottom), 22f, 22f, bgPaint);
        for (int i = 0; i < lines.size(); i++) {
            float y = firstBaseline + i * lineHeight;
            if (y <= CANVAS_H - 10f) canvas.drawText(lines.get(i), centerX, y, textPaint);
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
