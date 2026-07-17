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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
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

import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class V2Activity extends Activity {
    private static final int REQ_BLE = 210;
    private static final int REQ_NOTIF = 211;
    private static final int REQ_EXPORT = 212;
    private static final String CHANNEL = "sentry_v2_alerts";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(32);
    private final List<Device> online = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, BleDevice> bleFound = Collections.synchronizedMap(new LinkedHashMap<>());

    private SharedPreferences prefs;
    private FrameLayout content;
    private LinearLayout nav;
    private TextView headerTitle;
    private TextView headerSub;
    private int tab = 0;
    private boolean lanScanning;
    private boolean bleScanning;
    private BluetoothLeScanner bleScanner;

    private int bg, surface, surface2, text, muted, accent, good, warn, bad;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("sentry_local", MODE_PRIVATE);
        defaults();
        channel();
        palette();
        shell();
        render();
        log("Sentry v2.0 démarré · profil " + profile() + ".");
        if (prefs.getBoolean("auto_scan_start", false)) startLanScan();
    }

    private void defaults() {
        SharedPreferences.Editor e = prefs.edit();
        if (!prefs.contains("theme")) e.putString("theme", "dark");
        if (!prefs.contains("accent")) e.putString("accent", "sage");
        if (!prefs.contains("compact")) e.putBoolean("compact", false);
        if (!prefs.contains("privacy_mode")) e.putBoolean("privacy_mode", false);
        if (!prefs.contains("notify_new")) e.putBoolean("notify_new", true);
        if (!prefs.contains("keep_history")) e.putBoolean("keep_history", true);
        if (!prefs.contains("auto_scan_start")) e.putBoolean("auto_scan_start", false);
        if (!prefs.contains("scan_timeout")) e.putInt("scan_timeout", 500);
        if (!prefs.contains("animation")) e.putBoolean("animation", true);
        if (!prefs.contains("profile")) e.putString("profile", "Maison");
        if (!prefs.contains("show_latency")) e.putBoolean("show_latency", true);
        e.apply();
    }

    private String profile() { return prefs.getString("profile", "Maison"); }
    private String pk(String key) { return "p_" + profile() + "_" + key; }

    private void palette() {
        String t = prefs.getString("theme", "dark");
        if ("light".equals(t)) {
            bg = Color.rgb(239, 242, 240); surface = Color.WHITE; surface2 = Color.rgb(228, 234, 230);
            text = Color.rgb(25, 31, 28); muted = Color.rgb(96, 108, 102);
        } else if ("amoled".equals(t)) {
            bg = Color.BLACK; surface = Color.rgb(10, 12, 11); surface2 = Color.rgb(24, 28, 26);
            text = Color.rgb(245, 247, 246); muted = Color.rgb(150, 164, 156);
        } else {
            bg = Color.rgb(16, 19, 18); surface = Color.rgb(27, 32, 30); surface2 = Color.rgb(37, 44, 41);
            text = Color.rgb(241, 244, 242); muted = Color.rgb(158, 173, 165);
        }
        String a = prefs.getString("accent", "sage");
        accent = "blue".equals(a) ? Color.rgb(128, 181, 235) : "violet".equals(a) ? Color.rgb(188, 158, 232) : "amber".equals(a) ? Color.rgb(230, 188, 106) : Color.rgb(158, 207, 181);
        good = Color.rgb(126, 211, 157); warn = Color.rgb(231, 189, 108); bad = Color.rgb(235, 127, 127);
    }

    private void shell() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        root.setOnApplyWindowInsetsListener((v, i) -> { v.setPadding(0, i.getSystemWindowInsetTop(), 0, i.getSystemWindowInsetBottom()); return i; });

        LinearLayout head = new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL); head.setPadding(dp(18), dp(15), dp(18), dp(10));
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = tv("S", 18, Color.rgb(15, 23, 19), true); logo.setGravity(Gravity.CENTER); logo.setBackground(round(accent, 15));
        line.addView(logo, new LinearLayout.LayoutParams(dp(38), dp(38))); line.addView(gapW(11));
        headerTitle = tv("Sentry", 27, text, true); line.addView(headerTitle);
        TextView profile = tv(profile(), 11, accent, true); profile.setPadding(dp(10), dp(6), dp(10), dp(6)); profile.setBackground(round(surface2, 14));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); pp.gravity = Gravity.END; pp.leftMargin = dp(10);
        line.addView(profile, pp); profile.setOnClickListener(v -> chooseProfile());
        head.addView(line); head.addView(gap(4));
        headerSub = tv("Protection locale unifiée", 13, muted, false); head.addView(headerSub); root.addView(head);

        content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(dp(6), dp(7), dp(6), dp(7));
        String[] n = {"Accueil", "Carte", "Analyse", "Activité", "Réglages"};
        for (int i=0;i<n.length;i++) { final int x=i; TextView item=tv(n[i],11,muted,true); item.setGravity(Gravity.CENTER); item.setPadding(dp(3),dp(12),dp(3),dp(12)); item.setOnClickListener(v->{tab=x;render();}); nav.addView(item,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); }
        root.addView(nav); setContentView(root);
    }

    private void rebuild() { palette(); shell(); render(); }

    private void render() {
        String[] tt={"Sentry","Carte du réseau","Centre d’analyse","Activité","Réglages"};
        String[] ss={"Protection locale unifiée","Topologie et appareils détectés","Audit, LAN, Bluetooth et ports","Chronologie du profil " + profile(),"Paramètres globaux et profils"};
        headerTitle.setText(tt[tab]); headerSub.setText(ss[tab]);
        for(int i=0;i<nav.getChildCount();i++){TextView v=(TextView)nav.getChildAt(i);boolean on=i==tab;v.setTextColor(on?Color.rgb(15,23,19):muted);v.setBackground(on?round(accent,17):round(Color.TRANSPARENT,17));}
        content.removeAllViews();
        content.addView(tab==0?dashboard():tab==1?mapPage():tab==2?analysis():tab==3?activity():settings());
    }

    private View dashboard() {
        LinearLayout p=page(); int known=known().size(), trusted=0; for(String ip:known()) if(prefs.getBoolean(pk("trusted_"+ip),false)) trusted++;
        int unknown=Math.max(0,known-trusted); int score=Math.max(35,100-unknown*5-(auditIssues()*7));
        LinearLayout hero=card(); hero.addView(tv("SCORE DE PROTECTION",12,muted,true)); hero.addView(gap(8));
        LinearLayout line=new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        TextView scoreV=tv(score+"",46,score>80?good:score>60?warn:bad,true); line.addView(scoreV);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(14),0,0,0);info.addView(tv(score>80?"Excellent":score>60?"À améliorer":"Attention",18,text,true));info.addView(tv(online.size()+" en ligne · "+known+" mémorisés",13,muted,false));line.addView(info); hero.addView(line);
        hero.addView(gap(10)); hero.addView(progress(score)); p.addView(hero); p.addView(gap(11));

        LinearLayout quick=card();quick.addView(title("Actions rapides"));quick.addView(gap(9));
        TextView all=button(lanScanning?"Analyse en cours…":"Analyse intelligente",true);all.setOnClickListener(v->{startLanScan();startBleScan();});quick.addView(all);quick.addView(gap(8));
        TextView map=button("Voir la carte réseau",false);map.setOnClickListener(v->{tab=1;render();});quick.addView(map);quick.addView(gap(8));
        TextView report=button("Exporter un rapport",false);report.setOnClickListener(v->exportReport());quick.addView(report);p.addView(quick);p.addView(gap(11));

        LinearLayout state=card();state.addView(title("Résumé"));state.addView(gap(6));state.addView(row("Profil actif",profile()));state.addView(row("Appareils inconnus",String.valueOf(unknown)));state.addView(row("Bluetooth proches",String.valueOf(bleFound.size())));state.addView(row("Audit Android",auditIssues()==0?"Aucun point critique":auditIssues()+" point(s) à vérifier"));p.addView(state);p.addView(gap(11));
        LinearLayout recent=card();recent.addView(title("Derniers événements"));recent.addView(gap(7));recent.addView(tv(historyLines(5),13,muted,false));p.addView(recent);return scroll(p);
    }

    private View mapPage() {
        LinearLayout p=page(); NetworkMapView map=new NetworkMapView(this); map.setBackground(round(surface,24)); p.addView(map,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(330)));p.addView(gap(10));
        TextView scan=button(lanScanning?"Actualisation…":"Actualiser la carte",true);scan.setOnClickListener(v->startLanScan());p.addView(scan);p.addView(gap(10));
        List<String> ips=new ArrayList<>(known());ips.sort(Comparator.comparingInt(this::last));
        if(ips.isEmpty()){LinearLayout c=card();c.addView(title("Carte vide"));c.addView(gap(7));c.addView(tv("Lance une analyse pour découvrir les appareils du réseau.",14,muted,false));p.addView(c);} else for(String ip:ips){LinearLayout c=deviceCard(ip);p.addView(c);p.addView(gap(8));}
        return scroll(p);
    }

    private LinearLayout deviceCard(String ip){boolean on=isOnline(ip),trust=prefs.getBoolean(pk("trusted_"+ip),false);String name=prefs.getString(pk("name_"+ip),"Appareil "+last(ip));LinearLayout c=card();LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(tv(name,16,text,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView b=tv(on?"EN LIGNE":"HORS LIGNE",10,on?good:muted,true);b.setPadding(dp(8),dp(5),dp(8),dp(5));b.setBackground(round(surface2,12));top.addView(b);c.addView(top);c.addView(gap(5));c.addView(row("Adresse",mask(ip)));c.addView(row("Confiance",trust?"Approuvé":"À vérifier"));if(prefs.getBoolean("show_latency",true))c.addView(row("Latence",prefs.getLong(pk("latency_"+ip),-1)+" ms"));c.setOnClickListener(v->deviceDialog(ip));return c;}

    private View analysis(){LinearLayout p=page();
        LinearLayout audit=card();audit.addView(title("Audit Android"));audit.addView(gap(7));audit.addView(row("Verrouillage écran",isSecure()?"Actif":"Absent"));audit.addView(row("Options développeur",developer()?"Actives":"Désactivées"));audit.addView(row("Débogage USB",adb()?"Actif":"Désactivé"));audit.addView(row("Correctifs sécurité",Build.VERSION.SECURITY_PATCH));TextView refresh=button("Actualiser l’audit",false);refresh.setOnClickListener(v->{log("Audit Android actualisé.");render();});audit.addView(gap(8));audit.addView(refresh);p.addView(audit);p.addView(gap(10));

        LinearLayout lan=card();lan.addView(title("Analyse LAN"));lan.addView(gap(6));lan.addView(tv("Découverte locale /24, inventaire et comparaison avec le scan précédent.",13,muted,false));TextView scan=button(lanScanning?"Analyse en cours…":"Lancer l’analyse LAN",true);scan.setOnClickListener(v->startLanScan());lan.addView(gap(8));lan.addView(scan);p.addView(lan);p.addView(gap(10));

        LinearLayout ble=card();ble.addView(title("Bluetooth Lab"));ble.addView(gap(6));ble.addView(tv(bleScanning?"Recherche en cours…":bleFound.size()+" appareil(s) détecté(s)",13,muted,false));TextView bs=button(bleScanning?"Recherche en cours…":"Scanner les appareils BLE",false);bs.setOnClickListener(v->startBleScan());ble.addView(gap(8));ble.addView(bs);synchronized(bleFound){for(BleDevice d:bleFound.values()){ble.addView(gap(7));ble.addView(row(d.name,d.rssi+" dBm · "+d.distance));}}p.addView(ble);p.addView(gap(10));

        LinearLayout ports=card();ports.addView(title("Inspecteur de ports"));ports.addView(gap(6));ports.addView(tv("Inspection limitée aux adresses IPv4 privées et aux ports courants.",13,muted,false));EditText target=new EditText(this);target.setHint("192.168.1.10");target.setTextColor(text);target.setHintTextColor(muted);target.setSingleLine(true);ports.addView(target);TextView ps=button("Inspecter les ports autorisés",false);ps.setOnClickListener(v->scanPorts(target.getText().toString().trim()));ports.addView(gap(7));ports.addView(ps);p.addView(ports);return scroll(p);}

    private View activity(){LinearLayout p=page();LinearLayout c=card();c.addView(title("Chronologie"));c.addView(gap(8));String h=prefs.getString(pk("history"),"");if(h==null||h.trim().isEmpty())c.addView(tv("Aucun événement enregistré.",14,muted,false));else{String[] lines=h.split("\\n");for(String line:lines){if(line.trim().isEmpty())continue;LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(0,dp(7),0,dp(7));int cut=line.indexOf("] ");item.addView(tv(cut>0?line.substring(1,cut):"Événement",11,accent,true));item.addView(tv(cut>0?line.substring(cut+2):line,14,text,false));c.addView(item);}}p.addView(c);p.addView(gap(10));TextView clear=button("Effacer la chronologie",false);clear.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Effacer la chronologie ?").setPositiveButton("Effacer",(d,w)->{prefs.edit().remove(pk("history")).apply();render();}).setNegativeButton("Annuler",null).show());p.addView(clear);return scroll(p);}

    private View settings(){LinearLayout p=page();LinearLayout prof=card();prof.addView(title("Profils"));prof.addView(gap(7));prof.addView(row("Profil actif",profile()));TextView choose=button("Changer de profil",false);choose.setOnClickListener(v->chooseProfile());prof.addView(gap(8));prof.addView(choose);p.addView(prof);p.addView(gap(10));
        LinearLayout appearance=card();appearance.addView(title("Apparence"));appearance.addView(gap(6));appearance.addView(choice("Thème",themeLabel(),this::chooseTheme));appearance.addView(choice("Accent",accentLabel(),this::chooseAccent));appearance.addView(toggle("Mode compact","compact"));appearance.addView(toggle("Masquer les adresses","privacy_mode"));appearance.addView(toggle("Animations","animation"));p.addView(appearance);p.addView(gap(10));
        LinearLayout scans=card();scans.addView(title("Analyses"));scans.addView(gap(6));scans.addView(toggle("Scanner au démarrage","auto_scan_start"));scans.addView(toggle("Afficher la latence","show_latency"));scans.addView(choice("Délai LAN",prefs.getInt("scan_timeout",500)+" ms",this::chooseTimeout));p.addView(scans);p.addView(gap(10));
        LinearLayout alerts=card();alerts.addView(title("Alertes et données"));alerts.addView(gap(6));alerts.addView(toggle("Notifier les nouveaux appareils","notify_new"));alerts.addView(toggle("Conserver l’historique","keep_history"));TextView perm=button("Autoriser les notifications",false);perm.setOnClickListener(v->requestNotifications());alerts.addView(gap(8));alerts.addView(perm);TextView exp=button("Exporter le rapport du profil",false);exp.setOnClickListener(v->exportReport());alerts.addView(gap(8));alerts.addView(exp);p.addView(alerts);p.addView(gap(10));
        LinearLayout app=card();app.addView(title("Application"));app.addView(gap(6));app.addView(row("Version","2.0.0"));app.addView(row("Architecture","Interface unifiée"));app.addView(row("Cloud","Aucun"));app.addView(row("Télémétrie","Désactivée"));p.addView(app);return scroll(p);}

    private void startLanScan(){if(lanScanning)return;String local=localIp();if(local==null){toast("Connecte-toi à un réseau local IPv4.");return;}String[] a=local.split("\\.");String prefix=a[0]+"."+a[1]+"."+a[2]+".";Set<String> before=new HashSet<>(lastScan());online.clear();lanScanning=true;log("Analyse LAN lancée sur "+prefix+"0/24.");render();AtomicInteger left=new AtomicInteger(254);int timeout=prefs.getInt("scan_timeout",500);for(int i=1;i<=254;i++){final String host=prefix+i;pool.submit(()->{long s=System.nanoTime();boolean ok=false;try{ok=InetAddress.getByName(host).isReachable(timeout);}catch(Exception ignored){}long latency=(System.nanoTime()-s)/1_000_000;if(ok)online.add(new Device(host,latency));if(left.decrementAndGet()==0)ui.post(()->finishLan(before));});}}

    private void finishLan(Set<String> before){Set<String> now=new HashSet<>();for(Device d:online){now.add(d.ip);registerDevice(d);}Set<String> added=new HashSet<>(now);added.removeAll(before);Set<String> gone=new HashSet<>(before);gone.removeAll(now);prefs.edit().putStringSet(pk("last_scan"),now).apply();for(String ip:added)log("Nouvel appareil sur ce scan : "+ip+".");for(String ip:gone)log("Appareil absent depuis le dernier scan : "+ip+".");lanScanning=false;log("Analyse LAN terminée : "+now.size()+" en ligne, "+added.size()+" nouveau(x), "+gone.size()+" disparu(s).");render();}

    private void registerDevice(Device d){Set<String> set=known();boolean first=set.add(d.ip);SharedPreferences.Editor e=prefs.edit().putStringSet(pk("known"),set).putLong(pk("latency_"+d.ip),d.latency).putString(pk("last_"+d.ip),now());if(first){e.putString(pk("first_"+d.ip),now()).putString(pk("name_"+d.ip),d.ip.equals(localIp())?"Ce téléphone":"Appareil "+last(d.ip));log("Premier contact : "+d.ip+".");if(prefs.getBoolean("notify_new",true))notifyNew(d.ip);}e.apply();}

    private void startBleScan(){if(bleScanning)return;if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQ_BLE);return;}BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);BluetoothAdapter ad=bm==null?null:bm.getAdapter();if(ad==null||!ad.isEnabled()){toast("Active le Bluetooth.");return;}bleScanner=ad.getBluetoothLeScanner();if(bleScanner==null){toast("Scanner Bluetooth indisponible.");return;}bleFound.clear();bleScanning=true;log("Analyse Bluetooth démarrée.");bleScanner.startScan(bleCallback);ui.postDelayed(this::stopBleScan,12000);render();}

    private final ScanCallback bleCallback=new ScanCallback(){@Override public void onScanResult(int type,ScanResult r){String addr=r.getDevice().getAddress();String name="Appareil BLE";try{if(r.getDevice().getName()!=null)name=r.getDevice().getName();}catch(SecurityException ignored){}int rssi=r.getRssi();String dist=rssi>-55?"Très proche":rssi>-70?"Proche":rssi>-85?"Moyen":"Éloigné";bleFound.put(addr,new BleDevice(name,addr,rssi,dist));ui.post(()->{if(tab==2)render();});}};

    private void stopBleScan(){if(!bleScanning)return;bleScanning=false;try{if(bleScanner!=null)bleScanner.stopScan(bleCallback);}catch(SecurityException ignored){}log("Analyse Bluetooth terminée : "+bleFound.size()+" appareil(s).");render();}

    private void scanPorts(String ip){if(!privateIp(ip)){toast("Adresse IPv4 privée requise.");return;}int[] ports={21,22,23,25,53,80,110,139,143,443,445,554,631,1883,3389,8080,8443};toast("Inspection lancée…");pool.submit(()->{List<Integer> open=new ArrayList<>();for(int port:ports){try(Socket s=new Socket()){s.connect(new java.net.InetSocketAddress(ip,port),260);open.add(port);}catch(Exception ignored){}}ui.post(()->{log("Inspection de "+ip+" : "+open.size()+" port(s) ouvert(s).");new AlertDialog.Builder(this).setTitle("Résultat · "+ip).setMessage(open.isEmpty()?"Aucun port courant détecté.":"Ports ouverts : "+open.toString()).setPositiveButton("Fermer",null).show();});});}

    private void deviceDialog(String ip){EditText name=new EditText(this);name.setText(prefs.getString(pk("name_"+ip),"Appareil "+last(ip)));boolean trust=prefs.getBoolean(pk("trusted_"+ip),false);new AlertDialog.Builder(this).setTitle(mask(ip)).setView(name).setMessage("Première détection : "+prefs.getString(pk("first_"+ip),"—")+"\nDernière détection : "+prefs.getString(pk("last_"+ip),"—")).setPositiveButton("Enregistrer",(d,w)->{String n=name.getText().toString().trim();if(!n.isEmpty())prefs.edit().putString(pk("name_"+ip),n).apply();render();}).setNeutralButton(trust?"Retirer confiance":"Approuver",(d,w)->{prefs.edit().putBoolean(pk("trusted_"+ip),!trust).apply();log((trust?"Confiance retirée : ":"Appareil approuvé : ")+ip+".");render();}).setNegativeButton("Fermer",null).show();}

    private void chooseProfile(){String[] values={"Maison","Bureau","Lycée","Vacances","Personnalisé"};new AlertDialog.Builder(this).setTitle("Profil réseau").setItems(values,(d,w)->{prefs.edit().putString("profile",values[w]).apply();online.clear();bleFound.clear();log("Profil activé : "+values[w]+".");rebuild();}).show();}
    private void chooseTheme(){String[] v={"Sombre","Clair doux","AMOLED"};new AlertDialog.Builder(this).setTitle("Thème").setItems(v,(d,w)->{prefs.edit().putString("theme",w==1?"light":w==2?"amoled":"dark").apply();rebuild();}).show();}
    private void chooseAccent(){String[] v={"Sauge","Bleu","Violet","Ambre"};new AlertDialog.Builder(this).setTitle("Accent").setItems(v,(d,w)->{prefs.edit().putString("accent",w==1?"blue":w==2?"violet":w==3?"amber":"sage").apply();rebuild();}).show();}
    private void chooseTimeout(){String[] v={"Rapide · 300 ms","Équilibré · 500 ms","Précis · 800 ms"};new AlertDialog.Builder(this).setTitle("Délai LAN").setItems(v,(d,w)->{prefs.edit().putInt("scan_timeout",w==0?300:w==2?800:500).apply();render();}).show();}

    private int auditIssues(){int n=0;if(!isSecure())n++;if(developer())n++;if(adb())n++;return n;}
    private boolean isSecure(){KeyguardManager k=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);return k!=null&&k.isDeviceSecure();}
    private boolean developer(){try{return Settings.Global.getInt(getContentResolver(),Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,0)==1;}catch(Exception e){return false;}}
    private boolean adb(){try{return Settings.Global.getInt(getContentResolver(),Settings.Global.ADB_ENABLED,0)==1;}catch(Exception e){return false;}}

    private void channel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Alertes Sentry",NotificationManager.IMPORTANCE_DEFAULT);c.setDescription("Changements détectés sur le réseau local");NotificationManager n=getSystemService(NotificationManager.class);if(n!=null)n.createNotificationChannel(c);}}
    private void notifyNew(String ip){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(n==null)return;Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Nouvel appareil").setContentText(mask(ip)+" détecté dans le profil "+profile()).setAutoCancel(true);n.notify(Math.abs((profile()+ip).hashCode()),b.build());}
    private void requestNotifications(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF);else toast("Notifications disponibles.");}

    private void exportReport(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"Sentry-"+profile()+"-rapport.txt");startActivityForResult(i,REQ_EXPORT);}
    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==REQ_EXPORT&&c==RESULT_OK&&data!=null&&data.getData()!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(report().getBytes(java.nio.charset.StandardCharsets.UTF_8));toast("Rapport exporté.");log("Rapport exporté.");}catch(Exception e){toast("Export impossible.");}}}
    private String report(){StringBuilder s=new StringBuilder();s.append("SENTRY 2.0 · RAPPORT LOCAL\nProfil: ").append(profile()).append("\nDate: ").append(now()).append("\n\nAPPAREILS\n");for(String ip:known())s.append(prefs.getString(pk("name_"+ip),ip)).append(" · ").append(ip).append(" · ").append(prefs.getBoolean(pk("trusted_"+ip),false)?"confiance":"à vérifier").append("\n");s.append("\nAUDIT\nVerrouillage: ").append(isSecure()).append("\nOptions développeur: ").append(developer()).append("\nADB: ").append(adb()).append("\nCorrectif: ").append(Build.VERSION.SECURITY_PATCH).append("\n\nHISTORIQUE\n").append(prefs.getString(pk("history"),"Aucun"));return s.toString();}

    private NetworkInfo netInfo(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm==null)return new NetworkInfo("—","—");Network n=cm.getActiveNetwork();LinkProperties lp=n==null?null:cm.getLinkProperties(n);NetworkCapabilities nc=n==null?null:cm.getNetworkCapabilities(n);String trans=nc==null?"Déconnecté":nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"Mobile":nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"VPN":"Autre";List<String> dns=new ArrayList<>();if(lp!=null)for(InetAddress a:lp.getDnsServers())dns.add(a.getHostAddress());return new NetworkInfo(trans,dns.isEmpty()?"—":String.join(", ",dns));}
    private String localIp(){try{Enumeration<NetworkInterface> e=NetworkInterface.getNetworkInterfaces();while(e!=null&&e.hasMoreElements()){NetworkInterface ni=e.nextElement();if(!ni.isUp()||ni.isLoopback())continue;Enumeration<InetAddress> a=ni.getInetAddresses();while(a.hasMoreElements()){InetAddress x=a.nextElement();if(x instanceof Inet4Address&&x.isSiteLocalAddress())return x.getHostAddress();}}}catch(Exception ignored){}return null;}
    private boolean privateIp(String ip){try{InetAddress a=InetAddress.getByName(ip);return a instanceof Inet4Address&&a.isSiteLocalAddress();}catch(Exception e){return false;}}
    private Set<String> known(){Set<String>s=prefs.getStringSet(pk("known"),new HashSet<>());return s==null?new HashSet<>():new HashSet<>(s);}
    private Set<String> lastScan(){Set<String>s=prefs.getStringSet(pk("last_scan"),new HashSet<>());return s==null?new HashSet<>():new HashSet<>(s);}
    private boolean isOnline(String ip){synchronized(online){for(Device d:online)if(d.ip.equals(ip))return true;}return false;}
    private int last(String ip){try{return Integer.parseInt(ip.substring(ip.lastIndexOf('.')+1));}catch(Exception e){return 0;}}
    private String mask(String ip){if(!prefs.getBoolean("privacy_mode",false))return ip;int i=ip.lastIndexOf('.');return i>0?ip.substring(0,i)+".•••":"•••";}
    private String now(){return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.FRANCE).format(new Date());}
    private void log(String event){if(!prefs.getBoolean("keep_history",true))return;String old=prefs.getString(pk("history"),"");String line="["+new SimpleDateFormat("dd/MM HH:mm:ss",Locale.FRANCE).format(new Date())+"] "+event+"\n";String all=line+(old==null?"":old);if(all.length()>26000)all=all.substring(0,26000);prefs.edit().putString(pk("history"),all).apply();}
    private String historyLines(int count){String h=prefs.getString(pk("history"),"");if(h==null||h.trim().isEmpty())return "Aucune activité enregistrée.";String[] l=h.split("\\n");StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(count,l.length);i++)b.append(l[i]).append("\n");return b.toString().trim();}

    private LinearLayout page(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);int pad=prefs.getBoolean("compact",false)?11:17;p.setPadding(dp(pad),dp(5),dp(pad),dp(24));return p;}
    private ScrollView scroll(View v){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(v,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return s;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);int p=prefs.getBoolean("compact",false)?13:17;c.setPadding(dp(p),dp(p),dp(p),dp(p));c.setBackground(round(surface,23));c.setElevation(dp(2));return c;}
    private TextView title(String s){return tv(s,17,text,true);}
    private View row(String a,String b){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.TOP);r.setPadding(0,dp(6),0,dp(6));TextView l=tv(a,13,muted,false),rr=tv(b,13,text,true);rr.setGravity(Gravity.END);r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,.44f));r.addView(rr,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,.56f));return r;}
    private View toggle(String label,String key){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(9),0,dp(9));r.addView(tv(label,14,text,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));boolean on=prefs.getBoolean(key,false);TextView state=tv(on?"ACTIF":"INACTIF",10,on?good:muted,true);state.setPadding(dp(8),dp(5),dp(8),dp(5));state.setBackground(round(surface2,12));r.addView(state);r.setOnClickListener(v->{prefs.edit().putBoolean(key,!prefs.getBoolean(key,false)).apply();if("privacy_mode".equals(key)||"compact".equals(key))rebuild();else render();});return r;}
    private View choice(String label,String value,Runnable run){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(9),0,dp(9));r.addView(tv(label,14,text,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));r.addView(tv(value,13,accent,true));r.setOnClickListener(v->run.run());return r;}
    private TextView button(String s,boolean primary){TextView b=tv(s,15,primary?Color.rgb(15,23,19):text,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(14),dp(12),dp(14));b.setBackground(round(primary?accent:surface2,17));return b;}
    private View progress(int value){FrameLayout f=new FrameLayout(this);f.setBackground(round(surface2,7));View bar=new View(this);bar.setBackground(round(value>80?good:value>60?warn:bad,7));f.addView(bar,new FrameLayout.LayoutParams(0,dp(8)));f.post(()->{FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)bar.getLayoutParams();lp.width=(int)(f.getWidth()*value/100f);bar.setLayoutParams(lp);});return f;}
    private TextView tv(String s,float size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(0,1.12f);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    private GradientDrawable round(int color,float r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    private View gap(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private View gapW(int w){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1));return v;}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private String themeLabel(){String t=prefs.getString("theme","dark");return "light".equals(t)?"Clair doux":"amoled".equals(t)?"AMOLED":"Sombre";}
    private String accentLabel(){String a=prefs.getString("accent","sage");return "blue".equals(a)?"Bleu":"violet".equals(a)?"Violet":"amber".equals(a)?"Ambre":"Sauge";}

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_BLE&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startBleScan();}
    @Override protected void onDestroy(){try{if(bleScanning&&bleScanner!=null)bleScanner.stopScan(bleCallback);}catch(Exception ignored){}pool.shutdownNow();super.onDestroy();}

    private class NetworkMapView extends View {
        Paint line=new Paint(1),node=new Paint(1),label=new Paint(1);
        NetworkMapView(Context c){super(c);line.setStrokeWidth(dp(2));line.setColor(surface2);node.setColor(accent);label.setColor(text);label.setTextSize(dp(11));label.setTextAlign(Paint.Align.CENTER);setPadding(dp(10),dp(10),dp(10),dp(10));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight();float cx=w/2,cy=h/2;node.setColor(accent);c.drawCircle(cx,cy,dp(30),node);label.setTypeface(Typeface.DEFAULT_BOLD);c.drawText("Routeur",cx,cy+dp(4),label);List<String> ips=new ArrayList<>(known());if(ips.isEmpty()){label.setTypeface(Typeface.DEFAULT);c.drawText("Aucun appareil",cx,h-dp(28),label);return;}int max=Math.min(10,ips.size());float radius=Math.min(w,h)*.34f;for(int i=0;i<max;i++){double a=-Math.PI/2+2*Math.PI*i/max;float x=cx+(float)Math.cos(a)*radius,y=cy+(float)Math.sin(a)*radius;c.drawLine(cx,cy,x,y,line);String ip=ips.get(i);node.setColor(isOnline(ip)?good:prefs.getBoolean(pk("trusted_"+ip),false)?accent:muted);c.drawCircle(x,y,dp(20),node);label.setTypeface(Typeface.DEFAULT_BOLD);String name=prefs.getString(pk("name_"+ip),"Appareil "+last(ip));if(name.length()>11)name=name.substring(0,10)+"…";c.drawText(name,x,y+dp(35),label);}}}

    private static class Device{final String ip;final long latency;Device(String i,long l){ip=i;latency=l;}}
    private static class BleDevice{final String name,address,distance;final int rssi;BleDevice(String n,String a,int r,String d){name=n;address=a;rssi=r;distance=d;}}
    private static class NetworkInfo{final String transport,dns;NetworkInfo(String t,String d){transport=t;dns=d;}}
}
