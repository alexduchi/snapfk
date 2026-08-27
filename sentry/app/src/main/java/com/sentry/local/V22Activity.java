package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.ConsumerIrManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.wifi.aware.WifiAwareManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sentry v22 Reality Lab.
 *
 * Phone-only experiments that convert magnetic field, vibration, acoustic echoes,
 * optical camera data, light, pressure and dead-reckoned walking into visual clues.
 * Results are intentionally labelled as estimates, not x-ray vision or guaranteed
 * identification of external electronics.
 */
public class V22Activity extends V21Activity {
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
    private static final int REQ_AUDIO = 2201;
    private static final int REQ_CAMERA = 2202;

    private final Handler realityUi = new Handler(Looper.getMainLooper());
    private SharedPreferences realityPrefs;
    private SensorManager realitySensors;
    private Sensor magneticSensor;
    private Sensor linearAccelerationSensor;
    private Sensor pressureSensor;
    private Sensor lightSensor;
    private Sensor rotationSensor;
    private Sensor stepDetector;

    private float magneticX;
    private float magneticY;
    private float magneticZ;
    private float magneticMagnitude;
    private float accelerationMagnitude;
    private float pressure;
    private float light;
    private float heading;
    private int headingAccuracy;
    private boolean headingReady;

    private DirectionalSession activeDirectional;
    private VibrationSession activeVibration;
    private RealityMapSession activeMap;
    private String pendingPermissionAction = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        realityPrefs = getSharedPreferences("sentry_v22", MODE_PRIVATE);
        if (!realityPrefs.contains("step_length")) realityPrefs.edit().putFloat("step_length", .72f).apply();
        realitySensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (realitySensors != null) {
            magneticSensor = realitySensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            linearAccelerationSensor = realitySensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            pressureSensor = realitySensors.getDefaultSensor(Sensor.TYPE_PRESSURE);
            lightSensor = realitySensors.getDefaultSensor(Sensor.TYPE_LIGHT);
            rotationSensor = realitySensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            stepDetector = realitySensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        }
        realityUi.postDelayed(navLoop, 280);
        realityUi.postDelayed(this::showRealityHub, 650);
    }

    @Override protected void onResume() {
        super.onResume();
        registerRealitySensors();
    }

    @Override protected void onPause() {
        unregisterRealitySensors();
        super.onPause();
    }

    @Override protected void onDestroy() {
        realityUi.removeCallbacks(navLoop);
        stopSessions();
        unregisterRealitySensors();
        super.onDestroy();
    }

    private final Runnable navLoop = new Runnable() {
        @Override public void run() {
            LinearLayout nav = privateField(V4Activity.class, "nav", LinearLayout.class);
            if (nav != null && nav.getChildCount() >= 6) {
                View last = nav.getChildAt(5);
                if (last instanceof TextView) {
                    ((TextView) last).setText("Réalité");
                    last.setContentDescription("Ouvrir Reality Lab");
                    last.setOnClickListener(v -> showRealityHub());
                }
            }
            replaceExact(getWindow().getDecorView(), "S21", "S22");
            realityUi.postDelayed(this, 720);
        }
    };

    private void showRealityHub() {
        FrameLayout content = privateField(V4Activity.class, "content", FrameLayout.class);
        TextView title = privateField(V4Activity.class, "title", TextView.class);
        TextView subtitle = privateField(V4Activity.class, "subtitle", TextView.class);
        if (content == null) return;
        if (title != null) title.setText("REALITY LAB");
        if (subtitle != null) subtitle.setText("Magnétique · Sonar · Vibrations · Optique");
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(14), dp(8), dp(14), dp(30));
        root.addView(hero());
        root.addView(space(10));
        root.addView(statusStrip());

        section(root, "VOIR LES PHÉNOMÈNES INVISIBLES");
        feature(root, "FUSION RÉALITÉ 360°", "Flèche vers le secteur où plusieurs capteurs réagissent ensemble", "Tu tournes lentement sur place. Sentry mémorise, pour chaque direction, le champ magnétique, les vibrations, la lumière et le niveau acoustique disponible. La flèche vise le meilleur secteur statistique, avec une marge d'incertitude.", CYAN, () -> showDirectionalLab(DirectionalSession.MODE_FUSION));
        feature(root, "RADAR MAGNÉTIQUE", "Repérer aimants, moteurs, haut-parleurs, câbles et métal proche", "Le magnétomètre mesure le champ local. Un pic peut venir d'un aimant, d'un moteur, d'un haut-parleur, d'une structure métallique ou simplement du téléphone lui-même. Le balayage 360° aide à voir où le champ augmente.", VIOLET, () -> showDirectionalLab(DirectionalSession.MODE_MAGNETIC));
        feature(root, "VISION DES VIBRATIONS", "Transformer le téléphone en mini-sismographe", "Pose le téléphone sur une surface ou tiens-le près d'une machine. Sentry mesure l'intensité et cherche une fréquence dominante entre 1 et 50 Hz pour distinguer vibration stable, impulsions et mouvement irrégulier.", BLUE, this::showVibrationLab);
        feature(root, "SONAR EXPÉRIMENTAL", "Émettre un chirp aigu puis mesurer les échos", "Le haut-parleur émet un bref son aigu et le microphone enregistre le retour. La distance est une approximation très dépendante du modèle de téléphone, de sa latence, de la pièce et des réflexions.", WARN, this::requestEchoLab);
        feature(root, "SCANNER OPTIQUE", "Caméra avec détection de point lumineux, scintillement et bandes", "Analyse localement la luminosité des images. Il peut révéler certaines LED, télécommandes infrarouges visibles par le capteur, écrans et éclairages PWM. Beaucoup de caméras filtrent l'infrarouge, donc le résultat dépend du téléphone.", GOOD, this::requestOpticalScanner);

        section(root, "CARTOGRAPHIE PHYSIQUE");
        feature(root, "CARTE DE RÉALITÉ", "Tracer un chemin approximatif et colorer les anomalies", "Le détecteur de pas et la boussole estiment ton déplacement relatif. Chaque point mémorise champ magnétique, vibration, lumière et pression. La dérive augmente avec le temps : ce n'est pas une position GPS intérieure.", CYAN, this::showRealityMap);
        feature(root, "HYPERTRACK BLUETOOTH", "Flèche 2D et distance estimée vers un appareil Bluetooth", "Ouvre le suivi v21 avec balayage 360°, filtre de Kalman, calibration à un mètre et marge d'erreur.", BLUE, () -> invokePrivate(V21Activity.class, "showPicker"));
        feature(root, "CARTE DE SIGNAL", "Mémoriser les zones où un signal est fort ou faible", "Permet de comparer plusieurs endroits au lieu de croire une seule mesure instantanée.", VIOLET, () -> invokePrivate(V14Activity.class, "showWifiHeatmap"));

        section(root, "INTERACTIONS LÉGITIMES AVEC L'ÉLECTRONIQUE");
        feature(root, "CAPACITÉS DU TÉLÉPHONE", "IR, NFC, USB, Wi-Fi Aware et publicité Bluetooth", "Vérifie quels moyens physiques sont réellement disponibles. Sentry n'exploite aucune faille et ne prend pas le contrôle d'appareils sans protocole autorisé.", WARN, this::showHardwareCapabilities);
        feature(root, "CENTRE DE COMMANDEMENT", "Protection réseau, audits, coffre et incidents", "Ouvre l'ensemble des fonctions défensives de Sentry v20.", MUTED, () -> invokePrivate(V20Activity.class, "showCommandCenter"));

        root.addView(space(14));
        root.addView(text("Reality Lab mesure des effets physiques réels, mais un pic ne prouve pas l'identité d'un appareil. Les modules affichent une confiance et évitent les promesses de vision à travers les murs.", 11, MUTED, false));
        scroll.addView(root);
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activateRealityNav();
    }

    private View hero() {
        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(rounded(PANEL, 22));
        TextView small = text("SENTRY V22", 11, CYAN, true);
        small.setLetterSpacing(.16f);
        card.addView(small);
        card.addView(space(5));
        card.addView(text("Reality Lab", 30, TEXT, true));
        card.addView(space(5));
        card.addView(text("Le téléphone devient un laboratoire : champ magnétique, échos acoustiques, vibrations, lumière, caméra et cartographie par les pas.", 13, MUTED, false));
        return card;
    }

    private View statusStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric("CHAMP", magneticMagnitude > 0 ? Math.round(magneticMagnitude) + " µT" : "—", magneticMagnitude > 90 ? WARN : CYAN), weight());
        row.addView(spaceW(7));
        row.addView(metric("VIBRATION", String.format(Locale.FRANCE, "%.2f", accelerationMagnitude), accelerationMagnitude > 2 ? WARN : GOOD), weight());
        row.addView(spaceW(7));
        row.addView(metric("CAP", headingReady ? Math.round(heading) + "°" : "—", headingReady ? BLUE : MUTED), weight());
        return row;
    }

    private void showDirectionalLab(int mode) {
        stopSessions();
        if (mode == DirectionalSession.MODE_FUSION && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = "fusion";
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = column();
        root.setPadding(dp(13), dp(15), dp(13), dp(13));
        root.setBackgroundColor(BG);
        TextView title = text(mode == DirectionalSession.MODE_MAGNETIC ? "RADAR MAGNÉTIQUE" : "FUSION RÉALITÉ 360°", 20, TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView subtitle = text("Tourne lentement sur place, téléphone tenu devant toi", 11, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        DirectionView view = new DirectionView(this);
        root.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView status = text("Calibration des capteurs…", 13, TEXT, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(7), dp(8), dp(7));
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        TextView reset = action("RECALIBRER", PANEL_2, TEXT);
        TextView close = action("FERMER", CYAN, Color.rgb(2, 24, 22));
        buttons.addView(reset, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        buttons.addView(spaceW(7));
        buttons.addView(close, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(buttons);

        dialog.setContentView(root);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(rounded(BG, 0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        DirectionalSession session = new DirectionalSession(mode, dialog, view, status);
        activeDirectional = session;
        reset.setOnClickListener(v -> session.reset());
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> session.stop());
        session.start();
    }

    private final class DirectionalSession {
        static final int MODE_MAGNETIC = 1;
        static final int MODE_FUSION = 2;
        static final int SECTORS = 36;

        final int mode;
        final Dialog dialog;
        final DirectionView view;
        final TextView status;
        final double[] sums = new double[SECTORS];
        final double[] squares = new double[SECTORS];
        final int[] counts = new int[SECTORS];
        final AudioLevelMonitor audio = new AudioLevelMonitor();
        boolean running = true;
        long started;
        float magneticBaseline;
        float vibrationBaseline;
        float lightBaseline;
        float bestBearing = -1;
        float displayedBearing = -1;
        float uncertainty = 180;
        int confidence;
        float currentScore;

        DirectionalSession(int mode, Dialog dialog, DirectionView view, TextView status) {
            this.mode = mode; this.dialog = dialog; this.view = view; this.status = status;
        }

        void start() {
            reset();
            if (mode == MODE_FUSION) audio.start();
            realityUi.post(tick);
        }

        void reset() {
            Arrays.fill(sums, 0); Arrays.fill(squares, 0); Arrays.fill(counts, 0);
            started = System.currentTimeMillis();
            magneticBaseline = magneticMagnitude;
            vibrationBaseline = accelerationMagnitude;
            lightBaseline = light;
            bestBearing = displayedBearing = -1;
            confidence = 0;
            uncertainty = 180;
            view.flashReset();
        }

        void stop() {
            running = false;
            realityUi.removeCallbacks(tick);
            audio.stop();
            if (activeDirectional == this) activeDirectional = null;
        }

        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!running || !dialog.isShowing()) return;
                long age = System.currentTimeMillis() - started;
                if (age < 1800) {
                    magneticBaseline = blend(magneticBaseline, magneticMagnitude, .08f);
                    vibrationBaseline = blend(vibrationBaseline, accelerationMagnitude, .08f);
                    lightBaseline = blend(lightBaseline, light, .08f);
                }
                float magneticScore = clamp(Math.abs(magneticMagnitude - magneticBaseline) / 35f, 0, 1.5f);
                float vibrationScore = clamp(Math.abs(accelerationMagnitude - vibrationBaseline) / 2.5f, 0, 1.5f);
                float lightScore = lightBaseline <= 1 ? 0 : clamp(Math.abs(light - lightBaseline) / Math.max(20f, lightBaseline * .7f), 0, 1.2f);
                float acousticScore = clamp(audio.rms / 4500f, 0, 1.3f);
                currentScore = mode == MODE_MAGNETIC ? magneticScore : magneticScore * .44f + vibrationScore * .25f + lightScore * .12f + acousticScore * .19f;

                if (headingReady && age > 1200) addSample(heading, currentScore);
                estimateDirection();
                int coverage = coveragePercent();
                String direction = displayedBearing < 0 ? "direction en calibration" : relativeDirection(displayedBearing, heading);
                status.setText(direction + " · confiance " + confidence + "% · zone ±" + Math.round(uncertainty) + "°\n" +
                        "champ " + Math.round(magneticMagnitude) + " µT · vibration " + String.format(Locale.FRANCE, "%.2f", accelerationMagnitude) +
                        (mode == MODE_FUSION ? " · son " + Math.round(audio.rms) : ""));
                view.update(heading, displayedBearing, uncertainty, confidence, coverage, currentScore, mode, magneticMagnitude, accelerationMagnitude, audio.rms);
                realityUi.postDelayed(this, 120);
            }
        };

        private void addSample(float bearing, float score) {
            int index = Math.min(SECTORS - 1, Math.max(0, (int)(normalize360(bearing) / 10f)));
            sums[index] += score;
            squares[index] += score * score;
            counts[index]++;
        }

        private void estimateDirection() {
            int total = 0, covered = 0, best = -1;
            double bestValue = -1, second = -1;
            double[] avg = new double[SECTORS];
            for (int i = 0; i < SECTORS; i++) {
                total += counts[i];
                if (counts[i] == 0) continue;
                covered++;
                double mean = sums[i] / counts[i];
                double variance = Math.max(0, squares[i] / counts[i] - mean * mean);
                avg[i] = mean - Math.sqrt(variance) * .18 + Math.min(.18, counts[i] * .01);
                if (avg[i] > bestValue) { second = bestValue; bestValue = avg[i]; best = i; }
                else if (avg[i] > second) second = avg[i];
            }
            if (best < 0 || total < 15 || covered < 5) {
                bestBearing = displayedBearing = -1;
                confidence = Math.min(25, total);
                uncertainty = 180;
                return;
            }
            double sx = 0, sy = 0, sw = 0;
            for (int off = -3; off <= 3; off++) {
                int i = (best + off + SECTORS) % SECTORS;
                if (counts[i] == 0) continue;
                double weight = Math.exp((avg[i] - bestValue) / .24) * Math.min(5, counts[i]);
                double a = Math.toRadians(i * 10 + 5);
                sx += Math.cos(a) * weight; sy += Math.sin(a) * weight; sw += weight;
            }
            bestBearing = sw <= 0 ? best * 10 + 5 : normalize360((float)Math.toDegrees(Math.atan2(sy, sx)));
            displayedBearing = displayedBearing < 0 ? bestBearing : smoothAngle(displayedBearing, bestBearing, .18f);
            double contrast = Math.max(0, bestValue - second);
            int sensorBonus = headingAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ? 10 : headingAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ? 2 : -8;
            confidence = (int)Math.round(covered * 1.2 + Math.min(28, total * .55) + Math.min(35, contrast * 95) + sensorBonus);
            confidence = Math.max(8, Math.min(91, confidence));
            uncertainty = Math.max(10, Math.min(120, 108 - confidence + (SECTORS - covered) * 1.5f));
        }

        private int coveragePercent() {
            int covered = 0; for (int c : counts) if (c > 0) covered++;
            return Math.round(covered * 100f / SECTORS);
        }
    }

    private final class DirectionView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float phoneHeading;
        float target = -1;
        float error = 180;
        int confidence;
        int coverage;
        float score;
        int mode;
        float magnetic;
        float vibration;
        float audio;
        long resetFlash;

        DirectionView(Context c) { super(c); setBackgroundColor(BG); }
        void flashReset() { resetFlash = System.currentTimeMillis(); }
        void update(float h, float t, float e, int conf, int cov, float score, int mode, float magnetic, float vibration, float audio) {
            phoneHeading = h; target = t; error = e; confidence = conf; coverage = cov; this.score = score; this.mode = mode; this.magnetic = magnetic; this.vibration = vibration; this.audio = audio; invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h * .48f, r = Math.min(w, h) * .34f;
            p.setStyle(Paint.Style.FILL); p.setColor(BG); c.drawRect(0, 0, w, h, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1));
            for (int i = 1; i <= 5; i++) { p.setColor(Color.argb(55, 61, 235, 211)); c.drawCircle(cx, cy, r * i / 5f, p); }
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30 - 90); c.drawLine(cx, cy, cx + (float)Math.cos(a) * r, cy + (float)Math.sin(a) * r, p);
            }
            if (target >= 0) {
                float relative = normalize180(target - phoneHeading);
                drawCone(c, cx, cy, r * .93f, relative, error);
                double a = Math.toRadians(relative - 90);
                drawArrow(c, cx, cy, cx + (float)Math.cos(a) * r * .78f, cy + (float)Math.sin(a) * r * .78f, confidence >= 60 ? GOOD : WARN);
            }
            float pulse = 1f + .08f * (float)Math.sin(System.currentTimeMillis() / 130.0);
            int center = score > .8f ? BAD : score > .35f ? WARN : CYAN;
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(45, Color.red(center), Color.green(center), Color.blue(center))); c.drawCircle(cx, cy, dp(38) * pulse, p);
            p.setColor(center); c.drawCircle(cx, cy, dp(17), p);

            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(TEXT); p.setTextSize(dp(16));
            String label = target < 0 ? "BALAYAGE 360°" : relativeDirection(target, phoneHeading).toUpperCase(Locale.FRANCE);
            c.drawText(label, cx, cy + r + dp(30), p);
            p.setTextSize(dp(11)); p.setColor(CYAN);
            c.drawText(target < 0 ? "ANGLE EN CALIBRATION" : "CAP PROBABLE " + Math.round(target) + "° ± " + Math.round(error) + "°", cx, cy + r + dp(50), p);
            p.setColor(MUTED);
            c.drawText("COUVERTURE " + coverage + "% · CONFIANCE " + confidence + "%", cx, cy + r + dp(69), p);

            p.setTextSize(dp(10)); p.setColor(TEXT);
            String metrics = mode == DirectionalSession.MODE_MAGNETIC ? Math.round(magnetic) + " µT" : Math.round(magnetic) + " µT · " + String.format(Locale.FRANCE, "%.2f", vibration) + " m/s² · son " + Math.round(audio);
            c.drawText(metrics, cx, dp(28), p);
            if (System.currentTimeMillis() - resetFlash < 900) {
                float t = (System.currentTimeMillis() - resetFlash) / 900f;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(5)); p.setColor(Color.argb((int)(220 * (1 - t)), 181, 132, 247)); c.drawCircle(cx, cy, r * (.2f + .8f * t), p);
            }
            postInvalidateDelayed(32);
        }

        private void drawCone(Canvas c, float cx, float cy, float r, float relative, float error) {
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(42, 247, 190, 80));
            Path path = new Path(); path.moveTo(cx, cy);
            for (int i = 0; i <= 32; i++) {
                float deg = relative - error + 2 * error * i / 32f - 90f;
                double a = Math.toRadians(deg); path.lineTo(cx + (float)Math.cos(a) * r, cy + (float)Math.sin(a) * r);
            }
            path.close(); c.drawPath(path, p);
        }

        private void drawArrow(Canvas c, float sx, float sy, float ex, float ey, int color) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(dp(8)); p.setColor(color); c.drawLine(sx, sy, ex, ey, p);
            double a = Math.atan2(ey - sy, ex - sx); float head = dp(22);
            Path path = new Path();
            path.moveTo(ex, ey); path.lineTo(ex - (float)Math.cos(a - .55) * head, ey - (float)Math.sin(a - .55) * head);
            path.moveTo(ex, ey); path.lineTo(ex - (float)Math.cos(a + .55) * head, ey - (float)Math.sin(a + .55) * head);
            c.drawPath(path, p); p.setStrokeCap(Paint.Cap.BUTT);
        }
    }

    private void showVibrationLab() {
        stopSessions();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = column(); root.setPadding(dp(13), dp(15), dp(13), dp(13)); root.setBackgroundColor(BG);
        TextView title = text("VISION DES VIBRATIONS", 20, TEXT, true); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView hint = text("Pose le téléphone sur une surface pour une mesure plus propre", 11, MUTED, false); hint.setGravity(Gravity.CENTER); root.addView(hint);
        VibrationView graph = new VibrationView(this); root.addView(graph, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView status = text("Collecte des vibrations…", 13, TEXT, true); status.setGravity(Gravity.CENTER); root.addView(status);
        root.addView(space(9));
        TextView close = action("FERMER", CYAN, Color.rgb(2, 24, 22)); root.addView(close);
        dialog.setContentView(root); dialog.show();
        Window w = dialog.getWindow(); if (w != null) { w.setBackgroundDrawable(rounded(BG, 0)); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
        VibrationSession session = new VibrationSession(dialog, graph, status); activeVibration = session;
        close.setOnClickListener(v -> dialog.dismiss()); dialog.setOnDismissListener(d -> session.stop()); session.start();
    }

    private final class VibrationSession {
        final Dialog dialog; final VibrationView view; final TextView status;
        final List<AccelSample> samples = new ArrayList<>(); boolean running = true;
        VibrationSession(Dialog d, VibrationView v, TextView s) { dialog = d; view = v; status = s; }
        void start() { realityUi.post(update); }
        void stop() { running = false; realityUi.removeCallbacks(update); if (activeVibration == this) activeVibration = null; }
        void add(long time, float value) { samples.add(new AccelSample(time, value)); while (samples.size() > 512) samples.remove(0); }
        final Runnable update = new Runnable() {
            @Override public void run() {
                if (!running || !dialog.isShowing()) return;
                Analysis a = analyseVibration(samples);
                status.setText("RMS " + String.format(Locale.FRANCE, "%.3f m/s²", a.rms) + " · pic " + String.format(Locale.FRANCE, "%.2f m/s²", a.peak) + "\nfréquence dominante " + (a.frequency > 0 ? String.format(Locale.FRANCE, "%.1f Hz", a.frequency) : "en attente") + " · " + a.label);
                view.update(samples, a);
                realityUi.postDelayed(this, 380);
            }
        };
    }

    private Analysis analyseVibration(List<AccelSample> source) {
        if (source.size() < 40) return new Analysis(0, 0, 0, "collecte");
        int n = Math.min(256, source.size());
        List<AccelSample> s = source.subList(source.size() - n, source.size());
        double mean = 0, peak = 0; for (AccelSample x : s) mean += x.value; mean /= n;
        double rms = 0; for (AccelSample x : s) { double d = x.value - mean; rms += d * d; peak = Math.max(peak, Math.abs(d)); } rms = Math.sqrt(rms / n);
        double duration = (s.get(n - 1).time - s.get(0).time) / 1_000_000_000.0;
        if (duration <= .2) return new Analysis((float)rms, (float)peak, 0, "collecte");
        double sampleRate = (n - 1) / duration;
        double bestPower = 0, bestFreq = 0;
        for (double f = 1; f <= Math.min(50, sampleRate / 2 - 1); f += .5) {
            double re = 0, im = 0;
            for (int i = 0; i < n; i++) {
                double window = .5 - .5 * Math.cos(2 * Math.PI * i / (n - 1));
                double value = (s.get(i).value - mean) * window;
                double a = 2 * Math.PI * f * i / sampleRate;
                re += value * Math.cos(a); im -= value * Math.sin(a);
            }
            double power = re * re + im * im;
            if (power > bestPower) { bestPower = power; bestFreq = f; }
        }
        String label = rms < .03 ? "surface calme" : rms < .25 ? "vibration légère" : bestFreq > 0 && peak < rms * 5 ? "vibration périodique" : "mouvement irrégulier";
        return new Analysis((float)rms, (float)peak, (float)bestFreq, label);
    }

    private final class VibrationView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); List<AccelSample> samples = new ArrayList<>(); Analysis analysis = new Analysis(0,0,0,"collecte");
        VibrationView(Context c) { super(c); setBackgroundColor(BG); }
        void update(List<AccelSample> s, Analysis a) { samples = new ArrayList<>(s); analysis = a; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float w = getWidth(), h = getHeight(); p.setStyle(Paint.Style.FILL); p.setColor(BG); c.drawRect(0,0,w,h,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.argb(55,126,163,172)); for(int i=1;i<6;i++) c.drawLine(0,h*i/6f,w,h*i/6f,p);
            if(samples.size()>1){int n=Math.min(300,samples.size());float max=Math.max(.12f,analysis.peak);Path path=new Path();for(int i=0;i<n;i++){float x=w*i/(n-1f);float v=samples.get(samples.size()-n+i).value;float y=h/2f-v/max*h*.36f;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}p.setColor(CYAN);p.setStrokeWidth(dp(2));c.drawPath(path,p);}
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(28));p.setColor(TEXT);c.drawText(analysis.frequency>0?String.format(Locale.FRANCE,"%.1f Hz",analysis.frequency):"…",w/2,h*.24f,p);
            p.setTextSize(dp(12));p.setColor(MUTED);c.drawText(analysis.label.toUpperCase(Locale.FRANCE),w/2,h*.31f,p);postInvalidateDelayed(45);
        }
    }

    private void requestEchoLab() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = "echo";
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } else showEchoLab();
    }

    private void showEchoLab() {
        Dialog dialog = new Dialog(this);
        LinearLayout root = column(); root.setPadding(dp(18), dp(20), dp(18), dp(18)); root.setBackgroundColor(BG);
        TextView title = text("SONAR EXPÉRIMENTAL", 22, TEXT, true); title.setGravity(Gravity.CENTER); root.addView(title);
        root.addView(space(8));
        TextView status = text("Prêt. Monte le volume média et garde le téléphone immobile.", 13, MUTED, false); status.setGravity(Gravity.CENTER); root.addView(status);
        root.addView(space(16));
        TextView value = text("—", 38, CYAN, true); value.setGravity(Gravity.CENTER); root.addView(value);
        TextView quality = text("Aucune mesure", 12, MUTED, false); quality.setGravity(Gravity.CENTER); root.addView(quality);
        root.addView(space(18));
        TextView run = action("ÉMETTRE ET MESURER", WARN, Color.rgb(30,20,2)); root.addView(run); root.addView(space(8));
        TextView close = action("FERMER", PANEL_2, TEXT); root.addView(close);
        dialog.setContentView(root); dialog.show();
        Window w = dialog.getWindow(); if (w != null) { w.setBackgroundDrawable(rounded(BG,0)); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
        run.setOnClickListener(v -> {
            run.setEnabled(false); status.setText("Émission du chirp et écoute des échos…"); value.setText("MESURE");
            new Thread(() -> {
                EchoResult result = performEchoScan();
                runOnUiThread(() -> {
                    run.setEnabled(true);
                    if (result.success) {
                        value.setText(String.format(Locale.FRANCE, "%.2f m", result.distance));
                        quality.setText("marge approximative ±" + String.format(Locale.FRANCE, "%.2f m", result.error) + " · qualité " + result.quality + "%");
                        status.setText("Écho le plus net après le trajet direct. Recommence depuis plusieurs positions pour confirmer.");
                    } else {
                        value.setText("ÉCHEC"); quality.setText(result.message); status.setText("Le téléphone ou la pièce n'a pas produit un écho exploitable.");
                    }
                });
            }).start();
        });
        close.setOnClickListener(v -> dialog.dismiss());
    }

    private EchoResult performEchoScan() {
        final int sampleRate = 48000;
        final int chirpSamples = 1440;
        final int totalSamples = 24000;
        int min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord recorder = null; AudioTrack track = null;
        try {
            int source = Build.VERSION.SDK_INT >= 24 ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.MIC;
            try { recorder = new AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 3, totalSamples * 2)); }
            catch (Exception e) { recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 3, totalSamples * 2)); }
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) return new EchoResult(false,0,0,0,"microphone non initialisé");
            short[] chirp = new short[chirpSamples];
            for (int i = 0; i < chirpSamples; i++) {
                double t = i / (double)sampleRate;
                double f0 = 14500, f1 = 19500;
                double phase = 2 * Math.PI * (f0 * t + (f1 - f0) * t * t / (2 * (chirpSamples / (double)sampleRate)));
                double window = .5 - .5 * Math.cos(2 * Math.PI * i / (chirpSamples - 1));
                chirp[i] = (short)(Math.sin(phase) * window * 17000);
            }
            track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, chirp.length * 2, AudioTrack.MODE_STATIC);
            track.write(chirp, 0, chirp.length);
            short[] recorded = new short[totalSamples];
            recorder.startRecording();
            Thread.sleep(80);
            track.play();
            int offset = 0;
            while (offset < totalSamples) {
                int n = recorder.read(recorded, offset, totalSamples - offset);
                if (n <= 0) break;
                offset += n;
            }
            recorder.stop();
            if (offset < sampleRate / 5) return new EchoResult(false,0,0,0,"enregistrement trop court");

            int searchStart = sampleRate / 30;
            int searchEnd = Math.min(offset - chirpSamples - 1, sampleRate / 3);
            double[] corr = new double[Math.max(1, searchEnd - searchStart)];
            double max = 0; int direct = -1;
            for (int lag = searchStart; lag < searchEnd; lag += 2) {
                double sum = 0, energy = 1;
                for (int i = 0; i < chirpSamples; i += 3) { double x = recorded[lag + i]; sum += x * chirp[i]; energy += x * x; }
                double score = Math.abs(sum) / Math.sqrt(energy);
                corr[lag - searchStart] = score;
                if (score > max) { max = score; direct = lag; }
            }
            if (direct < 0 || max < 10000) return new EchoResult(false,0,0,0,"signal direct trop faible");
            int echoStart = direct + Math.round(sampleRate * .0025f);
            int echoEnd = Math.min(searchEnd, direct + Math.round(sampleRate * .070f));
            double echoMax = 0; int echoLag = -1; double noise = 0; int noiseN = 0;
            for (int lag = echoStart; lag < echoEnd; lag += 2) {
                double score = corr[lag - searchStart];
                noise += score; noiseN++;
                if (score > echoMax) { echoMax = score; echoLag = lag; }
            }
            noise = noiseN == 0 ? 1 : noise / noiseN;
            if (echoLag < 0 || echoMax < noise * 1.25) return new EchoResult(false,0,0,0,"aucun écho distinct");
            float distance = (echoLag - direct) / (float)sampleRate * 343f / 2f;
            int quality = (int)Math.max(5, Math.min(92, 28 + (echoMax / Math.max(1, noise) - 1) * 42));
            float error = Math.max(.18f, distance * (100 - quality) / 85f + .10f);
            return new EchoResult(true, distance, error, quality, "ok");
        } catch (Exception e) {
            return new EchoResult(false,0,0,0,e.getClass().getSimpleName());
        } finally {
            try { if (recorder != null) recorder.release(); } catch (Exception ignored) { }
            try { if (track != null) track.release(); } catch (Exception ignored) { }
        }
    }

    private void requestOpticalScanner() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = "camera";
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else startActivity(new Intent(this, OpticalScannerActivity.class));
    }

    private void showRealityMap() {
        stopSessions();
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = column(); root.setPadding(dp(12),dp(14),dp(12),dp(12)); root.setBackgroundColor(BG);
        TextView title = text("CARTE DE RÉALITÉ",20,TEXT,true); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView hint = text(stepDetector == null ? "Détecteur de pas absent : utilise AJOUTER UN POINT" : "Marche doucement ; la trajectoire est une estimation relative",11,MUTED,false); hint.setGravity(Gravity.CENTER); root.addView(hint);
        RealityMapView map = new RealityMapView(this); root.addView(map,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        TextView status=text("0 point · position approximative 0,0 m",12,TEXT,true);status.setGravity(Gravity.CENTER);root.addView(status);root.addView(space(8));
        LinearLayout buttons=new LinearLayout(this);TextView add=action("AJOUTER UN POINT",PANEL_2,TEXT);TextView reset=action("EFFACER",PANEL_2,TEXT);buttons.addView(add,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));buttons.addView(spaceW(7));buttons.addView(reset,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(buttons);root.addView(space(7));
        TextView close=action("FERMER",CYAN,Color.rgb(2,24,22));root.addView(close);
        dialog.setContentView(root);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(rounded(BG,0));w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);}
        RealityMapSession session=new RealityMapSession(dialog,map,status);activeMap=session;session.start();add.setOnClickListener(v->session.addPoint(false));reset.setOnClickListener(v->session.reset());close.setOnClickListener(v->dialog.dismiss());dialog.setOnDismissListener(d->session.stop());
    }

    private final class RealityMapSession {
        final Dialog dialog;final RealityMapView view;final TextView status;final List<MapPoint> points=new ArrayList<>();boolean running=true;float x,y;int steps;
        RealityMapSession(Dialog d,RealityMapView v,TextView s){dialog=d;view=v;status=s;}
        void start(){addPoint(false);realityUi.post(update);}
        void stop(){running=false;realityUi.removeCallbacks(update);if(activeMap==this)activeMap=null;}
        void reset(){points.clear();x=y=0;steps=0;addPoint(false);}
        void onStep(){float length=realityPrefs.getFloat("step_length",.72f);double a=Math.toRadians(heading);x+=(float)Math.sin(a)*length;y-=(float)Math.cos(a)*length;steps++;addPoint(true);}
        void addPoint(boolean automatic){float score=physicalScore();points.add(new MapPoint(x,y,score,magneticMagnitude,accelerationMagnitude,light,pressure,automatic));while(points.size()>800)points.remove(0);view.update(points,x,y);}
        final Runnable update=new Runnable(){@Override public void run(){if(!running||!dialog.isShowing())return;status.setText(points.size()+" points · "+steps+" pas · position ≈ "+String.format(Locale.FRANCE,"%.1f, %.1f m",x,y)+"\nscore physique "+Math.round(physicalScore()*100)+" %");view.update(points,x,y);realityUi.postDelayed(this,300);}};
    }

    private float physicalScore(){float mag=clamp(Math.abs(magneticMagnitude-50f)/80f,0,1);float vib=clamp(accelerationMagnitude/3f,0,1);float lux=clamp(light/5000f,0,1);return mag*.5f+vib*.35f+lux*.15f;}

    private final class RealityMapView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);List<MapPoint> points=new ArrayList<>();float x,y;
        RealityMapView(Context c){super(c);setBackgroundColor(BG);}
        void update(List<MapPoint> pts,float x,float y){points=new ArrayList<>(pts);this.x=x;this.y=y;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();p.setStyle(Paint.Style.FILL);p.setColor(BG);c.drawRect(0,0,w,h,p);float minX=0,maxX=0,minY=0,maxY=0;for(MapPoint q:points){minX=Math.min(minX,q.x);maxX=Math.max(maxX,q.x);minY=Math.min(minY,q.y);maxY=Math.max(maxY,q.y);}float span=Math.max(4f,Math.max(maxX-minX,maxY-minY)+2f);float scale=Math.min(w,h)/span;float cx=w/2f-(minX+maxX)/2f*scale,cy=h/2f-(minY+maxY)/2f*scale;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(Color.argb(45,126,163,172));for(int i=-10;i<=10;i++){c.drawLine(cx+i*scale,0,cx+i*scale,h,p);c.drawLine(0,cy+i*scale,w,cy+i*scale,p);}if(points.size()>1){Path path=new Path();for(int i=0;i<points.size();i++){MapPoint q=points.get(i);float px=cx+q.x*scale,py=cy+q.y*scale;if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}p.setColor(CYAN);p.setStrokeWidth(dp(2));c.drawPath(path,p);}p.setStyle(Paint.Style.FILL);for(MapPoint q:points){int color=q.score>.7f?BAD:q.score>.35f?WARN:GOOD;p.setColor(Color.argb(150,Color.red(color),Color.green(color),Color.blue(color)));c.drawCircle(cx+q.x*scale,cy+q.y*scale,dp(4)+q.score*dp(7),p);}p.setColor(TEXT);c.drawCircle(cx+x*scale,cy+y*scale,dp(8),p);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(10));p.setColor(MUTED);c.drawText("1 carreau ≈ 1 m",w/2,h-dp(12),p);}
    }

    private void showHardwareCapabilities() {
        ConsumerIrManager ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);boolean hasIr=ir!=null&&ir.hasIrEmitter();
        NfcAdapter nfc=NfcAdapter.getDefaultAdapter(this);boolean hasNfc=nfc!=null;
        UsbManager usb=(UsbManager)getSystemService(USB_SERVICE);Map<String,UsbDevice> devices=usb==null?Collections.emptyMap():usb.getDeviceList();
        boolean aware=false;if(Build.VERSION.SDK_INT>=26){WifiAwareManager manager=(WifiAwareManager)getSystemService(WIFI_AWARE_SERVICE);aware=manager!=null&&manager.isAvailable();}
        BluetoothAdapter bt=null;BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);if(bm!=null)bt=bm.getAdapter();boolean advertise=bt!=null&&bt.isMultipleAdvertisementSupported();
        StringBuilder b=new StringBuilder();b.append("Émetteur infrarouge : ").append(hasIr?"oui":"non").append('\n');b.append("NFC : ").append(hasNfc?"oui":"non").append('\n');b.append("Périphériques USB connectés : ").append(devices.size()).append('\n');b.append("Wi-Fi Aware direct : ").append(aware?"oui":"non").append('\n');b.append("Publicité Bluetooth LE : ").append(advertise?"oui":"non").append("\n\n");if(!devices.isEmpty()){b.append("USB détecté :\n");for(UsbDevice d:devices.values())b.append("• ").append(d.getDeviceName()).append(" · vendeur ").append(d.getVendorId()).append(" · produit ").append(d.getProductId()).append('\n');}b.append("\nCes interfaces nécessitent un protocole compatible ou ton autorisation explicite. Elles ne donnent pas un contrôle universel des appareils voisins.");
        new AlertDialog.Builder(this).setTitle("Capacités physiques du téléphone").setMessage(b.toString()).setPositiveButton("Réglages NFC",(d,w)->openSettings(Settings.ACTION_NFC_SETTINGS)).setNeutralButton("Réglages Bluetooth",(d,w)->openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)).setNegativeButton("Fermer",null).show();
    }

    private final class AudioLevelMonitor {
        volatile boolean running;volatile float rms;AudioRecord recorder;Thread thread;
        void start(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return;running=true;thread=new Thread(()->{int rate=16000;int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);try{recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,4096));short[] buffer=new short[1024];recorder.startRecording();while(running){int n=recorder.read(buffer,0,buffer.length);if(n>0){double sum=0;for(int i=0;i<n;i++)sum+=buffer[i]*buffer[i];float value=(float)Math.sqrt(sum/n);rms=rms*.7f+value*.3f;}}}catch(Exception ignored){}finally{try{if(recorder!=null){recorder.stop();recorder.release();}}catch(Exception ignored){}}},"SentryAudioLevel");thread.start();}
        void stop(){running=false;try{if(recorder!=null)recorder.stop();}catch(Exception ignored){}}
    }

    private void registerRealitySensors(){if(realitySensors==null)return;register(magneticSensor,SensorManager.SENSOR_DELAY_GAME);register(linearAccelerationSensor,SensorManager.SENSOR_DELAY_GAME);register(pressureSensor,SensorManager.SENSOR_DELAY_NORMAL);register(lightSensor,SensorManager.SENSOR_DELAY_NORMAL);register(rotationSensor,SensorManager.SENSOR_DELAY_GAME);register(stepDetector,SensorManager.SENSOR_DELAY_NORMAL);}
    private void register(Sensor sensor,int delay){if(sensor!=null)realitySensors.registerListener(this,sensor,delay);}
    private void unregisterRealitySensors(){if(realitySensors!=null)realitySensors.unregisterListener(this);}

    @Override public void onSensorChanged(SensorEvent event){
        super.onSensorChanged(event);
        int type=event.sensor.getType();
        if(type==Sensor.TYPE_MAGNETIC_FIELD&&event.values.length>=3){magneticX=event.values[0];magneticY=event.values[1];magneticZ=event.values[2];magneticMagnitude=(float)Math.sqrt(magneticX*magneticX+magneticY*magneticY+magneticZ*magneticZ);}
        else if(type==Sensor.TYPE_LINEAR_ACCELERATION&&event.values.length>=3){float m=(float)Math.sqrt(event.values[0]*event.values[0]+event.values[1]*event.values[1]+event.values[2]*event.values[2]);accelerationMagnitude=accelerationMagnitude*.72f+m*.28f;if(activeVibration!=null)activeVibration.add(event.timestamp,m);}
        else if(type==Sensor.TYPE_PRESSURE)pressure=event.values[0];
        else if(type==Sensor.TYPE_LIGHT)light=event.values[0];
        else if(type==Sensor.TYPE_ROTATION_VECTOR){float[] matrix=new float[9],orientation=new float[3];SensorManager.getRotationMatrixFromVector(matrix,event.values);SensorManager.getOrientation(matrix,orientation);float raw=normalize360((float)Math.toDegrees(orientation[0]));heading=headingReady?smoothAngle(heading,raw,.2f):raw;headingReady=true;}
        else if(type==Sensor.TYPE_STEP_DETECTOR&&activeMap!=null)activeMap.onStep();
    }

    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){super.onAccuracyChanged(sensor,accuracy);if(sensor!=null&&(sensor.getType()==Sensor.TYPE_ROTATION_VECTOR||sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD))headingAccuracy=accuracy;}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        boolean granted=grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED;
        if(requestCode==REQ_AUDIO){if(granted){String action=pendingPermissionAction;pendingPermissionAction="";if("fusion".equals(action))showDirectionalLab(DirectionalSession.MODE_FUSION);else showEchoLab();}else toast("Le microphone est nécessaire pour ce module.");}
        if(requestCode==REQ_CAMERA){if(granted)startActivity(new Intent(this,OpticalScannerActivity.class));else toast("La caméra est nécessaire pour le scanner optique.");}
    }

    private void stopSessions(){if(activeDirectional!=null)activeDirectional.stop();if(activeVibration!=null)activeVibration.stop();if(activeMap!=null)activeMap.stop();}
    private float blend(float a,float b,float f){return a+(b-a)*f;}
    private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private float normalize360(float v){v%=360f;return v<0?v+360f:v;}
    private float normalize180(float v){v=normalize360(v);return v>180f?v-360f:v;}
    private float smoothAngle(float current,float target,float factor){return normalize360(current+normalize180(target-current)*factor);}
    private String relativeDirection(float bearing,float current){float d=normalize180(bearing-current),a=Math.abs(d);if(a<=12)return"tout droit";if(a<=38)return d>0?"un peu à droite":"un peu à gauche";if(a<=85)return d>0?"à droite":"à gauche";if(a<=140)return d>0?"fortement à droite":"fortement à gauche";return"derrière toi";}

    private void activateRealityNav(){LinearLayout nav=privateField(V4Activity.class,"nav",LinearLayout.class);if(nav==null)return;for(int i=0;i<nav.getChildCount();i++)if(nav.getChildAt(i)instanceof TextView){TextView t=(TextView)nav.getChildAt(i);boolean active=i==5;t.setTextColor(active?Color.rgb(2,25,22):MUTED);t.setBackground(active?rounded(CYAN,17):rounded(Color.TRANSPARENT,17));}}
    private void feature(LinearLayout root,String title,String subtitle,String info,int color,Runnable action){LinearLayout card=column();card.setPadding(dp(15),dp(13),dp(12),dp(13));card.setBackground(rounded(PANEL,18));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView label=text(title,14,color,true);top.addView(label,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView help=text("i",14,color,true);help.setGravity(Gravity.CENTER);GradientDrawable hb=rounded(Color.TRANSPARENT,14);hb.setStroke(dp(1),color);help.setBackground(hb);help.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(title).setMessage(info).setPositiveButton("Compris",null).show());top.addView(help,new LinearLayout.LayoutParams(dp(30),dp(30)));card.addView(top);TextView sub=text(subtitle,11.5f,MUTED,false);sub.setPadding(0,dp(4),dp(36),0);card.addView(sub);card.setOnClickListener(v->action.run());root.addView(card);root.addView(space(8));}
    private void section(LinearLayout root,String label){root.addView(space(17));TextView t=text(label,11,MUTED,true);t.setLetterSpacing(.13f);root.addView(t);root.addView(space(7));}
    private TextView metric(String label,String value,int color){TextView v=text(label+"\n"+value,9.2f,color,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(4),dp(10),dp(4),dp(10));v.setBackground(rounded(PANEL,14));return v;}
    private TextView action(String label,int background,int foreground){TextView v=text(label,12,foreground,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(9),dp(13),dp(9),dp(13));v.setBackground(rounded(background,15));return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);}
    private TextView text(String value,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(0,1.12f);if(bold)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));return t;}
    private GradientDrawable rounded(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private View space(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private View spaceW(int w){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1));return v;}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void openSettings(String action){try{startActivity(new Intent(action));}catch(Exception e){toast("Écran système indisponible.");}}
    private Object privateValue(Class<?> owner,String name){try{Field f=owner.getDeclaredField(name);f.setAccessible(true);return f.get(this);}catch(Exception e){return null;}}
    private <T>T privateField(Class<?> owner,String name,Class<T>type){Object v=privateValue(owner,name);return type.isInstance(v)?type.cast(v):null;}
    private void invokePrivate(Class<?> owner,String name){try{Method m=owner.getDeclaredMethod(name);m.setAccessible(true);m.invoke(this);}catch(Exception e){toast("Fonction momentanément indisponible.");}}
    private void replaceExact(View v,String from,String to){if(v instanceof TextView&&from.contentEquals(((TextView)v).getText()))((TextView)v).setText(to);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)replaceExact(((ViewGroup)v).getChildAt(i),from,to);}

    private static final class AccelSample{final long time;final float value;AccelSample(long t,float v){time=t;value=v;}}
    private static final class Analysis{final float rms,peak,frequency;final String label;Analysis(float r,float p,float f,String l){rms=r;peak=p;frequency=f;label=l;}}
    private static final class EchoResult{final boolean success;final float distance,error;final int quality;final String message;EchoResult(boolean s,float d,float e,int q,String m){success=s;distance=d;error=e;quality=q;message=m;}}
    private static final class MapPoint{final float x,y,score,magnetic,vibration,light,pressure;final boolean automatic;MapPoint(float x,float y,float s,float m,float v,float l,float p,boolean a){this.x=x;this.y=y;score=s;magnetic=m;vibration=v;light=l;pressure=p;automatic=a;}}
}