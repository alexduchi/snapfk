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
    private final boolean portrait;
    private final float userScale;
    private final int canvasW;
    private final int canvasH;
    private final int xPercent;
    private final int yPercent;

    SubtitleOverlay(List<Segment> segments) {
        this(segments, 1920, 1080, 1.0f, 50, 84);
    }

    SubtitleOverlay(List<Segment> segments, int videoWidth, int videoHeight, float userScale) {
        this(segments, videoWidth, videoHeight, userScale, 50, 84);
    }

    SubtitleOverlay(List<Segment> segments, int videoWidth, int videoHeight, float userScale, int xPercent, int yPercent) {
        super(false);
        this.segments = segments;
        this.userScale = clamp(userScale, 0.60f, 1.40f);
        this.xPercent = Math.max(0, Math.min(100, xPercent));
        this.yPercent = Math.max(0, Math.min(100, yPercent));

        int w = Math.max(1, videoWidth);
        int h = Math.max(1, videoHeight);
        portrait = h > w;

        // Keep the overlay light for the GPU, but preserve EXACTLY the video aspect ratio.
        // This prevents a wide subtitle canvas from being stretched over portrait videos.
        final int maxSide = 720;
        if (w >= h) {
            canvasW = maxSide;
            canvasH = Math.max(96, Math.round(maxSide * (h / (float) w)));
        } else {
            canvasH = maxSide;
            canvasW = Math.max(96, Math.round(maxSide * (w / (float) h)));
        }
        setCanvasSize(canvasW, canvasH);

        // Scale the low-resolution canvas back to the real video frame size.
        // Because the canvas has the same aspect ratio, x/y positions map directly to the video.
        float scaleX = w / (float) canvasW;
        float scaleY = h / (float) canvasH;
        settings = new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, 0f)
                .setOverlayFrameAnchor(0f, 0f)
                .setScale(scaleX, scaleY)
                .setAlphaScale(1f)
                .build();

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setShadowLayer(5f, 0f, 2f, Color.BLACK);
        bgPaint.setColor(0xB8000000);
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

        float shortSide = Math.min(canvasW, canvasH);
        float fontSize = shortSide * 0.050f * userScale;
        float minFont = Math.max(12f, shortSide * 0.032f);
        float maxFont = Math.max(minFont, shortSide * 0.070f);
        fontSize = clamp(fontSize, minFont, maxFont);

        float maxTextWidth = canvasW * (portrait ? 0.82f : 0.86f);
        int preferredLines = portrait ? 4 : 3;
        ArrayList<String> lines;

        while (true) {
            textPaint.setTextSize(fontSize);
            lines = wrap(value, maxTextWidth);
            if (lines.size() <= preferredLines || fontSize <= minFont + 0.5f) break;
            fontSize = Math.max(minFont, fontSize - 1.5f);
        }

        float lineHeight = fontSize * 1.22f;
        float maxLineWidth = 0f;
        for (String line : lines) maxLineWidth = Math.max(maxLineWidth, textPaint.measureText(line));

        float padX = Math.max(8f, fontSize * 0.52f);
        float padY = Math.max(5f, fontSize * 0.28f);
        float blockW = Math.min(canvasW - 8f, maxLineWidth + padX * 2f);
        float blockH = Math.min(canvasH - 8f, lines.size() * lineHeight + padY * 2f);

        float requestedX = canvasW * (xPercent / 100f);
        float requestedY = canvasH * (yPercent / 100f);
        float halfW = blockW / 2f;
        float halfH = blockH / 2f;

        // Never allow the subtitle box to leave the actual video frame.
        float centerX = clamp(requestedX, halfW + 4f, canvasW - halfW - 4f);
        float centerY = clamp(requestedY, halfH + 4f, canvasH - halfH - 4f);

        float left = centerX - halfW;
        float right = centerX + halfW;
        float top = centerY - halfH;
        float bottom = centerY + halfH;
        float radius = Math.max(5f, fontSize * 0.22f);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, bgPaint);

        float contentH = lines.size() * lineHeight;
        float firstBaseline = centerY - contentH / 2f + lineHeight - fontSize * 0.18f;
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
