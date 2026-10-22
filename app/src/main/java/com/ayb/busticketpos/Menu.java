package com.ayb.busticketpos;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Observer;

import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class Menu extends AppCompatActivity {
    private static boolean hasCheckedRedirect = false;
    Button btnPrintTicket; // Declare
    Button  btn_start_day, btn_report;

    TextView current_day, current_trip;

    LinearLayout overlay, admin_layout;
    ProgressBar progressBar;
    TextView debugSecret;
    Button btn_start_trip, btn_update, btn_admin_close;
    EditText admin_password, userId, machineId;
    TextView tenant_name;
    ImageView aybway_logo;
    TextView kioskMode;

    LinearLayout admin_mode_layout;
    EditText kiosk_admin_password;
    Button btn_admin_mode_ok, btn_admin_mode_cancel;

    private WindowManager kioskWindowManager;
    private View kioskBlockerView;
    private TextView battery_icon, battery_state, battery_percentage, clock_view;
    android.os.Handler clockHandler;
    private Handler bootDelayHandler;
    private BroadcastReceiver batteryReceiver;
    final boolean[] isSyncTriggered = {false};

    @SuppressLint({"MissingInflatedId", "WrongViewCast", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_menu);
        // Status bar (clock + battery) must paint before heavy Menu init — setupButtons() was
        // blocking the main thread while battery updates sat queued on runOnUiThread().
        initStatusBarEarly();

        // Kiosk: show over lock screen and turn screen on when launched (e.g. after update relaunch)
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        // Defer non-UI work so the status bar and transition paint first.
        new Handler(Looper.getMainLooper()).post(this::runDeferredMenuCreateWork);

        long lastAlive = Prefs.getLastAlive(this);
        long now = System.currentTimeMillis();
        // Informational only: app was in background or killed for a while. Use Log.w so it doesn't appear as a crash in crash log.
        if (lastAlive > 0 && (now - lastAlive) > 2 * 60_000) {
            Log.w("Menu", "App was in background or killed for " + ((now - lastAlive) / 1000) + "s (not a crash)");
        }

        // --- ① Safe-boot warm-up check ---
        if (!Prefs.isBootReady(this)) {
            Log.w("MenuInit", "System just rebooted — delaying UI init for 1.5 s");

            // 🟢 Show loading overlay & spinner immediately
            View overlay = findViewById(R.id.overlay);
            ProgressBar progressBar = findViewById(R.id.progressBar);
            if (overlay != null) overlay.setVisibility(View.VISIBLE);
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

            // ⏳ Use a persistent reference for the delay handler
            bootDelayHandler = new Handler(getMainLooper());
            bootDelayHandler.postDelayed(() -> {
                Prefs.setBootReady(this, true);
                Log.d("MenuInit", "System ready → recreating Menu");

                if (overlay != null) overlay.setVisibility(View.GONE);
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                recreate();
            }, 1500);

            return; // 🚫 Skip rest of initialization on first boot
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (!hasCheckedRedirect) {
            hasCheckedRedirect = true;
            View redirectOverlay = findViewById(R.id.overlay);
            ProgressBar redirectPb = findViewById(R.id.progressBar);
            if (redirectOverlay != null) redirectOverlay.setVisibility(View.VISIBLE);
            if (redirectPb != null) redirectPb.setVisibility(View.VISIBLE);

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(Menu.this);
                    boolean hasRoutes = db.routeDao().countAll() > 0;
                    boolean hasStages = db.stageDao().countAll() > 0;
                    int tripStatus = Prefs.getTripStatus(Menu.this);

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (tripStatus == 1 && hasRoutes && hasStages) {
                            startActivity(new Intent(Menu.this, PrintTicket.class));
                            finish();
                            return;
                        }
                        if (redirectOverlay != null) redirectOverlay.setVisibility(View.GONE);
                        if (redirectPb != null) redirectPb.setVisibility(View.GONE);
                        initMenuUi();
                    });
                } catch (Exception e) {
                    Log.e("Redirect", "DB check failed", e);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        initMenuUi();
                    });
                }
            });
            return;
        }

        initMenuUi();
    }

    /** Main Menu UI setup (called once redirect check is done or skipped). */
    private void initMenuUi() {
        // Initialize the button by finding it by its ID from the layout
        btnPrintTicket = findViewById(R.id.btn_ticket);
        btn_start_day = findViewById(R.id.btn_start_day);
        current_day = findViewById(R.id.current_day_view);
        current_trip = findViewById(R.id.current_trip_view);
        overlay = findViewById(R.id.overlay);
        progressBar = findViewById(R.id.progressBar);
        btn_start_trip = findViewById(R.id.btn_start_trip);
        debugSecret = findViewById(R.id.debug_secret);
        btn_report = findViewById(R.id.btn_report);
        btn_update = findViewById(R.id.btn_update);
        admin_password = findViewById(R.id.admin_password);
        userId = findViewById(R.id.userId);
        admin_layout = findViewById(R.id.admin_layout);
        btn_admin_close = findViewById(R.id.btn_admin_close);
        machineId = findViewById(R.id.machineId);
        aybway_logo = findViewById(R.id.aybway_logo);
        kioskMode = findViewById(R.id.kioskMode);
        admin_mode_layout = findViewById(R.id.admin_mode_layout);
        kiosk_admin_password = findViewById(R.id.admin_mode_password);
        btn_admin_mode_ok = findViewById(R.id.btn_admin_mode_exit);
        btn_admin_mode_cancel = findViewById(R.id.btn_admin_mode_close);
        bindStatusBarWidgets();
        refreshBatteryUiImmediate();
        ensureBatteryReceiverRegistered();
        ensureClockRunning();
        tenant_name = findViewById(R.id.tenant_name);
        tenant_name.setText(PrefsSecure.getTenantName(this));

        if(Prefs.getDayStatus(this) == 1){
            current_day.setText("Day Started: " + Prefs.getDayDate(this));
            new Handler(Looper.getMainLooper()).post(this::setupButtons);

            btn_start_day.setText("END DAY");

            btn_start_trip.setVisibility(View.VISIBLE);
            btnPrintTicket.setVisibility(View.VISIBLE);
        }
        else{
            btn_start_day.setText("START DAY");
            overlay.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);

            btn_start_trip.setVisibility(View.GONE);
            btnPrintTicket.setVisibility(View.GONE);

            initialiseDB();

        }

        if(Prefs.getTripStatus(this) == 1){
            current_trip.setText("Current Trip: " + Prefs.getSelectedDirectionName(this));
            btn_start_trip.setText("STOP TRIP");
            btnPrintTicket.setVisibility(View.VISIBLE);
            btn_start_day.setVisibility(View.GONE);

        }
        else{
            current_trip.setText("");
            btn_start_trip.setText("BEGIN TRIP");
            btnPrintTicket.setVisibility(View.GONE);
            btn_start_day.setVisibility(View.VISIBLE);
        }

        final int[] kTapCount = {0};
        final long[] kLastTapTime = {0};

        kioskMode.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();

            if (currentTime - kLastTapTime[0] > 2000) {
                // Reset if more than 2 seconds since last tap
                kTapCount[0] = 0;
            }

            kTapCount[0]++;
            kLastTapTime[0] = currentTime;

            if (kTapCount[0] >= 15) {
//                enterAdminMode();
                exitKiosk();
                kTapCount[0] = 0;
            }

        });

        final int[] tapCount = {0};
        final long[] lastTapTime = {0};

        debugSecret.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastTapTime[0] > 2000) {
                // Reset if more than 2 seconds since last tap
                tapCount[0] = 0;
            }

            tapCount[0]++;
            lastTapTime[0] = currentTime;

            if (tapCount[0] >= 15) {
                updateMachine();
                tapCount[0] = 0;
            }
        });

        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v ->
                    startActivity(new Intent(Menu.this, DeviceMaintenanceActivity.class)));
        }

        final int[] tapCount_update = {0};
        final long[] lastTapTime_update = {0};

        aybway_logo.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastTapTime_update[0] > 2000) {
                // Reset if more than 2 seconds since last tap
                tapCount_update[0] = 0;
                isSyncTriggered[0] = false; // 🧭 allow re-trigger after pause
            }

            tapCount_update[0]++;
            lastTapTime_update[0] = currentTime;

            if (tapCount_update[0] >= 10 && !isSyncTriggered[0]) {
                isSyncTriggered[0] = true; // ✅ lock further triggers

                Toast.makeText(this, "Updating... Keep internet connection stable.", Toast.LENGTH_LONG).show();

                overlay.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE);

                DatabaseSyncManager.forceSync(this, (success, message) -> {
                    overlay.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                    if (success) {
                        setupButtons();
                    }
                });

                // Reset tap count
                tapCount_update[0] = 0;
            }
        });

    }

    private static long lastMenuMaintenanceMs = 0L;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshMenuStateFromPrefs();
    }

    /** Lightweight refresh when Menu is brought back (singleTop) without full recreate. */
    private void refreshMenuStateFromPrefs() {
        if (btn_start_day == null || current_day == null) return;
        if (Prefs.getDayStatus(this) == 1) {
            current_day.setText("Day Started: " + Prefs.getDayDate(this));
            btn_start_day.setText("END DAY");
            btn_start_trip.setVisibility(View.VISIBLE);
        } else {
            current_day.setText("");
            btn_start_day.setText("START DAY");
            btn_start_trip.setVisibility(View.GONE);
            btnPrintTicket.setVisibility(View.GONE);
        }
        if (Prefs.getTripStatus(this) == 1) {
            current_trip.setText("Current Trip: " + Prefs.getSelectedDirectionName(this));
            btn_start_trip.setText("STOP TRIP");
            btnPrintTicket.setVisibility(View.VISIBLE);
            btn_start_day.setVisibility(View.GONE);
        } else {
            current_trip.setText("");
            btn_start_trip.setText("BEGIN TRIP");
            btnPrintTicket.setVisibility(View.GONE);
            btn_start_day.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncPositioningDayFlagFromPrefs();
        refreshBatteryUiImmediate();
        if (btn_start_day != null) {
            refreshMenuStateFromPrefs();
        }
        new Handler(Looper.getMainLooper()).post(this::runDeferredMenuResumeWork);
    }

    /** Keep :bg positioning aligned with operator day state (file is read in :bg process). */
    private void syncPositioningDayFlagFromPrefs() {
        if (Prefs.getDayStatus(this) == 1) {
            Prefs.publishPositioningDayState(this, true, Prefs.getDayDate(this));
        } else {
            Prefs.publishPositioningDayState(this, false, null);
        }
    }

    private void runDeferredMenuCreateWork() {
        if (isFinishing() || isDestroyed()) return;
        App.scheduleDailyFetch(this);
        App.scheduleDailyFetchFallback(this);
    }

    private static final long STALE_20H_MS = 20L * 3600 * 1000;
    /** Avoid re-enqueueing / re-toasting on every Menu resume while the server is unreachable. */
    private static final long AUTO_RETRY_THROTTLE_MS = 30L * 60 * 1000;
    private static long lastAutoUpdateCheckAttemptMs = 0L;
    private static long lastAutoMasterSyncAttemptMs = 0L;

    /** 20h update check (check only) and master data refresh when Menu becomes visible. */
    private void runStaleDataChecks() {
        if (!Prefs.isBootReady(this)) return;
        if (!SyncQueueMaintenance.isOnline(this)) return;
        long now = System.currentTimeMillis();

        if (now - Prefs.getLastUpdateCheckAt(this) > STALE_20H_MS
                && now - lastAutoUpdateCheckAttemptMs > AUTO_RETRY_THROTTLE_MS) {
            lastAutoUpdateCheckAttemptMs = now;
            UpdateScheduler.enqueueCheckOnly(this, false);
            Log.d("Menu", "Last update check > 20h — enqueued check-only");
        }

        if (PrefsCache.shouldFetchAgain(this)
                && !DatabaseSyncManager.isSyncRunning()
                && now - lastAutoMasterSyncAttemptMs > AUTO_RETRY_THROTTLE_MS) {
            lastAutoMasterSyncAttemptMs = now;
            DatabaseSyncManager.startSync(getApplicationContext(), AppDatabase.getInstance(this));
            Log.d("Menu", "Last master data fetch > 20h — started sync (keeps existing data on failure)");
        }
    }

    private void runDeferredMenuResumeWork() {
        if (isFinishing() || isDestroyed()) return;
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
            KioskHelper.applyLockTaskOnly(this);
            blockStatusBarPullDown();
        }
        LocationForegroundService.startIfNeeded(this);
        long now = System.currentTimeMillis();
        if (now - lastMenuMaintenanceMs > 60_000L) {
            lastMenuMaintenanceMs = now;
            SyncQueueMaintenance.pruneAndScheduleDrainWhenOnline(this, false);
        }
        runStaleDataChecks();
    }

    private void initStatusBarEarly() {
        bindStatusBarWidgets();
        refreshBatteryUiImmediate();
        ensureBatteryReceiverRegistered();
        ensureClockRunning();
    }

    private void bindStatusBarWidgets() {
        if (clock_view != null) return;
        clock_view = findViewById(R.id.clock_view);
        battery_percentage = findViewById(R.id.battery_percentage);
        battery_state = findViewById(R.id.battery_state);
        battery_icon = findViewById(R.id.battery_icon);
    }

    /** One-shot read of sticky battery intent — safe before registerReceiver listener. */
    private void refreshBatteryUiImmediate() {
        bindStatusBarWidgets();
        if (battery_percentage == null) return;
        Intent sticky = ContextCompat.registerReceiver(
                this,
                null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        applyBatteryUi(sticky);
    }

    @SuppressLint("SetTextI18n")
    private void applyBatteryUi(@Nullable Intent intent) {
        if (intent == null || battery_percentage == null) return;

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        int batteryPct;
        if (level >= 0 && scale > 0) {
            batteryPct = (int) ((level / (float) scale) * 100);
        } else {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            batteryPct = bm != null
                    ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    : 0;
        }

        battery_percentage.setText(batteryPct + "%");

        String stateText;
        String iconText = "🔋";
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                stateText = "Charging";
                iconText = "⚡";
                break;
            case BatteryManager.BATTERY_STATUS_FULL:
                stateText = "Fully Charged";
                iconText = "🔋";
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                stateText = "";
                break;
            default:
                stateText = "";
        }

        battery_icon.setText(iconText);
        battery_state.setText(stateText);

        if (batteryPct < 20 && status != BatteryManager.BATTERY_STATUS_CHARGING) {
            battery_percentage.setTextColor(Color.RED);
            battery_icon.setTextColor(Color.RED);
        } else {
            battery_percentage.setTextColor(Color.BLACK);
            battery_icon.setTextColor(Color.WHITE);
        }
    }

    private void ensureBatteryReceiverRegistered() {
        if (batteryReceiver != null) return;
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                applyBatteryUi(intent);
            }
        };
        ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void ensureClockRunning() {
        if (clockHandler != null) return;
        clockHandler = new Handler(getMainLooper());
        clockHandler.post(getRunnable(clockHandler));
    }

    /** Shows a dialog asking to restart to complete registration; Restart button triggers reboot. */
    private void showRestartToCompleteDialog() {
        new AlertDialog.Builder(this)
                .setMessage("Restart to complete registration")
                .setCancelable(true)
                .setPositiveButton("Restart", (dialog, which) -> rebootDeviceIfOwner())
                .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /** Reboot device so :bg process picks up new provisioning (tenant/machine id). Device owner only. */
    private void rebootDeviceIfOwner() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(getPackageName())) return;
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            dpm.reboot(admin);
        } catch (Exception e) {
            Log.e("Menu", "Reboot after registration failed", e);
            Toast.makeText(this, "Registered. Please restart the device manually.", Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private Runnable getRunnable(Handler clockHandler) {
        final java.text.SimpleDateFormat clockFormat =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());

        final Runnable clockRunnable = new Runnable() {
            @Override
            public void run() {
                // Only update if screen is on to save battery
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                boolean screenOn = pm == null || pm.isInteractive();
                if (screenOn) {
                    clock_view.setText(clockFormat.format(new java.util.Date()));
                }

                // Coalesced 1s update — if system is busy, may drift a few ms, which is fine
                clockHandler.postDelayed(this, 1000);
            }
        };
        return clockRunnable;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
            } catch (Exception ignored) {
            }
            batteryReceiver = null;
        }

        // ✅ Safely stop clock updates
        if (clockHandler != null) {
            clockHandler.removeCallbacksAndMessages(null);
            clockHandler = null;
        }

        // ✅ Safely stop boot delay
        if (bootDelayHandler != null) {
            bootDelayHandler.removeCallbacksAndMessages(null);
            bootDelayHandler = null;
        }
    }


    private void exitKiosk() {
        admin_mode_layout.setVisibility(View.VISIBLE);
    }

    private void enableKioskModeIfDeviceOwner(Context context) {
        KioskHelper.applyLockTaskOnly(this);
        blockStatusBarPullDown();
        Log.d("KioskMode", "Kiosk applied (lock task + swipe-down blocked)");
    }



    @SuppressLint("ClickableViewAccessibility")
    private void blockStatusBarPullDown() {
        try {
            // Prevent duplicate overlays
            if (kioskBlockerView != null) return;

            kioskWindowManager = (WindowManager) getApplicationContext()
                    .getSystemService(Context.WINDOW_SERVICE);

            int height = (int) (50 * getResources().getDisplayMetrics().density);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSPARENT);

            params.gravity = Gravity.TOP;

            kioskBlockerView = new View(this);
            kioskBlockerView.setBackgroundColor(Color.TRANSPARENT);
            kioskBlockerView.setOnTouchListener((v, event) -> true); // block touches

            kioskWindowManager.addView(kioskBlockerView, params);
            Log.d("KioskMode", "Status bar overlay added");
        } catch (Exception e) {
            Log.e("KioskMode", "Failed to block swipe-down", e);
        }
    }

    private void removeStatusBarBlocker() {
        try {
            if (kioskWindowManager != null && kioskBlockerView != null) {
                kioskWindowManager.removeView(kioskBlockerView);
                kioskBlockerView = null;
                Log.d("KioskMode", "Status bar overlay removed");
            }
        } catch (Exception e) {
            Log.e("KioskMode", "Failed to remove status bar blocker", e);
        }
    }




    private void enterAdminMode() {
        try {
            // 1️⃣ Stop kiosk (Lock Task) mode
            stopLockTask();
            Log.d("AdminAccess", "Kiosk mode disabled");

            // 2️⃣ Remove swipe-down blocker overlay
            removeStatusBarBlocker();

            // 3️⃣ Lift network restrictions so admin can access Settings
            DevicePolicyManager dpm =
                    (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);

            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_AIRPLANE_MODE);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_WIFI);
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
                Log.d("AdminAccess", "Restrictions cleared");
            }

            // 4️⃣ Launch system Settings
            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d("AdminAccess", "Opened Settings for admin");

            Toast.makeText(this, "Admin mode activated – you can now access settings", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("AdminAccess", "Failed to enter admin mode", e);
        }
    }


    private void initialiseDB(){
        AppDatabase
                .getInstance(this)
                .routeDao()
                .getAllRoutesLive()
                .observe(this, new Observer<List<RouteEntity>>() {
                    private boolean firstFired = false;

                    @Override
                    public void onChanged(List<RouteEntity> routes) {
                        if (!firstFired) {
                            firstFired = true;
                            // 🔓 Unblock UI
                            overlay.setVisibility(View.GONE);
                            progressBar.setVisibility(View.GONE);

                            // Now the DB is ready and routes are loaded.
                            setupButtons();

                        }
                    }
                });
    }

    private void updateMachine() {
        admin_layout.setVisibility(View.VISIBLE);
    }


    private void setupButtons(){
        // Set an OnClickListener on the button
        btnPrintTicket.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());

                if(!Prefs.getDayDate(Menu.this).equals(today)){
                    String msg = "Day Expired! End Current Trip & Day and Start a New One";
                    SpannableString sizedMsg = new SpannableString(msg);
                    // the second parameter "true" means "treat the 18 as SP, not pixels"
                    sizedMsg.setSpan(
                            new AbsoluteSizeSpan(28, true),  // 18sp text size
                            0,                                // start
                            msg.length(),                     // end
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    );
                    sizedMsg.setSpan(
                            new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                            0, msg.length(),
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    );

                    AlertDialog dialog = new AlertDialog.Builder(Menu.this)
                            .setMessage(sizedMsg)
                            .setPositiveButton("OK", null)
                            .show();

                    // 🚫 Prevent dismiss when touching outside or pressing Back
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setCancelable(false);

                    Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

                    // • Increase text size to 20sp
                    okButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

                    // • Change text color to white (for contrast)
                    okButton.setTextColor(Color.WHITE);

                    // • Paint its background blue
                    okButton.setBackgroundColor(Color.BLUE);

                    // (Optional) If you want rounded corners or padding:
                    int pad = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()
                    );
                    okButton.setPadding(pad, pad, pad, pad);
                    okButton.setAllCaps(false);
                    LinearLayout parent = (LinearLayout) okButton.getParent();
                    parent.setGravity(Gravity.CENTER);

                    // (Optional) If you want the button to stretch across and then center its text:
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) okButton.getLayoutParams();
                    params.width = LinearLayout.LayoutParams.MATCH_PARENT;
                    okButton.setLayoutParams(params);
                    okButton.setGravity(Gravity.CENTER);

                    TextView messageView = dialog.findViewById(android.R.id.message);
                    if (messageView != null) {
                        // 3️⃣ convert 16dp to pixels
                        int extraPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                16,
                                getResources().getDisplayMetrics()
                        );

                        // 4️⃣ add it to the bottom padding
                        messageView.setPadding(
                                messageView.getPaddingLeft(),
                                messageView.getPaddingTop(),
                                messageView.getPaddingRight(),
                                messageView.getPaddingBottom() + extraPx
                        );
                    }

                    dialog.getWindow().setBackgroundDrawable(
                            new ColorDrawable(Color.parseColor("#FFAA00"))
                    );

                    // ✅ Add click listener for OK button
                    okButton.setOnClickListener(view -> {
                        dialog.dismiss();
                    });
                    return;

                }


                if (Prefs.getTripStatus(Menu.this) == 1) {
                    startActivity(new Intent(Menu.this, PrintTicket.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                } else {

                    String msg = "No Trip Selected!";
                    SpannableString sizedMsg = new SpannableString(msg);
                    // the second parameter "true" means "treat the 18 as SP, not pixels"
                    sizedMsg.setSpan(
                            new AbsoluteSizeSpan(48, true),  // 18sp text size
                            0,                                // start
                            msg.length(),                     // end
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                     );
                   sizedMsg.setSpan(
                            new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                            0, msg.length(),
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    );

                    AlertDialog dialog = new AlertDialog.Builder(Menu.this)
                            .setMessage(sizedMsg)
                            .setPositiveButton("OK", null)
                            .show();

                    Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

                    // • Increase text size to 20sp
                    okButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

                    // • Change text color to white (for contrast)
                    okButton.setTextColor(Color.WHITE);

                    // • Paint its background blue
                    okButton.setBackgroundColor(Color.BLUE);

                    // (Optional) If you want rounded corners or padding:
                    int pad = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()
                    );
                    okButton.setPadding(pad, pad, pad, pad);
                    okButton.setAllCaps(false);
                    LinearLayout parent = (LinearLayout) okButton.getParent();
                    parent.setGravity(Gravity.CENTER);

                    // (Optional) If you want the button to stretch across and then center its text:
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) okButton.getLayoutParams();
                    params.width = LinearLayout.LayoutParams.MATCH_PARENT;
                    okButton.setLayoutParams(params);
                    okButton.setGravity(Gravity.CENTER);

                    TextView messageView = dialog.findViewById(android.R.id.message);
                    if (messageView != null) {
                        // 3️⃣ convert 16dp to pixels
                        int extraPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                16,
                                getResources().getDisplayMetrics()
                        );

                        // 4️⃣ add it to the bottom padding
                        messageView.setPadding(
                                messageView.getPaddingLeft(),
                                messageView.getPaddingTop(),
                                messageView.getPaddingRight(),
                                messageView.getPaddingBottom() + extraPx
                        );
                    }

                    dialog.getWindow().setBackgroundDrawable(
                            new ColorDrawable(Color.parseColor("#FFAA00"))
                    );
                }
            }
        });

        btn_start_day.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Menu.this, Day.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }
        });

        btn_report.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Menu.this, Report.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }
        });

        btn_update.setOnClickListener(v -> {
            String pwd = admin_password.getText().toString().trim();
            String tenantId = userId.getText().toString().trim();
            String machine = machineId.getText().toString().trim();

            if (!pwd.equals("AYB%0000")) {
                Toast.makeText(Menu.this, "Unauthorised Access!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (tenantId.isEmpty() || machine.isEmpty()) {
                Toast.makeText(Menu.this, "Tenant ID and Machine ID required!", Toast.LENGTH_SHORT).show();
                return;
            }

            int tenantIdInt;
            int machineIdInt;
            try {
                tenantIdInt = Integer.parseInt(tenantId);
                machineIdInt = Integer.parseInt(machine);
            } catch (NumberFormatException e) {
                Toast.makeText(Menu.this, "Invalid Tenant ID or Machine ID.", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(Menu.this, "Registering...", Toast.LENGTH_SHORT).show();

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("tenant_id", tenantIdInt);
                    body.put("machine_id", machineIdInt);
                    String resp = HttpUtil.postJson(ApiEndpoints.PROVISION_URL, body.toString());
                    JSONObject j = new JSONObject(resp);

                    if (j.optBoolean("success", false)) {
                        String tn = j.optString("tenant_name", "");
                        String pn = j.optString("phone_number", "");
                        String ak = j.optString("api_key", "");
                        runOnUiThread(() -> {
                            PrefsSecure.saveTenantId(Menu.this, tenantId);
                            PrefsSecure.saveMachineId(Menu.this, machine);
                            PrefsSecure.saveTenantName(Menu.this, tn);
                            PrefsSecure.saveFeedbackNumber(Menu.this, pn);
                            PrefsSecure.saveApiKey(Menu.this, ak);
                            tenant_name.setText(PrefsSecure.getTenantName(Menu.this));
                            admin_layout.setVisibility(View.GONE);
                            Toast.makeText(Menu.this,
                                    "Registered successfully. Tenant: " + tn + ", Phone: " + pn,
                                    Toast.LENGTH_LONG).show();
                            // Reboot only on success so :bg picks up new provisioning
                            showRestartToCompleteDialog();
                        });
                    } else {
                        String err = j.optString("error", "Unknown error");
                        runOnUiThread(() -> Toast.makeText(Menu.this,
                                "Registration failed: " + err,
                                Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    final String displayMsg = getString(e);
                    runOnUiThread(() -> Toast.makeText(Menu.this,
                            "Registration failed: " + displayMsg,
                            Toast.LENGTH_LONG).show());
                }
            });
        });

        btn_admin_mode_ok.setOnClickListener(v -> {

            String pwd = kiosk_admin_password.getText().toString().trim();

            if (!pwd.equals("AYB%0000")) {
                Toast.makeText(Menu.this, "Unauthorised Access!", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(Menu.this, "Admin Mode Enabled!", Toast.LENGTH_SHORT).show();
            admin_mode_layout.setVisibility(View.GONE);
            kiosk_admin_password.setText("");
            enterAdminMode();
        });

        btn_admin_mode_cancel.setOnClickListener(v -> {

           admin_mode_layout.setVisibility(View.GONE);

        });

        btn_admin_close.setOnClickListener(v -> {
            admin_layout.setVisibility(View.GONE);
        });

        btn_start_trip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());

                if(!today.equals(Prefs.getDayDate(Menu.this)) && Prefs.getTripStatus(Menu.this) != 1){
                    String msg = "Day Expired! End Current Day and Start a New One";
                    SpannableString sizedMsg = new SpannableString(msg);
                    // the second parameter "true" means "treat the 18 as SP, not pixels"
                    sizedMsg.setSpan(
                            new AbsoluteSizeSpan(28, true),  // 18sp text size
                            0,                                // start
                            msg.length(),                     // end
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    );
                    sizedMsg.setSpan(
                            new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                            0, msg.length(),
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    );

                    AlertDialog dialog = new AlertDialog.Builder(Menu.this)
                            .setMessage(sizedMsg)
                            .setPositiveButton("OK", null)
                            .show();

                    // 🚫 Prevent dismiss when touching outside or pressing Back
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setCancelable(false);

                    Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

                    // • Increase text size to 20sp
                    okButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

                    // • Change text color to white (for contrast)
                    okButton.setTextColor(Color.WHITE);

                    // • Paint its background blue
                    okButton.setBackgroundColor(Color.BLUE);

                    // (Optional) If you want rounded corners or padding:
                    int pad = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()
                    );
                    okButton.setPadding(pad, pad, pad, pad);
                    okButton.setAllCaps(false);
                    LinearLayout parent = (LinearLayout) okButton.getParent();
                    parent.setGravity(Gravity.CENTER);

                    // (Optional) If you want the button to stretch across and then center its text:
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) okButton.getLayoutParams();
                    params.width = LinearLayout.LayoutParams.MATCH_PARENT;
                    okButton.setLayoutParams(params);
                    okButton.setGravity(Gravity.CENTER);

                    TextView messageView = dialog.findViewById(android.R.id.message);
                    if (messageView != null) {
                        // 3️⃣ convert 16dp to pixels
                        int extraPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                16,
                                getResources().getDisplayMetrics()
                        );

                        // 4️⃣ add it to the bottom padding
                        messageView.setPadding(
                                messageView.getPaddingLeft(),
                                messageView.getPaddingTop(),
                                messageView.getPaddingRight(),
                                messageView.getPaddingBottom() + extraPx
                        );
                    }

                    dialog.getWindow().setBackgroundDrawable(
                            new ColorDrawable(Color.parseColor("#FFAA00"))
                    );

                    // ✅ Add click listener for OK button
                    okButton.setOnClickListener(view -> {
                        dialog.dismiss();
                    });
                    return;

                }


                if (Prefs.getDayStatus(Menu.this) == 1) {
                    startActivity(new Intent(Menu.this, Trip.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                } else {

                    String msg = "No Bus Selected!";
                    SpannableString sizedMsg = new SpannableString(msg);
                    // the second parameter "true" means "treat the 18 as SP, not pixels"
                    sizedMsg.setSpan(
                            new AbsoluteSizeSpan(48, true),  // 18sp text size
                            0,                                // start
                            msg.length(),                     // end
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    );
                    sizedMsg.setSpan(
                            new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                            0, msg.length(),
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    );

                    AlertDialog dialog = new AlertDialog.Builder(Menu.this)
                            .setMessage(sizedMsg)
                            .setPositiveButton("OK", null)
                            .show();

                    Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

                    // • Increase text size to 20sp
                    okButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

                    // • Change text color to white (for contrast)
                    okButton.setTextColor(Color.WHITE);

                    // • Paint its background blue
                    okButton.setBackgroundColor(Color.BLUE);

                    // (Optional) If you want rounded corners or padding:
                    int pad = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics()
                    );
                    okButton.setPadding(pad, pad, pad, pad);
                    okButton.setAllCaps(false);
                    LinearLayout parent = (LinearLayout) okButton.getParent();
                    parent.setGravity(Gravity.CENTER);

                    // (Optional) If you want the button to stretch across and then center its text:
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) okButton.getLayoutParams();
                    params.width = LinearLayout.LayoutParams.MATCH_PARENT;
                    okButton.setLayoutParams(params);
                    okButton.setGravity(Gravity.CENTER);

                    TextView messageView = dialog.findViewById(android.R.id.message);
                    if (messageView != null) {
                        // 3️⃣ convert 16dp to pixels
                        int extraPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                16,
                                getResources().getDisplayMetrics()
                        );

                        // 4️⃣ add it to the bottom padding
                        messageView.setPadding(
                                messageView.getPaddingLeft(),
                                messageView.getPaddingTop(),
                                messageView.getPaddingRight(),
                                messageView.getPaddingBottom() + extraPx
                        );
                    }

                    dialog.getWindow().setBackgroundDrawable(
                            new ColorDrawable(Color.parseColor("#FFAA00"))
                    );


                }
            }
        });

    }

    @Nullable
    private static String getString(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        // If server returned JSON with "error", show that (e.g. "POST failed: HTTP 403 body={...}")
        if (msg != null && msg.contains("body=")) {
            int i = msg.indexOf("body=");
            if (i >= 0) {
                try {
                    String bodyPart = msg.substring(i + 5).trim();
                    JSONObject errJson = new JSONObject(bodyPart);
                    if (errJson.has("error")) {
                        msg = errJson.optString("error", msg);
                    }
                } catch (Exception ignored) { }
            }
        }
        return msg;
    }

}