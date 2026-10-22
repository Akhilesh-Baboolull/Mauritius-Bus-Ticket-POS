package com.ayb.busticketpos;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Password-protected Settings screen, opened from the gear icon on Menu.
 * Manual versions of the automatic jobs: update check / install, master data sync, upload drain.
 */
public class DeviceMaintenanceActivity extends AppCompatActivity {

    private static final String ADMIN_PASSWORD = "0000";
    private static final long REFRESH_INTERVAL_MS = 2000L;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 5L * 60 * 1000;

    private View panelAuth;
    private View panelTools;
    private EditText maintenancePassword;

    private TextView tvInstalledVersion, tvLastUpdateCheck, tvLastUpdateResult, tvUpdateAvailable, tvUpdateProgress;
    private TextView tvMasterLastFetch, tvMasterCounts, tvMasterProgress;
    private TextView queueStatusText, tvLastUpload, tvUploadProgress;
    private TextView tvInternet, tvBattery, tvDayTrip;
    private TextView tvBtmId, tvCompany, tvTenantId, tvFeedbackNumber, tvServer;
    private Button btnCheckUpdates, btnInstallNow, btnSyncMaster, btnSendPending;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean unlocked = false;
    private boolean masterSyncInProgress = false;

    private TextView tvLoginLockout;
    private Button btnUnlock;

    private final Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            if (!unlocked || isFinishing() || isDestroyed()) return;
            refreshAll();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final Runnable lockoutTick = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            if (applyLockoutState()) {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_maintenance);

        panelAuth = findViewById(R.id.panel_auth);
        panelTools = findViewById(R.id.panel_tools);
        maintenancePassword = findViewById(R.id.maintenance_password);

        tvInstalledVersion = findViewById(R.id.tv_installed_version);
        tvLastUpdateCheck = findViewById(R.id.tv_last_update_check);
        tvLastUpdateResult = findViewById(R.id.tv_last_update_result);
        tvUpdateAvailable = findViewById(R.id.tv_update_available);
        tvUpdateProgress = findViewById(R.id.tv_update_progress);
        tvMasterLastFetch = findViewById(R.id.tv_master_last_fetch);
        tvMasterCounts = findViewById(R.id.tv_master_counts);
        tvMasterProgress = findViewById(R.id.tv_master_progress);
        queueStatusText = findViewById(R.id.queue_status_text);
        tvLastUpload = findViewById(R.id.tv_last_upload);
        tvUploadProgress = findViewById(R.id.tv_upload_progress);
        tvInternet = findViewById(R.id.tv_internet);
        tvBattery = findViewById(R.id.tv_battery);
        tvDayTrip = findViewById(R.id.tv_day_trip);
        tvBtmId = findViewById(R.id.tv_btm_id);
        tvCompany = findViewById(R.id.tv_company);
        tvTenantId = findViewById(R.id.tv_tenant_id);
        tvFeedbackNumber = findViewById(R.id.tv_feedback_number);
        tvServer = findViewById(R.id.tv_server);

        btnCheckUpdates = findViewById(R.id.btn_check_updates);
        btnInstallNow = findViewById(R.id.btn_install_now);
        btnSyncMaster = findViewById(R.id.btn_sync_master);
        btnSendPending = findViewById(R.id.btn_send_pending);

        tvLoginLockout = findViewById(R.id.tv_login_lockout);
        btnUnlock = findViewById(R.id.btn_maintenance_unlock);
        btnUnlock.setOnClickListener(v -> tryUnlock());
        maintenancePassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                tryUnlock();
                return true;
            }
            return false;
        });
        findViewById(R.id.btn_maintenance_cancel_auth).setOnClickListener(v -> finish());
        findViewById(R.id.btn_maintenance_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_clear_sync_queue).setOnClickListener(v -> confirmClearQueue());

        btnCheckUpdates.setOnClickListener(v -> checkForUpdates());
        btnInstallNow.setOnClickListener(v -> confirmInstallNow());
        btnSyncMaster.setOnClickListener(v -> syncMasterData());
        btnSendPending.setOnClickListener(v -> sendPendingNow());

        observeUpdateWork();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (unlocked) {
            handler.removeCallbacks(refreshLoop);
            handler.post(refreshLoop);
        } else {
            handler.removeCallbacks(lockoutTick);
            handler.post(lockoutTick);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshLoop);
        handler.removeCallbacks(lockoutTick);
    }

    private void tryUnlock() {
        if (isLockedOut()) {
            applyLockoutState();
            return;
        }
        String pwd = maintenancePassword.getText().toString().trim();
        if (pwd.isEmpty()) {
            return;
        }
        if (!ADMIN_PASSWORD.equals(pwd)) {
            onFailedLogin();
            return;
        }
        Prefs.setSettingsFailedLogins(this, 0);
        Prefs.setSettingsLockedUntil(this, 0L);
        handler.removeCallbacks(lockoutTick);
        hideKeyboard();
        maintenancePassword.setText("");
        unlocked = true;
        panelAuth.setVisibility(View.GONE);
        panelTools.setVisibility(View.VISIBLE);
        fillAbout();
        handler.removeCallbacks(refreshLoop);
        handler.post(refreshLoop);
    }

    // ---------------------------------------------------------------------
    // Failed login lockout
    // ---------------------------------------------------------------------

    private void onFailedLogin() {
        maintenancePassword.setText("");
        int attempts = Prefs.getSettingsFailedLogins(this) + 1;
        Prefs.setSettingsFailedLogins(this, attempts);

        boolean lock = attempts % MAX_FAILED_ATTEMPTS == 0;
        long lockedUntil = 0L;
        if (lock) {
            lockedUntil = System.currentTimeMillis() + LOCKOUT_MS;
            Prefs.setSettingsLockedUntil(this, lockedUntil);
        }

        SyncClient.reportSettingsFailedLogin(this, attempts, lock, lockedUntil);

        if (lock) {
            hideKeyboard();
            handler.removeCallbacks(lockoutTick);
            handler.post(lockoutTick);
        } else {
            int left = MAX_FAILED_ATTEMPTS - (attempts % MAX_FAILED_ATTEMPTS);
            Toast.makeText(this, "Wrong PIN. " + left + " attempt(s) left before lock.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isLockedOut() {
        return remainingLockoutMs() > 0;
    }

    private long remainingLockoutMs() {
        long until = Prefs.getSettingsLockedUntil(this);
        if (until <= 0) return 0;
        long remaining = until - System.currentTimeMillis();
        if (remaining > LOCKOUT_MS) {
            // Clock moved backwards: never lock longer than one lockout period.
            until = System.currentTimeMillis() + LOCKOUT_MS;
            Prefs.setSettingsLockedUntil(this, until);
            remaining = LOCKOUT_MS;
        }
        return Math.max(0, remaining);
    }

    /** Updates the PIN panel for the current lock state. Returns true while still locked. */
    private boolean applyLockoutState() {
        long remaining = remainingLockoutMs();
        boolean locked = remaining > 0;
        maintenancePassword.setEnabled(!locked);
        btnUnlock.setEnabled(!locked);
        if (locked) {
            long totalSec = (remaining + 999) / 1000;
            tvLoginLockout.setText(String.format(Locale.US,
                    "Too many failed attempts.\nTry again in %d:%02d", totalSec / 60, totalSec % 60));
            tvLoginLockout.setVisibility(View.VISIBLE);
        } else {
            if (Prefs.getSettingsLockedUntil(this) > 0) {
                Prefs.setSettingsLockedUntil(this, 0L);
            }
            tvLoginLockout.setVisibility(View.GONE);
        }
        return locked;
    }

    // ---------------------------------------------------------------------
    // Refresh
    // ---------------------------------------------------------------------

    private void refreshAll() {
        boolean online = SyncQueueMaintenance.isOnline(this);

        tvInstalledVersion.setText("Installed version: " + installedVersionLabel());
        tvLastUpdateCheck.setText("Last checked: " + formatTime(Prefs.getLastUpdateCheckAt(this)));
        String result = Prefs.getLastUpdateResult(this);
        tvLastUpdateResult.setText("Last result: " + (result != null ? result : "—"));
        boolean updateAvailable = Prefs.isUpdateAvailable(this);
        if (updateAvailable) {
            String name = Prefs.getUpdateAvailableVersionName(this);
            tvUpdateAvailable.setText("Update available: " + (name != null ? name : "")
                    + " (" + Prefs.getUpdateAvailableVersionCode(this) + ")");
        } else {
            tvUpdateAvailable.setText("Update available: No");
        }
        btnInstallNow.setVisibility(updateAvailable ? View.VISIBLE : View.GONE);

        tvMasterLastFetch.setText("Last downloaded: " + formatTime(PrefsCache.getLastFetchTime(this)));
        tvLastUpload.setText("Last successful upload: " + formatTime(Prefs.getLastUploadSuccessAt(this)));

        tvInternet.setText("Internet: " + (online ? "Connected" : "Not connected"));
        tvBattery.setText("Battery: " + batteryLabel());
        tvDayTrip.setText("Day / Trip: " + dayTripLabel());

        btnCheckUpdates.setEnabled(online);
        btnInstallNow.setEnabled(online);
        btnSyncMaster.setEnabled(online && !masterSyncInProgress);
        btnSendPending.setEnabled(online);

        SyncQueueMaintenance.getQueueCountAsync(this, count ->
                queueStatusText.setText("Waiting to send: " + count + " item(s)"));

        final Context app = getApplicationContext();
        AppExecutors.runBg(() -> {
            int routes = 0;
            int buses = 0;
            try {
                AppDatabase db = AppDatabase.getInstance(app);
                routes = db.routeDao().countAll();
                buses = db.busDao().countAll();
            } catch (Exception ignored) {
            }
            final String counts = routes + " routes · " + buses + " buses";
            runOnUiThread(() -> {
                if (!isDestroyed()) tvMasterCounts.setText("On this device: " + counts);
            });
        });
    }

    private void fillAbout() {
        tvBtmId.setText("BTM ID: " + orDash(PrefsSecure.getMachineId(this)));
        tvCompany.setText("Registered company: " + orDash(PrefsSecure.getTenantName(this)));
        tvTenantId.setText("Tenant ID: " + orDash(PrefsSecure.getTenantId(this)));
        tvFeedbackNumber.setText("Ticket feedback number: " + orDash(PrefsSecure.getFeedbackNumber(this)));
        String host = Uri.parse(ApiEndpoints.BASE).getHost();
        tvServer.setText("Server: " + orDash(host));
    }

    // ---------------------------------------------------------------------
    // App updates
    // ---------------------------------------------------------------------

    private void observeUpdateWork() {
        WorkManager wm = WorkManager.getInstance(getApplicationContext());
        wm.getWorkInfosForUniqueWorkLiveData(UpdateScheduler.UNIQUE_CHECK_ONLY)
                .observe(this, infos -> showWorkState(infos, "Checking with server…"));
        wm.getWorkInfosForUniqueWorkLiveData(UpdateScheduler.UNIQUE_FULL)
                .observe(this, infos -> showWorkState(infos, "Downloading and installing update…"));
    }

    private void showWorkState(@Nullable List<WorkInfo> infos, String runningText) {
        if (infos == null || infos.isEmpty()) return;
        WorkInfo.State state = infos.get(0).getState();
        if (state == WorkInfo.State.RUNNING) {
            showStatus(tvUpdateProgress, runningText);
        } else if (state == WorkInfo.State.ENQUEUED && infos.get(0).getRunAttemptCount() > 0) {
            showStatus(tvUpdateProgress, "Will retry automatically");
        } else if (state.isFinished()) {
            tvUpdateProgress.setVisibility(View.GONE);
        }
        if (unlocked) refreshAll();
    }

    private void checkForUpdates() {
        if (!SyncQueueMaintenance.isOnline(this)) {
            showStatus(tvUpdateProgress, "No internet connection");
            return;
        }
        showStatus(tvUpdateProgress, "Checking with server…");
        UpdateScheduler.enqueueCheckOnly(this, false);
    }

    private void confirmInstallNow() {
        if (Prefs.getTripStatus(this) == 1) {
            showStatus(tvUpdateProgress, "End the current trip before installing an update");
            return;
        }
        String name = Prefs.getUpdateAvailableVersionName(this);
        new AlertDialog.Builder(this)
                .setTitle("Install update now?")
                .setMessage("Version " + (name != null ? name : "")
                        + " will be downloaded and installed. The app restarts when it finishes.")
                .setPositiveButton("Install", (d, w) -> {
                    showStatus(tvUpdateProgress, "Downloading and installing update…");
                    UpdateScheduler.enqueueInstallNow(this);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------------
    // Master data
    // ---------------------------------------------------------------------

    private void syncMasterData() {
        if (Prefs.getTripStatus(this) == 1) {
            showStatus(tvMasterProgress, "End the current trip before syncing routes & fares");
            return;
        }
        if (!SyncQueueMaintenance.isOnline(this)) {
            showStatus(tvMasterProgress, "No internet connection");
            return;
        }
        masterSyncInProgress = true;
        btnSyncMaster.setEnabled(false);
        showStatus(tvMasterProgress, "Downloading routes & fares…");
        DatabaseSyncManager.forceSync(this, (success, message) -> {
            masterSyncInProgress = false;
            showStatus(tvMasterProgress, success
                    ? "Routes & fares updated"
                    : "Sync failed, existing routes & fares kept" + (message != null ? " (" + message + ")" : ""));
            if (unlocked) refreshAll();
        });
    }

    // ---------------------------------------------------------------------
    // Pending uploads
    // ---------------------------------------------------------------------

    private void sendPendingNow() {
        if (!SyncQueueMaintenance.isOnline(this)) {
            showStatus(tvUploadProgress, "No internet connection");
            return;
        }
        showStatus(tvUploadProgress, "Sending pending data…");
        final Context app = getApplicationContext();
        AppExecutors.runBg(() -> SyncQueueUtil.requestDrain(app, true));
    }

    private void confirmClearQueue() {
        new AlertDialog.Builder(this)
                .setTitle("Clear sync queue?")
                .setMessage("This removes all pending offline uploads on this device. They will not be sent to the server.")
                .setPositiveButton("Clear", (d, w) -> clearQueue())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearQueue() {
        showStatus(tvUploadProgress, "Clearing…");
        SyncQueueMaintenance.clearAllQueueManual(this, removed -> {
            showStatus(tvUploadProgress, "Cleared " + removed + " item(s)");
            if (unlocked) refreshAll();
        });
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(maintenancePassword.getWindowToken(), 0);
        }
        maintenancePassword.clearFocus();
    }

    private static void showStatus(TextView tv, String text) {
        tv.setText(text);
        tv.setVisibility(View.VISIBLE);
    }

    private String installedVersionLabel() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + " (" + pi.getLongVersionCode() + ")";
        } catch (Exception e) {
            return "—";
        }
    }

    private String batteryLabel() {
        Intent batt = ContextCompat.registerReceiver(this, null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        if (batt == null) return "—";
        int level = batt.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batt.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = batt.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        String pct = (level >= 0 && scale > 0) ? ((int) (level * 100f / scale)) + "%" : "—";
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) return pct + " · Charging";
        if (status == BatteryManager.BATTERY_STATUS_FULL) return pct + " · Fully charged";
        return pct;
    }

    private String dayTripLabel() {
        if (Prefs.getDayStatus(this) != 1) return "No day started";
        String day = "Day started " + orDash(Prefs.getDayDate(this));
        String bus = Prefs.getSelectedBus(this);
        if (bus != null) day += " · Bus " + bus;
        if (Prefs.getTripStatus(this) == 1) {
            return day + " · Trip " + Prefs.getTripCount(this) + " active";
        }
        return day + " · No active trip";
    }

    private static String formatTime(long millis) {
        if (millis <= 0) return "Never";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private static String orDash(@Nullable String s) {
        return (s == null || s.trim().isEmpty()) ? "—" : s.trim();
    }
}
