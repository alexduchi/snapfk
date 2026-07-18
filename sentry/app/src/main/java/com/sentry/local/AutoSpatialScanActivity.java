package com.sentry.local;

import android.app.AlertDialog;
import android.os.Bundle;

/**
 * Simplified scan entry point. It keeps the proven ARCore measurement engine while
 * presenting a one-time minimal instruction before calibration starts.
 */
public class AutoSpatialScanActivity extends SpatialScanActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!getSharedPreferences("sentry_v25", MODE_PRIVATE).getBoolean("auto_scan_intro", false)) {
            new AlertDialog.Builder(this)
                    .setTitle("Scan automatique")
                    .setMessage("Marche lentement en faisant le tour de la pièce. Montre le sol, les murs puis le plafond. Sentry stabilise la caméra et te demande seulement de confirmer les coins difficiles.\n\nLe résultat reste une estimation AR, pas un relevé certifié.")
                    .setPositiveButton("Commencer", (d, w) -> getSharedPreferences("sentry_v25", MODE_PRIVATE).edit().putBoolean("auto_scan_intro", true).apply())
                    .setNegativeButton("Mode manuel", (d, w) -> finish())
                    .show();
        }
    }
}
