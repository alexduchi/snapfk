package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class V4Activity extends Activity {
    private static final int REQ_BLE = 401;
    private static final int REQ_EXPORT = 402;
    private static final int[] IDENT_PORTS = {22,23,53,80,139,443,445,554,631,1883,3389,5000,8008,8009,8080,8443,8883,9100};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(36);
    private final List<LanNode> online = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, SpatialDevice> devices = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, String> ssdp = Collections.synchronizedMap(new LinkedHashMap<>());

    private SharedPreferences prefs;
    private FrameLayout content;
    private LinearLayout nav;
    private TextView title;
    private TextView subtitle;
    private TextView liveStatus;
    private UniverseView universeView;
    private RadarView radarView;
    private BluetoothLeScanner bleScanner;
    private boolean bleScanning;
    private boolean lanScanning;
    private String selectedId;
    private int tab;
    private int bg, surface, surface2, text, muted, accent, good, warn, bad, cyan;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("sentry_local", MODE_PRIVATE);
        defaults();
        palette();
        importLegacy();
        hydrateDevices();
        buildShell();
        render();
        log("Sentry v4 Spatial Intelligence démarré.");
        if (prefs.getBoolean("v4_auto_scan", false)) startSpatialSweep();
    }

    private void defaults() {
        SharedPreferences.Editor e = prefs.edit();
        if (!prefs.contains("v4_profile")) e.putString("v4_profile", prefs.getString("profile", "Maison"));
        if (!prefs.contains("v4_theme")) e.putString("v4_theme", prefs.getString("theme", "dark"));
        if (!prefs.contains("v4_accent")) e.putString("v4_accent", prefs.getString("accent", "cyan"));
        if (!prefs.contains("v4_quality")) e.putString("v4_quality", "ultra");
        if (!prefs.contains("v4_animation")) e.putBoolean("v4_animation", true);
        if (!prefs.contains("v4_trails")) e.putBoolean("v4_trails", true);
        if (!prefs.contains("v4_labels")) e.putBoolean("v4_labels", true);
        if (!prefs.contains("v4_grid")) e.putBoolean("v4_grid", true);
        if (!prefs.contains("v4_privacy")) e.putBoolean("v4_privacy", false);
        if (!prefs.contains("v4_auto_scan")) e.putBoolean("v4_auto_scan", false);
        if (!prefs.contains("v4_timeout")) e.putInt("v4_timeout", 430);
        e.apply();
    }

    private String profile() { return prefs.getString("v4_profile", "Maison"); }
    private String pk(String key) { return "v4_" + profile() + "_" + key; }

    private void palette() {
        String theme = prefs.getString("v4_theme", "dark");
        if ("light".equals(theme)) {
            bg = Color.rgb(234, 240, 238); surface = Color.WHITE; surface2 = Color.rgb(218, 231, 226);
            text = Color.rgb(16, 27, 23); muted = Color.rgb(84, 105, 96);
        } else if ("amoled".equals(theme)) {
            bg = Color.BLACK; surface = Color.rgb(5, 10, 8); surface2 = Color.rgb(17, 30, 24);
            text = Color.rgb(239, 251, 246); muted = Color.rgb(123, 157, 143);
        } else {
            bg = Color.rgb(4, 12, 9); surface = Color.rgb(12, 25, 20); surface2 = Color.rgb(19, 41, 33);
            text = Color.rgb(238, 251, 246); muted = Color.rgb(124, 159, 144);
        }
        String a = prefs.getString("v4_accent", "cyan");
        accent = "violet".equals(a) ? Color.rgb(186, 139, 248) : "amber".equals(a) ? Color.rgb(241, 187, 80) : "red".equals(a) ? Color.rgb(242, 103, 123) : Color.rgb(58, 234, 194);
        cyan = Color.rgb(65, 188, 248); good = Color.rgb(87, 224, 139); warn = Color.rgb(244, 188, 80); bad = Color.rgb(244, 99, 119);
    }

    private void importLegacy() {
        if (prefs.getBoolean(pk("legacy_imported"), false)) return;
        String legacyPrefix = "v35_" + profile() + "_";
        Set<String> legacy = prefs.getStringSet(legacyPrefix + "known", new HashSet<>());
        if (legacy != null && !legacy.isEmpty()) {
            SharedPreferences.Editor e = prefs.edit();
            Set<String> known = new HashSet<>();
            for (String ip : legacy) {
                String id = "lan:" + ip;
                known.add(id);
                e.putString(pk("name_" + id), prefs.getString(legacyPrefix + "name_" + ip, "Appareil " + lastOctet(ip)));
                e.putString(pk("type_" + id), prefs.getString(legacyPrefix + "type_" + ip, "Appareil réseau"));
                e.putString(pk("vendor_" + id), prefs.getString(legacyPrefix + "vendor_" + ip, "Non déterminé"));
                e.putString(pk("ports_" + id), prefs.getString(legacyPrefix + "ports_" + ip, ""));
                e.putString(pk("address_" + id), ip);
                e.putString(pk("source_" + id), "LAN");
                e.putLong(pk("last_ms_" + id), System.currentTimeMillis());
            }
            e.putStringSet(pk("known"), known);
            e.putBoolean(pk("legacy_imported"), true);
            e.apply();
        } else prefs.edit().putBoolean(pk("legacy_imported"), true).apply();
    }

    private void hydrateDevices() {
        devices.clear();
        Set<String> known = prefs.getStringSet(pk("known"), new HashSet<>());
        if (known == null) return;
        for (String id : known) {
            SpatialDevice d = new SpatialDevice(id);
            d.name = prefs.getString(pk("name_" + id), "Unknown device");
            d.type = prefs.getString(pk("type_" + id), "unknown");
            d.vendor = prefs.getString(pk("vendor_" + id), "Non déterminé");
            d.address = prefs.getString(pk("address_" + id), "—");
            d.source = prefs.getString(pk("source_" + id), id.startsWith("ble:") ? "Bluetooth" : "LAN");
            d.room = prefs.getString(pk("room_" + id), "Non assignée");
            d.rssi = prefs.getInt(pk("rssi_" + id), -90);
            d.confidence = prefs.getInt(pk("confidence_" + id), 35);
            d.lastSeen = prefs.getLong(pk("last_ms_" + id), 0L);
            d.firstSeen = prefs.getLong(pk("first_ms_" + id), d.lastSeen);
            d.seenCount = prefs.getInt(pk("seen_" + id), 1);
            d.online = System.currentTimeMillis() - d.lastSeen < 120000;
            loadTrail(d);
            devices.put(id, d);
        }
        if (selectedId == null && !devices.isEmpty()) selectedId = devices.keySet().iterator().next();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        root.setOnApplyWindowInsetsListener((v, i) -> { v.setPadding(0, i.getSystemWindowInsetTop(), 0, i.getSystemWindowInsetBottom()); return i; });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(15), dp(12), dp(15), dp(8));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = tv("S4", 15, Color.rgb(4, 23, 16), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(accent, 15));
        top.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));
        top.addView(gapW(10));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        title = tv("SPATIAL UNIVERSE", 22, text, true);
        subtitle = tv("Digital twin · " + profile(), 12, muted, false);
        names.addView(title);
        names.addView(subtitle);
        top.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView profileBadge = tv(profile().toUpperCase(Locale.FRANCE), 9, accent, true);
        profileBadge.setPadding(dp(9), dp(6), dp(9), dp(6));
        profileBadge.setBackground(round(surface2, 12));
        profileBadge.setOnClickListener(v -> chooseProfile());
        top.addView(profileBadge);
        head.addView(top);
        root.addView(head);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(3), dp(5), dp(3), dp(7));
        String[] labels = {"Universe", "Inspect", "Rooms", "Radar", "Timeline", "Settings"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = tv(labels[i], 8.5f, muted, true);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(1), dp(11), dp(1), dp(11));
            item.setOnClickListener(v -> { tab = index; render(); });
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(nav);
        setContentView(root);
    }

    private void rebuild() { palette(); buildShell(); render(); }

    private void render() {
        String[] titles = {"SPATIAL UNIVERSE", "DEVICE INSPECT", "ROOM INTELLIGENCE", "PROXIMITY RADAR", "SPATIAL TIMELINE", "SYSTEM SETTINGS"};
        String[] subs = {"Digital twin 3D · " + devices.size() + " objets", "Dossier et modèle procédural", "Présence estimée par zones", "Bluetooth RSSI et trajectoires", "Mouvements et apparitions", "Qualité, confidentialité et rendu"};
        title.setText(titles[tab]); subtitle.setText(subs[tab]);
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView item = (TextView) nav.getChildAt(i);
            boolean active = i == tab;
            item.setTextColor(active ? Color.rgb(3, 22, 15) : muted);
            item.setBackground(active ? round(accent, 16) : round(Color.TRANSPARENT, 16));
        }
        content.removeAllViews();
        if (tab == 0) content.addView(universePage());
        else if (tab == 1) content.addView(inspectPage());
        else if (tab == 2) content.addView(roomsPage());
        else if (tab == 3) content.addView(radarPage());
        else if (tab == 4) content.addView(timelinePage());
        else content.addView(settingsPage());
    }

    private View universePage() {
        FrameLayout page = new FrameLayout(this);
        universeView = new UniverseView(this);
        page.addView(universeView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(12), dp(8), dp(12), dp(12));
        overlay.setGravity(Gravity.BOTTOM);
        LinearLayout hud = card();
        hud.getBackground().setAlpha(235);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        liveStatus = tv((lanScanning || bleScanning ? "LIVE SWEEP" : "SPATIAL IDLE") + " · " + devices.size() + " objets", 11, lanScanning || bleScanning ? good : accent, true);
        line.addView(liveStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView help = tv("Glisser · pincer · toucher", 10, muted, false);
        line.addView(help);
        hud.addView(line);
        hud.addView(gap(7));
        TextView scan = button(lanScanning || bleScanning ? "ARRÊTER LE SWEEP" : "LANCER SPATIAL SWEEP", true);
        scan.setOnClickListener(v -> { if (bleScanning || lanScanning) stopAllScans(); else startSpatialSweep(); });
        hud.addView(scan);
        overlay.addView(hud);
        page.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return page;
    }

    private View inspectPage() {
        LinearLayout page = page();
        SpatialDevice d = selectedDevice();
        LinearLayout picker = card();
        picker.addView(section("Objet sélectionné"));
        picker.addView(gap(5));
        if (devices.isEmpty()) picker.addView(tv("Aucun appareil connu. Lance un Spatial Sweep.", 12, muted, false));
        else {
            TextView choose = button(d == null ? "CHOISIR UN OBJET" : d.name, false);
            choose.setOnClickListener(v -> chooseDevice());
            picker.addView(choose);
        }
        page.addView(picker);
        if (d == null) return scroll(page);
        page.addView(gap(10));

        LinearLayout model = card();
        model.addView(section("Modèle spatial"));
        model.addView(gap(5));
        ModelView modelView = new ModelView(this, d);
        model.addView(modelView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        page.addView(model);
        page.addView(gap(10));

        LinearLayout identity = card();
        identity.addView(section(d.name));
        identity.addView(tv(d.typeLabel() + " · " + d.source, 11, accent, true));
        identity.addView(gap(7));
        identity.addView(row("Identifiant", privacy(d.address)));
        identity.addView(row("Constructeur probable", d.vendor));
        identity.addView(row("Confiance", d.confidence + "%"));
        identity.addView(row("État", d.online ? "Présent" : "Hors ligne"));
        identity.addView(row("Zone attribuée", d.room));
        identity.addView(row("Signal", d.source.equals("Bluetooth") ? d.rssi + " dBm" : "Réseau local"));
        identity.addView(row("Tendance", trend(d)));
        identity.addView(row("Stabilité", stability(d) + "%"));
        identity.addView(row("Première détection", dateTime(d.firstSeen)));
        identity.addView(row("Dernière détection", dateTime(d.lastSeen)));
        identity.addView(row("Observations", String.valueOf(d.seenCount)));
        identity.addView(row("Services", prefs.getString(pk("ports_" + d.id), "Non observés")));
        identity.addView(row("Empreinte", fingerprint(d).substring(0, 16).toUpperCase(Locale.FRANCE)));
        page.addView(identity);
        page.addView(gap(10));

        LinearLayout actions = card();
        actions.addView(section("Actions"));
        actions.addView(gap(6));
        TextView room = button("ASSIGNER UNE PIÈCE", false);
        room.setOnClickListener(v -> assignRoom(d));
        actions.addView(room);
        actions.addView(gap(7));
        TextView rename = button("RENOMMER", false);
        rename.setOnClickListener(v -> renameDevice(d));
        actions.addView(rename);
        page.addView(actions);
        return scroll(page);
    }

    private View roomsPage() {
        LinearLayout page = page();
        LinearLayout intro = card();
        intro.addView(section("Spatial rooms"));
        intro.addView(gap(5));
        intro.addView(tv("La pièce est une estimation ou une attribution manuelle. Un seul téléphone ne peut pas trianguler une position exacte.", 12, muted, false));
        page.addView(intro);
        page.addView(gap(10));

        RoomMapView roomMap = new RoomMapView(this);
        roomMap.setBackground(round(surface, 24));
        page.addView(roomMap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380)));
        page.addView(gap(10));

        String[] rooms = {"Salon", "Bureau", "Chambre", "Cuisine", "Garage", "Extérieur", "Non assignée"};
        for (String room : rooms) {
            List<SpatialDevice> in = devicesInRoom(room);
            if (in.isEmpty()) continue;
            LinearLayout c = card();
            c.addView(section(room));
            c.addView(tv(in.size() + " objet(s)", 10, accent, true));
            c.addView(gap(5));
            for (SpatialDevice d : in) c.addView(signalRow(d.typeLabel(), d.name + " · " + presenceProbability(d, room) + "% probable", d.online ? good : muted));
            page.addView(c);
            page.addView(gap(8));
        }
        return scroll(page);
    }

    private View radarPage() {
        LinearLayout page = page();
        LinearLayout controls = card();
        controls.addView(section("Proximity radar"));
        controls.addView(gap(5));
        controls.addView(tv("Les points utilisent une moyenne glissante RSSI. Les traces sont des évolutions de proximité, pas des coordonnées physiques.", 12, muted, false));
        TextView start = button(bleScanning ? "ARRÊTER LE RADAR" : "ACTIVER LE RADAR", true);
        start.setOnClickListener(v -> { if (bleScanning) stopBle(); else startBle(); start.setText(bleScanning ? "ARRÊTER LE RADAR" : "ACTIVER LE RADAR"); });
        controls.addView(gap(8));
        controls.addView(start);
        page.addView(controls);
        page.addView(gap(10));

        radarView = new RadarView(this);
        radarView.setBackground(round(surface, 24));
        page.addView(radarView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)));
        page.addView(gap(10));

        List<SpatialDevice> bluetooth = bluetoothDevices();
        for (SpatialDevice d : bluetooth) {
            LinearLayout c = card();
            c.addView(section(d.name));
            c.addView(tv(d.typeLabel() + " · " + zone(d.rssi), 10, zoneColor(zone(d.rssi)), true));
            c.addView(gap(5));
            c.addView(row("RSSI lissé", d.rssi + " dBm"));
            c.addView(row("Tendance", trend(d)));
            c.addView(row("Stabilité", stability(d) + "%"));
            c.addView(row("Pièce", d.room));
            c.setOnClickListener(v -> { selectedId = d.id; tab = 1; render(); });
            page.addView(c);
            page.addView(gap(8));
        }
        return scroll(page);
    }

    private View timelinePage() {
        LinearLayout page = page();
        LinearLayout stats = card();
        stats.addView(section("Spatial history"));
        stats.addView(gap(5));
        stats.addView(row("Objets connus", String.valueOf(devices.size())));
        stats.addView(row("Présents maintenant", String.valueOf(presentCount())));
        stats.addView(row("Bluetooth", String.valueOf(bluetoothDevices().size())));
        stats.addView(row("Réseau local", String.valueOf(lanDevices().size())));
        page.addView(stats);
        page.addView(gap(10));

        LinearLayout graph = card();
        graph.addView(section("Trajectoires de proximité"));
        graph.addView(gap(5));
        graph.addView(new TimelineGraphView(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        page.addView(graph);
        page.addView(gap(10));

        LinearLayout log = card();
        log.addView(section("Événements"));
        log.addView(gap(5));
        String raw = prefs.getString(pk("history"), "");
        if (raw == null || raw.trim().isEmpty()) log.addView(tv("Aucun événement.", 12, muted, false));
        else {
            String[] lines = raw.split("\\n");
            for (int i = 0; i < Math.min(60, lines.length); i++) {
                String line = lines[i];
                if (line.trim().isEmpty()) continue;
                int cut = line.indexOf("] ");
                log.addView(signalRow(cut > 0 ? line.substring(1, cut) : "EVENT", cut > 0 ? line.substring(cut + 2) : line, i < 3 ? accent : muted));
            }
        }
        page.addView(log);
        return scroll(page);
    }

    private View settingsPage() {
        LinearLayout page = page();
        LinearLayout render = card();
        render.addView(section("Rendu spatial"));
        render.addView(gap(4));
        render.addView(choice("Qualité", qualityLabel(), this::chooseQuality));
        render.addView(choice("Thème", themeLabel(), this::chooseTheme));
        render.addView(choice("Accent", accentLabel(), this::chooseAccent));
        render.addView(toggle("Animations 3D", "v4_animation"));
        render.addView(toggle("Traînées de mouvement", "v4_trails"));
        render.addView(toggle("Labels dans la scène", "v4_labels"));
        render.addView(toggle("Grille holographique", "v4_grid"));
        page.addView(render);
        page.addView(gap(10));

        LinearLayout scan = card();
        scan.addView(section("Acquisition"));
        scan.addView(gap(4));
        scan.addView(choice("Profil", profile(), this::chooseProfile));
        scan.addView(choice("Délai LAN", prefs.getInt("v4_timeout", 430) + " ms", this::chooseTimeout));
        scan.addView(toggle("Spatial Sweep au démarrage", "v4_auto_scan"));
        scan.addView(toggle("Masquer les identifiants", "v4_privacy"));
        page.addView(scan);
        page.addView(gap(10));

        LinearLayout data = card();
        data.addView(section("Données"));
        data.addView(gap(5));
        TextView export = button("EXPORTER LE DIGITAL TWIN", true);
        export.setOnClickListener(v -> exportReport());
        data.addView(export);
        data.addView(gap(7));
        TextView reset = button("RÉINITIALISER LE PROFIL", false);
        reset.setOnClickListener(v -> confirmReset());
        data.addView(reset);
        data.addView(gap(8));
        data.addView(row("Version", "4.0.0"));
        data.addView(row("Moteur visuel", "Spatial Canvas 3D"));
        data.addView(row("Traitement", "100 % local"));
        data.addView(row("Position", "Estimée avec incertitude"));
        data.addView(row("Interception de contenu", "Aucune"));
        page.addView(data);
        return scroll(page);
    }

    private void startSpatialSweep() {
        startLan();
        startBle();
        if (liveStatus != null) liveStatus.setText("LIVE SWEEP · acquisition LAN + BLE");
    }

    private void stopAllScans() { stopBle(); lanScanning = false; if (liveStatus != null) liveStatus.setText("SPATIAL IDLE · " + devices.size() + " objets"); }

    private void startLan() {
        if (lanScanning) return;
        String local = localIp();
        if (local == null || local.split("\\.").length != 4) { toast("Connexion IPv4 locale requise."); return; }
        String[] parts = local.split("\\.");
        String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
        online.clear(); ssdp.clear(); lanScanning = true;
        pool.submit(this::discoverSsdp);
        AtomicInteger remaining = new AtomicInteger(254);
        int timeout = prefs.getInt("v4_timeout", 430);
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + i;
            pool.submit(() -> {
                long begin = System.nanoTime(); boolean reachable = false;
                try { reachable = InetAddress.getByName(ip).isReachable(timeout); } catch (Exception ignored) {}
                long latency = Math.max(1, (System.nanoTime() - begin) / 1_000_000);
                if (reachable) online.add(new LanNode(ip, latency));
                if (remaining.decrementAndGet() == 0) ui.post(this::finishLan);
            });
        }
        log("Spatial Sweep LAN lancé sur " + prefix + "0/24.");
    }

    private void finishLan() {
        synchronized (online) {
            for (LanNode n : online) {
                String id = "lan:" + n.ip;
                SpatialDevice d = devices.get(id);
                if (d == null) d = new SpatialDevice(id);
                d.address = n.ip; d.source = "LAN"; d.online = true; d.lastSeen = System.currentTimeMillis();
                if (d.firstSeen == 0) d.firstSeen = d.lastSeen;
                d.seenCount++; d.rssi = -45;
                if (d.name == null || d.name.isEmpty()) d.name = "Network device " + lastOctet(n.ip);
                if (d.type == null || d.type.isEmpty()) d.type = "unknown";
                devices.put(id, d);
                persist(d);
                pool.submit(() -> identifyLan(n.ip));
            }
        }
        lanScanning = false;
        log("Spatial Sweep LAN terminé : " + online.size() + " actifs.");
        if (liveStatus != null) liveStatus.setText((bleScanning ? "LIVE BLE" : "SPATIAL IDLE") + " · " + devices.size() + " objets");
        if (universeView != null) universeView.invalidate();
    }

    private void identifyLan(String ip) {
        String id = "lan:" + ip;
        SpatialDevice d = devices.get(id); if (d == null) return;
        String host = reverseName(ip);
        String mac = arpMac(ip);
        List<Integer> ports = new ArrayList<>();
        Map<Integer,String> banners = new LinkedHashMap<>();
        for (int port : IDENT_PORTS) {
            try (Socket s = new Socket()) { s.connect(new InetSocketAddress(ip, port), 260); ports.add(port); }
            catch (Exception ignored) {}
        }
        for (int port : ports) if (port == 80 || port == 8080 || port == 8008 || port == 5000) {
            String b = httpBanner(ip, port); if (!b.isEmpty()) banners.put(port, b);
        }
        String combined = (host + " " + banners + " " + ssdp.getOrDefault(ip, "")).toLowerCase(Locale.ROOT);
        d.type = classifyLan(combined, ports);
        d.vendor = vendorFrom(combined);
        d.name = smartLanName(ip, host, d.type, d.vendor, combined);
        d.confidence = Math.min(98, 32 + (!host.isEmpty()?14:0) + (!mac.isEmpty()?10:0) + Math.min(20, ports.size()*3) + (!banners.isEmpty()?15:0) + (ssdp.containsKey(ip)?15:0));
        prefs.edit().putString(pk("ports_" + id), joinPorts(ports)).apply();
        persist(d);
        log("Objet réseau enrichi : " + d.name + ".");
        ui.post(() -> { if (universeView != null) universeView.invalidate(); });
    }

    private void startBle() {
        if (bleScanning) return;
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLE); return;
        }
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { toast("Active le Bluetooth."); return; }
        bleScanner = adapter.getBluetoothLeScanner();
        if (bleScanner == null) { toast("Scanner BLE indisponible."); return; }
        bleScanning = true;
        try { bleScanner.startScan(bleCallback); } catch (SecurityException e) { bleScanning = false; return; }
        ui.postDelayed(this::stopBle, 30000);
        log("Spatial Sweep Bluetooth activé.");
        if (radarView != null) radarView.invalidate();
    }

    private final ScanCallback bleCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            String id = "ble:" + address;
            SpatialDevice d = devices.get(id); if (d == null) d = new SpatialDevice(id);
            String name = "Bluetooth accessory";
            try { if (device.getName() != null && !device.getName().trim().isEmpty()) name = device.getName(); } catch (SecurityException ignored) {}
            int raw = result.getRssi();
            d.rssi = d.seenCount <= 1 ? raw : Math.round(d.rssi * .74f + raw * .26f);
            d.address = address; d.source = "Bluetooth"; d.name = name; d.online = true;
            d.vendor = vendorFrom(name.toLowerCase(Locale.ROOT));
            d.type = classifyBluetooth(name, device);
            d.confidence = Math.min(96, 44 + (!"Bluetooth accessory".equals(name) ? 26 : 0) + (!"unknown".equals(d.type) ? 20 : 0));
            d.lastSeen = System.currentTimeMillis(); if (d.firstSeen == 0) d.firstSeen = d.lastSeen; d.seenCount++;
            d.addTrail(d.rssi, d.lastSeen);
            devices.put(id, d); persist(d); saveTrail(d);
            if (selectedId == null) selectedId = id;
            SpatialDevice finalD = d;
            ui.post(() -> {
                if (liveStatus != null) liveStatus.setText("LIVE BLE · " + bluetoothDevices().size() + " accessoires");
                if (universeView != null) universeView.invalidate();
                if (radarView != null) radarView.invalidate();
            });
        }
    };

    private void stopBle() {
        if (!bleScanning) return;
        bleScanning = false;
        try { if (bleScanner != null) bleScanner.stopScan(bleCallback); } catch (SecurityException ignored) {}
        log("Spatial Sweep Bluetooth terminé : " + bluetoothDevices().size() + " accessoires.");
        if (liveStatus != null) liveStatus.setText((lanScanning ? "LIVE LAN" : "SPATIAL IDLE") + " · " + devices.size() + " objets");
        if (radarView != null) radarView.invalidate();
    }

    private String classifyBluetooth(String name, BluetoothDevice device) {
        String n = name.toLowerCase(Locale.ROOT);
        if (containsAny(n, "buds", "airpods", "earbuds", "earphone", "freebuds", "pixel buds")) return "earbuds";
        if (containsAny(n, "headset", "headphone", "wh-", "qc", "bose", "jbl tune")) return "headphones";
        if (containsAny(n, "speaker", "flip", "charge", "boom", "sound", "marshall")) return "speaker";
        if (containsAny(n, "watch", "band", "fitbit", "garmin", "amazfit")) return "watch";
        if (containsAny(n, "keyboard", "keychron", "mx keys")) return "keyboard";
        if (containsAny(n, "mouse", "mx master", "magic mouse")) return "mouse";
        if (containsAny(n, "controller", "gamepad", "dualshock", "dualsense", "xbox wireless", "joy-con")) return "gamepad";
        if (containsAny(n, "tag", "airtag", "tile", "smarttag")) return "tag";
        if (containsAny(n, "car", "tesla", "renault", "peugeot", "citroen", "bmw", "audi")) return "car";
        if (containsAny(n, "tv", "bravia", "smart tv")) return "tv";
        if (containsAny(n, "phone", "iphone", "galaxy", "pixel", "xiaomi", "oneplus")) return "phone";
        try {
            BluetoothClass bc = device.getBluetoothClass();
            if (bc != null) {
                int major = bc.getMajorDeviceClass();
                if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) return "speaker";
                if (major == BluetoothClass.Device.Major.COMPUTER) return "laptop";
                if (major == BluetoothClass.Device.Major.PHONE) return "phone";
                if (major == BluetoothClass.Device.Major.PERIPHERAL) return "gamepad";
                if (major == BluetoothClass.Device.Major.WEARABLE) return "watch";
            }
        } catch (SecurityException ignored) {}
        return "unknown";
    }

    private String classifyLan(String all, List<Integer> ports) {
        if (ports.contains(9100) || ports.contains(631) || containsAny(all, "printer", "ipp")) return "printer";
        if (ports.contains(554) || containsAny(all, "camera", "onvif", "rtsp")) return "camera";
        if (containsAny(all, "chromecast", "google cast", "airplay", "roku", "smart tv", "tizen", "webos")) return "tv";
        if (containsAny(all, "playstation", "xbox", "nintendo")) return "console";
        if (ports.contains(445) || ports.contains(139)) return "desktop";
        if (ports.contains(22) && (ports.contains(80) || ports.contains(443))) return "router";
        if (ports.contains(1883) || ports.contains(8883)) return "tag";
        return "unknown";
    }

    private String vendorFrom(String all) {
        String[][] table = {{"samsung","Samsung"},{"apple","Apple"},{"airpods","Apple"},{"google","Google"},{"sony","Sony"},{"playstation","Sony"},{"microsoft","Microsoft"},{"xbox","Microsoft"},{"lg","LG"},{"philips","Philips"},{"bose","Bose"},{"jbl","JBL"},{"logitech","Logitech"},{"razer","Razer"},{"corsair","Corsair"},{"garmin","Garmin"},{"fitbit","Fitbit"},{"xiaomi","Xiaomi"},{"huawei","Huawei"},{"tp-link","TP-Link"},{"ubiquiti","Ubiquiti"},{"synology","Synology"},{"qnap","QNAP"},{"canon","Canon"},{"epson","Epson"},{"brother","Brother"},{"tesla","Tesla"},{"renault","Renault"},{"peugeot","Peugeot"}};
        for (String[] v : table) if (all.contains(v[0])) return v[1] + " probable";
        return "Non déterminé";
    }

    private void persist(SpatialDevice d) {
        Set<String> known = prefs.getStringSet(pk("known"), new HashSet<>());
        known = known == null ? new HashSet<>() : new HashSet<>(known); known.add(d.id);
        SharedPreferences.Editor e = prefs.edit();
        e.putStringSet(pk("known"), known);
        e.putString(pk("name_" + d.id), d.name);
        e.putString(pk("type_" + d.id), d.type);
        e.putString(pk("vendor_" + d.id), d.vendor);
        e.putString(pk("address_" + d.id), d.address);
        e.putString(pk("source_" + d.id), d.source);
        e.putString(pk("room_" + d.id), d.room == null ? "Non assignée" : d.room);
        e.putInt(pk("rssi_" + d.id), d.rssi);
        e.putInt(pk("confidence_" + d.id), d.confidence);
        e.putLong(pk("last_ms_" + d.id), d.lastSeen);
        e.putLong(pk("first_ms_" + d.id), d.firstSeen);
        e.putInt(pk("seen_" + d.id), d.seenCount);
        e.apply();
    }

    private void saveTrail(SpatialDevice d) {
        StringBuilder b = new StringBuilder();
        for (TrailPoint p : d.trail) b.append(p.time).append(',').append(p.rssi).append(';');
        prefs.edit().putString(pk("trail_" + d.id), b.toString()).apply();
    }

    private void loadTrail(SpatialDevice d) {
        String raw = prefs.getString(pk("trail_" + d.id), ""); if (raw == null || raw.isEmpty()) return;
        for (String item : raw.split(";")) {
            if (item.trim().isEmpty()) continue; String[] p = item.split(",");
            try { d.trail.add(new TrailPoint(Long.parseLong(p[0]), Integer.parseInt(p[1]))); } catch (Exception ignored) {}
        }
    }

    private void chooseDevice() {
        List<SpatialDevice> list = new ArrayList<>(devices.values());
        list.sort(Comparator.comparing(a -> a.name.toLowerCase(Locale.ROOT)));
        String[] names = new String[list.size()]; for (int i=0;i<list.size();i++) names[i]=list.get(i).name + " · " + list.get(i).typeLabel();
        new AlertDialog.Builder(this).setTitle("Choisir un objet").setItems(names, (d,w)->{ selectedId=list.get(w).id; render(); }).show();
    }

    private void assignRoom(SpatialDevice d) {
        String[] rooms = {"Salon", "Bureau", "Chambre", "Cuisine", "Garage", "Extérieur", "Non assignée"};
        new AlertDialog.Builder(this).setTitle("Pièce probable").setItems(rooms, (x,w)->{ d.room=rooms[w]; persist(d); log(d.name + " assigné à " + rooms[w] + "."); render(); }).show();
    }

    private void renameDevice(SpatialDevice d) {
        EditText input = new EditText(this); input.setText(d.name); input.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Renommer").setView(input).setPositiveButton("Enregistrer", (x,w)->{ String value=input.getText().toString().trim(); if(!value.isEmpty()){d.name=value;persist(d);render();}}).setNegativeButton("Annuler",null).show();
    }

    private void chooseProfile() {
        String[] values = {"Maison", "Bureau", "Lycée", "Vacances", "Laboratoire"};
        new AlertDialog.Builder(this).setTitle("Profil spatial").setItems(values, (d,w)->{prefs.edit().putString("v4_profile",values[w]).apply();selectedId=null;hydrateDevices();rebuild();}).show();
    }

    private void chooseTheme() {
        String[] values={"Cyber sombre","Clair technique","AMOLED"};
        new AlertDialog.Builder(this).setTitle("Thème").setItems(values,(d,w)->{prefs.edit().putString("v4_theme",w==1?"light":w==2?"amoled":"dark").apply();rebuild();}).show();
    }

    private void chooseAccent() {
        String[] values={"Cyber cyan","Violet","Ambre","Rouge"};
        new AlertDialog.Builder(this).setTitle("Accent").setItems(values,(d,w)->{prefs.edit().putString("v4_accent",w==1?"violet":w==2?"amber":w==3?"red":"cyan").apply();rebuild();}).show();
    }

    private void chooseQuality() {
        String[] values={"Économie","Équilibrée","Ultra"};
        new AlertDialog.Builder(this).setTitle("Qualité 3D").setItems(values,(d,w)->{prefs.edit().putString("v4_quality",w==0?"eco":w==1?"balanced":"ultra").apply();render();}).show();
    }

    private void chooseTimeout() {
        String[] values={"Rapide · 280 ms","Équilibré · 430 ms","Précis · 800 ms"};
        new AlertDialog.Builder(this).setTitle("Délai LAN").setItems(values,(d,w)->{prefs.edit().putInt("v4_timeout",w==0?280:w==2?800:430).apply();render();}).show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle("Réinitialiser le profil ?").setMessage("Toutes les identités et trajectoires de " + profile() + " seront supprimées.")
                .setPositiveButton("Réinitialiser",(d,w)->{SharedPreferences.Editor e=prefs.edit();for(String key:prefs.getAll().keySet())if(key.startsWith("v4_"+profile()+"_"))e.remove(key);e.apply();devices.clear();selectedId=null;render();})
                .setNegativeButton("Annuler",null).show();
    }

    private void exportReport() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"Sentry-v4-Spatial-"+profile()+".json");startActivityForResult(i,REQ_EXPORT);
    }

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==REQ_EXPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(report().toString(2).getBytes(StandardCharsets.UTF_8));toast("Digital twin exporté.");}catch(Exception e){toast("Export impossible.");}}}

    private JSONObject report() throws Exception {
        JSONObject root=new JSONObject();root.put("application","Sentry 4.0 Spatial Intelligence");root.put("profile",profile());root.put("generated_at",now());root.put("positioning","estimated_with_uncertainty");root.put("local_only",true);
        JSONArray list=new JSONArray();for(SpatialDevice d:devices.values()){JSONObject o=new JSONObject();o.put("id",d.id);o.put("name",d.name);o.put("type",d.type);o.put("vendor",d.vendor);o.put("source",d.source);o.put("address",d.address);o.put("room",d.room);o.put("rssi",d.rssi);o.put("confidence",d.confidence);o.put("first_seen",d.firstSeen);o.put("last_seen",d.lastSeen);o.put("observations",d.seenCount);JSONArray trail=new JSONArray();for(TrailPoint p:d.trail){JSONObject t=new JSONObject();t.put("time",p.time);t.put("rssi",p.rssi);trail.put(t);}o.put("trail",trail);list.put(o);}root.put("devices",list);root.put("history",prefs.getString(pk("history"),""));return root;
    }

    private void discoverSsdp() {
        String q="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n";
        try(DatagramSocket s=new DatagramSocket()){s.setSoTimeout(600);byte[] p=q.getBytes(StandardCharsets.US_ASCII);s.send(new DatagramPacket(p,p.length,InetAddress.getByName("239.255.255.250"),1900));long end=System.currentTimeMillis()+4500;while(System.currentTimeMillis()<end){byte[] b=new byte[4096];DatagramPacket r=new DatagramPacket(b,b.length);try{s.receive(r);}catch(Exception timeout){continue;}String raw=new String(r.getData(),0,r.getLength(),StandardCharsets.ISO_8859_1);Map<String,String> h=parseHeaders(raw);ssdp.put(r.getAddress().getHostAddress(),h.getOrDefault("server","UPnP")+" · "+h.getOrDefault("st","service"));}}catch(Exception ignored){}
    }

    private Map<String,String> parseHeaders(String raw){Map<String,String> m=new HashMap<>();for(String line:raw.split("\\r?\\n")){int i=line.indexOf(':');if(i>0)m.put(line.substring(0,i).trim().toLowerCase(Locale.ROOT),line.substring(i+1).trim());}return m;}
    private String httpBanner(String ip,int port){try(Socket s=new Socket()){s.connect(new InetSocketAddress(ip,port),340);s.setSoTimeout(380);s.getOutputStream().write(("HEAD / HTTP/1.0\r\nHost: "+ip+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));InputStream in=s.getInputStream();byte[] b=new byte[800];int n=in.read(b);if(n<=0)return"";String v=new String(b,0,n,StandardCharsets.ISO_8859_1).replace('\r',' ').replace('\n',' ').trim();return v.length()>150?v.substring(0,150):v;}catch(Exception e){return"";}}
    private String reverseName(String ip){try{String h=InetAddress.getByName(ip).getCanonicalHostName();return h.equals(ip)?"":h;}catch(Exception e){return"";}}
    private String arpMac(String ip){try(BufferedReader br=new BufferedReader(new FileReader("/proc/net/arp"))){String line;while((line=br.readLine())!=null){String[] p=line.trim().split("\\s+");if(p.length>=4&&p[0].equals(ip)&&p[3].matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))return p[3].toUpperCase(Locale.ROOT);}}catch(Exception ignored){}return"";}
    private String smartLanName(String ip,String host,String type,String vendor,String combined){if(!host.isEmpty()&&!host.equals(ip)){String clean=host.replace(".local","").replace(".lan","");if(clean.length()<=28)return clean;}if(combined.contains("chromecast"))return"Chromecast "+lastOctet(ip);if(combined.contains("playstation"))return"PlayStation "+lastOctet(ip);if(combined.contains("xbox"))return"Xbox "+lastOctet(ip);if(!vendor.equals("Non déterminé"))return vendor.replace(" probable","")+" · "+typeLabel(type);return typeLabel(type)+" · "+lastOctet(ip);}
    private boolean containsAny(String value,String... needles){for(String n:needles)if(value.contains(n))return true;return false;}
    private String joinPorts(List<Integer> ports){List<String> out=new ArrayList<>();for(int p:ports)out.add(String.valueOf(p));return out.isEmpty()?"Aucun port courant":String.join(",",out);}

    private String localIp(){try{Enumeration<NetworkInterface> e=NetworkInterface.getNetworkInterfaces();while(e!=null&&e.hasMoreElements()){NetworkInterface n=e.nextElement();if(!n.isUp()||n.isLoopback())continue;Enumeration<InetAddress>a=n.getInetAddresses();while(a.hasMoreElements()){InetAddress x=a.nextElement();if(x instanceof Inet4Address&&x.isSiteLocalAddress())return x.getHostAddress();}}}catch(Exception ignored){}return null;}
    private int lastOctet(String ip){try{return Integer.parseInt(ip.substring(ip.lastIndexOf('.')+1));}catch(Exception e){return 0;}}
    private SpatialDevice selectedDevice(){if(selectedId==null)return null;return devices.get(selectedId);}
    private List<SpatialDevice> bluetoothDevices(){List<SpatialDevice> out=new ArrayList<>();for(SpatialDevice d:devices.values())if("Bluetooth".equals(d.source))out.add(d);out.sort((a,b)->Integer.compare(b.rssi,a.rssi));return out;}
    private List<SpatialDevice> lanDevices(){List<SpatialDevice> out=new ArrayList<>();for(SpatialDevice d:devices.values())if("LAN".equals(d.source))out.add(d);return out;}
    private List<SpatialDevice> devicesInRoom(String room){List<SpatialDevice> out=new ArrayList<>();for(SpatialDevice d:devices.values())if(room.equals(d.room))out.add(d);return out;}
    private int presentCount(){int n=0;long now=System.currentTimeMillis();for(SpatialDevice d:devices.values())if(now-d.lastSeen<120000)n++;return n;}
    private String zone(int rssi){return rssi>=-55?"immédiat":rssi>=-68?"proche":rssi>=-80?"moyen":"éloigné";}
    private int zoneColor(String z){return"immédiat".equals(z)?good:"proche".equals(z)?cyan:"moyen".equals(z)?warn:muted;}
    private String trend(SpatialDevice d){if(d.trail.size()<3)return"Données insuffisantes";int n=d.trail.size();int a=d.trail.get(n-1).rssi,b=d.trail.get(Math.max(0,n-4)).rssi;int delta=a-b;return delta>4?"Rapprochement":delta<-4?"Éloignement":"Stable";}
    private int stability(SpatialDevice d){if(d.trail.size()<3)return 50;double avg=0;for(TrailPoint p:d.trail)avg+=p.rssi;avg/=d.trail.size();double variance=0;for(TrailPoint p:d.trail)variance+=(p.rssi-avg)*(p.rssi-avg);variance/=d.trail.size();return Math.max(15,Math.min(99,(int)(100-Math.sqrt(variance)*7)));}
    private int presenceProbability(SpatialDevice d,String room){if(room.equals(d.room))return d.source.equals("Bluetooth")?Math.min(94,45+(d.rssi+100)):88;return Math.max(2,15-Math.abs(room.hashCode()-d.id.hashCode())%12);}
    private String privacy(String value){if(!prefs.getBoolean("v4_privacy",false))return value;if(value==null)return"—";if(value.contains(".")){int i=value.lastIndexOf('.');return value.substring(0,i)+".•••";}return"••:••:••:"+value.substring(Math.max(0,value.length()-5));}
    private String dateTime(long value){return value<=0?"—":new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.FRANCE).format(new Date(value));}
    private String now(){return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.FRANCE).format(new Date());}
    private String fingerprint(SpatialDevice d){return sha256(d.id+"|"+d.name+"|"+d.type+"|"+d.vendor+"|"+d.firstSeen);}
    private String sha256(String s){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:h)b.append(String.format(Locale.ROOT,"%02x",x));return b.toString();}catch(Exception e){return"00000000000000000000000000000000";}}
    private String typeLabel(String type){switch(type){case"earbuds":return"Écouteurs";case"headphones":return"Casque audio";case"speaker":return"Enceinte";case"watch":return"Montre";case"phone":return"Téléphone";case"gamepad":return"Manette";case"keyboard":return"Clavier";case"mouse":return"Souris";case"tv":return"Télévision";case"console":return"Console";case"printer":return"Imprimante";case"camera":return"Caméra";case"laptop":return"Ordinateur portable";case"desktop":return"PC / NAS";case"car":return"Véhicule";case"tag":return"Balise / objet connecté";case"router":return"Routeur";default:return"Appareil inconnu";}}
    private String qualityLabel(){String q=prefs.getString("v4_quality","ultra");return"eco".equals(q)?"Économie":"balanced".equals(q)?"Équilibrée":"Ultra";}
    private String themeLabel(){String t=prefs.getString("v4_theme","dark");return"light".equals(t)?"Clair technique":"amoled".equals(t)?"AMOLED":"Cyber sombre";}
    private String accentLabel(){String a=prefs.getString("v4_accent","cyan");return"violet".equals(a)?"Violet":"amber".equals(a)?"Ambre":"red".equals(a)?"Rouge":"Cyber cyan";}
    private void log(String event){String old=prefs.getString(pk("history"),"");String line="["+new SimpleDateFormat("dd/MM HH:mm:ss",Locale.FRANCE).format(new Date())+"] "+event+"\n"+(old==null?"":old);if(line.length()>40000)line=line.substring(0,40000);prefs.edit().putString(pk("history"),line).apply();}

    private LinearLayout page(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(14),dp(4),dp(14),dp(24));return p;}
    private ScrollView scroll(View v){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(v,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return s;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(15),dp(15),dp(15));c.setBackground(round(surface,22));c.setElevation(dp(2));return c;}
    private TextView section(String s){return tv(s,17,text,true);}
    private TextView button(String s,boolean primary){TextView b=tv(s,13,primary?Color.rgb(3,23,15):text,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(10),dp(13),dp(10),dp(13));b.setBackground(round(primary?accent:surface2,16));return b;}
    private View row(String l,String v){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.TOP);r.setPadding(0,dp(5),0,dp(5));TextView a=tv(l,12,muted,false);TextView b=tv(v==null?"—":v,12,text,true);b.setGravity(Gravity.END);r.addView(a,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,.43f));r.addView(b,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,.57f));return r;}
    private View signalRow(String tag,String body,int color){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(0,dp(6),0,dp(6));r.addView(tv(tag,9,color,true));r.addView(tv(body,12,text,false));return r;}
    private View toggle(String label,String key){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(8),0,dp(8));r.addView(tv(label,13,text,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));boolean on=prefs.getBoolean(key,false);TextView s=tv(on?"ON":"OFF",9,on?good:muted,true);s.setPadding(dp(8),dp(5),dp(8),dp(5));s.setBackground(round(surface2,11));r.addView(s);r.setOnClickListener(v->{prefs.edit().putBoolean(key,!prefs.getBoolean(key,false)).apply();render();});return r;}
    private View choice(String label,String value,Runnable action){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(8),0,dp(8));r.addView(tv(label,13,text,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));r.addView(tv(value,12,accent,true));r.setOnClickListener(v->action.run());return r;}
    private TextView tv(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(0,1.12f);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    private GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private View gap(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private View gapW(int w){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1));return v;}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==REQ_BLE&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startBle();}
    @Override protected void onDestroy(){try{if(bleScanning&&bleScanner!=null)bleScanner.stopScan(bleCallback);}catch(Exception ignored){}pool.shutdownNow();super.onDestroy();}

    private class UniverseView extends View {
        private final Paint grid=new Paint(1),line=new Paint(1),glow=new Paint(1),label=new Paint(1),particle=new Paint(1);
        private final ScaleGestureDetector scaler;
        private float rotation=.25f,tilt=.78f,zoom=1f,lastX,lastY;
        UniverseView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);grid.setStyle(Paint.Style.STROKE);grid.setStrokeWidth(dp(1));line.setStrokeWidth(dp(1.4f));label.setTextAlign(Paint.Align.CENTER);label.setTypeface(Typeface.DEFAULT_BOLD);glow.setStyle(Paint.Style.STROKE);scaler=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){zoom=Math.max(.55f,Math.min(2.2f,zoom*d.getScaleFactor()));invalidate();return true;}});}
        @Override public boolean onTouchEvent(MotionEvent e){scaler.onTouchEvent(e);if(e.getPointerCount()>1)return true;if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){rotation+=(e.getX()-lastX)*.008f;tilt=Math.max(.35f,Math.min(1.15f,tilt+(e.getY()-lastY)*.004f));lastX=e.getX();lastY=e.getY();invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP){SpatialDevice hit=hitTest(e.getX(),e.getY());if(hit!=null){selectedId=hit.id;tab=1;render();}return true;}return true;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h*.48f;Paint bgp=new Paint();bgp.setShader(new LinearGradient(0,0,0,h,bg,surface,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,bgp);if(prefs.getBoolean("v4_grid",true))drawGrid(c,cx,cy,w,h);drawParticles(c,w,h);List<SpatialDevice> list=new ArrayList<>(devices.values());list.sort(Comparator.comparingDouble(d->depthFor(d)));for(SpatialDevice d:list)drawEntity(c,d,cx,cy,w,h);drawCore(c,cx,cy);if(prefs.getBoolean("v4_animation",true)){rotation+=.0018f;postInvalidateDelayed(frameDelay());}}
        private void drawGrid(Canvas c,float cx,float cy,float w,float h){grid.setColor(Color.argb(70,Color.red(accent),Color.green(accent),Color.blue(accent)));for(int r=1;r<=7;r++){Path p=new Path();float radius=Math.min(w,h)*.07f*r*zoom;for(int i=0;i<=100;i++){double a=Math.PI*2*i/100.0;float x=cx+(float)Math.cos(a+rotation)*radius;float y=cy+(float)Math.sin(a+rotation)*radius*tilt*.52f;if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}c.drawPath(p,grid);}for(int i=0;i<16;i++){double a=rotation+Math.PI*2*i/16.0;float radius=Math.min(w,h)*.49f*zoom;c.drawLine(cx,cy,cx+(float)Math.cos(a)*radius,cy+(float)Math.sin(a)*radius*tilt*.52f,grid);}}
        private void drawParticles(Canvas c,float w,float h){int count="eco".equals(prefs.getString("v4_quality","ultra"))?18:"balanced".equals(prefs.getString("v4_quality","ultra"))?38:70;long t=System.currentTimeMillis();particle.setColor(Color.argb(90,Color.red(accent),Color.green(accent),Color.blue(accent)));for(int i=0;i<count;i++){float x=(float)((i*73.7+t*.018*(1+i%3))%w);float y=(float)((i*47.3+t*.009*(1+i%5))%h);c.drawCircle(x,y,dp(i%3==0?1.5f:1f),particle);}}
        private void drawCore(Canvas c,float cx,float cy){Paint p=new Paint(1);p.setShadowLayer(dp(22),0,0,accent);p.setColor(accent);c.drawCircle(cx,cy,dp(26),p);p.clearShadowLayer();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(Color.argb(190,Color.red(accent),Color.green(accent),Color.blue(accent)));float pulse=dp(36+(System.currentTimeMillis()%1700)/1700f*18);c.drawCircle(cx,cy,pulse,p);label.setColor(text);label.setTextSize(dp(10));c.drawText("SENTRY CORE",cx,cy+dp(4),label);}
        private void drawEntity(Canvas c,SpatialDevice d,float cx,float cy,float w,float h){Point3 point=project(d,cx,cy,w,h);d.screenX=point.x;d.screenY=point.y;d.screenRadius=point.size;line.setColor(Color.argb(d.online?160:70,Color.red(d.source.equals("Bluetooth")?cyan:accent),Color.green(d.source.equals("Bluetooth")?cyan:accent),Color.blue(d.source.equals("Bluetooth")?cyan:accent)));c.drawLine(cx,cy,point.x,point.y,line);if(prefs.getBoolean("v4_trails",true)&&d.trail.size()>1)drawTrail(c,d,point);drawModel(c,d,point.x,point.y,point.size,point.depth,false);if(prefs.getBoolean("v4_labels",true)){label.setColor(d.online?text:muted);label.setTextSize(dp(8+point.depth*2));String n=d.name.length()>16?d.name.substring(0,15)+"…":d.name;c.drawText(n,point.x,point.y+point.size*1.7f,label);label.setTypeface(Typeface.DEFAULT);label.setTextSize(dp(7));c.drawText(d.typeLabel()+" · "+(d.source.equals("Bluetooth")?zone(d.rssi):"LAN"),point.x,point.y+point.size*2.25f,label);label.setTypeface(Typeface.DEFAULT_BOLD);}}
        private void drawTrail(Canvas c,SpatialDevice d,Point3 point){Path p=new Path();int n=d.trail.size();for(int i=Math.max(0,n-12);i<n;i++){TrailPoint tp=d.trail.get(i);float age=(n-1-i)/12f;float x=point.x-dp(45)*age;float y=point.y+(tp.rssi-d.rssi)*dp(.9f)+dp(14)*age;if(i==Math.max(0,n-12))p.moveTo(x,y);else p.lineTo(x,y);}Paint trail=new Paint(1);trail.setStyle(Paint.Style.STROKE);trail.setStrokeWidth(dp(2));trail.setColor(Color.argb(120,Color.red(cyan),Color.green(cyan),Color.blue(cyan)));c.drawPath(p,trail);}
        private Point3 project(SpatialDevice d,float cx,float cy,float w,float h){int hash=Math.abs(d.id.hashCode());double base=(hash%360)*Math.PI/180.0+rotation;float ring=d.source.equals("Bluetooth")?rssiRadius(d.rssi):.62f+(hash%22)/100f;float radius=Math.min(w,h)*.45f*ring*zoom;float depth=(float)((Math.sin(base)+1)/2);float x=cx+(float)Math.cos(base)*radius;float y=cy+(float)Math.sin(base)*radius*tilt*.58f-(depth-.5f)*dp(80)*zoom;float size=dp(15+15*depth)*zoom;return new Point3(x,y,size,depth);}
        private double depthFor(SpatialDevice d){int hash=Math.abs(d.id.hashCode());double a=(hash%360)*Math.PI/180.0+rotation;return Math.sin(a);}
        private float rssiRadius(int rssi){return rssi>=-55?.28f:rssi>=-68?.46f:rssi>=-80?.66f:.84f;}
        private SpatialDevice hitTest(float x,float y){SpatialDevice best=null;float bestD=Float.MAX_VALUE;for(SpatialDevice d:devices.values()){float dx=x-d.screenX,dy=y-d.screenY,dist=(float)Math.sqrt(dx*dx+dy*dy);if(dist<Math.max(dp(30),d.screenRadius*1.8f)&&dist<bestD){best=d;bestD=dist;}}return best;}
        private int frameDelay(){String q=prefs.getString("v4_quality","ultra");return"eco".equals(q)?75:"balanced".equals(q)?45:28;}
    }

    private class ModelView extends View {
        private final SpatialDevice d; private float angle; private float lastX;
        ModelView(Context c,SpatialDevice d){super(c);this.d=d;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){angle+=(e.getX()-lastX)*.012f;lastX=e.getX();invalidate();return true;}return true;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f;Paint grid=new Paint(1);grid.setStyle(Paint.Style.STROKE);grid.setColor(Color.argb(70,Color.red(accent),Color.green(accent),Color.blue(accent)));for(int i=1;i<=4;i++)c.drawOval(cx-dp(45*i),cy-dp(10*i),cx+dp(45*i),cy+dp(10*i),grid);c.save();c.rotate((float)Math.toDegrees(angle),cx,cy);drawModel(c,d,cx,cy,dp(76),.8f,true);c.restore();Paint textPaint=new Paint(1);textPaint.setTextAlign(Paint.Align.CENTER);textPaint.setTypeface(Typeface.DEFAULT_BOLD);textPaint.setTextSize(dp(11));textPaint.setColor(accent);c.drawText(d.typeLabel().toUpperCase(Locale.FRANCE),cx,getHeight()-dp(18),textPaint);if(prefs.getBoolean("v4_animation",true)){angle+=.007f;postInvalidateDelayed(30);}}
    }

    private void drawModel(Canvas c,SpatialDevice d,float x,float y,float s,float depth,boolean large){Paint body=new Paint(1),edge=new Paint(1),glowPaint=new Paint(1);int base=d.online?accent:muted;body.setColor(base);edge.setStyle(Paint.Style.STROKE);edge.setStrokeWidth(Math.max(dp(1),s*.08f));edge.setColor(Color.argb(210,255,255,255));glowPaint.setColor(Color.argb(90,Color.red(base),Color.green(base),Color.blue(base)));glowPaint.setShadowLayer(s*.45f,0,0,base);float k=large?1f:.85f;c.drawCircle(x,y,s*1.08f,glowPaint);glowPaint.clearShadowLayer();switch(d.type){case"earbuds":drawEarbuds(c,x,y,s*k,body,edge);break;case"headphones":drawHeadphones(c,x,y,s*k,body,edge);break;case"speaker":drawSpeaker(c,x,y,s*k,body,edge);break;case"watch":drawWatch(c,x,y,s*k,body,edge);break;case"phone":drawPhone(c,x,y,s*k,body,edge);break;case"gamepad":drawGamepad(c,x,y,s*k,body,edge);break;case"keyboard":drawKeyboard(c,x,y,s*k,body,edge);break;case"mouse":drawMouse(c,x,y,s*k,body,edge);break;case"tv":drawTv(c,x,y,s*k,body,edge);break;case"console":drawConsole(c,x,y,s*k,body,edge);break;case"printer":drawPrinter(c,x,y,s*k,body,edge);break;case"camera":drawCamera(c,x,y,s*k,body,edge);break;case"laptop":drawLaptop(c,x,y,s*k,body,edge);break;case"desktop":drawDesktop(c,x,y,s*k,body,edge);break;case"car":drawCar(c,x,y,s*k,body,edge);break;case"tag":drawTag(c,x,y,s*k,body,edge);break;case"router":drawRouter(c,x,y,s*k,body,edge);break;default:drawUnknown(c,x,y,s*k,body,edge);}}
    private void drawEarbuds(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawOval(x-s*.75f,y-s*.45f,x-s*.1f,y+s*.1f,b);c.drawRect(x-s*.42f,y-s*.05f,x-s*.25f,y+s*.65f,b);c.drawOval(x+s*.1f,y-s*.45f,x+s*.75f,y+s*.1f,b);c.drawRect(x+s*.25f,y-s*.05f,x+s*.42f,y+s*.65f,b);c.drawOval(x-s*.85f,y+s*.45f,x+s*.85f,y+s*.9f,e);}
    private void drawHeadphones(Canvas c,float x,float y,float s,Paint b,Paint e){RectF arc=new RectF(x-s*.75f,y-s*.9f,x+s*.75f,y+s*.7f);e.setStrokeWidth(s*.22f);c.drawArc(arc,190,160,false,e);c.drawRoundRect(x-s*.82f,y-s*.15f,x-s*.38f,y+s*.65f,s*.18f,s*.18f,b);c.drawRoundRect(x+s*.38f,y-s*.15f,x+s*.82f,y+s*.65f,s*.18f,s*.18f,b);}
    private void drawSpeaker(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s*.58f,y-s*.9f,x+s*.58f,y+s*.9f,s*.22f,s*.22f,b);e.setStrokeWidth(s*.07f);c.drawCircle(x,y-s*.35f,s*.27f,e);c.drawCircle(x,y+s*.38f,s*.37f,e);}
    private void drawWatch(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s*.27f,y-s,x+s*.27f,y+s,s*.14f,s*.14f,b);c.drawRoundRect(x-s*.58f,y-s*.56f,x+s*.58f,y+s*.56f,s*.2f,s*.2f,e);c.drawCircle(x,y,s*.14f,b);}
    private void drawPhone(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s*.52f,y-s,x+s*.52f,y+s,s*.2f,s*.2f,b);c.drawRoundRect(x-s*.43f,y-s*.82f,x+s*.43f,y+s*.75f,s*.1f,s*.1f,e);c.drawCircle(x,y+s*.86f,s*.06f,e);}
    private void drawGamepad(Canvas c,float x,float y,float s,Paint b,Paint e){Path p=new Path();p.moveTo(x-s*.85f,y-s*.25f);p.cubicTo(x-s*.95f,y+s*.55f,x-s*.55f,y+s*.85f,x-s*.25f,y+s*.3f);p.lineTo(x+s*.25f,y+s*.3f);p.cubicTo(x+s*.55f,y+s*.85f,x+s*.95f,y+s*.55f,x+s*.85f,y-s*.25f);p.cubicTo(x+s*.65f,y-s*.75f,x-s*.65f,y-s*.75f,x-s*.85f,y-s*.25f);c.drawPath(p,b);c.drawRect(x-s*.48f,y-s*.12f,x-s*.12f,y+s*.02f,e);c.drawRect(x-s*.31f,y-s*.29f,x-s*.29f,y+s*.19f,e);c.drawCircle(x+s*.42f,y-s*.08f,s*.09f,e);c.drawCircle(x+s*.62f,y+s*.12f,s*.09f,e);}
    private void drawKeyboard(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s,y-s*.42f,x+s,y+s*.42f,s*.1f,s*.1f,b);e.setStrokeWidth(s*.025f);for(int r=0;r<3;r++)for(int col=0;col<8;col++){float kx=x-s*.82f+col*s*.235f,ky=y-s*.24f+r*s*.24f;c.drawRect(kx,ky,kx+s*.14f,ky+s*.13f,e);}}
    private void drawMouse(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawOval(x-s*.58f,y-s,x+s*.58f,y+s,b);c.drawLine(x,y-s*.92f,x,y-s*.15f,e);c.drawCircle(x,y-s*.45f,s*.08f,e);}
    private void drawTv(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s,y-s*.72f,x+s,y+s*.55f,s*.1f,s*.1f,b);c.drawRect(x-s*.82f,y-s*.54f,x+s*.82f,y+s*.36f,e);c.drawLine(x,y+s*.55f,x,y+s*.82f,e);c.drawLine(x-s*.38f,y+s*.82f,x+s*.38f,y+s*.82f,e);}
    private void drawConsole(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s*.46f,y-s,x+s*.46f,y+s,s*.15f,s*.15f,b);c.drawLine(x-s*.15f,y-s*.82f,x-s*.15f,y+s*.82f,e);c.drawCircle(x+s*.15f,y-s*.55f,s*.06f,e);}
    private void drawPrinter(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s*.8f,y-s*.35f,x+s*.8f,y+s*.65f,s*.14f,s*.14f,b);c.drawRect(x-s*.55f,y-s*.85f,x+s*.55f,y-s*.1f,e);c.drawRect(x-s*.5f,y+s*.25f,x+s*.5f,y+s*.85f,e);}
    private void drawCamera(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s*.75f,y-s*.52f,x+s*.75f,y+s*.52f,s*.15f,s*.15f,b);c.drawCircle(x,y,s*.38f,e);c.drawCircle(x,y,s*.15f,b);c.drawRect(x-s*.35f,y-s*.78f,x+s*.2f,y-s*.52f,b);}
    private void drawLaptop(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s*.78f,y-s*.9f,x+s*.78f,y+s*.28f,s*.12f,s*.12f,b);c.drawRect(x-s*.62f,y-s*.72f,x+s*.62f,y+s*.12f,e);Path p=new Path();p.moveTo(x-s,y+s*.35f);p.lineTo(x+s,y+s*.35f);p.lineTo(x+s*.75f,y+s*.72f);p.lineTo(x-s*.75f,y+s*.72f);p.close();c.drawPath(p,b);}
    private void drawDesktop(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s*.95f,y-s*.75f,x+s*.3f,y+s*.45f,s*.1f,s*.1f,b);c.drawRect(x-s*.75f,y-s*.58f,x+s*.1f,y+s*.28f,e);c.drawRoundRect(x+s*.46f,y-s*.9f,x+s*.9f,y+s*.75f,s*.08f,s*.08f,b);c.drawCircle(x+s*.68f,y+s*.48f,s*.06f,e);}
    private void drawCar(Canvas c,float x,float y,float s,Paint b,Paint e){Path p=new Path();p.moveTo(x-s,y+s*.2f);p.lineTo(x-s*.7f,y-s*.3f);p.lineTo(x-s*.35f,y-s*.65f);p.lineTo(x+s*.45f,y-s*.65f);p.lineTo(x+s*.8f,y-s*.25f);p.lineTo(x+s,y+s*.2f);p.lineTo(x+s,y+s*.55f);p.lineTo(x-s,y+s*.55f);p.close();c.drawPath(p,b);c.drawCircle(x-s*.58f,y+s*.58f,s*.22f,e);c.drawCircle(x+s*.58f,y+s*.58f,s*.22f,e);}
    private void drawTag(Canvas c,float x,float y,float s,Paint b,Paint e){Path p=new Path();p.moveTo(x,y-s);p.lineTo(x+s*.78f,y-s*.25f);p.lineTo(x+s*.55f,y+s*.75f);p.lineTo(x-s*.55f,y+s*.75f);p.lineTo(x-s*.78f,y-s*.25f);p.close();c.drawPath(p,b);c.drawCircle(x,y-s*.25f,s*.16f,e);}
    private void drawRouter(Canvas c,float x,float y,float s,Paint b,Paint e){c.drawRoundRect(x-s*.85f,y-s*.35f,x+s*.85f,y+s*.45f,s*.12f,s*.12f,b);c.drawLine(x-s*.55f,y-s*.35f,x-s*.75f,y-s,e);c.drawLine(x+s*.55f,y-s*.35f,x+s*.75f,y-s,e);for(int i=0;i<4;i++)c.drawCircle(x-s*.45f+i*s*.3f,y+s*.12f,s*.05f,e);}
    private void drawUnknown(Canvas c,float x,float y,float s,Paint b,Paint e){Path p=new Path();for(int i=0;i<6;i++){double a=-Math.PI/2+i*Math.PI/3;float px=x+(float)Math.cos(a)*s,py=y+(float)Math.sin(a)*s;if(i==0)p.moveTo(px,py);else p.lineTo(px,py);}p.close();c.drawPath(p,b);c.drawCircle(x,y,s*.35f,e);}

    private class RadarView extends View {
        private final Paint grid=new Paint(1),dot=new Paint(1),sweep=new Paint(1),lab=new Paint(1);
        RadarView(Context c){super(c);grid.setStyle(Paint.Style.STROKE);grid.setStrokeWidth(dp(1));grid.setColor(Color.argb(90,Color.red(accent),Color.green(accent),Color.blue(accent)));lab.setTextAlign(Paint.Align.CENTER);lab.setTextSize(dp(9));lab.setColor(text);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,max=Math.min(cx,cy)-dp(24);for(int i=1;i<=4;i++)c.drawCircle(cx,cy,max*i/4f,grid);c.drawLine(cx-max,cy,cx+max,cy,grid);c.drawLine(cx,cy-max,cx,cy+max,grid);double a=(System.currentTimeMillis()%5200)/5200.0*Math.PI*2;sweep.setColor(Color.argb(200,Color.red(accent),Color.green(accent),Color.blue(accent)));sweep.setStrokeWidth(dp(2));c.drawLine(cx,cy,cx+(float)Math.cos(a)*max,cy+(float)Math.sin(a)*max,sweep);dot.setColor(accent);c.drawCircle(cx,cy,dp(8),dot);int i=0;for(SpatialDevice d:bluetoothDevices()){double angle=(Math.abs(d.id.hashCode())%360)*Math.PI/180.0;float f=d.rssi>=-55?.22f:d.rssi>=-68?.44f:d.rssi>=-80?.69f:.9f;float x=cx+(float)Math.cos(angle)*max*f,y=cy+(float)Math.sin(angle)*max*f;dot.setColor(zoneColor(zone(d.rssi)));c.drawCircle(x,y,dp(8),dot);String n=d.name.length()>12?d.name.substring(0,11)+"…":d.name;c.drawText(n,x,y+dp(19),lab);if(++i>=16)break;}if(bleScanning&&prefs.getBoolean("v4_animation",true))postInvalidateDelayed(40);}
    }

    private class RoomMapView extends View {
        private final Paint roomPaint=new Paint(1),border=new Paint(1),labelPaint=new Paint(1),dot=new Paint(1);
        RoomMapView(Context c){super(c);border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(dp(2));border.setColor(Color.argb(150,Color.red(accent),Color.green(accent),Color.blue(accent)));labelPaint.setTextAlign(Paint.Align.CENTER);labelPaint.setTypeface(Typeface.DEFAULT_BOLD);labelPaint.setTextSize(dp(10));labelPaint.setColor(text);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),m=dp(12);String[] names={"Salon","Bureau","Chambre","Cuisine","Garage","Extérieur"};RectF[] rects={new RectF(m,m,w*.55f,h*.46f),new RectF(w*.57f,m,w-m,h*.33f),new RectF(w*.57f,h*.35f,w-m,h*.68f),new RectF(m,h*.48f,w*.38f,h*.78f),new RectF(w*.4f,h*.7f,w-m,h-m),new RectF(m,h*.8f,w*.38f,h-m)};for(int i=0;i<rects.length;i++){roomPaint.setColor(Color.argb(35+i*4,Color.red(accent),Color.green(accent),Color.blue(accent)));c.drawRoundRect(rects[i],dp(12),dp(12),roomPaint);c.drawRoundRect(rects[i],dp(12),dp(12),border);c.drawText(names[i],rects[i].centerX(),rects[i].top+dp(18),labelPaint);List<SpatialDevice> list=devicesInRoom(names[i]);for(int j=0;j<list.size();j++){SpatialDevice d=list.get(j);float x=rects[i].left+dp(22)+(j%4)*dp(34),y=rects[i].top+dp(42)+(j/4)*dp(36);dot.setColor(d.online?good:muted);c.drawCircle(x,y,dp(8),dot);}}}
    }

    private class TimelineGraphView extends View {
        private final Paint grid=new Paint(1),linePaint=new Paint(1),lab=new Paint(1);
        TimelineGraphView(Context c){super(c);grid.setColor(Color.argb(55,Color.red(muted),Color.green(muted),Color.blue(muted)));grid.setStrokeWidth(dp(1));linePaint.setStyle(Paint.Style.STROKE);linePaint.setStrokeWidth(dp(2));lab.setTextSize(dp(9));lab.setColor(muted);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();for(int i=1;i<5;i++)c.drawLine(0,h*i/5f,w,h*i/5f,grid);int colorIndex=0;for(SpatialDevice d:bluetoothDevices()){if(d.trail.size()<2)continue;linePaint.setColor(colorIndex++%2==0?cyan:accent);Path p=new Path();int n=d.trail.size();for(int i=0;i<n;i++){float x=n<=1?0:w*i/(n-1f);float y=h-dp(12)-(Math.max(-100,Math.min(-35,d.trail.get(i).rssi))+100)/65f*(h-dp(30));if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}c.drawPath(p,linePaint);if(colorIndex>=6)break;}c.drawText("-35 dBm",dp(8),dp(14),lab);c.drawText("-100 dBm",dp(8),h-dp(8),lab);}
    }

    private static class LanNode { final String ip; final long latency; LanNode(String ip,long latency){this.ip=ip;this.latency=latency;} }
    private static class TrailPoint { final long time; final int rssi; TrailPoint(long time,int rssi){this.time=time;this.rssi=rssi;} }
    private static class Point3 { final float x,y,size,depth; Point3(float x,float y,float size,float depth){this.x=x;this.y=y;this.size=size;this.depth=depth;} }
    private class SpatialDevice {
        final String id; String name="Unknown device",type="unknown",vendor="Non déterminé",address="—",source="Bluetooth",room="Non assignée";int rssi=-90,confidence=35,seenCount=0;long firstSeen,lastSeen;boolean online;float screenX,screenY,screenRadius;final List<TrailPoint> trail=new ArrayList<>();
        SpatialDevice(String id){this.id=id;}
        String typeLabel(){return V4Activity.this.typeLabel(type);}
        void addTrail(int value,long time){trail.add(new TrailPoint(time,value));while(trail.size()>90)trail.remove(0);}
    }
}
