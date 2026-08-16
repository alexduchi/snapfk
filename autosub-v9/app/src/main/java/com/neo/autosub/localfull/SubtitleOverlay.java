package com.neo.autosub.localfull;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import androidx.annotation.OptIn;
import androidx.media3.common.OverlaySettings;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.TextOverlay;

import java.util.List;

@OptIn(markerClass = UnstableApi.class)
final class SubtitleOverlay extends TextOverlay {
    static final class Segment {
        final double start, end;
        final String text;
        Segment(double s, double e, String t) { start = s; end = e; text = t; }
    }

    private final List<Segment> segments;
    private final OverlaySettings settings;

    SubtitleOverlay(List<Segment> segments) {
        this.segments = segments;
        settings = new StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, -0.82f)
                .setOverlayFrameAnchor(0f, -1f)
                .setAlphaScale(1f)
                .setScale(0.92f, 0.92f)
                .build();
    }

    @Override
    public SpannableString getText(long presentationTimeUs) {
        double t = presentationTimeUs / 1_000_000.0;
        String value = "";
        for (Segment s : segments) {
            if (t >= s.start && t < s.end) {
                value = wrap(s.text == null ? "" : s.text.trim());
                break;
            }
        }

        SpannableString out = new SpannableString(value);
        if (value.isEmpty()) return out;

        int flags = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE;
        out.setSpan(new ForegroundColorSpan(Color.WHITE), 0, out.length(), flags);
        out.setSpan(new BackgroundColorSpan(0xD9000000), 0, out.length(), flags);
        out.setSpan(new StyleSpan(Typeface.BOLD), 0, out.length(), flags);
        out.setSpan(new AbsoluteSizeSpan(72, false), 0, out.length(), flags);
        return out;
    }

    @Override
    public OverlaySettings getOverlaySettings(long presentationTimeUs) {
        return settings;
    }

    private static String wrap(String input) {
        if (input == null || input.isEmpty()) return "";
        String[] words = input.split("\\s+");
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (String word : words) {
            StringBuilder target = b.length() > 0 ? b : a;
            if (target.length() > 0 && target.length() + 1 + word.length() > 38 && b.length() == 0) {
                b.append(word);
            } else {
                if (target.length() > 0) target.append(' ');
                target.append(word);
            }
        }
        return b.length() == 0 ? a.toString() : a + "\n" + b;
    }
}
