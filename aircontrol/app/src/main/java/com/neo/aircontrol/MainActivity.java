package com.neo.aircontrol;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private TextView state;
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(buildUi());requestPerms();}
    private View buildUi(){
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(28),dp(24),dp(28));root.setBackgroundColor(0xFFF5F6F8);scroll.addView(root);
        TextView logo=new TextView(this);logo.setText("AC");logo.setTextSize(28);logo.setTextColor(Color.WHITE);logo.setGravity(Gravity.CENTER);logo.setBackgroundColor(0xFF111318);root.addView(logo,new LinearLayout.LayoutParams(dp(72),dp(72)));
        TextView title=new TextView(this);title.setText("AirControl V4");title.setTextSize(30);title.setTextColor(0xFF111318);title.setPadding(0,dp(18),0,dp(6));root.addView(title);
        TextView sub=new TextView(this);sub.setText("Contrôle mains libres · traitement local\n\n1. Autorise caméra + bulle flottante.\n2. Active AirControl dans Accessibilité.\n3. Appuie ON.\n4. C'est tout : montre simplement ta main et fais un mouvement net gauche/droite/haut/bas.\n\nAucun V, aucune calibration, aucun armement.");sub.setTextSize(16);sub.setTextColor(0xFF40444B);root.addView(sub);
        Button overlay=new Button(this);overlay.setText("Autoriser la bulle flottante");overlay.setOnClickListener(v->openOverlay());root.addView(overlay,lp());
        Button accessibility=new Button(this);accessibility.setText("Ouvrir Accessibilité");accessibility.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(accessibility,lp());
        Button on=new Button(this);on.setText("ON · démarrer automatiquement");on.setOnClickListener(v->startAir());root.addView(on,lp());
        Button off=new Button(this);off.setText("OFF · arrêter");off.setOnClickListener(v->{Intent i=new Intent(this,AirControlService.class);i.setAction(AirControlService.ACTION_STOP);startService(i);});root.addView(off,lp());
        state=new TextView(this);state.setPadding(0,dp(16),0,0);root.addView(state);refresh();return scroll;
    }
    private LinearLayout.LayoutParams lp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(10);return p;}
    private void requestPerms(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},7);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8);}
    private void openOverlay(){if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this))startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())));}
    private void startAir(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPerms();return;}if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){openOverlay();return;}Intent i=new Intent(this,AirControlService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);refresh();}
    private void refresh(){if(state!=null)state.setText("Accessibilité : "+(AirAccessibilityService.isReady()?"OK":"à activer")+"\nOverlay : "+(Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(this)?"OK":"à autoriser"));}
    @Override protected void onResume(){super.onResume();refresh();}
    private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
}
