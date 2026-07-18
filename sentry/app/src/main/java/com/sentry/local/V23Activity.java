package com.sentry.local;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Sentry v23: radar principal, assistant d'autorisations et mode Forteresse. */
public class V23Activity extends V222Activity {
    private static final int BG=Color.rgb(2,8,12), PANEL=Color.rgb(10,26,32), TEXT=Color.rgb(239,253,255), MUTED=Color.rgb(126,163,172);
    private static final int CYAN=Color.rgb(61,235,211), BLUE=Color.rgb(79,166,255), GOOD=Color.rgb(83,228,144), WARN=Color.rgb(247,190,80), BAD=Color.rgb(245,98,124), VIOLET=Color.rgb(181,132,247);
    private static final int REQ_PERMS=2301, REQ_VPN=2302;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private boolean pendingFortress;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("sentry_v23",MODE_PRIVATE);
        if(!prefs.contains("secure_screen"))prefs.edit().putBoolean("secure_screen",true).apply();
        secureScreen();
        ui.postDelayed(this::showDashboard,850);
        ui.postDelayed(this::installOrb,1050);
    }
    @Override protected void onResume(){super.onResume();secureScreen();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);super.onDestroy();}

    private void installOrb(){
        TextView v=text("V23\nFORT",10,Color.rgb(2,22,24),true);v.setGravity(Gravity.CENTER);v.setBackground(round(prefs.getBoolean("enabled",false)?GOOD:CYAN,24));v.setElevation(dp(16));v.setOnClickListener(x->showDashboard());
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(66),dp(66),Gravity.END|Gravity.BOTTOM);lp.setMargins(0,0,dp(14),dp(76));addContentView(v,lp);
    }

    private void showDashboard(){
        FrameLayout content=field(V4Activity.class,"content",FrameLayout.class);TextView title=field(V4Activity.class,"title",TextView.class);TextView sub=field(V4Activity.class,"subtitle",TextView.class);if(content==null)return;
        if(title!=null)title.setText("SENTRY FORTRESS");if(sub!=null)sub.setText("Radar visible · Protection maximale · Autorisations guidées");content.removeAllViews();invoke(V4Activity.class,"startSpatialSweep");
        ScrollView scroll=new ScrollView(this);LinearLayout root=column();root.setPadding(dp(14),dp(8),dp(14),dp(32));root.addView(hero());root.addView(space(10));root.addView(status());root.addView(space(10));
        RadarView radar=new RadarView(false);radar.setOnClickListener(v->fullRadar());root.addView(radar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(390)));root.addView(space(8));TextView open=action("OUVRIR LE RADAR PLEIN ECRAN",CYAN,Color.rgb(2,24,22));open.setOnClickListener(v->fullRadar());root.addView(open);
        section(root,"PROTECTION MAXIMALE");
        feature(root,prefs.getBoolean("enabled",false)?"MODE FORTERESSE ACTIF":"ACTIVER LE MODE FORTERESSE",prefs.getBoolean("enabled",false)?"Gardien permanent, écran protégé et contrôles continus":"Configurer permissions, gardien et confinement réseau",prefs.getBoolean("enabled",false)?GOOD:BAD,this::toggleFortress);
        feature(root,"AUTORISATIONS ET REGLAGES","Assistant complet avec uniquement les accès réellement utilisés",VIOLET,this::permissionWizard);
        feature(root,"AUDIT DE SECURITE V23","Score détaillé et recommandations Android",WARN,this::audit);
        feature(root,"OPTIMISATION AUTOMATIQUE","Adapter les scans à la batterie sans couper la protection",BLUE,this::optimise);
        section(root,"RADAR ET DETECTION");
        feature(root,"HYPERTRACK BLUETOOTH","Flèche 2D, balayage 360° et distance estimée",CYAN,()->invoke(V21Activity.class,"showPicker"));
        feature(root,"SURVEILLANCE BLUETOOTH","Anomalies, signaux très proches et environnement dense",BLUE,()->invoke(V20Activity.class,"showBluetoothWatch"));
        feature(root,"REALITY LAB","Magnétique, vibrations, sonar, optique et carte physique",VIOLET,()->invoke(V22Activity.class,"showRealityHub"));
        feature(root,"CENTRE DE COMMANDEMENT","VPN d'urgence, incidents, Wi-Fi, applications et coffre",GOOD,()->invoke(V20Activity.class,"showCommandCenter"));
        root.addView(space(14));root.addView(text("Le rayon du radar représente la puissance reçue. L'angle est une disposition visuelle stable, pas une direction radio réelle sans matériel UWB/directionnel participant.",11,MUTED,false));scroll.addView(root);content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
    }

    private View hero(){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(round(PANEL,23));TextView e=text("SENTRY V23",11,CYAN,true);e.setLetterSpacing(.16f);c.addView(e);c.addView(space(5));c.addView(text("Fortress & Radar",30,TEXT,true));c.addView(space(5));c.addView(text("Le radar devient l'élément principal. Le gardien défensif peut rester actif écran éteint lorsque Android l'autorise.",13,MUTED,false));return c;}
    private View status(){int s=score();LinearLayout r=new LinearLayout(this);r.addView(metric("SECURITE",s+"/100",s>=85?GOOD:s>=60?WARN:BAD),weight());r.addView(spaceW(7));r.addView(metric("GARDIEN",SentryGuardianService.isRunning()?"ACTIF":"ARRET",SentryGuardianService.isRunning()?GOOD:WARN),weight());r.addView(spaceW(7));r.addView(metric("VPN",EmergencyVpnService.isRunning()?"BLOQUE":"PRET",EmergencyVpnService.isRunning()?BAD:CYAN),weight());return r;}

    private void fullRadar(){
        invoke(V4Activity.class,"startSpatialSweep");Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);FrameLayout f=new FrameLayout(this);f.addView(new RadarView(true),new FrameLayout.LayoutParams(-1,-1));LinearLayout b=new LinearLayout(this);TextView scan=action("RE-SCANNER",BLUE,Color.rgb(2,18,28)),track=action("HYPERTRACK",VIOLET,Color.WHITE),close=action("FERMER",CYAN,Color.rgb(2,24,22));b.addView(scan,weight());b.addView(spaceW(6));b.addView(track,weight());b.addView(spaceW(6));b.addView(close,weight());FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);lp.setMargins(dp(12),0,dp(12),dp(18));f.addView(b,lp);scan.setOnClickListener(v->invoke(V4Activity.class,"startSpatialSweep"));track.setOnClickListener(v->{d.dismiss();invoke(V21Activity.class,"showPicker");});close.setOnClickListener(v->d.dismiss());d.setContentView(f);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(round(BG,0));w.setLayout(-1,-1);}
    }

    private final class RadarView extends View{
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean full;final long born=System.currentTimeMillis();RadarView(boolean f){super(V23Activity.this);full=f;setBackground(round(PANEL,22));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=full?h*.48f:h*.53f,r=Math.min(w*.44f,h*(full?.36f:.38f));p.setStyle(Paint.Style.FILL);p.setColor(PANEL);c.drawRoundRect(0,0,w,h,dp(22),dp(22),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));for(int i=1;i<=5;i++){p.setColor(Color.argb(72,61,235,211));c.drawCircle(cx,cy,r*i/5f,p);}for(int i=0;i<12;i++){double a=Math.toRadians(i*30-90);c.drawLine(cx,cy,cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r,p);}float sweep=((System.currentTimeMillis()-born)%3200)/3200f*360f;p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(48,61,235,211));Path cone=new Path();cone.moveTo(cx,cy);for(int i=0;i<=18;i++){double a=Math.toRadians(sweep-25+i*50.0/18.0-90);cone.lineTo(cx+(float)Math.cos(a)*r,cy+(float)Math.sin(a)*r);}cone.close();c.drawPath(cone,p);List<Point> list=points();int n=Math.min(full?28:18,list.size());for(int i=0;i<n;i++){Point d=list.get(i);float strength=clamp((d.rssi+100f)/58f,.04f,1f),rr=r*(1f-strength*.78f);double a=Math.toRadians(Math.floorMod(d.id.hashCode(),360)-90);float x=cx+(float)Math.cos(a)*rr,y=cy+(float)Math.sin(a)*rr;int col=d.rssi>=-55?GOOD:d.rssi>=-72?WARN:BLUE;p.setColor(Color.argb(65,Color.red(col),Color.green(col),Color.blue(col)));c.drawCircle(x,y,dp(12),p);p.setColor(col);c.drawCircle(x,y,dp(5),p);if(full||n<=10){p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(full?9:8));p.setColor(TEXT);c.drawText(shortName(d.name,full?15:10),x,y+dp(18),p);}}p.setColor(CYAN);c.drawCircle(cx,cy,dp(11),p);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(full?24:19));p.setColor(TEXT);c.drawText("RADAR BLUETOOTH",cx,dp(full?48:34),p);p.setTextSize(dp(11));p.setColor(MUTED);c.drawText(n+" appareil(s) affiché(s) · rayon = puissance",cx,dp(full?70:53),p);c.drawText("Angle visuel, pas un relèvement radio",cx,h-dp(full?76:18),p);postInvalidateDelayed(40);}
    }

    private void toggleFortress(){if(prefs.getBoolean("enabled",false)){new AlertDialog.Builder(this).setTitle("Désactiver Forteresse ?").setMessage("Le gardien et le confinement seront arrêtés.").setPositiveButton("Désactiver",(d,w)->disableFortress()).setNegativeButton("Annuler",null).show();return;}pendingFortress=true;if(missing().isEmpty())activateFortress();else permissionWizard();}
    private void activateFortress(){prefs.edit().putBoolean("enabled",true).putBoolean("secure_screen",true).apply();secureScreen();startGuardian();pendingFortress=false;Intent i=VpnService.prepare(this);if(i!=null)startActivityForResult(i,REQ_VPN);else startVpn();toast("Mode Forteresse activé");ui.postDelayed(this::specialSettings,500);}
    private void disableFortress(){prefs.edit().putBoolean("enabled",false).apply();stopService(new Intent(this,SentryGuardianService.class).setAction(SentryGuardianService.ACTION_STOP));stopService(new Intent(this,EmergencyVpnService.class).setAction(EmergencyVpnService.ACTION_STOP));toast("Mode Forteresse désactivé");showDashboard();}
    private void startGuardian(){Intent i=new Intent(this,SentryGuardianService.class).setAction(SentryGuardianService.ACTION_START);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    private void startVpn(){Intent i=new Intent(this,EmergencyVpnService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}

    private void permissionWizard(){List<String> m=missing();if(!m.isEmpty())requestPermissions(m.toArray(new String[0]),REQ_PERMS);else if(pendingFortress)activateFortress();else specialSettings();}
    private List<String> missing(){List<String> o=new ArrayList<>();add(o,Manifest.permission.CAMERA);add(o,Manifest.permission.RECORD_AUDIO);if(Build.VERSION.SDK_INT>=29)add(o,Manifest.permission.ACTIVITY_RECOGNITION);if(Build.VERSION.SDK_INT>=31){add(o,Manifest.permission.BLUETOOTH_SCAN);add(o,Manifest.permission.BLUETOOTH_CONNECT);}else add(o,Manifest.permission.ACCESS_FINE_LOCATION);if(Build.VERSION.SDK_INT>=33){add(o,Manifest.permission.NEARBY_WIFI_DEVICES);add(o,Manifest.permission.POST_NOTIFICATIONS);}return o;}
    private void add(List<String> l,String p){if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)l.add(p);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_PERMS){if(pendingFortress)activateFortress();else specialSettings();}}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==REQ_VPN&&c==Activity.RESULT_OK){startVpn();specialSettings();}}

    private void specialSettings(){String[] x={"Démarrer le gardien permanent","Autoriser l'activité sans restriction batterie","Configurer le VPN permanent / toujours actif","Ouvrir les notifications Sentry","Vérifier verrouillage et biométrie","Informations de l'application"};new AlertDialog.Builder(this).setTitle("Protection maximale Android").setMessage("Ces écrans système nécessitent ta validation. Sentry ne demande pas SMS, contacts ou accessibilité car il ne les utilise pas.").setItems(x,(d,w)->{if(w==0)startGuardian();else if(w==1)batterySettings();else if(w==2)open(Settings.ACTION_VPN_SETTINGS);else if(w==3)notificationSettings();else if(w==4)open(Settings.ACTION_SECURITY_SETTINGS);else appSettings();}).setPositiveButton("Terminé",(d,w)->showDashboard()).show();}
    private void audit(){int s=score();StringBuilder b=new StringBuilder("SCORE GLOBAL : "+s+" / 100\n\n");state(b,"Verrouillage sécurisé",deviceSecure());state(b,"Captures d'écran bloquées",prefs.getBoolean("secure_screen",true));state(b,"Gardien permanent",SentryGuardianService.isRunning());state(b,"Notifications autorisées",notifications());state(b,"Batterie sans restriction",batteryExempt());state(b,"Débogage USB désactivé",global(Settings.Global.ADB_ENABLED)==0);state(b,"Options développeur désactivées",global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)==0);state(b,"Permissions Sentry complètes",missing().isEmpty());state(b,"Autorisation VPN accordée",VpnService.prepare(this)==null);b.append("\nLe VPN de confinement bloque IPv4 et IPv6 sur ce téléphone. Aucun contenu chiffré n'est déchiffré.");new AlertDialog.Builder(this).setTitle("Audit V23").setMessage(b.toString()).setPositiveButton("Corriger",(d,w)->permissionWizard()).setNeutralButton(prefs.getBoolean("secure_screen",true)?"Autoriser captures":"Bloquer captures",(d,w)->toggleSecure()).setNegativeButton("Fermer",null).show();}
    private void optimise(){PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);boolean saver=p!=null&&p.isPowerSaveMode();SharedPreferences v14=getSharedPreferences("sentry_v14",MODE_PRIVATE),sp=getSharedPreferences("sentry_local",MODE_PRIVATE);v14.edit().putBoolean("smart_scan",true).putString("scan_mode",saver?"eco":"balanced").apply();sp.edit().putString("v4_quality",saver?"low":"high").putBoolean("v4_trails",!saver).putBoolean("v4_animation",!saver).apply();if(prefs.getBoolean("enabled",false))startGuardian();toast(saver?"Mode économie optimisé":"Mode équilibré optimisé");}

    private int score(){int s=12;if(deviceSecure())s+=16;if(prefs.getBoolean("secure_screen",true))s+=8;if(SentryGuardianService.isRunning())s+=14;if(notifications())s+=8;if(batteryExempt())s+=9;if(global(Settings.Global.ADB_ENABLED)==0)s+=10;if(global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)==0)s+=6;if(missing().isEmpty())s+=10;if(VpnService.prepare(this)==null)s+=7;return Math.min(100,s);}
    private boolean deviceSecure(){KeyguardManager k=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);return k!=null&&k.isDeviceSecure();}
    private boolean notifications(){NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);return Build.VERSION.SDK_INT<24||n==null||n.areNotificationsEnabled();}
    private boolean batteryExempt(){PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);return p!=null&&p.isIgnoringBatteryOptimizations(getPackageName());}
    private int global(String k){try{return Settings.Global.getInt(getContentResolver(),k,0);}catch(Exception e){return 0;}}
    private void state(StringBuilder b,String l,boolean ok){b.append(ok?"✓ ":"○ ").append(l).append('\n');}
    private void toggleSecure(){prefs.edit().putBoolean("secure_screen",!prefs.getBoolean("secure_screen",true)).apply();secureScreen();}
    private void secureScreen(){if(prefs!=null&&prefs.getBoolean("secure_screen",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private void batterySettings(){try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}catch(Exception e){open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);}}
    private void notificationSettings(){try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}catch(Exception e){appSettings();}}
    private void appSettings(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception e){toast("Réglage indisponible");}}

    private List<Point> points(){List<Point> o=new ArrayList<>();Object raw=value(V4Activity.class,"devices");if(!(raw instanceof Map))return o;for(Map.Entry<?,?> e:((Map<?,?>)raw).entrySet()){try{Object d=e.getValue();String id=String.valueOf(e.getKey()),name=String.valueOf(read(d,"name")),source=String.valueOf(read(d,"source"));Object rv=read(d,"rssi"),tv=read(d,"lastSeen");int r=rv instanceof Number?((Number)rv).intValue():-100;long t=tv instanceof Number?((Number)tv).longValue():0;if(System.currentTimeMillis()-t<600000&&(source.toLowerCase(Locale.ROOT).contains("bluetooth")||id.startsWith("ble:")))o.add(new Point(id,name,r,t));}catch(Exception ignored){}}o.sort(Comparator.comparingLong((Point p)->p.time).reversed().thenComparingInt(p->-p.rssi));return o;}
    private Object read(Object x,String n)throws Exception{Class<?> c=x.getClass();while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(x);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
    private Object value(Class<?> c,String n){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(this);}catch(Exception e){return null;}}
    private <T>T field(Class<?> c,String n,Class<T>t){Object v=value(c,n);return t.isInstance(v)?t.cast(v):null;}
    private void invoke(Class<?> c,String n){try{Method m=c.getDeclaredMethod(n);m.setAccessible(true);m.invoke(this);}catch(Exception e){toast("Fonction momentanément indisponible");}}
    private void open(String a){try{startActivity(new Intent(a));}catch(Exception e){toast("Écran système indisponible");}}
    private float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    private String shortName(String s,int n){if(s==null||"null".equals(s))return"Appareil";return s.length()<=n?s:s.substring(0,n-1)+"…";}

    private void section(LinearLayout r,String s){r.addView(space(18));TextView t=text(s,11,MUTED,true);t.setLetterSpacing(.13f);r.addView(t);r.addView(space(7));}
    private void feature(LinearLayout r,String a,String b,int c,Runnable x){LinearLayout v=column();v.setPadding(dp(15),dp(14),dp(15),dp(14));v.setBackground(round(PANEL,18));v.addView(text(a,14,c,true));TextView q=text(b,11.5f,MUTED,false);q.setPadding(0,dp(4),0,0);v.addView(q);v.setOnClickListener(z->x.run());r.addView(v);r.addView(space(8));}
    private TextView metric(String a,String b,int c){TextView v=text(a+"\n"+b,9.2f,c,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(4),dp(10),dp(4),dp(10));v.setBackground(round(PANEL,14));return v;}
    private TextView action(String a,int b,int c){TextView v=text(a,11.5f,c,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(8),dp(14),dp(8),dp(14));v.setBackground(round(b,15));return v;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private TextView text(String s,float z,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setLineSpacing(0,1.12f);if(b)v.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));return v;}
    private GradientDrawable round(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private View space(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private View spaceW(int w){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(dp(w),1));return v;}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private static final class Point{final String id,name;final int rssi;final long time;Point(String i,String n,int r,long t){id=i;name=n;rssi=r;time=t;}}
}
