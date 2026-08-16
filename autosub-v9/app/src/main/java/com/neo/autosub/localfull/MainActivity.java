package com.neo.autosub.localfull;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

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
    private Uri videoUri;
    private VideoView preview;
    private TextView status, detected, selected;
    private Spinner targetSpinner;
    private Button process;
    private final ArrayList<SubtitleOverlay.Segment> segments=new ArrayList<>();

    private static final String[][] LANGS={
        {"Français","fr"},{"English","en"},{"Español","es"},{"Deutsch","de"},{"Italiano","it"},{"Português","pt"},
        {"Nederlands","nl"},{"Polski","pl"},{"Русский","ru"},{"Українська","uk"},{"中文","zh"},{"日本語","ja"},{"한국어","ko"},
        {"العربية","ar"},{"हिन्दी","hi"},{"বাংলা","bn"},{"Türkçe","tr"},{"Tiếng Việt","vi"},{"ไทย","th"},{"Bahasa Indonesia","id"},
        {"Bahasa Melayu","ms"},{"فارسی","fa"},{"עברית","he"},{"Ελληνικά","el"},{"Čeština","cs"},{"Slovenčina","sk"},{"Magyar","hu"},
        {"Română","ro"},{"Български","bg"},{"Hrvatski","hr"},{"Slovenščina","sl"},{"Dansk","da"},{"Svenska","sv"},{"Norsk","no"},
        {"Suomi","fi"},{"Eesti","et"},{"Latviešu","lv"},{"Lietuvių","lt"},{"Català","ca"},{"Galego","gl"},{"Afrikaans","af"},
        {"Kiswahili","sw"},{"Filipino","tl"},{"ქართული","ka"},{"Shqip","sq"},{"Македонски","mk"},{"Íslenska","is"},{"Gaeilge","ga"},
        {"தமிழ்","ta"},{"తెలుగు","te"},{"ಕನ್ನಡ","kn"},{"मराठी","mr"},{"ગુજરાતી","gu"},{"اردو","ur"}
    };

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.WHITE);return t;}

    private void buildUi(){
        ScrollView sc=new ScrollView(this); sc.setBackgroundColor(Color.rgb(9,14,28));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(28));sc.addView(root);
        TextView title=txt("AutoSub Local V9",28);title.setTypeface(null,1);root.addView(title);
        TextView d=txt("Choisis une vidéo → détection locale → traduction → nouvelle vidéo sous-titrée",14);d.setTextColor(0xffaeb8d0);root.addView(d);
        preview=new VideoView(this); LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(230));vp.setMargins(0,dp(15),0,dp(10));root.addView(preview,vp);preview.setBackgroundColor(Color.BLACK);
        Button choose=new Button(this);choose.setText("Choisir une vidéo");root.addView(choose,new LinearLayout.LayoutParams(-1,-2));choose.setOnClickListener(v->pickVideo());
        selected=txt("Aucune vidéo sélectionnée",14);selected.setPadding(0,dp(8),0,0);root.addView(selected);
        detected=txt("Langue détectée : —",14);detected.setPadding(0,dp(8),0,dp(6));root.addView(detected);
        targetSpinner=new Spinner(this);String[] names=new String[LANGS.length];for(int i=0;i<LANGS.length;i++)names[i]=LANGS[i][0];targetSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));root.addView(targetSpinner);
        process=new Button(this);process.setText("Créer la vidéo sous-titrée");LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.setMargins(0,dp(12),0,0);root.addView(process,pp);process.setOnClickListener(v->runPipeline());
        status=txt("Prêt. Tout le traitement audio/vidéo est local.",14);status.setTextColor(0xff9fdcbf);status.setPadding(0,dp(10),0,0);root.addView(status);
        setContentView(sc);
    }

    private void pickVideo(){
        Intent i;
        if(Build.VERSION.SDK_INT>=33){
            i=new Intent(MediaStore.ACTION_PICK_IMAGES);i.setType("video/*");
        } else { i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*"); }
        try{startActivityForResult(i,PICK_VIDEO);}catch(Exception e){Intent f=new Intent(Intent.ACTION_OPEN_DOCUMENT);f.addCategory(Intent.CATEGORY_OPENABLE);f.setType("video/*");startActivityForResult(f,PICK_VIDEO);}
    }
    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==PICK_VIDEO&&c==RESULT_OK&&data!=null&&data.getData()!=null){
        videoUri=data.getData();try{getContentResolver().takePersistableUriPermission(videoUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        selected.setText("Vidéo sélectionnée : "+videoUri.getLastPathSegment());preview.setVideoURI(videoUri);preview.setMediaController(new MediaController(this));preview.start();segments.clear();detected.setText("Langue détectée : —");status.setText("Vidéo chargée. Appuie sur Créer.");
    }}

    private void runPipeline(){
        if(videoUri==null){Toast.makeText(this,"Choisis une vidéo d'abord.",Toast.LENGTH_LONG).show();return;}
        process.setEnabled(false);status.setText("Extraction audio locale…");
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
                ArrayList<SubtitleOverlay.Segment> done=(src.equals(target)?raw:translateLocal(src,target,raw));
                synchronized(segments){segments.clear();segments.addAll(done);} runOnUiThread(()->startExport(done));
            }catch(Exception e){runOnUiThread(()->{process.setEnabled(true);status.setText("Erreur : "+e.getMessage());});}
        }).start();
    }

    private File ensureModel() throws Exception{
        File f=new File(getFilesDir(),"ggml-tiny.bin");if(f.exists()&&f.length()>50_000_000)return f;
        runOnUiThread(()->status.setText("Préparation du modèle Whisper local…"));
        try(InputStream in=getAssets().open("ggml-tiny.bin");OutputStream out=new FileOutputStream(f)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return f;
    }

    private ArrayList<SubtitleOverlay.Segment> translateLocal(String src,String dst,ArrayList<SubtitleOverlay.Segment> raw)throws Exception{
        String source=TranslateLanguage.fromLanguageTag(src);String target=TranslateLanguage.fromLanguageTag(dst);
        if(source==null||target==null) throw new Exception("Traduction locale non disponible pour "+src+" → "+dst);
        runOnUiThread(()->status.setText("Chargement du pack de traduction locale "+src+" → "+dst+"…"));
        Translator tr=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build());
        Tasks.await(tr.downloadModelIfNeeded(new DownloadConditions.Builder().build()));
        ArrayList<SubtitleOverlay.Segment> out=new ArrayList<>();int i=0;
        for(SubtitleOverlay.Segment s:raw){String t=Tasks.await(tr.translate(s.text));out.add(new SubtitleOverlay.Segment(s.start,s.end,t));int p=++i;runOnUiThread(()->status.setText("Traduction locale : "+p+" / "+raw.size()));}
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
                @Override public void onCompleted(Composition composition,ExportResult result){try{Uri saved=saveToGallery(out);status.setText("Terminé. Nouvelle vidéo enregistrée dans la galerie : "+saved);Toast.makeText(MainActivity.this,"Vidéo sous-titrée enregistrée",Toast.LENGTH_LONG).show();}catch(Exception e){status.setText("Export terminé mais copie galerie impossible : "+e.getMessage());}process.setEnabled(true);}
                @Override public void onError(Composition composition,ExportResult result,ExportException e){process.setEnabled(true);status.setText("Erreur export vidéo : "+e.getMessage());}
            }).build();transformer.start(edited,out.getAbsolutePath());
        }catch(Exception e){process.setEnabled(true);status.setText("Erreur export : "+e.getMessage());}
    }

    private Uri saveToGallery(File src)throws Exception{
        ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.DISPLAY_NAME,"AutoSub_"+System.currentTimeMillis()+".mp4");v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
        if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/AutoSub");v.put(MediaStore.Video.Media.IS_PENDING,1);}
        Uri u=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("MediaStore indisponible");
        try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}src.delete();return u;
    }
}
