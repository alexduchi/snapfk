package com.neo.holoforge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;

import java.util.Arrays;

public final class MainActivity extends Activity {
    private static final int BG = 0xFF071017;
    private static final int CARD = 0xFF101D27;
    private static final int TEXT = 0xFFF1FAFF;
    private static final int MUTED = 0xFFA9BBC7;
    private static final int ACCENT = 0xFF52E8FF;

    private TextView diagnosticView;
    private String diagnostic = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildScreen());
        refreshDiagnostic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDiagnostic();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(34), dp(22), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("HoloForge AR", 32, TEXT, true));
        TextView subtitle = text("Diagnostic de démarrage Android", 14, MUTED, false);
        subtitle.setPadding(0, dp(5), 0, dp(24));
        root.addView(subtitle);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(round(CARD, 24, 0x4052E8FF));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(text("L’application Android démarre correctement", 18, TEXT, true));
        TextView explanation = text(
                "Cette version ne lance ni la caméra, ni OpenGL, ni MediaPipe. Elle vérifie d’abord que l’APK et ARCore sont reconnus par ton téléphone.",
                13, MUTED, false);
        explanation.setPadding(0, dp(10), 0, dp(16));
        card.addView(explanation);

        diagnosticView = text("Vérification…", 13, TEXT, false);
        diagnosticView.setTextIsSelectable(true);
        diagnosticView.setLineSpacing(0f, 1.18f);
        card.addView(diagnosticView);

        root.addView(space(18));
        TextView copy = button("Copier le diagnostic", true);
        copy.setOnClickListener(v -> copyDiagnostic());
        root.addView(copy);

        root.addView(space(10));
        TextView refresh = button("Relancer la vérification", false);
        refresh.setOnClickListener(v -> refreshDiagnostic());
        root.addView(refresh);

        TextView next = text(
                "Quand cet écran s’ouvre, la base APK est validée. La prochaine version réintégrera la caméra ARCore seule, puis le suivi des mains dans une étape séparée.",
                13, MUTED, false);
        next.setPadding(dp(3), dp(24), dp(3), 0);
        root.addView(next);
        return scroll;
    }

    private void refreshDiagnostic() {
        String arCoreStatus;
        try {
            ArCoreApk.Availability availability =
                    ArCoreApk.getInstance().checkAvailability(this);
            arCoreStatus = availability.name();
        } catch (Throwable error) {
            arCoreStatus = "ERREUR " + error.getClass().getSimpleName()
                    + (error.getMessage() == null ? "" : ": " + error.getMessage());
        }

        diagnostic = "HoloForge AR Diagnostic 0.3.0\n"
                + "Package : " + getPackageName() + "\n"
                + "Android : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Fabricant : " + Build.MANUFACTURER + "\n"
                + "Modèle : " + Build.MODEL + "\n"
                + "Appareil : " + Build.DEVICE + "\n"
                + "ABI : " + Arrays.toString(Build.SUPPORTED_ABIS) + "\n"
                + "ARCore : " + arCoreStatus;

        if (diagnosticView != null) diagnosticView.setText(diagnostic);
    }

    private void copyDiagnostic() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("HoloForge diagnostic", diagnostic));
        Toast.makeText(this, "Diagnostic copié", Toast.LENGTH_SHORT).show();
    }

    private TextView button(String label, boolean primary) {
        TextView view = text(label, 16, primary ? 0xFF031216 : TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(18), dp(17), dp(18), dp(17));
        view.setBackground(round(
                primary ? ACCENT : 0xFF192A36,
                22,
                primary ? ACCENT : 0x5052E8FF));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private View space(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return view;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
