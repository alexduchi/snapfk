package com.sentry.local;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!context.getSharedPreferences("sentry_v23", Context.MODE_PRIVATE).getBoolean("enabled", false)) return;
        Intent service = new Intent(context, SentryGuardianService.class).setAction(SentryGuardianService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
    }
}
