package fr.permsmp.afkgodnode;

import android.content.Context;
import android.content.SharedPreferences;

final class NodeConfig {
    static final String PREFS = "afk_god_node";
    final String server, nodeId, secret, publicId, name, kind;
    final int tier;
    NodeConfig(String server, String nodeId, String secret, String publicId, String name, String kind, int tier) {
        this.server=server; this.nodeId=nodeId; this.secret=secret; this.publicId=publicId; this.name=name; this.kind=kind; this.tier=tier;
    }
    static NodeConfig load(Context c) throws Exception {
        SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);String enc=p.getString("secret","");
        if(p.getString("nodeId","").isEmpty()||enc.isEmpty())throw new Exception("Nœud non configuré");
        return new NodeConfig(p.getString("server","https://afk.permsmp.fr"),p.getString("nodeId",""),SecretStore.decrypt(enc),p.getString("publicId",""),p.getString("name","Android Node"),p.getString("kind","android"),p.getInt("tier",3));
    }
    static void save(Context c,NodeConfig n)throws Exception{
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("server",n.server).putString("nodeId",n.nodeId).putString("secret",SecretStore.encrypt(n.secret)).putString("publicId",n.publicId).putString("name",n.name).putString("kind",n.kind).putInt("tier",n.tier).apply();
    }
    static boolean configured(Context c){return !c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("nodeId","").isEmpty();}
}
