package com.ayb.busticketpos;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
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
    private View day_status_dot, trip_status_dot;

    LinearLayout overlay;
    ProgressBar progressBar;
    Button btn_start_trip;
    TextView tenant_name;

    private TextView battery_icon, battery_state, battery_percentage, clock_view;
    android.os.Handler clockHandler;
    private Handler bootDelayHandler;
    private BroadcastReceiver batteryReceiver;

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
            AppLog.w("Menu", "App was in background or killed for " + ((now - lastAlive) / 1000) + "s (not a crash)");
        }

        // --- ① Safe-boot warm-up check ---
        if (!Prefs.isBootReady(this)) {
            AppLog.w("MenuInit", "System just rebooted — delaying UI init for 1.5 s");

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
                    AppLog.e("Redirect", "DB check failed", e);
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
        btn_report = findViewById(R.id.btn_report);
        bindStatusBarWidgets();
        refreshBatteryUiImmediate();
        ensureBatteryReceiverRegistered();
        ensureClockRunning();
        tenant_name = findViewById(R.id.tenant_name);
        tenant_name.setText(PrefsSecure.getTenantName(this));
        day_status_dot = findViewById(R.id.day_status_dot);
        trip_status_dot = findViewById(R.id.trip_status_dot);
        setTile(btnPrintTicket, "TICKETING", "Issue tickets for this trip");
        setTile(btn_report, "REPORT", "Print trip and day reports");

        if(Prefs.getDayStatus(this) == 1){
            new Handler(Looper.getMainLooper()).post(this::setupButtons);

            setTile(btn_start_day, "END DAY", endDaySubtitle());

            btn_start_trip.setVisibility(View.VISIBLE);
            btnPrintTicket.setVisibility(View.VISIBLE);
        }
        else{
            setTile(btn_start_day, "START DAY", "Choose a bus to begin the day");
            overlay.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);

            btn_start_trip.setVisibility(View.GONE);
            btnPrintTicket.setVisibility(View.GONE);

            initialiseDB();

        }

        if(Prefs.getTripStatus(this) == 1){
            setTile(btn_start_trip, "STOP TRIP", "Trip " + Prefs.getTripCount(this) + " in progress");
            btnPrintTicket.setVisibility(View.VISIBLE);
            btn_start_day.setVisibility(View.GONE);

        }
        else{
            setTile(btn_start_trip, "BEGIN TRIP", "Choose route and direction");
            btnPrintTicket.setVisibility(View.GONE);
            btn_start_day.setVisibility(View.VISIBLE);
        }
        updateStatusCard();

        View btnSettings = findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v ->
                    startActivity(new Intent(Menu.this, DeviceMaintenanceActivity.class)));
        }
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
        if (tenant_name != null) {
            tenant_name.setText(PrefsSecure.getTenantName(this));
        }
        if (Prefs.getDayStatus(this) == 1) {
            setTile(btn_start_day, "END DAY", endDaySubtitle());
            btn_start_trip.setVisibility(View.VISIBLE);
        } else {
            setTile(btn_start_day, "START DAY", "Choose a bus to begin the day");
            btn_start_trip.setVisibility(View.GONE);
            btnPrintTicket.setVisibility(View.GONE);
        }
        if (Prefs.getTripStatus(this) == 1) {
            setTile(btn_start_trip, "STOP TRIP", "Trip " + Prefs.getTripCount(this) + " in progress");
            btnPrintTicket.setVisibility(View.VISIBLE);
            btn_start_day.setVisibility(View.GONE);
        } else {
            setTile(btn_start_trip, "BEGIN TRIP", "Choose route and direction");
            btnPrintTicket.setVisibility(View.GONE);
            btn_start_day.setVisibility(View.VISIBLE);
        }
        updateStatusCard();
    }

    private static final int STATUS_ACTIVE = Color.parseColor("#2B9B06");
    private static final int STATUS_INACTIVE = Color.parseColor("#B0B4BA");

    /** Day / trip lines in the status card above the tiles. */
    private void updateStatusCard() {
        boolean dayOn = Prefs.getDayStatus(this) == 1;
        boolean tripOn = Prefs.getTripStatus(this) == 1;
        current_day.setText(dayOn ? "Day Started: " + Prefs.getDayDate(this) : "Day not started");
        current_trip.setText(tripOn ? "Current Trip: " + Prefs.getSelectedDirectionName(this) : "No active trip");
        if (day_status_dot != null) {
            day_status_dot.setBackgroundTintList(ColorStateList.valueOf(dayOn ? STATUS_ACTIVE : STATUS_INACTIVE));
        }
        if (trip_status_dot != null) {
            trip_status_dot.setBackgroundTintList(ColorStateList.valueOf(tripOn ? STATUS_ACTIVE : STATUS_INACTIVE));
        }
    }

    private String endDaySubtitle() {
        String bus = Prefs.getSelectedBus(this);
        return (bus != null && !bus.trim().isEmpty()) ? "Bus " + bus : "Close the day and submit the waybill";
    }

    /** Two-line tile label: bold title with a smaller subtitle underneath. */
    private static void setTile(@Nullable Button tile, String title, @Nullable String subtitle) {
        if (tile == null) return;
        SpannableStringBuilder sb = new SpannableStringBuilder(title);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (subtitle != null && !subtitle.isEmpty()) {
            sb.append('\n');
            int start = sb.length();
            sb.append(subtitle);
            sb.setSpan(new RelativeSizeSpan(0.58f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(0xE6FFFFFF), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tile.setText(sb);
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


    private void enableKioskModeIfDeviceOwner(Context context) {
        KioskHelper.applyLockTaskOnly(this);
        blockStatusBarPullDown();
        Log.d("KioskMode", "Kiosk applied (lock task + swipe-down blocked)");
    }

    private void blockStatusBarPullDown() {
        KioskHelper.addStatusBarBlocker(this);
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


    private void setupButtons(){
        // Set an OnClickListener on the button
        btnPrintTicket.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());

                if(!today.equals(Prefs.getDayDate(Menu.this))){
                    AppDialog.with(Menu.this)
                            .type(AppDialog.Type.WARNING)
                            .title("Day Expired!")
                            .message("End Current Trip & Day and Start a New One")
                            .positive("OK", null)
                            .cancelable(false)
                            .show();
                    return;

                }


                if (Prefs.getTripStatus(Menu.this) == 1) {
                    startActivity(new Intent(Menu.this, PrintTicket.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                } else {

                    AppDialog.with(Menu.this)
                            .type(AppDialog.Type.WARNING)
                            .title("No Trip Selected!")
                            .positive("OK", null)
                            .show();
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

        btn_start_trip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String today = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());

                if(!today.equals(Prefs.getDayDate(Menu.this)) && Prefs.getTripStatus(Menu.this) != 1){
                    AppDialog.with(Menu.this)
                            .type(AppDialog.Type.WARNING)
                            .title("Day Expired!")
                            .message("End Current Day and Start a New One")
                            .positive("OK", null)
                            .cancelable(false)
                            .show();
                    return;

                }


                if (Prefs.getDayStatus(Menu.this) == 1) {
                    startActivity(new Intent(Menu.this, Trip.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                } else {

                    AppDialog.with(Menu.this)
                            .type(AppDialog.Type.WARNING)
                            .title("No Bus Selected!")
                            .positive("OK", null)
                            .show();
                }
            }
        });

    }

}