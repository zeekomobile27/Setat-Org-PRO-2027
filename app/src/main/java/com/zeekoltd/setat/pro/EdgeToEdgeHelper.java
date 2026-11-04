package com.zeekoltd.setat.pro;

import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Centralized edge-to-edge setup for Android 15+ and older supported versions. */
public final class EdgeToEdgeHelper {
    private EdgeToEdgeHelper() {
        // Utility class.
    }

    public static void enable(Window window) {
        if (window == null) return;

        WindowCompat.enableEdgeToEdge(window);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                window, window.getDecorView());
        if (controller != null) {
            // Preserve the app's existing light system-bar icon appearance.
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }
    }
}
