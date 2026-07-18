package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(17, 20, 19);
    private static final int SURFACE = Color.rgb(28, 33, 31);
    private static final int SURFACE_2 = Color.rgb(37, 43, 40);
    private static final int TEXT = Color.rgb(240, 244, 242);
    private static final int MUTED = Color.rgb(163, 176, 170);
    private static final int ACCENT = Color.rgb(166, 203, 185);
    private static final int GOOD = Color.rgb(137, 205, 164);
    private static final int WARN = Color.rgb(228, 191, 120);
    private static final int BAD = Color.rgb(232, 133, 133);
    private static final int REQ_BLUETOOTH = 710;

    private final int[] commonPorts = {21, 22, 23, 25, 53, 80, 110, 139, 143, 443, 445, 587, 993, 995, 1883, 3000, 3306, 3389, 5432, 5900, 8000, 8080, 8443};
    private final Map<Integer, String> portNames = new LinkedHashMap<>();
    private final List<AuditItem> auditItems = new ArrayList<>();
    private final List<HostItem> hosts = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, BleItem> bleDevices = Collections.synchronizedMap(new LinkedHashMap<>());
    private final List<String> openPorts = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService pool = Executors.newFixedThreadPool(30);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FrameLayout content;
    private LinearLayout nav;
    private TextView topTitle;
    private TextView topSubtitle;
    private SharedPreferences prefs;
    private BluetoothLeScanner bluetoothScanner;
    private boolean scanningLan;
    private boolean scanningPorts;
    private boolean scanningBle;
    private int selectedTab;
    private int score;
    private String portTarget = "";
    private String pendingReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("sentry_local", MODE_PRIVATE);
        seedPorts();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildShell();
        refreshAuditData();
        render();
        addJournal("Sentry v1.1 a démarré en mode local.");
    }

    private void seedPorts() {
        portNames.put(21, "FTP"); portNames.put(22, "SSH"); portNames.put(23, "Telnet");
        portNames.put(25, "SMTP"); portNames.put(53, "DNS"); portNames.put(80, "HTTP");
        portNames.put(110, "POP3"); portNames.put(139, "NetBIOS"); portNames.put(143, "IMAP");
        portNames.put(443, "HTTPS"); portNames.put(445, "SMB"); portNames.put(587, "SMTP Submission");
        portNames.put(993, "IMAPS"); portNames.put(995, "POP3S"); portNames.put(1883, "MQTT");
        portNames.put(3000, "Dev/Web"); portNames.put(3306, "MySQL"); portNames.put(3389, "RDP");
        portNames.put(5432, "PostgreSQL"); portNames.put(5900, "VNC"); portNames.put(8000, "Web alternatif");
        portNames.put(8080, "HTTP alternatif"); portNames.put(8443, "HTTPS alternatif");
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(18), dp(22), dp(12));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("S", 18, Color.rgb(17, 24, 21), true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(ACCENT, 16));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(38), dp(38)));
        brand.addView(spaceW(12));
        topTitle = text("Sentry", 28, TEXT, true);
        brand.addView(topTitle);
        header.addView(brand);
        header.addView(space(6));
        topSubtitle = text("Local, privé, sous ton contrôle", 13, MUTED, false);
        header.addView(topSubtitle);
        root.addView(header);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        String[] labels = {"Accueil", "Audit", "Réseau", "Bluetooth", "Journal"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = text(labels[i], 11, MUTED, true);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(5), dp(12), dp(5), dp(12));
            item.setOnClickListener(v -> { selectedTab = index; render(); });
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(nav);
        setContentView(root);
    }

    private void render() {
        String[] titles = {"Sentry", "Audit local", "Réseau local", "Bluetooth Lab", "Journal privé"};
        String[] subtitles = {
                "Local, privé, sous ton contrôle",
                "Vérifications effectuées directement sur l’appareil",
                "Découverte et diagnostic de ton propre réseau",
                "Appareils BLE visibles à proximité",
                "Historique stocké uniquement sur ce téléphone"
        };
        topTitle.setText(titles[selectedTab]);
        topSubtitle.setText(subtitles[selectedTab]);
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView item = (TextView) nav.getChildAt(i);
            boolean active = i == selectedTab;
            item.setTextColor(active ? Color.rgb(17, 24, 21) : MUTED);
            item.setBackground(active ? rounded(ACCENT, 18) : rounded(Color.TRANSPARENT, 18));
        }
        content.removeAllViews();
        if (selectedTab == 0) content.addView(buildDashboard());
        else if (selectedTab == 1) content.addView(buildAudit());
        else if (selectedTab == 2) content.addView(buildNetwork());
        else if (selectedTab == 3) content.addView(buildBluetooth());
        else content.addView(buildJournal());
    }

    private View buildDashboard() {
        LinearLayout page = page();
        int scoreColor = score >= 80 ? GOOD : score >= 60 ? WARN : BAD;
        LinearLayout scoreCard = card();
        scoreCard.addView(text("ÉTAT DE SÉCURITÉ", 12, MUTED, true));
        scoreCard.addView(space(10));
        scoreCard.addView(text(score + "/100", 42, scoreColor, true));
        scoreCard.addView(space(5));
        scoreCard.addView(text(scoreMessage(), 15, TEXT, false));
        page.addView(scoreCard);
        page.addView(space(12));

        LinearLayout quick = card();
        quick.addView(sectionTitle("Actions rapides"));
        quick.addView(space(12));
        TextView audit = action("Relancer l’audit", true);
        audit.setOnClickListener(v -> { refreshAuditData(); addJournal("Audit local relancé."); render(); });
        quick.addView(audit);
        quick.addView(space(10));
        TextView lan = action(scanningLan ? "Analyse LAN en cours…" : "Scanner le réseau local", false);
        lan.setOnClickListener(v -> startLanScan());
        quick.addView(lan);
        quick.addView(space(10));
        TextView ble = action(scanningBle ? "Scan Bluetooth en cours…" : "Scanner le Bluetooth", false);
        ble.setOnClickListener(v -> startBluetoothScan());
        quick.addView(ble);
        quick.addView(space(10));
        TextView export = action("Exporter un rapport local", false);
        export.setOnClickListener(v -> exportReport());
        quick.addView(export);
        page.addView(quick);
        page.addView(space(12));

        LinearLayout summary = card();
        summary.addView(sectionTitle("Vue locale"));
        summary.addView(space(10));
        summary.addView(row("Hôtes LAN", String.valueOf(hosts.size())));
        summary.addView(row("Appareils BLE", String.valueOf(bleDevices.size())));
        summary.addView(row("Ports ouverts", String.valueOf(openPorts.size())));
        summary.addView(row("Version", "1.1.0"));
        page.addView(summary);
        page.addView(space(12));

        LinearLayout privacy = card();
        privacy.addView(sectionTitle("Confidentialité"));
        privacy.addView(space(10));
        privacy.addView(text("Aucun compte, aucune télémétrie et aucun serveur. Les résultats restent dans le stockage privé de Sentry.", 14, MUTED, false));
        page.addView(privacy);
        return scroll(page);
    }

    private View buildAudit() {
        LinearLayout page = page();
        TextView run = action("Actualiser maintenant", true);
        run.setOnClickListener(v -> { refreshAuditData(); addJournal("Audit local actualisé."); render(); });
        page.addView(run);
        page.addView(space(12));
        for (AuditItem item : auditItems) {
            LinearLayout c = card();
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.addView(text(item.title, 16, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView badge = text(item.ok ? "OK" : "À vérifier", 11, item.ok ? GOOD : WARN, true);
            badge.setPadding(dp(9), dp(5), dp(9), dp(5));
            badge.setBackground(rounded(item.ok ? Color.rgb(31, 57, 43) : Color.rgb(69, 54, 30), 12));
            titleRow.addView(badge);
            c.addView(titleRow);
            c.addView(space(8));
            c.addView(text(item.detail, 14, MUTED, false));
            page.addView(c);
            page.addView(space(10));
        }
        LinearLayout note = card();
        note.addView(text("Le score est indicatif et ne remplace pas un audit professionnel.", 13, MUTED, false));
        page.addView(note);
        return scroll(page);
    }

    private View buildNetwork() {
        LinearLayout page = page();
        NetworkState state = readNetworkState();
        LinearLayout status = card();
        status.addView(sectionTitle("Connexion active"));
        status.addView(space(10));
        status.addView(row("Transport", state.transport));
        status.addView(row("Internet validé", state.validated ? "Oui" : "Non"));
        status.addView(row("VPN actif", state.vpn ? "Oui" : "Non"));
        status.addView(row("Adresses", state.addresses));
        status.addView(row("DNS", state.dns));
        page.addView(status);
        page.addView(space(12));

        LinearLayout scan = card();
        scan.addView(sectionTitle("Découverte LAN"));
        scan.addView(space(8));
        scan.addView(text("Sentry analyse uniquement le /24 privé actif. Aucun résultat ne quitte le téléphone.", 13, MUTED, false));
        scan.addView(space(12));
        TextView scanButton = action(scanningLan ? "Analyse en cours…" : "Lancer la découverte", true);
        scanButton.setOnClickListener(v -> startLanScan());
        scan.addView(scanButton);
        scan.addView(space(12));
        scan.addView(text(hosts.isEmpty() ? "Aucun hôte détecté." : hosts.size() + " hôte(s) détecté(s)", 14, TEXT, true));
        synchronized (hosts) {
            for (HostItem host : hosts) {
                scan.addView(space(8));
                LinearLayout hostCard = miniCard();
                hostCard.addView(row(host.ip.equals(getPrivateIpv4()) ? "Ce téléphone" : "Appareil local", host.ip));
                hostCard.addView(row("Latence", host.latencyMs < 0 ? "Réponse locale" : host.latencyMs + " ms"));
                hostCard.setOnClickListener(v -> { portTarget = host.ip; render(); });
                scan.addView(hostCard);
            }
        }
        page.addView(scan);
        page.addView(space(12));

        LinearLayout ports = card();
        ports.addView(sectionTitle("Inspecteur de ports"));
        ports.addView(space(8));
        ports.addView(text("Uniquement pour une machine privée qui t’appartient ou pour laquelle tu as une autorisation explicite.", 13, MUTED, false));
        ports.addView(space(12));
        EditText target = input(portTarget.isEmpty() ? "192.168.1.1" : portTarget);
        ports.addView(target);
        ports.addView(space(10));
        TextView portButton = action(scanningPorts ? "Inspection en cours…" : "Tester les ports courants", false);
        portButton.setOnClickListener(v -> {
            portTarget = target.getText().toString().trim();
            hideKeyboard(target);
            startPortScan(portTarget);
        });
        ports.addView(portButton);
        ports.addView(space(12));
        if (openPorts.isEmpty()) ports.addView(text("Aucun résultat pour le moment.", 13, MUTED, false));
        else {
            ports.addView(text(openPorts.size() + " port(s) ouvert(s) sur " + portTarget, 14, TEXT, true));
            for (String result : openPorts) { ports.addView(space(7)); ports.addView(text(result, 13, GOOD, true)); }
        }
        page.addView(ports);
        return scroll(page);
    }

    private View buildBluetooth() {
        LinearLayout page = page();
        LinearLayout intro = card();
        intro.addView(sectionTitle("Bluetooth Low Energy"));
        intro.addView(space(8));
        intro.addView(text("Affiche uniquement les appareils qui diffusent publiquement à proximité. Le scan s’arrête automatiquement après 12 secondes.", 13, MUTED, false));
        intro.addView(space(12));
        TextView button = action(scanningBle ? "Scan en cours…" : "Lancer le scan BLE", true);
        button.setOnClickListener(v -> startBluetoothScan());
        intro.addView(button);
        page.addView(intro);
        page.addView(space(12));

        List<BleItem> list = new ArrayList<>();
        synchronized (bleDevices) { list.addAll(bleDevices.values()); }
        list.sort((a, b) -> Integer.compare(b.rssi, a.rssi));
        if (list.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("Aucun appareil Bluetooth détecté pour le moment.", 14, MUTED, false));
            page.addView(empty);
        } else {
            for (BleItem item : list) {
                LinearLayout c = card();
                c.addView(text(item.name, 16, TEXT, true));
                c.addView(space(7));
                c.addView(row("Identifiant", item.address));
                c.addView(row("Signal", item.rssi + " dBm · " + signalLabel(item.rssi)));
                c.addView(row("Vu", item.lastSeen));
                page.addView(c);
                page.addView(space(10));
            }
        }
        return scroll(page);
    }

    private View buildJournal() {
        LinearLayout page = page();
        TextView clear = action("Effacer le journal", false);
        clear.setOnClickListener(v -> { prefs.edit().remove("journal").apply(); render(); });
        page.addView(clear);
        page.addView(space(12));
        LinearLayout c = card();
        String journal = prefs.getString("journal", "");
        if (journal == null || journal.trim().isEmpty()) journal = "Aucune activité enregistrée.";
        TextView body = text(journal, 13, MUTED, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        c.addView(body);
        page.addView(c);
        return scroll(page);
    }

    private void refreshAuditData() {
        auditItems.clear();
        int total = 0;
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean secureLock = km != null && km.isDeviceSecure();
        auditItems.add(new AuditItem("Verrouillage écran", secureLock ? "Un verrouillage sécurisé est actif." : "Aucun verrouillage sécurisé détecté.", secureLock));
        if (secureLock) total += 20;

        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        int enc = dpm == null ? DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED : dpm.getStorageEncryptionStatus();
        boolean encrypted = enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE || enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY || enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER;
        auditItems.add(new AuditItem("Chiffrement", encrypted ? "Le stockage est chiffré par Android." : "État de chiffrement non confirmé.", encrypted));
        if (encrypted) total += 20;

        boolean adbOff = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 0;
        auditItems.add(new AuditItem("Débogage USB", adbOff ? "ADB est désactivé." : "ADB est actif. Désactive-le hors développement.", adbOff));
        if (adbOff) total += 15;

        boolean devOff = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 0;
        auditItems.add(new AuditItem("Options développeur", devOff ? "Les options développeur sont désactivées." : "Les options développeur sont actives.", devOff));
        if (devOff) total += 10;

        boolean rooted = isLikelyRooted();
        auditItems.add(new AuditItem("Intégrité système", rooted ? "Des traces courantes de root ont été détectées." : "Aucune trace courante de root détectée.", !rooted));
        if (!rooted) total += 20;

        String patch = Build.VERSION.SECURITY_PATCH;
        boolean patchKnown = patch != null && !patch.trim().isEmpty();
        auditItems.add(new AuditItem("Correctif Android", patchKnown ? "Correctif déclaré : " + patch : "Date indisponible.", patchKnown));
        if (patchKnown) total += 10;

        String privateDns = Settings.Global.getString(getContentResolver(), "private_dns_mode");
        boolean dnsOk = privateDns == null || !"off".equalsIgnoreCase(privateDns);
        auditItems.add(new AuditItem("DNS privé", privateDns == null ? "Mode automatique ou géré par le système." : "Mode : " + privateDns, dnsOk));
        if (dnsOk) total += 5;
        score = total;
    }

    private boolean isLikelyRooted() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk", "/data/adb/magisk", "/data/local/su"};
        for (String path : paths) try { if (new File(path).exists()) return true; } catch (SecurityException ignored) {}
        return false;
    }

    private NetworkState readNetworkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return new NetworkState("Indisponible", false, false, "—", "—");
        Network network = cm.getActiveNetwork();
        if (network == null) return new NetworkState("Déconnecté", false, false, "—", "—");
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        LinkProperties props = cm.getLinkProperties(network);
        String transport = "Autre";
        boolean validated = false, vpn = false;
        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "Wi-Fi";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "Réseau mobile";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "Ethernet";
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        }
        List<String> addresses = new ArrayList<>(), dns = new ArrayList<>();
        if (props != null) {
            for (LinkAddress a : props.getLinkAddresses()) addresses.add(a.toString());
            for (InetAddress d : props.getDnsServers()) dns.add(d.getHostAddress());
        }
        return new NetworkState(transport, validated, vpn, addresses.isEmpty() ? "—" : String.join("\n", addresses), dns.isEmpty() ? "—" : String.join(", ", dns));
    }

    private String getPrivateIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void startLanScan() {
        if (scanningLan) return;
        String local = getPrivateIpv4();
        if (local == null || local.split("\\.").length != 4) {
            Toast.makeText(this, "Connecte-toi à un réseau local IPv4.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] parts = local.split("\\.");
        String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
        hosts.clear();
        scanningLan = true;
        addJournal("Découverte LAN lancée sur " + prefix + "0/24.");
        render();
        AtomicInteger remaining = new AtomicInteger(254);
        for (int i = 1; i <= 254; i++) {
            final String host = prefix + i;
            pool.submit(() -> {
                long start = System.nanoTime();
                boolean reachable = false;
                try { reachable = InetAddress.getByName(host).isReachable(550); } catch (Exception ignored) {}
                long latency = (System.nanoTime() - start) / 1_000_000;
                if (reachable) {
                    synchronized (hosts) {
                        boolean exists = false;
                        for (HostItem h : hosts) if (h.ip.equals(host)) exists = true;
                        if (!exists) hosts.add(new HostItem(host, latency));
                        hosts.sort(Comparator.comparingInt(h -> lastOctet(h.ip)));
                    }
                    mainHandler.post(() -> { if (selectedTab == 2) render(); });
                }
                if (remaining.decrementAndGet() == 0) mainHandler.post(() -> {
                    scanningLan = false;
                    addJournal("Découverte LAN terminée : " + hosts.size() + " hôte(s).");
                    render();
                });
            });
        }
    }

    private int lastOctet(String ip) {
        try { return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1)); } catch (Exception e) { return 0; }
    }

    private void startPortScan(String target) {
        if (scanningPorts) return;
        if (!isPrivateIpv4(target)) {
            Toast.makeText(this, "Entre une adresse IPv4 privée valide.", Toast.LENGTH_LONG).show();
            return;
        }
        scanningPorts = true;
        openPorts.clear();
        addJournal("Inspection de ports autorisée lancée sur " + target + ".");
        render();
        AtomicInteger remaining = new AtomicInteger(commonPorts.length);
        for (int port : commonPorts) {
            pool.submit(() -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(target, port), 500);
                    openPorts.add(port + " · " + portNames.getOrDefault(port, "Service inconnu"));
                    openPorts.sort(Comparator.comparingInt(s -> Integer.parseInt(s.substring(0, s.indexOf(' ')))));
                } catch (Exception ignored) {}
                if (remaining.decrementAndGet() == 0) mainHandler.post(() -> {
                    scanningPorts = false;
                    addJournal("Inspection terminée sur " + target + " : " + openPorts.size() + " port(s) ouvert(s).");
                    render();
                });
            });
        }
    }

    private boolean isPrivateIpv4(String ip) {
        try {
            String[] p = ip.split("\\.");
            if (p.length != 4) return false;
            int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
            for (String x : p) { int n = Integer.parseInt(x); if (n < 0 || n > 255) return false; }
            return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168);
        } catch (Exception e) { return false; }
    }

    private void startBluetoothScan() {
        if (scanningBle) return;
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
            return;
        }
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth non disponible.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!adapter.isEnabled()) {
            Toast.makeText(this, "Active le Bluetooth puis relance le scan.", Toast.LENGTH_LONG).show();
            return;
        }
        bluetoothScanner = adapter.getBluetoothLeScanner();
        if (bluetoothScanner == null) {
            Toast.makeText(this, "Scanner BLE indisponible.", Toast.LENGTH_LONG).show();
            return;
        }
        bleDevices.clear();
        scanningBle = true;
        addJournal("Scan Bluetooth BLE lancé.");
        render();
        try {
            bluetoothScanner.startScan(bleCallback);
            mainHandler.postDelayed(this::stopBluetoothScan, 12000);
        } catch (SecurityException e) {
            scanningBle = false;
            Toast.makeText(this, "Permission Bluetooth refusée.", Toast.LENGTH_LONG).show();
            render();
        }
    }

    private final ScanCallback bleCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice d = result.getDevice();
            String address;
            String name = null;
            try {
                address = d.getAddress();
                name = d.getName();
            } catch (SecurityException e) {
                address = "Appareil masqué";
            }
            if (name == null && result.getScanRecord() != null) name = result.getScanRecord().getDeviceName();
            if (name == null || name.trim().isEmpty()) name = "Appareil BLE";
            String seen = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(new Date());
            bleDevices.put(address, new BleItem(name, address, result.getRssi(), seen));
            mainHandler.post(() -> { if (selectedTab == 3) render(); });
        }

        @Override public void onScanFailed(int errorCode) {
            mainHandler.post(() -> {
                scanningBle = false;
                addJournal("Échec du scan BLE, code " + errorCode + ".");
                Toast.makeText(MainActivity.this, "Scan BLE impossible (" + errorCode + ").", Toast.LENGTH_LONG).show();
                render();
            });
        }
    };

    private void stopBluetoothScan() {
        if (!scanningBle) return;
        try { if (bluetoothScanner != null) bluetoothScanner.stopScan(bleCallback); } catch (SecurityException ignored) {}
        scanningBle = false;
        addJournal("Scan BLE terminé : " + bleDevices.size() + " appareil(s).");
        render();
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLUETOOTH);
        else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_BLUETOOTH);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLUETOOTH) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) granted = false;
            if (granted) startBluetoothScan(); else Toast.makeText(this, "Le Bluetooth Lab nécessite cette autorisation.", Toast.LENGTH_LONG).show();
        }
    }

    private String signalLabel(int rssi) {
        if (rssi >= -55) return "Très proche";
        if (rssi >= -67) return "Proche";
        if (rssi >= -78) return "Moyen";
        return "Faible";
    }

    private void exportReport() {
        pendingReport = buildReport();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "Sentry-v1.1-rapport-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(new Date()) + ".txt");
        startActivityForResult(intent, 410);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 410 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) out.write(pendingReport.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                addJournal("Rapport local exporté.");
                Toast.makeText(this, "Rapport exporté.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "Export impossible : " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        }
    }

    private String buildReport() {
        NetworkState state = readNetworkState();
        StringBuilder r = new StringBuilder();
        r.append("SENTRY v1.1 — RAPPORT LOCAL\n");
        r.append("Généré le ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(new Date())).append("\n\n");
        r.append("APPAREIL\n").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\nAndroid ").append(Build.VERSION.RELEASE).append(" · API ").append(Build.VERSION.SDK_INT).append("\nCorrectif : ").append(safe(Build.VERSION.SECURITY_PATCH, "non indiqué")).append("\n\n");
        r.append("AUDIT — ").append(score).append("/100\n");
        for (AuditItem item : auditItems) r.append(item.ok ? "[OK] " : "[À VÉRIFIER] ").append(item.title).append(" — ").append(item.detail).append("\n");
        r.append("\nRÉSEAU\nTransport : ").append(state.transport).append("\nVPN : ").append(state.vpn ? "actif" : "inactif").append("\nAdresses : ").append(state.addresses).append("\nDNS : ").append(state.dns).append("\nHôtes : ").append(hosts.size()).append("\n");
        synchronized (hosts) { for (HostItem h : hosts) r.append("- ").append(h.ip).append(" · ").append(h.latencyMs).append(" ms\n"); }
        r.append("\nPORTS OUVERTS SUR ").append(portTarget.isEmpty() ? "aucune cible" : portTarget).append("\n");
        synchronized (openPorts) { for (String p : openPorts) r.append("- ").append(p).append("\n"); }
        r.append("\nBLUETOOTH BLE : ").append(bleDevices.size()).append(" appareil(s)\n");
        synchronized (bleDevices) { for (BleItem b : bleDevices.values()) r.append("- ").append(b.name).append(" · ").append(b.rssi).append(" dBm\n"); }
        r.append("\nAucune donnée n’a été envoyée vers un serveur par Sentry.\n");
        return r.toString();
    }

    private void addJournal(String event) {
        String old = prefs.getString("journal", "");
        String stamp = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(new Date());
        String updated = "[" + stamp + "] " + event + "\n" + (old == null ? "" : old);
        if (updated.length() > 14000) updated = updated.substring(0, 14000);
        prefs.edit().putString("journal", updated).apply();
    }

    private String scoreMessage() {
        if (score >= 90) return "Configuration locale solide.";
        if (score >= 75) return "Bon niveau, quelques points peuvent être renforcés.";
        if (score >= 55) return "Plusieurs réglages méritent une vérification.";
        return "Configuration à revoir avant des usages sensibles.";
    }

    private LinearLayout page() { LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(18), dp(8), dp(18), dp(24)); return p; }
    private ScrollView scroll(View child) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.setClipToPadding(false); s.addView(child, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); return s; }
    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(18), dp(17), dp(18), dp(17)); c.setBackground(rounded(SURFACE, 24)); c.setElevation(dp(2)); return c; }
    private LinearLayout miniCard() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(12), dp(8), dp(12), dp(8)); c.setBackground(rounded(SURFACE_2, 16)); return c; }
    private TextView sectionTitle(String value) { return text(value, 17, TEXT, true); }

    private View row(String label, String value) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.TOP); row.setPadding(0, dp(7), 0, dp(7));
        TextView left = text(label, 13, MUTED, false), right = text(value, 13, TEXT, true); right.setGravity(Gravity.END);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .42f)); row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .58f)); return row;
    }

    private TextView action(String label, boolean primary) { TextView a = text(label, 15, primary ? Color.rgb(17, 24, 21) : TEXT, true); a.setGravity(Gravity.CENTER); a.setPadding(dp(16), dp(14), dp(16), dp(14)); a.setBackground(rounded(primary ? ACCENT : SURFACE_2, 18)); a.setClickable(true); a.setFocusable(true); return a; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(TEXT); e.setSingleLine(true); e.setTextSize(15); e.setPadding(dp(14), dp(13), dp(14), dp(13)); e.setBackground(rounded(SURFACE_2, 16)); return e; }
    private TextView text(String value, float size, int color, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setLineSpacing(0, 1.12f); v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return v; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View spaceW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private GradientDrawable rounded(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }
    private void hideKeyboard(View v) { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0); }

    @Override protected void onDestroy() { stopBluetoothScan(); pool.shutdownNow(); super.onDestroy(); }

    private static class AuditItem { final String title, detail; final boolean ok; AuditItem(String t, String d, boolean o) { title=t; detail=d; ok=o; } }
    private static class HostItem { final String ip; final long latencyMs; HostItem(String i, long l) { ip=i; latencyMs=l; } }
    private static class BleItem { final String name, address, lastSeen; final int rssi; BleItem(String n, String a, int r, String l) { name=n; address=a; rssi=r; lastSeen=l; } }
    private static class NetworkState { final String transport, addresses, dns; final boolean validated, vpn; NetworkState(String t, boolean v, boolean p, String a, String d) { transport=t; validated=v; vpn=p; addresses=a; dns=d; } }
}
