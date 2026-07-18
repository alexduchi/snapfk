package com.sentry.local;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sentry v14 Spatial Intelligence.
 *
 * Adds durable local history, searchable device dossiers, confidence scores,
 * alerts, manual identity merging, place profiles, a Wi-Fi heatmap and an
 * adaptive scan loop. Everything stays phone-only and uses only data Android
 * actually exposes. Background scanning is deliberately not faked: the smart
 * loop runs while the app is open and respects the selected performance mode.
 */
public class V14Activity extends V13Activity {
    private static final int BG = Color.rgb(2, 9, 11);
    private static final int PANEL = Color.rgb(10, 27, 28);
    private static final int PANEL_2 = Color.rgb(18, 45, 46);
    private static final int TEXT = Color.rgb(239, 253, 252);
    private static final int MUTED = Color.rgb(128, 165, 161);
    private static final int ACCENT = Color.rgb(55, 235, 199);
    private static final int BLUE = Color.rgb(80, 168, 255);
    private static final int GOOD = Color.rgb(84, 227, 144);
    private static final int WARN = Color.rgb(246, 190, 80);
    private static final int BAD = Color.rgb(244, 99, 124);
    private static final int VIOLET = Color.rgb(180, 132, 246);
    private static final int REQ_EXPORT_V14 = 14014;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences intel;
    private boolean intelHubVisible;
    private String pendingExport = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        intel = getSharedPreferences("sentry_v14", MODE_PRIVATE);
        ensureDefaults();
        ui.postDelayed(this::bindIntelNavigation, 320);
        ui.postDelayed(this::captureSnapshotAndAlerts, 2800);
        ui.postDelayed(navWatcher, 1400);
        ui.postDelayed(historyLoop, scanInterval());
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(navWatcher);
        ui.removeCallbacks(historyLoop);
        super.onDestroy();
    }

    private final Runnable navWatcher = new Runnable() {
        @Override public void run() {
            bindIntelNavigation();
            ui.postDelayed(this, 1700);
        }
    };

    private final Runnable historyLoop = new Runnable() {
        @Override public void run() {
            if (intel.getBoolean("smart_scan", true)) {
                invokePrivate(V4Activity.class, "startSpatialSweep");
                ui.postDelayed(V14Activity.this::captureSnapshotAndAlerts, 1800);
            }
            ui.postDelayed(this, scanInterval());
        }
    };

    private void ensureDefaults() {
        SharedPreferences.Editor e = intel.edit();
        if (!intel.contains("smart_scan")) e.putBoolean("smart_scan", true);
        if (!intel.contains("scan_mode")) e.putString("scan_mode", "balanced");
        if (!intel.contains("active_place")) e.putString("active_place", "Maison");
        if (!intel.contains("place_names")) {
            Set<String> names = new HashSet<>();
            names.add("Maison");
            e.putStringSet("place_names", names);
        }
        e.apply();
    }

    private long scanInterval() {
        String mode = intel == null ? "balanced" : intel.getString("scan_mode", "balanced");
        if ("fast".equals(mode)) return 30000L;
        if ("eco".equals(mode)) return 180000L;
        return 75000L;
    }

    private void bindIntelNavigation() {
        LinearLayout nav = getField(V4Activity.class, "nav", LinearLayout.class);
        if (nav != null && nav.getChildCount() >= 6) {
            View hub = nav.getChildAt(5);
            if (hub instanceof TextView) {
                ((TextView) hub).setText("Intel");
                hub.setContentDescription("Ouvrir Spatial Intelligence");
                hub.setOnClickListener(v -> showIntelHub());
            }
        }
        replaceTextRecursive(getWindow().getDecorView(), "S13", "S14");
        replaceTextRecursive(getWindow().getDecorView(), "S12", "S14");
        replaceTextRecursive(getWindow().getDecorView(), "S4", "S14");
    }

    private void showIntelHub() {
        intelHubVisible = true;
        FrameLayout content = getField(V4Activity.class, "content", FrameLayout.class);
        TextView title = getField(V4Activity.class, "title", TextView.class);
        TextView subtitle = getField(V4Activity.class, "subtitle", TextView.class);
        if (content == null) return;
        if (title != null) title.setText("SPATIAL INTELLIGENCE");
        if (subtitle != null) subtitle.setText("History · Search · Alerts · Heatmap");
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(14), dp(8), dp(14), dp(26));
        root.addView(hero());
        root.addView(space(11));
        root.addView(statusStrip());

        section(root, "DEVICE INTELLIGENCE");
        addCard(root, "UNIVERSAL SEARCH", "Nom, alias, type, source, pièce ou note", ACCENT, this::showSearch);
        addCard(root, "DEVICE DOSSIERS", "Fiches persistantes, confiance, notes et historique RSSI", BLUE, this::showDossierPicker);
        addCard(root, "IDENTITY MERGE", "Regrouper manuellement les doublons Bluetooth et réseau", VIOLET, this::showMergeManager);
        addCard(root, "FIND MY DEVICE+", "Guidage signal de la v13 avec favoris prioritaires", GOOD,
                () -> invokePrivate(V13Activity.class, "showFindPicker"));

        section(root, "MEMORY & ALERTS");
        addCard(root, "HISTORY VAULT", "Snapshots persistants, courbes et évolution des présences", ACCENT, this::showHistoryVault);
        addCard(root, "ALERT CENTER", alertSubtitle(), WARN, this::showAlertCenter);
        addCard(root, "CHANGE DETECTION", "Comparer l’état actuel à la mémoire précédente", BLUE,
                () -> invokePrivate(V13Activity.class, "showChangeDetection"));

        section(root, "PLACES & COVERAGE");
        addCard(root, "PLACE PROFILES", "Maison, voiture, atelier, club ou voyage", GOOD, this::managePlaces);
        addCard(root, "WI-FI HEATMAP", "Touchez la carte en marchant pour enregistrer le signal", ACCENT, this::showWifiHeatmap);
        addCard(root, "ROOM SCAN", "Empreintes locales de pièces et appareils habituels", BLUE,
                () -> invokePrivate(V13Activity.class, "showRoomScan"));

        section(root, "AUTOMATION & EXPORT");
        addCard(root, "ADAPTIVE SCAN", adaptiveSubtitle(), WARN, this::configureAdaptiveScan);
        addCard(root, "DAILY STORY", "Résumé local des objets et changements", VIOLET,
                () -> invokePrivate(V13Activity.class, "showDailyStory"));
        addCard(root, "EXPORT INTELLIGENCE", "Historique, dossiers, alertes, profils et heatmap en JSON", ACCENT, this::exportIntelligence);
        addCard(root, "LIVING HUB V13", "Graphe, cinéma, favoris et comparaison de scans", MUTED,
                () -> invokePrivate(V13Activity.class, "showLivingHub"));
        addCard(root, "COMMAND HUB V12", "Outils réseau, GATT, sécurité et performance", MUTED,
                () -> invokePrivate(V12Activity.class, "showHubPage"));

        scroll.addView(root);
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activateIntelNav();
    }

    private View hero() {
        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(rounded(PANEL, 21));
        TextView eyebrow = text("SENTRY V14", 11, ACCENT, true);
        eyebrow.setLetterSpacing(.15f);
        card.addView(eyebrow);
        card.addView(space(5));
        card.addView(text("Spatial Intelligence", 27, TEXT, true));
        card.addView(space(5));
        card.addView(text("Sentry mémorise les observations, explique son niveau de confiance et transforme chaque appareil en dossier consultable.", 13, MUTED, false));
        return card;
    }

    private View statusStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric("HISTORY", String.valueOf(history().length()), ACCENT), weight());
        row.addView(spaceW(7));
        row.addView(metric("ALERTS", String.valueOf(unreadAlerts()), unreadAlerts() > 0 ? WARN : GOOD), weight());
        row.addView(spaceW(7));
        row.addView(metric("PLACE", shortText(intel.getString("active_place", "Maison"), 10), BLUE), weight());
        return row;
    }

    private void activateIntelNav() {
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

    private void captureSnapshotAndAlerts() {
        try {
            JSONObject snapshot = buildSnapshot();
            JSONArray history = history();
            JSONObject previous = history.length() == 0 ? null : history.optJSONObject(history.length() - 1);
            if (previous == null || shouldStore(previous, snapshot)) {
                history.put(snapshot);
                while (history.length() > 240) removeFirst(history);
                intel.edit().putString("history", history.toString()).apply();
                updateDossierStats(snapshot);
                detectAlerts(previous, snapshot);
            }
        } catch (Exception ignored) { }
    }

    private boolean shouldStore(JSONObject previous, JSONObject current) {
        long elapsed = current.optLong("time") - previous.optLong("time");
        if (elapsed >= 45000L) return true;
        JSONObject a = previous.optJSONObject("devices");
        JSONObject b = current.optJSONObject("devices");
        return a == null || b == null || a.length() != b.length();
    }

    private JSONObject buildSnapshot() throws Exception {
        JSONObject root = new JSONObject();
        root.put("time", System.currentTimeMillis());
        root.put("place", intel.getString("active_place", "Maison"));
        WifiInfo wi = wifiInfo();
        if (wi != null) {
            root.put("wifi_rssi", wi.getRssi());
            root.put("wifi_bssid", String.valueOf(wi.getBSSID()));
        }
        JSONObject objects = new JSONObject();
        for (DeviceInfo d : allDevices()) {
            JSONObject o = new JSONObject();
            o.put("name", displayName(d));
            o.put("raw_name", d.name);
            o.put("type", d.type);
            o.put("source", d.source);
            o.put("room", d.room);
            o.put("rssi", d.rssi);
            o.put("online", d.online || System.currentTimeMillis() - d.lastSeen < 180000L);
            o.put("last", d.lastSeen);
            objects.put(d.id, o);
        }
        root.put("devices", objects);
        return root;
    }

    private void updateDossierStats(JSONObject snapshot) {
        JSONObject objects = snapshot.optJSONObject("devices");
        if (objects == null) return;
        JSONArray names = objects.names();
        if (names == null) return;
        SharedPreferences.Editor e = intel.edit();
        long now = snapshot.optLong("time");
        for (int i = 0; i < names.length(); i++) {
            String id = names.optString(i);
            JSONObject o = objects.optJSONObject(id);
            if (o == null) continue;
            String key = safe(id);
            if (!intel.contains("first_" + key)) e.putLong("first_" + key, now);
            e.putLong("seen_" + key, now);
            e.putInt("rssi_" + key, o.optInt("rssi", -100));
        }
        e.apply();
    }

    private void detectAlerts(JSONObject previous, JSONObject current) {
        if (previous == null) return;
        JSONObject before = previous.optJSONObject("devices");
        JSONObject now = current.optJSONObject("devices");
        if (before == null || now == null) return;
        JSONArray currentIds = now.names();
        if (currentIds != null) for (int i = 0; i < currentIds.length(); i++) {
            String id = currentIds.optString(i);
            JSONObject n = now.optJSONObject(id);
            JSONObject p = before.optJSONObject(id);
            if (n == null) continue;
            if (p == null) addAlert("NEW_DEVICE", n.optString("name", id), "Nouvel appareil observé dans " + current.optString("place"));
            else {
                if (!p.optBoolean("online") && n.optBoolean("online")) addAlert("RETURNED", n.optString("name", id), "Appareil revenu");
                if (Math.abs(p.optInt("rssi", -100) - n.optInt("rssi", -100)) >= 24) addAlert("SIGNAL_CHANGE", n.optString("name", id), "Variation importante du signal");
                if (!p.optString("room").equals(n.optString("room"))) addAlert("ROOM_CHANGE", n.optString("name", id), "Pièce estimée modifiée");
            }
        }
        JSONArray previousIds = before.names();
        if (previousIds != null) for (int i = 0; i < previousIds.length(); i++) {
            String id = previousIds.optString(i);
            JSONObject p = before.optJSONObject(id);
            JSONObject n = now.optJSONObject(id);
            if (p != null && p.optBoolean("online") && (n == null || !n.optBoolean("online"))) {
                String name = p.optString("name", id);
                if (isFavorite(id)) addAlert("FAVORITE_LOST", name, "Favori non observé pendant le dernier état");
            }
        }
    }

    private void addAlert(String type, String title, String detail) {
        try {
            JSONArray alerts = alerts();
            if (alerts.length() > 0) {
                JSONObject last = alerts.optJSONObject(alerts.length() - 1);
                if (last != null && type.equals(last.optString("type")) && title.equals(last.optString("title"))
                        && System.currentTimeMillis() - last.optLong("time") < 60000L) return;
            }
            JSONObject alert = new JSONObject();
            alert.put("time", System.currentTimeMillis());
            alert.put("type", type);
            alert.put("title", title);
            alert.put("detail", detail);
            alert.put("read", false);
            alerts.put(alert);
            while (alerts.length() > 120) removeFirst(alerts);
            intel.edit().putString("alerts", alerts.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void showSearch() {
        EditText input = new EditText(this);
        input.setHint("Ex. écouteurs, bureau, bluetooth…");
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(rounded(PANEL_2, 14));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Universal Search").setView(wrap)
                .setPositiveButton("Rechercher", (d, w) -> showSearchResults(input.getText().toString().trim()))
                .setNeutralButton("Tous", (d, w) -> showSearchResults(""))
                .setNegativeButton("Annuler", null).show();
    }

    private void showSearchResults(String query) {
        String q = query.toLowerCase(Locale.ROOT);
        List<DeviceInfo> matches = new ArrayList<>();
        for (DeviceInfo d : allDevices()) {
            String haystack = (displayName(d) + " " + d.name + " " + d.type + " " + d.source + " " + d.room + " "
                    + intel.getString("category_" + safe(d.id), "") + " " + intel.getString("note_" + safe(d.id), "")).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || haystack.contains(q)) matches.add(d);
        }
        matches.sort((a, b) -> displayName(a).compareToIgnoreCase(displayName(b)));
        if (matches.isEmpty()) { toast("Aucun résultat."); return; }
        String[] labels = new String[matches.size()];
        for (int i = 0; i < matches.size(); i++) {
            DeviceInfo d = matches.get(i);
            labels[i] = (isFavorite(d.id) ? "★ " : "") + displayName(d) + "\n" + d.type + " · " + d.source + " · " + confidence(d) + "%";
        }
        new AlertDialog.Builder(this).setTitle(matches.size() + " résultat(s)").setItems(labels, (dialog, which) -> showDossier(matches.get(which)))
                .setNegativeButton("Fermer", null).show();
    }

    private void showDossierPicker() {
        List<DeviceInfo> devices = allDevices();
        devices.sort((a, b) -> displayName(a).compareToIgnoreCase(displayName(b)));
        if (devices.isEmpty()) { toast("Aucun appareil connu."); return; }
        String[] labels = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            DeviceInfo d = devices.get(i);
            labels[i] = (isFavorite(d.id) ? "★ " : "") + displayName(d) + "\n" + d.source + " · " + d.rssi + " dBm";
        }
        new AlertDialog.Builder(this).setTitle("Device Dossiers").setItems(labels, (dialog, which) -> showDossier(devices.get(which)))
                .setNegativeButton("Fermer", null).show();
    }

    private void showDossier(DeviceInfo d) {
        String key = safe(d.id);
        String alias = intel.getString("alias_" + key, "");
        String category = intel.getString("category_" + key, d.type);
        String note = intel.getString("note_" + key, "Aucune note");
        long first = intel.getLong("first_" + key, d.lastSeen);
        long last = intel.getLong("seen_" + key, d.lastSeen);
        String merge = intel.getString("merge_" + key, "Aucune");
        String report = "IDENTITÉ\n" + displayName(d) + "\nID : " + d.id + "\nSource : " + d.source +
                "\nType : " + category + "\nFusion : " + merge +
                "\n\nCONFIANCE\n" + confidence(d) + "% · " + confidenceExplanation(d) +
                "\n\nPRÉSENCE\nPremière observation : " + date(first) + "\nDernière observation : " + date(last) +
                "\nSignal : " + d.rssi + " dBm · " + signalLabel(d.rssi) + "\nPièce : " + d.room +
                "\n\nNOTE\n" + note;

        LinearLayout panel = column();
        TextView body = text(report, 13, TEXT, false);
        body.setTextIsSelectable(true);
        body.setPadding(dp(16), dp(8), dp(16), dp(12));
        panel.addView(body);
        panel.addView(new SignalHistoryView(this, d.id), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        new AlertDialog.Builder(this).setTitle(alias.isEmpty() ? d.name : alias).setView(scroll)
                .setPositiveButton("Modifier", (dialog, which) -> editDossier(d))
                .setNeutralButton("Find", (dialog, which) -> invokePrivate(V13Activity.class, "showFinder", new Class[]{String.class}, new Object[]{d.id}))
                .setNegativeButton("Fermer", null).show();
    }

    private void editDossier(DeviceInfo d) {
        LinearLayout root = column();
        root.setPadding(dp(18), dp(8), dp(18), 0);
        EditText alias = editField("Alias", intel.getString("alias_" + safe(d.id), ""));
        EditText category = editField("Catégorie", intel.getString("category_" + safe(d.id), d.type));
        EditText note = editField("Note", intel.getString("note_" + safe(d.id), ""));
        root.addView(alias); root.addView(space(8)); root.addView(category); root.addView(space(8)); root.addView(note);
        boolean favorite = isFavorite(d.id);
        new AlertDialog.Builder(this).setTitle("Modifier le dossier").setView(root)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    SharedPreferences.Editor e = intel.edit();
                    e.putString("alias_" + safe(d.id), alias.getText().toString().trim());
                    e.putString("category_" + safe(d.id), category.getText().toString().trim());
                    e.putString("note_" + safe(d.id), note.getText().toString().trim());
                    e.apply();
                })
                .setNeutralButton(favorite ? "Retirer favori" : "Ajouter favori", (dialog, which) -> setFavorite(d.id, !favorite))
                .setNegativeButton("Annuler", null).show();
    }

    private EditText editField(String hint, String value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value);
        field.setTextColor(TEXT);
        field.setHintTextColor(MUTED);
        field.setPadding(dp(13), dp(11), dp(13), dp(11));
        field.setBackground(rounded(PANEL_2, 13));
        return field;
    }

    private void showMergeManager() {
        List<DeviceInfo> devices = allDevices();
        if (devices.size() < 2) { toast("Au moins deux appareils sont nécessaires."); return; }
        devices.sort((a, b) -> displayName(a).compareToIgnoreCase(displayName(b)));
        String[] labels = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) labels[i] = displayName(devices.get(i)) + "\n" + devices.get(i).source;
        new AlertDialog.Builder(this).setTitle("Appareil principal").setItems(labels, (d, first) ->
                new AlertDialog.Builder(this).setTitle("Doublon à fusionner").setItems(labels, (x, second) -> {
                    if (first == second) { toast("Choisis deux appareils différents."); return; }
                    DeviceInfo root = devices.get(first);
                    DeviceInfo duplicate = devices.get(second);
                    intel.edit().putString("merge_" + safe(duplicate.id), root.id).apply();
                    addAlert("IDENTITY_MERGE", displayName(root), displayName(duplicate) + " regroupé avec ce dossier");
                    toast("Identités regroupées.");
                }).setNegativeButton("Annuler", null).show()
        ).setNeutralButton("Voir les fusions", (d, w) -> showMergeList())
                .setNegativeButton("Fermer", null).show();
    }

    private void showMergeList() {
        StringBuilder b = new StringBuilder();
        for (DeviceInfo d : allDevices()) {
            String root = intel.getString("merge_" + safe(d.id), "");
            if (!root.isEmpty()) b.append("• ").append(displayName(d)).append(" → ").append(displayName(root)).append('\n');
        }
        if (b.length() == 0) b.append("Aucune fusion manuelle.");
        new AlertDialog.Builder(this).setTitle("Identity Merge").setMessage(b.toString())
                .setPositiveButton("Tout annuler", (d, w) -> clearMergeMappings())
                .setNegativeButton("Fermer", null).show();
    }

    private void clearMergeMappings() {
        SharedPreferences.Editor e = intel.edit();
        for (String key : intel.getAll().keySet()) if (key.startsWith("merge_")) e.remove(key);
        e.apply();
    }

    private void showHistoryVault() {
        JSONArray history = history();
        if (history.length() == 0) { toast("L’historique est encore vide."); return; }
        Dialog dialog = new Dialog(this);
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(18), dp(16), dp(18));
        root.addView(text("HISTORY VAULT", 24, TEXT, true));
        root.addView(text(history.length() + " snapshots locaux · " + historyRange(), 12, MUTED, false));
        root.addView(space(10));
        root.addView(new PresenceHistoryView(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        root.addView(space(10));
        TextView details = action("VOIR LES DERNIERS ÉTATS", PANEL_2, TEXT);
        details.setOnClickListener(v -> showHistoryList());
        root.addView(details);
        root.addView(space(8));
        TextView clear = action("EFFACER L’HISTORIQUE", Color.rgb(62, 27, 34), BAD);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Effacer l’historique ?")
                .setMessage("Les dossiers et favoris restent conservés.")
                .setPositiveButton("Effacer", (d, w) -> { intel.edit().remove("history").apply(); dialog.dismiss(); })
                .setNegativeButton("Annuler", null).show());
        root.addView(clear);
        root.addView(space(8));
        TextView close = action("FERMER", PANEL_2, TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(close);
        dialog.setContentView(root);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) { w.setBackgroundDrawable(rounded(BG, 0)); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
    }

    private void showHistoryList() {
        JSONArray history = history();
        int count = Math.min(20, history.length());
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            JSONObject s = history.optJSONObject(history.length() - 1 - i);
            JSONObject devices = s == null ? null : s.optJSONObject("devices");
            labels[i] = (s == null ? "?" : date(s.optLong("time"))) + "\n" + (devices == null ? 0 : devices.length()) + " appareils · " + (s == null ? "" : s.optString("place"));
        }
        new AlertDialog.Builder(this).setTitle("Derniers états").setItems(labels, null).setPositiveButton("Fermer", null).show();
    }

    private String historyRange() {
        JSONArray h = history();
        if (h.length() == 0) return "aucune donnée";
        JSONObject first = h.optJSONObject(0);
        JSONObject last = h.optJSONObject(h.length() - 1);
        return date(first == null ? 0 : first.optLong("time")) + " → " + date(last == null ? 0 : last.optLong("time"));
    }

    private void showAlertCenter() {
        JSONArray alerts = alerts();
        if (alerts.length() == 0) { toast("Aucune alerte enregistrée."); return; }
        int count = Math.min(50, alerts.length());
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            JSONObject a = alerts.optJSONObject(alerts.length() - 1 - i);
            labels[i] = (a != null && !a.optBoolean("read") ? "● " : "") + (a == null ? "Alerte" : a.optString("title"))
                    + "\n" + (a == null ? "" : a.optString("detail")) + " · " + (a == null ? "" : date(a.optLong("time")));
        }
        new AlertDialog.Builder(this).setTitle("Alert Center").setItems(labels, (d, which) -> markAlertRead(alerts.length() - 1 - which))
                .setPositiveButton("Tout marquer lu", (d, w) -> markAllAlertsRead())
                .setNeutralButton("Effacer", (d, w) -> intel.edit().remove("alerts").apply())
                .setNegativeButton("Fermer", null).show();
    }

    private void markAlertRead(int index) {
        try {
            JSONArray alerts = alerts();
            JSONObject a = alerts.optJSONObject(index);
            if (a != null) a.put("read", true);
            intel.edit().putString("alerts", alerts.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void markAllAlertsRead() {
        try {
            JSONArray alerts = alerts();
            for (int i = 0; i < alerts.length(); i++) {
                JSONObject a = alerts.optJSONObject(i);
                if (a != null) a.put("read", true);
            }
            intel.edit().putString("alerts", alerts.toString()).apply();
            if (intelHubVisible) showIntelHub();
        } catch (Exception ignored) { }
    }

    private void managePlaces() {
        Set<String> names = new HashSet<>(intel.getStringSet("place_names", new HashSet<>()));
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        String[] labels = new String[sorted.size() + 1];
        for (int i = 0; i < sorted.size(); i++) labels[i] = (sorted.get(i).equals(intel.getString("active_place", "Maison")) ? "● " : "") + sorted.get(i);
        labels[labels.length - 1] = "+ Nouveau profil";
        new AlertDialog.Builder(this).setTitle("Place Profiles").setItems(labels, (d, which) -> {
            if (which == sorted.size()) askNewPlace();
            else {
                intel.edit().putString("active_place", sorted.get(which)).apply();
                addAlert("PLACE_CHANGED", sorted.get(which), "Profil de lieu activé");
                if (intelHubVisible) showIntelHub();
            }
        }).setNeutralButton("Supprimer un profil", (d, w) -> deletePlacePicker())
                .setNegativeButton("Fermer", null).show();
    }

    private void askNewPlace() {
        EditText input = editField("Nom du lieu", "");
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(18), dp(8), dp(18), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Nouveau profil").setView(wrap)
                .setPositiveButton("Créer", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    Set<String> names = new HashSet<>(intel.getStringSet("place_names", new HashSet<>()));
                    names.add(name);
                    intel.edit().putStringSet("place_names", names).putString("active_place", name).apply();
                }).setNegativeButton("Annuler", null).show();
    }

    private void deletePlacePicker() {
        List<String> places = new ArrayList<>(intel.getStringSet("place_names", new HashSet<>()));
        places.remove("Maison");
        if (places.isEmpty()) { toast("Aucun profil supprimable."); return; }
        String[] labels = places.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Supprimer un profil").setItems(labels, (d, which) -> {
            Set<String> names = new HashSet<>(intel.getStringSet("place_names", new HashSet<>()));
            names.remove(places.get(which));
            intel.edit().putStringSet("place_names", names).putString("active_place", "Maison").apply();
        }).setNegativeButton("Annuler", null).show();
    }

    private void showWifiHeatmap() {
        Dialog dialog = new Dialog(this);
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(18), dp(14), dp(18));
        root.addView(text("WI-FI HEATMAP", 24, TEXT, true));
        root.addView(text("Touchez une case à l’endroit approximatif où vous vous trouvez. Marchez, puis ajoutez plusieurs mesures.", 12, MUTED, false));
        root.addView(space(8));
        HeatmapView map = new HeatmapView(this);
        root.addView(map, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView reset = action("RÉINITIALISER LA CARTE", PANEL_2, WARN);
        reset.setOnClickListener(v -> { intel.edit().remove(heatmapKey()).apply(); map.reload(); });
        root.addView(reset);
        root.addView(space(8));
        TextView close = action("FERMER", PANEL_2, TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(close);
        dialog.setContentView(root);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) { w.setBackgroundDrawable(rounded(BG, 0)); w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
    }

    private String heatmapKey() { return "heatmap_" + safe(intel.getString("active_place", "Maison")); }

    private void configureAdaptiveScan() {
        String[] labels = {"Rapide · 30 s", "Équilibré · 75 s", "Économie · 3 min", "Désactivé"};
        new AlertDialog.Builder(this).setTitle("Adaptive Scan").setItems(labels, (d, which) -> {
            SharedPreferences.Editor e = intel.edit();
            if (which == 0) e.putBoolean("smart_scan", true).putString("scan_mode", "fast");
            else if (which == 1) e.putBoolean("smart_scan", true).putString("scan_mode", "balanced");
            else if (which == 2) e.putBoolean("smart_scan", true).putString("scan_mode", "eco");
            else e.putBoolean("smart_scan", false);
            e.apply();
            ui.removeCallbacks(historyLoop);
            ui.postDelayed(historyLoop, 2000);
            if (intelHubVisible) showIntelHub();
        }).setNegativeButton("Annuler", null).show();
    }

    private void exportIntelligence() {
        try {
            JSONObject root = new JSONObject();
            root.put("product", "Sentry v14 Spatial Intelligence");
            root.put("generated_at", System.currentTimeMillis());
            root.put("active_place", intel.getString("active_place", "Maison"));
            root.put("history", history());
            root.put("alerts", alerts());
            root.put("heatmap", new JSONArray(intel.getString(heatmapKey(), "[]")));
            JSONObject dossiers = new JSONObject();
            for (DeviceInfo d : allDevices()) {
                JSONObject o = new JSONObject();
                String key = safe(d.id);
                o.put("alias", intel.getString("alias_" + key, ""));
                o.put("category", intel.getString("category_" + key, d.type));
                o.put("note", intel.getString("note_" + key, ""));
                o.put("favorite", isFavorite(d.id));
                o.put("confidence", confidence(d));
                o.put("merged_into", intel.getString("merge_" + key, ""));
                dossiers.put(d.id, o);
            }
            root.put("dossiers", dossiers);
            pendingExport = root.toString(2);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "sentry-v14-intelligence-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(new Date()) + ".json");
            startActivityForResult(intent, REQ_EXPORT_V14);
        } catch (Exception e) { toast("Export impossible."); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT_V14 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out != null) out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                toast("Rapport v14 exporté.");
            } catch (Exception e) { toast("Écriture impossible."); }
        }
    }

    private JSONArray history() {
        try { return new JSONArray(intel == null ? "[]" : intel.getString("history", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private JSONArray alerts() {
        try { return new JSONArray(intel == null ? "[]" : intel.getString("alerts", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private int unreadAlerts() {
        int count = 0;
        JSONArray a = alerts();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !o.optBoolean("read")) count++;
        }
        return count;
    }

    private String alertSubtitle() {
        int unread = unreadAlerts();
        return unread == 0 ? "Aucun événement non lu" : unread + " événement(s) non lu(s)";
    }

    private String adaptiveSubtitle() {
        if (!intel.getBoolean("smart_scan", true)) return "Désactivé · scan uniquement manuel";
        String mode = intel.getString("scan_mode", "balanced");
        if ("fast".equals(mode)) return "Rapide · environ toutes les 30 secondes quand l’app est ouverte";
        if ("eco".equals(mode)) return "Économie · environ toutes les 3 minutes";
        return "Équilibré · environ toutes les 75 secondes";
    }

    private int confidence(DeviceInfo d) {
        int score = 25;
        if (d.name != null && !d.name.toLowerCase(Locale.ROOT).contains("unknown") && !d.name.toLowerCase(Locale.ROOT).contains("appareil")) score += 18;
        if (d.type != null && !"unknown".equalsIgnoreCase(d.type)) score += 14;
        if (d.source != null && !"unknown".equalsIgnoreCase(d.source)) score += 12;
        if (d.rssi > -95) score += 12;
        if (intel.contains("first_" + safe(d.id))) score += 8;
        if (isFavorite(d.id) || !intel.getString("alias_" + safe(d.id), "").isEmpty()) score += 6;
        return Math.min(95, score);
    }

    private String confidenceExplanation(DeviceInfo d) {
        int c = confidence(d);
        if (c >= 80) return "identité stable et plusieurs indices cohérents";
        if (c >= 60) return "classification probable";
        if (c >= 40) return "estimation partielle";
        return "données insuffisantes";
    }

    private String displayName(DeviceInfo d) {
        String rootId = intel.getString("merge_" + safe(d.id), "");
        if (!rootId.isEmpty()) {
            DeviceInfo root = findDevice(rootId);
            if (root != null) return displayNameWithoutMerge(root) + " · doublon";
        }
        return displayNameWithoutMerge(d);
    }

    private String displayName(String id) {
        DeviceInfo d = findDevice(id);
        return d == null ? id : displayNameWithoutMerge(d);
    }

    private String displayNameWithoutMerge(DeviceInfo d) {
        String alias = intel.getString("alias_" + safe(d.id), "");
        return alias.isEmpty() ? d.name : alias;
    }

    private boolean isFavorite(String id) {
        SharedPreferences old = getSharedPreferences("sentry_v13", MODE_PRIVATE);
        return old.getStringSet("favorites", new HashSet<>()).contains(id);
    }

    private void setFavorite(String id, boolean value) {
        SharedPreferences old = getSharedPreferences("sentry_v13", MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(old.getStringSet("favorites", new HashSet<>()));
        if (value) favorites.add(id); else favorites.remove(id);
        old.edit().putStringSet("favorites", favorites).apply();
    }

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
                d.lastSeen = readLong(object, "lastSeen", 0L);
                d.online = readBoolean(object, "online", System.currentTimeMillis() - d.lastSeen < 180000L);
                result.add(d);
            }
        } catch (Exception ignored) { }
        return result;
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
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); Object value = f.get(object); return value == null ? fallback : String.valueOf(value); }
        catch (Exception e) { return fallback; }
    }
    private int readInt(Object object, String name, int fallback) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.getInt(object); }
        catch (Exception e) { return fallback; }
    }
    private long readLong(Object object, String name, long fallback) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.getLong(object); }
        catch (Exception e) { return fallback; }
    }
    private boolean readBoolean(Object object, String name, boolean fallback) {
        try { Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.getBoolean(object); }
        catch (Exception e) { return fallback; }
    }

    private WifiInfo wifiInfo() {
        try {
            WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            return manager == null ? null : manager.getConnectionInfo();
        } catch (Exception e) { return null; }
    }

    private void removeFirst(JSONArray array) {
        if (array.length() == 0) return;
        JSONArray replacement = new JSONArray();
        for (int i = 1; i < array.length(); i++) replacement.put(array.opt(i));
        while (array.length() > 0) array.remove(0);
        for (int i = 0; i < replacement.length(); i++) array.put(replacement.opt(i));
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

    private void section(LinearLayout root, String title) {
        root.addView(space(15));
        TextView label = text(title, 10.5f, ACCENT, true);
        label.setLetterSpacing(.12f);
        root.addView(label);
        root.addView(space(7));
    }

    private void addCard(LinearLayout root, String title, String subtitle, int color, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(rounded(PANEL, 17));
        TextView marker = text("•", 24, color, true);
        marker.setGravity(Gravity.CENTER);
        card.addView(marker, new LinearLayout.LayoutParams(dp(26), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout labels = column();
        labels.addView(text(title, 14, TEXT, true));
        labels.addView(text(subtitle, 11.5f, MUTED, false));
        card.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(text("›", 27, color, false));
        card.setContentDescription(title + ". " + subtitle);
        card.setOnClickListener(v -> action.run());
        root.addView(card);
        root.addView(space(8));
    }

    private TextView metric(String label, String value, int color) {
        TextView view = text(label + "\n" + value, 9.5f, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(4), dp(10), dp(4), dp(10));
        view.setBackground(rounded(PANEL, 14));
        return view;
    }

    private TextView action(String label, int background, int foreground) {
        TextView view = text(label, 13, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(15), dp(12), dp(15));
        view.setBackground(rounded(background, 16));
        return view;
    }

    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(0, 1.12f);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); return t;
    }
    private GradientDrawable rounded(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View spaceW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private String safe(String text) { return text == null ? "unknown" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_"); }
    private String date(long ms) { return ms <= 0 ? "inconnue" : new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(new Date(ms)); }
    private String shortText(String value, int max) { return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…"; }

    private class PresenceHistoryView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        PresenceHistoryView(Context context) { super(context); setBackgroundColor(PANEL); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            JSONArray h = history();
            float left = dp(34), right = getWidth() - dp(16), top = dp(24), bottom = getHeight() - dp(32);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(Color.argb(70, 55, 235, 199));
            for (int i = 0; i <= 4; i++) { float y = top + (bottom - top) * i / 4f; c.drawLine(left, y, right, y, paint); }
            int start = Math.max(0, h.length() - 80); int maxDevices = 1;
            for (int i = start; i < h.length(); i++) { JSONObject d = h.optJSONObject(i); JSONObject o = d == null ? null : d.optJSONObject("devices"); maxDevices = Math.max(maxDevices, o == null ? 0 : o.length()); }
            paint.setColor(ACCENT); paint.setStrokeWidth(dp(3));
            boolean moved = false;
            android.graphics.Path path = new android.graphics.Path();
            int count = Math.max(1, h.length() - start - 1);
            for (int i = start; i < h.length(); i++) {
                JSONObject d = h.optJSONObject(i); JSONObject o = d == null ? null : d.optJSONObject("devices"); int n = o == null ? 0 : o.length();
                float x = left + (right - left) * (i - start) / count; float y = bottom - (bottom - top) * n / maxDevices;
                if (!moved) { path.moveTo(x, y); moved = true; } else path.lineTo(x, y);
            }
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextSize(dp(12)); paint.setColor(TEXT); paint.setTextAlign(Paint.Align.LEFT);
            c.drawText("Appareils observés", left, dp(17), paint);
            paint.setTextAlign(Paint.Align.RIGHT); paint.setColor(MUTED); c.drawText("max " + maxDevices, right, dp(17), paint);
        }
    }

    private class SignalHistoryView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String id;
        SignalHistoryView(Context context, String id) { super(context); this.id = id; setBackgroundColor(PANEL); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            JSONArray h = history(); float left = dp(30), right = getWidth() - dp(12), top = dp(23), bottom = getHeight() - dp(24);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(Color.argb(55, 80, 168, 255));
            c.drawLine(left, top, left, bottom, paint); c.drawLine(left, bottom, right, bottom, paint);
            android.graphics.Path path = new android.graphics.Path(); boolean moved = false; int points = 0;
            int start = Math.max(0, h.length() - 80); int span = Math.max(1, h.length() - start - 1);
            for (int i = start; i < h.length(); i++) {
                JSONObject s = h.optJSONObject(i); JSONObject devices = s == null ? null : s.optJSONObject("devices"); JSONObject d = devices == null ? null : devices.optJSONObject(id);
                if (d == null) continue; int rssi = d.optInt("rssi", -100); float x = left + (right - left) * (i - start) / span; float y = bottom - (bottom - top) * Math.max(0, Math.min(60, rssi + 105)) / 60f;
                if (!moved) { path.moveTo(x, y); moved = true; } else path.lineTo(x, y); points++;
            }
            paint.setColor(BLUE); paint.setStrokeWidth(dp(2)); c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(dp(11)); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setColor(TEXT); paint.setTextAlign(Paint.Align.LEFT); c.drawText("Historique RSSI · " + points + " mesures", dp(10), dp(16), paint);
        }
    }

    private class HeatmapView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private JSONArray samples = new JSONArray();
        HeatmapView(Context context) { super(context); reload(); setBackgroundColor(PANEL); }
        void reload() { try { samples = new JSONArray(intel.getString(heatmapKey(), "[]")); } catch (Exception e) { samples = new JSONArray(); } invalidate(); }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            WifiInfo wifi = wifiInfo();
            if (wifi == null) { toast("Wi-Fi non disponible."); return true; }
            try {
                JSONObject sample = new JSONObject();
                sample.put("x", Math.max(0, Math.min(1, event.getX() / Math.max(1f, getWidth()))));
                sample.put("y", Math.max(0, Math.min(1, event.getY() / Math.max(1f, getHeight()))));
                sample.put("rssi", wifi.getRssi()); sample.put("time", System.currentTimeMillis());
                samples.put(sample); while (samples.length() > 100) removeFirst(samples);
                intel.edit().putString(heatmapKey(), samples.toString()).apply(); invalidate();
            } catch (Exception ignored) { }
            return true;
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float cellW = getWidth() / 5f, cellH = getHeight() / 7f;
            float[][] sum = new float[5][7]; int[][] count = new int[5][7];
            for (int i = 0; i < samples.length(); i++) {
                JSONObject s = samples.optJSONObject(i); if (s == null) continue;
                int x = Math.min(4, Math.max(0, (int)(s.optDouble("x") * 5))); int y = Math.min(6, Math.max(0, (int)(s.optDouble("y") * 7)));
                sum[x][y] += s.optInt("rssi", -100); count[x][y]++;
            }
            paint.setStyle(Paint.Style.FILL);
            for (int x = 0; x < 5; x++) for (int y = 0; y < 7; y++) {
                int color = Color.rgb(18, 40, 42);
                if (count[x][y] > 0) { float rssi = sum[x][y] / count[x][y]; color = rssi > -58 ? GOOD : rssi > -72 ? WARN : BAD; }
                paint.setColor(Color.argb(count[x][y] > 0 ? 175 : 80, Color.red(color), Color.green(color), Color.blue(color)));
                RectF rect = new RectF(x * cellW + dp(2), y * cellH + dp(2), (x + 1) * cellW - dp(2), (y + 1) * cellH - dp(2)); c.drawRoundRect(rect, dp(9), dp(9), paint);
                if (count[x][y] > 0) { paint.setColor(Color.rgb(3, 18, 18)); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextSize(dp(10)); paint.setTextAlign(Paint.Align.CENTER); c.drawText(Math.round(sum[x][y] / count[x][y]) + "", rect.centerX(), rect.centerY() + dp(4), paint); }
            }
            paint.setColor(TEXT); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextSize(dp(12));
            c.drawText(samples.length() + " mesures · " + intel.getString("active_place", "Maison"), getWidth() / 2f, dp(17), paint);
        }
    }

    private static class DeviceInfo {
        String id = ""; String name = "Appareil"; String type = "unknown"; String source = "unknown"; String room = "Non assignée"; int rssi = -100; long lastSeen; boolean online;
    }
}
