package fr.permsmp.afkgodnode;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class Net {
    private static final String BOOTSTRAP="08fcdba209c26cc64506027ae4747b4370ae25b99b9f623751c1caa64e9f30d0";
    static final class Response { final int code; final JSONObject json; Response(int c,JSONObject j){code=c;json=j;} }
    static Response postAny(String url, JSONObject body, String auth) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(7000); c.setReadTimeout(7000); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("User-Agent","AFKGodNode-Android/1.3.0");
        if(auth!=null)c.setRequestProperty("Authorization","Bearer "+auth);
        byte[] b=body.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(b.length);
        try(OutputStream o=c.getOutputStream()){o.write(b);} int code=c.getResponseCode();
        InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream(); String text=read(in); c.disconnect();
        JSONObject j; try{j=text.isEmpty()?new JSONObject():new JSONObject(text);}catch(Exception e){j=new JSONObject();j.put("raw",text);}
        return new Response(code,j);
    }
    static JSONObject postBootstrap(String url, JSONObject body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(7000); c.setReadTimeout(7000); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("User-Agent","AFKGodNode-Android/1.3.0"); c.setRequestProperty("X-AFK-Bootstrap",BOOTSTRAP);
        byte[] b=body.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(b.length);
        try(OutputStream o=c.getOutputStream()){o.write(b);} int code=c.getResponseCode();
        InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream(); String text=read(in); c.disconnect();
        JSONObject j; try{j=text.isEmpty()?new JSONObject():new JSONObject(text);}catch(Exception e){j=new JSONObject();j.put("raw",text);}
        if(code<200||code>=300)throw new IOException("HTTP "+code+" "+j.toString());
        return j;
    }
    static JSONObject post(String url, JSONObject body, String auth) throws Exception {
        Response r=postAny(url,body,auth); if(r.code<200||r.code>=300)throw new IOException("HTTP "+r.code+" "+r.json.toString()); return r.json;
    }
    static boolean probe(String url) {
        try { HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(3500);c.setReadTimeout(3500);c.setRequestProperty("User-Agent","AFKGodNode-Android/1.3.0");int code=c.getResponseCode();c.disconnect();return code>=200&&code<500; } catch(Exception e){return false;}
    }
    static String read(InputStream i)throws Exception{if(i==null)return "";ByteArrayOutputStream o=new ByteArrayOutputStream();byte[]b=new byte[2048];int n;while((n=i.read(b))>0&&o.size()<65536)o.write(b,0,n);return o.toString("UTF-8");}
}
