package com.sentry.local;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Guided room measurement using ARCore visual-inertial tracking and Depth when available. */
public class SpatialScanActivity extends android.app.Activity implements GLSurfaceView.Renderer {
    private static final int REQ_CAMERA = 2401;
    private static final int BG = Color.rgb(2, 8, 12), PANEL = Color.rgb(8, 25, 31), TEXT = Color.rgb(239, 253, 255);
    private static final int MUTED = Color.rgb(137, 171, 178), CYAN = Color.rgb(61, 235, 211), BLUE = Color.rgb(79, 166, 255);
    private static final int GOOD = Color.rgb(83, 228, 144), WARN = Color.rgb(247, 190, 80), BAD = Color.rgb(245, 98, 124);

    private GLSurfaceView surface;
    private Session session;
    private BackgroundRenderer background;
    private boolean installRequested;
    private boolean depthSupported;
    private boolean resumed;
    private int surfaceWidth, surfaceHeight;
    private final AtomicBoolean captureRequested = new AtomicBoolean(false);
    private final List<Vec3> floorCorners = new ArrayList<>();
    private ScanStage stage = ScanStage.CALIBRATION;
    private Float floorY;
    private Float ceilingY;
    private int trackedFrames;
    private int stableFrames;
    private long lastUiUpdate;

    private TextView stepText, instructionText, trackingText, countText, captureButton, finishButton;
    private LinearLayout progressRow;

    private enum ScanStage { CALIBRATION, FLOOR, CEILING, RESULT }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
        buildUi();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        surface = new GLSurfaceView(this);
        surface.setPreserveEGLContextOnPause(true);
        surface.setEGLContextClientVersion(2);
        surface.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surface.setRenderer(this);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));

        View reticle = new ReticleView(this);
        root.addView(reticle, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = column();
        top.setPadding(dp(14), dp(12), dp(14), dp(12));
        top.setBackground(round(Color.argb(224, 4, 17, 22), 0));
        TextView title = text("SENTRY SPATIAL SCAN", 18, TEXT, true);
        TextView subtitle = text("Mesure 3D guidée · ARCore + profondeur visuelle", 11, CYAN, true);
        top.addView(title); top.addView(subtitle); top.addView(space(8));
        progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(progressRow);
        stepText = text("ÉTAPE 1/4 · CALIBRATION", 12, CYAN, true);
        instructionText = text("Déplace lentement le téléphone de gauche à droite. Montre le sol et plusieurs murs.", 13, TEXT, false);
        instructionText.setPadding(0, dp(8), 0, 0);
        trackingText = text("Initialisation AR…", 11, WARN, true);
        trackingText.setPadding(0, dp(6), 0, 0);
        top.addView(stepText); top.addView(instructionText); top.addView(trackingText);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(top, topLp);

        LinearLayout bottom = column();
        bottom.setPadding(dp(12), dp(10), dp(12), dp(16));
        bottom.setBackground(round(Color.argb(230, 4, 17, 22), 22));
        countText = text("0 coin enregistré", 12, MUTED, true);
        bottom.addView(countText);
        bottom.addView(space(7));
        LinearLayout controls = new LinearLayout(this);
        TextView undo = button("ANNULER", PANEL, TEXT);
        captureButton = button("AJOUTER CE COIN", CYAN, Color.rgb(2, 24, 22));
        finishButton = button("TERMINER LE SOL", BLUE, Color.WHITE);
        controls.addView(undo, weight()); controls.addView(spaceW(6)); controls.addView(captureButton, weight(1.45f)); controls.addView(spaceW(6)); controls.addView(finishButton, weight());
        bottom.addView(controls);
        bottom.addView(space(7));
        TextView help = text("Conseil : vise la jonction sol/mur, avance réellement dans la pièce et marque les coins dans l'ordre.", 10.5f, MUTED, false);
        bottom.addView(help);
        undo.setOnClickListener(v -> undoPoint());
        captureButton.setOnClickListener(v -> requestCapture());
        finishButton.setOnClickListener(v -> finishCurrentStage());
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bottomLp.setMargins(dp(10), 0, dp(10), dp(10));
        root.addView(bottom, bottomLp);
        setContentView(root);
        updateProgress();
    }

    @Override protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        if (!ensureSession()) return;
        try {
            session.resume();
            resumed = true;
            surface.onResume();
        } catch (CameraNotAvailableException e) {
            showFatal("Caméra indisponible", "Une autre application utilise peut-être la caméra. Ferme-la puis relance le scan.");
        }
    }

    @Override protected void onPause() {
        resumed = false;
        if (surface != null) surface.onPause();
        if (session != null) session.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (session != null) { session.close(); session = null; }
        super.onDestroy();
    }

    private boolean ensureSession() {
        if (session != null) return true;
        try {
            ArCoreApk.InstallStatus status = ArCoreApk.getInstance().requestInstall(this, !installRequested);
            if (status == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true;
                trackingText.setText("Installation des Services Google Play pour la RA…");
                return false;
            }
            session = new Session(this);
            Config config = session.getConfig();
            depthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
            if (depthSupported) config.setDepthMode(Config.DepthMode.AUTOMATIC);
            config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            session.configure(config);
            return true;
        } catch (UnavailableUserDeclinedInstallationException e) {
            showFatal("ARCore refusé", "Le scanner 3D a besoin des Services Google Play pour la RA.");
        } catch (UnavailableException e) {
            showFatal("Scanner non compatible", "ARCore n'est pas disponible sur ce téléphone ou doit être mis à jour.");
        } catch (Exception e) {
            showFatal("Initialisation impossible", e.getClass().getSimpleName() + " : " + safe(e.getMessage()));
        }
        return false;
    }

    @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        background = new BackgroundRenderer();
        background.create();
        if (session != null) session.setCameraTextureName(background.textureId);
    }

    @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int width, int height) {
        surfaceWidth = width; surfaceHeight = height;
        GLES20.glViewport(0, 0, width, height);
        updateDisplayGeometry();
    }

    @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Session current = session;
        if (current == null || !resumed || background == null) return;
        try {
            current.setCameraTextureName(background.textureId);
            updateDisplayGeometry();
            Frame frame = current.update();
            background.draw(frame);
            TrackingState state = frame.getCamera().getTrackingState();
            if (state == TrackingState.TRACKING) {
                trackedFrames++;
                stableFrames++;
                if (stage == ScanStage.CALIBRATION && stableFrames > 75) runOnUiThread(this::beginFloorStage);
            } else stableFrames = 0;
            if (captureRequested.compareAndSet(true, false)) captureAtCenter(frame);
            long now = System.currentTimeMillis();
            if (now - lastUiUpdate > 350) {
                lastUiUpdate = now;
                TrackingFailureReason reason = frame.getCamera().getTrackingFailureReason();
                runOnUiThread(() -> updateTracking(state, reason));
            }
        } catch (CameraNotAvailableException e) {
            runOnUiThread(() -> showFatal("Caméra interrompue", "Relance le scanner."));
        } catch (Exception ignored) { }
    }

    private void updateDisplayGeometry() {
        if (session == null || surfaceWidth == 0 || surfaceHeight == 0) return;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        session.setDisplayGeometry(rotation, surfaceWidth, surfaceHeight);
    }

    private void updateTracking(TrackingState state, TrackingFailureReason reason) {
        if (state == TrackingState.TRACKING) {
            trackingText.setText((depthSupported ? "PROFONDEUR ACTIVE" : "PROFONDEUR VISUELLE STANDARD") + " · suivi stable");
            trackingText.setTextColor(GOOD);
        } else if (state == TrackingState.PAUSED) {
            String message;
            switch (reason) {
                case INSUFFICIENT_LIGHT: message = "Ajoute de la lumière"; break;
                case EXCESSIVE_MOTION: message = "Ralentis le mouvement"; break;
                case INSUFFICIENT_FEATURES: message = "Vise une zone texturée et déplace-toi"; break;
                case CAMERA_UNAVAILABLE: message = "Caméra indisponible"; break;
                default: message = "Cherche des surfaces…";
            }
            trackingText.setText(message);
            trackingText.setTextColor(WARN);
        } else {
            trackingText.setText("Suivi arrêté");
            trackingText.setTextColor(BAD);
        }
    }

    private void beginFloorStage() {
        if (stage != ScanStage.CALIBRATION) return;
        stage = ScanStage.FLOOR;
        stepText.setText("ÉTAPE 2/4 · CONTOUR AU SOL");
        instructionText.setText("Vise le premier coin au niveau du sol. Appuie sur AJOUTER, puis fais tout le tour dans le même sens.");
        captureButton.setEnabled(true);
        updateProgress();
    }

    private void requestCapture() {
        if (stage == ScanStage.CALIBRATION) {
            toast("Attends que le suivi devienne stable.");
            return;
        }
        if (stage == ScanStage.RESULT) return;
        captureRequested.set(true);
        captureButton.setText("MESURE…");
    }

    private void captureAtCenter(Frame frame) {
        if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
            runOnUiThread(() -> captureFailed("Suivi insuffisant : déplace-toi plus lentement."));
            return;
        }
        List<HitResult> hits = frame.hitTest(surfaceWidth / 2f, surfaceHeight / 2f);
        HitResult best = null;
        for (HitResult hit : hits) {
            Trackable t = hit.getTrackable();
            boolean valid = t instanceof DepthPoint
                    || (t instanceof Plane && ((Plane) t).isPoseInPolygon(hit.getHitPose()))
                    || (t instanceof Point && ((Point) t).getOrientationMode() == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL);
            if (valid && hit.getDistance() > 0.15f && hit.getDistance() < 8.0f) { best = hit; break; }
        }
        if (best == null) {
            runOnUiThread(() -> captureFailed("Aucune surface fiable au centre. Vise une zone texturée près du coin."));
            return;
        }
        Pose pose = best.getHitPose();
        Vec3 point = new Vec3(pose.tx(), pose.ty(), pose.tz());
        runOnUiThread(() -> acceptPoint(point));
    }

    private void acceptPoint(Vec3 p) {
        captureButton.setText(stage == ScanStage.CEILING ? "MESURER LE PLAFOND" : "AJOUTER CE COIN");
        if (stage == ScanStage.FLOOR) {
            if (floorY == null) floorY = p.y;
            if (Math.abs(p.y - floorY) > 0.45f) {
                captureFailed("Ce point n'est pas au même niveau que le sol. Vise plus bas, à la jonction sol/mur.");
                return;
            }
            if (!floorCorners.isEmpty() && floorCorners.get(floorCorners.size() - 1).horizontalDistance(p) < 0.22f) {
                captureFailed("Ce point est trop proche du précédent. Déplace le viseur vers le coin suivant.");
                return;
            }
            floorCorners.add(new Vec3(p.x, floorY, p.z));
            floorY = medianFloorY();
            countText.setText(floorCorners.size() + (floorCorners.size() > 1 ? " coins enregistrés" : " coin enregistré"));
            instructionText.setText(floorCorners.size() < 3
                    ? "Continue vers le coin suivant, toujours dans le même sens."
                    : "Continue le contour ou appuie sur TERMINER LE SOL quand tous les coins sont marqués.");
        } else if (stage == ScanStage.CEILING) {
            float h = floorY == null ? 0f : Math.abs(p.y - floorY);
            if (h < 1.5f || h > 6.5f) {
                captureFailed("Hauteur peu plausible (" + fmt(h) + " m). Vise directement le plafond.");
                return;
            }
            ceilingY = p.y;
            showResults();
        }
        updateProgress();
    }

    private void captureFailed(String message) {
        captureButton.setText(stage == ScanStage.CEILING ? "MESURER LE PLAFOND" : "AJOUTER CE COIN");
        toast(message);
        instructionText.setText(message);
    }

    private void undoPoint() {
        if (stage == ScanStage.FLOOR && !floorCorners.isEmpty()) {
            floorCorners.remove(floorCorners.size() - 1);
            floorY = floorCorners.isEmpty() ? null : medianFloorY();
            countText.setText(floorCorners.size() + " coin(s) enregistré(s)");
            updateProgress();
        } else toast("Aucun point à annuler dans cette étape.");
    }

    private void finishCurrentStage() {
        if (stage == ScanStage.CALIBRATION) { toast("Calibration en cours."); return; }
        if (stage == ScanStage.FLOOR) {
            if (floorCorners.size() < 3) { toast("Il faut au moins 3 coins pour former une pièce."); return; }
            if (floorCorners.get(0).horizontalDistance(floorCorners.get(floorCorners.size() - 1)) < 0.25f && floorCorners.size() > 3) {
                floorCorners.remove(floorCorners.size() - 1);
            }
            stage = ScanStage.CEILING;
            stepText.setText("ÉTAPE 3/4 · HAUTEUR");
            instructionText.setText("Vise une zone plane du plafond, idéalement au-dessus de toi, puis appuie sur MESURER LE PLAFOND.");
            captureButton.setText("MESURER LE PLAFOND");
            finishButton.setText("SAISIR MANUELLEMENT");
            countText.setText("Sol validé · " + floorCorners.size() + " coins");
            updateProgress();
        } else if (stage == ScanStage.CEILING) showManualHeight();
    }

    private void showManualHeight() {
        EditText input = new EditText(this);
        input.setHint("ex. 2.50");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        int pad = dp(18); input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this).setTitle("Hauteur manuelle").setMessage("Mesure la hauteur sol-plafond en mètres.")
                .setView(input).setPositiveButton("Valider", (d, w) -> {
                    try {
                        float h = Float.parseFloat(input.getText().toString().replace(',', '.'));
                        if (h < 1.5f || h > 8f) throw new IllegalArgumentException();
                        ceilingY = (floorY == null ? 0f : floorY) + h;
                        showResults();
                    } catch (Exception e) { toast("Hauteur invalide."); }
                }).setNegativeButton("Annuler", null).show();
    }

    private void showResults() {
        stage = ScanStage.RESULT;
        float area = polygonArea();
        float perimeter = perimeter();
        float height = floorY == null || ceilingY == null ? 0f : Math.abs(ceilingY - floorY);
        float volume = area * height;
        String shape = shapeLabel();
        int confidence = confidenceScore();
        saveResult(area, perimeter, height, volume, shape, confidence);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = column(); root.setPadding(dp(18), dp(18), dp(18), dp(24)); root.setBackgroundColor(BG);
        root.addView(text("SCAN TERMINÉ", 12, GOOD, true));
        root.addView(text(shape, 28, TEXT, true));
        root.addView(space(8));
        PlanView plan = new PlanView(this, floorCorners);
        root.addView(plan, new LinearLayout.LayoutParams(-1, dp(310)));
        root.addView(space(12));
        root.addView(metric("SURFACE", fmt(area) + " m²", CYAN));
        root.addView(metric("PÉRIMÈTRE", fmt(perimeter) + " m", BLUE));
        root.addView(metric("HAUTEUR", fmt(height) + " m", GOOD));
        root.addView(metric("VOLUME", fmt(volume) + " m³", WARN));
        root.addView(metric("CONFIANCE", confidence + " / 100", confidence >= 75 ? GOOD : WARN));
        root.addView(space(8));
        root.addView(text(sideSummary(), 12, MUTED, false));
        root.addView(space(12));
        TextView again = button("NOUVEAU SCAN", CYAN, Color.rgb(2, 24, 22));
        TextView close = button("REVENIR À SENTRY", PANEL, TEXT);
        root.addView(again); root.addView(space(7)); root.addView(close);
        again.setOnClickListener(v -> { dialog.dismiss(); resetScan(); });
        close.setOnClickListener(v -> { dialog.dismiss(); finish(); });
        scroll.addView(root); dialog.setContentView(scroll); dialog.setCancelable(false); dialog.show();
        Window w = dialog.getWindow(); if (w != null) { w.setBackgroundDrawable(round(BG, 0)); w.setLayout(-1, -1); }
    }

    private void resetScan() {
        floorCorners.clear(); floorY = null; ceilingY = null; stage = ScanStage.CALIBRATION; stableFrames = 0;
        stepText.setText("ÉTAPE 1/4 · CALIBRATION");
        instructionText.setText("Déplace lentement le téléphone de gauche à droite. Montre le sol et plusieurs murs.");
        captureButton.setText("AJOUTER CE COIN"); finishButton.setText("TERMINER LE SOL"); countText.setText("0 coin enregistré");
        updateProgress();
    }

    private float polygonArea() {
        if (floorCorners.size() < 3) return 0f;
        double sum = 0;
        for (int i = 0; i < floorCorners.size(); i++) {
            Vec3 a = floorCorners.get(i), b = floorCorners.get((i + 1) % floorCorners.size());
            sum += a.x * b.z - b.x * a.z;
        }
        return (float) Math.abs(sum * 0.5);
    }

    private float perimeter() {
        float p = 0;
        for (int i = 0; i < floorCorners.size(); i++) p += floorCorners.get(i).horizontalDistance(floorCorners.get((i + 1) % floorCorners.size()));
        return p;
    }

    private String shapeLabel() {
        int n = floorCorners.size();
        if (n == 4) {
            int right = 0;
            for (int i = 0; i < 4; i++) if (Math.abs(angleAt(i) - 90f) < 18f) right++;
            if (right >= 3) return "Pièce rectangulaire";
            return "Quadrilatère irrégulier";
        }
        if (n == 6) return "Pièce en L ou hexagonale";
        if (n == 3) return "Pièce triangulaire";
        return "Pièce irrégulière · " + n + " coins";
    }

    private float angleAt(int i) {
        int n = floorCorners.size(); Vec3 a = floorCorners.get((i - 1 + n) % n), b = floorCorners.get(i), c = floorCorners.get((i + 1) % n);
        double ux = a.x - b.x, uz = a.z - b.z, vx = c.x - b.x, vz = c.z - b.z;
        double den = Math.sqrt(ux * ux + uz * uz) * Math.sqrt(vx * vx + vz * vz);
        if (den < 1e-6) return 0;
        return (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (ux * vx + uz * vz) / den))));
    }

    private int confidenceScore() {
        int score = depthSupported ? 45 : 33;
        score += Math.min(22, trackedFrames / 20);
        if (floorCorners.size() >= 4) score += 12;
        if (floorCorners.size() <= 10) score += 7;
        if (ceilingY != null) score += 9;
        if (polygonArea() > 1f) score += 5;
        return Math.min(96, score);
    }

    private String sideSummary() {
        StringBuilder b = new StringBuilder("CÔTÉS\n");
        for (int i = 0; i < floorCorners.size(); i++) b.append(i + 1).append(" → ").append(i + 2 > floorCorners.size() ? 1 : i + 2).append(" : ").append(fmt(floorCorners.get(i).horizontalDistance(floorCorners.get((i + 1) % floorCorners.size())))).append(" m\n");
        b.append("\nLa précision dépend de la lumière, des textures, des reflets et du mouvement. Ce scan n'est pas un relevé certifié.");
        return b.toString();
    }

    private void saveResult(float area, float perimeter, float height, float volume, String shape, int confidence) {
        try {
            JSONObject o = new JSONObject();
            o.put("time", System.currentTimeMillis()); o.put("shape", shape); o.put("area_m2", area); o.put("perimeter_m", perimeter);
            o.put("height_m", height); o.put("volume_m3", volume); o.put("confidence", confidence); o.put("depth_supported", depthSupported);
            JSONArray points = new JSONArray();
            for (Vec3 p : floorCorners) points.put(new JSONArray().put(p.x).put(p.y).put(p.z));
            o.put("floor_points", points);
            getSharedPreferences("sentry_spatial_scans", MODE_PRIVATE).edit().putString("latest", o.toString()).putLong("latest_time", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    private float medianFloorY() {
        List<Float> ys = new ArrayList<>(); for (Vec3 p : floorCorners) ys.add(p.y); ys.sort(Float::compare);
        return ys.get(ys.size() / 2);
    }

    private void updateProgress() {
        progressRow.removeAllViews();
        int active = stage == ScanStage.CALIBRATION ? 0 : stage == ScanStage.FLOOR ? 1 : stage == ScanStage.CEILING ? 2 : 3;
        for (int i = 0; i < 4; i++) {
            View dot = new View(this); dot.setBackground(round(i <= active ? CYAN : Color.rgb(35, 62, 68), 4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(5), 1); if (i > 0) lp.setMargins(dp(5), 0, 0, 0); progressRow.addView(dot, lp);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) onResume();
            else new AlertDialog.Builder(this).setTitle("Caméra requise").setMessage("Le scanner 3D ne peut pas fonctionner sans caméra.")
                    .setPositiveButton("Réglages", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("Fermer", (d, w) -> finish()).show();
        }
    }

    private void showFatal(String title, String message) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Fermer", (d, w) -> finish()).show();
    }

    private TextView metric(String name, String value, int color) {
        TextView v = text(name + "\n" + value, 15, color, true); v.setPadding(dp(14), dp(11), dp(14), dp(11)); v.setBackground(round(PANEL, 15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(7)); v.setLayoutParams(lp); return v;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String s, float size, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(0, 1.12f); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); return v; }
    private TextView button(String s, int bg, int fg) { TextView v = text(s, 11, fg, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(8), dp(13), dp(8), dp(13)); v.setBackground(round(bg, 14)); return v; }
    private GradientDrawable round(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout.LayoutParams weight() { return weight(1f); }
    private LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View spaceW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String fmt(float v) { return String.format(Locale.FRANCE, "%.2f", v); }
    private String safe(String s) { return s == null ? "" : s; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private static final class Vec3 {
        final float x, y, z;
        Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        float horizontalDistance(Vec3 o) { float dx = x - o.x, dz = z - o.z; return (float) Math.sqrt(dx * dx + dz * dz); }
    }

    private final class ReticleView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        ReticleView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) {
            float x = getWidth() / 2f, y = getHeight() / 2f;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setColor(stage == ScanStage.CALIBRATION ? WARN : CYAN);
            c.drawCircle(x, y, dp(22), p); c.drawLine(x - dp(32), y, x - dp(10), y, p); c.drawLine(x + dp(10), y, x + dp(32), y, p);
            c.drawLine(x, y - dp(32), x, y - dp(10), p); c.drawLine(x, y + dp(10), x, y + dp(32), p);
            postInvalidateDelayed(150);
        }
    }

    private final class PlanView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final List<Vec3> pts;
        PlanView(Context c, List<Vec3> source) { super(c); pts = new ArrayList<>(source); setBackground(round(PANEL, 20)); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); if (pts.size() < 3) return;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (Vec3 v : pts) { minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x); minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z); }
            float sx = (getWidth() - dp(54)) / Math.max(.1f, maxX - minX), sy = (getHeight() - dp(54)) / Math.max(.1f, maxZ - minZ), scale = Math.min(sx, sy);
            float ox = (getWidth() - (maxX - minX) * scale) / 2f, oy = (getHeight() - (maxZ - minZ) * scale) / 2f;
            Path path = new Path();
            for (int i = 0; i < pts.size(); i++) { float x = ox + (pts.get(i).x - minX) * scale, y = oy + (pts.get(i).z - minZ) * scale; if (i == 0) path.moveTo(x, y); else path.lineTo(x, y); }
            path.close(); p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(48, 61, 235, 211)); c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(3)); p.setColor(CYAN); c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(dp(10));
            for (int i = 0; i < pts.size(); i++) { float x = ox + (pts.get(i).x - minX) * scale, y = oy + (pts.get(i).z - minZ) * scale; p.setColor(GOOD); c.drawCircle(x, y, dp(6), p); p.setColor(TEXT); c.drawText(String.valueOf(i + 1), x, y - dp(11), p); }
        }
    }

    private static final class BackgroundRenderer {
        int textureId, program, position, texCoord, textureUniform;
        final FloatBuffer vertices = buffer(new float[]{-1,-1, 1,-1, -1,1, 1,1});
        final FloatBuffer transformed = buffer(new float[8]);
        final FloatBuffer ndc = buffer(new float[]{-1,-1, 1,-1, -1,1, 1,1});

        void create() {
            int[] t = new int[1]; GLES20.glGenTextures(1, t, 0); textureId = t[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            String vs = "attribute vec4 a_Position; attribute vec2 a_TexCoord; varying vec2 v_TexCoord; void main(){gl_Position=a_Position;v_TexCoord=a_TexCoord;}";
            String fs = "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES sTexture; varying vec2 v_TexCoord; void main(){gl_FragColor=texture2D(sTexture,v_TexCoord);}";
            program = link(vs, fs); position = GLES20.glGetAttribLocation(program, "a_Position"); texCoord = GLES20.glGetAttribLocation(program, "a_TexCoord"); textureUniform = GLES20.glGetUniformLocation(program, "sTexture");
        }

        void draw(Frame frame) {
            ndc.position(0); transformed.position(0);
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, ndc, Coordinates2d.TEXTURE_NORMALIZED, transformed);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDepthMask(false); GLES20.glUseProgram(program);
            vertices.position(0); GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices); GLES20.glEnableVertexAttribArray(position);
            transformed.position(0); GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 0, transformed); GLES20.glEnableVertexAttribArray(texCoord);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId); GLES20.glUniform1i(textureUniform, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(texCoord); GLES20.glDepthMask(true);
        }

        static FloatBuffer buffer(float[] values) { FloatBuffer b = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); b.put(values).position(0); return b; }
        static int shader(int type, String src) { int s = GLES20.glCreateShader(type); GLES20.glShaderSource(s, src); GLES20.glCompileShader(s); return s; }
        static int link(String vs, String fs) { int p = GLES20.glCreateProgram(); GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs)); GLES20.glLinkProgram(p); return p; }
    }
}
