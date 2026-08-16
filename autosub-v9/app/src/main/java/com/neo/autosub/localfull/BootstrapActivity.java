package com.neo.autosub.localfull;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class BootstrapActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent service = new Intent(this, ProcessingKeepAliveService.class);
        service.putExtra(ProcessingKeepAliveService.EXTRA_TEXT,
                "AutoSub prêt : téléchargement, transcription et export protégés en arrière-plan.");
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);

        Intent main = new Intent(this, StableMainActivity.class);
        main.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(main);
        finish();
    }
}
