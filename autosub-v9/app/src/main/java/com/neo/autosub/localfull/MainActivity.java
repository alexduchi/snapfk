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
import com.google.mlkit.nl.translate.*;

import androidx.annotation.OptIn;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.transformer.*;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends Activity {
    static { System.loadLibrary("autosub_native"); }
    public static native String nativeTranscribe(String modelPath,float[] samples);

    private static final int PICK_VIDEO=44;
    private static final int REQ_VIDEO_PERMISSION=45;
    private Uri videoUri;
    private VideoView preview;
    private TextView status, detected, selected;
    private Spinner targetSpinner;
    private Button process;
    private ProgressBar busy;
    private volatile boolean packTickerRunning=false;
    private final ArrayList<SubtitleOverlay.Segment> segments=new ArrayList<>();

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
        final Uri uri; final String name; final long duration;
        VideoEntry(Uri u,String n,long d){uri=u;name=n;duration=d;}
        @Override public String toString(){return name+"   •   "+formatDuration(duration);}
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
        new Handler(Looper.getMainLooper()).postDelayed(this::requestVideoPermissionIfNeeded,350);
    }

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.WHITE);return t;}

    private void buildUi(){
        ScrollView sc=new ScrollView(this); sc.setBackgroundColor(Color.rgb(9,14,28));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(28));sc.addView(root);
        TextView title=txt("AutoSub Local V10",28);title.setTypeface(null,1);root.addView(title);
        TextView d=txt("Toutes tes vidéos → détection locale → traduction → nouvelle vidéo sous-titrée",14);d.setTextColor(0xffaeb8d0);root.addView(d);
        preview=new VideoView(this); LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(230));vp.setMargins(0,dp(15),0,dp(10));root.addView(preview,vp);preview.setBackgroundColor(Color.BLACK);

        Button choose=new Button(this);choose.setText("Choisir parmi toutes mes vidéos");root.addView(choose,new LinearLayout.LayoutParams(-1,-2));choose.setOnClickListener(v->pickVideo());
        Button browse=new Button(this);browse.setText("Parcourir tous les fichiers vidéo");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,dp(6),0,0);root.addView(browse,bp);browse.setOnClickListener(v->openDocumentPicker());

        selected=txt("Aucune vidéo sélectionnée",14);selected.setPadding(0,dp(8),0,0);root.addView(selected);
        detected=txt("Langue détectée : —",14);detected.setPadding(0,dp(8),0,dp(6));root.addView(detected);
        targetSpinner=new Spinner(this);String[] names=new String[LANGS.length];for(int i=0;i<LANGS.length;i++)names[i]=LANGS[i][0];targetSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));root.addView(targetSpinner);

        process=new Button(this);process.setText("Créer la vidéo sous-titrée");LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.setMargins(0,dp(12),0,0);root.addView(process,pp);process.setOnClickListener(v->runPipeline());
        busy=new ProgressBar(this);busy.setIndeterminate(true);busy.setVisibility(View.GONE);LinearLayout.LayoutParams pr=new LinearLayout.LayoutParams(-2,-2);pr.gravity=Gravity.CENTER_HORIZONTAL;pr.setMargins(0,dp(8),0,0);root.addView(busy,pr);
        status=txt("Prêt. Autorise l'accès aux vidéos pour afficher toute ta bibliothèque.",14);status.setTextColor(0xff9fdcbf);status.setPadding(0,dp(10),0,0);root.addView(status);
        setContentView(sc);
    }

    private boolean hasFullVideoPermission(){
        if(Build.VERSION.SDK_INT>=33) return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;
        if(Build.VERSION.SDK_INT>=23) return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        return true;
    }

    private void requestVideoPermissionIfNeeded(){
        if(hasFullVideoPermission()) return;
        if(Build.VERSION.SDK_INT>=33){
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO},REQ_VIDEO_PERMISSION);
        }else if(Build.VERSION.SDK_INT>=23){
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_VIDEO_PERMISSION);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_VIDEO_PERMISSION){
            if(hasFullVideoPermission()) status.setText("Accès aux vidéos autorisé. Je peux afficher toute la bibliothèque MediaStore.");
            else {
                status.setText("Accès complet non accordé. Tu peux quand même utiliser « Parcourir tous les fichiers vidéo ».");
                Toast.makeText(this,"Pour voir toutes les vidéos directement dans AutoSub, autorise l'accès complet aux vidéos.",Toast.LENGTH_LONG).show();
            }
        }
    }

    private void pickVideo(){
        if(!hasFullVideoPermission()){
            requestVideoPermissionIfNeeded();
            openDocumentPicker();
            return;
        }
        showAllVideosPicker();
    }

    private void showAllVideosPicker(){
        final ArrayList<VideoEntry> entries=new ArrayList<>();
        String[] projection={MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.DURATION};
        try(Cursor c=getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,projection,null,null,MediaStore.Video.Media.DATE_ADDED+" DESC")){
            if(c!=null){
                int idCol=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int durCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                while(c.moveToNext()){
                    long id=c.getLong(idCol);String name=c.getString(nameCol);long dur=c.getLong(durCol);
                    Uri u=ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,id);
                    entries.add(new VideoEntry(u,name==null?("Vidéo "+id):name,dur));
                }
            }
        }catch(Exception e){status.setText("Impossible de lire MediaStore : "+e.getMessage());openDocumentPicker();return;}

        if(entries.isEmpty()){Toast.makeText(this,"Aucune vidéo trouvée dans MediaStore. Ouverture du navigateur de fichiers.",Toast.LENGTH_LONG).show();openDocumentPicker();return;}

        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),0);
        EditText search=new EditText(this);search.setHint("Rechercher une vidéo…");box.addView(search,new LinearLayout.LayoutParams(-1,-2));
        ListView list=new ListView(this);final ArrayAdapter<VideoEntry> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>(entries));list.setAdapter(adapter);box.addView(list,new LinearLayout.LayoutParams(-1,dp(440)));
        TextView count=txt(entries.size()+" vidéo(s) trouvée(s)",12);count.setTextColor(0xffaeb8d0);count.setPadding(0,dp(6),0,dp(6));box.addView(count);

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Toutes mes vidéos").setView(box).setNegativeButton("Fermer",null).setNeutralButton("Fichiers",(d,w)->openDocumentPicker()).create();
        list.setOnItemClickListener((p,v,pos,id)->{VideoEntry e=adapter.getItem(pos);if(e!=null){dialog.dismiss();selectVideo(e.uri,e.name,false);}});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){adapter.getFilter().filter(s);} public void afterTextChanged(Editable e){}});
        dialog.show();
    }

    private void openDocumentPicker(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(i,PICK_VIDEO);}catch(Exception e){Toast.makeText(this,"Aucun sélecteur de fichiers disponible",Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);
        if(r==PICK_VIDEO&&c==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri u=data.getData();
            try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            selectVideo(u,getDisplayName(u),true);
        }
    }

    private void selectVideo(Uri u,String name,boolean fromDocument){
        videoUri=u;selected.setText("Vidéo sélectionnée : "+(name==null?u.getLastPathSegment():name));
        preview.setVideoURI(videoUri);preview.setMediaController(new MediaController(this));preview.start();segments.clear();detected.setText("Langue détectée : —");status.setText("Vidéo chargée. Choisis la langue puis appuie sur Créer.");
    }

    private String getDisplayName(Uri u){
        try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.MediaColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}
        return u.getLastPathSegment();
    }

    private static String formatDuration(long ms){long total=Math.max(0,ms/1000);long h=total/3600,m=(total%3600)/60,s=total%60;return h>0?String.format(Locale.ROOT,"%d:%02d:%02d",h,m,s):String.format(Locale.ROOT,"%02d:%02d",m,s);}

    private void setBusy(boolean on){runOnUiThread(()->busy.setVisibility(on?View.VISIBLE:View.GONE));}

    private void runPipeline(){
        if(videoUri==null){Toast.makeText(this,"Choisis une vidéo d'abord.",Toast.LENGTH_LONG).show();return;}
        process.setEnabled(false);setBusy(true);status.setText("Extraction audio locale…");
        final int targetIndex=targetSpinner.getSelectedItemPosition();
        new Thread(()->{
            try{
                float[] audio=AudioDecoder.decode16kMono(this,videoUri);
                runOnUiThread(()->status.setText("Whisper local : transcription + détection de langue…"));
                File model=ensureModel();
                String json=nativeTranscribe(model.getAbsolutePath(),audio);
                JSONObject root=new JSONObject(json);if(root.has("error"))throw new Exception(root.getString("error"));
                String src=root.optString("language","en");JSONArray arr=root.getJSONArray("segments");ArrayList<SubtitleOverlay.Segment> raw=new ArrayList<>();
                for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String text=o.optString("text").trim();if(!text.isEmpty())raw.add(new SubtitleOverlay.Segment(o.getDouble("start"),o.getDouble("end"),text));}
                runOnUiThread(()->detected.setText("Langue détectée : "+src));
                String target=LANGS[targetIndex][1];
                ArrayList<SubtitleOverlay.Segment> done=(target.equals("same")||src.equals(target))?raw:translateLocal(src,target,raw);
                synchronized(segments){segments.clear();segments.addAll(done);} runOnUiThread(()->startExport(done));
            }catch(Exception e){packTickerRunning=false;setBusy(false);runOnUiThread(()->{process.setEnabled(true);status.setText("Erreur : "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));});}
        }).start();
    }

    private File ensureModel() throws Exception{
        File f=new File(getFilesDir(),"ggml-tiny.bin");if(f.exists()&&f.length()>50_000_000)return f;
        runOnUiThread(()->status.setText("Préparation du modèle Whisper local (une seule fois)…"));
        try(InputStream in=getAssets().open("ggml-tiny.bin");OutputStream out=new FileOutputStream(f)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return f;
    }

    private void startPackTicker(String src,String dst,long started){
        packTickerRunning=true;
        new Thread(()->{
            while(packTickerRunning){
                long sec=(System.currentTimeMillis()-started)/1000;
                runOnUiThread(()->{if(packTickerRunning)status.setText("Téléchargement du pack "+src+" → "+dst+" : "+sec+" s\nPremière utilisation seulement. L'app n'est pas bloquée : le modèle se télécharge en arrière-plan.");});
                try{Thread.sleep(1000);}catch(InterruptedException ignored){break;}
            }
        }).start();
    }

    private ArrayList<SubtitleOverlay.Segment> translateLocal(String src,String dst,ArrayList<SubtitleOverlay.Segment> raw)throws Exception{
        String source=TranslateLanguage.fromLanguageTag(src);String target=TranslateLanguage.fromLanguageTag(dst);
        if(source==null||target==null) throw new Exception("Traduction locale non disponible pour "+src+" → "+dst);
        Translator tr=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build());
        long started=System.currentTimeMillis();startPackTicker(src,dst,started);
        try{
            Tasks.await(tr.downloadModelIfNeeded(new DownloadConditions.Builder().build()),4,TimeUnit.MINUTES);
        }catch(Exception e){
            packTickerRunning=false;tr.close();
            throw new Exception("Le pack de langue n'a pas fini de se charger. Vérifie Internet puis réessaie. S'il est déjà en cache, le prochain essai sera immédiat.");
        }
        packTickerRunning=false;
        runOnUiThread(()->status.setText("Pack de langue prêt. Traduction locale…"));

        ArrayList<SubtitleOverlay.Segment> out=new ArrayList<>();
        final int chunk=6;
        for(int base=0;base<raw.size();base+=chunk){
            int end=Math.min(base+chunk,raw.size());ArrayList<Task<String>> jobs=new ArrayList<>();
            for(int j=base;j<end;j++)jobs.add(tr.translate(raw.get(j).text));
            List<String> texts=Tasks.await(Tasks.whenAllSuccess(jobs));
            for(int j=base;j<end;j++){SubtitleOverlay.Segment s=raw.get(j);out.add(new SubtitleOverlay.Segment(s.start,s.end,texts.get(j-base)));}
            int done=end;runOnUiThread(()->status.setText("Traduction locale : "+done+" / "+raw.size()));
        }
        tr.close();return out;
    }

    private void startExport(ArrayList<SubtitleOverlay.Segment> subs){
        try{
            status.setText("Création de la nouvelle vidéo avec sous-titres…");
            File out=new File(getExternalFilesDir(null),"AutoSub_"+System.currentTimeMillis()+".mp4");
            SubtitleOverlay overlay=new SubtitleOverlay(subs);OverlayEffect overlayEffect=new OverlayEffect(Collections.singletonList(overlay));
            Effects effects=new Effects(Collections.emptyList(),Collections.singletonList(overlayEffect));
            MediaItem media=MediaItem.fromUri(videoUri);EditedMediaItem edited=new EditedMediaItem.Builder(media).setEffects(effects).build();
            Transformer transformer=new Transformer.Builder(this).addListener(new Transformer.Listener(){
                @Override public void onCompleted(Composition composition,ExportResult result){try{Uri saved=saveToGallery(out);status.setText("Terminé. Nouvelle vidéo enregistrée dans la galerie : "+saved);Toast.makeText(MainActivity.this,"Vidéo sous-titrée enregistrée",Toast.LENGTH_LONG).show();}catch(Exception e){status.setText("Export terminé mais copie galerie impossible : "+e.getMessage());}process.setEnabled(true);setBusy(false);}
                @Override public void onError(Composition composition,ExportResult result,ExportException e){process.setEnabled(true);setBusy(false);status.setText("Erreur export vidéo : "+e.getMessage());}
            }).build();transformer.start(edited,out.getAbsolutePath());
        }catch(Exception e){process.setEnabled(true);setBusy(false);status.setText("Erreur export : "+e.getMessage());}
    }

    private Uri saveToGallery(File src)throws Exception{
        ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.DISPLAY_NAME,"AutoSub_"+System.currentTimeMillis()+".mp4");v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
        if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/AutoSub");v.put(MediaStore.Video.Media.IS_PENDING,1);}
        Uri u=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("MediaStore indisponible");
        try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}src.delete();return u;
    }
}
