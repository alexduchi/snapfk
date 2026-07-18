package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
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
import android.graphics.Paint;
import android.graphics.Path;
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
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
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
import java.security.KeyStore;
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

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class V35Activity extends Activity {
    private static final int REQ_BLE = 351;
    private static final int REQ_WIFI = 352;
    private static final int REQ_EXPORT = 353;
    private static final String CHANNEL = "sentry_v35_alerts";
    private static final String VAULT_ALIAS = "sentry_wifi_vault";
    private static final int[] ID_PORTS = {22, 23, 53, 80, 139, 443, 445, 554, 631, 1883, 3389, 5000, 8008, 8009, 8080, 8443, 8883, 9100};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(36);
    private final List<Node> online = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, BlePoint> ble = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, String> ssdp = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<Long> rxSeries = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> txSeries = Collections.synchronizedList(new ArrayList<>());

    private SharedPreferences prefs;
    private FrameLayout content;
    private LinearLayout nav;
    private TextView headerTitle;
    private TextView headerSub;
    private TextView radarStatus;
    private TextView trafficReadout;
    private RadarView radarView;
    private TrafficGraphView trafficGraph;
    private int tab;
    private boolean lanScanning;
    private boolean bleScanning;
    private BluetoothLeScanner bleScanner;
    private long lastRx = -1;
    private long lastTx = -1;
    private int bg, surface, surface2, text, muted, accent, good, warn, bad, cyan;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("sentry_local", MODE_PRIVATE);
        defaults();
        palette();
        createChannel();
        buildShell();
        render();
        startTrafficTicker();
        log("Sentry v3.5 Intelligence Overhaul démarré.");
        if (prefs.getBoolean("auto_scan_start", false)) startLanScan();
    }

    private void defaults() {
        SharedPreferences.Editor e = prefs.edit();
        if (!prefs.contains("theme")) e.putString("theme", "dark");
        if (!prefs.contains("accent")) e.putString("accent", "cyan");
        if (!prefs.contains("profile")) e.putString("profile", "Maison");
        if (!prefs.contains("privacy_mode")) e.putBoolean("privacy_mode", false);
        if (!prefs.contains("compact")) e.putBoolean("compact", false);
        if (!prefs.contains("animation")) e.putBoolean("animation", true);
        if (!prefs.contains("notify_new")) e.putBoolean("notify_new", true);
        if (!prefs.contains("keep_history")) e.putBoolean("keep_history", true);
        if (!prefs.contains("auto_scan_start")) e.putBoolean("auto_scan_start", false);
        if (!prefs.contains("scan_timeout")) e.putInt("scan_timeout", 430);
        if (!prefs.contains("identity_scan")) e.putBoolean("identity_scan", true);
        if (!prefs.contains("show_latency")) e.putBoolean("show_latency", true);
        e.apply();
    }

    private String profile() { return prefs.getString("profile", "Maison"); }
    private String pk(String key) { return "v35_" + profile() + "_" + key; }

    private void palette() {
        String theme = prefs.getString("theme", "dark");
        if ("light".equals(theme)) {
            bg = Color.rgb(235, 241, 239); surface = Color.WHITE; surface2 = Color.rgb(221, 232, 228);
            text = Color.rgb(17, 28, 24); muted = Color.rgb(83, 105, 96);
        } else if ("amoled".equals(theme)) {
            bg = Color.BLACK; surface = Color.rgb(7, 11, 9); surface2 = Color.rgb(19, 31, 26);
            text = Color.rgb(239, 250, 246); muted = Color.rgb(126, 158, 145);
        } else {
            bg = Color.rgb(6, 14, 11); surface = Color.rgb(14, 27, 22); surface2 = Color.rgb(22, 43, 35);
            text = Color.rgb(236, 249, 244); muted = Color.rgb(126, 159, 145);
        }
        String a = prefs.getString("accent", "cyan");
        accent = "violet".equals(a) ? Color.rgb(188, 142, 247) : "amber".equals(a) ? Color.rgb(240, 187, 84) : "red".equals(a) ? Color.rgb(241, 106, 124) : Color.rgb(65, 231, 192);
        cyan = Color.rgb(72, 190, 246); good = Color.rgb(89, 222, 140); warn = Color.rgb(243, 188, 83); bad = Color.rgb(243, 102, 121);
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
        TextView logo = tv("3.5", 14, Color.rgb(4, 22, 16), true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(accent, 14));
        top.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        top.addView(gapW(10));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        headerTitle = tv("COMMAND", 22, text, true);
        headerSub = tv("Intelligence locale autorisée", 12, muted, false);
        names.addView(headerTitle);
        names.addView(headerSub);
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
        nav.setPadding(dp(4), dp(5), dp(4), dp(7));
        String[] labels = {"Command", "Devices", "Wi-Fi", "Radar", "Traffic", "Settings"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = tv(labels[i], 9, muted, true);
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
        String[] titles = {"COMMAND CENTER", "DEVICE INTELLIGENCE", "WI-FI INTELLIGENCE", "PROXIMITY RADAR", "TRAFFIC OBSERVATORY", "SETTINGS"};
        String[] subtitles = {"Vue opérationnelle · " + profile(), "Identités et empreintes locales", "Radio, liaison et infrastructure", "RSSI stable sans saut de page", "Métadonnées agrégées Android", "Configuration globale"};
        headerTitle.setText(titles[tab]);
        headerSub.setText(subtitles[tab]);
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView item = (TextView) nav.getChildAt(i);
            boolean active = i == tab;
            item.setTextColor(active ? Color.rgb(4, 23, 16) : muted);
            item.setBackground(active ? round(accent, 16) : round(Color.TRANSPARENT, 16));
        }
        content.removeAllViews();
        if (tab == 0) content.addView(commandPage());
        else if (tab == 1) content.addView(devicesPage());
        else if (tab == 2) content.addView(wifiPage());
        else if (tab == 3) content.addView(radarPage());
        else if (tab == 4) content.addView(trafficPage());
        else content.addView(settingsPage());
    }

    private View commandPage() {
        LinearLayout page = page();
        int known = known().size();
        int trusted = 0;
        for (String ip : known()) if (prefs.getBoolean(pk("trusted_" + ip), false)) trusted++;
        int unknown = Math.max(0, known - trusted);
        int score = Math.max(28, 100 - unknown * 5 - auditPenalty());

        LinearLayout hero = card();
        hero.addView(tv("LIVE DEFENSE POSTURE", 10, accent, true));
        hero.addView(gap(5));
        LinearLayout scoreLine = new LinearLayout(this);
        scoreLine.setGravity(Gravity.CENTER_VERTICAL);
        scoreLine.addView(tv(String.valueOf(score), 52, score >= 85 ? good : score >= 65 ? warn : bad, true));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        copy.addView(tv(score >= 85 ? "ENVIRONNEMENT STABLE" : score >= 65 ? "ATTENTION REQUISE" : "ANALYSE PRIORITAIRE", 14, text, true));
        copy.addView(tv(online.size() + " actifs · " + known + " identifiés · " + unknown + " non approuvés", 12, muted, false));
        scoreLine.addView(copy);
        hero.addView(scoreLine);
        hero.addView(gap(8));
        hero.addView(progress(score));
        hero.addView(gap(8));
        hero.addView(new PulseView(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));
        page.addView(hero);
        page.addView(gap(10));

        LinearLayout mission = card();
        mission.addView(section("Operation Deep Sight"));
        mission.addView(gap(5));
        mission.addView(tv("Découverte LAN, identité probable, SSDP, latence, historique et changements. Aucun accès forcé.", 12, muted, false));
        TextView launch = button(lanScanning ? "OPÉRATION EN COURS…" : "LANCER L’ANALYSE COMPLÈTE", true);
        launch.setOnClickListener(v -> startLanScan());
        mission.addView(gap(8));
        mission.addView(launch);
        page.addView(mission);
        page.addView(gap(10));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.addView(stat("ONLINE", String.valueOf(online.size()), good), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        stats.addView(gapW(7));
        stats.addView(stat("BLE", String.valueOf(ble.size()), cyan), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        stats.addView(gapW(7));
        stats.addView(stat("SSDP", String.valueOf(ssdp.size()), accent), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(stats);
        page.addView(gap(10));

        LinearLayout mapCard = card();
        mapCard.addView(section("Topologie 3D interactive"));
        mapCard.addView(gap(4));
        mapCard.addView(tv("Glisse horizontalement pour tourner, verticalement pour incliner.", 11, muted, false));
        mapCard.addView(gap(5));
        mapCard.addView(new Topology3DView(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        page.addView(mapCard);
        page.addView(gap(10));

        LinearLayout recent = card();
        recent.addView(section("Flux opérationnel"));
        recent.addView(gap(5));
        recent.addView(tv(historyLines(7), 12, muted, false));
        page.addView(recent);
        return scroll(page);
    }

    private View devicesPage() {
        LinearLayout page = page();
        LinearLayout tools = card();
        tools.addView(section("Inventaire intelligent"));
        tools.addView(gap(5));
        tools.addView(tv("Les noms sont construits avec le hostname, les services visibles, les ports et les annonces locales.", 12, muted, false));
        TextView scan = button(lanScanning ? "IDENTIFICATION EN COURS…" : "ACTUALISER ET IDENTIFIER", true);
        scan.setOnClickListener(v -> startLanScan());
        tools.addView(gap(8));
        tools.addView(scan);
        page.addView(tools);
        page.addView(gap(10));

        List<String> ips = new ArrayList<>(known());
        ips.sort(Comparator.comparingInt(this::lastOctet));
        if (ips.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(section("Aucun appareil mémorisé"));
            empty.addView(gap(5));
            empty.addView(tv("Lance l’analyse complète pour créer l’inventaire.", 12, muted, false));
            page.addView(empty);
        } else {
            for (String ip : ips) {
                page.addView(deviceCard(ip));
                page.addView(gap(8));
            }
        }
        return scroll(page);
    }

    private LinearLayout deviceCard(String ip) {
        String name = displayName(ip);
        String type = prefs.getString(pk("type_" + ip), "Appareil réseau");
        String vendor = prefs.getString(pk("vendor_" + ip), "Constructeur non déterminé");
        boolean active = isOnline(ip);
        int confidence = prefs.getInt(pk("confidence_" + ip), 35);
        LinearLayout c = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(tv(name, 16, text, true));
        info.addView(tv(type + " · " + vendor, 11, muted, false));
        top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView badge = tv(active ? "ONLINE" : "OFFLINE", 9, active ? good : muted, true);
        badge.setPadding(dp(8), dp(5), dp(8), dp(5));
        badge.setBackground(round(surface2, 11));
        top.addView(badge);
        c.addView(top);
        c.addView(gap(5));
        c.addView(row("Adresse", maskIp(ip)));
        c.addView(row("Confiance", confidence + "%"));
        c.addView(row("Services", value(pk("ports_" + ip))));
        c.addView(row("Empreinte", fingerprint(ip).substring(0, 12).toUpperCase(Locale.FRANCE)));
        if (prefs.getBoolean("show_latency", true)) c.addView(row("Latence", latency(ip)));
        c.setOnClickListener(v -> deviceDialog(ip));
        return c;
    }

    private View wifiPage() {
        LinearLayout page = page();
        WifiSnapshot w = wifiSnapshot();
        LinearLayout identity = card();
        identity.addView(section("Identité radio"));
        identity.addView(gap(5));
        identity.addView(row("SSID", w.ssid));
        identity.addView(row("BSSID", maskMac(w.bssid)));
        identity.addView(row("Sécurité", w.security));
        identity.addView(row("Fréquence", w.frequency));
        identity.addView(row("Canal", w.channel));
        identity.addView(row("Signal", w.rssi));
        identity.addView(row("Débit liaison", w.linkSpeed));
        page.addView(identity);
        page.addView(gap(10));

        LinearLayout infra = card();
        infra.addView(section("Infrastructure"));
        infra.addView(gap(5));
        infra.addView(row("Adresse locale", maskIp(localIp())));
        infra.addView(row("Passerelle", maskIp(gateway())));
        infra.addView(row("DNS", prefs.getBoolean("privacy_mode", false) ? "Masqués" : dnsServers()));
        infra.addView(row("VPN", vpnActive() ? "Actif" : "Absent"));
        infra.addView(row("Internet validé", internetValidated() ? "Oui" : "Non"));
        page.addView(infra);
        page.addView(gap(10));

        LinearLayout signal = card();
        signal.addView(section("Analyse de liaison"));
        signal.addView(gap(5));
        signal.addView(tv(wifiAssessment(w), 13, text, false));
        signal.addView(gap(7));
        signal.addView(new WifiSignalView(this, w.rssiValue), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));
        page.addView(signal);
        page.addView(gap(10));

        LinearLayout vault = card();
        vault.addView(section("Coffre Wi-Fi manuel"));
        vault.addView(gap(5));
        vault.addView(tv("Android ne révèle pas automatiquement le mot de passe. Tu peux enregistrer volontairement le tien, chiffré par Android Keystore.", 12, muted, false));
        vault.addView(row("Secret mémorisé", hasWifiSecret(w.ssid) ? "Oui · chiffré" : "Non"));
        TextView manage = button("GÉRER LE SECRET MANUEL", false);
        manage.setOnClickListener(v -> manageWifiSecret(w.ssid));
        vault.addView(gap(7));
        vault.addView(manage);
        page.addView(vault);
        return scroll(page);
    }

    private View radarPage() {
        LinearLayout page = page();
        LinearLayout intro = card();
        intro.addView(section("Radar Bluetooth stable"));
        intro.addView(gap(5));
        intro.addView(tv("La page n’est plus reconstruite à chaque détection. Le scroll reste exactement où tu l’as laissé.", 12, muted, false));
        radarStatus = tv(bleScanning ? "RADAR ACTIF · " + ble.size() + " signaux" : ble.size() + " signaux mémorisés", 11, bleScanning ? good : accent, true);
        intro.addView(gap(6));
        intro.addView(radarStatus);
        TextView start = button(bleScanning ? "ARRÊTER LE RADAR" : "ACTIVER LE RADAR", true);
        start.setOnClickListener(v -> { if (bleScanning) stopBleScan(); else startBleScan(); start.setText(bleScanning ? "ARRÊTER LE RADAR" : "ACTIVER LE RADAR"); });
        intro.addView(gap(8));
        intro.addView(start);
        page.addView(intro);
        page.addView(gap(10));

        radarView = new RadarView(this);
        radarView.setBackground(round(surface, 24));
        page.addView(radarView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(410)));
        page.addView(gap(10));

        LinearLayout list = card();
        list.addView(section("Signaux détectés"));
        list.addView(gap(5));
        synchronized (ble) {
            if (ble.isEmpty()) list.addView(tv("Aucun signal Bluetooth pour le moment.", 12, muted, false));
            else for (BlePoint b : ble.values()) list.addView(signalRow(b.zone.toUpperCase(Locale.FRANCE), b.name + " · " + b.rssi + " dBm · ID " + shortId(b.address), zoneColor(b.zone)));
        }
        page.addView(list);
        return scroll(page);
    }

    private View trafficPage() {
        LinearLayout page = page();
        LinearLayout info = card();
        info.addView(section("Observatoire de trafic"));
        info.addView(gap(5));
        info.addView(tv("Mesures agrégées fournies par Android : volumes et vitesse. Aucun message, mot de passe ou contenu chiffré n’est lu.", 12, muted, false));
        trafficReadout = tv(trafficSummary(), 13, text, true);
        info.addView(gap(7));
        info.addView(trafficReadout);
        page.addView(info);
        page.addView(gap(10));

        trafficGraph = new TrafficGraphView(this);
        trafficGraph.setBackground(round(surface, 24));
        page.addView(trafficGraph, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(270)));
        page.addView(gap(10));

        LinearLayout details = card();
        details.addView(section("Compteurs Android"));
        details.addView(gap(5));
        details.addView(row("Réception globale", formatBytes(TrafficStats.getTotalRxBytes())));
        details.addView(row("Émission globale", formatBytes(TrafficStats.getTotalTxBytes())));
        details.addView(row("Réception Sentry", formatBytes(TrafficStats.getUidRxBytes(android.os.Process.myUid()))));
        details.addView(row("Émission Sentry", formatBytes(TrafficStats.getUidTxBytes(android.os.Process.myUid()))));
        details.addView(row("Type actif", activeTransport()));
        page.addView(details);
        return scroll(page);
    }

    private View settingsPage() {
        LinearLayout page = page();
        LinearLayout profile = card();
        profile.addView(section("Profils"));
        profile.addView(gap(4));
        profile.addView(choice("Environnement actif", profile(), this::chooseProfile));
        page.addView(profile);
        page.addView(gap(10));

        LinearLayout appearance = card();
        appearance.addView(section("Apparence et animations"));
        appearance.addView(gap(4));
        appearance.addView(choice("Thème", themeLabel(), this::chooseTheme));
        appearance.addView(choice("Accent", accentLabel(), this::chooseAccent));
        appearance.addView(toggle("Animations cinématiques", "animation"));
        appearance.addView(toggle("Interface compacte", "compact"));
        appearance.addView(toggle("Masquer les identifiants", "privacy_mode"));
        page.addView(appearance);
        page.addView(gap(10));

        LinearLayout analysis = card();
        analysis.addView(section("Analyses"));
        analysis.addView(gap(4));
        analysis.addView(choice("Délai LAN", prefs.getInt("scan_timeout", 430) + " ms", this::chooseTimeout));
        analysis.addView(toggle("Identification automatique", "identity_scan"));
        analysis.addView(toggle("Afficher la latence", "show_latency"));
        analysis.addView(toggle("Analyse au démarrage", "auto_scan_start"));
        page.addView(analysis);
        page.addView(gap(10));

        LinearLayout data = card();
        data.addView(section("Données et alertes"));
        data.addView(gap(4));
        data.addView(toggle("Notifications locales", "notify_new"));
        data.addView(toggle("Conserver la chronologie", "keep_history"));
        TextView export = button("EXPORTER LE RAPPORT JSON", true);
        export.setOnClickListener(v -> exportReport());
        data.addView(gap(7));
        data.addView(export);
        TextView clear = button("RÉINITIALISER LE PROFIL", false);
        clear.setOnClickListener(v -> confirmReset());
        data.addView(gap(7));
        data.addView(clear);
        page.addView(data);
        page.addView(gap(10));

        LinearLayout about = card();
        about.addView(section("Application"));
        about.addView(gap(4));
        about.addView(row("Version", "3.5.0"));
        about.addView(row("Moteur", "Device Intelligence 2"));
        about.addView(row("Traitement", "Local"));
        about.addView(row("Cloud", "Aucun"));
        about.addView(row("Contenu intercepté", "Aucun"));
        page.addView(about);
        return scroll(page);
    }

    private void startLanScan() {
        if (lanScanning) return;
        String local = localIp();
        if (local == null || local.split("\\.").length != 4) { toast("Connexion IPv4 locale requise."); return; }
        String[] parts = local.split("\\.");
        String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
        Set<String> previous = lastScan();
        online.clear();
        ssdp.clear();
        lanScanning = true;
        log("Analyse LAN v3.5 lancée sur " + prefix + "0/24.");
        render();
        pool.submit(this::discoverSsdp);
        AtomicInteger remaining = new AtomicInteger(254);
        int timeout = prefs.getInt("scan_timeout", 430);
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + i;
            pool.submit(() -> {
                long start = System.nanoTime();
                boolean reachable = false;
                try { reachable = InetAddress.getByName(ip).isReachable(timeout); } catch (Exception ignored) {}
                long latency = Math.max(1, (System.nanoTime() - start) / 1_000_000);
                if (reachable) online.add(new Node(ip, latency));
                if (remaining.decrementAndGet() == 0) ui.post(() -> finishLan(previous));
            });
        }
    }

    private void finishLan(Set<String> previous) {
        Set<String> current = new HashSet<>();
        synchronized (online) {
            for (Node n : online) {
                current.add(n.ip);
                register(n);
                if (prefs.getBoolean("identity_scan", true)) pool.submit(() -> identify(n.ip));
            }
        }
        Set<String> added = new HashSet<>(current); added.removeAll(previous);
        Set<String> gone = new HashSet<>(previous); gone.removeAll(current);
        prefs.edit().putStringSet(pk("last_scan"), current).apply();
        for (String ip : added) log("Nouvelle identité : " + ip + ".");
        for (String ip : gone) log("Identité absente : " + ip + ".");
        lanScanning = false;
        log("Analyse terminée : " + current.size() + " actifs, " + added.size() + " nouveaux.");
        render();
    }

    private void register(Node n) {
        Set<String> set = known();
        boolean first = set.add(n.ip);
        SharedPreferences.Editor e = prefs.edit();
        e.putStringSet(pk("known"), set);
        e.putLong(pk("latency_" + n.ip), n.latency);
        e.putString(pk("last_" + n.ip), now());
        e.putInt(pk("seen_" + n.ip), prefs.getInt(pk("seen_" + n.ip), 0) + 1);
        if (first) {
            e.putString(pk("first_" + n.ip), now());
            e.putString(pk("name_" + n.ip), "Appareil " + lastOctet(n.ip));
        }
        e.apply();
    }

    private void identify(String ip) {
        String host = reverseName(ip);
        String mac = arpMac(ip);
        List<Integer> ports = new ArrayList<>();
        Map<Integer, String> banners = new LinkedHashMap<>();
        for (int port : ID_PORTS) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, port), 260);
                ports.add(port);
            } catch (Exception ignored) {}
        }
        for (int port : ports) {
            if (port == 80 || port == 8080 || port == 8008 || port == 5000) {
                String b = httpBanner(ip, port);
                if (!b.isEmpty()) banners.put(port, b);
            }
        }
        String ssdpInfo = ssdp.getOrDefault(ip, "");
        String combined = (host + " " + banners + " " + ssdpInfo).toLowerCase(Locale.ROOT);
        String type = classify(combined, ports);
        String vendor = vendorFrom(combined);
        String name = smartName(ip, host, type, vendor, combined);
        int confidence = 30 + (!host.isEmpty() ? 15 : 0) + (!mac.isEmpty() ? 10 : 0) + Math.min(20, ports.size() * 3) + (!banners.isEmpty() ? 15 : 0) + (!ssdpInfo.isEmpty() ? 15 : 0);
        String fp = sha256(ip + "|" + mac + "|" + host + "|" + ports + "|" + banners + "|" + ssdpInfo);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(pk("host_" + ip), host);
        e.putString(pk("mac_" + ip), mac);
        e.putString(pk("ports_" + ip), joinPorts(ports));
        e.putString(pk("type_" + ip), type);
        e.putString(pk("vendor_" + ip), vendor);
        e.putString(pk("name_" + ip), name);
        e.putInt(pk("confidence_" + ip), Math.min(98, confidence));
        e.putString(pk("fingerprint_" + ip), fp);
        e.putString(pk("evidence_" + ip), evidence(host, mac, ports, banners, ssdpInfo));
        e.apply();
        log("Identité enrichie : " + ip + " → " + name + " (" + type + ").");
    }

    private String classify(String combined, List<Integer> ports) {
        if (ports.contains(9100) || ports.contains(631) || combined.contains("printer") || combined.contains("ipp")) return "Imprimante";
        if (ports.contains(554) || combined.contains("camera") || combined.contains("onvif") || combined.contains("rtsp")) return "Caméra IP";
        if (combined.contains("chromecast") || combined.contains("google cast") || combined.contains("airplay") || combined.contains("roku")) return "TV / multimédia";
        if (combined.contains("playstation") || combined.contains("xbox") || combined.contains("nintendo")) return "Console";
        if (ports.contains(445) || ports.contains(139)) return "PC / NAS";
        if (ports.contains(22) && (ports.contains(80) || ports.contains(443))) return "Serveur / équipement réseau";
        if (ports.contains(1883) || ports.contains(8883)) return "Objet connecté";
        if (ports.isEmpty()) return "Mobile / appareil filtré";
        return "Appareil réseau";
    }

    private String vendorFrom(String all) {
        String[][] table = {{"samsung","Samsung"},{"apple","Apple"},{"iphone","Apple"},{"google","Google"},{"chromecast","Google"},{"sony","Sony"},{"playstation","Sony"},{"microsoft","Microsoft"},{"xbox","Microsoft"},{"lg","LG"},{"philips","Philips"},{"hewlett","HP"},{" hp","HP"},{"canon","Canon"},{"epson","Epson"},{"brother","Brother"},{"synology","Synology"},{"qnap","QNAP"},{"ubiquiti","Ubiquiti"},{"tp-link","TP-Link"},{"netgear","Netgear"},{"xiaomi","Xiaomi"},{"huawei","Huawei"},{"raspberry","Raspberry Pi"}};
        for (String[] item : table) if (all.contains(item[0])) return item[1] + " probable";
        return "Constructeur non déterminé";
    }

    private String smartName(String ip, String host, String type, String vendor, String combined) {
        if (!host.isEmpty() && !host.equals(ip)) {
            String cleaned = host.replace(".local", "").replace(".lan", "");
            if (cleaned.length() <= 28) return cleaned;
        }
        if (combined.contains("chromecast")) return "Chromecast " + lastOctet(ip);
        if (combined.contains("playstation")) return "PlayStation " + lastOctet(ip);
        if (combined.contains("xbox")) return "Xbox " + lastOctet(ip);
        if (!vendor.startsWith("Constructeur")) return vendor.replace(" probable", "") + " · " + type;
        return type + " · " + lastOctet(ip);
    }

    private String evidence(String host, String mac, List<Integer> ports, Map<Integer, String> banners, String ssdpInfo) {
        List<String> lines = new ArrayList<>();
        if (!host.isEmpty()) lines.add("Hostname : " + host);
        if (!mac.isEmpty()) lines.add("Adresse matérielle observée dans ARP");
        if (!ports.isEmpty()) lines.add("Ports accessibles : " + joinPorts(ports));
        if (!banners.isEmpty()) lines.add("Bannières HTTP publiques détectées");
        if (!ssdpInfo.isEmpty()) lines.add("Annonce SSDP : " + ssdpInfo);
        if (lines.isEmpty()) lines.add("Présence LAN uniquement");
        return String.join("\n", lines);
    }

    private void discoverSsdp() {
        String query = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n";
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(600);
            byte[] payload = query.getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName("239.255.255.250"), 1900));
            long end = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < end) {
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try { socket.receive(packet); } catch (Exception timeout) { continue; }
                String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.ISO_8859_1);
                Map<String, String> headers = parseHeaders(raw);
                String ip = packet.getAddress().getHostAddress();
                String label = headers.getOrDefault("server", "UPnP") + " · " + headers.getOrDefault("st", "service");
                ssdp.put(ip, label);
            }
        } catch (Exception ignored) {}
    }

    private Map<String, String> parseHeaders(String raw) {
        Map<String, String> result = new HashMap<>();
        for (String line : raw.split("\\r?\\n")) {
            int i = line.indexOf(':');
            if (i > 0) result.put(line.substring(0, i).trim().toLowerCase(Locale.ROOT), line.substring(i + 1).trim());
        }
        return result;
    }

    private String httpBanner(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 350);
            socket.setSoTimeout(400);
            socket.getOutputStream().write(("HEAD / HTTP/1.0\r\nHost: " + ip + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[900];
            int n = in.read(buffer);
            if (n <= 0) return "";
            String value = new String(buffer, 0, n, StandardCharsets.ISO_8859_1).replace('\r', ' ').replace('\n', ' ').trim();
            return value.length() > 160 ? value.substring(0, 160) : value;
        } catch (Exception e) { return ""; }
    }

    private void startBleScan() {
        if (bleScanning) return;
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLE);
            return;
        }
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { toast("Active le Bluetooth."); return; }
        bleScanner = adapter.getBluetoothLeScanner();
        if (bleScanner == null) { toast("Scanner Bluetooth indisponible."); return; }
        ble.clear();
        bleScanning = true;
        try { bleScanner.startScan(bleCallback); } catch (SecurityException e) { bleScanning = false; return; }
        if (radarStatus != null) radarStatus.setText("RADAR ACTIF · 0 signaux");
        if (radarView != null) radarView.invalidate();
        ui.postDelayed(this::stopBleScan, 18000);
        log("Radar Bluetooth v3.5 activé.");
    }

    private final ScanCallback bleCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            String address = result.getDevice().getAddress();
            String name = "Appareil BLE";
            try { if (result.getDevice().getName() != null && !result.getDevice().getName().trim().isEmpty()) name = result.getDevice().getName(); } catch (SecurityException ignored) {}
            int rssi = result.getRssi();
            BlePoint old = ble.get(address);
            int smooth = old == null ? rssi : Math.round(old.rssi * 0.72f + rssi * 0.28f);
            String zone = smooth >= -55 ? "immédiat" : smooth >= -68 ? "proche" : smooth >= -80 ? "moyen" : "éloigné";
            ble.put(address, new BlePoint(name, address, smooth, zone));
            ui.post(() -> {
                if (radarStatus != null) radarStatus.setText("RADAR ACTIF · " + ble.size() + " signaux");
                if (radarView != null) radarView.invalidate();
            });
        }
    };

    private void stopBleScan() {
        if (!bleScanning) return;
        bleScanning = false;
        try { if (bleScanner != null) bleScanner.stopScan(bleCallback); } catch (SecurityException ignored) {}
        if (radarStatus != null) radarStatus.setText("RADAR TERMINÉ · " + ble.size() + " signaux");
        if (radarView != null) radarView.invalidate();
        log("Radar arrêté : " + ble.size() + " signaux.");
    }

    private void deviceDialog(String ip) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(5), dp(16), dp(5));
        body.addView(tv(displayName(ip), 21, text, true));
        body.addView(tv("DEVICE PASSPORT · " + fingerprint(ip).substring(0, 16).toUpperCase(Locale.FRANCE), 9, accent, true));
        body.addView(gap(8));
        body.addView(row("Adresse", maskIp(ip)));
        body.addView(row("Hostname", value(pk("host_" + ip))));
        body.addView(row("MAC", maskMac(value(pk("mac_" + ip)))));
        body.addView(row("Constructeur", value(pk("vendor_" + ip))));
        body.addView(row("Catégorie", value(pk("type_" + ip))));
        body.addView(row("Confiance", prefs.getInt(pk("confidence_" + ip), 35) + "%"));
        body.addView(row("Ports", value(pk("ports_" + ip))));
        body.addView(row("Première vue", value(pk("first_" + ip))));
        body.addView(row("Dernière vue", value(pk("last_" + ip))));
        body.addView(row("Observations", String.valueOf(prefs.getInt(pk("seen_" + ip), 0))));
        body.addView(gap(8));
        body.addView(tv("Éléments de preuve", 14, text, true));
        body.addView(tv(value(pk("evidence_" + ip)), 12, muted, false));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        boolean trusted = prefs.getBoolean(pk("trusted_" + ip), false);
        new AlertDialog.Builder(this).setTitle(maskIp(ip)).setView(scroll)
                .setPositiveButton("Réidentifier", (d, w) -> pool.submit(() -> { identify(ip); ui.post(this::render); }))
                .setNeutralButton(trusted ? "Retirer confiance" : "Approuver", (d, w) -> { prefs.edit().putBoolean(pk("trusted_" + ip), !trusted).apply(); render(); })
                .setNegativeButton("Fermer", null).show();
    }

    private WifiSnapshot wifiSnapshot() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, REQ_WIFI);
        }
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo info = wm == null ? null : wm.getConnectionInfo();
        if (info == null) return new WifiSnapshot("Non disponible", "—", "Non déterminée", "—", "—", "—", "—", -100);
        String ssid = info.getSSID();
        if (ssid != null) ssid = ssid.replace("\"", "");
        if (ssid == null || ssid.equals("<unknown ssid>")) ssid = "Autorisation requise / SSID masqué";
        String bssid = info.getBSSID() == null ? "—" : info.getBSSID();
        int frequency = info.getFrequency();
        int rssi = info.getRssi();
        String security = "Non exposée par Android";
        if (Build.VERSION.SDK_INT >= 31) {
            int type = info.getCurrentSecurityType();
            security = securityLabel(type);
        }
        return new WifiSnapshot(ssid, bssid, security, frequency > 0 ? frequency + " MHz" : "—", channelFor(frequency), rssi + " dBm", info.getLinkSpeed() + " Mb/s", rssi);
    }

    private String securityLabel(int type) {
        if (Build.VERSION.SDK_INT < 31) return "Non déterminée";
        if (type == WifiInfo.SECURITY_TYPE_OPEN) return "Ouverte";
        if (type == WifiInfo.SECURITY_TYPE_WEP) return "WEP";
        if (type == WifiInfo.SECURITY_TYPE_PSK) return "WPA/WPA2-PSK";
        if (type == WifiInfo.SECURITY_TYPE_SAE) return "WPA3-SAE";
        if (type == WifiInfo.SECURITY_TYPE_EAP || type == WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE) return "Entreprise";
        if (type == WifiInfo.SECURITY_TYPE_OWE) return "OWE";
        return "Autre / inconnue";
    }

    private String channelFor(int frequency) {
        if (frequency >= 2412 && frequency <= 2484) return String.valueOf((frequency == 2484) ? 14 : (frequency - 2407) / 5);
        if (frequency >= 5000 && frequency <= 5900) return String.valueOf((frequency - 5000) / 5);
        if (frequency >= 5955 && frequency <= 7115) return String.valueOf((frequency - 5950) / 5);
        return "—";
    }

    private String wifiAssessment(WifiSnapshot w) {
        if (w.rssiValue >= -55) return "Signal excellent. La liaison devrait être très stable et adaptée aux flux lourds.";
        if (w.rssiValue >= -67) return "Signal bon. Navigation, appels et vidéo devraient rester stables.";
        if (w.rssiValue >= -75) return "Signal moyen. Les murs ou interférences peuvent provoquer des variations.";
        return "Signal faible. Rapproche-toi du point d’accès ou vérifie le canal et les obstacles.";
    }

    private void manageWifiSecret(String ssid) {
        EditText input = new EditText(this);
        input.setHint("Mot de passe saisi volontairement");
        input.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Coffre Wi-Fi · " + ssid).setMessage("Sentry ne l’extrait pas. La valeur saisie sera chiffrée localement.").setView(input)
                .setPositiveButton("Enregistrer", (d, w) -> { String secret = input.getText().toString(); if (!secret.isEmpty()) { saveWifiSecret(ssid, secret); toast("Secret chiffré localement."); } })
                .setNeutralButton("Supprimer", (d, w) -> { prefs.edit().remove("vault_" + sha256(ssid)).apply(); toast("Secret supprimé."); })
                .setNegativeButton("Annuler", null).show();
    }

    private boolean hasWifiSecret(String ssid) { return prefs.contains("vault_" + sha256(ssid)); }

    private void saveWifiSecret(String ssid, String secret) {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(VAULT_ALIAS)) {
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(VAULT_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
                generator.generateKey();
            }
            SecretKey key = (SecretKey) ks.getKey(VAULT_ALIAS, null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs.edit().putString("vault_" + sha256(ssid), packed).apply();
        } catch (Exception e) { toast("Chiffrement indisponible."); }
    }

    private void startTrafficTicker() {
        ui.post(new Runnable() {
            @Override public void run() {
                long rx = TrafficStats.getTotalRxBytes();
                long tx = TrafficStats.getTotalTxBytes();
                long drx = lastRx < 0 ? 0 : Math.max(0, rx - lastRx);
                long dtx = lastTx < 0 ? 0 : Math.max(0, tx - lastTx);
                lastRx = rx; lastTx = tx;
                appendSeries(rxSeries, drx); appendSeries(txSeries, dtx);
                if (trafficReadout != null) trafficReadout.setText(trafficSummary());
                if (trafficGraph != null) trafficGraph.invalidate();
                ui.postDelayed(this, 1000);
            }
        });
    }

    private void appendSeries(List<Long> list, long value) { synchronized (list) { list.add(value); while (list.size() > 60) list.remove(0); } }
    private String trafficSummary() { long rx = rxSeries.isEmpty() ? 0 : rxSeries.get(rxSeries.size() - 1); long tx = txSeries.isEmpty() ? 0 : txSeries.get(txSeries.size() - 1); return "↓ " + formatRate(rx) + "   ↑ " + formatRate(tx); }

    private void exportReport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "Sentry-v3.5-" + profile() + ".json");
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_EXPORT && result == RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                out.write(report().toString(2).getBytes(StandardCharsets.UTF_8));
                toast("Rapport exporté.");
            } catch (Exception e) { toast("Export impossible."); }
        }
    }

    private JSONObject report() throws Exception {
        JSONObject root = new JSONObject();
        root.put("application", "Sentry 3.5");
        root.put("profile", profile());
        root.put("generated_at", now());
        root.put("local_only", true);
        WifiSnapshot w = wifiSnapshot();
        JSONObject wifi = new JSONObject();
        wifi.put("ssid", w.ssid); wifi.put("bssid", w.bssid); wifi.put("security", w.security); wifi.put("frequency", w.frequency); wifi.put("channel", w.channel); wifi.put("rssi", w.rssi); wifi.put("link_speed", w.linkSpeed);
        root.put("wifi", wifi);
        JSONArray devices = new JSONArray();
        for (String ip : known()) {
            JSONObject d = new JSONObject();
            d.put("name", displayName(ip)); d.put("ip", ip); d.put("hostname", prefs.getString(pk("host_" + ip), "")); d.put("mac", prefs.getString(pk("mac_" + ip), "")); d.put("type", prefs.getString(pk("type_" + ip), "")); d.put("vendor", prefs.getString(pk("vendor_" + ip), "")); d.put("ports", prefs.getString(pk("ports_" + ip), "")); d.put("confidence", prefs.getInt(pk("confidence_" + ip), 35)); d.put("fingerprint", fingerprint(ip));
            devices.put(d);
        }
        root.put("devices", devices);
        root.put("history", prefs.getString(pk("history"), ""));
        return root;
    }

    private void confirmReset() {
        new AlertDialog.Builder(this).setTitle("Réinitialiser le profil ?").setMessage("Les appareils, empreintes et l’historique de " + profile() + " seront supprimés.")
                .setPositiveButton("Réinitialiser", (d, w) -> {
                    SharedPreferences.Editor e = prefs.edit();
                    for (String key : prefs.getAll().keySet()) if (key.startsWith("v35_" + profile() + "_")) e.remove(key);
                    e.apply(); online.clear(); ble.clear(); ssdp.clear(); render();
                }).setNegativeButton("Annuler", null).show();
    }

    private void chooseProfile() {
        String[] values = {"Maison", "Bureau", "Lycée", "Vacances", "Laboratoire"};
        new AlertDialog.Builder(this).setTitle("Profil").setItems(values, (d, w) -> { prefs.edit().putString("profile", values[w]).apply(); online.clear(); ble.clear(); ssdp.clear(); rebuild(); }).show();
    }

    private void chooseTheme() {
        String[] values = {"Cyber sombre", "Clair technique", "AMOLED"};
        new AlertDialog.Builder(this).setTitle("Thème").setItems(values, (d, w) -> { prefs.edit().putString("theme", w == 1 ? "light" : w == 2 ? "amoled" : "dark").apply(); rebuild(); }).show();
    }

    private void chooseAccent() {
        String[] values = {"Cyber cyan", "Violet", "Ambre", "Rouge"};
        new AlertDialog.Builder(this).setTitle("Accent").setItems(values, (d, w) -> { prefs.edit().putString("accent", w == 1 ? "violet" : w == 2 ? "amber" : w == 3 ? "red" : "cyan").apply(); rebuild(); }).show();
    }

    private void chooseTimeout() {
        String[] values = {"Rapide · 280 ms", "Équilibré · 430 ms", "Précis · 800 ms"};
        new AlertDialog.Builder(this).setTitle("Délai LAN").setItems(values, (d, w) -> { prefs.edit().putInt("scan_timeout", w == 0 ? 280 : w == 2 ? 800 : 430).apply(); render(); }).show();
    }

    private int auditPenalty() { int p = 0; try { if (Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1) p += 8; } catch (Exception ignored) {} try { if (Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) p += 4; } catch (Exception ignored) {} return p; }

    private String localIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress a = addresses.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String gateway() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return "—";
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        if (lp != null) for (android.net.RouteInfo r : lp.getRoutes()) if (r.isDefaultRoute() && r.getGateway() != null) return r.getGateway().getHostAddress();
        return "—";
    }

    private String dnsServers() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return "—";
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        List<String> list = new ArrayList<>();
        if (lp != null) for (InetAddress a : lp.getDnsServers()) list.add(a.getHostAddress());
        return list.isEmpty() ? "—" : String.join(", ", list);
    }

    private boolean vpnActive() { ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); if (cm == null) return false; Network n = cm.getActiveNetwork(); NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n); return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN); }
    private boolean internetValidated() { ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); if (cm == null) return false; Network n = cm.getActiveNetwork(); NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n); return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED); }
    private String activeTransport() { ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); if (cm == null) return "—"; Network n = cm.getActiveNetwork(); NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n); if (c == null) return "Déconnecté"; if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi-Fi"; if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Mobile"; if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet"; if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "VPN"; return "Autre"; }

    private Set<String> known() { Set<String> s = prefs.getStringSet(pk("known"), new HashSet<>()); return s == null ? new HashSet<>() : new HashSet<>(s); }
    private Set<String> lastScan() { Set<String> s = prefs.getStringSet(pk("last_scan"), new HashSet<>()); return s == null ? new HashSet<>() : new HashSet<>(s); }
    private boolean isOnline(String ip) { synchronized (online) { for (Node n : online) if (n.ip.equals(ip)) return true; } return false; }
    private int lastOctet(String ip) { try { return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1)); } catch (Exception e) { return 0; } }
    private String reverseName(String ip) { try { String h = InetAddress.getByName(ip).getCanonicalHostName(); return h.equals(ip) ? "" : h; } catch (Exception e) { return ""; } }
    private String arpMac(String ip) { try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"))) { String line; while ((line = br.readLine()) != null) { String[] p = line.trim().split("\\s+"); if (p.length >= 4 && p[0].equals(ip) && p[3].matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) return p[3].toUpperCase(Locale.ROOT); } } catch (Exception ignored) {} return ""; }
    private String displayName(String ip) { return prefs.getString(pk("name_" + ip), "Appareil " + lastOctet(ip)); }
    private String latency(String ip) { long l = prefs.getLong(pk("latency_" + ip), -1); return l < 0 ? "—" : l + " ms"; }
    private String fingerprint(String ip) { String fp = prefs.getString(pk("fingerprint_" + ip), ""); return fp.isEmpty() ? sha256(ip + "|" + prefs.getString(pk("first_" + ip), "")) : fp; }
    private String value(String key) { String v = prefs.getString(key, ""); return v == null || v.trim().isEmpty() ? "Non observé" : v; }
    private String joinPorts(List<Integer> ports) { List<String> out = new ArrayList<>(); for (int p : ports) out.add(String.valueOf(p)); return out.isEmpty() ? "Aucun port courant" : String.join(",", out); }
    private String maskIp(String ip) { if (ip == null || ip.isEmpty()) return "—"; if (!prefs.getBoolean("privacy_mode", false)) return ip; int i = ip.lastIndexOf('.'); return i > 0 ? ip.substring(0, i) + ".•••" : "•••"; }
    private String maskMac(String mac) { if (mac == null || mac.isEmpty()) return "—"; if (!prefs.getBoolean("privacy_mode", false)) return mac; return "••:••:••:" + mac.substring(Math.max(0, mac.length() - 8)); }
    private String shortId(String value) { if (value == null || value.length() < 5) return "—"; return value.substring(Math.max(0, value.length() - 5)).replace(":", ""); }
    private String now() { return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(new Date()); }
    private String sha256(String source) { try { byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)); StringBuilder b = new StringBuilder(); for (byte x : hash) b.append(String.format(Locale.ROOT, "%02x", x)); return b.toString(); } catch (Exception e) { return "00000000000000000000000000000000"; } }

    private void log(String event) {
        if (!prefs.getBoolean("keep_history", true)) return;
        String old = prefs.getString(pk("history"), "");
        String all = "[" + new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(new Date()) + "] " + event + "\n" + (old == null ? "" : old);
        if (all.length() > 30000) all = all.substring(0, 30000);
        prefs.edit().putString(pk("history"), all).apply();
    }

    private String historyLines(int max) { String raw = prefs.getString(pk("history"), ""); if (raw == null || raw.trim().isEmpty()) return "Aucun événement."; String[] lines = raw.split("\\n"); StringBuilder b = new StringBuilder(); for (int i = 0; i < Math.min(max, lines.length); i++) b.append(lines[i]).append('\n'); return b.toString().trim(); }
    private String formatBytes(long value) { if (value < 0) return "Indisponible"; double v = value; String[] units = {"o", "Ko", "Mo", "Go", "To"}; int u = 0; while (v >= 1024 && u < units.length - 1) { v /= 1024; u++; } return String.format(Locale.FRANCE, v >= 100 ? "%.0f %s" : "%.1f %s", v, units[u]); }
    private String formatRate(long value) { return formatBytes(value) + "/s"; }
    private int zoneColor(String zone) { return "immédiat".equals(zone) ? good : "proche".equals(zone) ? cyan : "moyen".equals(zone) ? warn : muted; }
    private String themeLabel() { String t = prefs.getString("theme", "dark"); return "light".equals(t) ? "Clair technique" : "amoled".equals(t) ? "AMOLED" : "Cyber sombre"; }
    private String accentLabel() { String a = prefs.getString("accent", "cyan"); return "violet".equals(a) ? "Violet" : "amber".equals(a) ? "Ambre" : "red".equals(a) ? "Rouge" : "Cyber cyan"; }

    private LinearLayout page() { LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); int pad = prefs.getBoolean("compact", false) ? 10 : 15; p.setPadding(dp(pad), dp(4), dp(pad), dp(24)); return p; }
    private ScrollView scroll(View v) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(v, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); return s; }
    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); int pad = prefs.getBoolean("compact", false) ? 12 : 16; c.setPadding(dp(pad), dp(pad), dp(pad), dp(pad)); c.setBackground(round(surface, 22)); c.setElevation(dp(2)); return c; }
    private LinearLayout stat(String label, String value, int color) { LinearLayout c = card(); c.addView(tv(label, 9, muted, true)); c.addView(gap(4)); c.addView(tv(value, 27, color, true)); return c; }
    private TextView section(String value) { return tv(value, 17, text, true); }
    private View row(String label, String value) { LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.TOP); r.setPadding(0, dp(5), 0, dp(5)); TextView l = tv(label, 12, muted, false); TextView v = tv(value == null ? "—" : value, 12, text, true); v.setGravity(Gravity.END); r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .43f)); r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .57f)); return r; }
    private View signalRow(String tag, String value, int color) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setPadding(0, dp(6), 0, dp(6)); r.addView(tv(tag, 9, color, true)); r.addView(tv(value, 12, text, false)); return r; }
    private View toggle(String label, String key) { LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(8), 0, dp(8)); r.addView(tv(label, 13, text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); boolean on = prefs.getBoolean(key, false); TextView state = tv(on ? "ON" : "OFF", 9, on ? good : muted, true); state.setPadding(dp(8), dp(5), dp(8), dp(5)); state.setBackground(round(surface2, 11)); r.addView(state); r.setOnClickListener(v -> { prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply(); if ("compact".equals(key) || "privacy_mode".equals(key)) rebuild(); else render(); }); return r; }
    private View choice(String label, String value, Runnable action) { LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(8), 0, dp(8)); r.addView(tv(label, 13, text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); r.addView(tv(value, 12, accent, true)); r.setOnClickListener(v -> action.run()); return r; }
    private TextView button(String label, boolean primary) { TextView b = tv(label, 13, primary ? Color.rgb(4, 23, 16) : text, true); b.setGravity(Gravity.CENTER); b.setPadding(dp(10), dp(13), dp(10), dp(13)); b.setBackground(round(primary ? accent : surface2, 16)); return b; }
    private TextView tv(String value, float size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(0, 1.12f); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return t; }
    private GradientDrawable round(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private View gap(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View gapW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private View progress(int value) { FrameLayout f = new FrameLayout(this); f.setBackground(round(surface2, 6)); View b = new View(this); b.setBackground(round(value >= 85 ? good : value >= 65 ? warn : bad, 6)); f.addView(b, new FrameLayout.LayoutParams(0, dp(7))); f.post(() -> { FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) b.getLayoutParams(); lp.width = (int) (f.getWidth() * value / 100f); b.setLayoutParams(lp); }); return f; }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQ_BLE && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startBleScan();
        if (request == REQ_WIFI) render();
    }

    @Override protected void onDestroy() {
        try { if (bleScanning && bleScanner != null) bleScanner.stopScan(bleCallback); } catch (Exception ignored) {}
        pool.shutdownNow();
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Alertes Sentry 3.5", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager n = getSystemService(NotificationManager.class);
            if (n != null) n.createNotificationChannel(c);
        }
    }

    private class Topology3DView extends View {
        private final Paint grid = new Paint(1), line = new Paint(1), node = new Paint(1), label = new Paint(1), glow = new Paint(1);
        private float rotation = 0.25f, tilt = 0.72f, lastX, lastY;
        Topology3DView(Context c) { super(c); grid.setStyle(Paint.Style.STROKE); grid.setStrokeWidth(dp(1)); grid.setColor(Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent))); line.setStrokeWidth(dp(1.4f)); line.setColor(Color.argb(170, Color.red(accent), Color.green(accent), Color.blue(accent))); label.setTextSize(dp(9)); label.setTextAlign(Paint.Align.CENTER); label.setColor(text); glow.setStyle(Paint.Style.STROKE); glow.setStrokeWidth(dp(2)); }
        @Override public boolean onTouchEvent(MotionEvent e) { if (e.getAction() == MotionEvent.ACTION_DOWN) { lastX = e.getX(); lastY = e.getY(); return true; } if (e.getAction() == MotionEvent.ACTION_MOVE) { rotation += (e.getX() - lastX) * .008f; tilt = Math.max(.35f, Math.min(1.05f, tilt + (e.getY() - lastY) * .004f)); lastX = e.getX(); lastY = e.getY(); invalidate(); return true; } return true; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float cx = getWidth()/2f, cy = getHeight()*.55f, radius = Math.min(getWidth(), getHeight())*.37f;
            for (int ring=1; ring<=4; ring++) { Path p = new Path(); for (int i=0;i<=80;i++) { double a=Math.PI*2*i/80.0; float x=cx+(float)Math.cos(a)*radius*ring/4f; float y=cy+(float)Math.sin(a+rotation)*radius*ring/4f*tilt; if(i==0)p.moveTo(x,y);else p.lineTo(x,y);} c.drawPath(p,grid); }
            node.setColor(accent); c.drawCircle(cx,cy,dp(24),node); label.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("GATEWAY",cx,cy+dp(4),label);
            List<String> ips=new ArrayList<>(known()); ips.sort(Comparator.comparingInt(V35Activity.this::lastOctet)); int count=Math.min(16,ips.size());
            for(int i=0;i<count;i++){String ip=ips.get(i);double a=rotation+Math.PI*2*i/Math.max(1,count);float depth=(float)((Math.sin(a)+1)/2);float x=cx+(float)Math.cos(a)*radius;float y=cy+(float)Math.sin(a)*radius*tilt;float size=dp(10+8*depth);line.setAlpha((int)(90+140*depth));c.drawLine(cx,cy,x,y,line);node.setColor(isOnline(ip)?good:prefs.getBoolean(pk("trusted_"+ip),false)?cyan:muted);c.drawCircle(x,y,size,node);label.setTextSize(dp(8+2*depth));String n=displayName(ip);if(n.length()>14)n=n.substring(0,13)+"…";c.drawText(n,x,y+size+dp(12),label);}
            if(prefs.getBoolean("animation",true)){rotation+=.0025f;postInvalidateDelayed(32);}
        }
    }

    private class RadarView extends View {
        private final Paint grid = new Paint(1), dot = new Paint(1), label = new Paint(1), sweep = new Paint(1);
        RadarView(Context c) { super(c); grid.setStyle(Paint.Style.STROKE); grid.setStrokeWidth(dp(1)); grid.setColor(Color.argb(85, Color.red(accent), Color.green(accent), Color.blue(accent))); label.setTextSize(dp(10)); label.setTextAlign(Paint.Align.CENTER); label.setColor(text); sweep.setStrokeWidth(dp(2)); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float cx=getWidth()/2f,cy=getHeight()/2f,max=Math.min(cx,cy)-dp(25);for(int i=1;i<=4;i++)c.drawCircle(cx,cy,max*i/4f,grid);c.drawLine(cx-max,cy,cx+max,cy,grid);c.drawLine(cx,cy-max,cx,cy+max,grid);
            double angle=(System.currentTimeMillis()%5500)/5500.0*Math.PI*2;sweep.setColor(Color.argb(190,Color.red(accent),Color.green(accent),Color.blue(accent)));c.drawLine(cx,cy,cx+(float)Math.cos(angle)*max,cy+(float)Math.sin(angle)*max,sweep);dot.setColor(accent);c.drawCircle(cx,cy,dp(8),dot);c.drawText("SENTRY",cx,cy+dp(24),label);
            synchronized(ble){int i=0;for(BlePoint b:ble.values()){double a=(Math.abs(b.address.hashCode())%360)*Math.PI/180.0;float f=b.rssi>=-55?.22f:b.rssi>=-68?.44f:b.rssi>=-80?.69f:.91f;float x=cx+(float)Math.cos(a)*max*f,y=cy+(float)Math.sin(a)*max*f;dot.setColor(zoneColor(b.zone));c.drawCircle(x,y,dp(8),dot);String n=b.name.length()>12?b.name.substring(0,11)+"…":b.name;c.drawText(n,x,y+dp(20),label);if(++i>=14)break;}}
            if(bleScanning&&prefs.getBoolean("animation",true))postInvalidateDelayed(40);
        }
    }

    private class PulseView extends View {
        private final Paint axis = new Paint(1), wave = new Paint(1);
        PulseView(Context c) { super(c); axis.setColor(Color.argb(65, Color.red(muted), Color.green(muted), Color.blue(muted))); wave.setColor(accent); wave.setStrokeWidth(dp(2)); wave.setStyle(Paint.Style.STROKE); }
        @Override protected void onDraw(Canvas c) { super.onDraw(c);float w=getWidth(),h=getHeight();for(int i=1;i<6;i++)c.drawLine(w*i/6f,0,w*i/6f,h,axis);c.drawLine(0,h/2,w,h/2,axis);Path p=new Path();float phase=(System.currentTimeMillis()%3000)/35f;for(int x=0;x<=w;x+=4){float y=h/2f+(float)Math.sin((x+phase)*.06)*5;int m=(int)((x+phase)%145);if(m>52&&m<57)y-=24;else if(m>=57&&m<63)y+=29;else if(m>=63&&m<70)y-=14;if(x==0)p.moveTo(x,y);else p.lineTo(x,y);}c.drawPath(p,wave);if(prefs.getBoolean("animation",true))postInvalidateDelayed(40); }
    }

    private class WifiSignalView extends View {
        private final Paint p = new Paint(1); private final int rssi;
        WifiSignalView(Context c,int rssi){super(c);this.rssi=rssi;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(10));p.setStrokeCap(Paint.Cap.ROUND);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,base=getHeight()-dp(18);for(int i=1;i<=4;i++){p.setColor(i<=bars()?accent:surface2);float radius=dp(18+i*14);c.drawArc(cx-radius,base-radius,cx+radius,base+radius,205,130,false,p);}p.setStyle(Paint.Style.FILL);p.setColor(accent);c.drawCircle(cx,base,dp(7),p);p.setStyle(Paint.Style.STROKE);}
        private int bars(){if(rssi>=-55)return 4;if(rssi>=-67)return 3;if(rssi>=-75)return 2;return 1;}
    }

    private class TrafficGraphView extends View {
        private final Paint grid = new Paint(1), rxPaint = new Paint(1), txPaint = new Paint(1), label = new Paint(1);
        TrafficGraphView(Context c){super(c);grid.setColor(Color.argb(55,Color.red(muted),Color.green(muted),Color.blue(muted)));grid.setStrokeWidth(dp(1));rxPaint.setColor(cyan);rxPaint.setStrokeWidth(dp(2));rxPaint.setStyle(Paint.Style.STROKE);txPaint.setColor(accent);txPaint.setStrokeWidth(dp(2));txPaint.setStyle(Paint.Style.STROKE);label.setColor(muted);label.setTextSize(dp(10));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();for(int i=1;i<5;i++)c.drawLine(0,h*i/5f,w,h*i/5f,grid);c.drawText("RX",dp(12),dp(18),label);c.drawText("TX",dp(45),dp(18),label);long max=1; synchronized(rxSeries){for(long v:rxSeries)max=Math.max(max,v);} synchronized(txSeries){for(long v:txSeries)max=Math.max(max,v);}drawSeries(c,rxSeries,rxPaint,max,w,h);drawSeries(c,txSeries,txPaint,max,w,h);}
        private void drawSeries(Canvas c,List<Long> data,Paint paint,long max,float w,float h){Path p=new Path();synchronized(data){int n=data.size();for(int i=0;i<n;i++){float x=n<=1?0:w*i/(n-1f);float y=h-dp(12)-(h-dp(35))*data.get(i)/Math.max(1f,max);if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}}c.drawPath(p,paint);}
    }

    private static class Node { final String ip; final long latency; Node(String ip,long latency){this.ip=ip;this.latency=latency;} }
    private static class BlePoint { final String name,address,zone; final int rssi; BlePoint(String name,String address,int rssi,String zone){this.name=name;this.address=address;this.rssi=rssi;this.zone=zone;} }
    private static class WifiSnapshot { final String ssid,bssid,security,frequency,channel,rssi,linkSpeed; final int rssiValue; WifiSnapshot(String ssid,String bssid,String security,String frequency,String channel,String rssi,String linkSpeed,int rssiValue){this.ssid=ssid;this.bssid=bssid;this.security=security;this.frequency=frequency;this.channel=channel;this.rssi=rssi;this.linkSpeed=linkSpeed;this.rssiValue=rssiValue;} }
}
