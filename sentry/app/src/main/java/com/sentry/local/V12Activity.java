package com.sentry.local;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Sentry v12 Unified Interface.
 *
 * V4 spatial pages, V10 tools and V11 compatibility controls are presented
 * through one bottom navigation bar. The previous floating V10/V11 buttons
 * are removed. Dense scenes use smart labels by default to avoid unreadable
 * text collisions.
 */
public class V12Activity extends V11Activity {
    private static final int BG = Color.rgb(3, 11, 10);
    private static final int PANEL = Color.rgb(11, 28, 24);
    private static final int PANEL_2 = Color.rgb(19, 45, 38);
    private static final int TEXT = Color.rgb(238, 252, 248);
    private static final int MUTED = Color.rgb(128, 163, 151);
    private static final int ACCENT = Color.rgb(53, 232, 192);
    private static final int BLUE = Color.rgb(80, 168, 255);
    private static final int GOOD = Color.rgb(83, 224, 142);
    private static final int WARN = Color.rgb(244, 187, 76);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences v12;
    private SharedPreferences spatialPrefs;
    private LinearLayout unifiedNav;
    private int activeDestination;
    private boolean hubVisible;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        v12 = getSharedPreferences("sentry_v12", MODE_PRIVATE);
        spatialPrefs = getSharedPreferences("sentry_local", MODE_PRIVATE);

        // Prevent the old V11 popup from appearing on a clean V12 launch.
        getSharedPreferences("sentry_v11", MODE_PRIVATE)
                .edit().putBoolean("fusion_intro_seen", true).apply();

        if (!v12.contains("label_mode")) v12.edit().putString("label_mode", "auto").apply();
        if (!v12.contains("compact_hud")) v12.edit().putBoolean("compact_hud", true).apply();

        removeLegacyFloatingControls();
        installUnifiedNavigation();
        rebrandHeader();
        applySmartLabels();

        ui.postDelayed(() -> {
            removeLegacyFloatingControls();
            installUnifiedNavigation();
            rebrandHeader();
            applySmartLabels();
        }, 700);

        ui.postDelayed(labelWatcher, 2200);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(labelWatcher);
        super.onDestroy();
    }

    private final Runnable labelWatcher = new Runnable() {
        @Override public void run() {
            applySmartLabels();
            ui.postDelayed(this, 2600);
        }
    };

    private void installUnifiedNavigation() {
        LinearLayout nav = getField(V4Activity.class, "nav", LinearLayout.class);
        if (nav == null) return;
        unifiedNav = nav;
        nav.removeAllViews();
        nav.setPadding(dp(5), dp(5), dp(5), dp(7));

        String[] labels = {"Universe", "Inspect", "Rooms", "Radar", "Timeline", "Hub"};
        for (int i = 0; i < labels.length; i++) {
            final int destination = i;
            TextView item = text(labels[i], 9.5f, MUTED, true);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2), dp(12), dp(2), dp(12));
            item.setContentDescription("Ouvrir " + labels[i]);
            item.setOnClickListener(v -> navigate(destination));
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        setNavActive(activeDestination);
    }

    private void navigate(int destination) {
        activeDestination = destination;
        if (destination <= 4) {
            hubVisible = false;
            openSpatialTab(destination);
        } else {
            hubVisible = true;
            showHubPage();
        }
        setNavActive(destination);
    }

    private void setNavActive(int destination) {
        if (unifiedNav == null) return;
        for (int i = 0; i < unifiedNav.getChildCount(); i++) {
            View child = unifiedNav.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView item = (TextView) child;
            boolean active = i == destination;
            item.setTextColor(active ? Color.rgb(2, 25, 19) : MUTED);
            item.setBackground(active ? rounded(ACCENT, 17) : rounded(Color.TRANSPARENT, 17));
        }
    }

    private void showHubPage() {
        FrameLayout content = getField(V4Activity.class, "content", FrameLayout.class);
        TextView title = getField(V4Activity.class, "title", TextView.class);
        TextView subtitle = getField(V4Activity.class, "subtitle", TextView.class);
        if (content == null) return;

        if (title != null) title.setText("SENTRY HUB");
        if (subtitle != null) subtitle.setText("Spatial · Intelligence · Compatibility");
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(14), dp(8), dp(14), dp(24));

        root.addView(heroCard());
        root.addView(space(12));
        root.addView(statusStrip());

        section(root, "QUICK ACTIONS");
        addCard(root, "FULL SPATIAL SWEEP", "Bluetooth + LAN + actualisation du jumeau numérique", ACCENT,
                () -> { invokePrivate(V4Activity.class, "startSpatialSweep"); toast("Balayage spatial lancé."); });
        addCard(root, "ONE-TAP SETUP", "Permissions et configuration guidées dans la même interface", BLUE,
                () -> invokePrivate(V10Activity.class, "showSetupWizard", new Class[]{boolean.class}, new Object[]{false}));
        addCard(root, "SMART LABELS", labelDescription(), WARN, this::cycleLabelMode);

        section(root, "NETWORK & BLUETOOTH");
        addCard(root, "NETWORK X-RAY", "Wi-Fi, transport, IP, DNS, routes et capacités", BLUE,
                () -> invokePrivate(V10Activity.class, "showNetworkXray"));
        addCard(root, "GATT INSPECTOR", "Services Bluetooth d’un appareil associé", ACCENT,
                () -> invokePrivate(V10Activity.class, "showGattPicker"));
        addCard(root, "ROOM FINGERPRINT", "Calibrer et estimer la pièce actuelle", GOOD,
                () -> invokePrivate(V10Activity.class, "showRoomLab"));

        section(root, "INTELLIGENCE");
        addCard(root, "LIVE TELEMETRY", "Batterie, capteurs, orientation et trafic local", GOOD,
                () -> invokePrivate(V10Activity.class, "showTelemetry"));
        addCard(root, "REALITY HUD", "Vue radar immersive utilisant les capteurs disponibles", ACCENT,
                () -> invokePrivate(V10Activity.class, "showRealityHud"));
        addCard(root, "LOCAL AI BRIEF", "Résumé explicable généré uniquement sur le téléphone", BLUE,
                () -> invokePrivate(V10Activity.class, "showAiBrief"));
        addCard(root, "SECURITY AUDIT", "Verrouillage, chiffrement, patch Android et réglages", WARN,
                () -> invokePrivate(V10Activity.class, "showSecurityAudit"));

        section(root, "COMPATIBILITY & SYSTEM");
        addCard(root, "CAPABILITY MATRIX", "BLE, Wi-Fi RTT, UWB, AR, NFC et capteurs", BLUE,
                () -> invokePrivate(V10Activity.class, "showCapabilities"));
        addCard(root, "DEVICE COMPATIBILITY", "RAM, CPU, Android, architecture et profil actif", ACCENT,
                () -> invokePrivate(V11Activity.class, "showCompatibilityReport"));
        addCard(root, "PERFORMANCE PROFILE", "Auto, Économie, Équilibré, Ultra ou Safe", GOOD,
                () -> invokePrivate(V11Activity.class, "choosePerformanceProfile"));
        addCard(root, "SYSTEM SETTINGS", "Thème, qualité, profils, confidentialité et export v4", MUTED,
                () -> openSpatialTab(5));
        addCard(root, "EXPORT DIGITAL TWIN", "Créer un rapport JSON local complet", ACCENT,
                () -> invokePrivate(V10Activity.class, "exportV10"));

        scroll.addView(root);
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setNavActive(5);
    }

    private View heroCard() {
        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(rounded(PANEL, 21));
        TextView eyebrow = text("SENTRY V12", 11, ACCENT, true);
        eyebrow.setLetterSpacing(.14f);
        card.addView(eyebrow);
        card.addView(space(5));
        card.addView(text("Unified Command Hub", 25, TEXT, true));
        card.addView(space(5));
        card.addView(text("Toutes les fonctions v4, v10 et v11 sont maintenant intégrées ici. Aucun bouton flottant, aucune deuxième interface.", 13, MUTED, false));
        return card;
    }

    private View statusStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric("DEVICES", String.valueOf(deviceCount()), ACCENT), weight());
        row.addView(spaceW(7));
        row.addView(metric("LABELS", v12.getString("label_mode", "auto").toUpperCase(), WARN), weight());
        row.addView(spaceW(7));
        row.addView(metric("MODE", performanceLabel(), GOOD), weight());
        return row;
    }

    private String performanceLabel() {
        String resolved = getSharedPreferences("sentry_v11", MODE_PRIVATE)
                .getString("resolved_profile", "balanced");
        if ("economy".equals(resolved)) return "ECO";
        if ("safe".equals(resolved)) return "SAFE";
        if ("ultra".equals(resolved)) return "ULTRA";
        return "BALANCED";
    }

    private void cycleLabelMode() {
        String current = v12.getString("label_mode", "auto");
        String next = "auto".equals(current) ? "off" : "off".equals(current) ? "all" : "auto";
        v12.edit().putString("label_mode", next).apply();
        applySmartLabels();
        toast("Labels : " + ("auto".equals(next) ? "intelligents" : "off".equals(next) ? "masqués" : "tous affichés"));
        if (hubVisible) showHubPage();
    }

    private String labelDescription() {
        String mode = v12.getString("label_mode", "auto");
        if ("off".equals(mode)) return "Masqués · toucher un objet puis ouvrir Inspect";
        if ("all".equals(mode)) return "Tous affichés · peut devenir chargé avec beaucoup d’objets";
        return "Automatique · les noms sont masqués quand la scène devient dense";
    }

    private void applySmartLabels() {
        if (v12 == null || spatialPrefs == null) return;
        String mode = v12.getString("label_mode", "auto");
        boolean show;
        if ("all".equals(mode)) show = true;
        else if ("off".equals(mode)) show = false;
        else show = deviceCount() <= 11;

        if (spatialPrefs.getBoolean("v4_labels", true) != show) {
            spatialPrefs.edit().putBoolean("v4_labels", show).apply();
        }

        // Dense scenes are clearer without long trails, even on flagship phones.
        if ("auto".equals(mode) && deviceCount() > 20 && spatialPrefs.getBoolean("v4_trails", true)) {
            spatialPrefs.edit().putBoolean("v4_trails", false).apply();
        }
    }

    private int deviceCount() {
        try {
            Field field = V4Activity.class.getDeclaredField("devices");
            field.setAccessible(true);
            Object value = field.get(this);
            if (value instanceof Map) return ((Map<?, ?>) value).size();
        } catch (Exception ignored) { }
        return 0;
    }

    private void removeLegacyFloatingControls() {
        View root = getWindow().getDecorView();
        hideLegacyButtons(root);
    }

    private void hideLegacyButtons(View view) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            String text = value == null ? "" : value.toString();
            if (text.contains("V10\nCORE") || text.contains("V11\nFUSION")
                    || text.equals("V10 CORE") || text.equals("V11 FUSION")) {
                view.setVisibility(View.GONE);
                view.setEnabled(false);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) hideLegacyButtons(group.getChildAt(i));
        }
    }

    private void rebrandHeader() {
        replaceTextRecursive(getWindow().getDecorView(), "S4", "S12");
    }

    private void replaceTextRecursive(View view, String from, String to) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (from.contentEquals(textView.getText())) textView.setText(to);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) replaceTextRecursive(group.getChildAt(i), from, to);
        }
    }

    private void openSpatialTab(int target) {
        try {
            Field tab = V4Activity.class.getDeclaredField("tab");
            tab.setAccessible(true);
            tab.setInt(this, target);
            Method render = V4Activity.class.getDeclaredMethod("render");
            render.setAccessible(true);
            render.invoke(this);
            if (target <= 4) activeDestination = target;
            else activeDestination = 5;
            setNavActive(activeDestination);
            ui.postDelayed(this::installUnifiedNavigation, 40);
            ui.postDelayed(this::rebrandHeader, 60);
        } catch (Exception e) {
            toast("Page indisponible : " + e.getMessage());
        }
    }

    private void invokePrivate(Class<?> owner, String name) {
        invokePrivate(owner, name, new Class[0], new Object[0]);
    }

    private void invokePrivate(Class<?> owner, String name, Class<?>[] types, Object[] args) {
        try {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(this, args);
        } catch (Exception e) {
            toast("Fonction indisponible sur ce téléphone.");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Class<?> owner, String name, Class<T> type) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(this);
            if (type.isInstance(value)) return (T) value;
        } catch (Exception ignored) { }
        return null;
    }

    private void section(LinearLayout root, String title) {
        root.addView(space(15));
        TextView label = text(title, 10.5f, ACCENT, true);
        label.setLetterSpacing(.12f);
        root.addView(label);
        root.addView(space(7));
    }

    private void addCard(LinearLayout root, String title, String subtitle, int color, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(rounded(PANEL, 17));

        TextView marker = text("•", 24, color, true);
        marker.setGravity(Gravity.CENTER);
        card.addView(marker, new LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout labels = column();
        labels.addView(text(title, 14, TEXT, true));
        labels.addView(text(subtitle, 11.5f, MUTED, false));
        card.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(text("›", 27, color, false));
        card.setContentDescription(title + ". " + subtitle);
        card.setOnClickListener(v -> action.run());
        root.addView(card);
        root.addView(space(8));
    }

    private TextView metric(String label, String value, int color) {
        TextView metric = text(label + "\n" + value, 9.5f, color, true);
        metric.setGravity(Gravity.CENTER);
        metric.setPadding(dp(4), dp(10), dp(4), dp(10));
        metric.setBackground(rounded(PANEL, 14));
        return metric;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        if (bold) view.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return view;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private View space(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return view;
    }

    private View spaceW(int width) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(width), 1));
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
