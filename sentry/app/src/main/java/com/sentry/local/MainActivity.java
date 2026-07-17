package com.sentry.local;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
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

    private FrameLayout content;
    private LinearLayout nav;
    private TextView topTitle;
    private TextView topSubtitle;
    private int selectedTab = 0;
    private int score = 0;
    private final List<AuditItem> auditItems = new ArrayList<>();
    private final List<String> hosts = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService scanPool = Executors.newFixedThreadPool(28);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean scanning = false;
    private SharedPreferences prefs;
    private String pendingReport = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("sentry_local", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildShell();
        refreshAuditData();
        render();
        addJournal("Sentry a démarré en mode local.");
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(18), dp(22), dp(12));
        topTitle = text("Sentry", 28, TEXT, true);
        topSubtitle = text("Local, privé, sous ton contrôle", 13, MUTED, false);
        header.addView(topTitle);
        header.addView(space(4));
        header.addView(topSubtitle);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(8), dp(10), dp(8));
        nav.setBackgroundColor(BG);
        String[] labels = {"Accueil", "Audit", "Réseau", "Journal"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = text(labels[i], 13, MUTED, true);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(10), dp(12), dp(10), dp(12));
            item.setOnClickListener(v -> {
                selectedTab = index;
                render();
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
    }

    private void render() {
        updateHeader();
        updateNav();
        content.removeAllViews();
        if (selectedTab == 0) content.addView(buildDashboard());
        if (selectedTab == 1) content.addView(buildAudit());
        if (selectedTab == 2) content.addView(buildNetwork());
        if (selectedTab == 3) content.addView(buildJournal());
    }

    private void updateHeader() {
        String[] titles = {"Sentry", "Audit local", "Réseau local", "Journal privé"};
        String[] subtitles = {
                "Local, privé, sous ton contrôle",
                "Vérifications effectuées directement sur l’appareil",
                "Découverte volontaire du sous-réseau Wi-Fi actif",
                "Historique stocké uniquement sur ce téléphone"
        };
        topTitle.setText(titles[selectedTab]);
        topSubtitle.setText(subtitles[selectedTab]);
    }

    private void updateNav() {
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView item = (TextView) nav.getChildAt(i);
            boolean active = i == selectedTab;
            item.setTextColor(active ? Color.rgb(17, 24, 21) : MUTED);
            item.setBackground(active ? rounded(ACCENT, 18) : rounded(Color.TRANSPARENT, 18));
        }
    }

    private View buildDashboard() {
        LinearLayout page = page();
        int scoreColor = score >= 80 ? GOOD : score >= 60 ? WARN : BAD;

        LinearLayout scoreCard = card();
        scoreCard.addView(text("ÉTAT DE SÉCURITÉ", 12, MUTED, true));
        scoreCard.addView(space(10));
        scoreCard.addView(text(score + "/100", 42, scoreColor, true));
        scoreCard.addView(space(6));
        scoreCard.addView(text(scoreMessage(), 15, TEXT, false));
        page.addView(scoreCard);
        page.addView(space(12));

        LinearLayout quick = card();
        quick.addView(sectionTitle("Actions rapides"));
        quick.addView(space(12));
        TextView auditButton = action("Relancer l’audit", true);
        auditButton.setOnClickListener(v -> {
            refreshAuditData();
            addJournal("Audit local relancé.");
            render();
        });
        quick.addView(auditButton);
        quick.addView(space(10));
        TextView scanButton = action(scanning ? "Analyse en cours…" : "Scanner le réseau local", false);
        scanButton.setOnClickListener(v -> startLanScan());
        quick.addView(scanButton);
        quick.addView(space(10));
        TextView exportButton = action("Exporter un rapport local", false);
        exportButton.setOnClickListener(v -> exportReport());
        quick.addView(exportButton);
        page.addView(quick);
        page.addView(space(12));

        LinearLayout device = card();
        device.addView(sectionTitle("Cet appareil"));
        device.addView(space(12));
        device.addView(row("Modèle", Build.MANUFACTURER + " " + Build.MODEL));
        device.addView(row("Android", Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT));
        device.addView(row("Correctif", safe(Build.VERSION.SECURITY_PATCH, "Non indiqué")));
        device.addView(row("Architecture", String.join(", ", Build.SUPPORTED_ABIS)));
        page.addView(device);
        page.addView(space(12));

        LinearLayout privacy = card();
        privacy.addView(sectionTitle("Confidentialité"));
        privacy.addView(space(10));
        privacy.addView(text("Aucun compte, aucune télémétrie, aucun serveur. Les audits, hôtes détectés et journaux restent dans le stockage privé de l’application.", 14, MUTED, false));
        page.addView(privacy);
        return scroll(page);
    }

    private View buildAudit() {
        LinearLayout page = page();
        TextView run = action("Actualiser maintenant", true);
        run.setOnClickListener(v -> {
            refreshAuditData();
            addJournal("Audit local actualisé.");
            render();
        });
        page.addView(run);
        page.addView(space(12));

        for (AuditItem item : auditItems) {
            LinearLayout card = card();
            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView badge = text(item.ok ? "OK" : "À vérifier", 11, item.ok ? GOOD : WARN, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(9), dp(5), dp(9), dp(5));
            badge.setBackground(rounded(item.ok ? Color.rgb(31, 57, 43) : Color.rgb(69, 54, 30), 12));
            titleRow.addView(text(item.title, 16, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            titleRow.addView(badge);
            card.addView(titleRow);
            card.addView(space(8));
            card.addView(text(item.detail, 14, MUTED, false));
            page.addView(card);
            page.addView(space(10));
        }
        LinearLayout note = card();
        note.addView(text("Ce score est un diagnostic indicatif et ne remplace pas un audit de sécurité professionnel.", 13, MUTED, false));
        page.addView(note);
        return scroll(page);
    }

    private View buildNetwork() {
        LinearLayout page = page();
        NetworkState state = readNetworkState();

        LinearLayout status = card();
        status.addView(sectionTitle("Connexion active"));
        status.addView(space(12));
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
        scan.addView(text("Analyse uniquement le /24 correspondant à l’adresse IPv4 privée active. Aucun port n’est testé et aucune donnée ne quitte le téléphone.", 13, MUTED, false));
        scan.addView(space(12));
        TextView button = action(scanning ? "Analyse en cours…" : "Lancer la découverte", true);
        button.setOnClickListener(v -> startLanScan());
        scan.addView(button);
        scan.addView(space(12));
        scan.addView(text(hosts.isEmpty() ? "Aucun hôte détecté pour le moment." : hosts.size() + " hôte(s) détecté(s)", 14, TEXT, true));
        synchronized (hosts) {
            for (String host : hosts) {
                scan.addView(space(8));
                scan.addView(row("Appareil local", host));
            }
        }
        page.addView(scan);
        return scroll(page);
    }

    private View buildJournal() {
        LinearLayout page = page();
        TextView clear = action("Effacer le journal", false);
        clear.setOnClickListener(v -> {
            prefs.edit().remove("journal").apply();
            render();
        });
        page.addView(clear);
        page.addView(space(12));

        LinearLayout logCard = card();
        String journal = prefs.getString("journal", "");
        if (journal == null || journal.trim().isEmpty()) journal = "Aucune activité enregistrée.";
        TextView body = text(journal, 13, MUTED, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        logCard.addView(body);
        page.addView(logCard);
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
        boolean encrypted = enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE
                || enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY
                || enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER;
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
        auditItems.add(new AuditItem("Correctif Android", patchKnown ? "Correctif déclaré : " + patch : "Date de correctif indisponible.", patchKnown));
        if (patchKnown) total += 10;

        String privateDns = Settings.Global.getString(getContentResolver(), "private_dns_mode");
        boolean dnsOk = privateDns == null || !"off".equalsIgnoreCase(privateDns);
        String dnsDetail = privateDns == null ? "Mode automatique ou géré par le système." : "Mode : " + privateDns;
        auditItems.add(new AuditItem("DNS privé", dnsDetail, dnsOk));
        if (dnsOk) total += 5;

        score = total;
    }

    private boolean isLikelyRooted() {
        String[] paths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
                "/system/app/Superuser.apk", "/data/adb/magisk", "/data/local/su"
        };
        for (String path : paths) {
            try {
                if (new File(path).exists()) return true;
            } catch (SecurityException ignored) {
            }
        }
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
        boolean validated = false;
        boolean vpn = false;
        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "Wi-Fi";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "Réseau mobile";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "Ethernet";
            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transport = "Bluetooth";
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        }
        List<String> addresses = new ArrayList<>();
        List<String> dns = new ArrayList<>();
        if (props != null) {
            for (LinkAddress address : props.getLinkAddresses()) addresses.add(address.toString());
            for (InetAddress server : props.getDnsServers()) dns.add(server.getHostAddress());
        }
        return new NetworkState(transport, validated, vpn,
                addresses.isEmpty() ? "—" : String.join("\n", addresses),
                dns.isEmpty() ? "—" : String.join(", ", dns));
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
        } catch (Exception ignored) {
        }
        return null;
    }

    private void startLanScan() {
        if (scanning) return;
        String local = getPrivateIpv4();
        if (local == null || local.split("\\.").length != 4) {
            Toast.makeText(this, "Connecte-toi à un réseau local IPv4.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] parts = local.split("\\.");
        String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
        hosts.clear();
        scanning = true;
        addJournal("Découverte LAN lancée sur " + prefix + "0/24.");
        render();

        AtomicInteger remaining = new AtomicInteger(254);
        for (int i = 1; i <= 254; i++) {
            final String host = prefix + i;
            scanPool.submit(() -> {
                boolean reachable = false;
                try {
                    reachable = InetAddress.getByName(host).isReachable(450);
                } catch (Exception ignored) {
                }
                if (reachable) {
                    synchronized (hosts) {
                        if (!hosts.contains(host)) {
                            hosts.add(host);
                            Collections.sort(hosts, this::compareIp);
                        }
                    }
                    mainHandler.post(() -> {
                        if (selectedTab == 2) render();
                    });
                }
                if (remaining.decrementAndGet() == 0) {
                    mainHandler.post(() -> {
                        scanning = false;
                        addJournal("Découverte LAN terminée : " + hosts.size() + " hôte(s) détecté(s).");
                        render();
                    });
                }
            });
        }
    }

    private int compareIp(String a, String b) {
        try {
            String[] pa = a.split("\\.");
            String[] pb = b.split("\\.");
            return Integer.compare(Integer.parseInt(pa[3]), Integer.parseInt(pb[3]));
        } catch (Exception ignored) {
            return a.compareTo(b);
        }
    }

    private void exportReport() {
        pendingReport = buildReport();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        String date = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "Sentry-rapport-" + date + ".txt");
        startActivityForResult(intent, 410);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 410 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) out.write(pendingReport.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                addJournal("Rapport local exporté.");
                Toast.makeText(this, "Rapport exporté.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Export impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private String buildReport() {
        NetworkState state = readNetworkState();
        StringBuilder report = new StringBuilder();
        report.append("SENTRY — RAPPORT LOCAL\n");
        report.append("Généré le ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(new Date())).append("\n\n");
        report.append("APPAREIL\n");
        report.append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        report.append("Android ").append(Build.VERSION.RELEASE).append(" · API ").append(Build.VERSION.SDK_INT).append("\n");
        report.append("Correctif : ").append(safe(Build.VERSION.SECURITY_PATCH, "non indiqué")).append("\n\n");
        report.append("AUDIT — ").append(score).append("/100\n");
        for (AuditItem item : auditItems) report.append(item.ok ? "[OK] " : "[À VÉRIFIER] ").append(item.title).append(" — ").append(item.detail).append("\n");
        report.append("\nRÉSEAU\n");
        report.append("Transport : ").append(state.transport).append("\n");
        report.append("Internet validé : ").append(state.validated ? "oui" : "non").append("\n");
        report.append("VPN : ").append(state.vpn ? "actif" : "inactif").append("\n");
        report.append("Adresses : ").append(state.addresses).append("\n");
        report.append("DNS : ").append(state.dns).append("\n");
        report.append("Hôtes LAN détectés : ").append(hosts.size()).append("\n");
        synchronized (hosts) {
            for (String host : hosts) report.append("- ").append(host).append("\n");
        }
        report.append("\nAucune donnée n’a été envoyée vers un serveur par Sentry.\n");
        return report.toString();
    }

    private void addJournal(String event) {
        String old = prefs.getString("journal", "");
        String stamp = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(new Date());
        String updated = "[" + stamp + "] " + event + "\n" + (old == null ? "" : old);
        if (updated.length() > 12000) updated = updated.substring(0, 12000);
        prefs.edit().putString("journal", updated).apply();
    }

    private String scoreMessage() {
        if (score >= 90) return "Configuration locale solide.";
        if (score >= 75) return "Bon niveau, quelques points peuvent être renforcés.";
        if (score >= 55) return "Plusieurs réglages méritent une vérification.";
        return "Configuration à revoir avant des usages sensibles.";
    }

    private LinearLayout page() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(8), dp(18), dp(24));
        return page;
    }

    private ScrollView scroll(View child) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(child, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        card.setBackground(rounded(SURFACE, 24));
        card.setElevation(dp(2));
        return card;
    }

    private TextView sectionTitle(String value) {
        return text(value, 17, TEXT, true);
    }

    private View row(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView left = text(label, 13, MUTED, false);
        TextView right = text(value, 13, TEXT, true);
        right.setGravity(Gravity.END);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));
        row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));
        return row;
    }

    private TextView action(String label, boolean primary) {
        TextView action = text(label, 15, primary ? Color.rgb(17, 24, 21) : TEXT, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(16), dp(14), dp(16), dp(14));
        action.setBackground(rounded(primary ? ACCENT : SURFACE_2, 18));
        action.setClickable(true);
        action.setFocusable(true);
        return action;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private View space(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanPool.shutdownNow();
    }

    private static class AuditItem {
        final String title;
        final String detail;
        final boolean ok;

        AuditItem(String title, String detail, boolean ok) {
            this.title = title;
            this.detail = detail;
            this.ok = ok;
        }
    }

    private static class NetworkState {
        final String transport;
        final boolean validated;
        final boolean vpn;
        final String addresses;
        final String dns;

        NetworkState(String transport, boolean validated, boolean vpn, String addresses, String dns) {
            this.transport = transport;
            this.validated = validated;
            this.vpn = vpn;
            this.addresses = addresses;
            this.dns = dns;
        }
    }
}
