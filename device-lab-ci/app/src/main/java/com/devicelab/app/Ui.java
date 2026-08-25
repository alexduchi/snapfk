package com.devicelab.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public final class Ui {
    public static final int BG = Color.rgb(6, 12, 20);
    public static final int SURFACE = Color.rgb(13, 24, 36);
    public static final int SURFACE_2 = Color.rgb(18, 33, 48);
    public static final int BORDER = Color.rgb(35, 58, 78);
    public static final int ACCENT = Color.rgb(47, 198, 255);
    public static final int ACCENT_2 = Color.rgb(92, 116, 255);
    public static final int GOOD = Color.rgb(92, 224, 145);
    public static final int WARN = Color.rgb(255, 194, 91);
    public static final int BAD = Color.rgb(255, 104, 118);
    public static final int TEXT = Color.rgb(237, 244, 249);
    public static final int MUTED = Color.rgb(144, 165, 181);

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable bg(Context c, int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radius));
        return g;
    }

    public static GradientDrawable stroke(Context c, int color, int stroke, float radius) {
        GradientDrawable g = bg(c, color, radius);
        g.setStroke(dp(c, 1), stroke);
        return g;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView title(Context c, String s) {
        return text(c, s, 20, TEXT, true);
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(Locale.ROOT), 11, ACCENT, true);
        t.setLetterSpacing(.09f);
        return t;
    }

    public static TextView muted(Context c, String s) {
        return text(c, s, 13, MUTED, false);
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 15), dp(c, 16), dp(c, 15));
        l.setBackground(stroke(c, SURFACE, BORDER, 18));
        return l;
    }

    public static Button button(Context c, String s, boolean primary) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.rgb(3, 19, 29) : TEXT);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 48));
        b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        b.setBackground(primary ? bg(c, ACCENT, 14) : stroke(c, SURFACE_2, BORDER, 14));
        return b;
    }

    public static Button chip(Context c, String s, boolean selected) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(selected ? Color.rgb(3, 19, 29) : MUTED);
        b.setMinHeight(dp(c, 38));
        b.setPadding(dp(c, 13), 0, dp(c, 13), 0);
        b.setBackground(selected ? bg(c, ACCENT, 18) : stroke(c, SURFACE, BORDER, 18));
        return b;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(c, dp)));
        return v;
    }

    public static String f(double v, int n, String unit) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Indisponible";
        String p = n == 0 ? "%.0f" : n == 1 ? "%.1f" : "%.2f";
        return String.format(Locale.ROOT, p + (unit.length() == 0 ? "" : " " + unit), v);
    }

    public static String bytes(long b) {
        if (b < 0) return "Indisponible";
        double v = b;
        String[] u = {"o", "Ko", "Mo", "Go", "To"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024.0; i++; }
        return String.format(Locale.ROOT, i == 0 ? "%.0f %s" : "%.2f %s", v, u[i]);
    }
}
