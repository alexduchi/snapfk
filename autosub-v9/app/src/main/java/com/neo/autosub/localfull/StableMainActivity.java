package com.neo.autosub.localfull;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import android.text.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.json.*;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.*;

import androidx.annotation.OptIn;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.transformer.*;

@OptIn(markerClass = UnstableApi.class)
public class StableMainActivity extends Activity {
    private static final int PICK_VIDEO=44;
    private static final int REQ_VIDEO_PERMISSION=45;
    private static final int REQ_NOTIFICATIONS=46;

    private Uri videoUri;
    private VideoView preview;
    private TextView status, detected, selected;
    private Spinner targetSpinner;
    private Button process;
    private ProgressBar busy;
    private volatile boolean packTickerRunning=false;

    private static final String[][] LANGS={
        {"Même langue que la vidéo (sans traduction)","same"},
        {"Français","fr"},{"English","en"},{"Español","es"},{"Deutsch","de"},{"Italiano","it"},{"Português","pt"},
        {"Nederlands","nl"},{"Polski","pl"},{"Русский","ru"},{"Українська","uk"},{"中文","zh"},{"日本語","ja"},{"한국어","ko"},
        {"العربية","ar"},{"हिन्दी","hi"},{"বাংলা","bn"},{"Türkçe","tr"},{"Tiếng Việt","vi"},{"ไทย","th"},{"Bahasa Indonesia","id"},
        {"Bahasa Melayu","ms"},{"فارسی","fa"},{"עברית","he"},{"Ελληνικά","el"},{"Čeština","cs"},{"Slovenčina","sk"},{"Magyar","hu"},
        {"Română","ro"},{"Български","bg"},{"Hrvatski","hr"},{"Slovenščina","sl"},{"Dansk","da"},{"Svenska","sv"},{"Norsk","no"},
        {"Suomi","fi"},{"Eesti","et"},{"Latviešu","lv"},{"Lietuvių","lt"},{"Català","ca"},{"Galego","gl"},{"Afrikaans","af"},
        {"Kiswahili","sw"},{"Filipino","tl"},{"ქართული","ka"},{"Shqip","sq"},{"Македонски","mk"},{"Íslenska","is"},{"Gaeilge","ga"},
        {"தமிழ்","ta"},{"తెలుగు","te"},{"ಕನ್ನಡ","kn"},{"मराठी","mr"},{"ગુજરાતી","gu"},{"اردو","ur"}
    };

    private static class VideoEntry {
        final Uri uri; final String name; final long duration; final long dateAdded;
        VideoEntry(Uri u,String n,long d,long date){uri=u;name=n;duration=d;dateAdded=date;}
        @Override public String toString(){return name+"   •   "+formatDuration(duration);}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
        new Handler(Looper.getMainLooper()).postDelayed(this::requestVideoPermissionIfNeeded,300);
        new Handler(Looper.getMainLooper()).postDelayed(this::requestNotificationPermissionIfNeeded,700);
        updateGuard("AutoSub actif en arrière-plan. Tu peux éteindre l'écran ou changer d'application.");
    }

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.WHITE);return t;}

    private void buildUi(){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(Color.rgb(9,14,28));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(28));sc.addView(root);
        TextView title=txt("AutoSub Local V11",28);title.setTypeface(null,1);root.addView(title);
        TextView d=txt("Accès complet vidéos + traitement protégé en arrière-plan",14);d.setTextColor(0xffaeb8d0);root.addView(d);

        preview=new VideoView(this);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(230));vp.setMargins(0,dp(15),0,dp(10));root.addView(preview,vp);preview.setBackgroundColor(Color.BLACK);
        Button choose=new Button(this);choose.setText("Choisir parmi toutes mes vidéos");root.addView(choose,new LinearLayout.LayoutParams(-1,-2));choose.setOnClickListener(v->pickVideo());
        Button browse=new Button(this);browse.setText("Parcourir tous les fichiers vidéo");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,dp(6),0,0);root.addView(browse,bp);browse.setOnClickListener(v->openDocumentPicker());

        selected=txt("Aucune vidéo sélectionnée",14);selected.setPadding(0,dp(8),0,0);root.addView(selected);
        detected=txt("Langue détectée : —",14);detected.setPadding(0,dp(8),0,dp(6));root.addView(detected);
        targetSpinner=new Spinner(this);String[] names=new String[LANGS.length];for(int i=0;i<LANGS.length;i++)names[i]=LANGS[i][0];targetSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));root.addView(targetSpinner);

        process=new Button(this);process.setText("Créer la vidéo sous-titrée");LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.setMargins(0,dp(12),0,0);root.addView(process,pp);process.setOnClickListener(v->runPipeline());
        busy=new ProgressBar(this);busy.setIndeterminate(true);busy.setVisibility(View.GONE);LinearLayout.LayoutParams pr=new LinearLayout.LayoutParams(-2,-2);pr.gravity=Gravity.CENTER_HORIZONTAL;pr.setMargins(0,dp(8),0,0);root.addView(busy,pr);
        status=txt("Prêt. Le traitement continue même si AutoSub passe en arrière-plan.",14);status.setTextColor(0xff9fdcbf);status.setPadding(0,dp(10),0,0);root.addView(status);
        setContentView(sc);
    }

    private void requestNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
        }
    }

    private boolean hasFullVideoPermission(){
        if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;
        if(Build.VERSION.SDK_INT>=23)return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private void requestVideoPermissionIfNeeded(){
        if(hasFullVideoPermission())return;
        if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO},REQ_VIDEO_PERMISSION);
        else if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_VIDEO_PERMISSION);
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_VIDEO_PERMISSION){
            if(hasFullVideoPermission())status.setText("Accès complet aux vidéos autorisé.");
            else status.setText("Accès complet refusé : utilise « Parcourir tous les fichiers vidéo » ou autorise toutes les vidéos dans les réglages Android.");
        }
    }

    private void pickVideo(){
        if(!hasFullVideoPermission()){
            requestVideoPermissionIfNeeded();
            Toast.makeText(this,"Choisis « Autoriser toutes les vidéos » pour afficher toute la bibliothèque.",Toast.LENGTH_LONG).show();
            return;
        }
        showAllVideosPicker();
    }

    private void queryVolume(String volume,ArrayList<VideoEntry> out,HashSet<String> seen){
        Uri base=Build.VERSION.SDK_INT>=29?MediaStore.Video.Media.getContentUri(volume):MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] p={MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.DURATION,MediaStore.Video.Media.DATE_ADDED};
        try(Cursor c=getContentResolver().query(base,p,null,null,null)){
            if(c==null)return;
            int idCol=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int durCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            int dateCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
            while(c.moveToNext()){
                Uri u=ContentUris.withAppendedId(base,c.getLong(idCol));String key=u.toString();if(!seen.add(key))continue;
                String n=c.getString(nameCol);out.add(new VideoEntry(u,n==null?"Vidéo":n,c.getLong(durCol),c.getLong(dateCol)));
            }
        }catch(Exception ignored){}
    }

    private void showAllVideosPicker(){
        final ArrayList<VideoEntry> entries=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        if(Build.VERSION.SDK_INT>=29){for(String volume:MediaStore.getExternalVolumeNames(this))queryVolume(volume,entries,seen);}else queryVolume("external",entries,seen);
        entries.sort((a,b)->Long.compare(b.dateAdded,a.dateAdded));
        if(entries.isEmpty()){Toast.makeText(this,"Aucune vidéo indexée. Ouverture des fichiers.",Toast.LENGTH_LONG).show();openDocumentPicker();return;}

        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),0);
        EditText search=new EditText(this);search.setHint("Rechercher une vidéo…");box.addView(search,new LinearLayout.LayoutParams(-1,-2));
        ListView list=new ListView(this);final ArrayAdapter<VideoEntry> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>(entries));list.setAdapter(adapter);box.addView(list,new LinearLayout.LayoutParams(-1,dp(440)));
        TextView count=txt(entries.size()+" vidéo(s) trouvée(s) sur tous les volumes",12);count.setTextColor(0xffaeb8d0);box.addView(count);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Toutes mes vidéos").setView(box).setNegativeButton("Fermer",null).setNeutralButton("Fichiers",(d,w)->openDocumentPicker()).create();
        list.setOnItemClickListener((p,v,pos,id)->{VideoEntry e=adapter.getItem(pos);if(e!=null){dialog.dismiss();selectVideo(e.uri,e.name);}});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){adapter.getFilter().filter(s);}public void afterTextChanged(Editable e){}});
        dialog.show();
    }

    private void openDocumentPicker(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_VIDEO);
    }

    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if(r==PICK_VIDEO&&c==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            selectVideo(u,getDisplayName(u));
        }
    }

    private void selectVideo(Uri u,String name){
        videoUri=u;selected.setText("Vidéo sélectionnée : "+(name==null?u.getLastPathSegment():name));
        preview.setVideoURI(u);preview.setMediaController(new MediaController(this));preview.start();detected.setText("Langue détectée : —");status.setText("Vidéo chargée. Choisis la langue puis appuie sur Créer.");
    }

    private String getDisplayName(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.MediaColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return u.getLastPathSegment();}
    private static String formatDuration(long ms){long t=Math.max(0,ms/1000),h=t/3600,m=(t%3600)/60,s=t%60;return h>0?String.format(Locale.ROOT,"%d:%02d:%02d",h,m,s):String.format(Locale.ROOT,"%02d:%02d",m,s);}
    private void setBusy(boolean on){runOnUiThread(()->busy.setVisibility(on?View.VISIBLE:View.GONE));}

    private void updateGuard(String text){
        Intent i=new Intent(this,ProcessingKeepAliveService.class);i.putExtra(ProcessingKeepAliveService.EXTRA_TEXT,text);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
    }

    private void runPipeline(){
        if(videoUri==null){Toast.makeText(this,"Choisis une vidéo d'abord.",Toast.LENGTH_LONG).show();return;}
        process.setEnabled(false);setBusy(true);status.setText("Extraction audio locale…");updateGuard("Extraction audio locale…");
        final int targetIndex=targetSpinner.getSelectedItemPosition();
        new Thread(()->{
            try{
                float[] audio=AudioDecoder.decode16kMono(this,videoUri);
                uiStatus("Whisper local : transcription + détection de langue…");updateGuard("Whisper local en cours…");
                File model=ensureModel();
                String json=MainActivity.nativeTranscribe(model.getAbsolutePath(),audio);
                JSONObject root=new JSONObject(json);if(root.has("error"))throw new Exception(root.getString("error"));
                String src=root.optString("language","en");JSONArray arr=root.getJSONArray("segments");ArrayList<SubtitleOverlay.Segment> raw=new ArrayList<>();
                for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String text=o.optString("text").trim();if(!text.isEmpty())raw.add(new SubtitleOverlay.Segment(o.getDouble("start"),o.getDouble("end"),text));}
                runOnUiThread(()->detected.setText("Langue détectée : "+src));
                String target=LANGS[targetIndex][1];ArrayList<SubtitleOverlay.Segment> done=(target.equals("same")||src.equals(target))?raw:translateLocalStable(src,target,raw);
                runOnUiThread(()->startExport(done));
            }catch(Exception e){packTickerRunning=false;setBusy(false);String msg=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();uiStatus("Erreur : "+msg);updateGuard("Erreur AutoSub : "+msg);runOnUiThread(()->process.setEnabled(true));}
        }).start();
    }

    private void uiStatus(String s){runOnUiThread(()->status.setText(s));}

    private File ensureModel()throws Exception{
        File f=new File(getFilesDir(),"ggml-tiny.bin");if(f.exists()&&f.length()>50_000_000)return f;
        uiStatus("Préparation du modèle Whisper local…");updateGuard("Préparation de Whisper local…");
        try(InputStream in=getAssets().open("ggml-tiny.bin");OutputStream out=new FileOutputStream(f)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return f;
    }

    private void startPackTicker(String src,String dst,long started){
        packTickerRunning=true;new Thread(()->{while(packTickerRunning){long sec=(System.currentTimeMillis()-started)/1000;String msg="Pack de traduction "+src+" → "+dst+" : "+sec+" s\nTéléchargement protégé en arrière-plan, Wi‑Fi ou données mobiles.";uiStatus(msg);if(sec%5==0)updateGuard("Téléchargement du pack "+src+" → "+dst+" : "+sec+" s");try{Thread.sleep(1000);}catch(InterruptedException e){break;}}}).start();
    }

    private void downloadModelWithRetry(RemoteModelManager mgr,TranslateRemoteModel model,DownloadConditions conditions,String label)throws Exception{
        Exception last=null;
        for(int attempt=1;attempt<=3;attempt++){
            try{
                uiStatus("Pack "+label+" : tentative "+attempt+" / 3…");updateGuard("Pack "+label+" : tentative "+attempt+" / 3");
                Tasks.await(mgr.download(model,conditions),3,TimeUnit.MINUTES);return;
            }catch(Exception e){last=e;if(attempt<3)Thread.sleep(attempt*3000L);}
        }
        throw new Exception("Téléchargement du modèle "+label+" impossible après 3 tentatives"+(last!=null&&last.getMessage()!=null?" : "+last.getMessage():""));
    }

    private ArrayList<SubtitleOverlay.Segment> translateLocalStable(String src,String dst,ArrayList<SubtitleOverlay.Segment> raw)throws Exception{
        String source=TranslateLanguage.fromLanguageTag(src),target=TranslateLanguage.fromLanguageTag(dst);
        if(source==null||target==null)throw new Exception("Traduction locale non disponible pour "+src+" → "+dst);
        DownloadConditions conditions=new DownloadConditions.Builder().build();
        RemoteModelManager mgr=RemoteModelManager.getInstance();
        TranslateRemoteModel srcModel=new TranslateRemoteModel.Builder(source).build();
        TranslateRemoteModel dstModel=new TranslateRemoteModel.Builder(target).build();
        long started=System.currentTimeMillis();startPackTicker(src,dst,started);
        try{
            downloadModelWithRetry(mgr,srcModel,conditions,src);
            if(!src.equals(dst))downloadModelWithRetry(mgr,dstModel,conditions,dst);
        }finally{packTickerRunning=false;}

        Translator tr=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build());
        try{
            Tasks.await(tr.downloadModelIfNeeded(conditions),2,TimeUnit.MINUTES);
            uiStatus("Packs prêts. Traduction locale…");updateGuard("Packs prêts. Traduction locale…");
            ArrayList<SubtitleOverlay.Segment> out=new ArrayList<>();final int chunk=6;
            for(int base=0;base<raw.size();base+=chunk){int end=Math.min(base+chunk,raw.size());ArrayList<Task<String>> jobs=new ArrayList<>();for(int j=base;j<end;j++)jobs.add(tr.translate(raw.get(j).text));List<String> texts=Tasks.await(Tasks.whenAllSuccess(jobs));for(int j=base;j<end;j++){SubtitleOverlay.Segment s=raw.get(j);out.add(new SubtitleOverlay.Segment(s.start,s.end,texts.get(j-base)));}int done=end;uiStatus("Traduction locale : "+done+" / "+raw.size());if(done%30==0||done==raw.size())updateGuard("Traduction locale : "+done+" / "+raw.size());}
            return out;
        }finally{tr.close();}
    }

    private void startExport(ArrayList<SubtitleOverlay.Segment> subs){
        try{
            status.setText("Création de la nouvelle vidéo avec sous-titres…");updateGuard("Export de la vidéo sous-titrée…");
            File out=new File(getExternalFilesDir(null),"AutoSub_"+System.currentTimeMillis()+".mp4");
            OverlayEffect overlayEffect=new OverlayEffect(Collections.singletonList(new SubtitleOverlay(subs)));Effects effects=new Effects(Collections.emptyList(),Collections.singletonList(overlayEffect));
            EditedMediaItem edited=new EditedMediaItem.Builder(MediaItem.fromUri(videoUri)).setEffects(effects).build();
            Transformer transformer=new Transformer.Builder(this).addListener(new Transformer.Listener(){
                @Override public void onCompleted(Composition composition,ExportResult result){try{Uri saved=saveToGallery(out);status.setText("Terminé. Nouvelle vidéo enregistrée dans la galerie : "+saved);updateGuard("Terminé : vidéo sous-titrée enregistrée.");Toast.makeText(StableMainActivity.this,"Vidéo sous-titrée enregistrée",Toast.LENGTH_LONG).show();}catch(Exception e){status.setText("Export terminé mais copie galerie impossible : "+e.getMessage());updateGuard("Export terminé, copie galerie impossible.");}process.setEnabled(true);setBusy(false);}
                @Override public void onError(Composition composition,ExportResult result,ExportException e){process.setEnabled(true);setBusy(false);status.setText("Erreur export vidéo : "+e.getMessage());updateGuard("Erreur export vidéo.");}
            }).build();transformer.start(edited,out.getAbsolutePath());
        }catch(Exception e){process.setEnabled(true);setBusy(false);status.setText("Erreur export : "+e.getMessage());updateGuard("Erreur export vidéo.");}
    }

    private Uri saveToGallery(File src)throws Exception{
        ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.DISPLAY_NAME,"AutoSub_"+System.currentTimeMillis()+".mp4");v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/AutoSub");v.put(MediaStore.Video.Media.IS_PENDING,1);}Uri u=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("MediaStore indisponible");try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}src.delete();return u;
    }
}
