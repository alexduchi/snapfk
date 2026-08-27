package com.sentry.local;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Sentry v25: clean full-screen home, animated themes and expandable dock. */
public class V25Activity extends Activity {
    private static final int REQ_VPN = 2501;
    private FrameLayout root;
    private LinearLayout content, dockItems;
    private TextView arrow;
    private boolean dockOpen;
    private SharedPreferences prefs;
    private Theme theme;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(2,8,12));
        prefs = getSharedPreferences("sentry_v25", MODE_PRIVATE);
        theme = Theme.from(prefs.getString("theme", "aurora"));
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean fortress = getSharedPreferences("sentry_v23", MODE_PRIVATE).getBoolean("enabled", false);
        if (!fortress && EmergencyVpnService.isRequested(this)) EmergencyVpnService.stop(this);
        renderHome();
    }

    private void build() {
        root = new AnimatedGradient(this);
        setContentView(root);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1,-1); clp.setMargins(0,0,0,dp(72));
        root.addView(scroll,clp);
        buildDock();
        renderHome();
    }

    private void buildDock() {
        LinearLayout dock = new LinearLayout(this); dock.setOrientation(LinearLayout.VERTICAL); dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(8),dp(7),dp(8),dp(7)); dock.setBackground(round(Color.argb(238,5,17,23),24)); dock.setElevation(dp(20));
        dockItems = new LinearLayout(this); dockItems.setOrientation(LinearLayout.HORIZONTAL); dockItems.setGravity(Gravity.CENTER); dockItems.setVisibility(View.GONE);
        dock.addView(dockItems,new LinearLayout.LayoutParams(-1,0));
        arrow = label("⌃  MENU",12,Color.WHITE,true); arrow.setGravity(Gravity.CENTER); arrow.setPadding(0,dp(10),0,dp(10));
        arrow.setOnClickListener(v->toggleDock()); dock.addView(arrow,new LinearLayout.LayoutParams(-1,dp(46)));
        addDock("⌂","Accueil",this::renderHome); addDock("◉","Radar",()->startActivity(new Intent(this,V23Activity.class)));
        addDock("▣","Scan 3D",this::renderScan); addDock("◆","Sécurité",this::renderSecurity); addDock("≋","Réseau",this::renderNetwork); addDock("⚙","Thèmes",this::renderThemes);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM); lp.setMargins(dp(10),0,dp(10),dp(10)); root.addView(dock,lp);
    }

    private void toggleDock() {
        dockOpen=!dockOpen; arrow.animate().rotation(dockOpen?180:0).setDuration(260).start();
        if(dockOpen){dockItems.setVisibility(View.VISIBLE); ValueAnimator a=ValueAnimator.ofInt(0,dp(66));a.setDuration(280);a.setInterpolator(new DecelerateInterpolator());a.addUpdateListener(x->{dockItems.getLayoutParams().height=(int)x.getAnimatedValue();dockItems.requestLayout();});a.start();arrow.setText("⌄  FERMER");}
        else{ValueAnimator a=ValueAnimator.ofInt(dockItems.getHeight(),0);a.setDuration(220);a.addUpdateListener(x->{dockItems.getLayoutParams().height=(int)x.getAnimatedValue();dockItems.requestLayout();});a.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator x){dockItems.setVisibility(View.GONE);}});a.start();arrow.setText("⌃  MENU");}
    }

    private void addDock(String icon,String name,Runnable action){LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);TextView i=label(icon,20,theme.a,true),n=label(name,9,Color.LTGRAY,true);item.addView(i);item.addView(n);item.setOnClickListener(v->{action.run();if(dockOpen)toggleDock();});dockItems.addView(item,new LinearLayout.LayoutParams(0,-1,1));}

    private void renderHome(){clear("SENTRY V25","Spatial intelligence");hero("Protection douce, scan automatique et visualisation 3D.","Une interface propre, animée et entièrement personnalisable.");
        card("SCAN 3D AUTOMATIQUE","Un seul bouton : marche lentement, Sentry détecte les surfaces et construit la pièce.",theme.a,()->startActivity(new Intent(this,AutoSpatialScanActivity.class)));
        card("DERNIER MODÈLE 3D","Explorer le dernier scan en rotation, zoom et vue du dessus.",theme.b,()->startActivity(new Intent(this,RoomModelActivity.class)));
        card("MODE MANUEL PRÉCIS","Conserver le scan par coins pour corriger une pièce compliquée.",theme.c,()->startActivity(new Intent(this,SpatialScanActivity.class)));
        card("FORTERESSE & RADAR","Ouvrir les outils de détection et de protection existants.",Color.rgb(245,98,124),()->startActivity(new Intent(this,V23Activity.class)));
    }

    private void renderScan(){clear("SCAN SPATIAL","Simple et efficace");hero("Choisis Auto pour une utilisation presque sans intervention.","Le mode manuel reste disponible uniquement pour les corrections.");
        card("DÉMARRER LE SCAN AUTO","Détection continue du sol, des murs et du plafond, puis création automatique du plan.",theme.a,()->startActivity(new Intent(this,AutoSpatialScanActivity.class)));
        card("EXPLORER LE MODÈLE","Ouvrir la pièce en 3D interactive.",theme.b,()->startActivity(new Intent(this,RoomModelActivity.class)));
        card("CORRIGER MANUELLEMENT","Mesurer des coins précis si l'automatique hésite.",theme.c,()->startActivity(new Intent(this,SpatialScanActivity.class)));
    }

    private void renderSecurity(){clear("SÉCURITÉ","Contrôles clairs");boolean active=EmergencyVpnService.isRunning()||EmergencyVpnService.isRequested(this);
        card(active?"FORCER L'ARRÊT DU VPN":"VPN DÉSACTIVÉ",active?"Ferme immédiatement le tunnel, supprime la demande et libère le réseau.":"Aucun confinement réseau actif.",active?Color.rgb(245,98,124):Color.rgb(83,228,144),()->{EmergencyVpnService.stop(this);toast("VPN arrêté");renderSecurity();});
        card("ACTIVER LE CONFINEMENT","Bloquer volontairement IPv4 et IPv6 sur ce téléphone.",Color.rgb(247,190,80),()->{Intent p=VpnService.prepare(this);if(p!=null)startActivityForResult(p,REQ_VPN);else EmergencyVpnService.start(this);});
        card("ASSISTANT DE CONFIGURATION","Ouvrir les pages Android nécessaires sans automatiser des clics sensibles.",theme.b,this::showSetup);
    }

    private void renderNetwork(){clear("RÉSEAU","Observation locale");card("CENTRE RÉSEAU","Wi-Fi, DNS, appareils et incidents.",theme.a,()->startActivity(new Intent(this,V20Activity.class)));card("RADAR BLUETOOTH","Puissance reçue et suivi visuel.",theme.b,()->startActivity(new Intent(this,V23Activity.class)));}

    private void renderThemes(){clear("THÈMES","Couleurs et ambiance");for(Theme t:Theme.ALL)card(t.name,"Dégradé dynamique "+t.name.toLowerCase()+".",t.a,()->{prefs.edit().putString("theme",t.id).apply();theme=t;recreate();});}

    private void showSetup(){String[] x={"Notifications","Batterie sans restriction","VPN Android","Sécurité de l'appareil","Accessibilité (optionnelle)","Informations application"};new AlertDialog.Builder(this).setTitle("Configuration guidée").setItems(x,(d,w)->{Intent i;if(w==0)i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());else if(w==1)i=new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);else if(w==2)i=new Intent(Settings.ACTION_VPN_SETTINGS);else if(w==3)i=new Intent(Settings.ACTION_SECURITY_SETTINGS);else if(w==4)i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);else i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()));startActivity(i);}).setNegativeButton("Fermer",null).show();}

    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==REQ_VPN&&c==RESULT_OK){EmergencyVpnService.start(this);toast("Confinement activé");}}

    private void clear(String title,String sub){content.removeAllViews();content.setPadding(dp(16),dp(34),dp(16),dp(30));TextView t=label(title,30,Color.WHITE,true),s=label(sub,12,theme.a,true);content.addView(t);content.addView(s);content.addView(space(16));}
    private void hero(String a,String b){LinearLayout c=box();c.addView(label(a,19,Color.WHITE,true));c.addView(space(7));c.addView(label(b,12,Color.LTGRAY,false));content.addView(c);content.addView(space(12));}
    private void card(String title,String sub,int color,Runnable action){LinearLayout c=box();c.setAlpha(0f);c.setTranslationY(dp(18));TextView t=label(title,15,color,true),s=label(sub,11.5f,Color.LTGRAY,false);c.addView(t);c.addView(space(5));c.addView(s);c.setOnClickListener(v->{v.animate().scaleX(.97f).scaleY(.97f).setDuration(80).withEndAction(()->{v.animate().scaleX(1).scaleY(1).setDuration(120).start();action.run();}).start();});content.addView(c);content.addView(space(9));c.animate().alpha(1).translationY(0).setDuration(320).setStartDelay(content.getChildCount()*18L).start();}
    private LinearLayout box(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(15),dp(16),dp(15));l.setBackground(round(Color.argb(220,8,24,31),20));return l;}
    private TextView label(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(0,1.12f);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable round(int color,float r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    private View space(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private final class AnimatedGradient extends FrameLayout{Paint p=new Paint();float phase;ValueAnimator a;AnimatedGradient(Activity c){super(c);a=ValueAnimator.ofFloat(0,1);a.setDuration(12000);a.setRepeatCount(ValueAnimator.INFINITE);a.addUpdateListener(v->{phase=(float)v.getAnimatedValue();invalidate();});a.start();setWillNotDraw(false);}@Override protected void onDraw(Canvas c){super.onDraw(c);float shift=(float)Math.sin(phase*Math.PI*2)*getWidth()*.18f;p.setShader(new LinearGradient(shift,0,getWidth()-shift,getHeight(),theme.bg1,theme.bg2,Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),p);}}

    private static final class Theme{final String id,name;final int a,b,c,bg1,bg2;Theme(String i,String n,int a,int b,int c,int x,int y){id=i;name=n;this.a=a;this.b=b;this.c=c;bg1=x;bg2=y;}static final Theme[] ALL={new Theme("aurora","Aurora",Color.rgb(61,235,211),Color.rgb(181,132,247),Color.rgb(255,113,181),Color.rgb(2,8,18),Color.rgb(29,8,43)),new Theme("ocean","Ocean",Color.rgb(57,210,255),Color.rgb(72,255,190),Color.rgb(91,134,255),Color.rgb(0,13,28),Color.rgb(0,45,56)),new Theme("fortress","Fortress",Color.rgb(255,93,115),Color.rgb(255,170,70),Color.rgb(255,220,95),Color.rgb(20,2,7),Color.rgb(48,8,5)),new Theme("amoled","AMOLED",Color.WHITE,Color.rgb(61,235,211),Color.rgb(130,160,255),Color.BLACK,Color.rgb(0,10,14))};static Theme from(String id){for(Theme t:ALL)if(t.id.equals(id))return t;return ALL[0];}}
}
