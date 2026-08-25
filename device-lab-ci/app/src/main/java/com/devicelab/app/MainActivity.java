package com.devicelab.app;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity implements SensorEventListener, LocationListener {
    private static final int REQ_LOCATION=101, REQ_MIC=102, REQ_CAMERA=103, REQ_BT=104;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private LinearLayout content, navRow;
    private String page="Sensors";
    private final String[] pages={"Sensors","Location","Battery","System","Network","Hardware","Tests"};

    private SensorManager sensorManager;
    private final Map<Integer,SensorState> sensorStates=new HashMap<>();
    private float[] lastAccel, lastMag;
    private float orientationAz=Float.NaN, orientationPitch=Float.NaN, orientationRoll=Float.NaN;
    private TextView orientationText;
    private BubbleLevelView activeBubble;

    private LocationManager locationManager;
    private boolean locationRunning=false;
    private TextView locLat,locLon,locAlt,locSpeed,locAcc,locBearing,locProvider,locTime,locSats,locStatus;
    private int satellitesUsed=0,satellitesVisible=0;

    private Intent lastBatteryIntent;
    private CircularGaugeView batteryGauge;
    private LinearLayout batteryFields;

    private boolean micRunning=false;
    private AudioRecord audioRecord;
    private Thread micThread;
    private TextView micValue;
    private ProgressBar micMeter;
    private boolean torchOn=false;
    private String torchCameraId;

    private final Runnable refreshLoop=new Runnable(){@Override public void run(){refreshVisiblePage();ui.postDelayed(this,180);}};

    private final BroadcastReceiver batteryReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){lastBatteryIntent=i; if("Battery".equals(page)) refreshBatteryPage();}};
    private final GnssStatus.Callback gnssCallback=new GnssStatus.Callback(){@Override public void onSatelliteStatusChanged(GnssStatus s){int used=0;for(int i=0;i<s.getSatelliteCount();i++)if(s.usedInFix(i))used++;satellitesVisible=s.getSatelliteCount();satellitesUsed=used;if("Location".equals(page)&&locSats!=null)locSats.setText(used+" utilisés / "+satellitesVisible+" visibles");}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.BG);
        if(Build.VERSION.SDK_INT>=23)getWindow().getDecorView().setSystemUiVisibility(0);
        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        initSensorStates();
        buildShell();
        IntentFilter batteryFilter=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(batteryReceiver,batteryFilter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(batteryReceiver,batteryFilter);
        lastBatteryIntent=registerReceiver(null,batteryFilter);
        showPage("Sensors");
        ui.post(refreshLoop);
    }

    @Override protected void onResume(){super.onResume();registerSensors();}
    @Override protected void onPause(){super.onPause();if(sensorManager!=null)sensorManager.unregisterListener(this);if(torchOn)toggleTorch(false);}
    @Override protected void onDestroy(){ui.removeCallbacks(refreshLoop);try{unregisterReceiver(batteryReceiver);}catch(Exception ignored){}stopLocation();stopMic();super.onDestroy();}

    private void buildShell(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Ui.BG);
        root.setPadding(0,Build.VERSION.SDK_INT>=35?Ui.dp(this,10):0,0,0);

        LinearLayout header=Ui.row(this);header.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,9));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.logo_mark);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(logo,new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,42)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.setPadding(Ui.dp(this,10),0,0,0);
        TextView title=Ui.text(this,"DEVICE LAB",21,Ui.TEXT,true);title.setLetterSpacing(.035f);titles.addView(title);
        TextView sub=Ui.text(this,"Laboratoire matériel temps réel",11,Ui.MUTED,false);titles.addView(sub);
        header.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView offline=Ui.text(this,"HORS LIGNE",10,Ui.GOOD,true);offline.setGravity(Gravity.CENTER);offline.setBackground(Ui.stroke(this,Ui.SURFACE,Ui.BORDER,15));offline.setPadding(Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,10),Ui.dp(this,6));header.addView(offline);
        root.addView(header);

        HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);hsv.setFillViewport(false);
        navRow=Ui.row(this);navRow.setPadding(Ui.dp(this,12),Ui.dp(this,5),Ui.dp(this,12),Ui.dp(this,10));hsv.addView(navRow);root.addView(hsv);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(Ui.dp(this,14),Ui.dp(this,4),Ui.dp(this,14),Ui.dp(this,28));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
    }

    private void rebuildNav(){
        navRow.removeAllViews();
        for(final String p:pages){Button b=Ui.chip(this,p,p.equals(page));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,Ui.dp(this,40));lp.setMargins(0,0,Ui.dp(this,8),0);navRow.addView(b,lp);b.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){showPage(p);}});}
    }

    private void showPage(String p){page=p;rebuildNav();content.removeAllViews();orientationText=null;batteryGauge=null;batteryFields=null;micValue=null;micMeter=null;
        if("Sensors".equals(p))buildSensorsPage();else if("Location".equals(p))buildLocationPage();else if("Battery".equals(p))buildBatteryPage();else if("System".equals(p))buildSystemPage();else if("Network".equals(p))buildNetworkPage();else if("Hardware".equals(p))buildHardwarePage();else buildTestsPage();
    }

    private void addHeading(String title,String subtitle){content.addView(Ui.title(this,title));TextView s=Ui.muted(this,subtitle);s.setPadding(0,Ui.dp(this,5),0,Ui.dp(this,14));content.addView(s);}
    private void addSection(String s){TextView t=Ui.section(this,s);t.setPadding(0,Ui.dp(this,12),0,Ui.dp(this,8));content.addView(t);}
    private void addCard(LinearLayout c){LinearLayout.LayoutParams lp=Ui.match();lp.setMargins(0,0,0,Ui.dp(this,10));content.addView(c,lp);}
    private TextView field(LinearLayout c,String label,String value){LinearLayout r=Ui.row(this);TextView l=Ui.text(this,label,13,Ui.MUTED,false);TextView v=Ui.text(this,value,13,Ui.TEXT,true);v.setGravity(Gravity.END);r.addView(l,Ui.weight(1));r.addView(v,Ui.weight(1.35f));c.addView(r,Ui.match());c.addView(Ui.gap(this,8));return v;}
    private void note(LinearLayout c,String s){TextView t=Ui.text(this,s,12,Ui.MUTED,false);t.setPadding(0,Ui.dp(this,6),0,0);c.addView(t);}

    private void initSensorStates(){
        int[] types={Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE,Sensor.TYPE_MAGNETIC_FIELD,Sensor.TYPE_PRESSURE,Sensor.TYPE_LIGHT,Sensor.TYPE_PROXIMITY,Sensor.TYPE_GRAVITY,Sensor.TYPE_LINEAR_ACCELERATION,Sensor.TYPE_ROTATION_VECTOR,Sensor.TYPE_AMBIENT_TEMPERATURE,Sensor.TYPE_RELATIVE_HUMIDITY};
        for(int t:types){Sensor s=sensorManager==null?null:sensorManager.getDefaultSensor(t);sensorStates.put(t,new SensorState(s,isScalarSensor(t)));}
    }
    private boolean isScalarSensor(int t){return t==Sensor.TYPE_PRESSURE||t==Sensor.TYPE_LIGHT||t==Sensor.TYPE_PROXIMITY||t==Sensor.TYPE_AMBIENT_TEMPERATURE||t==Sensor.TYPE_RELATIVE_HUMIDITY;}
    private void registerSensors(){if(sensorManager==null)return;for(SensorState st:sensorStates.values())if(st.sensor!=null)sensorManager.registerListener(this,st.sensor,SensorManager.SENSOR_DELAY_GAME);}

    @Override public void onSensorChanged(SensorEvent e){
        SensorState st=sensorStates.get(e.sensor.getType());if(st!=null){st.push(e.values,e.timestamp);}
        if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){lastAccel=e.values.clone();if(activeBubble!=null&&e.values.length>=3)activeBubble.update(e.values[0],e.values[1],e.values[2]);computeFallbackOrientation();}
        else if(e.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD){lastMag=e.values.clone();computeFallbackOrientation();}
        else if(e.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR){float[] r=new float[9],o=new float[3];try{SensorManager.getRotationMatrixFromVector(r,e.values);SensorManager.getOrientation(r,o);orientationAz=deg360(o[0]);orientationPitch=(float)Math.toDegrees(o[1]);orientationRoll=(float)Math.toDegrees(o[2]);}catch(Exception ignored){}}
    }
    private float deg360(float rad){float d=(float)Math.toDegrees(rad);if(d<0)d+=360;return d;}
    private void computeFallbackOrientation(){if(sensorStates.get(Sensor.TYPE_ROTATION_VECTOR).sensor!=null||lastAccel==null||lastMag==null)return;float[] r=new float[9],i=new float[9],o=new float[3];if(SensorManager.getRotationMatrix(r,i,lastAccel,lastMag)){SensorManager.getOrientation(r,o);orientationAz=deg360(o[0]);orientationPitch=(float)Math.toDegrees(o[1]);orientationRoll=(float)Math.toDegrees(o[2]);}}
    @Override public void onAccuracyChanged(Sensor s,int accuracy){}

    private void buildSensorsPage(){
        addHeading("Sensors","Valeurs réelles issues de SensorManager. Aucun zéro n’est inventé lorsqu’un capteur manque.");
        Button level=Ui.button(this,"Ouvrir le niveau à bulle",true);level.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){showBubbleLevel();}});content.addView(level,Ui.match());content.addView(Ui.gap(this,10));
        orientationText=Ui.text(this,"Orientation : en attente…",14,Ui.TEXT,true);LinearLayout oc=Ui.card(this);oc.addView(Ui.section(this,"ORIENTATION"));oc.addView(Ui.gap(this,8));oc.addView(orientationText);note(oc,"Rotation Vector utilisé lorsqu’il existe, sinon calcul accéléromètre + magnétomètre.");addCard(oc);
        addSensorCard("Accéléromètre",Sensor.TYPE_ACCELEROMETER,"m/s²");
        addSensorCard("Gyroscope",Sensor.TYPE_GYROSCOPE,"rad/s");
        addSensorCard("Magnétomètre",Sensor.TYPE_MAGNETIC_FIELD,"µT");
        addSensorCard("Pression / baromètre",Sensor.TYPE_PRESSURE,"hPa");
        addSensorCard("Luminosité",Sensor.TYPE_LIGHT,"lux");
        addSensorCard("Proximité",Sensor.TYPE_PROXIMITY,"cm");
        addSensorCard("Accélération linéaire",Sensor.TYPE_LINEAR_ACCELERATION,"m/s²");
        addSensorCard("Gravité",Sensor.TYPE_GRAVITY,"m/s²");
        addSection("Tous les capteurs présents");
        LinearLayout all=Ui.card(this);List<Sensor> list=sensorManager==null?new ArrayList<Sensor>():sensorManager.getSensorList(Sensor.TYPE_ALL);if(list.isEmpty())all.addView(Ui.muted(this,"Aucun capteur exposé par Android."));else for(Sensor s:list){TextView n=Ui.text(this,s.getName(),13,Ui.TEXT,true);all.addView(n);String d="Type "+s.getType()+" · "+s.getVendor()+" · v"+s.getVersion()+" · résolution "+Ui.f(s.getResolution(),4,"")+" · portée "+Ui.f(s.getMaximumRange(),2,"")+" · "+Ui.f(s.getPower(),2,"mA");TextView m=Ui.text(this,d,11,Ui.MUTED,false);m.setPadding(0,2,0,Ui.dp(this,10));all.addView(m);}addCard(all);
    }

    private void addSensorCard(String title,int type,String unit){
        final SensorState st=sensorStates.get(type);LinearLayout c=Ui.card(this);LinearLayout top=Ui.row(this);TextView name=Ui.text(this,title,15,Ui.TEXT,true);TextView hz=Ui.text(this,st!=null&&st.sensor!=null?"en attente":"NON DISPONIBLE",11,st!=null&&st.sensor!=null?Ui.ACCENT:Ui.WARN,true);hz.setGravity(Gravity.END);top.addView(name,Ui.weight(1));top.addView(hz,Ui.weight(1));c.addView(top);c.addView(Ui.gap(this,8));TextView val=Ui.text(this,st!=null&&st.sensor!=null?"En attente d’une mesure…":"Ce composant n’est pas exposé par Android sur cet appareil.",14,st!=null&&st.sensor!=null?Ui.TEXT:Ui.MUTED,false);c.addView(val);TextView stats=Ui.text(this,"",11,Ui.MUTED,false);stats.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,8));c.addView(stats);
        GraphView graph=new GraphView(this);if(st!=null)graph.setSource(st);if(st!=null&&st.sensor!=null)c.addView(graph,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,155)));
        if(st!=null){st.uiValue=val;st.uiStats=stats;st.uiHz=hz;st.graph=graph;st.unit=unit;}
        addCard(c);
    }

    private void refreshSensorsUi(){
        if(orientationText!=null){if(Float.isFinite(orientationAz))orientationText.setText(String.format(Locale.ROOT,"Azimut %.1f°   •   Pitch %.1f°   •   Roll %.1f°",orientationAz,orientationPitch,orientationRoll));else orientationText.setText("Orientation : en attente d’une mesure valide…");}
        for(SensorState st:sensorStates.values()){if(st==null||st.uiValue==null||st.sensor==null)continue;if(!st.has){st.uiValue.setText("En attente d’une mesure…");continue;}String v;if(st.scalar){v=String.format(Locale.ROOT,"%.3f %s",st.x,st.unit);}else v=String.format(Locale.ROOT,"X %.3f   Y %.3f   Z %.3f %s",st.x,st.y,st.z,st.unit);st.uiValue.setText(v);float[] q=st.stats();st.uiStats.setText(String.format(Locale.ROOT,"min %.3f  •  moy %.3f  •  max %.3f  •  %d échantillons",q[0],q[1],q[2],st.ring.size()));st.uiHz.setText(String.format(Locale.ROOT,"%.1f Hz",st.hz));if(st.graph!=null)st.graph.invalidate();}
    }

    private void showBubbleLevel(){
        final Dialog d=new Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Ui.BG);final BubbleLevelView bubble=new BubbleLevelView(this);activeBubble=bubble;frame.addView(bubble,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));LinearLayout controls=Ui.row(this);controls.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14));Button close=Ui.button(this,"Fermer",false),cal=Ui.button(this,"Calibrer",true);controls.addView(close,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);cp.setMargins(Ui.dp(this,8),0,0,0);controls.addView(cal,cp);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP);frame.addView(controls,lp);close.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){d.dismiss();}});cal.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){bubble.calibrate();Toast.makeText(MainActivity.this,"Zéro enregistré",Toast.LENGTH_SHORT).show();}});d.setContentView(frame);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Ui.BG));w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);}d.setOnDismissListener(x->{activeBubble=null;});SensorState a=sensorStates.get(Sensor.TYPE_ACCELEROMETER);if(a!=null&&a.has)bubble.update(a.x,a.y,a.z);d.show();if(w!=null)w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void buildLocationPage(){
        addHeading("Location","GPS Android réel : position, altitude, vitesse, précision et satellites GNSS. La permission n’est demandée que lorsque tu lances la mesure.");
        LinearLayout c=Ui.card(this);locStatus=Ui.text(this,locationRunning?"GPS ACTIF":"GPS ARRÊTÉ",12,locationRunning?Ui.GOOD:Ui.MUTED,true);c.addView(locStatus);c.addView(Ui.gap(this,10));locLat=field(c,"Latitude","—");locLon=field(c,"Longitude","—");locAlt=field(c,"Altitude","—");locSpeed=field(c,"Vitesse","—");locAcc=field(c,"Précision","—");locBearing=field(c,"Cap","—");locProvider=field(c,"Source","—");locTime=field(c,"Dernier fix","—");locSats=field(c,"Satellites",satellitesUsed+" utilisés / "+satellitesVisible+" visibles");addCard(c);
        final Button toggle=Ui.button(this,locationRunning?"Arrêter le GPS":"Démarrer le GPS",true);toggle.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){if(locationRunning)stopLocation();else startLocation();showPage("Location");}});content.addView(toggle,Ui.match());
        Button settings=Ui.button(this,"Ouvrir les réglages de localisation",false);LinearLayout.LayoutParams sp=Ui.match();sp.setMargins(0,Ui.dp(this,8),0,0);content.addView(settings,sp);settings.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){try{startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception ignored){}}});
        if(hasLocationPermission()){try{Location last=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(last!=null)renderLocation(last);}catch(Exception ignored){}}
    }
    private boolean hasLocationPermission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void startLocation(){if(!hasLocationPermission()){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;}try{boolean any=false;if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,500,0,this);any=true;}if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1000,0,this);any=true;}locationManager.registerGnssStatusCallback(gnssCallback,ui);locationRunning=any;if(!any)Toast.makeText(this,"Active la localisation Android",Toast.LENGTH_LONG).show();}catch(SecurityException e){locationRunning=false;}catch(Exception e){locationRunning=false;Toast.makeText(this,"GPS indisponible: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}}
    private void stopLocation(){if(locationManager!=null){try{locationManager.removeUpdates(this);}catch(Exception ignored){}try{locationManager.unregisterGnssStatusCallback(gnssCallback);}catch(Exception ignored){}}locationRunning=false;}
    @Override public void onLocationChanged(Location l){if("Location".equals(page))renderLocation(l);}
    private void renderLocation(Location l){if(locLat==null)return;locLat.setText(String.format(Locale.ROOT,"%.7f°",l.getLatitude()));locLon.setText(String.format(Locale.ROOT,"%.7f°",l.getLongitude()));locAlt.setText(l.hasAltitude()?Ui.f(l.getAltitude(),1,"m"):"Indisponible");locSpeed.setText(l.hasSpeed()?Ui.f(l.getSpeed()*3.6,1,"km/h"):"Indisponible");String acc=l.hasAccuracy()?Ui.f(l.getAccuracy(),1,"m"):"Indisponible";if(Build.VERSION.SDK_INT>=26&&l.hasVerticalAccuracy())acc += "  · V ±"+Ui.f(l.getVerticalAccuracyMeters(),1,"m");locAcc.setText(acc);locBearing.setText(l.hasBearing()?Ui.f(l.getBearing(),1,"°"):"Indisponible");locProvider.setText(l.getProvider()==null?"Inconnue":l.getProvider());locTime.setText(android.text.format.DateFormat.format("HH:mm:ss",l.getTime()));if(locStatus!=null){locStatus.setText("GPS ACTIF · mesure reçue");locStatus.setTextColor(Ui.GOOD);}}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onProviderDisabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}

    private void buildBatteryPage(){addHeading("Battery","État de la batterie lu via ACTION_BATTERY_CHANGED et BatteryManager.");batteryGauge=new CircularGaugeView(this);content.addView(batteryGauge,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,205)));batteryFields=Ui.card(this);addCard(batteryFields);refreshBatteryPage();}
    private void refreshBatteryPage(){if(batteryFields==null)return;batteryFields.removeAllViews();Intent i=lastBatteryIntent;if(i==null){batteryFields.addView(Ui.muted(this,"Android n’a pas encore fourni l’état batterie."));return;}int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,-1);float pct=level>=0&&scale>0?level*100f/scale:Float.NaN;if(batteryGauge!=null)batteryGauge.setValue(pct,Float.isFinite(pct)?String.format(Locale.ROOT,"%.0f%%",pct):"—");int temp=i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE),volt=i.getIntExtra(BatteryManager.EXTRA_VOLTAGE,Integer.MIN_VALUE),status=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1),health=i.getIntExtra(BatteryManager.EXTRA_HEALTH,-1),plug=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);field(batteryFields,"Niveau",Float.isFinite(pct)?Ui.f(pct,1,"%"):"Indisponible");field(batteryFields,"Température",temp!=Integer.MIN_VALUE?Ui.f(temp/10.0,1,"°C"):"Indisponible");field(batteryFields,"Tension",volt!=Integer.MIN_VALUE?Ui.f(volt/1000.0,3,"V"):"Indisponible");field(batteryFields,"État",batteryStatus(status));field(batteryFields,"Santé",batteryHealth(health));field(batteryFields,"Source",plugged(plug));BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);if(bm!=null){field(batteryFields,"Capacité Android",propInt(bm,BatteryManager.BATTERY_PROPERTY_CAPACITY,"%",1));field(batteryFields,"Courant instantané",propIntScaled(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,1000.0,"mA"));field(batteryFields,"Courant moyen",propIntScaled(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE,1000.0,"mA"));field(batteryFields,"Charge restante",propIntScaled(bm,BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,1000.0,"mAh"));long energy=bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);field(batteryFields,"Énergie restante",energy==Long.MIN_VALUE?"Indisponible":Ui.f(energy/1_000_000_000.0,3,"Wh"));}}
    private String propInt(BatteryManager b,int id,String u,int dec){int v=b.getIntProperty(id);return v==Integer.MIN_VALUE?"Indisponible":Ui.f(v,dec,u);}private String propIntScaled(BatteryManager b,int id,double s,String u){int v=b.getIntProperty(id);return v==Integer.MIN_VALUE?"Indisponible":Ui.f(v/s,1,u);}
    private String batteryStatus(int v){switch(v){case BatteryManager.BATTERY_STATUS_CHARGING:return"En charge";case BatteryManager.BATTERY_STATUS_DISCHARGING:return"Décharge";case BatteryManager.BATTERY_STATUS_FULL:return"Pleine";case BatteryManager.BATTERY_STATUS_NOT_CHARGING:return"Pas en charge";default:return"Inconnu";}}
    private String batteryHealth(int v){switch(v){case BatteryManager.BATTERY_HEALTH_GOOD:return"Bonne";case BatteryManager.BATTERY_HEALTH_COLD:return"Froide";case BatteryManager.BATTERY_HEALTH_OVERHEAT:return"Surchauffe";case BatteryManager.BATTERY_HEALTH_DEAD:return"Défaillante";case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:return"Surtension";default:return"Inconnue";}}
    private String plugged(int p){if((p&BatteryManager.BATTERY_PLUGGED_AC)!=0)return"Secteur";if((p&BatteryManager.BATTERY_PLUGGED_USB)!=0)return"USB";if((p&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0)return"Sans fil";if(Build.VERSION.SDK_INT>=33&&(p&BatteryManager.BATTERY_PLUGGED_DOCK)!=0)return"Dock";return"Batterie";}

    private void buildSystemPage(){addHeading("System","Mémoire, stockage, CPU, Android, écran et caractéristiques accessibles sans privilège root.");addSection("Mémoire");LinearLayout mem=Ui.card(this);ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();if(am!=null){am.getMemoryInfo(mi);field(mem,"RAM totale",Ui.bytes(mi.totalMem));field(mem,"RAM disponible",Ui.bytes(mi.availMem));field(mem,"Seuil mémoire faible",Ui.bytes(mi.threshold));field(mem,"État",mi.lowMemory?"Mémoire faible":"Normal");}else mem.addView(Ui.muted(this,"ActivityManager indisponible"));addCard(mem);
        addSection("Stockage");LinearLayout st=Ui.card(this);try{StatFs sf=new StatFs(Environment.getDataDirectory().getAbsolutePath());field(st,"Total",Ui.bytes(sf.getTotalBytes()));field(st,"Disponible",Ui.bytes(sf.getAvailableBytes()));field(st,"Utilisé",Ui.bytes(sf.getTotalBytes()-sf.getAvailableBytes()));}catch(Exception e){st.addView(Ui.muted(this,"Statistiques stockage indisponibles"));}addCard(st);
        addSection("CPU / appareil");LinearLayout cpu=Ui.card(this);field(cpu,"Fabricant",safe(Build.MANUFACTURER));field(cpu,"Marque",safe(Build.BRAND));field(cpu,"Modèle",safe(Build.MODEL));field(cpu,"Appareil",safe(Build.DEVICE));field(cpu,"Produit",safe(Build.PRODUCT));field(cpu,"Android",Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT);field(cpu,"ABI",TextUtils.join(", ",Build.SUPPORTED_ABIS));field(cpu,"Cœurs logiques",String.valueOf(Runtime.getRuntime().availableProcessors()));if(Build.VERSION.SDK_INT>=31){field(cpu,"SoC",safe(Build.SOC_MANUFACTURER)+" "+safe(Build.SOC_MODEL));}String model=cpuModel();field(cpu,"CPU exposé",model.length()==0?"Indisponible":model);String freq=readFirst("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");field(cpu,"Fréquence max CPU0",freq.length()==0?"Indisponible":freq+" kHz");addCard(cpu);
        addSection("Écran");LinearLayout sc=Ui.card(this);DisplayMetrics dm=new DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(dm);Display d=getWindowManager().getDefaultDisplay();field(sc,"Résolution",dm.widthPixels+" × "+dm.heightPixels+" px");field(sc,"Densité",dm.densityDpi+" dpi · x"+String.format(Locale.ROOT,"%.2f",dm.density));field(sc,"Taux actuel",Ui.f(d.getRefreshRate(),2,"Hz"));if(Build.VERSION.SDK_INT>=23){Display.Mode m=d.getMode();field(sc,"Mode",m.getPhysicalWidth()+" × "+m.getPhysicalHeight()+" @ "+Ui.f(m.getRefreshRate(),2,"Hz"));Display.Mode[] modes=d.getSupportedModes();StringBuilder rs=new StringBuilder();for(Display.Mode x:modes){if(rs.length()>0)rs.append(" · ");rs.append(String.format(Locale.ROOT,"%.0f",x.getRefreshRate())).append("Hz");}field(sc,"Modes exposés",rs.length()==0?"Indisponible":rs.toString());}if(Build.VERSION.SDK_INT>=26){field(sc,"Wide color gamut",String.valueOf(getResources().getConfiguration().isScreenWideColorGamut()));}addCard(sc);
    }
    private String cpuModel(){String s=readFirstMatch("/proc/cpuinfo",new String[]{"model name","Hardware","Processor"});return s;}
    private String readFirst(String path){try{BufferedReader b=new BufferedReader(new FileReader(path));String s=b.readLine();b.close();return s==null?"":s.trim();}catch(Exception e){return"";}}
    private String readFirstMatch(String path,String[] keys){try{BufferedReader b=new BufferedReader(new FileReader(path));String line;while((line=b.readLine())!=null){for(String k:keys)if(line.toLowerCase(Locale.ROOT).startsWith(k.toLowerCase(Locale.ROOT))){b.close();int x=line.indexOf(':');return(x>=0?line.substring(x+1):line).trim();}}b.close();}catch(Exception ignored){}return"";}
    private String safe(String s){return s==null||s.trim().length()==0?"Indisponible":s;}

    private void buildNetworkPage(){addHeading("Network","État réseau local. Aucune permission Internet n’est déclarée par Device Lab.");addSection("Connexion active");LinearLayout net=Ui.card(this);ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm!=null){try{Network n=cm.getActiveNetwork();NetworkCapabilities nc=n==null?null:cm.getNetworkCapabilities(n);if(nc==null){field(net,"État","Aucune connexion active");}else{field(net,"Transport",transports(nc));field(net,"Validé",String.valueOf(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)));field(net,"Internet déclaré",String.valueOf(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));field(net,"Mesuré",cm.isActiveNetworkMetered()?"Oui":"Non");field(net,"Débit descendant annoncé",nc.getLinkDownstreamBandwidthKbps()>0?nc.getLinkDownstreamBandwidthKbps()+" kb/s":"Indisponible");field(net,"Débit montant annoncé",nc.getLinkUpstreamBandwidthKbps()>0?nc.getLinkUpstreamBandwidthKbps()+" kb/s":"Indisponible");}}catch(Exception e){net.addView(Ui.muted(this,"Informations réseau limitées par Android."));}}addCard(net);
        addSection("Wi‑Fi");LinearLayout wifi=Ui.card(this);WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(wm==null){wifi.addView(Ui.muted(this,"Wi‑Fi non exposé"));}else{try{field(wifi,"Wi‑Fi",wm.isWifiEnabled()?"Activé":"Désactivé");WifiInfo info=wm.getConnectionInfo();if(info!=null){field(wifi,"SSID",cleanSsid(info.getSSID()));field(wifi,"BSSID",safe(info.getBSSID()));field(wifi,"RSSI",info.getRssi()==-127?"Indisponible":info.getRssi()+" dBm");field(wifi,"Vitesse lien",info.getLinkSpeed()>0?info.getLinkSpeed()+" Mb/s":"Indisponible");if(Build.VERSION.SDK_INT>=21)field(wifi,"Fréquence",info.getFrequency()>0?info.getFrequency()+" MHz":"Indisponible");if(Build.VERSION.SDK_INT>=30){field(wifi,"Rx",info.getRxLinkSpeedMbps()>0?info.getRxLinkSpeedMbps()+" Mb/s":"Indisponible");field(wifi,"Tx",info.getTxLinkSpeedMbps()>0?info.getTxLinkSpeedMbps()+" Mb/s":"Indisponible");}}}catch(SecurityException e){wifi.addView(Ui.muted(this,"Android masque certains détails Wi‑Fi sans autorisation de localisation."));}}
        Button perm=Ui.button(this,"Autoriser les détails Wi‑Fi",false);perm.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);}});wifi.addView(perm);addCard(wifi);
        addSection("Réseau mobile");LinearLayout mobile=Ui.card(this);TelephonyManager tm=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);if(tm==null){mobile.addView(Ui.muted(this,"Téléphonie non disponible"));}else{field(mobile,"Opérateur",safe(tm.getNetworkOperatorName()));field(mobile,"Pays réseau",safe(tm.getNetworkCountryIso()));field(mobile,"SIM présente",String.valueOf(tm.getSimState()!=TelephonyManager.SIM_STATE_ABSENT));try{field(mobile,"Type données",networkTypeName(tm.getDataNetworkType()));}catch(SecurityException e){field(mobile,"Type données","Restreint par Android");}}addCard(mobile);
    }
    private String cleanSsid(String s){if(s==null||"<unknown ssid>".equalsIgnoreCase(s))return"Indisponible / permission requise";if(s.startsWith("\"")&&s.endsWith("\"")&&s.length()>1)return s.substring(1,s.length()-1);return s;}
    private String transports(NetworkCapabilities n){ArrayList<String>a=new ArrayList<>();if(n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))a.add("Wi‑Fi");if(n.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))a.add("Mobile");if(n.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))a.add("Ethernet");if(n.hasTransport(NetworkCapabilities.TRANSPORT_VPN))a.add("VPN");if(n.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH))a.add("Bluetooth");return a.isEmpty()?"Autre":TextUtils.join(" + ",a);}
    private String networkTypeName(int n){switch(n){case TelephonyManager.NETWORK_TYPE_LTE:return"LTE / 4G";case TelephonyManager.NETWORK_TYPE_NR:return"NR / 5G";case TelephonyManager.NETWORK_TYPE_HSPAP:return"HSPA+";case TelephonyManager.NETWORK_TYPE_HSPA:return"HSPA";case TelephonyManager.NETWORK_TYPE_UMTS:return"UMTS / 3G";case TelephonyManager.NETWORK_TYPE_EDGE:return"EDGE";case TelephonyManager.NETWORK_TYPE_GPRS:return"GPRS";case TelephonyManager.NETWORK_TYPE_UNKNOWN:return"Inconnu";default:return"Type Android "+n;}}

    private void buildHardwarePage(){addHeading("Hardware","Bluetooth, NFC, USB, caméras et audio. Les composants absents sont signalés explicitement.");
        addSection("Bluetooth / NFC");LinearLayout radio=Ui.card(this);BluetoothManager bm=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);BluetoothAdapter ba=bm==null?null:bm.getAdapter();field(radio,"Bluetooth",ba==null?"Non disponible":"Présent");if(ba!=null){try{field(radio,"État",ba.isEnabled()?"Activé":"Désactivé");if(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)field(radio,"Nom",safe(ba.getName()));else field(radio,"Nom","Permission Bluetooth requise");}catch(SecurityException e){field(radio,"État","Permission Bluetooth requise");}field(radio,"BLE",getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)?"Oui":"Non");}
        NfcAdapter nfc=NfcAdapter.getDefaultAdapter(this);field(radio,"NFC",nfc==null?"Non disponible":nfc.isEnabled()?"Présent · activé":"Présent · désactivé");if(Build.VERSION.SDK_INT>=31&&ba!=null){Button p=Ui.button(this,"Autoriser les détails Bluetooth",false);p.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);}});radio.addView(p);}addCard(radio);
        addSection("USB");LinearLayout usb=Ui.card(this);UsbManager um=(UsbManager)getSystemService(USB_SERVICE);if(um==null){usb.addView(Ui.muted(this,"USB Manager indisponible"));}else{Map<String,UsbDevice> devices=um.getDeviceList();field(usb,"USB Host",getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)?"Oui":"Non");field(usb,"Périphériques connectés",String.valueOf(devices.size()));for(UsbDevice d:devices.values()){String name="VID "+String.format(Locale.ROOT,"%04X",d.getVendorId())+" · PID "+String.format(Locale.ROOT,"%04X",d.getProductId());TextView t=Ui.text(this,name,12,Ui.TEXT,true);usb.addView(t);try{TextView s=Ui.text(this,safe(d.getManufacturerName())+" · "+safe(d.getProductName()),11,Ui.MUTED,false);usb.addView(s);}catch(Exception ignored){}}}addCard(usb);
        addSection("Caméras");LinearLayout cams=Ui.card(this);renderCameraInfo(cams);addCard(cams);
        addSection("Audio");LinearLayout audio=Ui.card(this);AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);if(am!=null){field(audio,"Haut‑parleur",getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)?"Présent":"Non déclaré");field(audio,"Microphone",getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)?"Présent":"Non déclaré");field(audio,"Fréquence sortie",safe(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE))+" Hz");field(audio,"Frames / buffer",safe(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)));}else audio.addView(Ui.muted(this,"AudioManager indisponible"));addCard(audio);
    }
    private void renderCameraInfo(LinearLayout cams){CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE);if(cm==null){cams.addView(Ui.muted(this,"CameraManager indisponible"));return;}try{String[] ids=cm.getCameraIdList();field(cams,"Nombre",String.valueOf(ids.length));for(String id:ids){CameraCharacteristics cc=cm.getCameraCharacteristics(id);Integer facing=cc.get(CameraCharacteristics.LENS_FACING);Boolean flash=cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);Size pix=cc.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);float[] focal=cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);Integer hw=cc.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);TextView h=Ui.text(this,"Caméra "+id+" · "+facingName(facing),13,Ui.TEXT,true);h.setPadding(0,Ui.dp(this,8),0,2);cams.addView(h);StringBuilder d=new StringBuilder();if(pix!=null)d.append(pix.getWidth()).append("×").append(pix.getHeight()).append(" · ").append(String.format(Locale.ROOT,"%.1f MP",pix.getWidth()*pix.getHeight()/1e6));d.append(" · flash ").append(Boolean.TRUE.equals(flash)?"oui":"non");d.append(" · niveau ").append(hwName(hw));if(focal!=null&&focal.length>0)d.append(" · focales ").append(Arrays.toString(focal)).append(" mm");if(Build.VERSION.SDK_INT>=30){Range<Float> zr=cc.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);if(zr!=null)d.append(" · zoom ").append(zr.getLower()).append("–").append(zr.getUpper()).append("×");}StreamConfigurationMap map=cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);if(map!=null){Size[] js=map.getOutputSizes(ImageFormat.JPEG);Size best=maxSize(js);if(best!=null)d.append(" · JPEG max ").append(best.getWidth()).append("×").append(best.getHeight());}TextView info=Ui.text(this,d.toString(),11,Ui.MUTED,false);cams.addView(info);}}catch(Exception e){cams.addView(Ui.muted(this,"Détails caméra indisponibles: "+e.getClass().getSimpleName()));}}
    private Size maxSize(Size[] a){if(a==null||a.length==0)return null;Size b=a[0];for(Size s:a)if((long)s.getWidth()*s.getHeight()>(long)b.getWidth()*b.getHeight())b=s;return b;}
    private String facingName(Integer i){if(i==null)return"inconnue";if(i==CameraCharacteristics.LENS_FACING_BACK)return"arrière";if(i==CameraCharacteristics.LENS_FACING_FRONT)return"avant";if(i==CameraCharacteristics.LENS_FACING_EXTERNAL)return"externe";return"type "+i;}
    private String hwName(Integer i){if(i==null)return"?";switch(i){case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:return"LEGACY";case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:return"LIMITED";case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:return"FULL";case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:return"LEVEL_3";case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL:return"EXTERNAL";default:return String.valueOf(i);}}

    private void buildTestsPage(){addHeading("Tests","Chaque test est lancé manuellement. Les permissions sensibles ne sont demandées qu’au moment où elles deviennent nécessaires.");
        addSection("Retour haptique / haut‑parleur");LinearLayout a=Ui.card(this);Button vib=Ui.button(this,"Tester la vibration",false);Button spk=Ui.button(this,"Tester le haut‑parleur",false);a.addView(vib);a.addView(Ui.gap(this,8));a.addView(spk);vib.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){testVibration();}});spk.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){testSpeaker();}});addCard(a);
        addSection("Microphone");LinearLayout m=Ui.card(this);micValue=Ui.text(this,"Micro arrêté",14,Ui.TEXT,true);m.addView(micValue);micMeter=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);micMeter.setMax(100);micMeter.setProgress(0);LinearLayout.LayoutParams mp=Ui.match();mp.setMargins(0,Ui.dp(this,12),0,Ui.dp(this,10));m.addView(micMeter,mp);final Button mic=Ui.button(this,micRunning?"Arrêter le micro":"Démarrer le test micro",true);mic.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){if(micRunning)stopMic();else startMic();showPage("Tests");}});m.addView(mic);note(m,"Niveau affiché en dBFS relatif au signal PCM 16 bits. Ce n’est pas un sonomètre calibré en dB SPL.");addCard(m);
        addSection("Lampe / écran");LinearLayout c=Ui.card(this);Button torch=Ui.button(this,torchOn?"Éteindre la lampe":"Tester la lampe torche",false);Button screen=Ui.button(this,"Test écran couleurs",false);c.addView(torch);c.addView(Ui.gap(this,8));c.addView(screen);torch.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);}else toggleTorch(!torchOn);showPage("Tests");}});screen.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){showScreenTest();}});addCard(c);
        addSection("Auto‑test capteurs");LinearLayout s=Ui.card(this);int present=0,active=0;for(SensorState st:sensorStates.values()){if(st.sensor!=null){present++;if(st.has)active++;}}field(s,"Capteurs principaux présents",String.valueOf(present));field(s,"Capteurs avec mesure reçue",String.valueOf(active));SensorState prox=sensorStates.get(Sensor.TYPE_PROXIMITY);field(s,"Proximité",prox==null||prox.sensor==null?"Non disponible":prox.has?Ui.f(prox.x,2,"cm"):"Présent · en attente");SensorState bar=sensorStates.get(Sensor.TYPE_PRESSURE);field(s,"Baromètre",bar==null||bar.sensor==null?"Non disponible":bar.has?Ui.f(bar.x,2,"hPa"):"Présent · en attente");addCard(s);
    }
    private void testVibration(){Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v==null||!v.hasVibrator()){Toast.makeText(this,"Vibreur non disponible",Toast.LENGTH_SHORT).show();return;}if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createWaveform(new long[]{0,100,70,160},-1));else v.vibrate(220);}
    private void testSpeaker(){try{final ToneGenerator tg=new ToneGenerator(AudioManager.STREAM_MUSIC,75);tg.startTone(ToneGenerator.TONE_PROP_BEEP2,650);ui.postDelayed(new Runnable(){@Override public void run(){try{tg.release();}catch(Exception ignored){}}},800);}catch(Exception e){Toast.makeText(this,"Test haut‑parleur indisponible",Toast.LENGTH_SHORT).show();}}
    private void startMic(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}if(micRunning)return;try{int rate=16000,min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)min=4096;audioRecord=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096));if(audioRecord.getState()!=AudioRecord.STATE_INITIALIZED){audioRecord.release();audioRecord=null;Toast.makeText(this,"Micro non initialisable",Toast.LENGTH_LONG).show();return;}micRunning=true;audioRecord.startRecording();micThread=new Thread(new Runnable(){@Override public void run(){short[] buf=new short[1024];while(micRunning&&audioRecord!=null){int n=audioRecord.read(buf,0,buf.length);if(n>0){double sum=0;for(int i=0;i<n;i++){double x=buf[i]/32768.0;sum+=x*x;}double rms=Math.sqrt(sum/n);final double db=rms>0?20*Math.log10(rms):-120;final int meter=(int)Math.max(0,Math.min(100,(db+80)*1.25));ui.post(new Runnable(){@Override public void run(){if(micValue!=null)micValue.setText(String.format(Locale.ROOT,"%.1f dBFS",db));if(micMeter!=null)micMeter.setProgress(meter);}});}}},"DeviceLabMic");micThread.start();}catch(Exception e){micRunning=false;Toast.makeText(this,"Micro indisponible: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}}
    private void stopMic(){micRunning=false;try{if(audioRecord!=null)audioRecord.stop();}catch(Exception ignored){}try{if(audioRecord!=null)audioRecord.release();}catch(Exception ignored){}audioRecord=null;micThread=null;}
    private void toggleTorch(boolean on){CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE);if(cm==null){Toast.makeText(this,"Caméra indisponible",Toast.LENGTH_SHORT).show();return;}try{if(torchCameraId==null){for(String id:cm.getCameraIdList()){CameraCharacteristics cc=cm.getCameraCharacteristics(id);Boolean f=cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);Integer face=cc.get(CameraCharacteristics.LENS_FACING);if(Boolean.TRUE.equals(f)&&(face==null||face==CameraCharacteristics.LENS_FACING_BACK)){torchCameraId=id;break;}}}if(torchCameraId==null){Toast.makeText(this,"Aucun flash exposé",Toast.LENGTH_SHORT).show();return;}cm.setTorchMode(torchCameraId,on);torchOn=on;}catch(SecurityException e){Toast.makeText(this,"Permission caméra requise",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Lampe indisponible: "+e.getClass().getSimpleName(),Toast.LENGTH_SHORT).show();}}
    private void showScreenTest(){final Dialog d=new Dialog(this,android.R.style.Theme_Material_NoActionBar_Fullscreen);final int[] colors={Color.BLACK,Color.WHITE,Color.RED,Color.GREEN,Color.BLUE,Color.GRAY};final String[] names={"NOIR","BLANC","ROUGE","VERT","BLEU","GRIS"};final FrameLayout f=new FrameLayout(this);final TextView t=Ui.text(this,"NOIR\nTouchez pour changer · appui long pour fermer",18,Color.WHITE,true);t.setGravity(Gravity.CENTER);f.setBackgroundColor(colors[0]);f.addView(t,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));final int[] idx={0};f.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){idx[0]=(idx[0]+1)%colors.length;f.setBackgroundColor(colors[idx[0]]);t.setText(names[idx[0]]+"\nTouchez pour changer · appui long pour fermer");t.setTextColor(idx[0]==1?Color.BLACK:Color.WHITE);}});f.setOnLongClickListener(new View.OnLongClickListener(){@Override public boolean onLongClick(View v){d.dismiss();return true;}});d.setContentView(f);d.show();}

    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] grants){super.onRequestPermissionsResult(req,perms,grants);if(req==REQ_LOCATION){if(hasLocationPermission()){if("Location".equals(page))startLocation();showPage(page);}else Toast.makeText(this,"Permission localisation refusée",Toast.LENGTH_SHORT).show();}else if(req==REQ_MIC){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startMic();else Toast.makeText(this,"Permission micro refusée",Toast.LENGTH_SHORT).show();}else if(req==REQ_CAMERA){if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)toggleTorch(true);else Toast.makeText(this,"Permission caméra refusée",Toast.LENGTH_SHORT).show();}else if(req==REQ_BT){showPage("Hardware");}}

    private void refreshVisiblePage(){if("Sensors".equals(page))refreshSensorsUi();else if("Battery".equals(page))refreshBatteryPage();}

    static final class SensorState implements GraphView.Source {
        final Sensor sensor;final boolean scalar;final Ring ring=new Ring(240);boolean has=false;float x=Float.NaN,y=Float.NaN,z=Float.NaN,hz=0;long lastTs=0;TextView uiValue,uiStats,uiHz;GraphView graph;String unit="";
        SensorState(Sensor s,boolean scalar){this.sensor=s;this.scalar=scalar;}
        void push(float[] v,long ts){if(v==null||v.length==0)return;x=v[0];y=v.length>1?v[1]:Float.NaN;z=v.length>2?v[2]:Float.NaN;has=true;if(lastTs>0&&ts>lastTs){float now=1_000_000_000f/(ts-lastTs);hz=hz==0?now:hz*.86f+now*.14f;}lastTs=ts;ring.add(x,y,z,scalar);}
        float[] stats(){return ring.stats(scalar);}
        @Override public int size(){return ring.size();}@Override public float xAt(int i){return ring.xAt(i);}@Override public float yAt(int i){return ring.yAt(i);}@Override public float zAt(int i){return ring.zAt(i);}@Override public boolean scalar(){return scalar;}
    }
    static final class Ring {
        final float[] x,y,z;int next=0,count=0;Ring(int n){x=new float[n];y=new float[n];z=new float[n];Arrays.fill(x,Float.NaN);Arrays.fill(y,Float.NaN);Arrays.fill(z,Float.NaN);}void add(float a,float b,float c,boolean scalar){x[next]=scalar?a:(float)Math.sqrt(a*a+(Float.isFinite(b)?b*b:0)+(Float.isFinite(c)?c*c:0));y[next]=b;z[next]=c;next=(next+1)%x.length;if(count<x.length)count++;}int size(){return count;}int idx(int i){int start=(next-count+x.length)%x.length;return(start+i)%x.length;}float xAt(int i){return x[idx(i)];}float yAt(int i){return y[idx(i)];}float zAt(int i){return z[idx(i)];}float[] stats(boolean scalar){if(count==0)return new float[]{Float.NaN,Float.NaN,Float.NaN};float mn=Float.POSITIVE_INFINITY,mx=Float.NEGATIVE_INFINITY;double sum=0;int n=0;for(int i=0;i<count;i++){float v=xAt(i);if(Float.isFinite(v)){mn=Math.min(mn,v);mx=Math.max(mx,v);sum+=v;n++;}}return n==0?new float[]{Float.NaN,Float.NaN,Float.NaN}:new float[]{mn,(float)(sum/n),mx};}}
}
