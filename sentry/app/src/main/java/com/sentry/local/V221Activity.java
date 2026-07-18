package com.sentry.local;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Sentry v22.1: compact navigation that replaces the inherited six-button bar. */
public class V221Activity extends V22Activity {
    private static final int BG = Color.rgb(2, 8, 12);
    private static final int PANEL = Color.rgb(10, 26, 32);
    private static final int MUTED = Color.rgb(126, 163, 172);
    private static final int TEXT = Color.rgb(239, 253, 255);
    private static final int CYAN = Color.rgb(61, 235, 211);

    private final Handler compactUi = new Handler(Looper.getMainLooper());
    private LinearLayout compactNav;
    private int activeTab;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        compactUi.postDelayed(this::installCompactNavigation, 180);
        compactUi.postDelayed(navGuard, 300);
    }

    @Override protected void onDestroy() {
        compactUi.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private final Runnable navGuard = new Runnable() {
        @Override public void run() {
            LinearLayout legacy = privateField(V4Activity.class, "nav", LinearLayout.class);
            if (legacy != null) legacy.setVisibility(View.GONE);
            if (compactNav == null || compactNav.getParent() == null) installCompactNavigation();
            replaceExact(getWindow().getDecorView(), "S22", "S22.1");
            compactUi.postDelayed(this, 450);
        }
    };

    private void installCompactNavigation() {
        LinearLayout legacy = privateField(V4Activity.class, "nav", LinearLayout.class);
        if (legacy == null || legacy.getParent() == null) {
            compactUi.postDelayed(this::installCompactNavigation, 180);
            return;
        }
        legacy.setVisibility(View.GONE);
        ViewGroup parent = (ViewGroup) legacy.getParent();
        if (compactNav != null && compactNav.getParent() == parent) return;

        compactNav = new LinearLayout(this);
        compactNav.setOrientation(LinearLayout.HORIZONTAL);
        compactNav.setGravity(Gravity.CENTER);
        compactNav.setPadding(dp(7), dp(6), dp(7), dp(8));
        compactNav.setBackgroundColor(BG);

        addTab("RÉALITÉ", 0, () -> invokePrivate(V22Activity.class, "showRealityHub"));
        addTab("DÉTECTER", 1, this::showDetectionMenu);
        addTab("CARTE", 2, () -> invokePrivate(V22Activity.class, "showRealityMap"));
        addTab("PLUS", 3, this::showMoreMenu);

        int legacyIndex = parent.indexOfChild(legacy);
        parent.addView(compactNav, Math.max(0, legacyIndex + 1), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setActive(0);
    }

    private void addTab(String label, int index, Runnable action) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setTextSize(10.5f);
        item.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        item.setGravity(Gravity.CENTER);
        item.setSingleLine(true);
        item.setPadding(dp(3), dp(12), dp(3), dp(12));
        item.setOnClickListener(v -> {
            setActive(index);
            action.run();
        });
        compactNav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private void setActive(int index) {
        activeTab = index;
        if (compactNav == null) return;
        for (int i = 0; i < compactNav.getChildCount(); i++) {
            TextView item = (TextView) compactNav.getChildAt(i);
            boolean active = i == index;
            item.setTextColor(active ? Color.rgb(2, 25, 22) : MUTED);
            item.setBackground(rounded(active ? CYAN : Color.TRANSPARENT, 16));
        }
    }

    private void showDetectionMenu() {
        String[] items = {
                "Fusion réalité 360°",
                "HyperTrack Bluetooth",
                "Radar magnétique",
                "Vision des vibrations",
                "Sonar expérimental",
                "Scanner optique"
        };
        new AlertDialog.Builder(this)
                .setTitle("Détection")
                .setItems(items, (d, which) -> {
                    if (which == 0) invokeDirectional(2);
                    else if (which == 1) invokePrivate(V21Activity.class, "showPicker");
                    else if (which == 2) invokeDirectional(1);
                    else if (which == 3) invokePrivate(V22Activity.class, "showVibrationLab");
                    else if (which == 4) invokePrivate(V22Activity.class, "requestEchoLab");
                    else invokePrivate(V22Activity.class, "requestOpticalScanner");
                })
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void showMoreMenu() {
        String[] items = {
                "Centre de commandement",
                "Capacités du téléphone",
                "Carte de signal",
                "Univers spatial classique",
                "Réglages HyperTrack"
        };
        new AlertDialog.Builder(this)
                .setTitle("Plus d’outils")
                .setItems(items, (d, which) -> {
                    if (which == 0) invokePrivate(V20Activity.class, "showCommandCenter");
                    else if (which == 1) invokePrivate(V22Activity.class, "showHardwareCapabilities");
                    else if (which == 2) invokePrivate(V14Activity.class, "showWifiHeatmap");
                    else if (which == 3) invokeV4Tab(0);
                    else invokePrivate(V21Activity.class, "showPrecisionSettings");
                })
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void invokeDirectional(int mode) {
        try {
            Method m = V22Activity.class.getDeclaredMethod("showDirectionalLab", int.class);
            m.setAccessible(true);
            m.invoke(this, mode);
        } catch (Exception e) {
            toast("Module momentanément indisponible.");
        }
    }

    private void invokeV4Tab(int tab) {
        try {
            Field f = V4Activity.class.getDeclaredField("tab");
            f.setAccessible(true);
            f.setInt(this, tab);
            Method m = V4Activity.class.getDeclaredMethod("render");
            m.setAccessible(true);
            m.invoke(this);
        } catch (Exception e) {
            toast("Univers spatial momentanément indisponible.");
        }
    }

    private void invokePrivate(Class<?> owner, String name) {
        try {
            Method m = owner.getDeclaredMethod(name);
            m.setAccessible(true);
            m.invoke(this);
        } catch (Exception e) {
            toast("Fonction momentanément indisponible.");
        }
    }

    private <T> T privateField(Class<?> owner, String name, Class<T> type) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            Object value = f.get(this);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void replaceExact(View view, String from, String to) {
        if (view instanceof TextView && from.contentEquals(((TextView) view).getText())) ((TextView) view).setText(to);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) replaceExact(group.getChildAt(i), from, to);
        }
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }
}