package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
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
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
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
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class V3Activity extends Activity {
    private static final int REQ_BLE = 301;
    private static final int REQ_NOTIF = 302;
    private static final int REQ_EXPORT = 303;
    private static final String CHANNEL = "sentry_v3_alerts";
    private static final int[] COMMON_PORTS = {21,22,23,25,53,80,110,139,143,443,445,554,631,1883,3000,3389,5000,5353,8000,8080,8443,8883,9100};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(36);
    private final List<DeviceState> online = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, BleState> ble = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, ServiceHit> services = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, String> ssdp = Collections.synchronizedMap(new LinkedHashMap<>());

    private SharedPreferences prefs;
    private FrameLayout content;
    private LinearLayout nav;
    private TextView headerTitle;
    private TextView headerSub;
    private int tab;
    private boolean lanScanning;
    private boolean deepScanning;
    private boolean bleScanning;
    private boolean serviceScanning;
    private BluetoothLeScanner bleScanner;
    private NsdManager nsd;
    private NsdManager.DiscoveryListener activeNsd;
    private WifiManager.MulticastLock multicastLock;

    private int bg, surface, surface2, text, muted, accent, good, warn, bad, cyan;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("sentry_local", MODE_PRIVATE);
        defaults();
        palette();
        createChannel();
        buildShell();
        render();
        log("Sentry v3.0 Cyber Operations Center démarré.");
        if (prefs.getBoolean("auto_scan_start", false)) startOperation(false);
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
        if (!prefs.contains("show_latency")) e.putBoolean("show_latency", true);
        if (!prefs.contains("scan_timeout")) e.putInt("scan_timeout", 450);
        if (!prefs.contains("deep_ports")) e.putBoolean("deep_ports", true);
        if (!prefs.contains("service_discovery")) e.putBoolean("service_discovery", true);
        e.apply();
    }

    private String profile() { return prefs.getString("profile", "Maison"); }
    private String pk(String key) { return "v3_" + profile() + "_" + key; }

    private void palette() {
        String theme = prefs.getString("theme", "dark");
        if ("light".equals(theme)) {
            bg = Color.rgb(236, 241, 239); surface = Color.WHITE; surface2 = Color.rgb(222, 232, 228);
            text = Color.rgb(18, 27, 24); muted = Color.rgb(88, 105, 98);
        } else if ("amoled".equals(theme)) {
            bg = Color.BLACK; surface = Color.rgb(8, 11, 10); surface2 = Color.rgb(20, 29, 26);
            text = Color.rgb(241, 248, 245); muted = Color.rgb(132, 155, 145);
        } else {
            bg = Color.rgb(8, 15, 13); surface = Color.rgb(16, 27, 23); surface2 = Color.rgb(25, 43, 36);
            text = Color.rgb(235, 247, 242); muted = Color.rgb(129, 157, 145);
        }
        String a = prefs.getString("accent", "cyan");
        accent = "violet".equals(a) ? Color.rgb(188, 145, 247) : "amber".equals(a) ? Color.rgb(239, 188, 92) : "red".equals(a) ? Color.rgb(238, 112, 125) : Color.rgb(75, 224, 194);
        cyan = Color.rgb(85, 196, 244); good = Color.rgb(93, 221, 143); warn = Color.rgb(242, 190, 88); bad = Color.rgb(242, 105, 120);
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
        head.setPadding(dp(16), dp(13), dp(16), dp(9));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = tv("S3", 15, Color.rgb(7, 22, 17), true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(accent, 14));
        top.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        top.addView(gapW(11));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        headerTitle = tv("COMMAND CENTER", 22, text, true);
        names.addView(headerTitle);
        headerSub = tv("Analyse locale autorisée", 12, muted, false);
        names.addView(headerSub);
        top.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView profile = tv(profile().toUpperCase(Locale.FRANCE), 10, accent, true);
        profile.setPadding(dp(9), dp(6), dp(9), dp(6));
        profile.setBackground(round(surface2, 13));
        profile.setOnClickListener(v -> chooseProfile());
        top.addView(profile);
        head.addView(top);
        root.addView(head);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(5), dp(6), dp(5), dp(7));
        String[] labels = {"Command", "Réseau", "Intel", "Radar", "Journal"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = tv(labels[i], 10, muted, true);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2), dp(12), dp(2), dp(12));
            item.setOnClickListener(v -> { tab = index; render(); });
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(nav);
        setContentView(root);
    }

    private void rebuild() { palette(); buildShell(); render(); }

    private void render() {
        String[] titles = {"COMMAND CENTER", "NETWORK TOPOLOGY", "DEVICE INTELLIGENCE", "PROXIMITY RADAR", "OPERATIONS LOG"};
        String[] subs = {"Vue SOC locale · profil " + profile(), "Carte logique et inventaire", "Empreintes, services et anomalies", "Distance Bluetooth estimée", "Chronologie et configuration"};
        headerTitle.setText(titles[tab]);
        headerSub.setText(subs[tab]);
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView item = (TextView) nav.getChildAt(i);
            boolean active = i == tab;
            item.setTextColor(active ? Color.rgb(6, 23, 17) : muted);
            item.setBackground(active ? round(accent, 18) : round(Color.TRANSPARENT, 18));
        }
        content.removeAllViews();
        if (tab == 0) content.addView(commandPage());
        else if (tab == 1) content.addView(networkPage());
        else if (tab == 2) content.addView(intelPage());
        else if (tab == 3) content.addView(radarPage());
        else content.addView(logPage());
    }

    private View commandPage() {
        LinearLayout page = page();
        int known = known().size();
        int trusted = trustedCount();
        int anomalies = anomalyCount();
        int score = Math.max(24, 100 - (known - trusted) * 4 - anomalies * 8 - auditIssues() * 6);

        LinearLayout hero = card();
        hero.addView(tv("LIVE SECURITY POSTURE", 11, accent, true));
        hero.addView(gap(6));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView scoreView = tv(score + "", 53, score >= 85 ? good : score >= 65 ? warn : bad, true);
        line.addView(scoreView);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(14), 0, 0, 0);
        details.addView(tv(score >= 85 ? "ENVIRONNEMENT STABLE" : score >= 65 ? "SURVEILLANCE CONSEILLÉE" : "ANALYSE REQUISE", 14, text, true));
        details.addView(tv(online.size() + " actifs · " + known + " identités · " + anomalies + " anomalie(s)", 12, muted, false));
        line.addView(details);
        hero.addView(line);
        hero.addView(gap(9));
        hero.addView(progress(score));
        hero.addView(gap(8));
        PulseView pulse = new PulseView(this);
        hero.addView(pulse, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        page.addView(hero);
        page.addView(gap(10));

        LinearLayout op = card();
        op.addView(section("Opération complète"));
        op.addView(gap(5));
        op.addView(tv("LAN + comparaison + services SSDP/mDNS + Bluetooth + analyse de changements.", 13, muted, false));
        TextView launch = button(lanScanning || serviceScanning || bleScanning ? "OPÉRATION EN COURS…" : "LANCER OPERATION SENTINEL", true);
        launch.setOnClickListener(v -> startOperation(true));
        op.addView(gap(9));
        op.addView(launch);
        page.addView(op);
        page.addView(gap(10));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(statCard("ACTIFS", String.valueOf(online.size()), good), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        grid.addView(gapW(8));
        grid.addView(statCard("SERVICES", String.valueOf(services.size() + ssdp.size()), cyan), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        grid.addView(gapW(8));
        grid.addView(statCard("BLE", String.valueOf(ble.size()), accent), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(grid);
        page.addView(gap(10));

        LinearLayout alerts = card();
        alerts.addView(section("Anomalies prioritaires"));
        alerts.addView(gap(6));
        List<String> items = currentAnomalies();
        if (items.isEmpty()) alerts.addView(tv("Aucun changement inhabituel détecté.", 13, good, false));
        else for (String s : items) alerts.addView(signalRow("ALERTE", s, bad));
        page.addView(alerts);
        page.addView(gap(10));

        LinearLayout recent = card();
        recent.addView(section("Derniers événements"));
        recent.addView(gap(6));
        recent.addView(tv(historyLines(6), 12, muted, false));
        page.addView(recent);
        return scroll(page);
    }

    private View networkPage() {
        LinearLayout page = page();
        GraphView graph = new GraphView(this);
        graph.setBackground(round(surface, 24));
        page.addView(graph, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(345)));
        page.addView(gap(9));
        TextView refresh = button(lanScanning ? "CARTOGRAPHIE EN COURS…" : "ACTUALISER LA TOPOLOGIE", true);
        refresh.setOnClickListener(v -> startLanScan());
        page.addView(refresh);
        page.addView(gap(10));

        List<String> ips = new ArrayList<>(known());
        ips.sort(Comparator.comparingInt(this::lastOctet));
        if (ips.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(section("Aucune identité réseau"));
            empty.addView(gap(6));
            empty.addView(tv("Lance une opération pour construire la carte du réseau local.", 13, muted, false));
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
        String name = prefs.getString(pk("name_" + ip), defaultName(ip));
        boolean active = isOnline(ip);
        boolean trusted = prefs.getBoolean(pk("trusted_" + ip), false);
        String type = prefs.getString(pk("type_" + ip), inferType(ip));
        int risk = riskFor(ip);
        LinearLayout card = card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout id = new LinearLayout(this);
        id.setOrientation(LinearLayout.VERTICAL);
        id.addView(tv(name, 16, text, true));
        id.addView(tv(type + " · " + confidence(ip) + "% de confiance", 11, muted, false));
        top.addView(id, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView state = tv(active ? "ONLINE" : "OFFLINE", 9, active ? good : muted, true);
        state.setPadding(dp(8), dp(5), dp(8), dp(5));
        state.setBackground(round(surface2, 11));
        top.addView(state);
        card.addView(top);
        card.addView(gap(6));
        card.addView(row("Adresse", mask(ip)));
        card.addView(row("Empreinte", fingerprint(ip).substring(0, 11).toUpperCase(Locale.FRANCE)));
        card.addView(row("Risque", risk < 25 ? "Faible" : risk < 55 ? "Modéré" : "Élevé"));
        card.addView(row("Confiance", trusted ? "Approuvé" : "Non approuvé"));
        if (prefs.getBoolean("show_latency", true)) card.addView(row("Latence", latency(ip)));
        card.setOnClickListener(v -> passport(ip));
        return card;
    }

    private View intelPage() {
        LinearLayout page = page();
        LinearLayout mission = card();
        mission.addView(section("Device Intelligence Engine"));
        mission.addView(gap(6));
        mission.addView(tv("Croisement des services annoncés, ports accessibles, bannières publiques, certificats, identité réseau et historique.", 13, muted, false));
        TextView discover = button(serviceScanning ? "DÉCOUVERTE EN COURS…" : "DÉCOUVRIR LES SERVICES", true);
        discover.setOnClickListener(v -> startServiceDiscovery());
        mission.addView(gap(9));
        mission.addView(discover);
        page.addView(mission);
        page.addView(gap(10));

        LinearLayout net = card();
        net.addView(section("Infrastructure active"));
        net.addView(gap(5));
        NetInfo n = netInfo();
        net.addView(row("Transport", n.transport));
        net.addView(row("Adresse locale", mask(n.local)));
        net.addView(row("Passerelle", mask(n.gateway)));
        net.addView(row("DNS", prefs.getBoolean("privacy_mode", false) ? "Masqué" : n.dns));
        net.addView(row("VPN", n.vpn ? "Actif" : "Absent"));
        page.addView(net);
        page.addView(gap(10));

        LinearLayout found = card();
        found.addView(section("Services et annonces"));
        found.addView(gap(5));
        if (services.isEmpty() && ssdp.isEmpty()) found.addView(tv("Aucune annonce capturée. Lance la découverte.", 13, muted, false));
        synchronized (services) {
            for (ServiceHit s : services.values()) found.addView(signalRow("mDNS", s.name + " · " + s.type + " · " + mask(s.host) + ":" + s.port, cyan));
        }
        synchronized (ssdp) {
            for (Map.Entry<String, String> e : ssdp.entrySet()) found.addView(signalRow("SSDP", e.getValue(), accent));
        }
        page.addView(found);
        page.addView(gap(10));

        LinearLayout audit = card();
        audit.addView(section("Audit du terminal"));
        audit.addView(gap(5));
        audit.addView(row("Verrouillage", isSecure() ? "Actif" : "Absent"));
        audit.addView(row("Débogage USB", adbEnabled() ? "Actif" : "Désactivé"));
        audit.addView(row("Options développeur", developerEnabled() ? "Actives" : "Désactivées"));
        audit.addView(row("Correctif Android", Build.VERSION.SECURITY_PATCH));
        audit.addView(row("Version Android", Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT));
        audit.addView(row("Modèle local", Build.MANUFACTURER + " " + Build.MODEL));
        page.addView(audit);
        page.addView(gap(10));

        LinearLayout targets = card();
        targets.addView(section("Analyses approfondies"));
        targets.addView(gap(5));
        List<String> ips = new ArrayList<>(known());
        ips.sort(Comparator.comparingInt(this::lastOctet));
        if (ips.isEmpty()) targets.addView(tv("Aucune cible locale mémorisée.", 13, muted, false));
        for (String ip : ips) {
            TextView b = button("ANALYSER · " + prefs.getString(pk("name_" + ip), mask(ip)), false);
            b.setOnClickListener(v -> deepScan(ip, true));
            targets.addView(b);
            targets.addView(gap(7));
        }
        page.addView(targets);
        return scroll(page);
    }

    private View radarPage() {
        LinearLayout page = page();
        LinearLayout status = card();
        status.addView(section("Radar de proximité expérimental"));
        status.addView(gap(5));
        status.addView(tv("Les anneaux représentent des zones RSSI, pas une position métrique exacte. Les murs et l’orientation peuvent déplacer les points.", 12, muted, false));
        TextView scan = button(bleScanning ? "RADAR ACTIF…" : "ACTIVER LE RADAR BLE", true);
        scan.setOnClickListener(v -> startBleScan());
        status.addView(gap(8));
        status.addView(scan);
        page.addView(status);
        page.addView(gap(10));

        RadarView radar = new RadarView(this);
        radar.setBackground(round(surface, 24));
        page.addView(radar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(390)));
        page.addView(gap(10));

        LinearLayout list = card();
        list.addView(section("Signaux observés"));
        list.addView(gap(5));
        synchronized (ble) {
            if (ble.isEmpty()) list.addView(tv("Aucun appareil Bluetooth détecté.", 13, muted, false));
            else for (BleState b : ble.values()) {
                list.addView(signalRow(b.zone.toUpperCase(Locale.FRANCE), b.name + " · " + b.rssi + " dBm · ID " + shortId(b.address), zoneColor(b.zone)));
            }
        }
        page.addView(list);
        return scroll(page);
    }

    private View logPage() {
        LinearLayout page = page();
        LinearLayout log = card();
        log.addView(section("Chronologie opérationnelle"));
        log.addView(gap(5));
        String history = prefs.getString(pk("history"), "");
        if (history == null || history.trim().isEmpty()) log.addView(tv("Aucun événement enregistré.", 13, muted, false));
        else {
            String[] lines = history.split("\\n");
            for (int i = 0; i < Math.min(lines.length, 50); i++) {
                String line = lines[i];
                if (line.trim().isEmpty()) continue;
                int cut = line.indexOf("] ");
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(0, dp(6), 0, dp(6));
                item.addView(tv(cut > 0 ? line.substring(1, cut) : "EVENT", 10, accent, true));
                item.addView(tv(cut > 0 ? line.substring(cut + 2) : line, 13, text, false));
                log.addView(item);
            }
        }
        page.addView(log);
        page.addView(gap(10));

        LinearLayout config = card();
        config.addView(section("Configuration globale"));
        config.addView(gap(4));
        config.addView(choice("Profil", profile(), this::chooseProfile));
        config.addView(choice("Thème", themeLabel(), this::chooseTheme));
        config.addView(choice("Accent", accentLabel(), this::chooseAccent));
        config.addView(choice("Délai LAN", prefs.getInt("scan_timeout", 450) + " ms", this::chooseTimeout));
        config.addView(toggle("Mode compact", "compact"));
        config.addView(toggle("Masquer les adresses", "privacy_mode"));
        config.addView(toggle("Animations", "animation"));
        config.addView(toggle("Analyse au démarrage", "auto_scan_start"));
        config.addView(toggle("Afficher la latence", "show_latency"));
        config.addView(toggle("Scanner les ports courants", "deep_ports"));
        config.addView(toggle("Découverte de services", "service_discovery"));
        config.addView(toggle("Notifier les nouveaux appareils", "notify_new"));
        config.addView(toggle("Conserver la chronologie", "keep_history"));
        page.addView(config);
        page.addView(gap(10));

        LinearLayout actions = card();
        actions.addView(section("Rapports et contrôle"));
        actions.addView(gap(6));
        TextView export = button("EXPORTER LE DOSSIER D’INTELLIGENCE", true);
        export.setOnClickListener(v -> exportReport());
        actions.addView(export);
        actions.addView(gap(7));
        TextView notif = button("AUTORISER LES NOTIFICATIONS", false);
        notif.setOnClickListener(v -> requestNotifications());
        actions.addView(notif);
        actions.addView(gap(7));
        TextView clear = button("EFFACER LA CHRONOLOGIE", false);
        clear.setOnClickListener(v -> confirmClear());
        actions.addView(clear);
        actions.addView(gap(8));
        actions.addView(row("Version", "3.0.0"));
        actions.addView(row("Cloud", "Aucun"));
        actions.addView(row("Télémétrie", "Désactivée"));
        actions.addView(row("Mode", "Observation et audit autorisés"));
        page.addView(actions);
        return scroll(page);
    }

    private void startOperation(boolean deep) {
        if (lanScanning || serviceScanning || bleScanning) return;
        log("OPERATION SENTINEL lancée.");
        startLanScan();
        if (prefs.getBoolean("service_discovery", true)) startServiceDiscovery();
        startBleScan();
        if (deep) ui.postDelayed(() -> {
            List<String> targets = new ArrayList<>();
            synchronized (online) { for (DeviceState d : online) targets.add(d.ip); }
            int limit = Math.min(12, targets.size());
            for (int i = 0; i < limit; i++) deepScan(targets.get(i), false);
        }, 5000);
    }

    private void startLanScan() {
        if (lanScanning) return;
        String local = localIp();
        if (local == null || !local.contains(".")) { toast("Connexion IPv4 locale requise."); return; }
        String[] p = local.split("\\.");
        if (p.length != 4) { toast("Réseau IPv4 non compatible."); return; }
        String prefix = p[0] + "." + p[1] + "." + p[2] + ".";
        Set<String> previous = lastScan();
        online.clear();
        lanScanning = true;
        log("Cartographie LAN lancée sur " + prefix + "0/24.");
        render();
        AtomicInteger remaining = new AtomicInteger(254);
        int timeout = prefs.getInt("scan_timeout", 450);
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + i;
            pool.submit(() -> {
                long begin = System.nanoTime();
                boolean reachable = false;
                try { reachable = InetAddress.getByName(ip).isReachable(timeout); } catch (Exception ignored) {}
                long latency = Math.max(1, (System.nanoTime() - begin) / 1_000_000);
                if (reachable) online.add(new DeviceState(ip, latency));
                if (remaining.decrementAndGet() == 0) ui.post(() -> finishLan(previous));
            });
        }
    }

    private void finishLan(Set<String> previous) {
        Set<String> now = new HashSet<>();
        synchronized (online) {
            for (DeviceState d : online) { now.add(d.ip); registerDevice(d); }
        }
        Set<String> added = new HashSet<>(now); added.removeAll(previous);
        Set<String> gone = new HashSet<>(previous); gone.removeAll(now);
        prefs.edit().putStringSet(pk("last_scan"), now).apply();
        for (String ip : added) {
            addAnomaly("Nouvelle identité réseau : " + ip);
            log("Nouvel appareil détecté : " + ip + ".");
        }
        for (String ip : gone) log("Appareil absent depuis le scan précédent : " + ip + ".");
        lanScanning = false;
        log("Cartographie terminée : " + now.size() + " actifs, " + added.size() + " nouveaux, " + gone.size() + " absents.");
        render();
    }

    private void registerDevice(DeviceState d) {
        Set<String> set = known();
        boolean first = set.add(d.ip);
        SharedPreferences.Editor e = prefs.edit();
        e.putStringSet(pk("known"), set);
        e.putLong(pk("latency_" + d.ip), d.latency);
        e.putString(pk("last_" + d.ip), timestamp());
        int seen = prefs.getInt(pk("seen_" + d.ip), 0) + 1;
        e.putInt(pk("seen_" + d.ip), seen);
        if (first) {
            e.putString(pk("first_" + d.ip), timestamp());
            e.putString(pk("name_" + d.ip), defaultName(d.ip));
            e.putString(pk("type_" + d.ip), "Appareil réseau");
            if (prefs.getBoolean("notify_new", true)) notifyNew(d.ip);
        }
        e.apply();
        String mac = arpMac(d.ip);
        if (!mac.isEmpty()) prefs.edit().putString(pk("mac_" + d.ip), mac).apply();
    }

    private void deepScan(String ip, boolean show) {
        if (!privateIp(ip)) { toast("Adresse privée locale requise."); return; }
        if (deepScanning && show) { toast("Une analyse approfondie est déjà active."); return; }
        if (show) { deepScanning = true; toast("Analyse approfondie lancée…"); }
        pool.submit(() -> {
            DeepResult r = inspect(ip);
            saveDeep(r);
            ui.post(() -> {
                if (show) {
                    deepScanning = false;
                    passport(ip);
                }
                if (tab == 0 || tab == 1 || tab == 2) render();
            });
        });
    }

    private DeepResult inspect(String ip) {
        DeepResult r = new DeepResult(ip);
        r.hostname = reverseName(ip);
        r.mac = arpMac(ip);
        if (prefs.getBoolean("deep_ports", true)) {
            for (int port : COMMON_PORTS) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(ip, port), 280);
                    r.ports.add(port);
                } catch (Exception ignored) {}
            }
        }
        for (int port : r.ports) {
            String banner = banner(ip, port);
            if (!banner.isEmpty()) r.banners.put(port, banner);
        }
        r.type = classify(r);
        r.os = inferOs(r);
        r.vendor = inferVendor(r);
        r.cert = certificate(ip, r.ports);
        r.confidence = confidenceFrom(r);
        r.fingerprint = sha256(ip + "|" + r.mac + "|" + r.hostname + "|" + r.ports + "|" + r.banners + "|" + r.cert);
        return r;
    }

    private void saveDeep(DeepResult r) {
        String oldPorts = prefs.getString(pk("ports_" + r.ip), "");
        String newPorts = joinInts(r.ports);
        String oldHost = prefs.getString(pk("host_" + r.ip), "");
        String oldFp = prefs.getString(pk("fingerprint_" + r.ip), "");
        SharedPreferences.Editor e = prefs.edit();
        e.putString(pk("ports_" + r.ip), newPorts);
        e.putString(pk("host_" + r.ip), r.hostname);
        e.putString(pk("mac_" + r.ip), r.mac);
        e.putString(pk("vendor_" + r.ip), r.vendor);
        e.putString(pk("type_" + r.ip), r.type);
        e.putString(pk("os_" + r.ip), r.os);
        e.putString(pk("banners_" + r.ip), r.banners.toString());
        e.putString(pk("cert_" + r.ip), r.cert);
        e.putString(pk("fingerprint_" + r.ip), r.fingerprint);
        e.putInt(pk("confidence_" + r.ip), r.confidence);
        e.putString(pk("deep_last_" + r.ip), timestamp());
        e.apply();
        if (!oldPorts.isEmpty() && !oldPorts.equals(newPorts)) addAnomaly("Ports modifiés sur " + r.ip + " : " + oldPorts + " → " + newPorts);
        if (!oldHost.isEmpty() && !r.hostname.isEmpty() && !oldHost.equals(r.hostname)) addAnomaly("Nom réseau modifié sur " + r.ip + ".");
        if (!oldFp.isEmpty() && !oldFp.equals(r.fingerprint)) addAnomaly("Empreinte modifiée sur " + r.ip + ".");
        log("Analyse intelligence terminée pour " + r.ip + " : " + r.type + ", " + r.ports.size() + " port(s), confiance " + r.confidence + "%.");
    }

    private String banner(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 420);
            s.setSoTimeout(420);
            OutputStream out = s.getOutputStream();
            if (port == 80 || port == 8080 || port == 8000 || port == 3000 || port == 5000) {
                out.write(("HEAD / HTTP/1.0\r\nHost: " + ip + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            } else if (port == 554) {
                out.write(("OPTIONS rtsp://" + ip + "/ RTSP/1.0\r\nCSeq: 1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
            InputStream in = s.getInputStream();
            byte[] buf = new byte[1024];
            int n = in.read(buf);
            if (n <= 0) return "";
            String raw = new String(buf, 0, n, StandardCharsets.ISO_8859_1).replace('\r', ' ').replace('\n', ' ').trim();
            return raw.length() > 180 ? raw.substring(0, 180) : raw;
        } catch (Exception e) { return ""; }
    }

    private String certificate(String ip, List<Integer> ports) {
        int port = ports.contains(443) ? 443 : ports.contains(8443) ? 8443 : -1;
        if (port < 0) return "";
        try {
            TrustManager[] trust = { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trust, new SecureRandom());
            try (SSLSocket socket = (SSLSocket) ctx.getSocketFactory().createSocket()) {
                socket.connect(new InetSocketAddress(ip, port), 700);
                socket.setSoTimeout(700);
                socket.startHandshake();
                java.security.cert.Certificate[] chain = socket.getSession().getPeerCertificates();
                if (chain.length > 0 && chain[0] instanceof X509Certificate) {
                    X509Certificate x = (X509Certificate) chain[0];
                    return x.getSubjectX500Principal().getName() + " · expire " + new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(x.getNotAfter());
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void startServiceDiscovery() {
        if (serviceScanning) return;
        serviceScanning = true;
        services.clear();
        ssdp.clear();
        log("Découverte SSDP/mDNS lancée.");
        acquireMulticast();
        pool.submit(this::ssdpDiscovery);
        discoverNsdType(0);
        ui.postDelayed(() -> {
            serviceScanning = false;
            stopNsd();
            releaseMulticast();
            log("Découverte terminée : " + services.size() + " service(s) mDNS, " + ssdp.size() + " annonce(s) SSDP.");
            render();
        }, 15000);
        render();
    }

    private void ssdpDiscovery() {
        String request = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n";
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(700);
            byte[] data = request.getBytes(StandardCharsets.US_ASCII);
            DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName("239.255.255.250"), 1900);
            socket.send(p);
            long until = System.currentTimeMillis() + 6000;
            while (System.currentTimeMillis() < until) {
                byte[] buf = new byte[4096];
                DatagramPacket reply = new DatagramPacket(buf, buf.length);
                try { socket.receive(reply); } catch (Exception timeout) { continue; }
                String raw = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.ISO_8859_1);
                Map<String, String> h = parseHeaders(raw);
                String host = reply.getAddress().getHostAddress();
                String server = h.getOrDefault("server", "Service UPnP");
                String st = h.getOrDefault("st", h.getOrDefault("nt", "ssdp"));
                String loc = h.getOrDefault("location", "");
                ssdp.put(host + "|" + st, mask(host) + " · " + server + " · " + st + (loc.isEmpty() ? "" : " · " + loc));
            }
        } catch (Exception e) { log("SSDP indisponible : " + e.getClass().getSimpleName() + "."); }
    }

    private Map<String, String> parseHeaders(String raw) {
        Map<String, String> map = new HashMap<>();
        for (String line : raw.split("\\r?\\n")) {
            int i = line.indexOf(':');
            if (i > 0) map.put(line.substring(0, i).trim().toLowerCase(Locale.ROOT), line.substring(i + 1).trim());
        }
        return map;
    }

    private final String[] nsdTypes = {"_http._tcp.", "_https._tcp.", "_workstation._tcp.", "_ipp._tcp.", "_printer._tcp.", "_googlecast._tcp.", "_airplay._tcp.", "_spotify-connect._tcp.", "_ssh._tcp."};

    private void discoverNsdType(int index) {
        if (!serviceScanning || index >= nsdTypes.length) return;
        nsd = (NsdManager) getSystemService(NSD_SERVICE);
        if (nsd == null) return;
        String type = nsdTypes[index];
        activeNsd = new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String s) {}
            public void onDiscoveryStopped(String s) {}
            public void onStartDiscoveryFailed(String s, int e) { stopNsd(); ui.postDelayed(() -> discoverNsdType(index + 1), 250); }
            public void onStopDiscoveryFailed(String s, int e) { stopNsd(); }
            public void onServiceLost(NsdServiceInfo info) {}
            public void onServiceFound(NsdServiceInfo info) {
                try {
                    nsd.resolveService(info, new NsdManager.ResolveListener() {
                        public void onResolveFailed(NsdServiceInfo i, int e) {}
                        public void onServiceResolved(NsdServiceInfo i) {
                            String host = i.getHost() == null ? "—" : i.getHost().getHostAddress();
                            ServiceHit hit = new ServiceHit(i.getServiceName(), i.getServiceType(), host, i.getPort());
                            services.put(host + "|" + i.getServiceType() + "|" + i.getServiceName(), hit);
                        }
                    });
                } catch (Exception ignored) {}
            }
        };
        try { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, activeNsd); } catch (Exception ignored) {}
        ui.postDelayed(() -> { stopNsd(); discoverNsdType(index + 1); }, 1200);
    }

    private void stopNsd() {
        if (nsd != null && activeNsd != null) {
            try { nsd.stopServiceDiscovery(activeNsd); } catch (Exception ignored) {}
        }
        activeNsd = null;
    }

    private void acquireMulticast() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("sentry-v3-discovery");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (Exception ignored) {}
    }

    private void releaseMulticast() {
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); } catch (Exception ignored) {}
    }

    private void startBleScan() {
        if (bleScanning) return;
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLE);
            return;
        }
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { toast("Active le Bluetooth."); return; }
        bleScanner = adapter.getBluetoothLeScanner();
        if (bleScanner == null) { toast("Scanner BLE indisponible."); return; }
        ble.clear();
        bleScanning = true;
        log("Radar Bluetooth activé.");
        try { bleScanner.startScan(bleCallback); } catch (SecurityException e) { bleScanning = false; return; }
        ui.postDelayed(this::stopBleScan, 14000);
        render();
    }

    private final ScanCallback bleCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            String address = result.getDevice().getAddress();
            String name = "Appareil BLE";
            try { if (result.getDevice().getName() != null && !result.getDevice().getName().trim().isEmpty()) name = result.getDevice().getName(); } catch (SecurityException ignored) {}
            int rssi = result.getRssi();
            String zone = rssi >= -55 ? "immédiat" : rssi >= -68 ? "proche" : rssi >= -80 ? "moyen" : "éloigné";
            ble.put(address, new BleState(name, address, rssi, zone, System.currentTimeMillis()));
            ui.post(() -> { if (tab == 3) render(); });
        }
    };

    private void stopBleScan() {
        if (!bleScanning) return;
        bleScanning = false;
        try { if (bleScanner != null) bleScanner.stopScan(bleCallback); } catch (SecurityException ignored) {}
        log("Radar Bluetooth arrêté : " + ble.size() + " signal(s).");
        render();
    }

    private void passport(String ip) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(8), dp(18), dp(8));
        String name = prefs.getString(pk("name_" + ip), defaultName(ip));
        body.addView(tv(name, 22, text, true));
        body.addView(tv("DEVICE PASSPORT · " + fingerprint(ip).substring(0, 16).toUpperCase(Locale.FRANCE), 10, accent, true));
        body.addView(gap(10));
        body.addView(row("IP actuelle", mask(ip)));
        body.addView(row("Nom réseau", value(pk("host_" + ip))));
        body.addView(row("Adresse matérielle", prefs.getBoolean("privacy_mode", false) ? "Masquée" : value(pk("mac_" + ip))));
        body.addView(row("Constructeur probable", value(pk("vendor_" + ip))));
        body.addView(row("Catégorie probable", prefs.getString(pk("type_" + ip), inferType(ip))));
        body.addView(row("Système probable", value(pk("os_" + ip))));
        body.addView(row("Confiance classification", confidence(ip) + "%"));
        body.addView(row("Ports observés", value(pk("ports_" + ip))));
        body.addView(row("Certificat TLS", value(pk("cert_" + ip))));
        body.addView(row("Bannières publiques", value(pk("banners_" + ip))));
        body.addView(row("Première apparition", value(pk("first_" + ip))));
        body.addView(row("Dernière apparition", value(pk("last_" + ip))));
        body.addView(row("Dernière analyse intel", value(pk("deep_last_" + ip))));
        body.addView(row("Nombre d’observations", String.valueOf(prefs.getInt(pk("seen_" + ip), 0))));
        body.addView(row("Latence", latency(ip)));
        body.addView(row("Stabilité", stability(ip) + "%"));
        body.addView(row("Niveau de risque", riskLabel(riskFor(ip))));
        body.addView(gap(10));
        body.addView(tv("Pourquoi cette classification ?", 15, text, true));
        body.addView(gap(4));
        body.addView(tv(classificationReasons(ip), 12, muted, false));
        scroll.addView(body);

        EditText rename = new EditText(this);
        rename.setText(name);
        rename.setSingleLine(true);
        boolean trusted = prefs.getBoolean(pk("trusted_" + ip), false);
        new AlertDialog.Builder(this)
                .setTitle(mask(ip))
                .setView(scroll)
                .setPositiveButton("Analyse complète", (d, w) -> deepScan(ip, true))
                .setNeutralButton(trusted ? "Retirer confiance" : "Approuver", (d, w) -> {
                    prefs.edit().putBoolean(pk("trusted_" + ip), !trusted).apply();
                    log((trusted ? "Confiance retirée : " : "Identité approuvée : ") + ip + ".");
                    render();
                })
                .setNegativeButton("Fermer", null)
                .show();
    }

    private String classify(DeepResult r) {
        String all = (r.hostname + " " + r.banners + " " + ssdpFor(r.ip)).toLowerCase(Locale.ROOT);
        if (r.ports.contains(9100) || r.ports.contains(631) || all.contains("printer") || all.contains("ipp")) return "Imprimante";
        if (r.ports.contains(554) || all.contains("camera") || all.contains("onvif") || all.contains("rtsp")) return "Caméra / vidéo IP";
        if (all.contains("chromecast") || all.contains("google cast") || all.contains("airplay") || all.contains("roku")) return "Lecteur multimédia / TV";
        if (all.contains("playstation") || all.contains("xbox") || all.contains("nintendo")) return "Console de jeu";
        if (r.ports.contains(445) || r.ports.contains(139)) return "Ordinateur / NAS";
        if (r.ports.contains(22) && (r.ports.contains(80) || r.ports.contains(443))) return "Serveur / équipement administrable";
        if (all.contains("router") || all.contains("gateway") || all.contains("openwrt") || all.contains("fritz")) return "Routeur / passerelle";
        if (r.ports.contains(1883) || r.ports.contains(8883)) return "Objet connecté / broker MQTT";
        if (r.ports.isEmpty()) return "Mobile ou appareil filtré";
        return "Appareil réseau";
    }

    private String inferOs(DeepResult r) {
        String all = (r.hostname + " " + r.banners + " " + ssdpFor(r.ip)).toLowerCase(Locale.ROOT);
        if (all.contains("windows") || all.contains("microsoft-iis")) return "Windows probable";
        if (all.contains("android") || all.contains("chromecast")) return "Android / système Google probable";
        if (all.contains("apple") || all.contains("airplay") || all.contains("darwin")) return "Apple / Darwin probable";
        if (all.contains("linux") || all.contains("ubuntu") || all.contains("debian") || all.contains("openwrt")) return "Linux probable";
        if (all.contains("tizen") || all.contains("samsung")) return "Tizen / Samsung probable";
        if (all.contains("webos") || all.contains("lg")) return "webOS / LG probable";
        return "Non déterminé";
    }

    private String inferVendor(DeepResult r) {
        String all = (r.hostname + " " + r.banners + " " + ssdpFor(r.ip)).toLowerCase(Locale.ROOT);
        String[][] vendors = {{"samsung","Samsung"},{"apple","Apple"},{"google","Google"},{"chromecast","Google"},{"sony","Sony"},{"playstation","Sony"},{"microsoft","Microsoft"},{"xbox","Microsoft"},{"lg","LG"},{"philips","Philips"},{"hp ","HP"},{"hewlett","HP"},{"canon","Canon"},{"epson","Epson"},{"brother","Brother"},{"synology","Synology"},{"qnap","QNAP"},{"ubiquiti","Ubiquiti"},{"tp-link","TP-Link"},{"netgear","Netgear"},{"xiaomi","Xiaomi"},{"huawei","Huawei"},{"raspberry","Raspberry Pi"}};
        for (String[] v : vendors) if (all.contains(v[0])) return v[1] + " · inféré";
        if (!r.mac.isEmpty()) return "OUI " + r.mac.substring(0, Math.min(8, r.mac.length())) + " · base locale non résolue";
        return "Non déterminé";
    }

    private int confidenceFrom(DeepResult r) {
        int c = 28;
        if (!r.hostname.isEmpty() && !r.hostname.equals(r.ip)) c += 12;
        if (!r.mac.isEmpty()) c += 12;
        if (!r.ports.isEmpty()) c += Math.min(18, r.ports.size() * 3);
        if (!r.banners.isEmpty()) c += 18;
        if (!r.cert.isEmpty()) c += 8;
        if (!ssdpFor(r.ip).isEmpty()) c += 15;
        return Math.min(99, c);
    }

    private String reverseName(String ip) {
        try {
            String host = InetAddress.getByName(ip).getCanonicalHostName();
            return host.equals(ip) ? "" : host;
        } catch (Exception e) { return ""; }
    }

    private String arpMac(String ip) {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length >= 4 && p[0].equals(ip) && p[3].matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) return p[3].toUpperCase(Locale.ROOT);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String ssdpFor(String ip) {
        StringBuilder b = new StringBuilder();
        synchronized (ssdp) {
            for (Map.Entry<String, String> e : ssdp.entrySet()) if (e.getKey().startsWith(ip + "|")) b.append(e.getValue()).append(' ');
        }
        return b.toString().trim();
    }

    private String inferType(String ip) {
        String t = prefs.getString(pk("type_" + ip), "");
        return t.isEmpty() ? "Appareil réseau" : t;
    }

    private int confidence(String ip) { return prefs.getInt(pk("confidence_" + ip), prefs.getString(pk("ports_" + ip), "").isEmpty() ? 36 : 62); }

    private String fingerprint(String ip) {
        String fp = prefs.getString(pk("fingerprint_" + ip), "");
        if (!fp.isEmpty()) return fp;
        return sha256(ip + "|" + prefs.getString(pk("mac_" + ip), "") + "|" + prefs.getString(pk("first_" + ip), ""));
    }

    private int riskFor(String ip) {
        int risk = prefs.getBoolean(pk("trusted_" + ip), false) ? 5 : 24;
        Set<Integer> ports = parsePorts(prefs.getString(pk("ports_" + ip), ""));
        if (ports.contains(23)) risk += 28;
        if (ports.contains(21)) risk += 9;
        if (ports.contains(445) || ports.contains(139)) risk += 12;
        if (ports.contains(3389)) risk += 14;
        if (ports.contains(554)) risk += 8;
        if (!prefs.getString(pk("cert_" + ip), "").isEmpty()) risk -= 3;
        return Math.max(0, Math.min(100, risk));
    }

    private String classificationReasons(String ip) {
        List<String> reasons = new ArrayList<>();
        String host = prefs.getString(pk("host_" + ip), "");
        String ports = prefs.getString(pk("ports_" + ip), "");
        String banners = prefs.getString(pk("banners_" + ip), "");
        String mac = prefs.getString(pk("mac_" + ip), "");
        if (!host.isEmpty()) reasons.add("Nom réseau observé : " + host + ".");
        if (!mac.isEmpty()) reasons.add("Adresse matérielle observée sur la table ARP locale.");
        if (!ports.isEmpty()) reasons.add("Services accessibles sur les ports : " + ports + ".");
        if (!banners.isEmpty()) reasons.add("Des bannières publiques ont répondu aux connexions autorisées.");
        if (!ssdpFor(ip).isEmpty()) reasons.add("Annonce UPnP/SSDP associée à cette adresse.");
        if (reasons.isEmpty()) reasons.add("Classification faible fondée uniquement sur la présence et l’historique LAN.");
        return String.join("\n", reasons);
    }

    private int trustedCount() { int n = 0; for (String ip : known()) if (prefs.getBoolean(pk("trusted_" + ip), false)) n++; return n; }
    private int auditIssues() { int n = 0; if (!isSecure()) n++; if (adbEnabled()) n++; if (developerEnabled()) n++; return n; }
    private boolean isSecure() { KeyguardManager k = (KeyguardManager) getSystemService(KEYGUARD_SERVICE); return k != null && k.isDeviceSecure(); }
    private boolean developerEnabled() { try { return Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1; } catch (Exception e) { return false; } }
    private boolean adbEnabled() { try { return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1; } catch (Exception e) { return false; } }

    private void addAnomaly(String text) {
        String old = prefs.getString(pk("anomalies"), "");
        String line = timestampShort() + " · " + text;
        String all = line + "\n" + (old == null ? "" : old);
        if (all.length() > 16000) all = all.substring(0, 16000);
        prefs.edit().putString(pk("anomalies"), all).apply();
    }

    private List<String> currentAnomalies() {
        String raw = prefs.getString(pk("anomalies"), "");
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        String[] lines = raw.split("\\n");
        for (int i = 0; i < Math.min(5, lines.length); i++) if (!lines[i].trim().isEmpty()) out.add(lines[i]);
        return out;
    }

    private int anomalyCount() {
        String raw = prefs.getString(pk("anomalies"), "");
        if (raw == null || raw.trim().isEmpty()) return 0;
        return Math.min(9, raw.split("\\n").length);
    }

    private void chooseProfile() {
        String[] values = {"Maison", "Bureau", "Lycée", "Vacances", "Laboratoire"};
        new AlertDialog.Builder(this).setTitle("Profil d’environnement").setItems(values, (d, w) -> {
            prefs.edit().putString("profile", values[w]).apply();
            online.clear(); ble.clear(); services.clear(); ssdp.clear();
            log("Profil activé : " + values[w] + ".");
            rebuild();
        }).show();
    }

    private void chooseTheme() {
        String[] values = {"Cyber sombre", "Clair technique", "AMOLED"};
        new AlertDialog.Builder(this).setTitle("Thème").setItems(values, (d, w) -> {
            prefs.edit().putString("theme", w == 1 ? "light" : w == 2 ? "amoled" : "dark").apply();
            rebuild();
        }).show();
    }

    private void chooseAccent() {
        String[] values = {"Cyber cyan", "Violet", "Ambre", "Rouge"};
        new AlertDialog.Builder(this).setTitle("Couleur d’accent").setItems(values, (d, w) -> {
            prefs.edit().putString("accent", w == 1 ? "violet" : w == 2 ? "amber" : w == 3 ? "red" : "cyan").apply();
            rebuild();
        }).show();
    }

    private void chooseTimeout() {
        String[] values = {"Rapide · 280 ms", "Équilibré · 450 ms", "Précis · 800 ms"};
        new AlertDialog.Builder(this).setTitle("Délai de détection").setItems(values, (d, w) -> {
            prefs.edit().putInt("scan_timeout", w == 0 ? 280 : w == 2 ? 800 : 450).apply();
            render();
        }).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Effacer le journal ?").setMessage("Les appareils et leurs passeports seront conservés.")
                .setPositiveButton("Effacer", (d, w) -> { prefs.edit().remove(pk("history")).remove(pk("anomalies")).apply(); render(); })
                .setNegativeButton("Annuler", null).show();
    }

    private void exportReport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "Sentry-v3-" + profile() + "-intelligence.json");
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_EXPORT && result == RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                out.write(reportJson().toString(2).getBytes(StandardCharsets.UTF_8));
                toast("Dossier d’intelligence exporté.");
                log("Rapport JSON exporté.");
            } catch (Exception e) { toast("Export impossible."); }
        }
    }

    private JSONObject reportJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("application", "Sentry 3.0");
        root.put("profile", profile());
        root.put("generated_at", timestamp());
        root.put("local_only", true);
        NetInfo n = netInfo();
        JSONObject network = new JSONObject();
        network.put("transport", n.transport);
        network.put("local", n.local);
        network.put("gateway", n.gateway);
        network.put("dns", n.dns);
        network.put("vpn", n.vpn);
        root.put("network", network);
        JSONArray devices = new JSONArray();
        List<String> ips = new ArrayList<>(known());
        ips.sort(Comparator.comparingInt(this::lastOctet));
        for (String ip : ips) {
            JSONObject d = new JSONObject();
            d.put("name", prefs.getString(pk("name_" + ip), defaultName(ip)));
            d.put("ip", ip);
            d.put("hostname", prefs.getString(pk("host_" + ip), ""));
            d.put("mac", prefs.getString(pk("mac_" + ip), ""));
            d.put("vendor", prefs.getString(pk("vendor_" + ip), ""));
            d.put("type", prefs.getString(pk("type_" + ip), inferType(ip)));
            d.put("os", prefs.getString(pk("os_" + ip), ""));
            d.put("ports", prefs.getString(pk("ports_" + ip), ""));
            d.put("certificate", prefs.getString(pk("cert_" + ip), ""));
            d.put("fingerprint", fingerprint(ip));
            d.put("confidence", confidence(ip));
            d.put("risk", riskFor(ip));
            d.put("trusted", prefs.getBoolean(pk("trusted_" + ip), false));
            d.put("first_seen", prefs.getString(pk("first_" + ip), ""));
            d.put("last_seen", prefs.getString(pk("last_" + ip), ""));
            devices.put(d);
        }
        root.put("devices", devices);
        root.put("history", prefs.getString(pk("history"), ""));
        root.put("anomalies", prefs.getString(pk("anomalies"), ""));
        return root;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Alertes Sentry v3", NotificationManager.IMPORTANCE_DEFAULT);
            c.setDescription("Changements observés sur les réseaux autorisés");
            NotificationManager n = getSystemService(NotificationManager.class);
            if (n != null) n.createNotificationChannel(c);
        }
    }

    private void notifyNew(String ip) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager n = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (n == null) return;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Nouvelle identité réseau").setContentText(mask(ip) + " détectée dans " + profile()).setAutoCancel(true);
        n.notify(Math.abs((profile() + ip).hashCode()), b.build());
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        else toast("Notifications disponibles.");
    }

    private NetInfo netInfo() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return new NetInfo("Déconnecté", "—", "—", "—", false);
        Network active = cm.getActiveNetwork();
        LinkProperties lp = active == null ? null : cm.getLinkProperties(active);
        NetworkCapabilities nc = active == null ? null : cm.getNetworkCapabilities(active);
        String transport = nc == null ? "Déconnecté" : nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Mobile" : nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" : nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN" : "Autre";
        boolean vpn = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        String local = localIp();
        String gateway = "—";
        List<String> dns = new ArrayList<>();
        if (lp != null) {
            for (android.net.RouteInfo r : lp.getRoutes()) if (r.isDefaultRoute() && r.getGateway() != null) gateway = r.getGateway().getHostAddress();
            for (InetAddress a : lp.getDnsServers()) dns.add(a.getHostAddress());
        }
        return new NetInfo(transport, local == null ? "—" : local, gateway, dns.isEmpty() ? "—" : String.join(", ", dns), vpn);
    }

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

    private boolean privateIp(String ip) {
        try { InetAddress a = InetAddress.getByName(ip); return a instanceof Inet4Address && a.isSiteLocalAddress(); }
        catch (Exception e) { return false; }
    }

    private Set<String> known() { Set<String> s = prefs.getStringSet(pk("known"), new HashSet<>()); return s == null ? new HashSet<>() : new HashSet<>(s); }
    private Set<String> lastScan() { Set<String> s = prefs.getStringSet(pk("last_scan"), new HashSet<>()); return s == null ? new HashSet<>() : new HashSet<>(s); }
    private boolean isOnline(String ip) { synchronized (online) { for (DeviceState d : online) if (d.ip.equals(ip)) return true; } return false; }
    private int lastOctet(String ip) { try { return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1)); } catch (Exception e) { return 0; } }
    private String defaultName(String ip) { return ip.equals(localIp()) ? "Ce téléphone" : "Node-" + String.format(Locale.FRANCE, "%03d", lastOctet(ip)); }
    private String latency(String ip) { long l = prefs.getLong(pk("latency_" + ip), -1); return l < 0 ? "—" : l + " ms"; }
    private int stability(String ip) { int seen = prefs.getInt(pk("seen_" + ip), 0); return Math.min(99, 55 + seen * 5); }
    private String riskLabel(int risk) { return risk < 25 ? "Faible · " + risk + "/100" : risk < 55 ? "Modéré · " + risk + "/100" : "Élevé · " + risk + "/100"; }
    private String value(String key) { String v = prefs.getString(key, ""); return v == null || v.trim().isEmpty() ? "Non observé" : v; }
    private String mask(String value) { if (value == null) return "—"; if (!prefs.getBoolean("privacy_mode", false)) return value; int i = value.lastIndexOf('.'); return i > 0 ? value.substring(0, i) + ".•••" : "••••••"; }
    private String shortId(String value) { if (value == null || value.length() < 5) return "—"; return value.substring(Math.max(0, value.length() - 5)).replace(":", ""); }
    private String timestamp() { return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(new Date()); }
    private String timestampShort() { return new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(new Date()); }

    private void log(String event) {
        if (!prefs.getBoolean("keep_history", true)) return;
        String old = prefs.getString(pk("history"), "");
        String all = "[" + timestampShort() + "] " + event + "\n" + (old == null ? "" : old);
        if (all.length() > 30000) all = all.substring(0, 30000);
        prefs.edit().putString(pk("history"), all).apply();
    }

    private String historyLines(int max) {
        String raw = prefs.getString(pk("history"), "");
        if (raw == null || raw.trim().isEmpty()) return "Aucune activité enregistrée.";
        String[] lines = raw.split("\\n");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(max, lines.length); i++) b.append(lines[i]).append('\n');
        return b.toString().trim();
    }

    private String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : hash) b.append(String.format(Locale.ROOT, "%02x", x));
            return b.toString();
        } catch (Exception e) { return "00000000000000000000000000000000"; }
    }

    private String joinInts(List<Integer> values) {
        List<String> out = new ArrayList<>();
        for (Integer i : values) out.add(String.valueOf(i));
        return String.join(",", out);
    }

    private Set<Integer> parsePorts(String raw) {
        Set<Integer> out = new HashSet<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String p : raw.split(",")) try { out.add(Integer.parseInt(p.trim())); } catch (Exception ignored) {}
        return out;
    }

    private int zoneColor(String zone) { return "immédiat".equals(zone) ? good : "proche".equals(zone) ? cyan : "moyen".equals(zone) ? warn : muted; }
    private String themeLabel() { String t = prefs.getString("theme", "dark"); return "light".equals(t) ? "Clair technique" : "amoled".equals(t) ? "AMOLED" : "Cyber sombre"; }
    private String accentLabel() { String a = prefs.getString("accent", "cyan"); return "violet".equals(a) ? "Violet" : "amber".equals(a) ? "Ambre" : "red".equals(a) ? "Rouge" : "Cyber cyan"; }

    private LinearLayout page() { LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); int pad = prefs.getBoolean("compact", false) ? 10 : 15; p.setPadding(dp(pad), dp(5), dp(pad), dp(24)); return p; }
    private ScrollView scroll(View v) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(v, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); return s; }
    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); int pad = prefs.getBoolean("compact", false) ? 12 : 16; c.setPadding(dp(pad), dp(pad), dp(pad), dp(pad)); c.setBackground(round(surface, 22)); c.setElevation(dp(2)); return c; }
    private LinearLayout statCard(String label, String value, int color) { LinearLayout c = card(); c.addView(tv(label, 9, muted, true)); c.addView(gap(5)); c.addView(tv(value, 28, color, true)); return c; }
    private TextView section(String text) { return tv(text, 17, this.text, true); }
    private View row(String label, String value) { LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.TOP); r.setPadding(0, dp(5), 0, dp(5)); TextView l = tv(label, 12, muted, false); TextView v = tv(value == null ? "—" : value, 12, text, true); v.setGravity(Gravity.END); r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .43f)); r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .57f)); return r; }
    private View signalRow(String tag, String body, int color) { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setPadding(0, dp(6), 0, dp(6)); r.addView(tv(tag, 9, color, true)); r.addView(tv(body, 12, text, false)); return r; }
    private View toggle(String label, String key) { LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(8), 0, dp(8)); r.addView(tv(label, 13, text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); boolean on = prefs.getBoolean(key, false); TextView state = tv(on ? "ON" : "OFF", 9, on ? good : muted, true); state.setPadding(dp(8), dp(5), dp(8), dp(5)); state.setBackground(round(surface2, 11)); r.addView(state); r.setOnClickListener(v -> { prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply(); if ("compact".equals(key) || "privacy_mode".equals(key)) rebuild(); else render(); }); return r; }
    private View choice(String label, String value, Runnable run) { LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(8), 0, dp(8)); r.addView(tv(label, 13, text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); r.addView(tv(value, 12, accent, true)); r.setOnClickListener(v -> run.run()); return r; }
    private TextView button(String label, boolean primary) { TextView b = tv(label, 13, primary ? Color.rgb(6, 23, 17) : text, true); b.setGravity(Gravity.CENTER); b.setPadding(dp(10), dp(13), dp(10), dp(13)); b.setBackground(round(primary ? accent : surface2, 16)); return b; }
    private TextView tv(String value, float size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(0, 1.12f); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return t; }
    private GradientDrawable round(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private View gap(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View gapW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private View progress(int value) { FrameLayout frame = new FrameLayout(this); frame.setBackground(round(surface2, 6)); View bar = new View(this); bar.setBackground(round(value >= 85 ? good : value >= 65 ? warn : bad, 6)); frame.addView(bar, new FrameLayout.LayoutParams(0, dp(7))); frame.post(() -> { FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams(); lp.width = (int) (frame.getWidth() * value / 100f); bar.setLayoutParams(lp); }); return frame; }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQ_BLE && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startBleScan();
    }

    @Override protected void onDestroy() {
        try { if (bleScanning && bleScanner != null) bleScanner.stopScan(bleCallback); } catch (Exception ignored) {}
        stopNsd(); releaseMulticast(); pool.shutdownNow(); super.onDestroy();
    }

    private class GraphView extends View {
        private final Paint line = new Paint(1), node = new Paint(1), label = new Paint(1), glow = new Paint(1);
        GraphView(Context c) { super(c); line.setStrokeWidth(dp(1.5f)); line.setColor(Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent))); label.setColor(text); label.setTextSize(dp(10)); label.setTextAlign(Paint.Align.CENTER); glow.setStyle(Paint.Style.STROKE); glow.setStrokeWidth(dp(2)); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2;
            glow.setColor(Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)));
            for (int r = 55; r <= 125; r += 35) c.drawCircle(cx, cy, dp(r), glow);
            node.setColor(accent); c.drawCircle(cx, cy, dp(28), node);
            label.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("GATEWAY", cx, cy + dp(4), label);
            List<String> ips = new ArrayList<>(known()); ips.sort(Comparator.comparingInt(V3Activity.this::lastOctet));
            if (ips.isEmpty()) { label.setTypeface(Typeface.DEFAULT); c.drawText("Lance une cartographie", cx, h - dp(25), label); return; }
            int count = Math.min(14, ips.size()); float radius = Math.min(w, h) * .36f;
            for (int i = 0; i < count; i++) {
                String ip = ips.get(i); double angle = -Math.PI / 2 + (2 * Math.PI * i / count);
                float x = cx + (float) Math.cos(angle) * radius, y = cy + (float) Math.sin(angle) * radius;
                c.drawLine(cx, cy, x, y, line);
                int color = isOnline(ip) ? good : prefs.getBoolean(pk("trusted_" + ip), false) ? cyan : muted;
                node.setColor(color); c.drawCircle(x, y, dp(17), node);
                String n = prefs.getString(pk("name_" + ip), defaultName(ip)); if (n.length() > 12) n = n.substring(0, 11) + "…";
                label.setTypeface(Typeface.DEFAULT_BOLD); c.drawText(n, x, y + dp(30), label);
                label.setTypeface(Typeface.DEFAULT); c.drawText(mask(ip), x, y + dp(42), label);
            }
        }
    }

    private class RadarView extends View {
        private final Paint grid = new Paint(1), dot = new Paint(1), label = new Paint(1), sweep = new Paint(1);
        RadarView(Context c) { super(c); grid.setStyle(Paint.Style.STROKE); grid.setStrokeWidth(dp(1)); grid.setColor(Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent))); label.setTextSize(dp(10)); label.setTextAlign(Paint.Align.CENTER); label.setColor(text); sweep.setStrokeWidth(dp(2)); sweep.setColor(Color.argb(180, Color.red(accent), Color.green(accent), Color.blue(accent))); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float cx = getWidth()/2f, cy = getHeight()/2f, max = Math.min(cx, cy)-dp(24);
            for (int i=1;i<=4;i++) c.drawCircle(cx,cy,max*i/4f,grid);
            c.drawLine(cx-max,cy,cx+max,cy,grid); c.drawLine(cx,cy-max,cx,cy+max,grid);
            double sweepAngle=(System.currentTimeMillis()%6000)/6000.0*Math.PI*2; c.drawLine(cx,cy,cx+(float)Math.cos(sweepAngle)*max,cy+(float)Math.sin(sweepAngle)*max,sweep);
            dot.setColor(accent); c.drawCircle(cx,cy,dp(8),dot); label.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("SENTRY",cx,cy+dp(24),label);
            synchronized (ble) {
                int i=0; for(BleState b:ble.values()) { double a=(Math.abs(b.address.hashCode())%360)*Math.PI/180.0; float factor=b.rssi>=-55?.22f:b.rssi>=-68?.45f:b.rssi>=-80?.69f:.91f; float x=cx+(float)Math.cos(a)*max*factor, y=cy+(float)Math.sin(a)*max*factor; dot.setColor(zoneColor(b.zone)); c.drawCircle(x,y,dp(8),dot); String n=b.name.length()>12?b.name.substring(0,11)+"…":b.name; label.setTypeface(Typeface.DEFAULT_BOLD); c.drawText(n,x,y+dp(20),label); if(++i>=12)break; }
            }
            if (bleScanning && prefs.getBoolean("animation", true)) postInvalidateDelayed(40);
        }
    }

    private class PulseView extends View {
        private final Paint axis = new Paint(1), wave = new Paint(1);
        PulseView(Context c) { super(c); axis.setColor(Color.argb(80, Color.red(muted), Color.green(muted), Color.blue(muted))); axis.setStrokeWidth(dp(1)); wave.setColor(accent); wave.setStrokeWidth(dp(2)); wave.setStyle(Paint.Style.STROKE); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float w=getWidth(),h=getHeight(); for(int i=1;i<5;i++)c.drawLine(w*i/5f,0,w*i/5f,h,axis); c.drawLine(0,h/2,w,h/2,axis); Path p=new Path(); for(int x=0;x<=w;x+=4){float base=h/2f;float pulse=(float)(Math.sin(x*.055)*5);int mod=x%130;if(mod>45&&mod<50)pulse=-22;else if(mod>=50&&mod<56)pulse=28;else if(mod>=56&&mod<64)pulse=-13;float y=base+pulse;if(x==0)p.moveTo(x,y);else p.lineTo(x,y);}c.drawPath(p,wave); if(prefs.getBoolean("animation",true))postInvalidateDelayed(90); }
    }

    private static class DeviceState { final String ip; final long latency; DeviceState(String ip, long latency) { this.ip = ip; this.latency = latency; } }
    private static class BleState { final String name, address, zone; final int rssi; final long seen; BleState(String name, String address, int rssi, String zone, long seen) { this.name=name; this.address=address; this.rssi=rssi; this.zone=zone; this.seen=seen; } }
    private static class ServiceHit { final String name, type, host; final int port; ServiceHit(String name, String type, String host, int port) { this.name=name; this.type=type; this.host=host; this.port=port; } }
    private static class NetInfo { final String transport, local, gateway, dns; final boolean vpn; NetInfo(String transport, String local, String gateway, String dns, boolean vpn) { this.transport=transport; this.local=local; this.gateway=gateway; this.dns=dns; this.vpn=vpn; } }
    private static class DeepResult { final String ip; String hostname="", mac="", vendor="", type="", os="", cert="", fingerprint=""; int confidence; final List<Integer> ports=new ArrayList<>(); final Map<Integer,String> banners=new LinkedHashMap<>(); DeepResult(String ip){this.ip=ip;} }
}
