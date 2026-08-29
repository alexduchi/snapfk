package com.neo.tracepilot;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class TraceDb extends SQLiteOpenHelper {
  static final String NAME="tracepilot_v7.db";
  TraceDb(Context c){super(c,NAME,null,1);try{setWriteAheadLoggingEnabled(true);}catch(Throwable ignored){}}
  @Override public void onCreate(SQLiteDatabase d){
    d.execSQL("CREATE TABLE points(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,acc REAL,alt REAL,speed REAL,bearing REAL,provider TEXT,sats_used INTEGER,sats_visible INTEGER,internet INTEGER,pressure REAL,accel REAL,gyro REAL,mag REAL,steps REAL,mode TEXT,confidence REAL,scores TEXT,source TEXT DEFAULT 'raw')");
    d.execSQL("CREATE INDEX points_ts ON points(ts)");
    d.execSQL("CREATE TABLE evidence(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER,pressure REAL,accel REAL,gyro REAL,mag REAL,steps REAL,sats_used INTEGER,sats_visible INTEGER,internet INTEGER)");
    d.execSQL("CREATE INDEX evidence_ts ON evidence(ts)");
    d.execSQL("CREATE TABLE gaps(id INTEGER PRIMARY KEY AUTOINCREMENT,start_ts INTEGER,end_ts INTEGER,start_lat REAL,start_lon REAL,end_lat REAL,end_lon REAL,status TEXT,mode TEXT,confidence REAL,path_json TEXT,reason TEXT)");
    d.execSQL("CREATE INDEX gaps_status ON gaps(status)");
    d.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY,v TEXT)");
  }
  @Override public void onUpgrade(SQLiteDatabase d,int o,int n){}
  long addPoint(Point p){ContentValues v=new ContentValues();v.put("ts",p.ts);v.put("lat",p.lat);v.put("lon",p.lon);v.put("acc",p.acc);v.put("alt",p.alt);v.put("speed",p.speed);v.put("bearing",p.bearing);v.put("provider",p.provider);v.put("sats_used",p.satsUsed);v.put("sats_visible",p.satsVisible);v.put("internet",p.internet?1:0);v.put("pressure",p.pressure);v.put("accel",p.accel);v.put("gyro",p.gyro);v.put("mag",p.mag);v.put("steps",p.steps);v.put("mode",p.mode);v.put("confidence",p.confidence);v.put("scores",p.scores);v.put("source",p.source);return getWritableDatabase().insert("points",null,v);}
  void addEvidence(long ts,double pressure,double accel,double gyro,double mag,double steps,int used,int visible,boolean internet){ContentValues v=new ContentValues();v.put("ts",ts);v.put("pressure",pressure);v.put("accel",accel);v.put("gyro",gyro);v.put("mag",mag);v.put("steps",steps);v.put("sats_used",used);v.put("sats_visible",visible);v.put("internet",internet?1:0);getWritableDatabase().insert("evidence",null,v);}
  long addGap(Point a,Point b){Cursor c=getReadableDatabase().rawQuery("SELECT id FROM gaps WHERE start_ts=? AND end_ts=? LIMIT 1",new String[]{String.valueOf(a.ts),String.valueOf(b.ts)});try{if(c.moveToFirst())return c.getLong(0);}finally{c.close();}ContentValues v=new ContentValues();v.put("start_ts",a.ts);v.put("end_ts",b.ts);v.put("start_lat",a.lat);v.put("start_lon",a.lon);v.put("end_lat",b.lat);v.put("end_lon",b.lon);v.put("status","pending");v.put("mode","pending");v.put("confidence",0);v.put("path_json","");v.put("reason","En attente d'Internet et de contexte cartographique");return getWritableDatabase().insert("gaps",null,v);}
  void updateGap(long id,String status,String mode,double conf,String path,String reason){ContentValues v=new ContentValues();v.put("status",status);v.put("mode",mode);v.put("confidence",conf);v.put("path_json",path);v.put("reason",reason);getWritableDatabase().update("gaps",v,"id=?",new String[]{String.valueOf(id)});}
  Point last(){Cursor c=getReadableDatabase().rawQuery("SELECT "+PCOLS+" FROM points ORDER BY ts DESC LIMIT 1",null);try{return c.moveToFirst()?point(c):null;}finally{c.close();}}
  List<Point> points(long start,long end,int limit){Cursor c=getReadableDatabase().rawQuery("SELECT "+PCOLS+" FROM points WHERE ts>=? AND ts<? ORDER BY ts ASC LIMIT ?",new String[]{String.valueOf(start),String.valueOf(end),String.valueOf(limit)});List<Point> a=new ArrayList<>();try{while(c.moveToNext())a.add(point(c));}finally{c.close();}return a;}
  List<Point> pointsDesc(long start,long end,int limit){Cursor c=getReadableDatabase().rawQuery("SELECT "+PCOLS+" FROM points WHERE ts>=? AND ts<? ORDER BY ts DESC LIMIT ?",new String[]{String.valueOf(start),String.valueOf(end),String.valueOf(limit)});List<Point> a=new ArrayList<>();try{while(c.moveToNext())a.add(point(c));}finally{c.close();}return a;}
  List<Gap> gaps(long start,long end){Cursor c=getReadableDatabase().rawQuery("SELECT id,start_ts,end_ts,start_lat,start_lon,end_lat,end_lon,status,mode,confidence,path_json,reason FROM gaps WHERE start_ts>=? AND start_ts<? ORDER BY start_ts ASC",new String[]{String.valueOf(start),String.valueOf(end)});List<Gap>a=new ArrayList<>();try{while(c.moveToNext())a.add(gap(c));}finally{c.close();}return a;}
  List<Gap> pending(int limit){Cursor c=getReadableDatabase().rawQuery("SELECT id,start_ts,end_ts,start_lat,start_lon,end_lat,end_lon,status,mode,confidence,path_json,reason FROM gaps WHERE status='pending' ORDER BY start_ts ASC LIMIT ?",new String[]{String.valueOf(limit)});List<Gap>a=new ArrayList<>();try{while(c.moveToNext())a.add(gap(c));}finally{c.close();}return a;}
  long count(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM points",null);try{return c.moveToFirst()?c.getLong(0):0;}finally{c.close();}}
  int pendingCount(){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM gaps WHERE status='pending'",null);try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
  void setMeta(String k,String v){ContentValues c=new ContentValues();c.put("k",k);c.put("v",v==null?"":v);getWritableDatabase().insertWithOnConflict("meta",null,c,SQLiteDatabase.CONFLICT_REPLACE);}
  String getMeta(String k,String def){Cursor c=getReadableDatabase().rawQuery("SELECT v FROM meta WHERE k=?",new String[]{k});try{return c.moveToFirst()?c.getString(0):def;}finally{c.close();}}
  long bytes(Context ctx){File f=ctx.getDatabasePath(NAME);long n=f.exists()?f.length():0;File w=new File(f.getPath()+"-wal");if(w.exists())n+=w.length();return n;}
  static final String PCOLS="id,ts,lat,lon,acc,alt,speed,bearing,provider,sats_used,sats_visible,internet,pressure,accel,gyro,mag,steps,mode,confidence,scores,source";
  static Point point(Cursor c){Point p=new Point();int i=0;p.id=c.getLong(i++);p.ts=c.getLong(i++);p.lat=c.getDouble(i++);p.lon=c.getDouble(i++);p.acc=c.getDouble(i++);p.alt=c.getDouble(i++);p.speed=c.getDouble(i++);p.bearing=c.getDouble(i++);p.provider=c.getString(i++);p.satsUsed=c.getInt(i++);p.satsVisible=c.getInt(i++);p.internet=c.getInt(i++)!=0;p.pressure=c.getDouble(i++);p.accel=c.getDouble(i++);p.gyro=c.getDouble(i++);p.mag=c.getDouble(i++);p.steps=c.getDouble(i++);p.mode=c.getString(i++);p.confidence=c.getDouble(i++);p.scores=c.getString(i++);p.source=c.getString(i++);return p;}
  static Gap gap(Cursor c){Gap g=new Gap();int i=0;g.id=c.getLong(i++);g.startTs=c.getLong(i++);g.endTs=c.getLong(i++);g.startLat=c.getDouble(i++);g.startLon=c.getDouble(i++);g.endLat=c.getDouble(i++);g.endLon=c.getDouble(i++);g.status=c.getString(i++);g.mode=c.getString(i++);g.confidence=c.getDouble(i++);g.pathJson=c.getString(i++);g.reason=c.getString(i++);return g;}
  static final class Point {long id,ts;double lat,lon,acc,alt,speed,bearing,pressure,accel,gyro,mag,steps,confidence;String provider="",mode="unknown",scores="{}",source="raw";int satsUsed,satsVisible;boolean internet;}
  static final class Gap {long id,startTs,endTs;double startLat,startLon,endLat,endLon,confidence;String status="pending",mode="pending",pathJson="",reason="";}
}
