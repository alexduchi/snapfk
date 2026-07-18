package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.net.RouteInfo;
import android.net.VpnService;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
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
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

/** Sentry v20 Command Center - suite mobile defensive, locale et explicable. */
public class V20Activity extends V15Activity implements SensorEventListener {
    private static final int BG = Color.rgb(2, 8, 12);
    private static final int PANEL = Color.rgb(10, 26, 32);
    private static final int PANEL_2 = Color.rgb(17, 43, 51);
    private static final int TEXT = Color.rgb(239, 253, 255);
    private static final int MUTED = Color.rgb(126, 163, 172);
    private static final int CYAN = Color.rgb(61, 235, 211);
    private static final int BLUE = Color.rgb(79, 166, 255);
    private static final int GOOD = Color.rgb(83, 228, 144);
    private static final int WARN = Color.rgb(247, 190, 80);
    private static final int BAD = Color.rgb(245, 98, 124);
    private static final int VIOLET = Color.rgb(181, 132, 247);
    private static final int REQ_VPN = 20001;
    private static final int REQ_EXPORT = 20002;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs20;
    private SensorManager sensorManager;
    private float pressure;
    private float light;
    private float acceleration;
    private long lastReactorRun;
    private String pendingExport = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs20 = getSharedPreferences("sentry_v20", MODE_PRIVATE);
        defaults();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        ui.postDelayed(navLoop, 350);
        ui.postDelayed(reactorLoop, 4500);
    }

    @Override protected void onResume() {
        super.onResume();
        registerSensors();
        ui.postDelayed(this::translateVisibleUi, 250);
    }

    @Override protected void onPause() {
        unregisterSensors();
        super.onPause();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacks(navLoop);
        ui.removeCallbacks(reactorLoop);
        unregisterSensors();
        super.onDestroy();
    }

    private void defaults() {
        SharedPreferences.Editor e = prefs20.edit();
        if (!prefs20.contains("mode")) e.putString("mode", "Quotidien");
        if (!prefs20.contains("react_new_device")) e.putBoolean("react_new_device", true);
        if (!prefs20.contains("react_wifi_change")) e.putBoolean("react_wifi_change", true);
        if (!prefs20.contains("react_pressure")) e.putBoolean("react_pressure", false);
        if (!prefs20.contains("react_place")) e.putBoolean("react_place", true);
        if (!prefs20.contains("react_auto_incident")) e.putBoolean("react_auto_incident", false);
        e.apply();
    }

    private final Runnable navLoop = new Runnable() {
        @Override public void run() {
            bindNavigation();
            translateVisibleUi();
            ui.postDelayed(this, 850);
        }
    };

    private final Runnable reactorLoop = new Runnable() {
        @Override public void run() {
            runEventReactor();
            ui.postDelayed(this, 15000);
        }
    };

    private void bindNavigation() {
        LinearLayout nav = getPrivateField(V4Activity.class, "nav", LinearLayout.class);
        if (nav != null && nav.getChildCount() >= 6) {
            View hub = nav.getChildAt(5);
            if (hub instanceof TextView) {
                ((TextView) hub).setText("Centre");
                hub.setContentDescription("Ouvrir le centre de commandement");
                hub.setOnClickListener(v -> showCommandCenter());
            }
        }
        replaceExact(getWindow().getDecorView(), "S15", "S20");
    }

    private void showCommandCenter() {
        FrameLayout content = getPrivateField(V4Activity.class, "content", FrameLayout.class);
        TextView title = getPrivateField(V4Activity.class, "title", TextView.class);
        TextView subtitle = getPrivateField(V4Activity.class, "subtitle", TextView.class);
        if (content == null) return;
        if (title != null) title.setText("CENTRE DE COMMANDEMENT");
        if (subtitle != null) subtitle.setText("Protection · Recherche · Réseau · Incidents");
        content.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(14), dp(8), dp(14), dp(30));
        root.addView(hero());
        root.addView(space(10));
        root.addView(statusStrip());

        section(root, "URGENCE ET PROTECTION");
        feature(root, "CONFINEMENT RÉSEAU", EmergencyVpnService.isRunning() ? "ACTIF : Internet du téléphone est bloqué" : "Bloquer immédiatement les connexions du téléphone", "Crée un VPN local d'urgence sans serveur extérieur. Tout le trafic IPv4 du téléphone est envoyé dans une interface fermée. Cela protège le téléphone, pas toute la box Wi-Fi.", BAD, this::toggleLockdown);
        feature(root, "CAPTURE D'INCIDENT", "Enregistrer l'état Wi-Fi, Bluetooth, DNS, appareil et capteurs", "Crée une photographie horodatée de la situation puis la place dans le coffre chiffré. Utile avant de couper les connexions ou de modifier les réglages.", WARN, () -> captureIncident("Capture manuelle", "Capture déclenchée depuis le centre de commandement."));
        feature(root, "GARDIEN WI-FI", "Vérifier identité du réseau, DNS, passerelle et portail captif", "Compare le réseau actuel à une empreinte de confiance. Une différence n'est pas forcément une attaque : un routeur, un répéteur ou un DNS peut légitimement changer.", CYAN, this::showWifiGuardian);
        feature(root, "SURVEILLANCE BLUETOOTH", "Repérer activité dense, noms vides et signaux très proches", "Analyse uniquement ce qu'Android autorise à voir. Il signale des comportements inhabituels, sans prétendre identifier un gadget précis ou une attaque avec certitude.", BLUE, this::showBluetoothWatch);

        section(root, "RECHERCHE ET ESPACE");
        feature(root, "RECHERCHE DE SIGNAL", "Vibrations progressives et secteur probable sur 360 degrés", "Suit la puissance Bluetooth. Tourne lentement avec le téléphone : Sentry retient le secteur ayant donné le meilleur signal. Les murs et ton corps peuvent fausser la direction.", CYAN, () -> invokePrivate(V15Activity.class, "showHunterPicker"));
        feature(root, "CARTE DE SIGNAL", "Enregistrer les zones où le Wi-Fi est fort ou faible", "Tu touches la carte en te déplaçant. Sentry mémorise la puissance reçue à chaque point afin de créer une carte thermique locale.", VIOLET, () -> invokePrivate(V14Activity.class, "showWifiHeatmap"));
        feature(root, "PULSE SPATIAL", "Résumé instantané des capteurs, du réseau et des appareils", "Combine pression, lumière, mouvement, Wi-Fi et appareils visibles pour expliquer l'environnement actuel. Les conclusions restent des estimations.", GOOD, this::showSpatialPulse);
        feature(root, "LIEUX ET ARRIVÉES", "Reconnaître Maison, Atelier, Club, Voiture ou autres lieux", "Enregistre une empreinte faite du Wi-Fi et des appareils proches. Le réacteur peut ensuite signaler une arrivée, un départ ou un changement d'environnement.", BLUE, this::managePlaces);

        section(root, "CYBERSÉCURITÉ ET CONFIDENTIALITÉ");
        feature(root, "RADAR DES APPLICATIONS", "Classer les applications visibles selon leurs permissions sensibles", "Vérifie micro, caméra, localisation, SMS, contacts, superposition et installation de paquets. Android peut limiter la liste d'applications visible à Sentry.", VIOLET, this::showAppRadar);
        feature(root, "AUDIT DU TÉLÉPHONE", "Verrouillage, chiffrement, débogage USB, VPN et batterie", "Contrôle des réglages de sécurité réellement exposés par Android. Sentry ne modifie rien sans action explicite de ta part.", WARN, this::showPhoneAudit);
        feature(root, "OBSERVATOIRE RÉSEAU", "Voir IP, DNS, routes, appareils LAN et changements", "Ouvre les outils réseau existants de Sentry pour observer ton propre réseau ou un réseau que tu as l'autorisation d'auditer.", BLUE, this::showNetworkObservatory);
        feature(root, "CERTIFICATS ET VPN", "Contrôler les certificats installés et l'état des VPN", "Ouvre les réglages Android officiels. Un certificat utilisateur inconnu peut permettre une interception locale si une application ou un profil lui fait confiance.", CYAN, this::showCertificateCenter);

        section(root, "AUTOMATISATION");
        feature(root, "RÉACTEUR D'ÉVÉNEMENTS", "Déclencher alertes et captures selon les changements", "Surveille les nouveaux appareils, changements Wi-Fi, variations de pression et changements de lieu pendant que Sentry est ouvert.", GOOD, this::configureReactor);
        feature(root, "MODES DE MISSION", "Quotidien, Wi-Fi public, voyage, incident, éco ou démonstration", "Chaque mode ajuste la fréquence des scans, la sensibilité et les événements prioritaires. Le mode ne contourne aucune limitation Android.", VIOLET, this::chooseMissionMode);
        feature(root, "RÉSUMÉ INTELLIGENT LOCAL", "Transformer les données en explication simple en français", "Produit un résumé par règles locales, sans envoyer les données vers un service en ligne. Il distingue les faits observés des suppositions.", CYAN, this::showLocalBrief);
        feature(root, "SIMULATEUR D'ENTRAÎNEMENT", "S'exercer sur de faux incidents sans risque", "Génère des scénarios fictifs : Wi-Fi cloné, DNS modifié, balise persistante, objet perdu ou changement d'étage.", WARN, this::showTrainingSimulator);

        section(root, "MÉMOIRE ET RAPPORTS");
        feature(root, "LABORATOIRE D'INCIDENTS", "Relire, comparer et supprimer les captures enregistrées", "Chaque incident contient une date, un niveau, les signaux observés et une explication. Les données sont chiffrées dans le stockage privé de l'application.", BLUE, this::showIncidentLab);
        feature(root, "COFFRE CHIFFRÉ", "Vérifier la clé locale et le nombre d'éléments protégés", "Utilise Android Keystore avec AES-GCM. La clé ne quitte pas le téléphone et n'est pas exportée dans les rapports.", VIOLET, this::showVaultStatus);
        feature(root, "EXPORTER UN RAPPORT", "Créer un fichier texte compréhensible et partageable", "Exporte un résumé en français. Les secrets du coffre et la clé de chiffrement ne sont jamais inclus.", CYAN, this::exportReport);
        feature(root, "OUTILS SENTRY EXISTANTS", "Historique, dossiers, GATT, performances et réglages", "Ouvre les modules des versions précédentes en gardant la nouvelle navigation française.", MUTED, () -> invokePrivate(V14Activity.class, "showIntelHub"));

        root.addView(space(14));
        root.addView(text("Sentry est un outil défensif. Il ne neutralise pas les appareils voisins et ne protège pas toute la box depuis un téléphone non administrateur.", 11, MUTED, false));
        scroll.addView(root);
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activateCenterNav();
    }

    private View hero() {
        LinearLayout card = column();
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(rounded(PANEL, 22));
        TextView small = text("SENTRY V20", 11, CYAN, true);
        small.setLetterSpacing(.16f);
        card.addView(small);
        card.addView(space(5));
        card.addView(text("Centre de commandement", 27, TEXT, true));
        card.addView(space(5));
        card.addView(text("Toutes les fonctions sont en français, avec un bouton d'information à côté de chaque outil.", 13, MUTED, false));
        return card;
    }

    private View statusStrip() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(metric("MODE", prefs20.getString("mode", "Quotidien"), CYAN), weight());
        row.addView(spaceW(7));
        row.addView(metric("RISQUE", riskLabel(currentRiskScore()), riskColor(currentRiskScore())), weight());
        row.addView(spaceW(7));
        row.addView(metric("COFFRE", String.valueOf(incidentCount()), VIOLET), weight());
        return row;
    }

    private void feature(LinearLayout root, String title, String subtitle, String info, int color, Runnable action) {
        LinearLayout card = column();
        card.setPadding(dp(15), dp(13), dp(12), dp(13));
        card.setBackground(rounded(PANEL, 18));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(title, 14, color, true);
        top.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView help = text("i", 14, color, true);
        help.setGravity(Gravity.CENTER);
        GradientDrawable helpBg = rounded(Color.TRANSPARENT, 14);
        helpBg.setStroke(dp(1), color);
        help.setBackground(helpBg);
        help.setContentDescription("Informations sur " + title);
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(title).setMessage(info).setPositiveButton("Compris", null).show());
        top.addView(help, new LinearLayout.LayoutParams(dp(30), dp(30)));
        card.addView(top);
        TextView sub = text(subtitle, 11.5f, MUTED, false);
        sub.setPadding(0, dp(4), dp(36), 0);
        card.addView(sub);
        card.setOnClickListener(v -> action.run());
        root.addView(card);
        root.addView(space(8));
    }

    private void toggleLockdown() {
        if (EmergencyVpnService.isRunning()) {
            stopService(new Intent(this, EmergencyVpnService.class));
            toast("Confinement réseau désactivé.");
            ui.postDelayed(this::showCommandCenter, 350);
            return;
        }
        Intent permission = VpnService.prepare(this);
        if (permission != null) startActivityForResult(permission, REQ_VPN);
        else startEmergencyVpn();
    }

    private void startEmergencyVpn() {
        captureIncident("Confinement réseau", "Le bouclier réseau local a été activé.");
        Intent service = new Intent(this, EmergencyVpnService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        toast("Confinement actif : le trafic du téléphone est bloqué.");
        ui.postDelayed(this::showCommandCenter, 500);
    }

    private void showWifiGuardian() {
        WifiSnapshot snap = wifiSnapshot();
        if (!snap.connected) {
            showText("Gardien Wi-Fi", "Aucun réseau Wi-Fi actif.\n\nLe gardien compare uniquement le réseau auquel le téléphone est connecté.");
            return;
        }
        String key = "trusted_wifi_" + safe(snap.ssid);
        String trusted = prefs20.getString(key, "");
        List<String> warnings = new ArrayList<>();
        if (!snap.validated) warnings.add("La connexion Internet n'est pas validée par Android.");
        if (snap.captive) warnings.add("Un portail captif est détecté.");
        if (trusted.isEmpty()) warnings.add("Ce réseau n'a pas encore d'empreinte de confiance.");
        else {
            try {
                JSONObject old = new JSONObject(trusted);
                if (!same(old.optString("bssid"), snap.bssid)) warnings.add("Le point d'accès BSSID a changé.");
                if (!same(old.optString("gateway"), snap.gateway)) warnings.add("La passerelle a changé.");
                if (!same(old.optString("dns"), snap.dns)) warnings.add("Les serveurs DNS ont changé.");
            } catch (Exception e) { warnings.add("L'ancienne empreinte est illisible."); }
        }
        int score = Math.max(10, 100 - warnings.size() * 18);
        StringBuilder b = new StringBuilder();
        b.append("RÉSEAU\n").append(snap.ssid).append("\n\n");
        b.append("BSSID : ").append(snap.bssid).append('\n');
        b.append("Signal : ").append(snap.rssi).append(" dBm\n");
        b.append("IP : ").append(snap.ip).append('\n');
        b.append("Passerelle : ").append(snap.gateway).append('\n');
        b.append("DNS : ").append(snap.dns).append('\n');
        b.append("Internet validé : ").append(snap.validated ? "oui" : "non").append('\n');
        b.append("Portail captif : ").append(snap.captive ? "oui" : "non").append("\n\n");
        b.append("SCORE DE CONFIANCE : ").append(score).append(" / 100\n\n");
        if (warnings.isEmpty()) b.append("Aucune différence importante détectée.");
        else for (String w : warnings) b.append("• ").append(w).append('\n');
        new AlertDialog.Builder(this).setTitle("Gardien Wi-Fi").setMessage(b.toString())
                .setPositiveButton("Faire confiance", (d, w) -> trustWifi(snap))
                .setNeutralButton("Créer un incident", (d, w) -> captureIncident("Alerte Wi-Fi", b.toString()))
                .setNegativeButton("Fermer", null).show();
    }

    private void trustWifi(WifiSnapshot snap) {
        try {
            JSONObject o = new JSONObject();
            o.put("ssid", snap.ssid); o.put("bssid", snap.bssid); o.put("gateway", snap.gateway); o.put("dns", snap.dns); o.put("time", System.currentTimeMillis());
            prefs20.edit().putString("trusted_wifi_" + safe(snap.ssid), o.toString()).apply();
            toast("Empreinte Wi-Fi enregistrée.");
        } catch (Exception e) { toast("Impossible d'enregistrer l'empreinte."); }
    }

    private void showBluetoothWatch() {
        List<DeviceSnapshot> devices = deviceSnapshots(true);
        long now = System.currentTimeMillis();
        int recent = 0, unnamed = 0, veryClose = 0;
        Map<String,Integer> names = new HashMap<>();
        for (DeviceSnapshot d : devices) {
            if (now - d.lastSeen < 60000) recent++;
            if (d.name.trim().isEmpty() || d.name.toLowerCase(Locale.ROOT).contains("unknown") || d.name.toLowerCase(Locale.ROOT).contains("inconnu")) unnamed++;
            if (d.rssi >= -48 && now - d.lastSeen < 30000) veryClose++;
            String n = d.name.toLowerCase(Locale.ROOT).trim();
            names.put(n, names.getOrDefault(n, 0) + 1);
        }
        int duplicates = 0;
        for (int count : names.values()) if (count >= 3) duplicates++;
        StringBuilder b = new StringBuilder();
        b.append("Appareils Bluetooth connus : ").append(devices.size()).append('\n');
        b.append("Observés depuis moins d'une minute : ").append(recent).append('\n');
        b.append("Noms vides ou génériques : ").append(unnamed).append('\n');
        b.append("Signaux très proches : ").append(veryClose).append('\n');
        b.append("Noms répétés au moins 3 fois : ").append(duplicates).append("\n\n");
        if (recent > 18) b.append("• Environnement Bluetooth très dense.\n");
        if (unnamed >= 4) b.append("• Plusieurs appareils ne donnent pas de nom exploitable.\n");
        if (duplicates > 0) b.append("• Plusieurs identités partagent le même nom.\n");
        if (veryClose > 0) b.append("• Au moins un émetteur a été reçu avec un signal très fort.\n");
        if (recent <= 18 && unnamed < 4 && duplicates == 0) b.append("Aucun motif inhabituel évident dans les données disponibles.\n");
        b.append("\nCe résultat indique une anomalie possible, pas l'identité d'un gadget.");
        new AlertDialog.Builder(this).setTitle("Surveillance Bluetooth").setMessage(b.toString())
                .setPositiveButton("Rechercher un signal", (d, w) -> invokePrivate(V15Activity.class, "showHunterPicker"))
                .setNeutralButton("Enregistrer", (d, w) -> captureIncident("Observation Bluetooth", b.toString()))
                .setNegativeButton("Fermer", null).show();
    }

    private void showSpatialPulse() {
        WifiSnapshot wifi = wifiSnapshot();
        List<DeviceSnapshot> all = deviceSnapshots(false);
        int recent = 0;
        for (DeviceSnapshot d : all) if (System.currentTimeMillis() - d.lastSeen < 120000) recent++;
        float base = prefs20.getFloat("pressure_base", 0f);
        if (base == 0f && pressure > 0f) { base = pressure; prefs20.edit().putFloat("pressure_base", pressure).apply(); }
        float delta = pressure > 0 && base > 0 ? pressure - base : 0;
        String motion = acceleration < 0.7f ? "téléphone plutôt immobile" : acceleration < 2.5f ? "mouvement léger" : "mouvement marqué";
        StringBuilder b = new StringBuilder();
        b.append("PULSE SPATIAL\n\n");
        b.append("Wi-Fi : ").append(wifi.connected ? wifi.ssid + " · " + wifi.rssi + " dBm" : "non connecté").append('\n');
        b.append("Appareils observés récemment : ").append(recent).append('\n');
        b.append("Pression : ").append(pressure > 0 ? String.format(Locale.FRANCE, "%.2f hPa", pressure) : "capteur absent").append('\n');
        b.append("Écart de pression : ").append(String.format(Locale.FRANCE, "%+.2f hPa", delta)).append('\n');
        b.append("Lumière : ").append(light > 0 ? String.format(Locale.FRANCE, "%.0f lux", light) : "non disponible").append('\n');
        b.append("Mouvement : ").append(motion).append("\n\n");
        if (Math.abs(delta) > 0.28f) b.append("Une variation de hauteur ou de météo est possible. La pression seule ne permet pas de trancher.\n");
        b.append("Lieu probable : ").append(recognizedPlace()).append('\n');
        b.append("Niveau de risque actuel : ").append(riskLabel(currentRiskScore())).append('.');
        showText("Pulse spatial", b.toString());
    }

    private void managePlaces() {
        Set<String> names = new HashSet<>(prefs20.getStringSet("place_names", new HashSet<>()));
        List<String> sorted = new ArrayList<>(names); Collections.sort(sorted);
        StringBuilder b = new StringBuilder("Lieu reconnu : ").append(recognizedPlace()).append("\n\nLieux enregistrés :\n");
        if (sorted.isEmpty()) b.append("Aucun"); else for (String n : sorted) b.append("• ").append(n).append('\n');
        new AlertDialog.Builder(this).setTitle("Lieux et arrivées").setMessage(b.toString())
                .setPositiveButton("Enregistrer ce lieu", (d, w) -> askPlaceName())
                .setNeutralButton("Réinitialiser", (d, w) -> prefs20.edit().remove("place_names").apply())
                .setNegativeButton("Fermer", null).show();
    }

    private void askPlaceName() {
        EditText input = new EditText(this); input.setHint("Exemple : Maison"); input.setSingleLine(true);
        FrameLayout wrap = new FrameLayout(this); wrap.setPadding(dp(18), dp(8), dp(18), 0); wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Nom du lieu").setView(wrap)
                .setPositiveButton("Enregistrer", (d, w) -> savePlace(input.getText().toString().trim()))
                .setNegativeButton("Annuler", null).show();
    }

    private void savePlace(String name) {
        if (name.isEmpty()) { toast("Nom invalide."); return; }
        try {
            JSONObject fp = new JSONObject(); WifiSnapshot wifi = wifiSnapshot();
            fp.put("ssid", wifi.ssid); fp.put("bssid", wifi.bssid); fp.put("devices", recentDeviceIds()); fp.put("pressure", pressure); fp.put("time", System.currentTimeMillis());
            Set<String> names = new HashSet<>(prefs20.getStringSet("place_names", new HashSet<>())); names.add(name);
            prefs20.edit().putStringSet("place_names", names).putString("place_" + safe(name), fp.toString()).apply();
            toast("Lieu enregistré : " + name);
        } catch (Exception e) { toast("Enregistrement impossible."); }
    }

    private String recognizedPlace() {
        Set<String> names = prefs20.getStringSet("place_names", new HashSet<>());
        if (names == null || names.isEmpty()) return "non déterminé";
        WifiSnapshot wifi = wifiSnapshot(); Set<String> current = recentDeviceIdsSet();
        String bestName = "non déterminé"; int best = 0;
        for (String name : names) {
            try {
                JSONObject fp = new JSONObject(prefs20.getString("place_" + safe(name), "{}"));
                int score = 0;
                if (same(fp.optString("ssid"), wifi.ssid) && !wifi.ssid.isEmpty()) score += 45;
                if (same(fp.optString("bssid"), wifi.bssid) && !wifi.bssid.isEmpty()) score += 25;
                JSONArray ids = fp.optJSONArray("devices");
                if (ids != null) for (int i = 0; i < ids.length(); i++) if (current.contains(ids.optString(i))) score += 3;
                if (score > best) { best = score; bestName = name; }
            } catch (Exception ignored) { }
        }
        return best >= 35 ? bestName + " (" + Math.min(96, best) + " %)" : "non déterminé";
    }

    private void showAppRadar() {
        final Dialog loading = new AlertDialog.Builder(this).setTitle("Radar des applications").setMessage("Analyse en cours…").create();
        loading.show();
        new Thread(() -> {
            List<AppRisk> list = scanApps();
            runOnUiThread(() -> {
                loading.dismiss();
                StringBuilder b = new StringBuilder();
                b.append("Applications visibles analysées : ").append(list.size()).append("\n\n");
                int limit = Math.min(14, list.size());
                for (int i = 0; i < limit; i++) {
                    AppRisk r = list.get(i);
                    b.append(i + 1).append(". ").append(r.label).append(" · risque ").append(r.score).append("\n   ").append(r.reasons).append("\n");
                }
                b.append("\nUn score élevé signifie beaucoup de capacités sensibles, pas forcément une application malveillante.");
                new AlertDialog.Builder(this).setTitle("Radar des applications").setMessage(b.toString())
                        .setPositiveButton("Réglages des applications", (d, w) -> openSettings(Settings.ACTION_APPLICATION_SETTINGS))
                        .setNeutralButton("Enregistrer", (d, w) -> captureIncident("Audit des applications", b.toString()))
                        .setNegativeButton("Fermer", null).show();
            });
        }).start();
    }

    private List<AppRisk> scanApps() {
        List<AppRisk> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<PackageInfo> packages;
        try { packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS); }
        catch (Exception e) { packages = new ArrayList<>(); }
        Set<String> sensitive = new HashSet<>(Arrays.asList(
                Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG,
                "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.QUERY_ALL_PACKAGES"));
        for (PackageInfo p : packages) {
            int score = 0; List<String> reasons = new ArrayList<>();
            if (p.requestedPermissions != null) for (String perm : p.requestedPermissions) if (sensitive.contains(perm)) {
                score += perm.contains("SMS") || perm.contains("CALL_LOG") ? 16 : 10;
                reasons.add(shortPermission(perm));
            }
            ApplicationInfo ai = p.applicationInfo;
            if (ai != null && (ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) { score += 12; reasons.add("débogable"); }
            String installer = null;
            try { installer = pm.getInstallerPackageName(p.packageName); } catch (Exception ignored) { }
            if (installer == null && ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) { score += 8; reasons.add("origine non indiquée"); }
            if (score <= 0) continue;
            String label;
            try { label = String.valueOf(pm.getApplicationLabel(ai)); } catch (Exception e) { label = p.packageName; }
            out.add(new AppRisk(label, Math.min(100, score), TextUtils.join(", ", reasons)));
        }
        out.sort(Comparator.comparingInt((AppRisk x) -> x.score).reversed());
        return out;
    }

    private void showPhoneAudit() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean secure = keyguard != null && keyguard.isDeviceSecure();
        int adb = settingGlobal(Settings.Global.ADB_ENABLED);
        int dev = settingGlobal(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        int encryption = dpm == null ? DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED : dpm.getStorageEncryptionStatus();
        boolean vpn = activeVpn();
        BatteryState battery = batteryState();
        int score = 100;
        if (!secure) score -= 35; if (adb == 1) score -= 18; if (dev == 1) score -= 8; if (!vpn && !wifiSnapshot().validated) score -= 8;
        StringBuilder b = new StringBuilder();
        b.append("SCORE DE SÉCURITÉ : ").append(Math.max(0, score)).append(" / 100\n\n");
        b.append("Verrouillage sécurisé : ").append(secure ? "oui" : "non").append('\n');
        b.append("Options développeur : ").append(dev == 1 ? "activées" : "désactivées").append('\n');
        b.append("Débogage USB : ").append(adb == 1 ? "activé" : "désactivé").append('\n');
        b.append("Chiffrement stockage : ").append(encryptionLabel(encryption)).append('\n');
        b.append("VPN actif : ").append(vpn ? "oui" : "non").append('\n');
        b.append("Batterie : ").append(battery.level).append(" % · ").append(String.format(Locale.FRANCE, "%.1f °C", battery.temperature)).append('\n');
        if (Build.VERSION.SDK_INT >= 29) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) b.append("État thermique : ").append(pm.getCurrentThermalStatus()).append('\n');
        }
        new AlertDialog.Builder(this).setTitle("Audit du téléphone").setMessage(b.toString())
                .setPositiveButton("Réglages sécurité", (d, w) -> openSettings(Settings.ACTION_SECURITY_SETTINGS))
                .setNeutralButton("Options développeur", (d, w) -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                .setNegativeButton("Fermer", null).show();
    }

    private void showNetworkObservatory() {
        WifiSnapshot s = wifiSnapshot();
        String summary = "Réseau : " + (s.connected ? s.ssid : "aucun") + "\nIP : " + s.ip + "\nPasserelle : " + s.gateway + "\nDNS : " + s.dns + "\n\nChoisis l'outil à ouvrir.";
        new AlertDialog.Builder(this).setTitle("Observatoire réseau").setMessage(summary)
                .setPositiveButton("Rayons X réseau", (d, w) -> invokePrivate(V10Activity.class, "showNetworkXray"))
                .setNeutralButton("Centre technique", (d, w) -> invokePrivate(V12Activity.class, "showHubPage"))
                .setNegativeButton("Fermer", null).show();
    }

    private void showCertificateCenter() {
        String message = "VPN actif : " + (activeVpn() ? "oui" : "non") + "\nConfinement Sentry : " + (EmergencyVpnService.isRunning() ? "actif" : "inactif") +
                "\n\nAndroid ne donne pas à une application normale la liste complète des certificats de toutes les autres applications. Utilise les écrans système ci-dessous.";
        new AlertDialog.Builder(this).setTitle("Certificats et VPN").setMessage(message)
                .setPositiveButton("Certificats", (d, w) -> openSettings("com.android.settings.TRUSTED_CREDENTIALS"))
                .setNeutralButton("VPN", (d, w) -> openSettings(Settings.ACTION_VPN_SETTINGS))
                .setNegativeButton("Fermer", null).show();
    }

    private void configureReactor() {
        String[] labels = {"Nouvel appareil proche", "Changement d'identité Wi-Fi", "Variation de pression", "Arrivée ou départ d'un lieu", "Créer automatiquement un incident"};
        boolean[] values = {prefs20.getBoolean("react_new_device", true), prefs20.getBoolean("react_wifi_change", true), prefs20.getBoolean("react_pressure", false), prefs20.getBoolean("react_place", true), prefs20.getBoolean("react_auto_incident", false)};
        new AlertDialog.Builder(this).setTitle("Réacteur d'événements").setMultiChoiceItems(labels, values, (d, which, checked) -> values[which] = checked)
                .setPositiveButton("Enregistrer", (d, w) -> prefs20.edit()
                        .putBoolean("react_new_device", values[0]).putBoolean("react_wifi_change", values[1])
                        .putBoolean("react_pressure", values[2]).putBoolean("react_place", values[3])
                        .putBoolean("react_auto_incident", values[4]).apply())
                .setNeutralButton("Voir le journal", (d, w) -> showText("Journal du réacteur", prefs20.getString("reactor_log", "Aucun événement.")))
                .setNegativeButton("Annuler", null).show();
    }

    private void chooseMissionMode() {
        String[] modes = {"Quotidien", "Recherche d'objet", "Wi-Fi public", "Audit maison", "Voyage", "Incident", "Économie", "Démonstration"};
        int selected = Math.max(0, Arrays.asList(modes).indexOf(prefs20.getString("mode", "Quotidien")));
        new AlertDialog.Builder(this).setTitle("Modes de mission").setSingleChoiceItems(modes, selected, (d, which) -> {
            String mode = modes[which]; prefs20.edit().putString("mode", mode).apply(); applyMissionMode(mode); d.dismiss(); toast("Mode activé : " + mode); showCommandCenter();
        }).setNegativeButton("Annuler", null).show();
    }

    private void applyMissionMode(String mode) {
        SharedPreferences v14 = getSharedPreferences("sentry_v14", MODE_PRIVATE);
        SharedPreferences.Editor e = v14.edit();
        if ("Économie".equals(mode)) e.putString("scan_mode", "eco").putBoolean("smart_scan", true);
        else if ("Incident".equals(mode) || "Recherche d'objet".equals(mode)) e.putString("scan_mode", "fast").putBoolean("smart_scan", true);
        else e.putString("scan_mode", "balanced").putBoolean("smart_scan", true);
        e.apply();
        if ("Wi-Fi public".equals(mode)) prefs20.edit().putBoolean("react_wifi_change", true).putBoolean("react_auto_incident", true).apply();
        if ("Voyage".equals(mode)) prefs20.edit().putBoolean("react_place", true).putBoolean("react_new_device", true).apply();
    }

    private void showLocalBrief() {
        WifiSnapshot w = wifiSnapshot(); int risk = currentRiskScore(); String place = recognizedPlace();
        List<DeviceSnapshot> devices = deviceSnapshots(false); int recent = 0; for (DeviceSnapshot d : devices) if (System.currentTimeMillis() - d.lastSeen < 120000) recent++;
        StringBuilder b = new StringBuilder();
        b.append("RÉSUMÉ LOCAL\n\n");
        b.append(w.connected ? "Le téléphone est connecté au Wi-Fi « " + w.ssid + " » avec un signal de " + w.rssi + " dBm. " : "Le téléphone n'est pas connecté au Wi-Fi. ");
        b.append(w.validated ? "Android confirme l'accès à Internet. " : "L'accès à Internet n'est pas confirmé. ");
        if (w.captive) b.append("Un portail captif est présent. ");
        b.append("\n\n").append(recent).append(" appareils ont été observés récemment. Le lieu probable est ").append(place).append(". ");
        if (pressure > 0) b.append("La pression mesurée est de ").append(String.format(Locale.FRANCE, "%.2f hPa", pressure)).append(". ");
        b.append("\n\nLe niveau de risque calculé est ").append(riskLabel(risk)).append(" (").append(risk).append("/100). ");
        if (risk < 35) b.append("Aucune anomalie importante n'est visible dans les données actuelles.");
        else if (risk < 65) b.append("Quelques éléments méritent une vérification, sans preuve d'attaque.");
        else b.append("Plusieurs signaux inhabituels sont présents : une capture d'incident est conseillée.");
        b.append("\n\nCe texte est généré localement par des règles, pas par une analyse en ligne.");
        showText("Résumé intelligent local", b.toString());
    }

    private void showTrainingSimulator() {
        String[] scenarios = {"Faux Wi-Fi probable", "DNS modifié", "Balise Bluetooth persistante", "Objet favori perdu", "Changement d'étage", "Nouvel appareil réseau"};
        new AlertDialog.Builder(this).setTitle("Simulateur d'entraînement").setItems(scenarios, (d, which) -> runScenario(scenarios[which])).setNegativeButton("Fermer", null).show();
    }

    private void runScenario(String scenario) {
        String steps;
        switch (scenario) {
            case "Faux Wi-Fi probable": steps = "1. Ne saisis aucun mot de passe.\n2. Ouvre Gardien Wi-Fi.\n3. Compare BSSID, DNS et passerelle.\n4. Utilise Confinement réseau si nécessaire.\n5. Crée une capture d'incident."; break;
            case "DNS modifié": steps = "1. Note les nouveaux DNS.\n2. Vérifie si le routeur a été reconfiguré.\n3. Coupe la connexion si le changement est inexpliqué.\n4. Restaure un DNS de confiance depuis les réglages réseau."; break;
            case "Balise Bluetooth persistante": steps = "1. Observe plusieurs minutes.\n2. Déplace-toi pour voir si le signal te suit.\n3. Lance Recherche de signal.\n4. Ne conclus pas à une attaque avec un seul RSSI."; break;
            case "Objet favori perdu": steps = "1. Retourne au dernier lieu connu.\n2. Lance Recherche de signal.\n3. Marche lentement.\n4. Recalibre la direction après chaque obstacle."; break;
            case "Changement d'étage": steps = "1. Calibre la pression au point de départ.\n2. Monte ou descends.\n3. Compare pression et mouvement.\n4. Confirme manuellement l'étage pour améliorer le profil."; break;
            default: steps = "1. Vérifie qu'il s'agit de ton réseau.\n2. Observe le nom, l'adresse et l'heure d'apparition.\n3. Classe l'appareil dans un dossier.\n4. Enregistre une référence si l'appareil est légitime.";
        }
        new AlertDialog.Builder(this).setTitle("Entraînement : " + scenario).setMessage("SCÉNARIO FICTIF\n\n" + steps + "\n\nAucune attaque réelle n'est générée.")
                .setPositiveButton("Créer un faux incident", (d, w) -> captureIncident("Simulation : " + scenario, steps))
                .setNegativeButton("Terminer", null).show();
    }

    private void showIncidentLab() {
        List<Incident> incidents = loadIncidents();
        if (incidents.isEmpty()) { showText("Laboratoire d'incidents", "Aucun incident enregistré."); return; }
        String[] labels = new String[incidents.size()];
        for (int i = 0; i < incidents.size(); i++) labels[i] = date(incidents.get(i).time) + " · " + incidents.get(i).title;
        new AlertDialog.Builder(this).setTitle("Laboratoire d'incidents").setItems(labels, (d, which) -> showIncident(incidents.get(which)))
                .setNeutralButton("Tout supprimer", (d, w) -> new AlertDialog.Builder(this).setTitle("Supprimer le coffre ?").setMessage("Tous les incidents seront effacés définitivement.")
                        .setPositiveButton("Supprimer", (x, y) -> prefs20.edit().remove("vault_records").apply()).setNegativeButton("Annuler", null).show())
                .setNegativeButton("Fermer", null).show();
    }

    private void showIncident(Incident i) {
        String body = "Date : " + date(i.time) + "\nNiveau : " + i.severity + "\n\n" + i.details;
        new AlertDialog.Builder(this).setTitle(i.title).setMessage(body)
                .setPositiveButton("Copier", (d, w) -> copyText(i.title + "\n" + body))
                .setNeutralButton("Exporter", (d, w) -> beginExport(i.title + "\n\n" + body, "incident-sentry.txt"))
                .setNegativeButton("Fermer", null).show();
    }

    private void showVaultStatus() {
        String state;
        try { Vault.ensureKey(); state = "clé Android Keystore disponible"; }
        catch (Exception e) { state = "erreur de clé : " + e.getClass().getSimpleName(); }
        showText("Coffre chiffré", "État : " + state + "\nÉléments protégés : " + incidentCount() + "\nAlgorithme : AES-GCM 256 bits\n\nLa clé reste dans Android Keystore.");
    }

    private void exportReport() {
        StringBuilder b = new StringBuilder();
        b.append("RAPPORT SENTRY V20\n").append(date(System.currentTimeMillis())).append("\n\n");
        b.append("Mode : ").append(prefs20.getString("mode", "Quotidien")).append('\n');
        b.append("Lieu probable : ").append(recognizedPlace()).append('\n');
        b.append("Niveau de risque : ").append(currentRiskScore()).append(" / 100\n\n");
        WifiSnapshot w = wifiSnapshot();
        b.append("WI-FI\nSSID : ").append(w.ssid).append("\nBSSID : ").append(w.bssid).append("\nIP : ").append(w.ip).append("\nPasserelle : ").append(w.gateway).append("\nDNS : ").append(w.dns).append("\n\n");
        b.append("CAPTEURS\nPression : ").append(pressure).append(" hPa\nLumière : ").append(light).append(" lux\nMouvement : ").append(acceleration).append("\n\n");
        b.append("INCIDENTS\n");
        for (Incident i : loadIncidents()) b.append("- ").append(date(i.time)).append(" · ").append(i.title).append(" · ").append(i.severity).append('\n');
        b.append("\nRapport local. Les positions et directions radio sont des estimations.");
        beginExport(b.toString(), "rapport-sentry-v20.txt");
    }

    private void beginExport(String text, String name) {
        pendingExport = text;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN && resultCode == Activity.RESULT_OK) startEmergencyVpn();
        if (requestCode == REQ_EXPORT && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out != null) out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                toast("Rapport exporté.");
            } catch (Exception e) { toast("Export impossible."); }
        }
    }

    private void captureIncident(String title, String note) {
        try {
            WifiSnapshot w = wifiSnapshot(); JSONObject o = new JSONObject();
            o.put("time", System.currentTimeMillis()); o.put("title", title); o.put("severity", riskLabel(currentRiskScore()));
            o.put("details", note + "\n\nWi-Fi : " + w.ssid + "\nBSSID : " + w.bssid + "\nIP : " + w.ip + "\nPasserelle : " + w.gateway + "\nDNS : " + w.dns + "\nAppareils récents : " + recentDeviceIdsSet().size() + "\nPression : " + pressure + " hPa\nMode : " + prefs20.getString("mode", "Quotidien"));
            JSONArray records = new JSONArray(prefs20.getString("vault_records", "[]"));
            records.put(Vault.encrypt(o.toString()));
            while (records.length() > 120) removeFirst(records);
            prefs20.edit().putString("vault_records", records.toString()).apply();
            toast("Incident enregistré dans le coffre.");
        } catch (Exception e) { toast("Impossible d'enregistrer l'incident."); }
    }

    private List<Incident> loadIncidents() {
        List<Incident> out = new ArrayList<>();
        try {
            JSONArray records = new JSONArray(prefs20.getString("vault_records", "[]"));
            for (int i = records.length() - 1; i >= 0; i--) {
                try {
                    JSONObject o = new JSONObject(Vault.decrypt(records.optString(i)));
                    out.add(new Incident(o.optLong("time"), o.optString("title"), o.optString("severity"), o.optString("details")));
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private int incidentCount() {
        try { return new JSONArray(prefs20.getString("vault_records", "[]")).length(); }
        catch (Exception e) { return 0; }
    }

    private void runEventReactor() {
        if (prefs20 == null) return;
        long now = System.currentTimeMillis(); if (now - lastReactorRun < 10000) return; lastReactorRun = now;
        String wifi = wifiSnapshot().signature();
        String previousWifi = prefs20.getString("reactor_wifi", "");
        String devices = TextUtils.join("|", recentDeviceIdsSet());
        String previousDevices = prefs20.getString("reactor_devices", "");
        float previousPressure = prefs20.getFloat("reactor_pressure_value", pressure);
        String place = recognizedPlace(); String previousPlace = prefs20.getString("reactor_place_value", place);
        List<String> events = new ArrayList<>();
        if (prefs20.getBoolean("react_wifi_change", true) && !previousWifi.isEmpty() && !same(previousWifi, wifi)) events.add("Identité du réseau modifiée");
        if (prefs20.getBoolean("react_new_device", true) && !previousDevices.isEmpty()) {
            Set<String> before = new HashSet<>(Arrays.asList(previousDevices.split("\\|")));
            for (String id : recentDeviceIdsSet()) if (!before.contains(id)) { events.add("Nouvel appareil observé : " + id); break; }
        }
        if (prefs20.getBoolean("react_pressure", false) && pressure > 0 && Math.abs(pressure - previousPressure) >= .35f) events.add("Variation de pression : " + String.format(Locale.FRANCE, "%+.2f hPa", pressure - previousPressure));
        if (prefs20.getBoolean("react_place", true) && !previousPlace.isEmpty() && !same(previousPlace, place)) events.add("Lieu probable : " + previousPlace + " → " + place);
        if (!events.isEmpty()) {
            String line = "[" + new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(new Date()) + "] " + TextUtils.join(" · ", events) + "\n" + prefs20.getString("reactor_log", "");
            if (line.length() > 30000) line = line.substring(0, 30000);
            prefs20.edit().putString("reactor_log", line).apply();
            if (prefs20.getBoolean("react_auto_incident", false)) captureIncident("Réacteur d'événements", TextUtils.join("\n", events));
        }
        prefs20.edit().putString("reactor_wifi", wifi).putString("reactor_devices", devices).putFloat("reactor_pressure_value", pressure).putString("reactor_place_value", place).apply();
    }

    private int currentRiskScore() {
        int risk = 8; WifiSnapshot w = wifiSnapshot();
        if (w.connected && !w.validated) risk += 18;
        if (w.captive) risk += 14;
        if (w.connected && prefs20.getString("trusted_wifi_" + safe(w.ssid), "").isEmpty()) risk += 8;
        int recent = 0, unnamed = 0, close = 0;
        for (DeviceSnapshot d : deviceSnapshots(true)) {
            if (System.currentTimeMillis() - d.lastSeen < 60000) recent++;
            if (d.name.toLowerCase(Locale.ROOT).contains("unknown") || d.name.toLowerCase(Locale.ROOT).contains("inconnu")) unnamed++;
            if (d.rssi >= -48 && System.currentTimeMillis() - d.lastSeen < 30000) close++;
        }
        if (recent > 20) risk += 12; if (unnamed >= 5) risk += 10; if (close >= 3) risk += 8;
        if (settingGlobal(Settings.Global.ADB_ENABLED) == 1) risk += 10;
        return Math.min(100, risk);
    }

    private WifiSnapshot wifiSnapshot() {
        WifiSnapshot s = new WifiSnapshot();
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = cm == null || network == null ? null : cm.getNetworkCapabilities(network);
            LinkProperties lp = cm == null || network == null ? null : cm.getLinkProperties(network);
            s.connected = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            s.validated = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            s.captive = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);
            if (wi != null) { s.ssid = cleanSsid(wi.getSSID()); s.bssid = value(wi.getBSSID()); s.rssi = wi.getRssi(); }
            if (lp != null) {
                List<String> dns = new ArrayList<>(); for (InetAddress a : lp.getDnsServers()) dns.add(a.getHostAddress()); s.dns = TextUtils.join(", ", dns);
                List<String> ips = new ArrayList<>(); for (LinkAddress a : lp.getLinkAddresses()) ips.add(a.getAddress().getHostAddress()); s.ip = TextUtils.join(", ", ips);
                for (RouteInfo r : lp.getRoutes()) if (r.isDefaultRoute() && r.getGateway() != null) { s.gateway = r.getGateway().getHostAddress(); break; }
            }
        } catch (Exception ignored) { }
        return s;
    }

    private List<DeviceSnapshot> deviceSnapshots(boolean bluetoothOnly) {
        List<DeviceSnapshot> out = new ArrayList<>(); Object raw = getPrivateValue(V4Activity.class, "devices");
        if (!(raw instanceof Map)) return out;
        for (Object value : ((Map<?, ?>) raw).values()) {
            try {
                DeviceSnapshot d = new DeviceSnapshot(); d.id = String.valueOf(readField(value, "id")); d.name = String.valueOf(readField(value, "name")); d.source = String.valueOf(readField(value, "source"));
                Object rssi = readField(value, "rssi"); Object last = readField(value, "lastSeen"); d.rssi = rssi instanceof Number ? ((Number) rssi).intValue() : -100; d.lastSeen = last instanceof Number ? ((Number) last).longValue() : 0;
                if (!bluetoothOnly || d.source.toLowerCase(Locale.ROOT).contains("bluetooth") || d.id.startsWith("ble:")) out.add(d);
            } catch (Exception ignored) { }
        }
        out.sort(Comparator.comparingLong((DeviceSnapshot d) -> d.lastSeen).reversed());
        return out;
    }

    private JSONArray recentDeviceIds() { JSONArray a = new JSONArray(); for (String id : recentDeviceIdsSet()) a.put(id); return a; }
    private Set<String> recentDeviceIdsSet() { Set<String> ids = new HashSet<>(); long now = System.currentTimeMillis(); for (DeviceSnapshot d : deviceSnapshots(false)) if (now - d.lastSeen < 180000) ids.add(d.id); return ids; }

    private boolean activeVpn() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            for (Network n : cm.getAllNetworks()) { NetworkCapabilities c = cm.getNetworkCapabilities(n); if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true; }
        } catch (Exception ignored) { }
        return false;
    }

    private BatteryState batteryState() {
        Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return new BatteryState(0, 0);
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0); int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100); int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        return new BatteryState(scale == 0 ? level : Math.round(level * 100f / scale), temp / 10f);
    }

    private void registerSensors() {
        if (sensorManager == null) return;
        register(Sensor.TYPE_PRESSURE); register(Sensor.TYPE_LIGHT); register(Sensor.TYPE_LINEAR_ACCELERATION);
    }
    private void register(int type) { Sensor s = sensorManager.getDefaultSensor(type); if (s != null) sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL); }
    private void unregisterSensors() { if (sensorManager != null) sensorManager.unregisterListener(this); }
    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_PRESSURE) pressure = e.values[0];
        else if (e.sensor.getType() == Sensor.TYPE_LIGHT) light = e.values[0];
        else if (e.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && e.values.length >= 3) acceleration = (float)Math.sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2]);
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void translateVisibleUi() {
        View root = getWindow().getDecorView();
        Map<String,String> map = new LinkedHashMap<>();
        map.put("Universe", "Univers"); map.put("Inspect", "Inspecter"); map.put("Rooms", "Pièces"); map.put("Timeline", "Chronologie"); map.put("Living", "Vivant"); map.put("Intel", "Données"); map.put("Hunter", "Recherche");
        map.put("Find My Device", "Retrouver un appareil"); map.put("Find My Device+", "Retrouver un appareil+"); map.put("Favorites", "Favoris"); map.put("What Changed?", "Qu'est-ce qui a changé ?");
        map.put("Compare Scans", "Comparer les scans"); map.put("Room Scan", "Scanner une pièce"); map.put("Focus Device", "Ouvrir un appareil"); map.put("Daily Story", "Résumé quotidien"); map.put("Cinematic Scan", "Scan cinématique");
        map.put("Signal Hunter", "Recherche de signal"); map.put("Reaction Settings", "Réglages des réactions"); map.put("Rescan", "Relancer le scan"); map.put("Close", "Fermer"); map.put("Settings", "Réglages"); map.put("Export", "Exporter");
        for (Map.Entry<String,String> e : map.entrySet()) replaceExact(root, e.getKey(), e.getValue());
    }

    private void activateCenterNav() {
        LinearLayout nav = getPrivateField(V4Activity.class, "nav", LinearLayout.class); if (nav == null) return;
        for (int i = 0; i < nav.getChildCount(); i++) if (nav.getChildAt(i) instanceof TextView) {
            TextView child = (TextView) nav.getChildAt(i); boolean active = i == 5;
            child.setTextColor(active ? Color.rgb(2, 25, 22) : MUTED); child.setBackground(active ? rounded(CYAN, 17) : rounded(Color.TRANSPARENT, 17));
        }
    }

    private void section(LinearLayout root, String label) { root.addView(space(17)); TextView t = text(label, 11, MUTED, true); t.setLetterSpacing(.13f); root.addView(t); root.addView(space(7)); }
    private TextView metric(String label, String value, int color) { TextView v = text(label + "\n" + value, 9.2f, color, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(4), dp(10), dp(4), dp(10)); v.setBackground(rounded(PANEL, 14)); return v; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }
    private TextView text(String value, float size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(0, 1.12f); if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); return t; }
    private GradientDrawable rounded(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private View space(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View spaceW(int w) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return v; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void showText(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Fermer", null).show(); }
    private String date(long ms) { return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(new Date(ms)); }
    private String safe(String v) { return v == null ? "vide" : v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_"); }
    private String cleanSsid(String v) { if (v == null || "<unknown ssid>".equalsIgnoreCase(v)) return "inconnu"; return v.replace("\"", ""); }
    private String value(String v) { return v == null ? "—" : v; }
    private boolean same(String a, String b) { return String.valueOf(a).equals(String.valueOf(b)); }
    private int settingGlobal(String key) { try { return Settings.Global.getInt(getContentResolver(), key, 0); } catch (Exception e) { return 0; } }
    private String riskLabel(int score) { return score < 35 ? "faible" : score < 65 ? "modéré" : "élevé"; }
    private int riskColor(int score) { return score < 35 ? GOOD : score < 65 ? WARN : BAD; }
    private String encryptionLabel(int status) { if (status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE || status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY || status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER) return "actif"; if (status == DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE) return "inactif"; return "non déterminé"; }
    private String shortPermission(String p) { int i = p.lastIndexOf('.'); String s = i >= 0 ? p.substring(i + 1) : p; return s.toLowerCase(Locale.ROOT).replace('_', ' '); }
    private void openSettings(String action) { try { startActivity(new Intent(action)); } catch (Exception e) { toast("Écran système indisponible."); } }
    private void copyText(String s) { ClipboardManager c = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); if (c != null) c.setPrimaryClip(ClipData.newPlainText("Sentry", s)); toast("Copié."); }
    private void removeFirst(JSONArray a) { try { if (Build.VERSION.SDK_INT >= 19) a.remove(0); } catch (Exception ignored) { } }

    private Object getPrivateValue(Class<?> owner, String name) { try { Field f = owner.getDeclaredField(name); f.setAccessible(true); return f.get(this); } catch (Exception e) { return null; } }
    private <T> T getPrivateField(Class<?> owner, String name, Class<T> type) { Object v = getPrivateValue(owner, name); return type.isInstance(v) ? type.cast(v) : null; }
    private Object readField(Object target, String name) throws Exception { Class<?> t = target.getClass(); while (t != null) { try { Field f = t.getDeclaredField(name); f.setAccessible(true); return f.get(target); } catch (NoSuchFieldException e) { t = t.getSuperclass(); } } throw new NoSuchFieldException(name); }
    private void invokePrivate(Class<?> owner, String name) { try { Method m = owner.getDeclaredMethod(name); m.setAccessible(true); m.invoke(this); } catch (Exception e) { toast("Fonction momentanément indisponible."); } }
    private void replaceExact(View v, String from, String to) { if (v instanceof TextView && from.contentEquals(((TextView)v).getText())) ((TextView)v).setText(to); if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) replaceExact(((ViewGroup)v).getChildAt(i), from, to); }

    private static final class WifiSnapshot {
        boolean connected, validated, captive; String ssid = "inconnu", bssid = "—", ip = "—", gateway = "—", dns = "—"; int rssi = -100;
        String signature() { return ssid + "|" + bssid + "|" + gateway + "|" + dns; }
    }
    private static final class DeviceSnapshot { String id = "", name = "Appareil", source = ""; int rssi = -100; long lastSeen; }
    private static final class AppRisk { final String label; final int score; final String reasons; AppRisk(String l, int s, String r) { label = l; score = s; reasons = r; } }
    private static final class BatteryState { final int level; final float temperature; BatteryState(int l, float t) { level = l; temperature = t; } }
    private static final class Incident { final long time; final String title, severity, details; Incident(long t, String ti, String s, String d) { time = t; title = ti; severity = s; details = d; } }

    private static final class Vault {
        private static final String STORE = "AndroidKeyStore";
        private static final String ALIAS = "sentry_v20_vault";
        static void ensureKey() throws Exception {
            KeyStore ks = KeyStore.getInstance(STORE); ks.load(null); if (ks.containsAlias(ALIAS)) return;
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
            gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            gen.generateKey();
        }
        static SecretKey key() throws Exception { ensureKey(); KeyStore ks = KeyStore.getInstance(STORE); ks.load(null); return (SecretKey)ks.getKey(ALIAS, null); }
        static String encrypt(String plain) throws Exception {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key()); byte[] iv = c.getIV(); byte[] data = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[1 + iv.length + data.length]; out[0] = (byte)iv.length; System.arraycopy(iv, 0, out, 1, iv.length); System.arraycopy(data, 0, out, 1 + iv.length, data.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        }
        static String decrypt(String encoded) throws Exception {
            byte[] all = Base64.decode(encoded, Base64.NO_WRAP); int n = all[0] & 255; byte[] iv = Arrays.copyOfRange(all, 1, 1 + n); byte[] data = Arrays.copyOfRange(all, 1 + n, all.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv)); return new String(c.doFinal(data), StandardCharsets.UTF_8);
        }
    }
}