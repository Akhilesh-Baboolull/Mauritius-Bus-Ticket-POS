package com.ayb.busticketpos;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Kiosk: the system Back button / gesture does nothing on every screen.
 * Screens are left only through their own on-screen buttons (Menu, Back, etc.).
 * Home and Recents are blocked by lock task (see KioskHelper.applyLockTaskOnly).
 * Dialogs handle Back in their own window and are not affected.
 */
final class KioskBackBlocker implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        if (!(activity instanceof ComponentActivity)) return;
        try {
            ComponentActivity componentActivity = (ComponentActivity) activity;
            componentActivity.getOnBackPressedDispatcher().addCallback(componentActivity,
                    new OnBackPressedCallback(true) {
                        @Override
                        public void handleOnBackPressed() {
                            // Intentionally ignored.
                        }
                    });
        } catch (Throwable t) {
            AppLog.w("KioskBackBlocker", "Could not block Back on " + activity.getClass().getSimpleName(), t);
        }
    }

    @Override public void onActivityStarted(@NonNull Activity activity) {}
    @Override public void onActivityResumed(@NonNull Activity activity) {}
    @Override public void onActivityPaused(@NonNull Activity activity) {}
    @Override public void onActivityStopped(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) {}
}
