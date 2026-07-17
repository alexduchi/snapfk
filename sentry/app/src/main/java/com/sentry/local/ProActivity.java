package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.net.TrafficStats;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ProActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 801;
    private static final String CHANNEL = "sentry_alerts";

    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(30);
    private final List<DeviceItem> currentDevices = Collections.synchronizedList(new ArrayList<>());

    private FrameLayout content;
    private LinearLayout nav;
    private TextView title;
    private TextView subtitle;
    private int selectedTab = 0;
    private boolean scanning = false;

    private int bg;
    private int surface;
    private int surface2;
    private int text;
    private int muted;
    private int accent;
    private int good;
    private int warn;
    private int bad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("sentry_local", MODE_PRIVATE);
        ensureDefaults();
        createNotificationChannel();
        applyPalette();
        buildShell();
        render();
        log("Sentry Pro v1.3 démarré.");
        if (prefs.getBoolean("auto_scan_start", false)) startLanScan();
    }

    private void ensureDefaults() {
        SharedPreferences.Editor e = prefs.edit();
        if (!prefs.contains("theme")) e.putString("theme", "dark");
        if (!prefs.contains("accent")) e.putString("accent", "sage");
        if (!prefs.contains("compact")) e.putBoolean("compact", false);
        if (!prefs.contains("show_latency")) e.putBoolean("show_latency", true);
        if (!prefs.contains("notify_new")) e.putBoolean("notify_new", true);
        if (!prefs.contains("auto_scan_start")) e.putBoolean("auto_scan_start", false);
        if (!prefs.contains("scan_timeout")) e.putInt("scan_timeout", 500);
        if (!prefs.contains("privacy_mode")) e.putBoolean("privacy_mode", false);
        if (!prefs.contains("confirm_tools")) e.putBoolean("confirm_tools", true);
        if (!prefs.contains("keep_history")) e.putBoolean("keep_history", true);
        e.apply();
    }

    private void applyPalette() {
        String theme = prefs.getString("theme", "dark");
        if ("soft".equals(theme)) {
            bg = Color.rgb(236, 239, 236);
            surface = Color.rgb(250, 251, 249);
            surface2 = Color.rgb(226, 232, 228);
            text = Color.rgb(30, 36, 33);
            muted = Color.rgb(98, 111, 104);
        } else if ("amoled".equals(theme)) {
            bg = Color.BLACK;
            surface = Color.rgb(12, 14, 13);
            surface2 = Color.rgb(24, 28, 26);
            text = Color.rgb(245, 247, 246);
            muted = Color.rgb(154, 168, 160);
        } else {
            bg = Color.rgb(17, 20, 19);
            surface = Color.rgb(28, 33, 31);
            surface2 = Color.rgb(37, 43, 40);
            text = Color.rgb(240, 244, 242);
            muted = Color.rgb(163, 176, 170);
        }
        String choice = prefs.getString("accent", "sage");
        if ("blue".equals(choice)) accent = Color.rgb(139, 184, 232);
        else if ("violet".equals(choice)) accent = Color.rgb(190, 161, 229);
        else if ("amber".equals(choice)) accent = Color.rgb(229, 190, 120);
        else accent = Color.rgb(166, 203, 185);
        good = Color.rgb(137, 205, 164);
        warn = Color.rgb(228, 191, 120);
        bad = Color.rgb(232, 133, 133);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(17), dp(20), dp(11));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("S", 18, Color.rgb(17, 24, 21), true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(rounded(accent, 16));
        brand.addView(logo, new LinearLayout.LayoutParams(dp(38), dp(38)));
        brand.addView(spaceW(12));
        title = text("Sentry Pro", 27, text, true);
        brand.addView(title);
        header.addView(brand);
        header.addView(space(5));
        subtitle = text("Centre de contrôle local", 13, muted, false);
        header.addView(subtitle);
        root.addView(header);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(8), dp(8), dp(8));
        String[] labels = {"Accueil", "Appareils", "Vie privée", "Réglages"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            TextView item = text(labels[i], 12, muted, true);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(6), dp(12), dp(6), dp(12));
            item.setOnClickListener(v -> { selectedTab = tab; render(); });
            nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(nav);
        setContentView(root);
    }

    private void rebuildUi() {
        applyPalette();
        buildShell();
        render();
    }

    private void render() {
        String[] titles = {"Sentry Pro", "Appareils locaux", "Vie privée", "Réglages"};
        String[] subtitles = {
                "Centre de contrôle local",
                "Appareils connus, nouveaux et de confiance",
                "État réseau et données locales",
                "Personnalisation et comportement"
        };
        title.setText(titles[selectedTab]);
        subtitle.setText(subtitles[selectedTab]);
        for (int i = 0; i < nav.getChildCount(); i++) {
            TextView item = (TextView) nav.getChildAt(i);
            boolean active = i == selectedTab;
            item.setTextColor(active ? Color.rgb(17, 24, 21) : muted);
            item.setBackground(active ? rounded(accent, 18) : rounded(Color.TRANSPARENT, 18));
        }
        content.removeAllViews();
        if (selectedTab == 0) content.addView(buildDashboard());
        else if (selectedTab == 1) content.addView(buildDevices());
        else if (selectedTab == 2) content.addView(buildPrivacy());
        else content.addView(buildSettings());
    }

    private View buildDashboard() {
        LinearLayout page = page();
        int known = knownIps().size();
        int online = currentDevices.size();
        int trusted = 0;
        for (String ip : knownIps()) if (prefs.getBoolean("trusted_" + ip, false)) trusted++;

        LinearLayout hero = card();
        hero.addView(text("PROTECTION LOCALE", 12, muted, true));
        hero.addView(space(10));
        hero.addView(text(scanning ? "Analyse en cours" : "Prêt", 36, scanning ? warn : good, true));
        hero.addView(space(5));
        hero.addView(text(scanning ? "Découverte des appareils du réseau privé…" : "Aucune donnée n’est envoyée vers un serveur.", 14, text, false));
        page.addView(hero);
        page.addView(space(12));

        LinearLayout stats = card();
        stats.addView(sectionTitle("Vue d’ensemble"));
        stats.addView(space(8));
        stats.addView(row("En ligne maintenant", String.valueOf(online)));
        stats.addView(row("Appareils mémorisés", String.valueOf(known)));
        stats.addView(row("De confiance", String.valueOf(trusted)));
        stats.addView(row("Version", "1.3.0 Pro"));
        page.addView(stats);
        page.addView(space(12));

        LinearLayout actions = card();
        actions.addView(sectionTitle("Actions rapides"));
        actions.addView(space(11));
        TextView scan = action(scanning ? "Analyse en cours…" : "Scanner le réseau", true);
        scan.setOnClickListener(v -> startLanScan());
        actions.addView(scan);
        actions.addView(space(9));
        TextView devices = action("Gérer les appareils", false);
        devices.setOnClickListener(v -> { selectedTab = 1; render(); });
        actions.addView(devices);
        actions.addView(space(9));
        TextView tools = action("Ouvrir les outils avancés", false);
        tools.setOnClickListener(v -> openClassicTools());
        actions.addView(tools);
        page.addView(actions);
        page.addView(space(12));

        LinearLayout recent = card();
        recent.addView(sectionTitle("Dernière activité"));
        recent.addView(space(8));
        recent.addView(text(lastHistoryLines(5), 13, muted, false));
        page.addView(recent);
        return scroll(page);
    }

    private View buildDevices() {
        LinearLayout page = page();
        TextView scan = action(scanning ? "Analyse en cours…" : "Actualiser les appareils", true);
        scan.setOnClickListener(v -> startLanScan());
        page.addView(scan);
        page.addView(space(12));

        List<String> ips = new ArrayList<>(knownIps());
        ips.sort(Comparator.comparingInt(this::lastOctet));
        if (ips.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(sectionTitle("Aucun appareil mémorisé"));
            empty.addView(space(8));
            empty.addView(text("Lance une analyse pour créer ton inventaire local.", 14, muted, false));
            page.addView(empty);
            return scroll(page);
        }

        for (String ip : ips) {
            boolean online = isOnline(ip);
            boolean trusted = prefs.getBoolean("trusted_" + ip, false);
            String name = prefs.getString("name_" + ip, defaultDeviceName(ip));
            LinearLayout c = card();
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(text(name, 16, text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView badge = text(online ? "EN LIGNE" : "HORS LIGNE", 10, online ? good : muted, true);
            badge.setPadding(dp(8), dp(5), dp(8), dp(5));
            badge.setBackground(rounded(surface2, 12));
            top.addView(badge);
            c.addView(top);
            c.addView(space(7));
            c.addView(row("Adresse", privacyIp(ip)));
            c.addView(row("Statut", trusted ? "De confiance" : "À vérifier"));
            c.addView(row("Première détection", prefs.getString("first_" + ip, "—")));
            c.addView(row("Dernière détection", prefs.getString("last_" + ip, "—")));
            c.addView(row("Nombre de détections", String.valueOf(prefs.getInt("seen_" + ip, 0))));
            c.setOnClickListener(v -> showDeviceDialog(ip));
            page.addView(c);
            page.addView(space(10));
        }
        return scroll(page);
    }

    private View buildPrivacy() {
        LinearLayout page = page();
        NetworkInfo info = readNetworkInfo();
        LinearLayout state = card();
        state.addView(sectionTitle("Connexion active"));
        state.addView(space(8));
        state.addView(row("Transport", info.transport));
        state.addView(row("Internet validé", info.validated ? "Oui" : "Non"));
        state.addView(row("VPN actif", info.vpn ? "Oui" : "Non"));
        state.addView(row("Adresses", prefs.getBoolean("privacy_mode", false) ? "Masquées" : info.addresses));
        state.addView(row("DNS", prefs.getBoolean("privacy_mode", false) ? "Masqués" : info.dns));
        page.addView(state);
        page.addView(space(12));

        LinearLayout traffic = card();
        traffic.addView(sectionTitle("Activité réseau de Sentry"));
        traffic.addView(space(8));
        long rx = TrafficStats.getUidRxBytes(getApplicationInfo().uid);
        long tx = TrafficStats.getUidTxBytes(getApplicationInfo().uid);
        traffic.addView(row("Reçu", formatBytes(rx)));
        traffic.addView(row("Envoyé", formatBytes(tx)));
        traffic.addView(text("Ces compteurs concernent uniquement l’application Sentry depuis le dernier redémarrage du téléphone.", 12, muted, false));
        page.addView(traffic);
        page.addView(space(12));

        LinearLayout storage = card();
        storage.addView(sectionTitle("Données locales"));
        storage.addView(space(8));
        storage.addView(row("Inventaire", knownIps().size() + " appareil(s)"));
        storage.addView(row("Historique", historyCount() + " événement(s)"));
        storage.addView(row("Cloud", "Aucun"));
        storage.addView(row("Télémétrie", "Désactivée"));
        storage.addView(space(10));
        TextView clearHistory = action("Effacer l’historique", false);
        clearHistory.setOnClickListener(v -> confirmClearHistory());
        storage.addView(clearHistory);
        storage.addView(space(9));
        TextView resetInventory = action("Réinitialiser l’inventaire", false);
        resetInventory.setOnClickListener(v -> confirmResetInventory());
        storage.addView(resetInventory);
        page.addView(storage);
        return scroll(page);
    }

    private View buildSettings() {
        LinearLayout page = page();

        LinearLayout appearance = card();
        appearance.addView(sectionTitle("Apparence"));
        appearance.addView(space(10));
        appearance.addView(settingChoice("Thème", themeLabel(), () -> chooseTheme()));
        appearance.addView(settingChoice("Couleur d’accent", accentLabel(), () -> chooseAccent()));
        appearance.addView(settingToggle("Mode compact", "Réduit les espacements des cartes", "compact"));
        appearance.addView(settingToggle("Masquer les adresses", "Masque IP et DNS dans l’interface", "privacy_mode"));
        page.addView(appearance);
        page.addView(space(12));

        LinearLayout scans = card();
        scans.addView(sectionTitle("Analyses"));
        scans.addView(space(10));
        scans.addView(settingToggle("Scanner au démarrage", "Analyse uniquement lorsque l’application est ouverte", "auto_scan_start"));
        scans.addView(settingToggle("Afficher la latence", "Ajoute le temps de réponse des appareils", "show_latency"));
        scans.addView(settingChoice("Délai de détection", prefs.getInt("scan_timeout", 500) + " ms", () -> chooseTimeout()));
        scans.addView(settingToggle("Confirmation outils", "Demande confirmation avant les outils réseau", "confirm_tools"));
        page.addView(scans);
        page.addView(space(12));

        LinearLayout alerts = card();
        alerts.addView(sectionTitle("Alertes et historique"));
        alerts.addView(space(10));
        alerts.addView(settingToggle("Nouveaux appareils", "Notification locale lors d’une première détection", "notify_new"));
        alerts.addView(settingToggle("Conserver l’historique", "Journal local des événements importants", "keep_history"));
        TextView permission = action("Autoriser les notifications", false);
        permission.setOnClickListener(v -> requestNotificationPermission());
        alerts.addView(space(8));
        alerts.addView(permission);
        page.addView(alerts);
        page.addView(space(12));

        LinearLayout pro = card();
        pro.addView(sectionTitle("Application"));
        pro.addView(space(8));
        pro.addView(row("Nom", "Sentry Pro"));
        pro.addView(row("Version", "1.3.0"));
        pro.addView(row("Stockage", "100 % local"));
        pro.addView(row("Compte requis", "Non"));
        pro.addView(space(10));
        TextView classic = action("Outils avancés v1.1", true);
        classic.setOnClickListener(v -> openClassicTools());
        pro.addView(classic);
        page.addView(pro);
        return scroll(page);
    }

    private View settingToggle(String name, String description, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(name, 14, text, true));
        copy.addView(text(description, 12, muted, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        boolean enabled = prefs.getBoolean(key, false);
        TextView state = text(enabled ? "ACTIF" : "INACTIF", 10, enabled ? good : muted, true);
        state.setPadding(dp(9), dp(6), dp(9), dp(6));
        state.setBackground(rounded(surface2, 13));
        row.addView(state);
        row.setOnClickListener(v -> {
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply();
            if ("privacy_mode".equals(key) || "compact".equals(key)) rebuildUi(); else render();
        });
        return row;
    }

    private View settingChoice(String name, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.addView(text(name, 14, text, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text(value, 13, accent, true));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void chooseTheme() {
        String[] values = {"Sombre", "Doux clair", "AMOLED"};
        new AlertDialog.Builder(this).setTitle("Choisir le thème").setItems(values, (d, which) -> {
            String value = which == 1 ? "soft" : which == 2 ? "amoled" : "dark";
            prefs.edit().putString("theme", value).apply();
            rebuildUi();
        }).show();
    }

    private void chooseAccent() {
        String[] values = {"Sauge", "Bleu", "Violet", "Ambre"};
        new AlertDialog.Builder(this).setTitle("Couleur d’accent").setItems(values, (d, which) -> {
            String value = which == 1 ? "blue" : which == 2 ? "violet" : which == 3 ? "amber" : "sage";
            prefs.edit().putString("accent", value).apply();
            rebuildUi();
        }).show();
    }

    private void chooseTimeout() {
        String[] values = {"Rapide · 300 ms", "Équilibré · 500 ms", "Précis · 800 ms"};
        new AlertDialog.Builder(this).setTitle("Délai de détection").setItems(values, (d, which) -> {
            int value = which == 0 ? 300 : which == 2 ? 800 : 500;
            prefs.edit().putInt("scan_timeout", value).apply();
            render();
        }).show();
    }

    private void startLanScan() {
        if (scanning) return;
        String local = getPrivateIpv4();
        if (local == null) {
            Toast.makeText(this, "Connecte-toi à un réseau local IPv4.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] parts = local.split("\\.");
        if (parts.length != 4) return;
        String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
        currentDevices.clear();
        scanning = true;
        log("Analyse LAN lancée sur " + prefix + "0/24.");
        render();
        AtomicInteger remaining = new AtomicInteger(254);
        int timeout = prefs.getInt("scan_timeout", 500);
        for (int i = 1; i <= 254; i++) {
            final String host = prefix + i;
            pool.submit(() -> {
                long start = System.nanoTime();
                boolean reachable = false;
                try { reachable = InetAddress.getByName(host).isReachable(timeout); } catch (Exception ignored) {}
                long latency = (System.nanoTime() - start) / 1_000_000;
                if (reachable) {
                    currentDevices.add(new DeviceItem(host, latency));
                    mainHandler.post(() -> registerSeenDevice(host, latency));
                }
                if (remaining.decrementAndGet() == 0) mainHandler.post(() -> {
                    currentDevices.sort(Comparator.comparingInt(d -> lastOctet(d.ip)));
                    scanning = false;
                    log("Analyse terminée : " + currentDevices.size() + " appareil(s) en ligne.");
                    render();
                });
            });
        }
    }

    private void registerSeenDevice(String ip, long latency) {
        Set<String> known = knownIps();
        boolean isNew = !known.contains(ip);
        if (isNew) {
            known.add(ip);
            prefs.edit().putStringSet("known_ips", known).apply();
            prefs.edit().putString("first_" + ip, now()).apply();
            prefs.edit().putString("name_" + ip, defaultDeviceName(ip)).apply();
            log("Nouvel appareil détecté : " + ip + ".");
            if (prefs.getBoolean("notify_new", true)) notifyNewDevice(ip);
        }
        int seen = prefs.getInt("seen_" + ip, 0) + 1;
        prefs.edit().putInt("seen_" + ip, seen).putString("last_" + ip, now()).putLong("latency_" + ip, latency).apply();
        if (selectedTab == 1) render();
    }

    private void showDeviceDialog(String ip) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText name = new EditText(this);
        name.setText(prefs.getString("name_" + ip, defaultDeviceName(ip)));
        name.setHint("Nom de l’appareil");
        box.addView(name);
        boolean trusted = prefs.getBoolean("trusted_" + ip, false);
        String[] actions = {trusted ? "Retirer la confiance" : "Marquer de confiance", "Ouvrir dans les outils avancés", "Oublier cet appareil"};
        new AlertDialog.Builder(this)
                .setTitle(privacyIp(ip))
                .setView(box)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String value = name.getText().toString().trim();
                    if (!value.isEmpty()) prefs.edit().putString("name_" + ip, value).apply();
                    render();
                })
                .setNeutralButton("Actions", null)
                .setNegativeButton("Fermer", null)
                .create()
                .setOnShowListener(dialog -> {
                    AlertDialog ad = (AlertDialog) dialog;
                    ad.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> new AlertDialog.Builder(this)
                            .setTitle("Actions appareil")
                            .setItems(actions, (x, which) -> {
                                if (which == 0) {
                                    prefs.edit().putBoolean("trusted_" + ip, !trusted).apply();
                                    log((trusted ? "Confiance retirée : " : "Appareil approuvé : ") + ip + ".");
                                    render();
                                } else if (which == 1) {
                                    prefs.edit().putString("last_tool_target", ip).apply();
                                    openClassicTools();
                                } else forgetDevice(ip);
                            }).show());
                });
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        // The actual dialog is created above through showDeviceEditor to avoid duplicate state.
        showDeviceEditor(ip, name, trusted, actions);
    }

    private void showDeviceEditor(String ip, EditText name, boolean trusted, String[] actions) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        if (name.getParent() != null) ((ViewGroup) name.getParent()).removeView(name);
        box.addView(name);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(privacyIp(ip))
                .setView(box)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String value = name.getText().toString().trim();
                    if (!value.isEmpty()) prefs.edit().putString("name_" + ip, value).apply();
                    render();
                })
                .setNeutralButton("Actions", null)
                .setNegativeButton("Fermer", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Actions appareil")
                .setItems(actions, (z, which) -> {
                    if (which == 0) {
                        prefs.edit().putBoolean("trusted_" + ip, !trusted).apply();
                        log((trusted ? "Confiance retirée : " : "Appareil approuvé : ") + ip + ".");
                        dialog.dismiss();
                        render();
                    } else if (which == 1) {
                        prefs.edit().putString("last_tool_target", ip).apply();
                        openClassicTools();
                    } else {
                        dialog.dismiss();
                        forgetDevice(ip);
                    }
                }).show()));
        dialog.show();
    }

    private void forgetDevice(String ip) {
        Set<String> known = knownIps();
        known.remove(ip);
        prefs.edit().putStringSet("known_ips", known)
                .remove("name_" + ip).remove("trusted_" + ip).remove("first_" + ip)
                .remove("last_" + ip).remove("seen_" + ip).remove("latency_" + ip).apply();
        log("Appareil oublié : " + ip + ".");
        render();
    }

    private void openClassicTools() {
        Runnable open = () -> startActivity(new Intent(this, MainActivity.class));
        if (!prefs.getBoolean("confirm_tools", true)) { open.run(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Outils réseau")
                .setMessage("Utilise les inspections uniquement sur tes propres appareils ou avec une autorisation explicite.")
                .setPositiveButton("Continuer", (d, w) -> open.run())
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this).setTitle("Effacer l’historique ?").setMessage("Cette action est définitive.")
                .setPositiveButton("Effacer", (d, w) -> { prefs.edit().remove("pro_history").apply(); render(); })
                .setNegativeButton("Annuler", null).show();
    }

    private void confirmResetInventory() {
        new AlertDialog.Builder(this).setTitle("Réinitialiser l’inventaire ?").setMessage("Les noms, statuts de confiance et dates seront supprimés.")
                .setPositiveButton("Réinitialiser", (d, w) -> {
                    for (String ip : knownIps()) {
                        prefs.edit().remove("name_" + ip).remove("trusted_" + ip).remove("first_" + ip)
                                .remove("last_" + ip).remove("seen_" + ip).remove("latency_" + ip).apply();
                    }
                    prefs.edit().remove("known_ips").apply();
                    currentDevices.clear();
                    log("Inventaire local réinitialisé.");
                    render();
                }).setNegativeButton("Annuler", null).show();
    }

    private Set<String> knownIps() {
        Set<String> stored = prefs.getStringSet("known_ips", new HashSet<>());
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    private boolean isOnline(String ip) {
        synchronized (currentDevices) { for (DeviceItem d : currentDevices) if (d.ip.equals(ip)) return true; }
        return false;
    }

    private String defaultDeviceName(String ip) {
        String local = getPrivateIpv4();
        return ip.equals(local) ? "Ce téléphone" : "Appareil " + lastOctet(ip);
    }

    private String privacyIp(String ip) {
        if (!prefs.getBoolean("privacy_mode", false)) return ip;
        int dot = ip.lastIndexOf('.');
        return dot > 0 ? ip.substring(0, dot) + ".•••" : "•••";
    }

    private NetworkInfo readNetworkInfo() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return new NetworkInfo("Indisponible", false, false, "—", "—");
        Network network = cm.getActiveNetwork();
        if (network == null) return new NetworkInfo("Déconnecté", false, false, "—", "—");
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
            for (LinkAddress address : props.getLinkAddresses()) addresses.add(address.toString());
            for (InetAddress server : props.getDnsServers()) dns.add(server.getHostAddress());
        }
        return new NetworkInfo(transport, validated, vpn, addresses.isEmpty() ? "—" : String.join("\n", addresses), dns.isEmpty() ? "—" : String.join(", ", dns));
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Alertes Sentry", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Nouveaux appareils détectés sur le réseau local");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void notifyNewDevice(String ip) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, CHANNEL)
                : new android.app.Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Nouvel appareil local")
                .setContentText(privacyIp(ip) + " vient d’être détecté")
                .setAutoCancel(true);
        manager.notify(Math.abs(ip.hashCode()), builder.build());
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        } else Toast.makeText(this, "Les notifications sont déjà disponibles.", Toast.LENGTH_SHORT).show();
    }

    private void log(String event) {
        if (!prefs.getBoolean("keep_history", true)) return;
        String old = prefs.getString("pro_history", "");
        String line = "[" + new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(new Date()) + "] " + event + "\n";
        String updated = line + (old == null ? "" : old);
        if (updated.length() > 18000) updated = updated.substring(0, 18000);
        prefs.edit().putString("pro_history", updated).apply();
    }

    private String lastHistoryLines(int count) {
        String history = prefs.getString("pro_history", "");
        if (history == null || history.trim().isEmpty()) return "Aucune activité enregistrée.";
        String[] lines = history.split("\n");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(count, lines.length); i++) out.append(lines[i]).append("\n");
        return out.toString().trim();
    }

    private int historyCount() {
        String history = prefs.getString("pro_history", "");
        if (history == null || history.trim().isEmpty()) return 0;
        return history.split("\n").length;
    }

    private String now() { return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(new Date()); }
    private int lastOctet(String ip) { try { return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1)); } catch (Exception e) { return 0; } }
    private String formatBytes(long value) {
        if (value < 0) return "Indisponible";
        if (value < 1024) return value + " o";
        if (value < 1024 * 1024) return String.format(Locale.FRANCE, "%.1f Ko", value / 1024d);
        return String.format(Locale.FRANCE, "%.1f Mo", value / (1024d * 1024d));
    }
    private String themeLabel() { String v = prefs.getString("theme", "dark"); return "soft".equals(v) ? "Doux clair" : "amoled".equals(v) ? "AMOLED" : "Sombre"; }
    private String accentLabel() { String v = prefs.getString("accent", "sage"); return "blue".equals(v) ? "Bleu" : "violet".equals(v) ? "Violet" : "amber".equals(v) ? "Ambre" : "Sauge"; }

    private LinearLayout page() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        int pad = prefs.getBoolean("compact", false) ? 12 : 18;
        p.setPadding(dp(pad), dp(7), dp(pad), dp(24));
        return p;
    }
    private ScrollView scroll(View child) { ScrollView s = new ScrollView(this); s.setFillViewport(true); s.addView(child, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); return s; }
    private LinearLayout card() { LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); int p = prefs.getBoolean("compact", false) ? 13 : 17; c.setPadding(dp(p), dp(p), dp(p), dp(p)); c.setBackground(rounded(surface, 23)); c.setElevation(dp(2)); return c; }
    private TextView sectionTitle(String value) { return text(value, 17, text, true); }
    private View row(String label, String value) {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.TOP); r.setPadding(0, dp(7), 0, dp(7));
        TextView left = text(label, 13, muted, false), right = text(value, 13, text, true); right.setGravity(Gravity.END);
        r.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .43f));
        r.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .57f));
        return r;
    }
    private TextView action(String label, boolean primary) { TextView a = text(label, 15, primary ? Color.rgb(17, 24, 21) : text, true); a.setGravity(Gravity.CENTER); a.setPadding(dp(15), dp(14), dp(15), dp(14)); a.setBackground(rounded(primary ? accent : surface2, 17)); return a; }
    private TextView text(String value, float size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(0, 1.12f); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return t; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View spaceW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private GradientDrawable rounded(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() { pool.shutdownNow(); super.onDestroy(); }

    private static class DeviceItem { final String ip; final long latency; DeviceItem(String ip, long latency) { this.ip = ip; this.latency = latency; } }
    private static class NetworkInfo { final String transport, addresses, dns; final boolean validated, vpn; NetworkInfo(String t, boolean v, boolean p, String a, String d) { transport=t; validated=v; vpn=p; addresses=a; dns=d; } }
}
