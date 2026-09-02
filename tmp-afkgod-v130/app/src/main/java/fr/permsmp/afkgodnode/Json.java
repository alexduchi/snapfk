package fr.permsmp.afkgodnode;
import org.json.JSONObject;
final class Json {
    static JSONObject heartbeat(int score, boolean discord, boolean power, int battery) throws Exception {
        JSONObject o=new JSONObject();
        o.put("score",score); o.put("active",false); o.put("canHostDiscord",true); o.put("hostReady",true);
        o.put("discordReachable",discord); o.put("onExternalPower",power); if (battery>=0)o.put("battery",battery);
        o.put("agentVersion","1.3.0"); o.put("platform","Android"); o.put("arch",android.os.Build.SUPPORTED_ABIS.length>0?android.os.Build.SUPPORTED_ABIS[0]:"unknown");
        return o;
    }
}
