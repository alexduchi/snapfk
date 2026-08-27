package com.sentry.local;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sentry v15 Signal Hunter.
 *
 * Uses Bluetooth RSSI, phone orientation and progressive haptics to help the
 * user approach a device that is already visible to Android. Direction is an
 * estimate built during a slow 360 degree sweep; it is never presented as a
 * true radio bearing.
 */
public class V15Activity extends V14Activity {
    private static final int BG = Color.rgb(2, 8, 11);
    private static final int PANEL = Color.rgb(9, 25, 30);
    private static final int PANEL_2 = Color.rgb(16, 42, 48);
    private static final int TEXT = Color.rgb(240, 253, 255);
    private static final int MUTED = Color.rgb(126, 163, 171);
    private static final int ACCENT = Color.rgb(61, 235, 211);
    private static final int BLUE = Color.rgb(77, 166, 255);
    private static final int GOOD = Color.rgb(82, 228, 144);
    private static final int WARN = Color.rgb(247, 188, 78);
    private static final int BAD = Color.rgb(245, 96, 124);
    private static final int VIOLET = Color.rgb(180, 130, 246);

    private final Handler hunterUi = new Handler(Looper.getMainLooper());
    private SharedPreferences hunterPrefs;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float heading;
    private boolean orientationReady;
    private HunterSession activeSession;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        hunterPrefs = getSharedPreferences("sentry_v15", MODE_PRIVATE);
        if (!hunterPrefs.contains("haptics")) hunterPrefs.edit().putBoolean("haptics", true).apply();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        hunterUi.postDelayed(navWatcher, 420);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerOrientation();
    }

    @Override
    protected void onPause() {
        unregisterOrientation();
        cancelVibration();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        hunterUi.removeCallbacks(navWatcher);
        if (activeSession != null) activeSession.stop();
        super.onDestroy();
    }

    private final Runnable navWatcher = new Runnable() {
        @Override public void run() {
            bindHunterNavigation();
            hunterUi.postDelayed(this, 620);
        }
    };

    private void bindHunterNavigation() {
        LinearLayout nav = getPrivateField(V4Activity.class, "nav", LinearLayout.class);
        if (nav != null && nav.getChildCount() >= 6) {
            View hub = nav.getChildAt(5);
            if (hub instanceof TextView) {
                ((TextView) hub).setText("Hunter");
                hub.setContentDescription("Ouvrir Signal Hunter");
                hub.setOnClickListener(v -> showHunterHub());
            }
        }
        replaceTextRecursive(getWindow().getDecorView(), "S14", "S15");
    }

    private void showHunterHub() {
        FrameLayout content = getPrivateField(V4Activity.class, "content", FrameLayout.class);
        TextView title = getPrivateField(V4Activity.class, "title", TextView.class);
        TextView subtitle = getPrivateField(V4Activity.class, "subtitle", TextView.class);
        if (content == null) return;
        if (title != null) title.setText("SIGNAL HUNTER");
        if (subtitle != null) subtitle.setText("Proximity · Sweep · Haptics");
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(14), dp(8), dp(14), dp(28));
        root.addView(hero());
        root.addView(space(11));
        root.addView(statusStrip());

        section(root, "LIVE TRACKING");
        addCard(root, "START SIGNAL HUNTER", "Choisir un appareil Bluetooth et suivre son signal en direct", ACCENT, this::showHunterPicker);
        addCard(root, "REACTION SETTINGS", "Vibrations progressives, réaction de retrouvailles et calibration", VIOLET, this::showHunterSettings);
        addCard(root, "RESCAN NOW", "Relancer immédiatement le balayage Bluetooth et spatial", BLUE, () -> {
            invokePrivate(V4Activity.class, "startSpatialSweep");
            toast("Balayage lancé.");
        });

        section(root, "SENTRY CORE");
        addCard(root, "SPATIAL INTELLIGENCE V14", "Historique, dossiers, alertes, heatmap et profils", GOOD, () -> invokePrivate(V14Activity.class, "showIntelHub"));
        addCard(root, "FIND MY DEVICE CLASSIC", "Guidage simple par puissance du signal", WARN, () -> invokePrivate(V13Activity.class, "showFindPicker"));
        addCard(root, "COMMAND HUB", "Outils réseau, GATT, sécurité et diagnostics", MUTED, () -> invokePrivate(V12Activity.class, "showHubPage"));

        root.addView(space(12));
        root.addView(text("La direction est une estimation obtenue en tournant lentement avec le téléphone. Les murs, le corps et les réflexions radio peuvent déplacer le meilleur secteur.", 11, MUTED, false));
        scroll.addView(root);
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activateHunterNav();
    }

    private View hero() {
        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(rounded(PANEL, 21));
        TextView eyebrow = text("SENTRY V15", 11, ACCENT, true);
        eyebrow.setLetterSpacing(.15f);
        card.addView(eyebrow);
        card.addView(space(5));
        card.addView(text("Signal Hunter", 28, TEXT, true));
        card.addView(space(5));
        card.addView(text("Un mode chaud-froid avec signal lissé, vibrations progressives et balayage directionnel estimé.", 13, MUTED, false));
        return card;
    }

    private View statusStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int ble = bluetoothDevices().size();
        row.addView(metric("BLE", String.valueOf(ble), ACCENT), weight());
        row.addView(spaceW(7));
        row.addView(metric("HAPTICS", hunterPrefs.getBoolean("haptics", true) ? "ON" : "OFF", hunterPrefs.getBoolean("haptics", true) ? GOOD : MUTED), weight());
        row.addView(spaceW(7));
        row.addView(metric("COMPASS", orientationReady ? "READY" : "WAIT", orientationReady ? BLUE : WARN), weight());
        return row;
    }

    private void activateHunterNav() {
        LinearLayout nav = getPrivateField(V4Activity.class, "nav", LinearLayout.class);
        if (nav == null) return;
        for (int i = 0; i < nav.getChildCount(); i++) {
            View child = nav.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            boolean active = i == 5;
            ((TextView) child).setTextColor(active ? Color.rgb(2, 24, 22) : MUTED);
            child.setBackground(active ? rounded(ACCENT, 17) : rounded(Color.TRANSPARENT, 17));
        }
    }

    private void showHunterPicker() {
        invokePrivate(V4Activity.class, "startSpatialSweep");
        List<RadioDevice> devices = bluetoothDevices();
        if (devices.isEmpty()) {
            toast("Aucun appareil Bluetooth connu. Attends la fin du scan puis réessaie.");
            return;
        }
        String[] labels = new String[devices.size()];
        long now = System.currentTimeMillis();
        for (int i = 0; i < devices.size(); i++) {
            RadioDevice d = devices.get(i);
            String age = now - d.lastSeen < 15000 ? "LIVE" : shortAge(now - d.lastSeen);
            labels[i] = d.name + "\n" + d.rssi + " dBm · " + signalLabel(d.rssi) + " · " + age;
        }
        new AlertDialog.Builder(this)
                .setTitle("Signal Hunter")
                .setItems(labels, (dialog, which) -> showHunter(devices.get(which).id))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showHunter(String id) {
        if (activeSession != null) activeSession.stop();
        RadioDevice initial = findDevice(id);
        if (initial == null) {
            toast("Appareil introuvable.");
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(16), dp(14), dp(14));

        TextView label = text("SENTRY V15 · SIGNAL HUNTER", 11, ACCENT, true);
        label.setGravity(Gravity.CENTER);
        label.setLetterSpacing(.12f);
        root.addView(label);
        TextView target = text(initial.name, 25, TEXT, true);
        target.setGravity(Gravity.CENTER);
        target.setPadding(0, dp(5), 0, dp(3));
        root.addView(target);

        HunterView view = new HunterView(this);
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView status = text("Initialisation du suivi…", 13, TEXT, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(status);

        TextView hint = text("Pour estimer le sens : tiens le téléphone devant toi et tourne lentement sur 360°. La vibration accélère quand le signal se renforce.", 10.5f, MUTED, false);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);
        root.addView(space(9));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView calibrate = action("CALIBRER", PANEL_2, TEXT);
        TextView haptics = action(hunterPrefs.getBoolean("haptics", true) ? "HAPTICS ON" : "HAPTICS OFF", PANEL_2, TEXT);
        row.addView(calibrate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(spaceW(7));
        row.addView(haptics, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row);
        root.addView(space(7));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        TextView rescan = action("RESCAN", BLUE, Color.rgb(2, 18, 28));
        TextView close = action("FERMER", ACCENT, Color.rgb(2, 24, 22));
        row2.addView(rescan, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row2.addView(spaceW(7));
        row2.addView(close, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row2);

        dialog.setContentView(root);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(rounded(BG, 0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        HunterSession session = new HunterSession(id, dialog, view, status, haptics);
        activeSession = session;
        calibrate.setOnClickListener(v -> session.resetCalibration());
        haptics.setOnClickListener(v -> session.toggleHaptics());
        rescan.setOnClickListener(v -> session.forceScan());
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> session.stop());
        session.start();
    }

    private void showHunterSettings() {
        boolean enabled = hunterPrefs.getBoolean("haptics", true);
        String message = "Vibrations progressives : " + (enabled ? "activées" : "désactivées") +
                "\n\nProche : impulsions rapides\nMoyen : impulsions espacées\nPerdu : silence\nRetrouvé : motif spécial\n\nLa direction reste une estimation de balayage.";
        new AlertDialog.Builder(this)
                .setTitle("Reaction Settings")
                .setMessage(message)
                .setPositiveButton(enabled ? "Désactiver haptics" : "Activer haptics", (d, w) -> {
                    hunterPrefs.edit().putBoolean("haptics", !enabled).apply();
                    cancelVibration();
                    showHunterHub();
                })
                .setNeutralButton("Tester", (d, w) -> vibratePattern(new long[]{0, 70, 70, 110, 70, 180}))
                .setNegativeButton("Fermer", null)
                .show();
    }

    private final class HunterSession {
        final String id;
        final Dialog dialog;
        final HunterView view;
        final TextView status;
        final TextView hapticButton;
        final double[] sectorSum = new double[12];
        final double[] sectorSq = new double[12];
        final int[] sectorCount = new int[12];
        final List<Integer> rawWindow = new ArrayList<>();
        final List<SignalPoint> signalHistory = new ArrayList<>();
        boolean running = true;
        boolean wasVisible;
        long lastDeviceSeen;
        int lastRaw = -127;
        int best = -127;
        long lastHaptic;
        long lastSweep;
        float smoothed = -100;

        HunterSession(String id, Dialog dialog, HunterView view, TextView status, TextView hapticButton) {
            this.id = id;
            this.dialog = dialog;
            this.view = view;
            this.status = status;
            this.hapticButton = hapticButton;
        }

        void start() {
            resetCalibration();
            forceScan();
            hunterUi.post(tick);
        }

        void stop() {
            running = false;
            hunterUi.removeCallbacks(tick);
            cancelVibration();
            if (activeSession == this) activeSession = null;
        }

        void resetCalibration() {
            for (int i = 0; i < 12; i++) {
                sectorSum[i] = 0;
                sectorSq[i] = 0;
                sectorCount[i] = 0;
            }
            view.flashCalibration();
            toast("Calibration remise à zéro. Tourne lentement sur 360°.");
        }

        void toggleHaptics() {
            boolean value = !hunterPrefs.getBoolean("haptics", true);
            hunterPrefs.edit().putBoolean("haptics", value).apply();
            hapticButton.setText(value ? "HAPTICS ON" : "HAPTICS OFF");
            if (!value) cancelVibration(); else vibrateOne(55, 130);
        }

        void forceScan() {
            invokePrivate(V4Activity.class, "startSpatialSweep");
            lastSweep = System.currentTimeMillis();
        }

        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!running || !dialog.isShowing()) return;
                long now = System.currentTimeMillis();
                if (now - lastSweep > 4600) forceScan();
                RadioDevice d = findDevice(id);
                boolean visible = d != null && now - d.lastSeen < 12000 && d.rssi <= -15 && d.rssi >= -120;

                if (visible) {
                    boolean fresh = d.lastSeen != lastDeviceSeen || d.rssi != lastRaw;
                    if (fresh) {
                        lastDeviceSeen = d.lastSeen;
                        lastRaw = d.rssi;
                        rawWindow.add(d.rssi);
                        while (rawWindow.size() > 9) rawWindow.remove(0);
                        smoothed = filteredRssi(rawWindow);
                        best = Math.max(best, Math.round(smoothed));
                        signalHistory.add(new SignalPoint(now, smoothed));
                        while (!signalHistory.isEmpty() && now - signalHistory.get(0).time > 9000) signalHistory.remove(0);
                        if (orientationReady) addSectorSample(heading, smoothed);
                    }
                    Direction direction = directionEstimate();
                    String trend = trendLabel();
                    int coverage = coveragePercent();
                    String side = direction.bearing < 0 ? "tourne lentement pour calibrer" : relativeDirection(direction.bearing, heading);
                    status.setText(Math.round(smoothed) + " dBm · " + signalLabel(Math.round(smoothed)) + " · " + trend +
                            "\nmeilleur " + best + " dBm · " + side + " · confiance " + direction.confidence + "%");
                    view.update(d.name, smoothed, best, trend, heading, direction.bearing, direction.confidence, coverage, true);
                    if (!wasVisible) {
                        view.flashFound();
                        if (hunterPrefs.getBoolean("haptics", true)) vibratePattern(new long[]{0, 80, 60, 100, 60, 190});
                    } else tickHaptics(smoothed, trend, now);
                } else {
                    status.setText("Signal momentanément perdu\nReste dans la zone et relance un balayage");
                    Direction direction = directionEstimate();
                    view.update(d == null ? "Appareil" : d.name, smoothed, best, "recherche", heading, direction.bearing, direction.confidence, coveragePercent(), false);
                    cancelVibration();
                }
                wasVisible = visible;
                hunterUi.postDelayed(this, 280);
            }
        };

        private void addSectorSample(float bearing, float value) {
            int sector = Math.min(11, Math.max(0, (int) (normalize360(bearing) / 30f)));
            sectorSum[sector] += value;
            sectorSq[sector] += value * value;
            sectorCount[sector]++;
        }

        private Direction directionEstimate() {
            int bestIndex = -1;
            double bestAverage = -999;
            double second = -999;
            int total = 0;
            int covered = 0;
            for (int i = 0; i < 12; i++) {
                total += sectorCount[i];
                if (sectorCount[i] == 0) continue;
                covered++;
                double average = sectorSum[i] / sectorCount[i];
                if (average > bestAverage) {
                    second = bestAverage;
                    bestAverage = average;
                    bestIndex = i;
                } else if (average > second) second = average;
            }
            if (bestIndex < 0 || total < 6 || covered < 3) return new Direction(-1, Math.min(22, total * 3));
            double mean = bestAverage;
            double variance = Math.max(0, sectorSq[bestIndex] / sectorCount[bestIndex] - mean * mean);
            double spread = second < -900 ? 0 : bestAverage - second;
            int confidence = (int) Math.round(covered * 4.2 + Math.min(24, sectorCount[bestIndex] * 2.5) + Math.min(28, Math.max(0, spread) * 4) - Math.min(18, Math.sqrt(variance) * 2));
            confidence = Math.max(12, Math.min(88, confidence));
            float bearing = bestIndex * 30f + 15f;
            return new Direction(bearing, confidence);
        }

        private int coveragePercent() {
            int covered = 0;
            for (int count : sectorCount) if (count > 0) covered++;
            return Math.round(covered / 12f * 100f);
        }

        private String trendLabel() {
            if (signalHistory.size() < 3) return "stabilisation";
            SignalPoint current = signalHistory.get(signalHistory.size() - 1);
            SignalPoint reference = signalHistory.get(0);
            for (SignalPoint point : signalHistory) {
                if (current.time - point.time >= 2800) {
                    reference = point;
                    break;
                }
            }
            float delta = current.value - reference.value;
            if (delta >= 3.2f) return "tu te rapproches";
            if (delta <= -3.2f) return "tu t’éloignes";
            return "signal stable";
        }

        private void tickHaptics(float rssi, String trend, long now) {
            if (!hunterPrefs.getBoolean("haptics", true)) return;
            long interval;
            long duration;
            int amplitude;
            if (rssi >= -52) { interval = 170; duration = 68; amplitude = 245; }
            else if (rssi >= -61) { interval = 300; duration = 55; amplitude = 205; }
            else if (rssi >= -70) { interval = 520; duration = 44; amplitude = 165; }
            else if (rssi >= -80) { interval = 820; duration = 34; amplitude = 120; }
            else { interval = 1350; duration = 25; amplitude = 85; }
            if ("tu te rapproches".equals(trend)) interval = Math.max(130, (long) (interval * .78));
            if (now - lastHaptic >= interval) {
                vibrateOne(duration, amplitude);
                lastHaptic = now;
            }
        }
    }

    private final class HunterView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        String name = "Appareil";
        String trend = "recherche";
        float rssi = -100;
        int best = -127;
        float phoneHeading;
        float targetBearing = -1;
        int confidence;
        int coverage;
        boolean visible;
        long foundFlash;
        long calibrationFlash;
        final long born = System.currentTimeMillis();

        HunterView(Context context) {
            super(context);
            setBackgroundColor(BG);
        }

        void update(String name, float rssi, int best, String trend, float phoneHeading, float targetBearing, int confidence, int coverage, boolean visible) {
            this.name = name;
            this.rssi = rssi;
            this.best = best;
            this.trend = trend;
            this.phoneHeading = phoneHeading;
            this.targetBearing = targetBearing;
            this.confidence = confidence;
            this.coverage = coverage;
            this.visible = visible;
            invalidate();
        }

        void flashFound() { foundFlash = System.currentTimeMillis(); invalidate(); }
        void flashCalibration() { calibrationFlash = System.currentTimeMillis(); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h * .52f;
            float radius = Math.min(w, h) * .34f;
            long now = System.currentTimeMillis();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BG);
            c.drawRect(0, 0, w, h, paint);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setColor(TEXT);
            paint.setTextSize(dp(20));
            c.drawText(shortName(name, 28), cx, dp(35), paint);
            paint.setTextSize(dp(12));
            paint.setColor(MUTED);
            c.drawText(Math.round(rssi) + " dBm · meilleur " + best + " dBm", cx, dp(58), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            for (int i = 1; i <= 5; i++) {
                paint.setColor(Color.argb(55, 61, 235, 211));
                c.drawCircle(cx, cy, radius * i / 5f, paint);
            }
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30 - 90);
                c.drawLine(cx, cy, cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius, paint);
            }

            float sweep = ((now - born) % 2800) / 2800f * 360f;
            paint.setColor(Color.argb(110, 61, 235, 211));
            paint.setStrokeWidth(dp(3));
            RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            c.drawArc(oval, sweep - 24, 24, false, paint);

            float strength = Math.max(0f, Math.min(1f, (rssi + 100f) / 55f));
            int signalColor = !visible ? MUTED : strength > .72f ? GOOD : strength > .42f ? WARN : BAD;
            float pulse = 1f + .08f * (float) Math.sin(now / 120.0);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(45, Color.red(signalColor), Color.green(signalColor), Color.blue(signalColor)));
            c.drawCircle(cx, cy, (dp(32) + radius * .17f * strength) * pulse, paint);
            paint.setColor(signalColor);
            c.drawCircle(cx, cy, dp(16) + radius * .09f * strength, paint);

            if (targetBearing >= 0) {
                float relative = normalize180(targetBearing - phoneHeading);
                double angle = Math.toRadians(relative - 90f);
                float arrowR = radius * .72f;
                float ax = cx + (float) Math.cos(angle) * arrowR;
                float ay = cy + (float) Math.sin(angle) * arrowR;
                drawArrow(c, cx, cy, ax, ay, confidence >= 55 ? ACCENT : WARN);
            }

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(18));
            paint.setColor(visible ? TEXT : MUTED);
            c.drawText(trend.toUpperCase(Locale.FRANCE), cx, cy + radius + dp(36), paint);
            paint.setTextSize(dp(11));
            paint.setColor(ACCENT);
            String direction = targetBearing < 0 ? "DIRECTION EN CALIBRATION" : "CAP ESTIMÉ " + Math.round(targetBearing) + "° · " + confidence + "%";
            c.drawText(direction, cx, cy + radius + dp(58), paint);
            paint.setColor(MUTED);
            c.drawText("COUVERTURE 360° : " + coverage + "% · TÉLÉPHONE " + Math.round(phoneHeading) + "°", cx, cy + radius + dp(77), paint);

            if (now - foundFlash < 900) {
                float t = (now - foundFlash) / 900f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(5));
                paint.setColor(Color.argb((int) (220 * (1 - t)), 82, 228, 144));
                c.drawCircle(cx, cy, radius * (.25f + .8f * t), paint);
            }
            if (now - calibrationFlash < 1000) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(4));
                paint.setColor(Color.argb(180, 180, 130, 246));
                c.drawCircle(cx, cy, radius * .96f, paint);
            }
            postInvalidateDelayed(32);
        }

        private void drawArrow(Canvas c, float sx, float sy, float ex, float ey, int color) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(7));
            paint.setColor(color);
            c.drawLine(sx, sy, ex, ey, paint);
            double angle = Math.atan2(ey - sy, ex - sx);
            float head = dp(18);
            Path p = new Path();
            p.moveTo(ex, ey);
            p.lineTo(ex - (float) Math.cos(angle - .55) * head, ey - (float) Math.sin(angle - .55) * head);
            p.moveTo(ex, ey);
            p.lineTo(ex - (float) Math.cos(angle + .55) * head, ey - (float) Math.sin(angle + .55) * head);
            c.drawPath(p, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }
    }

    private void registerOrientation() {
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(orientationListener, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void unregisterOrientation() {
        if (sensorManager != null) sensorManager.unregisterListener(orientationListener);
    }

    private final SensorEventListener orientationListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
            float[] matrix = new float[9];
            float[] orientation = new float[3];
            SensorManager.getRotationMatrixFromVector(matrix, event.values);
            SensorManager.getOrientation(matrix, orientation);
            heading = normalize360((float) Math.toDegrees(orientation[0]));
            orientationReady = true;
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private List<RadioDevice> bluetoothDevices() {
        List<RadioDevice> result = new ArrayList<>();
        Object raw = getPrivateValue(V4Activity.class, "devices");
        if (!(raw instanceof Map)) return result;
        for (Object value : ((Map<?, ?>) raw).values()) {
            RadioDevice d = copyDevice(value);
            if (d == null) continue;
            String source = d.source.toLowerCase(Locale.ROOT);
            if (source.contains("bluetooth") || d.id.startsWith("ble:")) result.add(d);
        }
        result.sort(Comparator.comparingLong((RadioDevice d) -> d.lastSeen).reversed().thenComparingInt(d -> -d.rssi));
        return result;
    }

    private RadioDevice findDevice(String id) {
        Object raw = getPrivateValue(V4Activity.class, "devices");
        if (!(raw instanceof Map)) return null;
        Object value = ((Map<?, ?>) raw).get(id);
        return copyDevice(value);
    }

    private RadioDevice copyDevice(Object value) {
        if (value == null) return null;
        try {
            RadioDevice d = new RadioDevice();
            d.id = String.valueOf(readField(value, "id"));
            Object name = readField(value, "name");
            Object source = readField(value, "source");
            Object rssi = readField(value, "rssi");
            Object lastSeen = readField(value, "lastSeen");
            d.name = name == null ? "Appareil" : String.valueOf(name);
            d.source = source == null ? "Bluetooth" : String.valueOf(source);
            d.rssi = rssi instanceof Number ? ((Number) rssi).intValue() : -100;
            d.lastSeen = lastSeen instanceof Number ? ((Number) lastSeen).longValue() : 0L;
            return d;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object readField(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private Object getPrivateValue(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(this);
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> T getPrivateField(Class<?> owner, String name, Class<T> type) {
        Object value = getPrivateValue(owner, name);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private void invokePrivate(Class<?> owner, String methodName) {
        try {
            Method method = owner.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            toast("Fonction momentanément indisponible.");
        }
    }

    private void replaceTextRecursive(View view, String from, String to) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (from.contentEquals(text.getText())) text.setText(to);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) replaceTextRecursive(group.getChildAt(i), from, to);
        }
    }

    private void vibrateOne(long duration, int amplitude) {
        if (!hunterPrefs.getBoolean("haptics", true)) return;
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(duration, Math.max(1, Math.min(255, amplitude))));
            else vibrator.vibrate(duration);
        } catch (Exception ignored) { }
    }

    private void vibratePattern(long[] pattern) {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            else vibrator.vibrate(pattern, -1);
        } catch (Exception ignored) { }
    }

    private void cancelVibration() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) try { vibrator.cancel(); } catch (Exception ignored) { }
    }

    private float filteredRssi(List<Integer> values) {
        if (values.isEmpty()) return -100;
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int start = sorted.size() >= 5 ? 1 : 0;
        int end = sorted.size() >= 5 ? sorted.size() - 1 : sorted.size();
        float sum = 0;
        for (int i = start; i < end; i++) sum += sorted.get(i);
        return sum / Math.max(1, end - start);
    }

    private String signalLabel(int rssi) {
        if (rssi >= -52) return "très proche";
        if (rssi >= -62) return "proche";
        if (rssi >= -72) return "moyen";
        if (rssi >= -82) return "faible";
        return "très faible";
    }

    private String relativeDirection(float bearing, float currentHeading) {
        float relative = normalize180(bearing - currentHeading);
        float abs = Math.abs(relative);
        if (abs <= 22) return "devant";
        if (abs >= 158) return "derrière";
        if (relative > 0) return abs < 75 ? "à droite" : "fortement à droite";
        return abs < 75 ? "à gauche" : "fortement à gauche";
    }

    private String shortAge(long ms) {
        if (ms < 60000) return Math.max(1, ms / 1000) + " s";
        if (ms < 3600000) return ms / 60000 + " min";
        if (ms < 86400000) return ms / 3600000 + " h";
        return ms / 86400000 + " j";
    }

    private float normalize360(float value) {
        value %= 360f;
        return value < 0 ? value + 360f : value;
    }

    private float normalize180(float value) {
        value = normalize360(value);
        return value > 180f ? value - 360f : value;
    }

    private String shortName(String value, int max) {
        if (value == null) return "Appareil";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private void section(LinearLayout root, String label) {
        root.addView(space(18));
        TextView view = text(label, 11, MUTED, true);
        view.setLetterSpacing(.13f);
        root.addView(view);
        root.addView(space(7));
    }

    private void addCard(LinearLayout root, String title, String subtitle, int color, Runnable action) {
        LinearLayout card = column();
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(rounded(PANEL, 18));
        TextView t = text(title, 14, color, true);
        TextView s = text(subtitle, 11.5f, MUTED, false);
        s.setPadding(0, dp(4), 0, 0);
        card.addView(t);
        card.addView(s);
        card.setOnClickListener(v -> action.run());
        root.addView(card);
        root.addView(space(8));
    }

    private TextView metric(String label, String value, int color) {
        TextView view = text(label + "\n" + value, 9.5f, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(4), dp(10), dp(4), dp(10));
        view.setBackground(rounded(PANEL, 14));
        return view;
    }

    private TextView action(String label, int background, int foreground) {
        TextView view = text(label, 12, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(9), dp(13), dp(9), dp(13));
        view.setBackground(rounded(background, 15));
        return view;
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

    private View space(int h) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return view;
    }

    private View spaceW(int w) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1));
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static final class RadioDevice {
        String id = "";
        String name = "Appareil";
        String source = "Bluetooth";
        int rssi = -100;
        long lastSeen;
    }

    private static final class SignalPoint {
        final long time;
        final float value;
        SignalPoint(long time, float value) { this.time = time; this.value = value; }
    }

    private static final class Direction {
        final float bearing;
        final int confidence;
        Direction(float bearing, int confidence) { this.bearing = bearing; this.confidence = confidence; }
    }
}