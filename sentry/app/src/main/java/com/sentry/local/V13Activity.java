package com.sentry.local;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sentry v13 Living Digital Twin.
 *
 * Extends the unified v12 interface with practical phone-only intelligence:
 * favorites, find-device guidance, an animated network graph, scan baselines,
 * room snapshots, daily stories, comparisons and a cinematic scan. Estimates
 * are explicitly presented as estimates; the app does not claim directional
 * ranging when Android only exposes signal strength.
 */
public class V13Activity extends V12Activity {
    private static final int BG = Color.rgb(2, 9, 10);
    private static final int PANEL = Color.rgb(10, 26, 26);
    private static final int PANEL_2 = Color.rgb(18, 45, 43);
    private static final int TEXT = Color.rgb(238, 253, 251);
    private static final int MUTED = Color.rgb(126, 164, 159);
    private static final int ACCENT = Color.rgb(54, 235, 198);
    private static final int BLUE = Color.rgb(79, 165, 255);
    private static final int GOOD = Color.rgb(83, 226, 143);
    private static final int WARN = Color.rgb(245, 188, 78);
    private static final int BAD = Color.rgb(244, 98, 123);
    private static final int VIOLET = Color.rgb(179, 132, 246);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences living;
    private boolean livingHubVisible;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        living = getSharedPreferences("sentry_v13", MODE_PRIVATE);
        ui.postDelayed(this::bindLivingNavigation, 260);
        ui.postDelayed(this::bindLivingNavigation, 900);
        ui.postDelayed(() -> {
            if (!living.contains("baseline")) living.edit().putString("baseline", snapshotJson()).apply();
        }, 3500);
        ui.postDelayed(navWatcher, 1400);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(navWatcher);
        super.onDestroy();
    }

    private final Runnable navWatcher = new Runnable() {
        @Override public void run() {
            bindLivingNavigation();
            ui.postDelayed(this, 1700);
        }
    };

    private void bindLivingNavigation() {
        LinearLayout nav = getField(V4Activity.class, "nav", LinearLayout.class);
        if (nav != null && nav.getChildCount() >= 6) {
            View hub = nav.getChildAt(5);
            if (hub instanceof TextView) {
                ((TextView) hub).setText("Living");
                hub.setContentDescription("Ouvrir Living Hub");
                hub.setOnClickListener(v -> showLivingHub());
            }
        }
        replaceTextRecursive(getWindow().getDecorView(), "S12", "S13");
        replaceTextRecursive(getWindow().getDecorView(), "S4", "S13");
    }

    private void showLivingHub() {
        livingHubVisible = true;
        FrameLayout content = getField(V4Activity.class, "content", FrameLayout.class);
        TextView title = getField(V4Activity.class, "title", TextView.class);
        TextView subtitle = getField(V4Activity.class, "subtitle", TextView.class);
        if (content == null) return;
        if (title != null) title.setText("LIVING DIGITAL TWIN");
        if (subtitle != null) subtitle.setText("Find · Graph · Changes · Stories");

        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(14), dp(8), dp(14), dp(26));
        root.addView(hero());
        root.addView(space(11));
        root.addView(statusStrip());

        section(root, "LIVE ACTIONS");
        addCard(root, "FIND MY DEVICE", "Guidage par puissance du signal, tendance et meilleure mesure", ACCENT, this::showFindPicker);
        addCard(root, "CINEMATIC SCAN", "Balayage animé avec présentation automatique des appareils", VIOLET, this::showCinematicScan);
        addCard(root, "SMART NETWORK GRAPH", "Relations vivantes entre le téléphone, le réseau et les appareils", BLUE, this::showNetworkGraph);

        section(root, "MEMORY & CHANGES");
        addCard(root, "WHAT CHANGED?", "Nouveaux, revenus, disparus ou fortement modifiés", WARN, this::showChangeDetection);
        addCard(root, "COMPARE SCANS", "Comparer l’état actuel avec un point de référence manuel", BLUE, this::showCompareScans);
        addCard(root, "SENTRY STORY", "Résumé quotidien local et explicable", ACCENT, this::showDailyStory);

        section(root, "ROOMS & OBJECTS");
        addCard(root, "ROOM SCAN", "Mémoriser les appareils et le Wi-Fi caractéristiques d’une pièce", GOOD, this::showRoomScan);
        addCard(root, "FAVORITES", "Épingler les objets importants et les retrouver rapidement", WARN, this::manageFavorites);
        addCard(root, "FOCUS DEVICE", "Ouvrir directement le dossier d’un appareil sélectionné", VIOLET, this::showFocusPicker);

        section(root, "V12 COMMAND TOOLS");
        addCard(root, "FULL SPATIAL SWEEP", "Actualiser Bluetooth, LAN et le jumeau spatial", ACCENT,
                () -> { invokePrivate(V4Activity.class, "startSpatialSweep"); toast("Balayage spatial lancé."); });
        addCard(root, "NETWORK X-RAY", "Wi-Fi, transport, IP, DNS et routes", BLUE,
                () -> invokePrivate(V10Activity.class, "showNetworkXray"));
        addCard(root, "LIVE TELEMETRY", "Batterie, capteurs, orientation et trafic", GOOD,
                () -> invokePrivate(V10Activity.class, "showTelemetry"));
        addCard(root, "SECURITY & CAPABILITIES", "Audit Android et matrice des fonctions disponibles", WARN, this::showSystemChoices);
        addCard(root, "UNIFIED V12 HUB", "Tous les outils techniques et réglages avancés", MUTED, this::openV12Hub);

        scroll.addView(root);
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activateLivingNav();
    }

    private View hero() {
        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(rounded(PANEL, 21));
        TextView eyebrow = text("SENTRY V13", 11, ACCENT, true);
        eyebrow.setLetterSpacing(.15f);
        card.addView(eyebrow);
        card.addView(space(5));
        card.addView(text("Living Digital Twin", 26, TEXT, true));
        card.addView(space(5));
        card.addView(text("L’environnement devient une mémoire vivante : objets favoris, changements, pièces, graphes et recherche de proximité.", 13, MUTED, false));
        return card;
    }

    private View statusStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric("ONLINE", String.valueOf(onlineDevices().size()), GOOD), weight());
        row.addView(spaceW(7));
        row.addView(metric("FAVORITES", String.valueOf(favorites().size()), WARN), weight());
        row.addView(spaceW(7));
        row.addView(metric("ROOM", bestRoomShort(), ACCENT), weight());
        return row;
    }

    private void activateLivingNav() {
        LinearLayout nav = getField(V4Activity.class, "nav", LinearLayout.class);
        if (nav == null) return;
        for (int i = 0; i < nav.getChildCount(); i++) {
            View child = nav.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            boolean active = i == 5;
            ((TextView) child).setTextColor(active ? Color.rgb(2, 25, 20) : MUTED);
            child.setBackground(active ? rounded(ACCENT, 17) : rounded(Color.TRANSPARENT, 17));
        }
    }

    private void showFindPicker() {
        List<DeviceInfo> devices = sortedDevices(true);
        if (devices.isEmpty()) { toast("Aucun appareil Bluetooth récent à suivre."); return; }
        String[] labels = new String[devices.size()];
        Set<String> fav = favorites();
        for (int i = 0; i < devices.size(); i++) {
            DeviceInfo d = devices.get(i);
            labels[i] = (fav.contains(d.id) ? "★ " : "") + d.name + "\n" + signalLabel(d.rssi) + " · " + d.source;
        }
        new AlertDialog.Builder(this).setTitle("Find My Device").setItems(labels, (dialog, which) -> showFinder(devices.get(which).id)).setNegativeButton("Annuler", null).show();
    }

    private void showFinder(String id) {
        invokePrivate(V4Activity.class, "startSpatialSweep");
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        FinderView radar = new FinderView(this, id);
        root.addView(radar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView status = text("Lecture du signal…", 14, TEXT, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8), dp(10), dp(8), dp(10));
        root.addView(status);
        TextView note = text("Le téléphone ne fournit pas une direction réelle. Marche lentement : Sentry compare la puissance du signal et indique si tu te rapproches.", 11, MUTED, false);
        note.setGravity(Gravity.CENTER);
        root.addView(note);
        root.addView(space(12));
        TextView close = action("FERMER", PANEL_2, TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(close);
        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> radar.stop());
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) { w.setBackgroundDrawable(rounded(BG, 0)); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }

        Runnable updater = new Runnable() {
            int previous = -127;
            int best = -127;
            @Override public void run() {
                if (!dialog.isShowing()) return;
                DeviceInfo current = findDevice(id);
                if (current == null) {
                    status.setText("Appareil non observé pendant ce scan");
                } else {
                    int rssi = current.rssi;
                    if (rssi > best) best = rssi;
                    String trend = previous == -127 ? "stabilisation" : rssi >= previous + 3 ? "tu te rapproches" : rssi <= previous - 3 ? "tu t’éloignes" : "signal stable";
                    status.setText(current.name + " · " + rssi + " dBm\n" + trend + " · meilleur " + best + " dBm");
                    radar.update(rssi, trend, current.name);
                    previous = rssi;
                }
                ui.postDelayed(this, 850);
            }
        };
        ui.post(updater);
    }

    private void showNetworkGraph() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout frame = new FrameLayout(this);
        NetworkGraphView graph = new NetworkGraphView(this);
        frame.addView(graph, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView close = action("FERMER", PANEL_2, TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.setMargins(dp(18), 0, dp(18), dp(20));
        frame.addView(close, lp);
        dialog.setContentView(frame);
        dialog.setOnDismissListener(d -> graph.stop());
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) { w.setBackgroundDrawable(rounded(BG, 0)); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
    }

    private void showChangeDetection() {
        String baseline = living.getString("baseline", "");
        String current = snapshotJson();
        String report = compareSnapshots(baseline, current, true);
        new AlertDialog.Builder(this).setTitle("What Changed?").setMessage(report)
                .setPositiveButton("Mettre à jour la référence", (d, w) -> { living.edit().putString("baseline", current).putLong("baseline_at", System.currentTimeMillis()).apply(); toast("Nouvelle référence enregistrée."); })
                .setNeutralButton("Relancer un scan", (d, w) -> invokePrivate(V4Activity.class, "startSpatialSweep"))
                .setNegativeButton("Fermer", null).show();
    }

    private void showCompareScans() {
        String a = living.getString("compare_a", "");
        String now = snapshotJson();
        if (a.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Compare Scans").setMessage("Aucun scan A n’est enregistré. Enregistre l’état actuel, puis reviens plus tard pour comparer.")
                    .setPositiveButton("Enregistrer scan A", (d, w) -> living.edit().putString("compare_a", now).putLong("compare_a_at", System.currentTimeMillis()).apply())
                    .setNegativeButton("Annuler", null).show();
            return;
        }
        String report = compareSnapshots(a, now, false);
        long at = living.getLong("compare_a_at", 0);
        String prefix = "SCAN A\n" + (at == 0 ? "date inconnue" : date(at)) + "\n\n";
        new AlertDialog.Builder(this).setTitle("Compare Scans").setMessage(prefix + report)
                .setPositiveButton("Remplacer scan A", (d, w) -> living.edit().putString("compare_a", now).putLong("compare_a_at", System.currentTimeMillis()).apply())
                .setNegativeButton("Fermer", null).show();
    }

    private void showDailyStory() {
        List<DeviceInfo> all = sortedDevices(false);
        List<DeviceInfo> online = onlineDevices();
        Set<String> fav = favorites();
        int ble = 0, lan = 0;
        for (DeviceInfo d : online) if (d.source.toLowerCase(Locale.ROOT).contains("blue")) ble++; else lan++;
        StringBuilder b = new StringBuilder();
        b.append("SENTRY STORY · ").append(new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(new Date())).append("\n\n");
        b.append("Le jumeau connaît ").append(all.size()).append(" objets, dont ").append(online.size()).append(" observés récemment. ");
        b.append(ble).append(" sont visibles par Bluetooth et ").append(lan).append(" par le réseau ou d’autres sources.\n\n");
        b.append("ZONE PROBABLE\n").append(bestRoomLong()).append("\n\n");
        b.append("OBJETS IMPORTANTS\n");
        int shown = 0;
        for (DeviceInfo d : sortedDevices(true)) {
            if (!fav.contains(d.id) && shown >= 2) continue;
            b.append("• ").append(fav.contains(d.id) ? "★ " : "").append(d.name).append(" · ").append(signalLabel(d.rssi)).append(" · ").append(d.room).append('\n');
            if (++shown >= 5) break;
        }
        if (shown == 0) b.append("• Aucun objet récent\n");
        b.append("\nCHANGEMENTS\n").append(compactChangeSummary(living.getString("baseline", ""), snapshotJson())).append("\n\n");
        b.append("Cette Story est générée localement à partir des observations enregistrées. Les positions et distances restent estimées.");
        living.edit().putString("last_story", b.toString()).putLong("last_story_at", System.currentTimeMillis()).apply();
        showText("Sentry Story", b.toString());
    }

    private void showRoomScan() {
        Set<String> rooms = living.getStringSet("room_names", new HashSet<>());
        StringBuilder summary = new StringBuilder("Pièce la plus ressemblante :\n").append(bestRoomLong()).append("\n\nPièces mémorisées : ");
        summary.append(rooms.isEmpty() ? "aucune" : android.text.TextUtils.join(", ", rooms));
        new AlertDialog.Builder(this).setTitle("Room Scan").setMessage(summary.toString())
                .setPositiveButton("Scanner cette pièce", (d, w) -> askRoomName())
                .setNeutralButton("Gérer", (d, w) -> manageRooms())
                .setNegativeButton("Fermer", null).show();
    }

    private void askRoomName() {
        EditText input = new EditText(this);
        input.setHint("Ex. Bureau");
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(rounded(PANEL_2, 14));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Nom de la pièce").setView(wrap)
                .setPositiveButton("Enregistrer", (d, w) -> saveRoom(input.getText().toString().trim()))
                .setNegativeButton("Annuler", null).show();
    }

    private void saveRoom(String room) {
        if (room.isEmpty()) { toast("Nom invalide."); return; }
        try {
            JSONObject data = new JSONObject();
            data.put("time", System.currentTimeMillis());
            WifiInfo wifi = wifiInfo();
            if (wifi != null) { data.put("bssid", String.valueOf(wifi.getBSSID())); data.put("rssi", wifi.getRssi()); }
            JSONArray ids = new JSONArray();
            for (DeviceInfo d : onlineDevices()) ids.put(d.id);
            data.put("devices", ids);
            String key = safe(room);
            Set<String> names = new HashSet<>(living.getStringSet("room_names", new HashSet<>()));
            names.add(room);
            living.edit().putStringSet("room_names", names).putString("room_" + key, data.toString()).apply();
            toast(room + " mémorisée avec " + ids.length() + " objets.");
        } catch (Exception e) { toast("Room Scan impossible."); }
    }

    private void manageRooms() {
        List<String> rooms = new ArrayList<>(living.getStringSet("room_names", new HashSet<>()));
        Collections.sort(rooms);
        if (rooms.isEmpty()) { toast("Aucune pièce mémorisée."); return; }
        String[] labels = rooms.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Pièces mémorisées").setItems(labels, (d, which) -> {
            String room = rooms.get(which);
            new AlertDialog.Builder(this).setTitle(room).setMessage("Supprimer l’empreinte de cette pièce ?")
                    .setPositiveButton("Supprimer", (x, y) -> {
                        Set<String> names = new HashSet<>(living.getStringSet("room_names", new HashSet<>()));
                        names.remove(room);
                        living.edit().putStringSet("room_names", names).remove("room_" + safe(room)).apply();
                    }).setNegativeButton("Annuler", null).show();
        }).setNegativeButton("Fermer", null).show();
    }

    private void manageFavorites() {
        List<DeviceInfo> devices = sortedDevices(false);
        if (devices.isEmpty()) { toast("Aucun appareil connu."); return; }
        Set<String> selected = new HashSet<>(favorites());
        String[] labels = new String[devices.size()];
        boolean[] checked = new boolean[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            labels[i] = devices.get(i).name + " · " + devices.get(i).source;
            checked[i] = selected.contains(devices.get(i).id);
        }
        new AlertDialog.Builder(this).setTitle("Favorites").setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
            if (isChecked) selected.add(devices.get(which).id); else selected.remove(devices.get(which).id);
        }).setPositiveButton("Enregistrer", (d, w) -> { living.edit().putStringSet("favorites", selected).apply(); toast(selected.size() + " favoris enregistrés."); if (livingHubVisible) showLivingHub(); })
                .setNegativeButton("Annuler", null).show();
    }

    private void showFocusPicker() {
        List<DeviceInfo> devices = sortedDevices(true);
        if (devices.isEmpty()) { toast("Aucun appareil connu."); return; }
        String[] labels = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) labels[i] = devices.get(i).name + "\n" + devices.get(i).type + " · " + devices.get(i).room;
        new AlertDialog.Builder(this).setTitle("Focus Device").setItems(labels, (d, which) -> {
            DeviceInfo chosen = devices.get(which);
            try {
                Field selected = V4Activity.class.getDeclaredField("selectedId");
                selected.setAccessible(true);
                selected.set(this, chosen.id);
                openSpatialTab(1);
            } catch (Exception e) { toast("Impossible d’ouvrir le dossier."); }
        }).setNegativeButton("Annuler", null).show();
    }

    private void showCinematicScan() {
        invokePrivate(V4Activity.class, "startSpatialSweep");
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout frame = new FrameLayout(this);
        CinematicView view = new CinematicView(this);
        frame.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView close = action("QUITTER LE MODE CINÉMATIQUE", PANEL_2, TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.setMargins(dp(18), 0, dp(18), dp(18));
        frame.addView(close, lp);
        dialog.setContentView(frame);
        dialog.setOnDismissListener(d -> view.stop());
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) { w.setBackgroundDrawable(rounded(Color.BLACK, 0)); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
    }

    private void showSystemChoices() {
        String[] choices = {"Security Audit", "Capability Matrix", "Device Compatibility", "Performance Profile"};
        new AlertDialog.Builder(this).setTitle("Security & Capabilities").setItems(choices, (d, which) -> {
            if (which == 0) invokePrivate(V10Activity.class, "showSecurityAudit");
            else if (which == 1) invokePrivate(V10Activity.class, "showCapabilities");
            else if (which == 2) invokePrivate(V11Activity.class, "showCompatibilityReport");
            else invokePrivate(V11Activity.class, "choosePerformanceProfile");
        }).setNegativeButton("Fermer", null).show();
    }

    private void openV12Hub() {
        try {
            Method m = V12Activity.class.getDeclaredMethod("showHubPage");
            m.setAccessible(true);
            m.invoke(this);
            livingHubVisible = false;
        } catch (Exception e) { toast("Hub technique indisponible."); }
    }

    private String snapshotJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("time", System.currentTimeMillis());
            JSONObject objects = new JSONObject();
            for (DeviceInfo d : allDevices()) {
                JSONObject o = new JSONObject();
                o.put("name", d.name); o.put("rssi", d.rssi); o.put("room", d.room);
                o.put("online", d.online); o.put("source", d.source); o.put("last", d.lastSeen);
                objects.put(d.id, o);
            }
            root.put("devices", objects);
            return root.toString();
        } catch (Exception e) { return "{}"; }
    }

    private String compareSnapshots(String oldRaw, String newRaw, boolean friendly) {
        try {
            JSONObject oldObjects = oldRaw.isEmpty() ? new JSONObject() : new JSONObject(oldRaw).optJSONObject("devices");
            JSONObject newObjects = new JSONObject(newRaw).optJSONObject("devices");
            if (oldObjects == null) oldObjects = new JSONObject();
            if (newObjects == null) newObjects = new JSONObject();
            List<String> fresh = new ArrayList<>(), returned = new ArrayList<>(), missing = new ArrayList<>(), changed = new ArrayList<>();
            JSONArray newNames = newObjects.names();
            if (newNames != null) for (int i = 0; i < newNames.length(); i++) {
                String id = newNames.getString(i); JSONObject now = newObjects.getJSONObject(id);
                if (!oldObjects.has(id)) fresh.add(now.optString("name", id));
                else {
                    JSONObject before = oldObjects.getJSONObject(id);
                    if (!before.optBoolean("online") && now.optBoolean("online")) returned.add(now.optString("name", id));
                    if (Math.abs(before.optInt("rssi", -100) - now.optInt("rssi", -100)) >= 20 || !before.optString("room").equals(now.optString("room"))) changed.add(now.optString("name", id));
                }
            }
            JSONArray oldNames = oldObjects.names();
            if (oldNames != null) for (int i = 0; i < oldNames.length(); i++) {
                String id = oldNames.getString(i); JSONObject before = oldObjects.getJSONObject(id);
                JSONObject now = newObjects.optJSONObject(id);
                if (before.optBoolean("online") && (now == null || !now.optBoolean("online"))) missing.add(before.optString("name", id));
            }
            StringBuilder b = new StringBuilder();
            appendGroup(b, "NOUVEAUX", fresh); appendGroup(b, "REVENUS", returned); appendGroup(b, "DISPARUS RÉCEMMENT", missing); appendGroup(b, "SIGNAL OU PIÈCE MODIFIÉ", changed);
            if (b.length() == 0) b.append(friendly ? "Aucun changement important détecté depuis la référence." : "Les deux scans sont très proches.");
            b.append("\n\nLes changements reposent sur les observations accessibles à Android et peuvent inclure des variations normales de signal.");
            return b.toString();
        } catch (Exception e) { return "Comparaison impossible : données de référence invalides."; }
    }

    private String compactChangeSummary(String oldRaw, String newRaw) {
        String full = compareSnapshots(oldRaw, newRaw, true);
        int note = full.indexOf("\n\nLes changements");
        return note > 0 ? full.substring(0, note) : full;
    }

    private void appendGroup(StringBuilder b, String title, List<String> items) {
        if (items.isEmpty()) return;
        if (b.length() > 0) b.append('\n');
        b.append(title).append(" · ").append(items.size()).append('\n');
        int max = Math.min(6, items.size());
        for (int i = 0; i < max; i++) b.append("• ").append(items.get(i)).append('\n');
        if (items.size() > max) b.append("• +").append(items.size() - max).append(" autres\n");
    }

    private String bestRoomShort() {
        String room = bestRoomLong();
        int cut = room.indexOf('·');
        String value = cut > 0 ? room.substring(0, cut).trim() : room;
        return value.length() > 10 ? value.substring(0, 10) : value;
    }

    private String bestRoomLong() {
        Set<String> rooms = living.getStringSet("room_names", new HashSet<>());
        if (rooms.isEmpty()) return "Non calibrée · lance Room Scan";
        Set<String> current = new HashSet<>();
        for (DeviceInfo d : onlineDevices()) current.add(d.id);
        WifiInfo wifi = wifiInfo();
        String currentBssid = wifi == null ? "" : String.valueOf(wifi.getBSSID());
        String best = "Inconnue"; int bestScore = -1;
        for (String room : rooms) {
            try {
                JSONObject data = new JSONObject(living.getString("room_" + safe(room), "{}"));
                JSONArray ids = data.optJSONArray("devices");
                Set<String> reference = new HashSet<>();
                if (ids != null) for (int i = 0; i < ids.length(); i++) reference.add(ids.getString(i));
                int intersection = 0;
                for (String id : current) if (reference.contains(id)) intersection++;
                int union = current.size() + reference.size() - intersection;
                int score = union == 0 ? 0 : Math.round(intersection * 100f / union);
                if (!currentBssid.isEmpty() && currentBssid.equalsIgnoreCase(data.optString("bssid"))) score += 20;
                if (score > bestScore) { bestScore = score; best = room; }
            } catch (Exception ignored) { }
        }
        return best + " · confiance " + Math.max(15, Math.min(95, bestScore)) + " %";
    }

    private WifiInfo wifiInfo() {
        try {
            WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            return manager == null ? null : manager.getConnectionInfo();
        } catch (Exception e) { return null; }
    }

    private Set<String> favorites() { return new HashSet<>(living.getStringSet("favorites", new HashSet<>())); }

    private List<DeviceInfo> allDevices() {
        List<DeviceInfo> result = new ArrayList<>();
        try {
            Field field = V4Activity.class.getDeclaredField("devices");
            field.setAccessible(true);
            Object raw = field.get(this);
            if (!(raw instanceof Map)) return result;
            for (Object entryObj : ((Map<?, ?>) raw).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObj;
                Object object = entry.getValue();
                DeviceInfo d = new DeviceInfo();
                d.id = String.valueOf(entry.getKey());
                d.name = readString(object, "name", "Appareil");
                d.type = readString(object, "type", "unknown");
                d.source = readString(object, "source", "unknown");
                d.room = readString(object, "room", "Non assignée");
                d.rssi = readInt(object, "rssi", -100);
                d.lastSeen = readLong(object, "lastSeen", 0);
                d.online = readBoolean(object, "online", System.currentTimeMillis() - d.lastSeen < 180000);
                result.add(d);
            }
        } catch (Exception ignored) { }
        return result;
    }

    private List<DeviceInfo> onlineDevices() {
        List<DeviceInfo> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (DeviceInfo d : allDevices()) if (d.online || now - d.lastSeen < 180000) result.add(d);
        return result;
    }

    private List<DeviceInfo> sortedDevices(boolean recentFirst) {
        List<DeviceInfo> list = allDevices();
        Set<String> fav = favorites();
        list.sort((a, b) -> {
            int f = Boolean.compare(fav.contains(b.id), fav.contains(a.id));
            if (f != 0) return f;
            if (recentFirst) {
                int on = Boolean.compare(b.online, a.online);
                if (on != 0) return on;
                return Integer.compare(b.rssi, a.rssi);
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        return list;
    }

    private DeviceInfo findDevice(String id) {
        for (DeviceInfo d : allDevices()) if (d.id.equals(id)) return d;
        return null;
    }

    private String signalLabel(int rssi) {
        if (rssi >= -52) return "très proche";
        if (rssi >= -64) return "proche";
        if (rssi >= -76) return "distance moyenne";
        if (rssi >= -88) return "éloigné";
        return "très éloigné";
    }

    private String readString(Object object, String name, String fallback) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); Object value = f.get(object); return value == null ? fallback : String.valueOf(value); } catch (Exception e) { return fallback; }
    }
    private int readInt(Object object, String name, int fallback) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.getInt(object); } catch (Exception e) { return fallback; }
    }
    private long readLong(Object object, String name, long fallback) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.getLong(object); } catch (Exception e) { return fallback; }
    }
    private boolean readBoolean(Object object, String name, boolean fallback) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.getBoolean(object); } catch (Exception e) { return fallback; }
    }

    private void openSpatialTab(int target) {
        try {
            Field tab = V4Activity.class.getDeclaredField("tab"); tab.setAccessible(true); tab.setInt(this, target);
            Method render = V4Activity.class.getDeclaredMethod("render"); render.setAccessible(true); render.invoke(this);
            livingHubVisible = false;
            ui.postDelayed(this::bindLivingNavigation, 70);
        } catch (Exception e) { toast("Page indisponible."); }
    }

    private void invokePrivate(Class<?> owner, String name) { invokePrivate(owner, name, new Class[0], new Object[0]); }
    private Object invokePrivate(Class<?> owner, String name, Class<?>[] types, Object[] args) {
        try { Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(this, args); }
        catch (Exception e) { toast("Fonction indisponible sur ce téléphone."); return null; }
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Class<?> owner, String name, Class<T> type) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); Object value = field.get(this); return type.isInstance(value) ? (T) value : null; }
        catch (Exception e) { return null; }
    }

    private void replaceTextRecursive(View view, String from, String to) {
        if (view instanceof TextView && from.contentEquals(((TextView) view).getText())) ((TextView) view).setText(to);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) replaceTextRecursive(group.getChildAt(i), from, to);
        }
    }

    private void showText(String title, String body) {
        TextView view = text(body, 13, TEXT, false); view.setTextIsSelectable(true); view.setPadding(dp(18), dp(10), dp(18), dp(18));
        ScrollView scroll = new ScrollView(this); scroll.addView(view);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Fermer", null).show();
    }

    private void section(LinearLayout root, String title) {
        root.addView(space(15)); TextView label = text(title, 10.5f, ACCENT, true); label.setLetterSpacing(.12f); root.addView(label); root.addView(space(7));
    }

    private void addCard(LinearLayout root, String title, String subtitle, int color, Runnable action) {
        LinearLayout card = new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(14), dp(13), dp(14), dp(13)); card.setBackground(rounded(PANEL, 17));
        TextView marker = text("•", 24, color, true); marker.setGravity(Gravity.CENTER); card.addView(marker, new LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout labels = column(); labels.addView(text(title, 14, TEXT, true)); labels.addView(text(subtitle, 11.5f, MUTED, false));
        card.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)); card.addView(text("›", 27, color, false));
        card.setContentDescription(title + ". " + subtitle); card.setOnClickListener(v -> action.run()); root.addView(card); root.addView(space(8));
    }

    private TextView metric(String label, String value, int color) {
        TextView view = text(label + "\n" + value, 9.5f, color, true); view.setGravity(Gravity.CENTER); view.setPadding(dp(4), dp(10), dp(4), dp(10)); view.setBackground(rounded(PANEL, 14)); return view;
    }
    private TextView action(String label, int background, int foreground) {
        TextView view = text(label, 13, foreground, true); view.setGravity(Gravity.CENTER); view.setPadding(dp(12), dp(15), dp(12), dp(15)); view.setBackground(rounded(background, 16)); return view;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }
    private TextView text(String value, float size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(0, 1.12f); if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); return t; }
    private GradientDrawable rounded(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View spaceW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private String safe(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_"); }
    private String date(long ms) { return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(new Date(ms)); }

    private class FinderView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int rssi = -100;
        private String trend = "stabilisation";
        private String name = "Appareil";
        private boolean running = true;
        private final long born = System.currentTimeMillis();
        FinderView(Context context, String id) { super(context); setBackgroundColor(BG); DeviceInfo d = findDevice(id); if (d != null) { rssi = d.rssi; name = d.name; } }
        void update(int value, String direction, String deviceName) { rssi = value; trend = direction; name = deviceName; invalidate(); }
        void stop() { running = false; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float cx = getWidth()/2f, cy = getHeight()/2f; float max = Math.min(getWidth(), getHeight())*.39f;
            float pulse = ((System.currentTimeMillis()-born)%1800)/1800f;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            for (int i=1;i<=5;i++) { paint.setColor(Color.argb(65,54,235,198)); c.drawCircle(cx,cy,max*i/5f,paint); }
            paint.setColor(Color.argb((int)(120*(1-pulse)),54,235,198)); paint.setStrokeWidth(dp(3)); c.drawCircle(cx,cy,max*pulse,paint);
            float strength = Math.max(0f,Math.min(1f,(rssi+100)/55f));
            paint.setStyle(Paint.Style.FILL); paint.setColor(strength>.7f?GOOD:strength>.4f?WARN:BAD); c.drawCircle(cx,cy,dp(18)+max*.22f*strength,paint);
            paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextSize(dp(25)); paint.setColor(TEXT); c.drawText(name,cx,dp(58),paint);
            paint.setTextSize(dp(15)); paint.setColor(ACCENT); c.drawText(rssi+" dBm · "+signalLabel(rssi),cx,dp(85),paint);
            paint.setTextSize(dp(20)); paint.setColor(TEXT); c.drawText(trend.toUpperCase(Locale.FRANCE),cx,cy+max*.58f,paint);
            if (running) postInvalidateDelayed(32);
        }
    }

    private class NetworkGraphView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean running = true;
        private final long born = System.currentTimeMillis();
        NetworkGraphView(Context context) { super(context); setBackgroundColor(BG); }
        void stop() { running = false; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); List<DeviceInfo> devices = sortedDevices(true); int count = Math.min(24, devices.size()); float cx=getWidth()/2f, cy=getHeight()/2f; float radius=Math.min(getWidth(),getHeight())*.34f;
            paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setColor(TEXT); paint.setTextSize(dp(24)); c.drawText("SMART NETWORK GRAPH",cx,dp(52),paint);
            paint.setTextSize(dp(12)); paint.setColor(MUTED); c.drawText(count+" nœuds visibles · liens = observations",cx,dp(75),paint);
            float pulse=((System.currentTimeMillis()-born)%2400)/2400f;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(Color.argb(70,54,235,198)); c.drawCircle(cx,cy,radius,paint);
            Set<String> fav=favorites();
            for(int i=0;i<count;i++) { DeviceInfo d=devices.get(i); double a=Math.PI*2*i/Math.max(1,count)-Math.PI/2; float x=cx+(float)Math.cos(a)*radius; float y=cy+(float)Math.sin(a)*radius;
                int alpha=d.online?130:45; paint.setColor(Color.argb(alpha,79,165,255)); paint.setStrokeWidth(fav.contains(d.id)?dp(3):dp(1)); c.drawLine(cx,cy,x,y,paint);
                float node=fav.contains(d.id)?dp(10):dp(7); paint.setStyle(Paint.Style.FILL); paint.setColor(fav.contains(d.id)?WARN:(d.online?ACCENT:MUTED)); c.drawCircle(x,y,node,paint);
                if(count<=12||fav.contains(d.id)){paint.setColor(TEXT);paint.setTextSize(dp(9));paint.setTextAlign(Paint.Align.CENTER);c.drawText(shortName(d.name,13),x,y+dp(20),paint);} paint.setStyle(Paint.Style.STROKE);
                float px=cx+(x-cx)*pulse, py=cy+(y-cy)*pulse; paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb((int)(180*(1-pulse)),54,235,198)); c.drawCircle(px,py,dp(3),paint); paint.setStyle(Paint.Style.STROKE);
            }
            paint.setStyle(Paint.Style.FILL); paint.setColor(BLUE); c.drawCircle(cx,cy,dp(27),paint); paint.setColor(Color.rgb(2,20,24)); paint.setTextSize(dp(11)); paint.setTypeface(Typeface.DEFAULT_BOLD); c.drawText("PHONE",cx,cy+dp(4),paint);
            if(running) postInvalidateDelayed(40);
        }
    }

    private class CinematicView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean running=true; private final long born=System.currentTimeMillis();
        CinematicView(Context context){super(context);setBackgroundColor(Color.BLACK);}
        void stop(){running=false;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);List<DeviceInfo> devices=sortedDevices(true);float cx=getWidth()/2f,cy=getHeight()/2f;long age=System.currentTimeMillis()-born;float phase=(age%5000)/5000f;
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(2,10,10));c.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));for(int i=1;i<=7;i++){paint.setColor(Color.argb(35,54,235,198));c.drawCircle(cx,cy,Math.min(getWidth(),getHeight())*i/13f,paint);}float sweep=phase*360f;paint.setColor(Color.argb(85,54,235,198));paint.setStrokeWidth(dp(4));RectF oval=new RectF(cx-dp(145),cy-dp(145),cx+dp(145),cy+dp(145));c.drawArc(oval,sweep-32,32,false,paint);
            DeviceInfo d=devices.isEmpty()?null:devices.get((int)((age/2800)%devices.size()));paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setStyle(Paint.Style.FILL);paint.setColor(ACCENT);paint.setTextSize(dp(13));c.drawText("SENTRY V13 · CINEMATIC SWEEP",cx,dp(54),paint);
            if(d==null){paint.setColor(TEXT);paint.setTextSize(dp(25));c.drawText("RECHERCHE D’APPAREILS",cx,cy,paint);}else{float glow=dp(42)+dp(12)*(float)Math.sin(age/240.0);paint.setColor(Color.argb(65,54,235,198));c.drawCircle(cx,cy,glow,paint);paint.setColor(ACCENT);c.drawCircle(cx,cy,dp(20),paint);paint.setColor(TEXT);paint.setTextSize(dp(27));c.drawText(d.name,cx,cy+dp(85),paint);paint.setTextSize(dp(14));paint.setColor(MUTED);c.drawText(d.type+" · "+d.source+" · "+signalLabel(d.rssi),cx,cy+dp(113),paint);paint.setTextSize(dp(12));c.drawText(d.room+" · "+d.rssi+" dBm",cx,cy+dp(138),paint);}
            paint.setColor(MUTED);paint.setTextSize(dp(11));c.drawText("Présentation locale · positions estimées",cx,getHeight()-dp(82),paint);if(running)postInvalidateDelayed(32);}
    }

    private String shortName(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"…";}

    private static class DeviceInfo {
        String id=""; String name="Appareil"; String type="unknown"; String source="unknown"; String room="Non assignée"; int rssi=-100; long lastSeen; boolean online;
    }
}
