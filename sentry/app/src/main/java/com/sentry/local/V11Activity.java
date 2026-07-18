package com.sentry.local;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Sentry v11 Fusion Edition.
 *
 * The v4 spatial universe and the v10 command modules are exposed from one
 * activity and one navigation console. A compatibility controller scales the
 * rendering and scan intensity to the phone instead of assuming flagship
 * hardware. No native library is used, so both 32-bit and 64-bit Android
 * devices can install the same APK.
 */
public class V11Activity extends V10Activity {
    private static final int BG = Color.rgb(2, 8, 12);
    private static final int PANEL = Color.rgb(10, 23, 30);
    private static final int PANEL_2 = Color.rgb(17, 39, 48);
    private static final int TEXT = Color.rgb(236, 252, 255);
    private static final int MUTED = Color.rgb(133, 167, 176);
    private static final int CYAN = Color.rgb(62, 236, 224);
    private static final int BLUE = Color.rgb(84, 169, 255);
    private static final int GOOD = Color.rgb(85, 229, 146);
    private static final int WARN = Color.rgb(246, 190, 82);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences fusion;
    private SharedPreferences spatial;
    private DeviceProfile deviceProfile;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        fusion = getSharedPreferences("sentry_v11", MODE_PRIVATE);
        spatial = getSharedPreferences("sentry_local", MODE_PRIVATE);
        deviceProfile = inspectDevice();
        applyAutomaticProfile(false);
        installFusionDock();

        ui.postDelayed(() -> {
            if (!fusion.getBoolean("fusion_intro_seen", false)
                    && getSharedPreferences("sentry_v10", MODE_PRIVATE).getBoolean("setup_complete", false)) {
                showFusionIntro();
            }
        }, 1100);
    }

    private void installFusionDock() {
        TextView dock = text("V11\nFUSION", 10, Color.rgb(0, 23, 26), true);
        dock.setGravity(Gravity.CENTER);
        dock.setContentDescription("Ouvrir Sentry v11 Fusion Console");
        dock.setBackground(rounded(BLUE, 23));
        dock.setElevation(dp(14));
        dock.setOnClickListener(v -> showFusionConsole());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(68), dp(68), Gravity.START | Gravity.BOTTOM);
        lp.setMargins(dp(14), 0, 0, dp(72));
        addContentView(dock, lp);
    }

    private void showFusionIntro() {
        fusion.edit().putBoolean("fusion_intro_seen", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Sentry v11 Fusion")
                .setMessage("La v4 Spatial Universe et la v10 Command Center fonctionnent maintenant dans la même interface.\n\n"
                        + "Le mode graphique vient d’être réglé automatiquement sur « " + deviceProfile.modeLabel + " » pour ce téléphone. "
                        + "Tu peux le changer à tout moment depuis le bouton V11 FUSION.")
                .setPositiveButton("Ouvrir Fusion", (d, w) -> showFusionConsole())
                .setNegativeButton("Continuer", null)
                .show();
    }

    private void showFusionConsole() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(17), dp(22), dp(17), dp(28));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text("11", 19, Color.rgb(0, 22, 25), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(CYAN, 17));
        head.addView(badge, new LinearLayout.LayoutParams(dp(50), dp(50)));
        head.addView(spaceW(12));
        LinearLayout names = column();
        names.addView(text("FUSION CONSOLE", 24, TEXT, true));
        names.addView(text("Spatial v4 + Command v10 · une seule appli", 12, MUTED, false));
        head.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(head);
        root.addView(space(12));
        root.addView(summaryStrip());
        root.addView(space(12));

        section(root, "SPATIAL ENGINE V4");
        module(root, "SPATIAL UNIVERSE", "Jumeau numérique 3D et balayage complet", () -> { dialog.dismiss(); openSpatialTab(0); });
        module(root, "DEVICE INSPECT", "Dossiers, modèles et informations des appareils", () -> { dialog.dismiss(); openSpatialTab(1); });
        module(root, "ROOM INTELLIGENCE", "Pièces, zones et présence estimée des appareils", () -> { dialog.dismiss(); openSpatialTab(2); });
        module(root, "PROXIMITY RADAR", "Bluetooth RSSI, distance estimée et trajectoires", () -> { dialog.dismiss(); openSpatialTab(3); });
        module(root, "CINEMATIC TIMELINE", "Historique et relecture des apparitions", () -> { dialog.dismiss(); openSpatialTab(4); });

        section(root, "COMMAND ENGINE V10");
        module(root, "COMMAND CENTER", "Télémétrie, Network X-Ray, GATT, Reality HUD et audit", () -> { dialog.dismiss(); invokeV10("showCommandCenter"); });
        module(root, "ONE-TAP SETUP", "Relancer l’assistant de permissions et capacités", () -> { dialog.dismiss(); invokeV10("showSetupWizard", boolean.class, false); });
        module(root, "FULL SPATIAL SWEEP", "Démarrer tous les scans compatibles", () -> { dialog.dismiss(); invokeV4("startSpatialSweep"); toast("Balayage spatial lancé."); });

        section(root, "COMPATIBILITÉ V11");
        module(root, "DEVICE COMPATIBILITY", "Diagnostic matériel et version Android", this::showCompatibilityReport);
        module(root, "PERFORMANCE PROFILE", "Automatique, économie, équilibré ou ultra", this::choosePerformanceProfile);
        module(root, "SAFE MODE", "Réduction maximale des animations et de la charge", this::activateSafeMode);
        module(root, "SYSTEM SETTINGS", "Thème, confidentialité, qualité et profils", () -> { dialog.dismiss(); openSpatialTab(5); });

        TextView close = action("FERMER", PANEL_2, TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(space(12));
        root.addView(close);
        scroll.addView(root);
        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(rounded(BG, 0));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private View summaryStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric("ANDROID", Build.VERSION.RELEASE, Build.VERSION.SDK_INT >= 29 ? GOOD : WARN), weight());
        row.addView(spaceW(7));
        row.addView(metric("RAM", deviceProfile.ramLabel, deviceProfile.lowRam ? WARN : GOOD), weight());
        row.addView(spaceW(7));
        row.addView(metric("MODE", deviceProfile.modeLabel.toUpperCase(Locale.FRANCE), CYAN), weight());
        return row;
    }

    private void showCompatibilityReport() {
        deviceProfile = inspectDevice();
        StringBuilder b = new StringBuilder();
        b.append("SENTRY V11 COMPATIBILITY\n\n");
        b.append("Android ").append(Build.VERSION.RELEASE).append(" · API ").append(Build.VERSION.SDK_INT).append('\n');
        b.append("Modèle : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        b.append("Mémoire : ").append(deviceProfile.ramLabel).append(deviceProfile.lowRam ? " · appareil low-RAM" : "").append('\n');
        b.append("CPU : ").append(deviceProfile.cores).append(" cœurs disponibles\n");
        b.append("Architecture : ").append(Build.SUPPORTED_ABIS.length == 0 ? "inconnue" : Build.SUPPORTED_ABIS[0]).append('\n');
        b.append("OpenGL ES : ").append(deviceProfile.glVersion).append('\n');
        b.append("Écran : ").append(deviceProfile.widthDp).append(" × ").append(deviceProfile.heightDp).append(" dp\n");
        b.append("Économie batterie : ").append(deviceProfile.powerSave ? "active" : "inactive").append("\n\n");
        b.append("PROFIL ACTIF\n").append(deviceProfile.modeLabel).append(" · score ").append(deviceProfile.score).append("/100\n\n");
        b.append("COMPATIBILITÉ\n");
        b.append("• Installation : Android 6.0 ou supérieur\n");
        b.append("• Téléphones 32 bits et 64 bits : pris en charge\n");
        b.append("• Bluetooth/UWB/RTT absents : fallbacks automatiques\n");
        b.append("• Téléphones peu puissants : rendu et effets réduits\n");
        b.append("• Tablettes et écrans étroits : interface adaptative\n");
        b.append("• Aucun appareil Samsung spécifique n’est requis\n\n");
        b.append("Certaines données restent limitées par les permissions et le matériel réellement présents.");
        showText("Device Compatibility", b.toString());
    }

    private void choosePerformanceProfile() {
        String[] labels = {
                "Automatique · recommandé",
                "Économie · téléphones anciens",
                "Équilibré · compatibilité maximale",
                "Ultra · flagship récent"
        };
        new AlertDialog.Builder(this)
                .setTitle("Performance Profile")
                .setItems(labels, (d, which) -> {
                    if (which == 0) {
                        fusion.edit().putString("performance", "auto").apply();
                        applyAutomaticProfile(true);
                    } else if (which == 1) applyProfile("economy", true);
                    else if (which == 2) applyProfile("balanced", true);
                    else applyProfile("ultra", true);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void activateSafeMode() {
        applyProfile("safe", true);
        new AlertDialog.Builder(this)
                .setTitle("Safe Mode activé")
                .setMessage("Animations lourdes, traînées et grille avancée sont désactivées. Les scans utilisent des délais plus courts pour limiter la chauffe et les blocages sur les téléphones anciens.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void applyAutomaticProfile(boolean announce) {
        String selected = fusion.getString("performance", "auto");
        if (!"auto".equals(selected)) {
            applyProfile(selected, announce);
            return;
        }
        deviceProfile = inspectDevice();
        String profile;
        if (deviceProfile.lowRam || Build.VERSION.SDK_INT <= 26 || deviceProfile.score < 42) profile = "economy";
        else if (deviceProfile.powerSave || deviceProfile.score < 68) profile = "balanced";
        else profile = "ultra";
        applyProfileValues(profile);
        deviceProfile.modeLabel = labelFor(profile) + " auto";
        fusion.edit().putString("resolved_profile", profile).apply();
        if (announce) toast("Profil automatique : " + deviceProfile.modeLabel);
    }

    private void applyProfile(String profile, boolean announce) {
        fusion.edit().putString("performance", profile).putString("resolved_profile", profile).apply();
        applyProfileValues(profile);
        deviceProfile = inspectDevice();
        deviceProfile.modeLabel = labelFor(profile);
        if (announce) toast("Profil activé : " + deviceProfile.modeLabel);
    }

    private void applyProfileValues(String profile) {
        SharedPreferences.Editor e = spatial.edit();
        if ("safe".equals(profile)) {
            e.putString("v4_quality", "low");
            e.putBoolean("v4_animation", false);
            e.putBoolean("v4_trails", false);
            e.putBoolean("v4_grid", false);
            e.putBoolean("v4_labels", true);
            e.putInt("v4_timeout", 220);
        } else if ("economy".equals(profile)) {
            e.putString("v4_quality", "low");
            e.putBoolean("v4_animation", false);
            e.putBoolean("v4_trails", false);
            e.putBoolean("v4_grid", true);
            e.putBoolean("v4_labels", true);
            e.putInt("v4_timeout", 280);
        } else if ("balanced".equals(profile)) {
            e.putString("v4_quality", "high");
            e.putBoolean("v4_animation", true);
            e.putBoolean("v4_trails", false);
            e.putBoolean("v4_grid", true);
            e.putBoolean("v4_labels", true);
            e.putInt("v4_timeout", 360);
        } else {
            e.putString("v4_quality", "ultra");
            e.putBoolean("v4_animation", true);
            e.putBoolean("v4_trails", true);
            e.putBoolean("v4_grid", true);
            e.putBoolean("v4_labels", true);
            e.putInt("v4_timeout", 430);
        }
        e.apply();
    }

    private DeviceProfile inspectDevice() {
        DeviceProfile p = new DeviceProfile();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(memoryInfo);
        p.totalRam = memoryInfo.totalMem;
        p.lowRam = am != null && am.isLowRamDevice();
        p.cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        p.ramLabel = p.totalRam <= 0 ? "?" : String.format(Locale.FRANCE, "%.1f GB", p.totalRam / 1073741824f);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        p.powerSave = pm != null && pm.isPowerSaveMode();

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configuration = manager == null ? null : manager.getDeviceConfigurationInfo();
        p.glVersion = configuration == null ? "inconnue" : configuration.getGlEsVersion();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        p.widthDp = Math.round(dm.widthPixels / dm.density);
        p.heightDp = Math.round(dm.heightPixels / dm.density);

        int score = 20;
        score += Math.min(30, p.cores * 4);
        if (p.totalRam >= 8L * 1024 * 1024 * 1024) score += 30;
        else if (p.totalRam >= 6L * 1024 * 1024 * 1024) score += 25;
        else if (p.totalRam >= 4L * 1024 * 1024 * 1024) score += 18;
        else if (p.totalRam >= 3L * 1024 * 1024 * 1024) score += 10;
        else score += 3;
        score += Math.min(20, Math.max(0, Build.VERSION.SDK_INT - 23) * 2);
        if (p.lowRam) score -= 20;
        if (p.powerSave) score -= 10;
        p.score = Math.max(10, Math.min(100, score));

        String resolved = fusion == null ? "balanced" : fusion.getString("resolved_profile", "balanced");
        p.modeLabel = labelFor(resolved);
        return p;
    }

    private String labelFor(String profile) {
        if ("safe".equals(profile)) return "Safe";
        if ("economy".equals(profile)) return "Économie";
        if ("ultra".equals(profile)) return "Ultra";
        return "Équilibré";
    }

    private void openSpatialTab(int tab) {
        try {
            Field field = V4Activity.class.getDeclaredField("tab");
            field.setAccessible(true);
            field.setInt(this, tab);
            Method render = V4Activity.class.getDeclaredMethod("render");
            render.setAccessible(true);
            render.invoke(this);
        } catch (Exception e) {
            toast("Module spatial indisponible : " + e.getMessage());
        }
    }

    private void invokeV4(String name) {
        try {
            Method method = V4Activity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            toast("Fonction indisponible sur ce téléphone.");
        }
    }

    private void invokeV10(String name) {
        try {
            Method method = V10Activity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            toast("Module v10 indisponible : " + e.getMessage());
        }
    }

    private void invokeV10(String name, Class<?> type, Object value) {
        try {
            Method method = V10Activity.class.getDeclaredMethod(name, type);
            method.setAccessible(true);
            method.invoke(this, value);
        } catch (Exception e) {
            toast("Assistant indisponible : " + e.getMessage());
        }
    }

    private void showText(String title, String body) {
        TextView text = text(body, 13, TEXT, false);
        text.setTextIsSelectable(true);
        text.setPadding(dp(18), dp(10), dp(18), dp(18));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Fermer", null).show();
    }

    private void section(LinearLayout root, String label) {
        root.addView(space(13));
        TextView title = text(label, 10, CYAN, true);
        title.setLetterSpacing(.12f);
        root.addView(title);
        root.addView(space(7));
    }

    private void module(LinearLayout root, String title, String subtitle, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(rounded(PANEL, 17));
        LinearLayout labels = column();
        labels.addView(text(title, 14, TEXT, true));
        labels.addView(text(subtitle, 12, MUTED, false));
        card.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(text("›", 28, CYAN, false));
        card.setContentDescription(title + ". " + subtitle);
        card.setOnClickListener(v -> action.run());
        root.addView(card);
        root.addView(space(8));
    }

    private TextView metric(String label, String value, int color) {
        TextView text = text(label + "\n" + value, 10, color, true);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(4), dp(10), dp(4), dp(10));
        text.setBackground(rounded(PANEL, 14));
        return text;
    }

    private TextView action(String label, int background, int foreground) {
        TextView text = text(label, 13, foreground, true);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(12), dp(15), dp(12), dp(15));
        text.setBackground(rounded(background, 16));
        return text;
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
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setLineSpacing(0, 1.12f);
        if (bold) text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return text;
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

    private static class DeviceProfile {
        long totalRam;
        boolean lowRam;
        boolean powerSave;
        int cores;
        int score;
        int widthDp;
        int heightDp;
        String ramLabel;
        String glVersion;
        String modeLabel;
    }
}
