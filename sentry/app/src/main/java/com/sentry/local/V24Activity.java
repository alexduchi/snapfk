package com.sentry.local;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Sentry v24 launcher: keeps v23 modules and makes the 3D scanner immediately accessible. */
public class V24Activity extends V23Activity {
    private TextView scanButton;
    private TextView vpnButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getSharedPreferences("sentry_v24", MODE_PRIVATE).edit().putLong("last_launch", System.currentTimeMillis()).apply();
        addQuickControls();
    }

    @Override protected void onResume() {
        super.onResume();
        // Fortress disabled means its network confinement must never remain requested.
        if (!getSharedPreferences("sentry_v23", MODE_PRIVATE).getBoolean("enabled", false)
                && (EmergencyVpnService.isRunning() || EmergencyVpnService.isRequested(this))) {
            EmergencyVpnService.stop(this);
        }
        refreshVpnButton();
    }

    private void addQuickControls() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(7), dp(8), dp(7));
        bar.setBackground(round(Color.rgb(5, 18, 24), 18));
        bar.setElevation(dp(18));

        scanButton = button("SCAN 3D", Color.rgb(61, 235, 211), Color.rgb(2, 27, 25));
        scanButton.setOnClickListener(v -> startActivity(new Intent(this, SpatialScanActivity.class)));
        bar.addView(scanButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        ViewGroup.MarginLayoutParams spacer = new ViewGroup.MarginLayoutParams(dp(7), 1);
        TextView gap = new TextView(this);
        bar.addView(gap, spacer);

        vpnButton = button("VPN LIBRE", Color.rgb(79, 166, 255), Color.rgb(2, 18, 28));
        vpnButton.setOnClickListener(v -> {
            if (EmergencyVpnService.isRunning() || EmergencyVpnService.isRequested(this)) {
                EmergencyVpnService.stop(this);
                Toast.makeText(this, "Arrêt VPN demandé : le tunnel est fermé.", Toast.LENGTH_SHORT).show();
                vpnButton.postDelayed(this::refreshVpnButton, 500);
            } else {
                Toast.makeText(this, "Le confinement réseau est déjà désactivé.", Toast.LENGTH_SHORT).show();
            }
        });
        bar.addView(vpnButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.setMargins(dp(12), 0, dp(92), dp(12));
        addContentView(bar, lp);
        refreshVpnButton();
    }

    private void refreshVpnButton() {
        if (vpnButton == null) return;
        boolean active = EmergencyVpnService.isRunning() || EmergencyVpnService.isRequested(this);
        vpnButton.setText(active ? "COUPER VPN" : "VPN LIBRE");
        vpnButton.setTextColor(active ? Color.WHITE : Color.rgb(2, 18, 28));
        vpnButton.setBackground(round(active ? Color.rgb(245, 98, 124) : Color.rgb(79, 166, 255), 14));
    }

    private TextView button(String label, int background, int foreground) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextColor(foreground);
        v.setTextSize(11.5f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(background, 14));
        return v;
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
