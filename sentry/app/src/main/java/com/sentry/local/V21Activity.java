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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sentry v21 HyperTrack.
 * Phone-only Bluetooth tracking using robust RSSI filtering, compass/gyro fusion,
 * 360-degree sector scoring, calibration and uncertainty reporting.
 */
public class V21Activity extends V20Activity {
    private static final int BG = Color.rgb(2, 8, 12);
    private static final int PANEL = Color.rgb(10, 26, 32);
    private static final int PANEL_2 = Color.rgb(17, 43, 51);
    private static final int TEXT = Color.rgb(239, 253, 255);
    private static final int MUTED = Color.rgb(126, 163, 172);
    private static final int CYAN = Color.rgb(61, 235, 211);
    private static final int BLUE = Color.rgb(79, 166, 255);
    private static final int GOOD = Color.rgb(83, 228, 144);
    private static final int WARN = Color.rgb(247, 190, 80);
    private static final int BAD = Color.rgb(245, 98, 124);
    private static final int VIOLET = Color.rgb(181, 132, 247);

    private final Handler hyperUi = new Handler(Looper.getMainLooper());
    private SharedPreferences hyperPrefs;
    private SensorManager hyperSensors;
    private Sensor rotationSensor;
    private Sensor gyroSensor;
    private Sensor accelerometer;
    private float heading;
    private float gyroZ;
    private float motion;
    private int compassAccuracy;
    private boolean headingReady;
    private HyperSession activeSession;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        hyperPrefs = getSharedPreferences("sentry_v21", MODE_PRIVATE);
        if (!hyperPrefs.contains("reference_rssi")) hyperPrefs.edit().putFloat("reference_rssi", -59f).apply();
        if (!hyperPrefs.contains("path_loss")) hyperPrefs.edit().putFloat("path_loss", 2.2f).apply();
        if (!hyperPrefs.contains("haptics")) hyperPrefs.edit().putBoolean("haptics", true).apply();
        hyperSensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (hyperSensors != null) {
            rotationSensor = hyperSensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            gyroSensor = hyperSensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelerometer = hyperSensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }
        hyperUi.postDelayed(navLoop, 320);
        hyperUi.postDelayed(this::showHyperHub, 620);
    }

    @Override protected void onResume() {
        super.onResume();
        registerHyperSensors();
    }

    @Override protected void onPause() {
        unregisterHyperSensors();
        cancelVibration();
        super.onPause();
    }

    @Override protected void onDestroy() {
        hyperUi.removeCallbacks(navLoop);
        if (activeSession != null) activeSession.stop();
        unregisterHyperSensors();
        super.onDestroy();
    }

    private final Runnable navLoop = new Runnable() {
        @Override public void run() {
            LinearLayout nav = privateField(V4Activity.class, "nav", LinearLayout.class);
            if (nav != null && nav.getChildCount() >= 6) {
                View last = nav.getChildAt(5);
                if (last instanceof TextView) {
                    ((TextView) last).setText("Hyper");
                    last.setContentDescription("Ouvrir HyperTrack");
                    last.setOnClickListener(v -> showHyperHub());
                }
            }
            replaceExact(getWindow().getDecorView(), "S20", "S21");
            hyperUi.postDelayed(this, 700);
        }
    };

    private void showHyperHub() {
        FrameLayout content = privateField(V4Activity.class, "content", FrameLayout.class);
        TextView title = privateField(V4Activity.class, "title", TextView.class);
        TextView subtitle = privateField(V4Activity.class, "subtitle", TextView.class);
        if (content == null) return;
        if (title != null) title.setText("HYPERTRACK");
        if (subtitle != null) subtitle.setText("Flèche 2D · Distance estimée · Confiance");
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = column();
        root.setPadding(dp(14), dp(8), dp(14), dp(28));
        root.addView(hero());
        root.addView(space(10));
        root.addView(statusStrip());

        section(root, "SUIVI ULTRA-PRÉCIS AVEC LE TÉLÉPHONE");
        feature(root, "LANCER HYPERTRACK", "Choisir un appareil Bluetooth et obtenir une flèche 2D", "HyperTrack fusionne le RSSI Bluetooth, la boussole, le gyroscope et le mouvement. Il filtre les mesures aberrantes et cherche le meilleur secteur pendant que tu tournes lentement.", CYAN, this::showPicker);
        feature(root, "CALIBRATION À 1 MÈTRE", "Améliorer l'estimation de distance pour un appareil précis", "Place l'appareil à environ un mètre du téléphone, sans obstacle, puis laisse Sentry mesurer. Cette calibration ne rend pas le RSSI parfait, mais elle réduit l'erreur de distance.", VIOLET, this::showCalibrationPicker);
        feature(root, "RÉGLAGES DE PRÉCISION", "Adapter le modèle aux murs et à la pièce", "Le coefficient de propagation décrit la vitesse à laquelle le signal baisse. Une pièce ouverte utilise une valeur faible, un environnement avec murs utilise une valeur plus élevée.", BLUE, this::showPrecisionSettings);
        feature(root, "DIAGNOSTIC DU CAP", "Vérifier boussole, gyroscope et qualité des capteurs", "Une boussole perturbée par du métal ou un aimant rend la flèche moins fiable. Fais un mouvement en forme de huit si la précision est faible.", WARN, this::showSensorDiagnostic);

        section(root, "OUTILS COMPLÉMENTAIRES");
        feature(root, "CARTE DE SIGNAL", "Mémoriser les zones où le signal est le plus fort", "La carte thermique aide lorsque la direction instantanée saute. En marchant et en ajoutant plusieurs points, tu repères une zone probable.", GOOD, () -> invokePrivate(V14Activity.class, "showWifiHeatmap"));
        feature(root, "CENTRE SENTRY V20", "Protection Wi-Fi, incidents, audits et coffre chiffré", "Ouvre toutes les fonctions de cybersécurité défensive et les outils précédents.", MUTED, () -> invokePrivate(V20Activity.class, "showCommandCenter"));

        root.addView(space(13));
        root.addView(text("Important : avec un téléphone seul et un appareil Bluetooth classique, l'angle et la distance restent des estimations. HyperTrack affiche toujours une marge d'erreur et un niveau de confiance.", 11, MUTED, false));
        scroll.addView(root);
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activateHyperNav();
    }

    private View hero() {
        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(rounded(PANEL, 22));
        TextView small = text("SENTRY V21", 11, CYAN, true);
        small.setLetterSpacing(.16f);
        card.addView(small);
        card.addView(space(5));
        card.addView(text("HyperTrack", 30, TEXT, true));
        card.addView(space(5));
        card.addView(text("Le maximum réaliste avec un téléphone seul : filtrage robuste, balayage 360°, flèche stabilisée et distance avec marge d'erreur.", 13, MUTED, false));
        return card;
    }

    private View statusStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric("BOUSSOLE", headingReady ? "PRÊTE" : "ATTENTE", headingReady ? GOOD : WARN), weight());
        row.addView(spaceW(7));
        row.addView(metric("CAPTEUR", sensorQualityLabel(), sensorQualityColor()), weight());
        row.addView(spaceW(7));
        row.addView(metric("CALIB. 1M", Math.round(hyperPrefs.getFloat("reference_rssi", -59f)) + " dBm", VIOLET), weight());
        return row;
    }

    private void showPicker() {
        invokePrivate(V4Activity.class, "startSpatialSweep");
        List<RadioDevice> devices = bluetoothDevices();
        if (devices.isEmpty()) {
            toast("Aucun appareil Bluetooth connu. Attends quelques secondes puis réessaie.");
            return;
        }
        String[] labels = new String[devices.size()];
        long now = System.currentTimeMillis();
        for (int i = 0; i < devices.size(); i++) {
            RadioDevice d = devices.get(i);
            labels[i] = d.name + "\n" + d.rssi + " dBm · vu il y a " + age(now - d.lastSeen);
        }
        new AlertDialog.Builder(this).setTitle("Choisir l'appareil à suivre").setItems(labels, (dialog, which) -> showHyperTracker(devices.get(which).id)).setNegativeButton("Annuler", null).show();
    }

    private void showHyperTracker(String id) {
        if (activeSession != null) activeSession.stop();
        RadioDevice initial = findDevice(id);
        if (initial == null) { toast("Appareil introuvable."); return; }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(14), dp(12), dp(12));

        TextView top = text("SENTRY V21 · HYPERTRACK", 11, CYAN, true);
        top.setGravity(Gravity.CENTER);
        top.setLetterSpacing(.14f);
        root.addView(top);
        TextView target = text(initial.name, 25, TEXT, true);
        target.setGravity(Gravity.CENTER);
        target.setPadding(0, dp(4), 0, 0);
        root.addView(target);

        HyperView view = new HyperView(this);
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView status = text("Collecte des premières mesures…", 13, TEXT, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.addView(status);
        TextView tip = text("Tourne lentement sur 360° en tenant le téléphone devant toi. Ensuite, marche quelques pas dans le sens indiqué pour confirmer la tendance.", 10.5f, MUTED, false);
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);
        root.addView(space(8));

        LinearLayout row1 = new LinearLayout(this);
        TextView reset = action("NOUVEAU 360°", PANEL_2, TEXT);
        TextView freeze = action("FIGER", PANEL_2, TEXT);
        row1.addView(reset, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row1.addView(spaceW(7));
        row1.addView(freeze, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row1);
        root.addView(space(7));

        LinearLayout row2 = new LinearLayout(this);
        TextView scan = action("RE-SCANNER", BLUE, Color.rgb(2, 18, 28));
        TextView close = action("FERMER", CYAN, Color.rgb(2, 24, 22));
        row2.addView(scan, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
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

        HyperSession session = new HyperSession(id, dialog, view, status, freeze);
        activeSession = session;
        reset.setOnClickListener(v -> session.resetSweep());
        freeze.setOnClickListener(v -> session.toggleFreeze());
        scan.setOnClickListener(v -> session.forceScan());
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> session.stop());
        session.start();
    }

    private final class HyperSession {
        private static final int SECTORS = 36;
        final String id;
        final Dialog dialog;
        final HyperView view;
        final TextView status;
        final TextView freezeButton;
        final double[] sums = new double[SECTORS];
        final double[] squares = new double[SECTORS];
        final int[] counts = new int[SECTORS];
        final List<Integer> rawWindow = new ArrayList<>();
        final List<SignalPoint> trendWindow = new ArrayList<>();
        final Kalman1D kalman = new Kalman1D();
        boolean running = true;
        boolean frozen;
        long lastSeenStamp;
        int lastRaw = -127;
        int bestRssi = -127;
        float filtered = -100f;
        float arrowBearing = -1f;
        float displayedBearing = -1f;
        int confidence;
        float angleError = 180f;
        float distance = -1f;
        float distanceError = -1f;
        long lastScan;
        long lastVibration;

        HyperSession(String id, Dialog dialog, HyperView view, TextView status, TextView freezeButton) {
            this.id = id; this.dialog = dialog; this.view = view; this.status = status; this.freezeButton = freezeButton;
        }

        void start() { resetSweep(); forceScan(); hyperUi.post(tick); }
        void stop() { running = false; hyperUi.removeCallbacks(tick); cancelVibration(); if (activeSession == this) activeSession = null; }

        void resetSweep() {
            Arrays.fill(sums, 0); Arrays.fill(squares, 0); Arrays.fill(counts, 0);
            rawWindow.clear(); trendWindow.clear(); kalman.reset();
            arrowBearing = displayedBearing = -1f; confidence = 0; angleError = 180f;
            view.flashReset();
            toast("Nouveau balayage : tourne lentement sur 360°.");
        }

        void toggleFreeze() {
            frozen = !frozen;
            freezeButton.setText(frozen ? "REPRENDRE" : "FIGER");
            if (frozen) cancelVibration();
        }

        void forceScan() { invokePrivate(V4Activity.class, "startSpatialSweep"); lastScan = System.currentTimeMillis(); }

        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!running || !dialog.isShowing()) return;
                long now = System.currentTimeMillis();
                if (now - lastScan > 3200) forceScan();
                RadioDevice d = findDevice(id);
                boolean freshVisible = d != null && now - d.lastSeen < 14000 && d.rssi >= -120 && d.rssi <= -10;
                if (!frozen && freshVisible) {
                    boolean newSample = d.lastSeen != lastSeenStamp || d.rssi != lastRaw;
                    if (newSample) {
                        lastSeenStamp = d.lastSeen; lastRaw = d.rssi;
                        rawWindow.add(d.rssi); while (rawWindow.size() > 11) rawWindow.remove(0);
                        float robust = robustMean(rawWindow);
                        filtered = kalman.update(robust, Math.max(1.5f, sampleStd(rawWindow)));
                        bestRssi = Math.max(bestRssi, Math.round(filtered));
                        trendWindow.add(new SignalPoint(now, filtered));
                        while (!trendWindow.isEmpty() && now - trendWindow.get(0).time > 10000) trendWindow.remove(0);
                        if (headingReady && Math.abs(gyroZ) < 2.4f) addSector(heading, filtered);
                        updateEstimate();
                    }
                }

                String trend = trendLabel();
                int coverage = coveragePercent();
                String directionText = arrowBearing < 0 ? "tourne pour calibrer" : relativeDirection(displayedBearing, heading);
                String distanceText = distance < 0 ? "distance en attente" : String.format(Locale.FRANCE, "%.1f m ± %.1f m", distance, distanceError);
                status.setText(Math.round(filtered) + " dBm · " + trend + "\n" + directionText + " · " + distanceText + " · confiance " + confidence + "%");
                view.update(d == null ? "Appareil" : d.name, filtered, bestRssi, heading, displayedBearing, confidence, angleError, distance, distanceError, coverage, trend, freshVisible, frozen);
                if (!frozen && freshVisible) tickHaptics(now);
                else cancelVibration();
                hyperUi.postDelayed(this, 220);
            }
        };

        private void addSector(float bearing, float rssi) {
            int index = Math.min(SECTORS - 1, Math.max(0, (int)(normalize360(bearing) / (360f / SECTORS))));
            sums[index] += rssi; squares[index] += rssi * rssi; counts[index]++;
        }

        private void updateEstimate() {
            int total = 0, covered = 0;
            double bestScore = -9999, secondScore = -9999;
            int bestIndex = -1;
            double[] means = new double[SECTORS];
            for (int i = 0; i < SECTORS; i++) {
                total += counts[i];
                if (counts[i] == 0) { means[i] = -999; continue; }
                covered++;
                means[i] = sums[i] / counts[i];
                double variance = Math.max(0, squares[i] / counts[i] - means[i] * means[i]);
                double score = means[i] - Math.sqrt(variance) * 0.35 + Math.min(2.5, counts[i] * 0.18);
                if (score > bestScore) { secondScore = bestScore; bestScore = score; bestIndex = i; }
                else if (score > secondScore) secondScore = score;
            }
            if (bestIndex < 0 || total < 8 || covered < 4) {
                confidence = Math.min(24, total * 2);
                arrowBearing = displayedBearing = -1f;
            } else {
                double sx = 0, sy = 0, sw = 0;
                for (int offset = -2; offset <= 2; offset++) {
                    int i = (bestIndex + offset + SECTORS) % SECTORS;
                    if (counts[i] == 0) continue;
                    double w = Math.exp((means[i] - means[bestIndex]) / 2.6) * Math.min(4, counts[i]);
                    double angle = Math.toRadians((i + .5) * 360.0 / SECTORS);
                    sx += Math.cos(angle) * w; sy += Math.sin(angle) * w; sw += w;
                }
                float rawBearing = sw <= 0 ? (bestIndex + .5f) * 360f / SECTORS : normalize360((float)Math.toDegrees(Math.atan2(sy, sx)));
                arrowBearing = rawBearing;
                displayedBearing = displayedBearing < 0 ? rawBearing : smoothAngle(displayedBearing, rawBearing, .22f);
                double contrast = secondScore < -900 ? 0 : bestScore - secondScore;
                int sensorBonus = compassAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ? 10 : compassAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ? 2 : -8;
                int motionPenalty = motion > 2.5f ? 12 : motion > 1.2f ? 5 : 0;
                confidence = (int)Math.round(covered * 1.3 + Math.min(28, total * .9) + Math.min(28, Math.max(0, contrast) * 5.5) + sensorBonus - motionPenalty);
                confidence = Math.max(8, Math.min(93, confidence));
                angleError = Math.max(8f, Math.min(95f, 105f - confidence + (36 - covered) * 1.4f));
            }
            float ref = hyperPrefs.getFloat("reference_rssi", -59f);
            float n = hyperPrefs.getFloat("path_loss", 2.2f);
            distance = (float)Math.pow(10.0, (ref - filtered) / (10.0 * Math.max(1.2f, n)));
            distance = Math.max(.15f, Math.min(60f, distance));
            float sigma = Math.max(2f, sampleStd(rawWindow));
            float relative = (float)(Math.log(10) / (10.0 * Math.max(1.2f, n)) * sigma);
            distanceError = Math.max(.25f, distance * (relative + (100 - confidence) / 130f));
            distanceError = Math.min(Math.max(.4f, distance * 1.8f), distanceError);
        }

        private String trendLabel() {
            if (trendWindow.size() < 3) return "stabilisation";
            SignalPoint newest = trendWindow.get(trendWindow.size() - 1);
            SignalPoint old = trendWindow.get(0);
            for (SignalPoint p : trendWindow) if (newest.time - p.time >= 3000) { old = p; break; }
            float delta = newest.value - old.value;
            if (delta >= 3f) return "tu te rapproches";
            if (delta <= -3f) return "tu t'éloignes";
            return "signal stable";
        }

        private int coveragePercent() {
            int n = 0; for (int c : counts) if (c > 0) n++;
            return Math.round(n * 100f / SECTORS);
        }

        private void tickHaptics(long now) {
            if (!hyperPrefs.getBoolean("haptics", true)) return;
            long interval;
            int amplitude;
            if (distance > 0 && distance < .7f) { interval = 120; amplitude = 255; }
            else if (distance < 1.5f) { interval = 210; amplitude = 220; }
            else if (distance < 3f) { interval = 380; amplitude = 180; }
            else if (distance < 6f) { interval = 650; amplitude = 130; }
            else { interval = 1050; amplitude = 90; }
            if (now - lastVibration >= interval) { vibrate(42, amplitude); lastVibration = now; }
        }
    }

    private final class HyperView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        String name = "Appareil";
        String trend = "recherche";
        float rssi = -100, phoneHeading, bearing = -1, angleError = 180, distance = -1, distanceError = -1;
        int best = -127, confidence, coverage;
        boolean visible, frozen;
        long resetFlash;
        final long born = System.currentTimeMillis();

        HyperView(Context c) { super(c); setBackgroundColor(BG); }

        void update(String n, float r, int b, float h, float target, int conf, float err, float dist, float distErr, int cov, String tr, boolean vis, boolean fr) {
            name = n; rssi = r; best = b; phoneHeading = h; bearing = target; confidence = conf; angleError = err; distance = dist; distanceError = distErr; coverage = cov; trend = tr; visible = vis; frozen = fr; invalidate();
        }

        void flashReset() { resetFlash = System.currentTimeMillis(); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h * .50f;
            float radius = Math.min(w, h) * .34f;
            long now = System.currentTimeMillis();
            paint.setStyle(Paint.Style.FILL); paint.setColor(BG); c.drawRect(0, 0, w, h, paint);

            paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setColor(TEXT); paint.setTextSize(dp(20));
            c.drawText(shortName(name, 26), cx, dp(34), paint);
            paint.setTextSize(dp(11)); paint.setColor(MUTED);
            c.drawText(Math.round(rssi) + " dBm · meilleur " + best + " dBm · couverture " + coverage + "%", cx, dp(55), paint);

            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            for (int i = 1; i <= 5; i++) { paint.setColor(Color.argb(52, 61, 235, 211)); c.drawCircle(cx, cy, radius * i / 5f, paint); }
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30 - 90); c.drawLine(cx, cy, cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius, paint);
            }

            if (bearing >= 0) {
                float relative = normalize180(bearing - phoneHeading);
                drawUncertainty(c, cx, cy, radius * .90f, relative, angleError);
                double a = Math.toRadians(relative - 90);
                float ex = cx + (float)Math.cos(a) * radius * .78f;
                float ey = cy + (float)Math.sin(a) * radius * .78f;
                drawArrow(c, cx, cy, ex, ey, confidence >= 65 ? GOOD : confidence >= 40 ? CYAN : WARN);
            }

            float pulse = 1f + .07f * (float)Math.sin(now / 120.0);
            int centerColor = !visible ? MUTED : rssi >= -55 ? GOOD : rssi >= -70 ? WARN : BAD;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(48, Color.red(centerColor), Color.green(centerColor), Color.blue(centerColor)));
            c.drawCircle(cx, cy, dp(36) * pulse, paint);
            paint.setColor(centerColor); c.drawCircle(cx, cy, dp(17), paint);

            paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setColor(TEXT); paint.setTextSize(dp(18));
            c.drawText(trend.toUpperCase(Locale.FRANCE), cx, cy + radius + dp(30), paint);
            paint.setTextSize(dp(12)); paint.setColor(CYAN);
            String cap = bearing < 0 ? "ANGLE EN CALIBRATION" : "CAP " + Math.round(bearing) + "° ± " + Math.round(angleError) + "°";
            c.drawText(cap, cx, cy + radius + dp(50), paint);
            paint.setColor(TEXT);
            String dist = distance < 0 ? "DISTANCE EN ATTENTE" : String.format(Locale.FRANCE, "DISTANCE %.1f m ± %.1f m", distance, distanceError);
            c.drawText(dist, cx, cy + radius + dp(69), paint);
            paint.setColor(MUTED); paint.setTextSize(dp(10));
            c.drawText("CONFIANCE " + confidence + "% · TÉLÉPHONE " + Math.round(phoneHeading) + "°" + (frozen ? " · FIGÉ" : ""), cx, cy + radius + dp(86), paint);

            if (now - resetFlash < 850) {
                float t = (now - resetFlash) / 850f;
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(5)); paint.setColor(Color.argb((int)(210 * (1 - t)), 181, 132, 247));
                c.drawCircle(cx, cy, radius * (.25f + .75f * t), paint);
            }
            postInvalidateDelayed(32);
        }

        private void drawUncertainty(Canvas c, float cx, float cy, float r, float relative, float error) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(42, 247, 190, 80));
            Path p = new Path(); p.moveTo(cx, cy);
            int steps = 28;
            for (int i = 0; i <= steps; i++) {
                float deg = relative - error + (2 * error * i / steps) - 90f;
                double a = Math.toRadians(deg);
                p.lineTo(cx + (float)Math.cos(a) * r, cy + (float)Math.sin(a) * r);
            }
            p.close(); c.drawPath(p, paint);
        }

        private void drawArrow(Canvas c, float sx, float sy, float ex, float ey, int color) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeWidth(dp(8)); paint.setColor(color);
            c.drawLine(sx, sy, ex, ey, paint);
            double a = Math.atan2(ey - sy, ex - sx); float head = dp(22);
            Path p = new Path();
            p.moveTo(ex, ey); p.lineTo(ex - (float)Math.cos(a - .55) * head, ey - (float)Math.sin(a - .55) * head);
            p.moveTo(ex, ey); p.lineTo(ex - (float)Math.cos(a + .55) * head, ey - (float)Math.sin(a + .55) * head);
            c.drawPath(p, paint); paint.setStrokeCap(Paint.Cap.BUTT);
        }
    }

    private void showCalibrationPicker() {
        invokePrivate(V4Activity.class, "startSpatialSweep");
        List<RadioDevice> devices = bluetoothDevices();
        if (devices.isEmpty()) { toast("Aucun appareil Bluetooth disponible."); return; }
        String[] labels = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) labels[i] = devices.get(i).name + " · " + devices.get(i).rssi + " dBm";
        new AlertDialog.Builder(this).setTitle("Calibration à 1 mètre").setMessage("Place l'appareil à environ 1 mètre, sans mur entre les deux.")
                .setItems(labels, (d, which) -> runCalibration(devices.get(which).id, devices.get(which).name)).setNegativeButton("Annuler", null).show();
    }

    private void runCalibration(String id, String name) {
        Dialog dialog = new Dialog(this);
        LinearLayout root = column(); root.setPadding(dp(22), dp(26), dp(22), dp(26)); root.setBackgroundColor(BG);
        TextView title = text("Calibration : " + name, 22, TEXT, true); title.setGravity(Gravity.CENTER); root.addView(title); root.addView(space(14));
        TextView state = text("Reste immobile. Collecte pendant environ 10 secondes…", 14, MUTED, false); state.setGravity(Gravity.CENTER); root.addView(state); root.addView(space(14));
        TextView value = text("— dBm", 34, CYAN, true); value.setGravity(Gravity.CENTER); root.addView(value); root.addView(space(18));
        TextView cancel = action("ANNULER", PANEL_2, TEXT); root.addView(cancel);
        dialog.setContentView(root); dialog.show();
        Window w = dialog.getWindow(); if (w != null) { w.setBackgroundDrawable(rounded(BG, 0)); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
        List<Integer> samples = new ArrayList<>(); long start = System.currentTimeMillis(); final long[] last = {0};
        Runnable collect = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                long now = System.currentTimeMillis();
                if (now - last[0] > 1300) { invokePrivate(V4Activity.class, "startSpatialSweep"); last[0] = now; }
                RadioDevice d = findDevice(id);
                if (d != null && now - d.lastSeen < 6000 && d.rssi > -120) samples.add(d.rssi);
                int left = Math.max(0, 10 - (int)((now - start) / 1000));
                if (!samples.isEmpty()) value.setText(Math.round(robustMean(samples)) + " dBm");
                state.setText("Mesures : " + samples.size() + " · encore " + left + " s");
                if (now - start >= 10000) {
                    if (samples.size() < 4) { state.setText("Pas assez de mesures. Rapproche l'appareil et recommence."); return; }
                    float ref = robustMean(samples);
                    hyperPrefs.edit().putFloat("reference_rssi", ref).apply();
                    state.setText("Calibration enregistrée : " + Math.round(ref) + " dBm à 1 mètre.");
                    value.setText("TERMINÉ");
                    hyperUi.postDelayed(dialog::dismiss, 1400);
                    return;
                }
                hyperUi.postDelayed(this, 350);
            }
        };
        cancel.setOnClickListener(v -> dialog.dismiss());
        hyperUi.post(collect);
    }

    private void showPrecisionSettings() {
        String[] labels = {"Pièce ouverte · 1,7", "Intérieur normal · 2,2", "Plusieurs murs · 2,7", "Environnement dense · 3,2"};
        float[] values = {1.7f, 2.2f, 2.7f, 3.2f};
        float current = hyperPrefs.getFloat("path_loss", 2.2f);
        int selected = 1; for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - current) < .05f) selected = i;
        boolean haptics = hyperPrefs.getBoolean("haptics", true);
        new AlertDialog.Builder(this).setTitle("Réglages de précision")
                .setSingleChoiceItems(labels, selected, (d, which) -> { hyperPrefs.edit().putFloat("path_loss", values[which]).apply(); d.dismiss(); toast("Modèle enregistré : " + labels[which]); })
                .setPositiveButton(haptics ? "Désactiver vibrations" : "Activer vibrations", (d, w) -> hyperPrefs.edit().putBoolean("haptics", !haptics).apply())
                .setNeutralButton("Réinitialiser calibration", (d, w) -> hyperPrefs.edit().putFloat("reference_rssi", -59f).putFloat("path_loss", 2.2f).apply())
                .setNegativeButton("Fermer", null).show();
    }

    private void showSensorDiagnostic() {
        String message = "Cap actuel : " + Math.round(heading) + "°\nPrécision boussole : " + sensorQualityLabel() + "\nRotation gyroscope : " + String.format(Locale.FRANCE, "%.2f rad/s", gyroZ) + "\nMouvement : " + String.format(Locale.FRANCE, "%.2f m/s²", motion) +
                "\n\nPour améliorer la flèche : éloigne le téléphone des aimants et objets métalliques, fais un mouvement en forme de huit, puis recommence un balayage 360° lent.";
        new AlertDialog.Builder(this).setTitle("Diagnostic du cap").setMessage(message).setPositiveButton("Compris", null).show();
    }

    private void registerHyperSensors() {
        if (hyperSensors == null) return;
        if (rotationSensor != null) hyperSensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        if (gyroSensor != null) hyperSensors.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        if (accelerometer != null) hyperSensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    private void unregisterHyperSensors() { if (hyperSensors != null) hyperSensors.unregisterListener(this); }

    @Override public void onSensorChanged(SensorEvent event) {
        super.onSensorChanged(event);
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] matrix = new float[9]; float[] orientation = new float[3];
            SensorManager.getRotationMatrixFromVector(matrix, event.values); SensorManager.getOrientation(matrix, orientation);
            float raw = normalize360((float)Math.toDegrees(orientation[0]));
            heading = headingReady ? smoothAngle(heading, raw, .18f) : raw;
            headingReady = true;
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && event.values.length >= 3) {
            gyroZ = event.values[2];
        } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && event.values.length >= 3) {
            float m = (float)Math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]);
            motion = motion * .75f + m * .25f;
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        super.onAccuracyChanged(sensor, accuracy);
        if (sensor != null && sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) compassAccuracy = accuracy;
    }

    private List<RadioDevice> bluetoothDevices() {
        List<RadioDevice> out = new ArrayList<>(); Object raw = privateValue(V4Activity.class, "devices");
        if (!(raw instanceof Map)) return out;
        for (Object value : ((Map<?, ?>)raw).values()) {
            RadioDevice d = copyDevice(value);
            if (d != null && (d.id.startsWith("ble:") || d.source.toLowerCase(Locale.ROOT).contains("bluetooth"))) out.add(d);
        }
        out.sort(Comparator.comparingLong((RadioDevice d) -> d.lastSeen).reversed().thenComparingInt(d -> -d.rssi));
        return out;
    }

    private RadioDevice findDevice(String id) {
        Object raw = privateValue(V4Activity.class, "devices");
        if (!(raw instanceof Map)) return null;
        return copyDevice(((Map<?, ?>)raw).get(id));
    }

    private RadioDevice copyDevice(Object value) {
        if (value == null) return null;
        try {
            RadioDevice d = new RadioDevice();
            d.id = String.valueOf(readField(value, "id"));
            d.name = String.valueOf(readField(value, "name"));
            d.source = String.valueOf(readField(value, "source"));
            Object r = readField(value, "rssi"); Object t = readField(value, "lastSeen");
            d.rssi = r instanceof Number ? ((Number)r).intValue() : -100;
            d.lastSeen = t instanceof Number ? ((Number)t).longValue() : 0;
            return d;
        } catch (Exception e) { return null; }
    }

    private float robustMean(List<Integer> values) {
        if (values.isEmpty()) return -100f;
        List<Integer> sorted = new ArrayList<>(values); Collections.sort(sorted);
        int trim = sorted.size() >= 7 ? 1 : 0;
        float sum = 0; for (int i = trim; i < sorted.size() - trim; i++) sum += sorted.get(i);
        return sum / Math.max(1, sorted.size() - 2 * trim);
    }

    private float sampleStd(List<Integer> values) {
        if (values.size() < 2) return 5f;
        float mean = 0; for (int v : values) mean += v; mean /= values.size();
        float variance = 0; for (int v : values) variance += (v - mean) * (v - mean); variance /= values.size() - 1;
        return (float)Math.sqrt(Math.max(0, variance));
    }

    private String relativeDirection(float bearing, float current) {
        float d = normalize180(bearing - current), a = Math.abs(d);
        if (a <= 12) return "tout droit";
        if (a <= 35) return d > 0 ? "un peu à droite" : "un peu à gauche";
        if (a <= 80) return d > 0 ? "à droite" : "à gauche";
        if (a <= 135) return d > 0 ? "fortement à droite" : "fortement à gauche";
        return "derrière toi";
    }

    private float smoothAngle(float current, float target, float factor) {
        return normalize360(current + normalize180(target - current) * factor);
    }

    private float normalize360(float v) { v %= 360f; return v < 0 ? v + 360f : v; }
    private float normalize180(float v) { v = normalize360(v); return v > 180f ? v - 360f : v; }

    private String sensorQualityLabel() {
        if (!headingReady) return "INDISPONIBLE";
        if (compassAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH) return "EXCELLENTE";
        if (compassAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return "BONNE";
        if (compassAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW) return "FAIBLE";
        return "À CALIBRER";
    }

    private int sensorQualityColor() {
        if (compassAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH) return GOOD;
        if (compassAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return CYAN;
        return WARN;
    }

    private void activateHyperNav() {
        LinearLayout nav = privateField(V4Activity.class, "nav", LinearLayout.class); if (nav == null) return;
        for (int i = 0; i < nav.getChildCount(); i++) if (nav.getChildAt(i) instanceof TextView) {
            TextView t = (TextView)nav.getChildAt(i); boolean active = i == 5;
            t.setTextColor(active ? Color.rgb(2, 25, 22) : MUTED); t.setBackground(active ? rounded(CYAN, 17) : rounded(Color.TRANSPARENT, 17));
        }
    }

    private void feature(LinearLayout root, String title, String subtitle, String info, int color, Runnable action) {
        LinearLayout card = column(); card.setPadding(dp(15), dp(13), dp(12), dp(13)); card.setBackground(rounded(PANEL, 18));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(title, 14, color, true); top.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView help = text("i", 14, color, true); help.setGravity(Gravity.CENTER); GradientDrawable helpBg = rounded(Color.TRANSPARENT, 14); helpBg.setStroke(dp(1), color); help.setBackground(helpBg);
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(title).setMessage(info).setPositiveButton("Compris", null).show());
        top.addView(help, new LinearLayout.LayoutParams(dp(30), dp(30))); card.addView(top);
        TextView sub = text(subtitle, 11.5f, MUTED, false); sub.setPadding(0, dp(4), dp(36), 0); card.addView(sub);
        card.setOnClickListener(v -> action.run()); root.addView(card); root.addView(space(8));
    }

    private void section(LinearLayout root, String label) { root.addView(space(17)); TextView t = text(label, 11, MUTED, true); t.setLetterSpacing(.13f); root.addView(t); root.addView(space(7)); }
    private TextView metric(String label, String value, int color) { TextView v = text(label + "\n" + value, 9.2f, color, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(4), dp(10), dp(4), dp(10)); v.setBackground(rounded(PANEL, 14)); return v; }
    private TextView action(String label, int background, int foreground) { TextView v = text(label, 12, foreground, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(9), dp(13), dp(9), dp(13)); v.setBackground(rounded(background, 15)); return v; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }
    private TextView text(String value, float size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(0, 1.12f); if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); return t; }
    private GradientDrawable rounded(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View spaceW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private String age(long ms) { if (ms < 60000) return Math.max(1, ms / 1000) + " s"; if (ms < 3600000) return ms / 60000 + " min"; return ms / 3600000 + " h"; }
    private String shortName(String s, int max) { if (s == null) return "Appareil"; return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }

    private Object privateValue(Class<?> owner, String name) { try { Field f = owner.getDeclaredField(name); f.setAccessible(true); return f.get(this); } catch (Exception e) { return null; } }
    private <T> T privateField(Class<?> owner, String name, Class<T> type) { Object v = privateValue(owner, name); return type.isInstance(v) ? type.cast(v) : null; }
    private Object readField(Object target, String name) throws Exception { Class<?> t = target.getClass(); while (t != null) { try { Field f = t.getDeclaredField(name); f.setAccessible(true); return f.get(target); } catch (NoSuchFieldException e) { t = t.getSuperclass(); } } throw new NoSuchFieldException(name); }
    private void invokePrivate(Class<?> owner, String name) { try { Method m = owner.getDeclaredMethod(name); m.setAccessible(true); m.invoke(this); } catch (Exception e) { toast("Fonction momentanément indisponible."); } }
    private void replaceExact(View v, String from, String to) { if (v instanceof TextView && from.contentEquals(((TextView)v).getText())) ((TextView)v).setText(to); if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) replaceExact(((ViewGroup)v).getChildAt(i), from, to); }

    private void vibrate(long duration, int amplitude) {
        Vibrator v = (Vibrator)getSystemService(VIBRATOR_SERVICE); if (v == null || !v.hasVibrator()) return;
        try { if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(duration, Math.max(1, Math.min(255, amplitude)))); else v.vibrate(duration); } catch (Exception ignored) { }
    }
    private void cancelVibration() { Vibrator v = (Vibrator)getSystemService(VIBRATOR_SERVICE); if (v != null) try { v.cancel(); } catch (Exception ignored) { } }

    private static final class RadioDevice { String id = "", name = "Appareil", source = "Bluetooth"; int rssi = -100; long lastSeen; }
    private static final class SignalPoint { final long time; final float value; SignalPoint(long t, float v) { time = t; value = v; } }
    private static final class Kalman1D {
        private boolean ready; private float estimate; private float error = 10f;
        void reset() { ready = false; estimate = 0; error = 10f; }
        float update(float measurement, float noise) {
            if (!ready) { ready = true; estimate = measurement; error = Math.max(2f, noise); return estimate; }
            error += .7f;
            float r = Math.max(2f, noise * noise);
            float gain = error / (error + r);
            estimate = estimate + gain * (measurement - estimate);
            error = (1 - gain) * error;
            return estimate;
        }
    }
}