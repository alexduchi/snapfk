package com.neo.autosub.localfull;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import androidx.annotation.OptIn;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.CanvasOverlay;
import androidx.media3.effect.StaticOverlaySettings;

import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
final class SubtitleCanvasOverlay extends CanvasOverlay {
    private final List<SubtitleOverlay.Segment> segments;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final OverlaySettings settings;

    SubtitleCanvasOverlay(List<SubtitleOverlay.Segment> segments) {
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
    public void configure(Size videoSize) {
        super.configure(videoSize);
    }

    @Override
    public OverlaySettings getOverlaySettings(long presentationTimeUs) {
        return settings;
    }

    @Override
    public synchronized void onDraw(Canvas canvas, long presentationTimeUs) {
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);

        double t = presentationTimeUs / 1_000_000.0;
        String value = null;
        for (SubtitleOverlay.Segment s : segments) {
            if (t >= s.start && t < s.end) {
                value = s.text;
                break;
            }
        }
        if (value == null || value.trim().isEmpty()) return;

        value = value.trim();
        float width = canvas.getWidth();
        float height = canvas.getHeight();
        float size = Math.max(36f, Math.min(86f, width * 0.052f));
        textPaint.setTextSize(size);

        float maxTextWidth = width * 0.84f;
        ArrayList<String> lines = wrap(value, maxTextWidth);
        float lineHeight = size * 1.22f;
        float bottom = height * 0.90f;
        float blockHeight = lines.size() * lineHeight;
        float firstBaseline = bottom - blockHeight + lineHeight - size * 0.16f;

        float maxLine = 0f;
        for (String line : lines) maxLine = Math.max(maxLine, textPaint.measureText(line));
        float padX = size * 0.55f;
        float padY = size * 0.34f;
        float left = width / 2f - maxLine / 2f - padX;
        float right = width / 2f + maxLine / 2f + padX;
        float top = firstBaseline - size - padY;
        float bgBottom = firstBaseline + (lines.size() - 1) * lineHeight + size * 0.28f + padY;
        canvas.drawRoundRect(new RectF(left, top, right, bgBottom), size * 0.22f, size * 0.22f, bgPaint);

        for (int i = 0; i < lines.size(); i++) {
            canvas.drawText(lines.get(i), width / 2f, firstBaseline + i * lineHeight, textPaint);
        }
    }

    private ArrayList<String> wrap(String text, float maxWidth) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (textPaint.measureText(candidate) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        if (out.size() > 3) {
            ArrayList<String> compact = new ArrayList<>();
            compact.add(out.get(0));
            compact.add(out.get(1));
            StringBuilder rest = new StringBuilder(out.get(2));
            for (int i = 3; i < out.size(); i++) rest.append(' ').append(out.get(i));
            compact.add(rest.toString());
            return compact;
        }
        return out;
    }
}
