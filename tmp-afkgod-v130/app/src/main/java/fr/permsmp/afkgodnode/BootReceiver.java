package fr.permsmp.afkgodnode;
import android.content.*;
import android.os.Build;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i){
        if(!NodeConfig.configured(c))return;
        Intent s=new Intent(c,NodeService.class);
        try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);}catch(Exception ignored){}
    }
}
