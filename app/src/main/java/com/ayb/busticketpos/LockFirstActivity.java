package com.ayb.busticketpos;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Launcher activity: on first start after a boot, shows the loading UI; otherwise goes straight to Menu.
 * Always applies lock task immediately, then starts Menu and finishes.
 */
public class LockFirstActivity extends AppCompatActivity {
    private static final String TAG = "LockFirstActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setShowWhenLocked(true);
        setTurnScreenOn(true);

        boolean showLoading = !Prefs.getLockFirstShownThisBoot(this);
        if (showLoading) {
            Prefs.setLockFirstShownThisBoot(this, true);
            setContentView(R.layout.activity_lock_first);
        } else {
            setContentView(R.layout.activity_lock_first_blank);
        }

        KioskHelper.applyLockTaskOnly(this);

        Intent menu = new Intent(this, Menu.class);
        menu.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(menu);
        finish();
        Log.d(TAG, "Lock applied, started Menu (loadingShown=" + showLoading + ")");
    }
}
