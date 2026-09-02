package fr.permsmp.afkgodnode;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String SERVER="https://afk.permsmp.fr";
    private LinearLayout root;
    private TextView status;
    private EditText name;

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(235,240,250));t.setPadding(0,dp(7),0,dp(7));return t;}
    private EditText field(String value){EditText e=new EditText(this);e.setText(value);e.setSingleLine(true);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.GRAY);e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(120,150,230)));return e;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);return b;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(8,11,18));
        ScrollView sc=new ScrollView(this);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(36),dp(22),dp(36));root.setBackgroundColor(Color.rgb(8,11,18));sc.addView(root);setContentView(sc);
        root.addView(text("AFK God Node",36));root.addView(text("Android Host Candidate · v1.3.0",15));status=text("",14);root.addView(status);
        if(NodeConfig.configured(this))showConfigured();else showSetup();
    }

    private void showSetup(){
        root.addView(text("Activer ce téléphone",22));
        root.addView(text("Aucun Discord, aucun code et aucun compte à connecter. Donne juste un nom public puis active l’appareil.",14));
        root.addView(text("Nom public",13));
        name=field("Téléphone Android");root.addView(name);
        Button go=button("ACTIVER CET APPAREIL");go.setOnClickListener(v->activate());root.addView(go);
        root.addView(text("Après ce clic, le node s’enregistre tout seul et démarre en arrière-plan.",13));
    }

    private void activate(){
        status.setText("Activation en cours…");
        final String publicName=name==null?"Téléphone Android":name.getText().toString().trim();
        Executors.newSingleThreadExecutor().execute(()->{
            try{
                JSONObject req=new JSONObject();req.put("name",publicName.isEmpty()?"Téléphone Android":publicName);req.put("kind","android");req.put("tier",3);
                JSONObject r=Net.postBootstrap(SERVER+"/api/v3/nodes/auto-enroll",req);
                NodeConfig n=new NodeConfig(SERVER,r.getString("nodeId"),r.getString("nodeSecret"),r.optString("publicId"),r.optString("name","Téléphone Android"),r.optString("kind","android"),r.optInt("tier",3));
                NodeConfig.save(this,n);
                runOnUiThread(()->{startNode();recreate();});
            }catch(Exception e){runOnUiThread(()->status.setText("Erreur : "+e.getMessage()));}
        });
    }

    private void showConfigured(){
        try{NodeConfig c=NodeConfig.load(this);status.setText("ACTIF DANS LE CLUSTER\n"+c.name+" · T"+c.tier+" · STANDBY\nAucun compte Discord n’est utilisé sur cet appareil.");}
        catch(Exception e){status.setText(e.getMessage());}
        root.addView(text("Le node tourne en arrière-plan et sera relancé automatiquement après redémarrage Android.",13));
        Button start=button("RELANCER LE NODE");start.setOnClickListener(v->startNode());root.addView(start);
        startNode();
    }

    private void startNode(){
        try{Intent i=new Intent(this,NodeService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}catch(Exception e){status.append("\nErreur service : "+e.getMessage());}
    }
}
