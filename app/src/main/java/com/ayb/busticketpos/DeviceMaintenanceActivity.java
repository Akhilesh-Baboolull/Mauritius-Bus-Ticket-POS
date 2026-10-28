package com.ayb.busticketpos;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.content.ContextCompat;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

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
    /** Second password for registration and exiting kiosk mode (same as the old Menu panels). */
    private static final String REGISTER_PASSWORD = "AYB%0000";
    private static final long REFRESH_INTERVAL_MS = 2000L;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 5L * 60 * 1000;

    private View panelAuth;
    private View panelTools;
    private EditText maintenancePassword;
    private TextInputLayout tilMaintenancePassword;

    private TextView tvInstalledVersion, tvLastUpdateCheck, tvLastUpdateResult, tvUpdateAvailable, tvUpdateProgress;
    private TextView tvMasterLastFetch, tvMasterCounts, tvMasterProgress;
    private TextView queueStatusText, tvLastUpload, tvUploadProgress;
    private TextView tvInternet, tvBattery, tvDayTrip;
    private TextView tvBtmId, tvCompany, tvFeedbackNumber, tvServer;
    private Button btnCheckUpdates, btnInstallNow, btnSyncMaster, btnSendPending;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean unlocked = false;
    private boolean masterSyncInProgress = false;

    private TextView tvLoginLockout;
    private Button btnUnlock;

    private SeekBar seekBrightness;
    private TextView tvBrightnessValue, tvBrightnessNote;

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
        tilMaintenancePassword = findViewById(R.id.til_maintenance_password);
        // Always start at the PIN; status rows are only filled after a successful unlock.
        panelAuth.setVisibility(View.VISIBLE);
        panelTools.setVisibility(View.GONE);

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
        findViewById(R.id.btn_menu_top).setOnClickListener(v -> finish());
        findViewById(R.id.btn_clear_sync_queue).setOnClickListener(v -> confirmClearQueue());
        findViewById(R.id.btn_view_pending).setOnClickListener(v -> showPendingItemsDialog());
        findViewById(R.id.btn_register).setOnClickListener(v -> showRegisterDialog());
        findViewById(R.id.btn_exit_kiosk).setOnClickListener(v -> showExitKioskDialog());
        findViewById(R.id.btn_audit_log).setOnClickListener(v -> {
            SettingsAudit.log(this, SettingsAudit.Event.AUDIT_LOG_VIEWED);
            startActivity(new Intent(this, AuditLogActivity.class));
        });

        btnCheckUpdates.setOnClickListener(v -> checkForUpdates());
        btnInstallNow.setOnClickListener(v -> confirmInstallNow());
        btnSyncMaster.setOnClickListener(v -> syncMasterData());
        btnSendPending.setOnClickListener(v -> sendPendingNow());

        setupBrightnessControl();

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
        SettingsAudit.log(this, SettingsAudit.Event.LOGIN_SUCCESS);
        tilMaintenancePassword.setError(null);
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

        String description = "Wrong PIN (attempt " + attempts + ")"
                + (lock ? ", settings locked for " + (LOCKOUT_MS / 60000) + " minutes" : "");
        JSONObject details = new JSONObject();
        try {
            details.put("attempt_no", attempts)
                    .put("locked", lock)
                    .put("locked_until", lock
                            ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(lockedUntil))
                            : JSONObject.NULL);
        } catch (Exception ignored) {
        }
        SettingsAudit.log(this, SettingsAudit.Event.LOGIN_FAILED, description, details);

        if (lock) {
            hideKeyboard();
            handler.removeCallbacks(lockoutTick);
            handler.post(lockoutTick);
        } else {
            int left = MAX_FAILED_ATTEMPTS - (attempts % MAX_FAILED_ATTEMPTS);
            tilMaintenancePassword.setError("Wrong PIN. " + left + " attempt(s) left before lock.");
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
        tilMaintenancePassword.setEnabled(!locked);
        btnUnlock.setEnabled(!locked);
        if (locked) {
            tilMaintenancePassword.setError(null);
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
        SettingsAudit.log(this, SettingsAudit.Event.UPDATE_CHECK_REQUESTED);
    }

    private void confirmInstallNow() {
        if (Prefs.getTripStatus(this) == 1) {
            showStatus(tvUpdateProgress, "End the current trip before installing an update");
            return;
        }
        String name = Prefs.getUpdateAvailableVersionName(this);
        AppDialog.with(this)
                .type(AppDialog.Type.QUESTION)
                .title("Install update now?")
                .message("Version " + (name != null ? name : "")
                        + " will be downloaded and installed. The app restarts when it finishes.")
                .negative("Cancel", null)
                .positive("Install", () -> {
                    showStatus(tvUpdateProgress, "Downloading and installing update…");
                    UpdateScheduler.enqueueInstallNow(this);
                    SettingsAudit.log(this, SettingsAudit.Event.UPDATE_INSTALL_REQUESTED,
                            "Version " + (name != null ? name : "")
                                    + " (" + Prefs.getUpdateAvailableVersionCode(this) + ")");
                })
                .positiveColor(android.graphics.Color.parseColor("#2B9B06"))
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
            if (success) {
                SettingsAudit.log(this, SettingsAudit.Event.MASTER_SYNC_SUCCESS);
            } else {
                SettingsAudit.log(this, SettingsAudit.Event.MASTER_SYNC_FAILED,
                        message != null ? message : "Unknown error");
            }
            if (unlocked) refreshAll();
        });
    }

    // ---------------------------------------------------------------------
    // Screen brightness
    // ---------------------------------------------------------------------

    private void setupBrightnessControl() {
        seekBrightness = findViewById(R.id.seek_brightness);
        tvBrightnessValue = findViewById(R.id.tv_brightness_value);
        tvBrightnessNote = findViewById(R.id.tv_brightness_note);

        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        boolean canWrite = dpm != null && dpm.isDeviceOwnerApp(getPackageName());

        int current = readSystemBrightness();
        seekBrightness.setProgress(current);
        tvBrightnessValue.setText("Brightness: " + brightnessPercent(current) + "%");

        if (!canWrite) {
            seekBrightness.setEnabled(false);
            showStatus(tvBrightnessNote, "Brightness can only be changed when the app is device owner");
            return;
        }

        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvBrightnessValue.setText("Brightness: " + brightnessPercent(progress) + "%");
                if (fromUser) applySystemBrightness(dpm, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                applySystemBrightness(dpm, seekBar.getProgress());
                SettingsAudit.log(DeviceMaintenanceActivity.this, SettingsAudit.Event.BRIGHTNESS_CHANGED,
                        "Set to " + brightnessPercent(seekBar.getProgress()) + "%");
            }
        });
    }

    /** Device owner write; switches off auto-brightness so the chosen level sticks. */
    private void applySystemBrightness(DevicePolicyManager dpm, int value) {
        try {
            ComponentName admin = new ComponentName(this, MyDeviceAdminReceiver.class);
            if (readBrightnessMode() != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        String.valueOf(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));
                showStatus(tvBrightnessNote, "Auto-brightness turned off");
            }
            dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS, String.valueOf(value));
        } catch (Exception e) {
            showStatus(tvBrightnessNote, "Could not change brightness: " + e.getMessage());
        }
    }

    private int readSystemBrightness() {
        try {
            return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            return 128;
        }
    }

    private int readBrightnessMode() {
        try {
            return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
        } catch (Exception e) {
            return Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;
        }
    }

    private static int brightnessPercent(int value) {
        return Math.round(value * 100f / 255f);
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
        SettingsAudit.log(this, SettingsAudit.Event.PENDING_UPLOAD_REQUESTED);
    }

    private static final int PENDING_VIEW_LIMIT = 200;

    /** Scrollable list of what is waiting in sync_queue (newest first). Tap a row for the full payload. */
    private void showPendingItemsDialog() {
        // Local only: sending this online could itself add a row to the queue being inspected.
        SettingsAudit.log(this, SettingsAudit.Event.PENDING_ITEMS_VIEWED, null, null, false);

        View content = getLayoutInflater().inflate(R.layout.dialog_pending_items, null);
        TextView summary = content.findViewById(R.id.tv_pending_summary);
        ViewGroup container = content.findViewById(R.id.pending_container);
        AppCompatDialog dialog = AppDialog.wrap(this, content, 0.95f);
        content.findViewById(R.id.btn_pending_close).setOnClickListener(v -> AppDialog.dismissSafely(dialog));
        AppDialog.showSafely(this, dialog);

        SyncQueueMaintenance.getQueueCountAsync(this, total ->
                SyncQueueMaintenance.getQueueItemsAsync(this, PENDING_VIEW_LIMIT, items -> {
                    if (isFinishing() || isDestroyed() || !dialog.isShowing()) return;
                    container.removeAllViews();
                    if (items.isEmpty()) {
                        summary.setText("Nothing is waiting to be sent");
                        return;
                    }
                    summary.setText(total > items.size()
                            ? "Showing newest " + items.size() + " of " + total + " item(s)"
                            : total + " item(s) · newest first · tap for details");
                    for (SyncJob job : items) {
                        container.addView(buildPendingRow(container, job));
                    }
                }));
    }

    private View buildPendingRow(ViewGroup parent, SyncJob job) {
        View row = getLayoutInflater().inflate(R.layout.item_pending_job, parent, false);
        TextView type = row.findViewById(R.id.pending_type);
        TextView retries = row.findViewById(R.id.pending_retries);
        TextView time = row.findViewById(R.id.pending_time);
        TextView error = row.findViewById(R.id.pending_error);
        TextView payload = row.findViewById(R.id.pending_payload);

        type.setText(pendingJobLabel(job));
        retries.setText(job.retryCount == 1 ? "1 retry" : job.retryCount + " retries");
        time.setText("Queued " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(job.createdAt)) + " · #" + job.id);

        if (job.lastError != null && !job.lastError.trim().isEmpty()) {
            error.setText("Last error: " + job.lastError.trim());
        } else {
            error.setVisibility(View.GONE);
        }

        String body = job.payloadJson != null ? job.payloadJson : "";
        payload.setText(body);
        row.setOnClickListener(v -> {
            boolean expanded = payload.getMaxLines() == Integer.MAX_VALUE;
            payload.setMaxLines(expanded ? 2 : Integer.MAX_VALUE);
            error.setMaxLines(expanded ? 3 : Integer.MAX_VALUE);
            if (!expanded) {
                try {
                    payload.setText(new JSONObject(body).toString(2));
                } catch (Exception ignored) {
                    payload.setText(body);
                }
            } else {
                payload.setText(body);
            }
        });
        return row;
    }

    private static String pendingJobLabel(SyncJob job) {
        String ep = job.endpoint != null ? job.endpoint : "";
        String label;
        if (ep.equals(ApiEndpoints.CREATE_DAY)) label = "Start day";
        else if (ep.equals(ApiEndpoints.END_DAY)) label = "End day";
        else if (ep.equals(ApiEndpoints.START_TRIP)) label = "Start trip";
        else if (ep.equals(ApiEndpoints.END_TRIP)) label = "End trip";
        else if (ep.equals(ApiEndpoints.RECORD_TICKET)) label = "Ticket";
        else if (ep.equals(ApiEndpoints.BLANK_TICKET)) label = "Blank ticket";
        else if (ep.equals(ApiEndpoints.POSITIONING)) label = "Location";
        else if (ep.equals(ApiEndpoints.REPORT_URL)) label = "Update report";
        else if (ep.equals(ApiEndpoints.SETTINGS_AUDIT)) label = "Audit event";
        else label = ep.startsWith(ApiEndpoints.BASE) ? ep.substring(ApiEndpoints.BASE.length()) : ep;

        if (ep.equals(ApiEndpoints.SETTINGS_AUDIT) && job.payloadJson != null) {
            try {
                String name = new JSONObject(job.payloadJson).optString("event_name", "");
                if (!name.isEmpty()) label += ": " + name;
            } catch (Exception ignored) {
            }
        }
        return label;
    }

    private void confirmClearQueue() {
        AppDialog.with(this)
                .type(AppDialog.Type.WARNING)
                .title("Clear sync queue?")
                .message("This removes all pending offline uploads on this device. They will not be sent to the server.")
                .negative("Cancel", null)
                .positive("Clear", this::clearQueue)
                .positiveColor(android.graphics.Color.parseColor("#D32F2F"))
                .show();
    }

    private void clearQueue() {
        showStatus(tvUploadProgress, "Clearing…");
        SyncQueueMaintenance.clearAllQueueManual(this, removed -> {
            showStatus(tvUploadProgress, "Cleared " + removed + " item(s)");
            SettingsAudit.log(this, SettingsAudit.Event.SYNC_QUEUE_CLEARED,
                    "Removed " + removed + " pending item(s)");
            if (unlocked) refreshAll();
        });
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // Registration (moved from the 15-tap gesture on Menu's version number)
    // ---------------------------------------------------------------------

    /** Rounded card dialog: the layout draws its own background, buttons are part of the layout. */
    private AppCompatDialog createCardDialog(View content) {
        AppCompatDialog dialog = new AppCompatDialog(this);
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown == null) return;
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9f);
            shown.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        });
        return dialog;
    }

    private void showRegisterDialog() {
        View form = getLayoutInflater().inflate(R.layout.dialog_register_device, null);
        TextInputLayout tilPassword = form.findViewById(R.id.til_register_password);
        TextInputLayout tilTenant = form.findViewById(R.id.til_register_tenant);
        TextInputLayout tilMachine = form.findViewById(R.id.til_register_machine);
        EditText etPassword = form.findViewById(R.id.register_admin_password);
        EditText etTenant = form.findViewById(R.id.register_tenant_id);
        EditText etMachine = form.findViewById(R.id.register_machine_id);
        TextView tvStatus = form.findViewById(R.id.register_status);
        MaterialButton btnSubmit = form.findViewById(R.id.btn_register_submit);

        AppCompatDialog dialog = createCardDialog(form);

        Runnable submit = () -> {
            tilPassword.setError(null);
            tilTenant.setError(null);
            tilMachine.setError(null);
            tvStatus.setVisibility(View.GONE);
            submitRegistration(dialog, btnSubmit, tvStatus, tilPassword, tilTenant, tilMachine,
                    etPassword.getText().toString().trim(),
                    etTenant.getText().toString().trim(),
                    etMachine.getText().toString().trim());
        };

        form.findViewById(R.id.btn_register_cancel).setOnClickListener(v -> dialog.dismiss());
        btnSubmit.setOnClickListener(v -> submit.run());
        etMachine.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });
        dialog.show();
        SettingsAudit.log(this, SettingsAudit.Event.REGISTRATION_OPENED);
    }

    private void logRegistrationFailed(String reason, @Nullable String tenantId, @Nullable String machine) {
        JSONObject details = new JSONObject();
        try {
            details.put("entered_tenant_id", tenantId != null && !tenantId.isEmpty() ? tenantId : JSONObject.NULL)
                    .put("entered_machine_id", machine != null && !machine.isEmpty() ? machine : JSONObject.NULL);
        } catch (Exception ignored) {
        }
        SettingsAudit.log(this, SettingsAudit.Event.REGISTRATION_FAILED, reason, details);
    }

    private void submitRegistration(AppCompatDialog dialog, MaterialButton btnSubmit, TextView tvStatus,
                                    TextInputLayout tilPassword, TextInputLayout tilTenant,
                                    TextInputLayout tilMachine,
                                    String pwd, String tenantId, String machine) {
        if (!pwd.equals(REGISTER_PASSWORD)) {
            tilPassword.setError("Unauthorised Access!");
            logRegistrationFailed("Password error", tenantId, machine);
            return;
        }
        if (tenantId.isEmpty() || machine.isEmpty()) {
            if (tenantId.isEmpty()) tilTenant.setError("Tenant ID required");
            if (machine.isEmpty()) tilMachine.setError("Machine ID required");
            logRegistrationFailed("Tenant ID or Machine ID missing", tenantId, machine);
            return;
        }
        int tenantIdInt;
        int machineIdInt;
        try {
            tenantIdInt = Integer.parseInt(tenantId);
            machineIdInt = Integer.parseInt(machine);
        } catch (NumberFormatException e) {
            showStatus(tvStatus, "Invalid Tenant ID or Machine ID.");
            logRegistrationFailed("Invalid Tenant ID or Machine ID", tenantId, machine);
            return;
        }

        hideKeyboard(dialog.getCurrentFocus());
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Registering…");
        Runnable resetButton = () -> {
            btnSubmit.setEnabled(true);
            btnSubmit.setText("Register");
        };
        AppExecutors.runBg(() -> {
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
                        PrefsSecure.saveTenantId(this, tenantId);
                        PrefsSecure.saveMachineId(this, machine);
                        PrefsSecure.saveTenantName(this, tn);
                        PrefsSecure.saveFeedbackNumber(this, pn);
                        PrefsSecure.saveApiKey(this, ak);
                        SettingsAudit.log(this, SettingsAudit.Event.DEVICE_REGISTERED,
                                "Registered as machine " + machine + (tn.isEmpty() ? "" : " for " + tn));
                        // Provisioning is saved above even if the screen was closed meanwhile.
                        if (isFinishing() || isDestroyed()) return;
                        fillAbout();
                        AppDialog.dismissSafely(dialog);
                        Toast.makeText(this,
                                "Registered successfully. Tenant: " + tn + ", Phone: " + pn,
                                Toast.LENGTH_LONG).show();
                        // Reboot only on success so :bg picks up new provisioning
                        showRestartToCompleteDialog();
                    });
                } else {
                    String err = j.optString("error", "Unknown error");
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            logRegistrationFailed(err, tenantId, machine);
                            return;
                        }
                        resetButton.run();
                        showStatus(tvStatus, "Registration failed: " + err);
                        logRegistrationFailed(err, tenantId, machine);
                    });
                }
            } catch (Exception e) {
                final String displayMsg = registrationErrorMessage(e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        logRegistrationFailed(displayMsg, tenantId, machine);
                        return;
                    }
                    resetButton.run();
                    showStatus(tvStatus, "Registration failed: " + displayMsg);
                    logRegistrationFailed(displayMsg, tenantId, machine);
                });
            }
        });
    }

    private void showRestartToCompleteDialog() {
        AppDialog.with(this)
                .type(AppDialog.Type.SUCCESS)
                .title("Registered")
                .message("Restart to complete registration")
                .negative("Later", null)
                .positive("Restart", this::rebootDeviceIfOwner)
                .show();
    }

    /** Reboot so the :bg process picks up new provisioning (tenant/machine id). Device owner only. */
    private void rebootDeviceIfOwner() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(getPackageName())) return;
            dpm.reboot(new ComponentName(this, MyDeviceAdminReceiver.class));
        } catch (Exception e) {
            Toast.makeText(this, "Registered. Please restart the device manually.", Toast.LENGTH_LONG).show();
        }
    }

    /** Shows the server's "error" field when the HTTP error body contains JSON. */
    private static String registrationErrorMessage(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        int i = msg.indexOf("body=");
        if (i >= 0) {
            try {
                JSONObject errJson = new JSONObject(msg.substring(i + 5).trim());
                if (errJson.has("error")) {
                    msg = errJson.optString("error", msg);
                }
            } catch (Exception ignored) {
            }
        }
        return msg;
    }

    // ---------------------------------------------------------------------
    // Exit kiosk / admin mode (moved from the 15-tap gesture on Menu's "Powered By")
    // ---------------------------------------------------------------------

    private void showExitKioskDialog() {
        View form = getLayoutInflater().inflate(R.layout.dialog_admin_password, null);
        TextInputLayout tilPassword = form.findViewById(R.id.til_admin_password);
        EditText etPassword = form.findViewById(R.id.admin_mode_password);

        AppCompatDialog dialog = createCardDialog(form);

        Runnable submit = () -> {
            if (!etPassword.getText().toString().trim().equals(REGISTER_PASSWORD)) {
                tilPassword.setError("Unauthorised Access!");
                SettingsAudit.log(this, SettingsAudit.Event.ADMIN_ACCESS_FAILED, "Password error");
                return;
            }
            tilPassword.setError(null);
            hideKeyboard(etPassword);
            dialog.dismiss();
            SettingsAudit.log(this, SettingsAudit.Event.ADMIN_ACCESS_UNLOCKED,
                    "Kiosk mode turned off and Android settings opened");
            Toast.makeText(this, "Admin Mode Enabled!", Toast.LENGTH_SHORT).show();
            KioskHelper.enterAdminMode(this);
            Toast.makeText(this, "Admin mode activated – you can now access settings",
                    Toast.LENGTH_LONG).show();
        };

        form.findViewById(R.id.btn_admin_cancel).setOnClickListener(v -> dialog.dismiss());
        form.findViewById(R.id.btn_admin_submit).setOnClickListener(v -> submit.run());
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });
        dialog.show();
        SettingsAudit.log(this, SettingsAudit.Event.ADMIN_ACCESS_OPENED);
    }

    private void hideKeyboard() {
        hideKeyboard(maintenancePassword);
        maintenancePassword.clearFocus();
    }

    private void hideKeyboard(@Nullable View focused) {
        if (focused == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
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
