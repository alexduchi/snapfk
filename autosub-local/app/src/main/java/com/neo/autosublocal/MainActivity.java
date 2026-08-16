package com.neo.autosublocal;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.PackageManager;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int REQ_MEDIA = 100;
    private LinearLayout list;
    private TextView status;
    private final ArrayList<Uri> uris = new ArrayList<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(11,16,32));
        getWindow().setNavigationBarColor(Color.rgb(11,16,32));
        buildUi();
        requestGalleryAccess();
    }

    private int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, int sp, int color) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); return v;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(11,16,32));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));

        TextView title = text("AutoSub Local", 28, Color.WHITE);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView desc = text("Build install-fix 100 % locale : aucune API externe.", 14, Color.rgb(180,195,230));
        desc.setPadding(0,dp(6),0,dp(14));
        root.addView(desc);

        status = text("Vérification de l'accès à la galerie…", 14, Color.rgb(140,220,190));
        root.addView(status);

        Button refresh = new Button(this);
        refresh.setText("Actualiser la galerie");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1,-2); rp.setMargins(0,dp(12),0,dp(8));
        root.addView(refresh,rp);
        refresh.setOnClickListener(v -> { if(hasPermission()) loadVideos(); else requestGalleryAccess(); });

        TextView note = text("Cette build sert d'abord à valider que l'APK s'installe correctement sur ton Samsung et qu'elle voit toute ta galerie. Le moteur Whisper local sera branché ensuite sur cette base, sans clé API.", 13, Color.rgb(205,205,220));
        note.setPadding(0,dp(6),0,dp(12));
        root.addView(note);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list,new LinearLayout.LayoutParams(-1,-2));
        setContentView(scroll);
    }

    private boolean hasPermission() {
        if(Build.VERSION.SDK_INT >= 33) return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestGalleryAccess() {
        if(hasPermission()) { loadVideos(); return; }
        if(Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQ_MEDIA);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA);
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if(req == REQ_MEDIA && hasPermission()) loadVideos();
        else status.setText("Accès refusé. Autorise les vidéos dans Réglages > Applications > AutoSub Local > Autorisations.");
    }

    private void loadVideos() {
        list.removeAllViews(); uris.clear();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE };
        String sort = MediaStore.Video.Media.DATE_ADDED + " DESC";
        try(Cursor c = getContentResolver().query(collection, projection, null, null, sort)) {
            if(c == null) { status.setText("Impossible de lire MediaStore."); return; }
            int idIx=c.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameIx=c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int durIx=c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            int sizeIx=c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int count=0;
            while(c.moveToNext() && count < 200) {
                long id=c.getLong(idIx); String name=c.getString(nameIx); long dur=c.getLong(durIx); long size=c.getLong(sizeIx);
                Uri u=ContentUris.withAppendedId(collection,id); uris.add(u); count++;
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12),dp(10),dp(12),dp(10));
                card.setBackgroundColor(Color.rgb(25,32,54));
                TextView n=text(name==null?"Vidéo":name,16,Color.WHITE); n.setTypeface(null,1); card.addView(n);
                String meta=(dur/1000)+" s · "+String.format(java.util.Locale.US,"%.1f Mo",size/1048576.0);
                card.addView(text(meta,12,Color.rgb(175,190,220)));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,0,0,dp(8)); list.addView(card,cp);
            }
            status.setText("Galerie accessible : "+count+" vidéo(s) affichée(s).");
        } catch(Exception e) {
            status.setText("Erreur galerie : "+e.getClass().getSimpleName()+" — "+e.getMessage());
        }
    }
}
