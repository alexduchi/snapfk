package com.neo.autosub.localfull;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
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
public class V14MainActivity extends Activity {
    private static final int PICK_VIDEO=44;
    private static final int REQ_VIDEO_PERMISSION=45;
    private static final int REQ_NOTIFICATIONS=46;

    private Uri videoUri;
    private FrameLayout previewFrame;
    private VideoView preview;
    private TextView subtitlePreview, status, detected, selected, sizeLabel, xLabel, yLabel;
    private Spinner targetSpinner;
    private Button process, exportButton, resetPositionButton;
    private ProgressBar busy;
    private SeekBar sizeSeek, xSeek, ySeek;
    private volatile boolean packTickerRunning=false;
    private final Handler previewHandler=new Handler(Looper.getMainLooper());
    private final ArrayList<SubtitleOverlay.Segment> preparedSubs=new ArrayList<>();
    private int videoWidth=1920, videoHeight=1080;
    private float subtitleScale=1.0f;
    private int subtitleXPercent=50;
    private int subtitleYPercent=84;

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
        updateGuard("AutoSub V16 actif en arrière-plan.");
    }

    @Override protected void onDestroy(){
        super.onDestroy();
        previewHandler.removeCallbacksAndMessages(null);
    }

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView txt(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.WHITE);return t;}

    private void buildUi(){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(Color.rgb(9,14,28));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(28));sc.addView(root);
        TextView title=txt("AutoSub Local V16",28);title.setTypeface(null,1);root.addView(title);
        TextView d=txt("Aperçu fidèle + sous-titres adaptatifs et réglables",14);d.setTextColor(0xffaeb8d0);root.addView(d);

        previewFrame=new FrameLayout(this);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(300));fp.setMargins(0,dp(15),0,dp(10));root.addView(previewFrame,fp);previewFrame.setBackgroundColor(Color.BLACK);
        preview=new VideoView(this);FrameLayout.LayoutParams videoLp=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);previewFrame.addView(preview,videoLp);
        subtitlePreview=txt("",20);subtitlePreview.setGravity(Gravity.CENTER);subtitlePreview.setTypeface(null,1);subtitlePreview.setTextColor(Color.WHITE);subtitlePreview.setShadowLayer(6f,0f,2f,Color.BLACK);subtitlePreview.setBackgroundColor(0xB8000000);subtitlePreview.setPadding(dp(10),dp(6),dp(10),dp(6));subtitlePreview.setMaxLines(4);subtitlePreview.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);sp.leftMargin=dp(8);sp.rightMargin=dp(8);previewFrame.addView(subtitlePreview,sp);

        Button choose=new Button(this);choose.setText("Choisir parmi toutes mes vidéos");root.addView(choose,new LinearLayout.LayoutParams(-1,-2));choose.setOnClickListener(v->pickVideo());
        Button browse=new Button(this);browse.setText("Parcourir tous les fichiers vidéo");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.setMargins(0,dp(6),0,0);root.addView(browse,bp);browse.setOnClickListener(v->openDocumentPicker());

        selected=txt("Aucune vidéo sélectionnée",14);selected.setPadding(0,dp(8),0,0);root.addView(selected);
        detected=txt("Langue détectée : —",14);detected.setPadding(0,dp(8),0,dp(6));root.addView(detected);
        targetSpinner=new Spinner(this);String[] names=new String[LANGS.length];for(int i=0;i<LANGS.length;i++)names[i]=LANGS[i][0];targetSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));root.addView(targetSpinner);

        process=new Button(this);process.setText("Générer les sous-titres et afficher l'aperçu");LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.setMargins(0,dp(12),0,0);root.addView(process,pp);process.setOnClickListener(v->runPipeline());

        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(0,dp(10),0,0);root.addView(controls,new LinearLayout.LayoutParams(-1,-2));

        sizeLabel=txt("Taille des sous-titres : 100 %",14);sizeLabel.setVisibility(View.GONE);controls.addView(sizeLabel);
        sizeSeek=new SeekBar(this);sizeSeek.setMax(100);sizeSeek.setProgress(50);sizeSeek.setVisibility(View.GONE);controls.addView(sizeSeek,new LinearLayout.LayoutParams(-1,-2));
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){subtitleScale=0.60f+(p/100f)*0.80f;sizeLabel.setText("Taille des sous-titres : "+Math.round(subtitleScale*100)+" %");applyPreviewLayout();}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });

        xLabel=txt("Position horizontale : 50 % (centre)",14);xLabel.setVisibility(View.GONE);controls.addView(xLabel);
        xSeek=new SeekBar(this);xSeek.setMax(100);xSeek.setProgress(50);xSeek.setVisibility(View.GONE);controls.addView(xSeek,new LinearLayout.LayoutParams(-1,-2));
        xSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){subtitleXPercent=p;xLabel.setText("Position horizontale : "+p+" %");applyPreviewPosition();}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });

        yLabel=txt("Position verticale : 84 % (vers le bas)",14);yLabel.setVisibility(View.GONE);controls.addView(yLabel);
        ySeek=new SeekBar(this);ySeek.setMax(100);ySeek.setProgress(84);ySeek.setVisibility(View.GONE);controls.addView(ySeek,new LinearLayout.LayoutParams(-1,-2));
        ySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){subtitleYPercent=p;yLabel.setText("Position verticale : "+p+" %");applyPreviewPosition();}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });

        resetPositionButton=new Button(this);resetPositionButton.setText("Réinitialiser la position");resetPositionButton.setVisibility(View.GONE);controls.addView(resetPositionButton,new LinearLayout.LayoutParams(-1,-2));
        resetPositionButton.setOnClickListener(v->{subtitleXPercent=50;subtitleYPercent=84;xSeek.setProgress(50);ySeek.setProgress(84);applyPreviewPosition();});

        exportButton=new Button(this);exportButton.setText("Exporter cette vidéo avec ces sous-titres");exportButton.setVisibility(View.GONE);controls.addView(exportButton,new LinearLayout.LayoutParams(-1,-2));exportButton.setOnClickListener(v->{if(!preparedSubs.isEmpty())startExport(new ArrayList<>(preparedSubs));});

        busy=new ProgressBar(this);busy.setIndeterminate(true);busy.setVisibility(View.GONE);LinearLayout.LayoutParams pr=new LinearLayout.LayoutParams(-2,-2);pr.gravity=Gravity.CENTER_HORIZONTAL;pr.setMargins(0,dp(8),0,0);root.addView(busy,pr);
        status=txt("Prêt. Les sous-titres seront prévisualisés avant l'export.",14);status.setTextColor(0xff9fdcbf);status.setPadding(0,dp(10),0,0);root.addView(status);
        setContentView(sc);
    }

    private void setControlsVisible(boolean visible){int v=visible?View.VISIBLE:View.GONE;sizeLabel.setVisibility(v);sizeSeek.setVisibility(v);xLabel.setVisibility(v);xSeek.setVisibility(v);yLabel.setVisibility(v);ySeek.setVisibility(v);resetPositionButton.setVisibility(v);exportButton.setVisibility(v);}
    private void requestNotificationPermissionIfNeeded(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);}
    private boolean hasFullVideoPermission(){if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED;if(Build.VERSION.SDK_INT>=23)return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;return true;}
    private void requestVideoPermissionIfNeeded(){if(hasFullVideoPermission())return;if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO},REQ_VIDEO_PERMISSION);else if(Build.VERSION.SDK_INT>=23)requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_VIDEO_PERMISSION);}
    private void pickVideo(){if(!hasFullVideoPermission()){requestVideoPermissionIfNeeded();Toast.makeText(this,"Choisis « Autoriser toutes les vidéos ».",Toast.LENGTH_LONG).show();return;}showAllVideosPicker();}

    private void queryVolume(String volume,ArrayList<VideoEntry> out,HashSet<String> seen){Uri base=Build.VERSION.SDK_INT>=29?MediaStore.Video.Media.getContentUri(volume):MediaStore.Video.Media.EXTERNAL_CONTENT_URI;String[] p={MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.DURATION,MediaStore.Video.Media.DATE_ADDED};try(Cursor c=getContentResolver().query(base,p,null,null,null)){if(c==null)return;int idCol=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID),nameCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME),durCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION),dateCol=c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);while(c.moveToNext()){Uri u=ContentUris.withAppendedId(base,c.getLong(idCol));if(!seen.add(u.toString()))continue;String n=c.getString(nameCol);out.add(new VideoEntry(u,n==null?"Vidéo":n,c.getLong(durCol),c.getLong(dateCol)));}}catch(Exception ignored){}}
    private void showAllVideosPicker(){final ArrayList<VideoEntry> entries=new ArrayList<>();HashSet<String> seen=new HashSet<>();if(Build.VERSION.SDK_INT>=29){for(String volume:MediaStore.getExternalVolumeNames(this))queryVolume(volume,entries,seen);}else queryVolume("external",entries,seen);entries.sort((a,b)->Long.compare(b.dateAdded,a.dateAdded));if(entries.isEmpty()){openDocumentPicker();return;}LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText search=new EditText(this);search.setHint("Rechercher une vidéo…");box.addView(search,new LinearLayout.LayoutParams(-1,-2));ListView list=new ListView(this);final ArrayAdapter<VideoEntry> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>(entries));list.setAdapter(adapter);box.addView(list,new LinearLayout.LayoutParams(-1,dp(440)));TextView count=txt(entries.size()+" vidéo(s)",12);box.addView(count);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Toutes mes vidéos").setView(box).setNegativeButton("Fermer",null).setNeutralButton("Fichiers",(d,w)->openDocumentPicker()).create();list.setOnItemClickListener((p,v,pos,id)->{VideoEntry e=adapter.getItem(pos);if(e!=null){dialog.dismiss();selectVideo(e.uri,e.name);}});search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){adapter.getFilter().filter(s);}public void afterTextChanged(Editable e){}});dialog.show();}
    private void openDocumentPicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_VIDEO);}
    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==PICK_VIDEO&&c==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}selectVideo(u,getDisplayName(u));}}

    private void selectVideo(Uri u,String name){videoUri=u;preparedSubs.clear();previewHandler.removeCallbacksAndMessages(null);subtitlePreview.setText("");subtitlePreview.setVisibility(View.GONE);setControlsVisible(false);selected.setText("Vidéo sélectionnée : "+(name==null?u.getLastPathSegment():name));readVideoDimensions(u);preview.setVideoURI(u);preview.setMediaController(new MediaController(this));updatePreviewVideoLayout();preview.start();detected.setText("Langue détectée : —");status.setText("Vidéo chargée : "+videoWidth+"×"+videoHeight+". Génère l'aperçu.");}
    private String getDisplayName(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{MediaStore.MediaColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return u.getLastPathSegment();}
    private static String formatDuration(long ms){long t=Math.max(0,ms/1000),h=t/3600,m=(t%3600)/60,s=t%60;return h>0?String.format(Locale.ROOT,"%d:%02d:%02d",h,m,s):String.format(Locale.ROOT,"%02d:%02d",m,s);}
    private void setBusy(boolean on){runOnUiThread(()->busy.setVisibility(on?View.VISIBLE:View.GONE));}

    private void readVideoDimensions(Uri u){MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(this,u);String ws=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),hs=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);int w=ws==null?1920:Integer.parseInt(ws),h=hs==null?1080:Integer.parseInt(hs);String rot=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);int rr=rot==null?0:Integer.parseInt(rot);if(rr==90||rr==270){int tmp=w;w=h;h=tmp;}videoWidth=Math.max(1,w);videoHeight=Math.max(1,h);}catch(Exception ignored){videoWidth=1920;videoHeight=1080;}finally{try{r.release();}catch(Exception ignored){}}}

    private void updatePreviewVideoLayout(){
        if(previewFrame==null||preview==null)return;
        previewFrame.post(()->{
            int fw=previewFrame.getWidth(),fh=previewFrame.getHeight();if(fw<=0||fh<=0)return;
            float videoAspect=videoWidth/(float)Math.max(1,videoHeight);float frameAspect=fw/(float)Math.max(1,fh);int vw,vh;
            if(videoAspect>frameAspect){vw=fw;vh=Math.max(1,Math.round(fw/videoAspect));}else{vh=fh;vw=Math.max(1,Math.round(fh*videoAspect));}
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(vw,vh,Gravity.CENTER);preview.setLayoutParams(lp);preview.requestLayout();applyPreviewPosition();
        });
    }

    private void updateGuard(String text){Intent i=new Intent(this,ProcessingKeepAliveService.class);i.putExtra(ProcessingKeepAliveService.EXTRA_TEXT,text);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}

    private void runPipeline(){if(videoUri==null){Toast.makeText(this,"Choisis une vidéo d'abord.",Toast.LENGTH_LONG).show();return;}process.setEnabled(false);setControlsVisible(false);subtitlePreview.setVisibility(View.GONE);setBusy(true);status.setText("Extraction audio locale…");updateGuard("Extraction audio locale…");final int targetIndex=targetSpinner.getSelectedItemPosition();new Thread(()->{try{float[] audio=AudioDecoder.decode16kMono(this,videoUri);uiStatus("Whisper local : transcription + détection…");File model=ensureModel();String json=MainActivity.nativeTranscribe(model.getAbsolutePath(),audio);JSONObject root=new JSONObject(json);if(root.has("error"))throw new Exception(root.getString("error"));String src=root.optString("language","en");JSONArray arr=root.getJSONArray("segments");ArrayList<SubtitleOverlay.Segment> raw=new ArrayList<>();for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String text=o.optString("text").trim();if(!text.isEmpty())raw.add(new SubtitleOverlay.Segment(o.getDouble("start"),o.getDouble("end"),text));}if(raw.isEmpty())throw new Exception("Whisper n'a détecté aucun texte dans cette vidéo.");runOnUiThread(()->detected.setText("Langue détectée : "+src));String target=LANGS[targetIndex][1];ArrayList<SubtitleOverlay.Segment> done=(target.equals("same")||src.equals(target))?raw:translateLocalStable(src,target,raw);runOnUiThread(()->showSubtitlePreview(done));}catch(Exception e){packTickerRunning=false;setBusy(false);String msg=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();uiStatus("Erreur : "+msg);runOnUiThread(()->process.setEnabled(true));}}).start();}

    private void showSubtitlePreview(ArrayList<SubtitleOverlay.Segment> subs){preparedSubs.clear();preparedSubs.addAll(subs);setBusy(false);process.setEnabled(true);subtitleScale=1.0f;subtitleXPercent=50;subtitleYPercent=84;sizeSeek.setProgress(50);xSeek.setProgress(50);ySeek.setProgress(84);setControlsVisible(true);subtitlePreview.setVisibility(View.VISIBLE);updatePreviewVideoLayout();applyPreviewLayout();status.setText("Aperçu prêt. Ce que tu vois correspond à l'export. Règle taille + position, puis exporte.");preview.seekTo(0);preview.start();previewHandler.removeCallbacksAndMessages(null);previewHandler.post(previewTicker);}
    private final Runnable previewTicker=new Runnable(){public void run(){if(!preparedSubs.isEmpty()&&videoUri!=null){double t=preview.getCurrentPosition()/1000.0;String value="";for(SubtitleOverlay.Segment s:preparedSubs){if(t>=s.start&&t<s.end){value=s.text;break;}}if(!value.equals(subtitlePreview.getText().toString())){subtitlePreview.setText(value);applyPreviewLayout();}subtitlePreview.setVisibility(value.isEmpty()?View.INVISIBLE:View.VISIBLE);}previewHandler.postDelayed(this,100);}};

    private void applyPreviewLayout(){float ratio=videoWidth/(float)Math.max(1,videoHeight);float baseSp=ratio<0.68f?14f:(ratio<1.0f?16f:19f);subtitlePreview.setTextSize(baseSp*subtitleScale);subtitlePreview.setMaxLines(ratio<1.0f?4:3);applyPreviewPosition();}
    private void applyPreviewPosition(){if(previewFrame==null||subtitlePreview==null)return;previewFrame.post(()->{int contentW=preview.getWidth(),contentH=preview.getHeight();if(contentW<=0||contentH<=0)return;float videoAspect=videoWidth/(float)Math.max(1,videoHeight);int maxTextWidth=(int)Math.max(dp(70),contentW*(videoAspect<0.68f?0.82f:0.86f));subtitlePreview.setMaxWidth(maxTextWidth);subtitlePreview.measure(View.MeasureSpec.makeMeasureSpec(maxTextWidth,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(contentH,View.MeasureSpec.AT_MOST));float sw=subtitlePreview.getMeasuredWidth(),sh=subtitlePreview.getMeasuredHeight();float maxX=Math.max(0f,(contentW-sw)/2f-dp(4)),maxY=Math.max(0f,(contentH-sh)/2f-dp(4));float nx=(Math.max(0,Math.min(100,subtitleXPercent))-50)/50f;float ny=(Math.max(0,Math.min(100,subtitleYPercent))-50)/50f;subtitlePreview.setTranslationX(nx*maxX);subtitlePreview.setTranslationY(ny*maxY);});}

    private void uiStatus(String s){runOnUiThread(()->status.setText(s));}
    private File ensureModel()throws Exception{File f=new File(getFilesDir(),"ggml-tiny.bin");if(f.exists()&&f.length()>50_000_000)return f;uiStatus("Préparation du modèle Whisper local…");try(InputStream in=getAssets().open("ggml-tiny.bin");OutputStream out=new FileOutputStream(f)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return f;}
    private void startPackTicker(String src,String dst,long started){packTickerRunning=true;new Thread(()->{while(packTickerRunning){long sec=(System.currentTimeMillis()-started)/1000;uiStatus("Pack "+src+" → "+dst+" : "+sec+" s");try{Thread.sleep(1000);}catch(InterruptedException e){break;}}}).start();}
    private void downloadModelWithRetry(RemoteModelManager mgr,TranslateRemoteModel model,DownloadConditions conditions,String label)throws Exception{Exception last=null;for(int attempt=1;attempt<=3;attempt++){try{uiStatus("Pack "+label+" : tentative "+attempt+" / 3…");Tasks.await(mgr.download(model,conditions),3,TimeUnit.MINUTES);return;}catch(Exception e){last=e;if(attempt<3)Thread.sleep(attempt*3000L);}}throw new Exception("Téléchargement du modèle "+label+" impossible"+(last!=null&&last.getMessage()!=null?" : "+last.getMessage():""));}
    private ArrayList<SubtitleOverlay.Segment> translateLocalStable(String src,String dst,ArrayList<SubtitleOverlay.Segment> raw)throws Exception{String source=TranslateLanguage.fromLanguageTag(src),target=TranslateLanguage.fromLanguageTag(dst);if(source==null||target==null)throw new Exception("Traduction locale non disponible pour "+src+" → "+dst);DownloadConditions conditions=new DownloadConditions.Builder().build();RemoteModelManager mgr=RemoteModelManager.getInstance();TranslateRemoteModel srcModel=new TranslateRemoteModel.Builder(source).build();TranslateRemoteModel dstModel=new TranslateRemoteModel.Builder(target).build();long started=System.currentTimeMillis();startPackTicker(src,dst,started);try{downloadModelWithRetry(mgr,srcModel,conditions,src);if(!src.equals(dst))downloadModelWithRetry(mgr,dstModel,conditions,dst);}finally{packTickerRunning=false;}Translator tr=Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build());try{Tasks.await(tr.downloadModelIfNeeded(conditions),2,TimeUnit.MINUTES);ArrayList<SubtitleOverlay.Segment> out=new ArrayList<>();final int chunk=6;for(int base=0;base<raw.size();base+=chunk){int end=Math.min(base+chunk,raw.size());ArrayList<Task<String>> jobs=new ArrayList<>();for(int j=base;j<end;j++)jobs.add(tr.translate(raw.get(j).text));List<String> texts=Tasks.await(Tasks.whenAllSuccess(jobs));for(int j=base;j<end;j++){SubtitleOverlay.Segment s=raw.get(j);out.add(new SubtitleOverlay.Segment(s.start,s.end,texts.get(j-base)));}int done=end;uiStatus("Traduction locale : "+done+" / "+raw.size());}return out;}finally{tr.close();}}

    private void startExport(ArrayList<SubtitleOverlay.Segment> subs){if(subs.isEmpty()){Toast.makeText(this,"Aucun sous-titre à exporter.",Toast.LENGTH_LONG).show();return;}preview.pause();exportButton.setEnabled(false);process.setEnabled(false);setBusy(true);status.setText("Création de la vidéo avec le rendu de l'aperçu…");updateGuard("Export vidéo sous-titrée…");try{File out=new File(getExternalFilesDir(null),"AutoSub_"+System.currentTimeMillis()+".mp4");OverlayEffect overlayEffect=new OverlayEffect(Collections.singletonList(new SubtitleOverlay(subs,videoWidth,videoHeight,subtitleScale,subtitleXPercent,subtitleYPercent)));Effects effects=new Effects(Collections.emptyList(),Collections.singletonList(overlayEffect));EditedMediaItem edited=new EditedMediaItem.Builder(MediaItem.fromUri(videoUri)).setEffects(effects).build();Transformer transformer=new Transformer.Builder(this).addListener(new Transformer.Listener(){@Override public void onCompleted(Composition composition,ExportResult result){try{Uri saved=saveToGallery(out);status.setText("Terminé. Vidéo enregistrée : "+saved);Toast.makeText(V14MainActivity.this,"Vidéo sous-titrée enregistrée",Toast.LENGTH_LONG).show();}catch(Exception e){status.setText("Export terminé mais copie galerie impossible : "+e.getMessage());}exportButton.setEnabled(true);process.setEnabled(true);setBusy(false);}@Override public void onError(Composition composition,ExportResult result,ExportException e){exportButton.setEnabled(true);process.setEnabled(true);setBusy(false);status.setText("Erreur export vidéo : "+e.getMessage());}}).build();transformer.start(edited,out.getAbsolutePath());}catch(Exception e){exportButton.setEnabled(true);process.setEnabled(true);setBusy(false);status.setText("Erreur export : "+e.getMessage());}}
    private Uri saveToGallery(File src)throws Exception{ContentValues v=new ContentValues();v.put(MediaStore.Video.Media.DISPLAY_NAME,"AutoSub_"+System.currentTimeMillis()+".mp4");v.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");if(Build.VERSION.SDK_INT>=29){v.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/AutoSub");v.put(MediaStore.Video.Media.IS_PENDING,1);}Uri u=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("MediaStore indisponible");try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(u)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}if(Build.VERSION.SDK_INT>=29){ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(u,done,null,null);}src.delete();return u;}
}
