package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sentry v10 is the phone-only command layer built above the complete v4 spatial engine.
 * It deliberately avoids pretending that Android can see people through walls or access
 * radio data that the OS does not expose. Every advanced module has a local fallback.
 */
public class V10Activity extends V4Activity implements SensorEventListener {
    private static final int REQ_CORE_PERMISSIONS = 10010;
    private static final int REQ_ENABLE_BT = 10011;
    private static final int REQ_EXPORT = 10012;

    private static final int BG = Color.rgb(2, 8, 12);
    private static final int PANEL = Color.rgb(10, 23, 30);
    private static final int PANEL_2 = Color.rgb(16, 38, 47);
    private static final int TEXT = Color.rgb(235, 252, 255);
    private static final int MUTED = Color.rgb(132, 166, 174);
    private static final int CYAN = Color.rgb(64, 235, 224);
    private static final int BLUE = Color.rgb(82, 169, 255);
    private static final int GOOD = Color.rgb(83, 228, 145);
    private static final int WARN = Color.rgb(247, 191, 83);
    private static final int BAD = Color.rgb(245, 99, 124);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences v10;
    private SensorManager sensorManager;
    private float heading;
    private float pitch;
    private float roll;
    private float pressure;
    private float light;
    private long trafficStartRx;
    private long trafficStartTx;
    private long telemetryStarted;
    private String pendingExport = "";
    private BluetoothGatt activeGatt;
    private Dialog setupDialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        v10 = getSharedPreferences("sentry_v10", MODE_PRIVATE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        trafficStartRx = TrafficStats.getTotalRxBytes();
        trafficStartTx = TrafficStats.getTotalTxBytes();
        telemetryStarted = System.currentTimeMillis();
        installCommandOrb();
        logV10("Sentry v10 Digital Twin Core démarré.");
        ui.postDelayed(() -> {
            if (!v10.getBoolean("setup_complete", false)) showSetupWizard(false);
            else autoProbe();
        }, 450);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerSensors();
    }

    @Override
    protected void onPause() {
        unregisterSensors();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (activeGatt != null) {
            try { activeGatt.close(); } catch (Exception ignored) { }
            activeGatt = null;
        }
        super.onDestroy();
    }

    private void installCommandOrb() {
        TextView orb = text("V10\nCORE", 10, Color.rgb(0, 22, 25), true);
        orb.setGravity(Gravity.CENTER);
        orb.setBackground(rounded(CYAN, 24));
        orb.setElevation(dp(14));
        orb.setOnClickListener(v -> showCommandCenter());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(66), dp(66), Gravity.END | Gravity.BOTTOM);
        lp.setMargins(0, 0, dp(14), dp(72));
        addContentView(orb, lp);
    }

    private void showSetupWizard(boolean forced) {
        if (setupDialog != null && setupDialog.isShowing()) return;
        setupDialog = new Dialog(this);
        setupDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setupDialog.setCancelable(!forced);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column(20);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.addView(text("SENTRY V10", 13, CYAN, true));
        root.addView(text("Digital Twin Core", 32, TEXT, true));
        root.addView(space(8));
        root.addView(text("Un bouton configure automatiquement tout ce qu’Android autorise. Les rares fenêtres système doivent être validées manuellement.", 15, MUTED, false));
        root.addView(space(18));

        root.addView(statusCard("Bluetooth Intelligence", bluetoothReady(), "Scan spatial, proximité, appareils associés et inspection GATT."));
        root.addView(statusCard("Wi-Fi Intelligence", wifiReady(), "Réseau, signal, DNS, passerelle, trafic et empreintes de pièces."));
        root.addView(statusCard("Spatial Sensors", sensorManager != null, "Boussole, mouvement, lumière, pression et Reality HUD."));
        root.addView(statusCard("Advanced Ranging", hasRtt() || hasUwb(), "Wi-Fi RTT ou UWB si le téléphone les possède, fallback probabiliste sinon."));
        root.addView(statusCard("Local Privacy", true, "Aucun compte, aucun cloud obligatoire, aucun matériel externe."));
        root.addView(space(14));

        TextView install = action("TOUT CONFIGURER", CYAN, Color.rgb(0, 24, 27));
        install.setOnClickListener(v -> beginOneTapSetup());
        root.addView(install);
        root.addView(space(9));
        TextView immediate = action("OUVRIR EN MODE IMMÉDIAT", PANEL_2, TEXT);
        immediate.setOnClickListener(v -> finishSetup("Mode immédiat activé. Les options manquantes restent accessibles dans V10 Core."));
        root.addView(immediate);
        root.addView(space(14));
        root.addView(text("Sentry n’invente jamais de localisation humaine. Sans matériel externe, la présence est estimée uniquement à partir des appareils et signaux réellement accessibles au téléphone.", 12, MUTED, false));
        scroll.addView(root);
        setupDialog.setContentView(scroll);
        Window w = setupDialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(rounded(BG, 0));
        }
        setupDialog.show();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void beginOneTapSetup() {
        List<String> missing = missingPermissions();
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQ_CORE_PERMISSIONS);
            return;
        }
        continueSetupAfterPermissions();
    }

    private List<String> missingPermissions() {
        List<String> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            addMissing(result, Manifest.permission.BLUETOOTH_SCAN);
            addMissing(result, Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            addMissing(result, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            addMissing(result, Manifest.permission.NEARBY_WIFI_DEVICES);
            addMissing(result, Manifest.permission.POST_NOTIFICATIONS);
        }
        return result;
    }

    private void addMissing(List<String> list, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) list.add(permission);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQ_CORE_PERMISSIONS) continueSetupAfterPermissions();
    }

    private void continueSetupAfterPermissions() {
        BluetoothAdapter adapter = bluetoothAdapter();
        if (adapter != null && !adapter.isEnabled()) {
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT);
                return;
            } catch (Exception ignored) { }
        }
        finishSetup("Configuration automatique terminée.");
    }

    private void finishSetup(String message) {
        v10.edit().putBoolean("setup_complete", true).putLong("setup_at", System.currentTimeMillis()).apply();
        if (setupDialog != null) setupDialog.dismiss();
        toast(message);
        logV10(message);
        autoProbe();
        openV4Tab(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE_BT) finishSetup("Configuration terminée. Bluetooth vérifié.");
        if (requestCode == REQ_EXPORT && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out != null) out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                toast("Rapport v10 exporté.");
            } catch (Exception e) { toast("Export impossible : " + e.getMessage()); }
        }
    }

    private void autoProbe() {
        logV10("Auto-probe : " + capabilitySummaryOneLine());
        invokeV4("startSpatialSweep");
    }

    private void showCommandCenter() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column(14);
        root.setPadding(dp(18), dp(22), dp(18), dp(26));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("10", 18, Color.rgb(0, 22, 25), true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(CYAN, 18));
        header.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(spaceW(12));
        LinearLayout names = column(2);
        names.addView(text("COMMAND CENTER", 23, TEXT, true));
        names.addView(text("Phone-only Digital Twin", 12, MUTED, false));
        header.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(header);
        root.addView(space(8));
        root.addView(metricStrip());
        root.addView(space(8));

        addModule(root, "AUTO SETUP", "Permissions et diagnostic automatique", () -> showSetupWizard(false));
        addModule(root, "CAPABILITY MATRIX", "RTT, UWB, AR, BLE, NFC, capteurs et fallbacks", this::showCapabilities);
        addModule(root, "LIVE TELEMETRY", "Batterie, capteurs, orientation et trafic", this::showTelemetry);
        addModule(root, "NETWORK X-RAY", "Wi-Fi, IP, DNS, passerelle et transport actif", this::showNetworkXray);
        addModule(root, "GATT INSPECTOR", "Services Bluetooth d’un appareil associé", this::showGattPicker);
        addModule(root, "ROOM FINGERPRINT", "Calibration et estimation locale de pièce", this::showRoomLab);
        addModule(root, "REALITY HUD", "Radar immersif avec boussole et fusion capteurs", this::showRealityHud);
        addModule(root, "LOCAL AI BRIEF", "Résumé explicable généré entièrement sur le téléphone", this::showAiBrief);
        addModule(root, "SECURITY AUDIT", "Verrouillage, chiffrement, patch et configuration", this::showSecurityAudit);
        addModule(root, "CINEMATIC TIMELINE", "Relecture des apparitions et mouvements estimés", () -> { dialog.dismiss(); openV4Tab(4); });
        addModule(root, "SPATIAL UNIVERSE", "Jumeau numérique 3D interactif", () -> { dialog.dismiss(); openV4Tab(0); });
        addModule(root, "PROXIMITY RADAR", "Signaux Bluetooth, trajectoires et proximité", () -> { dialog.dismiss(); openV4Tab(3); });
        addModule(root, "LEGACY LABS", "Accès aux moteurs Sentry précédents", this::showLegacyLabs);
        addModule(root, "EXPORT DIGITAL TWIN", "Rapport JSON local complet", this::exportV10);
        addModule(root, "SYSTEM SETTINGS", "Qualité, thème, confidentialité et profils", () -> { dialog.dismiss(); openV4Tab(5); });

        TextView close = action("FERMER", PANEL_2, TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(space(8));
        root.addView(close);
        scroll.addView(root);
        dialog.setContentView(scroll);
        Window w = dialog.getWindow();
        dialog.show();
        if (w != null) {
            w.setBackgroundDrawable(rounded(BG, 0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private View metricStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric("CORE", v10.getBoolean("setup_complete", false) ? "READY" : "SETUP", v10.getBoolean("setup_complete", false) ? GOOD : WARN), weight());
        row.addView(spaceW(7));
        row.addView(metric("RANGING", hasUwb() ? "UWB" : hasRtt() ? "RTT" : "FUSION", hasUwb() || hasRtt() ? CYAN : BLUE), weight());
        row.addView(spaceW(7));
        row.addView(metric("PRIVACY", "LOCAL", GOOD), weight());
        return row;
    }

    private void addModule(LinearLayout root, String title, String sub, Runnable action) {
        LinearLayout card = column(4);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(rounded(PANEL, 17));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = column(3);
        labels.addView(text(title, 14, TEXT, true));
        labels.addView(text(sub, 12, MUTED, false));
        line.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = text("›", 28, CYAN, false);
        line.addView(arrow);
        card.addView(line);
        card.setOnClickListener(v -> action.run());
        root.addView(card);
    }

    private void showCapabilities() {
        StringBuilder b = new StringBuilder();
        b.append("SENTRY V10 CAPABILITY MATRIX\n\n");
        line(b, "Bluetooth LE", hasFeature(PackageManager.FEATURE_BLUETOOTH_LE), "Spatial scan + RSSI");
        line(b, "Wi-Fi RTT", hasRtt(), hasRtt() ? "Ranging matériel disponible" : "Fallback RSSI + empreintes");
        line(b, "Ultra Wideband", hasUwb(), hasUwb() ? "Ranging UWB disponible" : "Fallback probabiliste");
        line(b, "AR services", packageExists("com.google.ar.core"), packageExists("com.google.ar.core") ? "Compatible avec futures vues caméra" : "Reality HUD sans caméra");
        line(b, "NFC", hasFeature(PackageManager.FEATURE_NFC), "Identification locale");
        line(b, "Baromètre", hasSensor(Sensor.TYPE_PRESSURE), "Variation d’altitude");
        line(b, "Boussole", hasSensor(Sensor.TYPE_MAGNETIC_FIELD), "Orientation Reality HUD");
        line(b, "Gyroscope", hasSensor(Sensor.TYPE_GYROSCOPE), "Stabilisation de mouvement");
        line(b, "Capteur lumière", hasSensor(Sensor.TYPE_LIGHT), "Contexte ambiant");
        b.append("\nLes fonctions absentes sont remplacées automatiquement, jamais simulées avec de fausses données.");
        showText("Capability Matrix", b.toString());
    }

    private void showTelemetry() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery == null ? 100 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = level < 0 ? -1 : Math.round(level * 100f / Math.max(1, scale));
        int temp = battery == null ? 0 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        long rx = Math.max(0, TrafficStats.getTotalRxBytes() - trafficStartRx);
        long tx = Math.max(0, TrafficStats.getTotalTxBytes() - trafficStartTx);
        long elapsed = Math.max(1, (System.currentTimeMillis() - telemetryStarted) / 1000);
        String report = "BATTERIE\n" + (pct < 0 ? "Non disponible" : pct + " %") + " · " + String.format(Locale.FRANCE, "%.1f °C", temp / 10f) +
                "\n\nORIENTATION\nCap " + Math.round(heading) + "° · Pitch " + Math.round(pitch) + "° · Roll " + Math.round(roll) + "°" +
                "\n\nENVIRONNEMENT\nPression " + (pressure == 0 ? "—" : Math.round(pressure) + " hPa") + " · Lumière " + (light == 0 ? "—" : Math.round(light) + " lx") +
                "\n\nTRAFIC DEPUIS L’OUVERTURE\nReçu " + bytes(rx) + " · Envoyé " + bytes(tx) + "\nMoyenne " + bytes((rx + tx) / elapsed) + "/s" +
                "\n\nAucune communication chiffrée n’est déchiffrée. Seuls des compteurs système sont affichés.";
        showText("Live Telemetry", report);
    }

    private void showNetworkXray() {
        StringBuilder b = new StringBuilder();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network network = cm == null ? null : cm.getActiveNetwork();
        NetworkCapabilities caps = cm == null || network == null ? null : cm.getNetworkCapabilities(network);
        LinkProperties lp = cm == null || network == null ? null : cm.getLinkProperties(network);
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
        b.append("TRANSPORT\n");
        if (caps == null) b.append("Aucun réseau actif\n");
        else {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) b.append("Wi-Fi");
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) b.append("Cellulaire");
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) b.append("Ethernet");
            else b.append("Autre");
            b.append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? " · Internet validé\n" : " · Non validé\n");
        }
        if (wi != null) {
            b.append("\nWI-FI\nSSID ").append(cleanSsid(wi.getSSID())).append("\nBSSID ").append(wi.getBSSID()).append("\nRSSI ").append(wi.getRssi()).append(" dBm");
            if (Build.VERSION.SDK_INT >= 21) b.append("\nFréquence ").append(wi.getFrequency()).append(" MHz\nLien ").append(wi.getLinkSpeed()).append(" Mbps");
        }
        if (lp != null) {
            b.append("\n\nADRESSES\n");
            for (LinkAddress a : lp.getLinkAddresses()) b.append(a).append('\n');
            b.append("\nDNS\n");
            for (java.net.InetAddress dns : lp.getDnsServers()) b.append(dns.getHostAddress()).append('\n');
            if (lp.getRoutes() != null) {
                b.append("\nROUTES\n");
                for (android.net.RouteInfo route : lp.getRoutes()) b.append(route).append('\n');
            }
        }
        b.append("\nRTT : ").append(hasRtt() ? "disponible" : "indisponible, fallback actif");
        showText("Network X-Ray", b.toString());
        logV10("Network X-Ray consulté.");
    }

    private void showGattPicker() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            beginOneTapSetup();
            return;
        }
        BluetoothAdapter adapter = bluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) { toast("Bluetooth indisponible ou désactivé."); return; }
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("GATT Inspector").setMessage("Aucun appareil associé. Associe d’abord un accessoire dans Android.")
                    .setPositiveButton("Ouvrir Bluetooth", (d, w) -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))).setNegativeButton("Fermer", null).show();
            return;
        }
        List<BluetoothDevice> devices = new ArrayList<>(bonded);
        String[] labels = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            String name;
            try { name = devices.get(i).getName(); } catch (Exception e) { name = null; }
            labels[i] = (name == null ? "Appareil associé" : name) + "\n" + devices.get(i).getAddress();
        }
        new AlertDialog.Builder(this).setTitle("Choisir un appareil GATT").setItems(labels, (d, which) -> inspectGatt(devices.get(which))).setNegativeButton("Annuler", null).show();
    }

    private void inspectGatt(BluetoothDevice device) {
        toast("Connexion GATT…");
        if (activeGatt != null) try { activeGatt.close(); } catch (Exception ignored) { }
        try {
            activeGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS) {
                        ui.post(() -> toast("Connexion GATT refusée ou non supportée."));
                    }
                }
                @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    StringBuilder b = new StringBuilder();
                    b.append("APPAREIL\n").append(device.getAddress()).append("\n\nSERVICES\n");
                    if (status != BluetoothGatt.GATT_SUCCESS) b.append("Découverte impossible, statut ").append(status);
                    else for (BluetoothGattService service : gatt.getServices()) {
                        b.append('\n').append(describeUuid(service.getUuid())).append("\n");
                        for (BluetoothGattCharacteristic c : service.getCharacteristics()) b.append("  • ").append(describeUuid(c.getUuid())).append(" [").append(properties(c.getProperties())).append("]\n");
                    }
                    ui.post(() -> showText("GATT Inspector", b.toString()));
                    logV10("Inspection GATT terminée pour " + device.getAddress());
                    ui.postDelayed(() -> { try { gatt.close(); } catch (Exception ignored) { } }, 1000);
                }
            });
            ui.postDelayed(() -> {
                if (activeGatt != null) {
                    try { activeGatt.disconnect(); activeGatt.close(); } catch (Exception ignored) { }
                    activeGatt = null;
                }
            }, 20000);
        } catch (Exception e) { toast("GATT impossible : " + e.getMessage()); }
    }

    private void showRoomLab() {
        String estimate = estimateRoom();
        String rooms = v10.getString("rooms", "");
        String message = "Pièce estimée : " + estimate + "\n\nPièces calibrées : " + (rooms.isEmpty() ? "aucune" : rooms.replace("|", ", ")) +
                "\n\nLa calibration enregistre uniquement l’empreinte Wi-Fi locale : BSSID et RSSI. Aucun cloud.";
        new AlertDialog.Builder(this).setTitle("Room Fingerprint").setMessage(message)
                .setPositiveButton("Calibrer ici", (d, w) -> askRoomName())
                .setNeutralButton("Réinitialiser", (d, w) -> { v10.edit().remove("rooms").apply(); toast("Empreintes supprimées."); })
                .setNegativeButton("Fermer", null).show();
    }

    private void askRoomName() {
        EditText input = new EditText(this);
        input.setHint("Ex. Bureau");
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        input.setBackground(rounded(PANEL_2, 14));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Nom de la pièce").setView(wrap).setPositiveButton("Enregistrer", (d, w) -> calibrateRoom(input.getText().toString().trim())).setNegativeButton("Annuler", null).show();
    }

    private void calibrateRoom(String room) {
        if (room.isEmpty()) { toast("Nom invalide."); return; }
        WifiInfo wi = wifiInfo();
        if (wi == null || wi.getBSSID() == null) { toast("Connecte le téléphone au Wi-Fi pour calibrer."); return; }
        String key = safe(room);
        v10.edit().putString("room_" + key + "_bssid", wi.getBSSID()).putInt("room_" + key + "_rssi", wi.getRssi()).apply();
        String rooms = v10.getString("rooms", "");
        List<String> list = splitRooms(rooms);
        if (!list.contains(room)) list.add(room);
        v10.edit().putString("rooms", joinRooms(list)).apply();
        toast(room + " calibrée.");
        logV10("Empreinte de pièce calibrée : " + room);
    }

    private String estimateRoom() {
        WifiInfo wi = wifiInfo();
        List<String> rooms = splitRooms(v10.getString("rooms", ""));
        if (wi == null || rooms.isEmpty() || wi.getBSSID() == null) return "non déterminée";
        String best = "non déterminée";
        int score = Integer.MAX_VALUE;
        for (String room : rooms) {
            String key = safe(room);
            String bssid = v10.getString("room_" + key + "_bssid", "");
            int rssi = v10.getInt("room_" + key + "_rssi", -100);
            int s = Math.abs(wi.getRssi() - rssi) + (bssid.equalsIgnoreCase(wi.getBSSID()) ? 0 : 35);
            if (s < score) { score = s; best = room; }
        }
        return best + " · confiance " + Math.max(20, 100 - score * 2) + " %";
    }

    private void showRealityHud() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        RealityView view = new RealityView(this);
        view.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(rounded(Color.BLACK, 0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        logV10("Reality HUD ouvert.");
    }

    private void showAiBrief() {
        String room = estimateRoom();
        WifiInfo wi = wifiInfo();
        int rssi = wi == null ? -127 : wi.getRssi();
        String signal = rssi > -55 ? "excellent" : rssi > -67 ? "bon" : rssi > -78 ? "faible" : "très faible";
        long rx = Math.max(0, TrafficStats.getTotalRxBytes() - trafficStartRx);
        long tx = Math.max(0, TrafficStats.getTotalTxBytes() - trafficStartTx);
        StringBuilder b = new StringBuilder();
        b.append("SYNTHÈSE LOCALE\n\n");
        b.append("Sentry fonctionne en mode ").append(v10.getBoolean("setup_complete", false) ? "configuré" : "partiel").append(". ");
        b.append("La pièce actuelle est ").append(room).append(". ");
        b.append("Le signal Wi-Fi est ").append(signal).append(" (").append(rssi).append(" dBm). ");
        if (hasUwb()) b.append("Le téléphone possède l’UWB, utilisable avec des appareils participants. ");
        else if (hasRtt()) b.append("Le Wi-Fi RTT est disponible pour des bornes compatibles. ");
        else b.append("Le moteur utilise la fusion RSSI et les empreintes locales comme fallback. ");
        b.append("Depuis l’ouverture, ").append(bytes(rx + tx)).append(" ont transité selon les compteurs Android. ");
        b.append("Aucune détection de personnes ni interception de contenu n’est active.\n\n");
        b.append("RECOMMANDATIONS\n");
        if (!bluetoothReady()) b.append("• Autoriser Bluetooth pour enrichir le jumeau spatial.\n");
        if (splitRooms(v10.getString("rooms", "")).isEmpty()) b.append("• Calibrer au moins deux pièces pour améliorer l’estimation de zone.\n");
        if (!isDeviceSecure()) b.append("• Activer un verrouillage sécurisé du téléphone.\n");
        if (rssi < -75) b.append("• Se rapprocher du point d’accès ou ajuster sa position.\n");
        b.append("• Utiliser Timeline après plusieurs scans pour comparer les changements.");
        logV10("Local AI Brief généré.");
        showText("Local AI Brief", b.toString());
    }

    private void showSecurityAudit() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        int encryption = dpm == null ? DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED : dpm.getStorageEncryptionStatus();
        String enc = encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE || encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER ? "actif" : encryption == DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED ? "non signalé" : "à vérifier";
        boolean dev = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        String report = "VERROUILLAGE\n" + (isDeviceSecure() ? "Sécurisé" : "Aucun verrouillage sécurisé détecté") +
                "\n\nCHIFFREMENT\n" + enc +
                "\n\nPATCH ANDROID\n" + Build.VERSION.SECURITY_PATCH +
                "\n\nSYSTÈME\nAndroid " + Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT +
                "\n\nOPTIONS DÉVELOPPEUR\n" + (dev ? "Actives" : "Inactives") +
                "\n\nSENTRY\nDonnées locales · sauvegarde système désactivée · trafic non déchiffré";
        new AlertDialog.Builder(this).setTitle("Security Audit").setMessage(report)
                .setPositiveButton("Réglages sécurité", (d, w) -> startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)))
                .setNeutralButton("Batterie", (d, w) -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))
                .setNegativeButton("Fermer", null).show();
    }

    private void showLegacyLabs() {
        String[] labels = {"Sentry v1 Local Audit", "Sentry Pro v1.3", "Sentry v2 Intelligence", "Sentry v3", "Sentry v3.5", "Sentry v4 Spatial"};
        Class<?>[] classes = {MainActivity.class, ProActivity.class, V2Activity.class, V3Activity.class, V35Activity.class, V4Activity.class};
        new AlertDialog.Builder(this).setTitle("Legacy Labs").setItems(labels, (d, which) -> startActivity(new Intent(this, classes[which]))).setNegativeButton("Fermer", null).show();
    }

    private void exportV10() {
        try {
            JSONObject root = new JSONObject();
            root.put("product", "Sentry v10 Digital Twin Core");
            root.put("generated_at", iso(System.currentTimeMillis()));
            root.put("phone_only", true);
            root.put("person_detection", false);
            root.put("capabilities", capabilitiesJson());
            root.put("network", networkJson());
            root.put("telemetry", telemetryJson());
            root.put("estimated_room", estimateRoom());
            root.put("journal", new JSONArray(v10.getString("journal", "").split("\\n")));
            JSONObject settings = new JSONObject();
            for (Map.Entry<String, ?> e : v10.getAll().entrySet()) {
                if (!e.getKey().contains("journal")) settings.put(e.getKey(), String.valueOf(e.getValue()));
            }
            root.put("v10_settings", settings);
            pendingExport = root.toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "sentry-v10-digital-twin-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(new Date()) + ".json");
            startActivityForResult(intent, REQ_EXPORT);
        } catch (Exception e) { toast("Rapport impossible : " + e.getMessage()); }
    }

    private JSONObject capabilitiesJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("ble", hasFeature(PackageManager.FEATURE_BLUETOOTH_LE));
        o.put("wifi_rtt", hasRtt());
        o.put("uwb", hasUwb());
        o.put("ar_services", packageExists("com.google.ar.core"));
        o.put("nfc", hasFeature(PackageManager.FEATURE_NFC));
        o.put("pressure", hasSensor(Sensor.TYPE_PRESSURE));
        o.put("gyroscope", hasSensor(Sensor.TYPE_GYROSCOPE));
        o.put("magnetometer", hasSensor(Sensor.TYPE_MAGNETIC_FIELD));
        return o;
    }

    private JSONObject networkJson() throws Exception {
        JSONObject o = new JSONObject();
        WifiInfo wi = wifiInfo();
        if (wi != null) {
            o.put("ssid", cleanSsid(wi.getSSID()));
            o.put("bssid", wi.getBSSID());
            o.put("rssi", wi.getRssi());
            o.put("frequency_mhz", wi.getFrequency());
            o.put("link_mbps", wi.getLinkSpeed());
        }
        return o;
    }

    private JSONObject telemetryJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("heading", heading);
        o.put("pitch", pitch);
        o.put("roll", roll);
        o.put("pressure_hpa", pressure);
        o.put("light_lux", light);
        o.put("rx_bytes_session", Math.max(0, TrafficStats.getTotalRxBytes() - trafficStartRx));
        o.put("tx_bytes_session", Math.max(0, TrafficStats.getTotalTxBytes() - trafficStartTx));
        return o;
    }

    private void openV4Tab(int target) {
        try {
            Field field = V4Activity.class.getDeclaredField("tab");
            field.setAccessible(true);
            field.setInt(this, target);
            Method render = V4Activity.class.getDeclaredMethod("render");
            render.setAccessible(true);
            render.invoke(this);
        } catch (Exception e) { toast("Module spatial indisponible : " + e.getMessage()); }
    }

    private void invokeV4(String name) {
        try {
            Method method = V4Activity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception ignored) { }
    }

    private void registerSensors() {
        if (sensorManager == null) return;
        register(Sensor.TYPE_ROTATION_VECTOR);
        register(Sensor.TYPE_PRESSURE);
        register(Sensor.TYPE_LIGHT);
    }

    private void register(int type) {
        Sensor sensor = sensorManager.getDefaultSensor(type);
        if (sensor != null) sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
    }

    private void unregisterSensors() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] matrix = new float[9];
            float[] values = new float[3];
            SensorManager.getRotationMatrixFromVector(matrix, event.values);
            SensorManager.getOrientation(matrix, values);
            heading = (float) ((Math.toDegrees(values[0]) + 360) % 360);
            pitch = (float) Math.toDegrees(values[1]);
            roll = (float) Math.toDegrees(values[2]);
        } else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) pressure = event.values[0];
        else if (event.sensor.getType() == Sensor.TYPE_LIGHT) light = event.values[0];
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private BluetoothAdapter bluetoothAdapter() {
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    private boolean bluetoothReady() {
        BluetoothAdapter adapter = bluetoothAdapter();
        boolean permission = Build.VERSION.SDK_INT < 31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
        return adapter != null && adapter.isEnabled() && permission;
    }

    private WifiInfo wifiInfo() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            return wm == null ? null : wm.getConnectionInfo();
        } catch (Exception e) { return null; }
    }

    private boolean wifiReady() {
        WifiInfo wi = wifiInfo();
        return wi != null && wi.getNetworkId() != -1;
    }

    private boolean hasRtt() { return hasFeature("android.hardware.wifi.rtt"); }
    private boolean hasUwb() { return Build.VERSION.SDK_INT >= 31 && hasFeature("android.hardware.uwb"); }
    private boolean hasFeature(String feature) { return getPackageManager().hasSystemFeature(feature); }
    private boolean hasSensor(int type) { return sensorManager != null && sensorManager.getDefaultSensor(type) != null; }

    private boolean packageExists(String packageName) {
        try { getPackageManager().getPackageInfo(packageName, 0); return true; }
        catch (Exception e) { return false; }
    }

    private boolean isDeviceSecure() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    private String capabilitySummaryOneLine() {
        return "BLE=" + hasFeature(PackageManager.FEATURE_BLUETOOTH_LE) + ", RTT=" + hasRtt() + ", UWB=" + hasUwb() + ", AR=" + packageExists("com.google.ar.core");
    }

    private void line(StringBuilder b, String name, boolean available, String fallback) {
        b.append(available ? "✓ " : "○ ").append(name).append("\n   ").append(fallback).append("\n\n");
    }

    private String describeUuid(UUID uuid) {
        String s = uuid.toString().toLowerCase(Locale.ROOT);
        if (s.startsWith("0000180f")) return "Battery Service · " + uuid;
        if (s.startsWith("00001800")) return "Generic Access · " + uuid;
        if (s.startsWith("00001801")) return "Generic Attribute · " + uuid;
        if (s.startsWith("0000180a")) return "Device Information · " + uuid;
        if (s.startsWith("00002a19")) return "Battery Level · " + uuid;
        if (s.startsWith("0000180d")) return "Heart Rate · " + uuid;
        return uuid.toString();
    }

    private String properties(int p) {
        List<String> items = new ArrayList<>();
        if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) items.add("READ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) items.add("WRITE");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) items.add("WRITE_NR");
        if ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) items.add("NOTIFY");
        if ((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) items.add("INDICATE");
        return items.isEmpty() ? "—" : android.text.TextUtils.join(",", items);
    }

    private String cleanSsid(String ssid) {
        if (ssid == null) return "inconnu";
        return ssid.replace("\"", "");
    }

    private String bytes(long value) {
        if (value < 1024) return value + " B";
        if (value < 1024L * 1024) return String.format(Locale.FRANCE, "%.1f KB", value / 1024f);
        if (value < 1024L * 1024 * 1024) return String.format(Locale.FRANCE, "%.1f MB", value / 1048576f);
        return String.format(Locale.FRANCE, "%.2f GB", value / 1073741824f);
    }

    private void logV10(String event) {
        String old = v10 == null ? "" : v10.getString("journal", "");
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(new Date()) + " · " + event;
        String merged = old.isEmpty() ? line : old + "\n" + line;
        if (merged.length() > 40000) merged = merged.substring(merged.length() - 40000);
        if (v10 != null) v10.edit().putString("journal", merged).apply();
    }

    private String iso(long time) { return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.FRANCE).format(new Date(time)); }
    private String safe(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_"); }

    private List<String> splitRooms(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return list;
        for (String value : raw.split("\\|")) if (!value.trim().isEmpty()) list.add(value.trim());
        return list;
    }

    private String joinRooms(List<String> rooms) { return android.text.TextUtils.join("|", rooms); }

    private void showText(String title, String body) {
        TextView view = text(body, 13, TEXT, false);
        view.setTextIsSelectable(true);
        view.setPadding(dp(18), dp(10), dp(18), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Fermer", null).show();
    }

    private LinearLayout statusCard(String title, boolean ok, String sub) {
        LinearLayout card = column(4);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(PANEL, 15));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = text(ok ? "✓" : "○", 16, ok ? GOOD : WARN, true);
        line.addView(dot, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout labels = column(2);
        labels.addView(text(title, 14, TEXT, true));
        labels.addView(text(sub, 11, MUTED, false));
        line.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(line);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout wrapper = column(8);
        wrapper.addView(card);
        return wrapper;
    }

    private TextView metric(String label, String value, int color) {
        TextView t = text(label + "\n" + value, 10, color, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(5), dp(10), dp(5), dp(10));
        t.setBackground(rounded(PANEL, 14));
        return t;
    }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }

    private TextView action(String label, int color, int textColor) {
        TextView t = text(label, 13, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(15), dp(12), dp(15));
        t.setBackground(rounded(color, 16));
        return t;
    }

    private LinearLayout column(int gap) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        if (gap > 0) l.setDividerDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        l.setDividerPadding(gap);
        return l;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View spaceW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private class RealityView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private long born = System.currentTimeMillis();
        RealityView(Context context) { super(context); setBackgroundColor(Color.BLACK); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(80, 64, 235, 224));
            for (int i = 1; i <= 5; i++) c.drawCircle(cx, cy, Math.min(w, h) * i / 12f, paint);
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30 - 90);
                c.drawLine(cx, cy, cx + (float) Math.cos(a) * w, cy + (float) Math.sin(a) * w, paint);
            }
            float sweep = ((System.currentTimeMillis() - born) % 4000) / 4000f * 360f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(72, 64, 235, 224));
            RectF oval = new RectF(cx - Math.min(w, h) * .42f, cy - Math.min(w, h) * .42f, cx + Math.min(w, h) * .42f, cy + Math.min(w, h) * .42f);
            c.drawArc(oval, sweep - 18, 18, true, paint);
            paint.setColor(CYAN);
            c.drawCircle(cx, cy, dp(6), paint);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(26));
            c.drawText("REALITY HUD", cx, dp(54), paint);
            paint.setTextSize(dp(13));
            paint.setColor(TEXT);
            c.drawText("CAP " + Math.round(heading) + "°   ·   " + estimateRoom(), cx, dp(82), paint);
            paint.setTextSize(dp(12));
            paint.setColor(MUTED);
            c.drawText("Fusion capteurs + réseau · toucher pour fermer", cx, h - dp(42), paint);
            paint.setTextSize(dp(11));
            c.drawText(hasUwb() ? "UWB READY" : hasRtt() ? "WI-FI RTT READY" : "PROBABILISTIC FALLBACK", cx, h - dp(20), paint);
            postInvalidateDelayed(32);
        }
    }
}
