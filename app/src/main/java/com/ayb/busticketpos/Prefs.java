package com.ayb.busticketpos;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** App preferences (file: app_prefs). PrefsCache = cache_prefs; PrefsSecure = secure/device. */
public class Prefs {
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_SELECTED_ROUTE = "selected_route";
    private static final String KEY_DAY_STATUS = "day_status";
    private static final String KEY_DAY_DATE = "day_date";
    private static final String KEY_SELECTED_BUS = "selected_bus";

    // ── Trip Keys ─────────────────────────────────────
    private static final String KEY_TRIP_STATUS    = "trip_status";
    private static final String KEY_TRIP_COUNT     = "trip_count";
    private static final String KEY_SELECTED_ROUTE_NAME = "selected_route_name";
    private static final String KEY_ROUTE_DIRECTION      = "current_route_direction";
    private static final String KEY_SELECTED_DIRECTION_NAME = "current_route_direction_name";

    //─────────────────────────────────────
    private static final String KEY_TICKET_COUNT = "ticket_count";
    private static final String KEY_CURRENT_STAGE_ID = "current_stage_id";
    private static final String KEY_SERVER_CURRENT_DAY_ID = "current_server_day_id";
    private static final String KEY_SERVER_CURRENT_TRIP_ID = "current_server_trip_id";
//    private static final String KEY_DAY_START_TIME = "day_start_time";
    private static final String KEY_DAY_END_TIME = "day_end_time";
    private static final String KEY_CURRENT_TRIP_REPORT_COUNT = "current_trip_report_count";
    private static final String KEY_ALL_TRIPS_REPORT_COUNT = "all_trips_report_count";
    private static final String KEY_CUSTOM_TRIP_REPORT_COUNT = "custom_trip_report_count";
    private static final String KEY_SUMMARY_REPORT_COUNT = "summary_report_count";
    private static final String KEY_HEARTBEAT = "heartbeat";
    /** Set by UpdateWorker before install; cleared in App.onCreate to run DB sync after update relaunch. */
    private static final String KEY_RUN_DB_SYNC_ON_NEXT_LAUNCH = "run_db_sync_on_next_launch";
    /** Set on install success; UnlockReceiver brings app to front on USER_PRESENT and clears this. */
    private static final String KEY_PENDING_RELAUNCH_AFTER_UNLOCK = "pending_relaunch_after_unlock";
    /** One-time: after cancelAllWork() to clear job flood on device-owner devices where Clear data is greyed out. */
    private static final String KEY_WORKMANAGER_FLOOD_CLEANED_V1 = "workmanager_flood_cleaned_v1";
    /** Set before install so PackageReplacedReceiver can report INSTALL_SUCCESS (releaseId, fromV, toV). */
    private static final String KEY_PENDING_INSTALL_REPORT = "pending_install_report";
    private static final String KEY_PENDING_INSTALL_RELEASE_ID = "pending_install_release_id";
    private static final String KEY_PENDING_INSTALL_FROM_V = "pending_install_from_v";
    private static final String KEY_PENDING_INSTALL_TO_V = "pending_install_to_v";
    /** Reset on boot; LockFirstActivity shows loading UI only when this is false, then sets true (once per boot). */
    private static final String KEY_LOCK_FIRST_SHOWN_THIS_BOOT = "lock_first_shown_this_boot";
    /** When UpdateWorker last completed an update check (any outcome). Used for launch fallback: if > 20h ago, enqueue check. */
    private static final String KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at";
    private static final String KEY_SYNC_QUEUE_DAY_PURGE = "sync_queue_day_purge";

    private static final String KEY_UPDATE_AVAILABLE = "update_available";
    private static final String KEY_UPDATE_AVAILABLE_VERSION_CODE = "update_available_version_code";
    private static final String KEY_UPDATE_AVAILABLE_VERSION_NAME = "update_available_version_name";
    private static final String KEY_LAST_UPDATE_RESULT = "last_update_result";
    private static final String KEY_LAST_NIGHTLY_UPDATE_RUN_AT = "last_nightly_update_run_at";
    private static final String KEY_LAST_UPLOAD_SUCCESS_AT = "last_upload_success_at";
    private static final String KEY_SETTINGS_FAILED_LOGINS = "settings_failed_logins";
    private static final String KEY_SETTINGS_LOCKED_UNTIL = "settings_locked_until";

    /** Cross-process flag for :bg location (SharedPreferences are not reliable across processes). */
    private static final String POSITIONING_DAY_ACTIVE_FILE = "positioning_day_active";
    private static final String POSITIONING_DAY_DATE_FILE = "positioning_day_date";

    public static void setLastAlive(Context ctx, long millis) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_HEARTBEAT, millis).commit();
    }

    public static long getLastAlive(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_HEARTBEAT, 0L);
    }

    public static void initReportCount(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit().putInt(KEY_CURRENT_TRIP_REPORT_COUNT, 0).commit();
        prefs.edit().putInt(KEY_ALL_TRIPS_REPORT_COUNT, 0).commit();
        prefs.edit().putInt(KEY_CUSTOM_TRIP_REPORT_COUNT, 0).commit();
        prefs.edit().putInt(KEY_SUMMARY_REPORT_COUNT, 0).commit();

    }

    public static int getCurrentTripCount(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_CURRENT_TRIP_REPORT_COUNT, 0);
    }

    public static int getAllTripsCount(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_ALL_TRIPS_REPORT_COUNT, 0);
    }

    public static int getCustomTripCount(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_CUSTOM_TRIP_REPORT_COUNT, 0);
    }

    public static int getSummaryTripCount(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SUMMARY_REPORT_COUNT, 0);
    }

    public static void incrementCurrentTrip(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_CURRENT_TRIP_REPORT_COUNT, prefs.getInt(KEY_CURRENT_TRIP_REPORT_COUNT,0) + 1).commit();
    }

    public static void incrementAllTrips(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_ALL_TRIPS_REPORT_COUNT, prefs.getInt(KEY_ALL_TRIPS_REPORT_COUNT,0) + 1).commit();
    }

    public static void incrementCustomTrip(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_CUSTOM_TRIP_REPORT_COUNT, prefs.getInt(KEY_CUSTOM_TRIP_REPORT_COUNT,0) + 1).commit();
    }

    public static void incrementSummary(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SUMMARY_REPORT_COUNT, prefs.getInt(KEY_SUMMARY_REPORT_COUNT,0) + 1).commit();
    }

    public static int getCurrentServerDayID(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SERVER_CURRENT_DAY_ID, 0);
    }

    public static void saveCurrentServerDayID(Context ctx, int dayID){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SERVER_CURRENT_DAY_ID, dayID).commit();
    }

    public static int getCurrentServerTripID(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SERVER_CURRENT_TRIP_ID, 0);
    }

    public static void saveCurrentServerTripID(Context ctx, int tripID){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SERVER_CURRENT_TRIP_ID, tripID).commit();
    }

    public static int getCurrentStageID(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_CURRENT_STAGE_ID, 0);
    }

    public static void saveCurrentStageID(Context ctx, int stageID){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_CURRENT_STAGE_ID, stageID).commit();
    }

    public static int getTicketCount(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_TICKET_COUNT, 0);
    }

    public static void saveTicketCount(Context ctx, int ticket_count){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_TICKET_COUNT, ticket_count).commit();
    }

    public static void SaveDay(Context ctx, String date, int day_status){

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DAY_DATE, date).commit();
        prefs.edit().putInt(KEY_DAY_STATUS, day_status).commit();
        prefs.edit().putInt(KEY_TRIP_STATUS, 0).commit();
        prefs.edit().putInt(KEY_TRIP_COUNT, 0).commit();
        prefs.edit().putInt(KEY_TICKET_COUNT, 0).commit();
        prefs.edit().putString(KEY_DAY_END_TIME, "").commit();

        initReportCount(ctx);
        if (day_status == 1) {
            publishPositioningDayState(ctx, true, date);
        }
    }

    public static String getDayDate(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DAY_DATE, null);
    }

    public static int getDayStatus(Context ctx){
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_DAY_STATUS, -1);
    }

    public static void clearDay(Context ctx){

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_DAY_STATUS)
                .remove(KEY_TRIP_STATUS)
                .remove(KEY_TRIP_COUNT)
                .remove(KEY_TICKET_COUNT)
                .remove(KEY_DAY_DATE)
                .remove(KEY_SELECTED_BUS)
                .remove(KEY_SELECTED_ROUTE)
                .remove(KEY_SELECTED_ROUTE_NAME)
                .remove(KEY_ROUTE_DIRECTION)
                .remove(KEY_SELECTED_DIRECTION_NAME)
                .remove(KEY_CURRENT_STAGE_ID)
                .remove(KEY_SERVER_CURRENT_DAY_ID)
                .remove(KEY_SERVER_CURRENT_TRIP_ID)
                .commit();
        publishPositioningDayState(ctx, false, null);
    }

    /**
     * Written on the main process when day starts/ends; read by {@code :bg} for interval positioning.
     */
    public static void publishPositioningDayState(Context ctx, boolean dayStarted, @Nullable String dayDateDdMmYy) {
        Context app = ctx.getApplicationContext();
        try {
            try (FileOutputStream fos = app.openFileOutput(POSITIONING_DAY_ACTIVE_FILE, Context.MODE_PRIVATE)) {
                fos.write(dayStarted ? "1".getBytes(StandardCharsets.UTF_8) : "0".getBytes(StandardCharsets.UTF_8));
            }
            if (dayDateDdMmYy != null) {
                try (FileOutputStream fos = app.openFileOutput(POSITIONING_DAY_DATE_FILE, Context.MODE_PRIVATE)) {
                    fos.write(dayDateDdMmYy.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                File dateFile = new File(app.getFilesDir(), POSITIONING_DAY_DATE_FILE);
                if (dateFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dateFile.delete();
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean isPositioningDayStarted(Context ctx) {
        try {
            File f = new File(ctx.getApplicationContext().getFilesDir(), POSITIONING_DAY_ACTIVE_FILE);
            if (!f.exists()) return false;
            try (FileInputStream in = new FileInputStream(f)) {
                int b = in.read();
                return b == '1';
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public static String getPositioningDayDate(Context ctx) {
        try {
            File f = new File(ctx.getApplicationContext().getFilesDir(), POSITIONING_DAY_DATE_FILE);
            if (!f.exists()) return null;
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[(int) Math.min(f.length(), 32)];
                int n = in.read(buf);
                if (n <= 0) return null;
                return new String(buf, 0, n, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Save the selected bus number */
    public static void saveSelectedBus(Context ctx, String busNo) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_SELECTED_BUS, busNo)
                .commit();
    }

    /** Retrieve the saved bus number, or null */
    public static String getSelectedBus(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SELECTED_BUS, null);
    }

    /** Clear the saved bus number */
    public static void clearSelectedBus(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_SELECTED_BUS)
                .commit();
    }

    /** Save the selected route ID */
    public static void saveSelectedRoute(Context ctx, String routeId) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_SELECTED_ROUTE, routeId)
                .commit();
    }

    /** Get the saved route ID */
    public static String getSelectedRoute(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SELECTED_ROUTE, null);
    }

    /** Save the selected route *name* */
    public static void saveSelectedRouteName(Context ctx, String routeName) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_SELECTED_ROUTE_NAME, routeName)
                .commit();
    }

    /** Get the saved route *name* */
    public static String getSelectedRouteName(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SELECTED_ROUTE_NAME, null);
    }

    /** Clear route ID & name */
//    public static void clearSelectedRoute(Context ctx) {
//        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
//        prefs.edit()
//                .remove(KEY_SELECTED_ROUTE)
//                .remove(KEY_SELECTED_ROUTE_NAME)
//                .commit();
//    }

    /** Save the current trip status (0 or 1) */
    public static void saveTripStatus(Context ctx, int status) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_TRIP_STATUS, status).commit();
    }
    /** Get the current trip status (default 0) */
    public static int getTripStatus(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_TRIP_STATUS, 0);
    }

    /** Save the total number of trips so far */
    public static void saveTripCount(Context ctx, int count) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_TRIP_COUNT, count).commit();
    }
    /** Get how many trips have been started (default 0) */
    public static int getTripCount(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_TRIP_COUNT, 0);
    }

    /** Clear trip status & count */
    public static void clearTrip(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_TRIP_STATUS)
                .remove(KEY_SELECTED_ROUTE)
                .remove(KEY_SELECTED_ROUTE_NAME)
                .remove(KEY_ROUTE_DIRECTION)
                .remove(KEY_SELECTED_DIRECTION_NAME)
                .commit();
    }

    /** Save the chosen direction: 1 or –1 */
    public static void saveRouteDirection(Context ctx, int direction) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_ROUTE_DIRECTION, direction)
                .commit();
    }

    /** Get the saved direction (default 0 = none) */
    public static int getRouteDirection(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_ROUTE_DIRECTION, 0);
    }

    /** Clear the saved direction */
//    public static void clearRouteDirection(Context ctx) {
//        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
//        prefs.edit()
//                .remove(KEY_ROUTE_DIRECTION)
//                .commit();
//    }

    public static void saveDirectionName(Context ctx, String directionName) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_SELECTED_DIRECTION_NAME, directionName)
                .commit();
    }

    public static String getSelectedDirectionName(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SELECTED_DIRECTION_NAME, "");
    }

    // Boot-ready flag utilities
    public static boolean isBootReady(Context ctx) {
        return ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getBoolean("boot_ready", false);
    }

    public static void setBootReady(Context ctx, boolean ready) {
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("boot_ready", ready)
                .commit();
    }

    /** Set when an update is being installed so the relaunched app runs DB sync once. */
    public static void setRunDbSyncOnNextLaunch(Context ctx, boolean run) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RUN_DB_SYNC_ON_NEXT_LAUNCH, run)
                .apply();
    }

    /** Returns true if DB sync was requested for next launch, and clears the flag. */
    public static boolean getAndClearRunDbSyncOnNextLaunch(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean value = prefs.getBoolean(KEY_RUN_DB_SYNC_ON_NEXT_LAUNCH, false);
        if (value) prefs.edit().putBoolean(KEY_RUN_DB_SYNC_ON_NEXT_LAUNCH, false).apply();
        return value;
    }

    public static boolean getWorkManagerFloodCleaned(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_WORKMANAGER_FLOOD_CLEANED_V1, false);
    }

    public static void setWorkManagerFloodCleaned(Context ctx, boolean done) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_WORKMANAGER_FLOOD_CLEANED_V1, done)
                .apply();
    }

    /** Set when an update install succeeded so we bring the app to front on next unlock. Uses commit() so it persists before process may be killed. */
    public static void setPendingRelaunchAfterUnlock(Context ctx, boolean pending) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING_RELAUNCH_AFTER_UNLOCK, pending)
                .commit();
    }

    /** Returns true if we should bring app to front after unlock (post-update), and clears the flag. */
    public static boolean getAndClearPendingRelaunchAfterUnlock(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean value = prefs.getBoolean(KEY_PENDING_RELAUNCH_AFTER_UNLOCK, false);
        if (value) prefs.edit().putBoolean(KEY_PENDING_RELAUNCH_AFTER_UNLOCK, false).apply();
        return value;
    }

    /** Set before starting install so PackageReplacedReceiver can report INSTALL_SUCCESS. Uses commit() so it survives process kill. */
    public static void setPendingInstallReportContext(Context ctx, int releaseId, int fromV, int toV) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PENDING_INSTALL_REPORT, true)
                .putInt(KEY_PENDING_INSTALL_RELEASE_ID, releaseId)
                .putInt(KEY_PENDING_INSTALL_FROM_V, fromV)
                .putInt(KEY_PENDING_INSTALL_TO_V, toV)
                .commit();
    }

    /** Returns {releaseId, fromV, toV} if a pending install report was set, then clears. Otherwise null. */
    public static int[] getAndClearPendingInstallReportContext(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_PENDING_INSTALL_REPORT, false)) return null;
        int[] out = new int[]{
                prefs.getInt(KEY_PENDING_INSTALL_RELEASE_ID, -1),
                prefs.getInt(KEY_PENDING_INSTALL_FROM_V, -1),
                prefs.getInt(KEY_PENDING_INSTALL_TO_V, -1)
        };
        prefs.edit()
                .remove(KEY_PENDING_INSTALL_REPORT)
                .remove(KEY_PENDING_INSTALL_RELEASE_ID)
                .remove(KEY_PENDING_INSTALL_FROM_V)
                .remove(KEY_PENDING_INSTALL_TO_V)
                .apply();
        return out;
    }

    /** Reset on each boot (call from BootReceiver). LockFirstActivity shows loading only when this is false. */
    public static void setLockFirstShownThisBoot(Context ctx, boolean shown) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LOCK_FIRST_SHOWN_THIS_BOOT, shown)
                .apply();
    }

    public static boolean getLockFirstShownThisBoot(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_LOCK_FIRST_SHOWN_THIS_BOOT, false);
    }

    public static void setLastUpdateCheckAt(Context ctx, long millis) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_UPDATE_CHECK_AT, millis)
                .apply();
    }

    public static long getLastUpdateCheckAt(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_UPDATE_CHECK_AT, 0L);
    }

    /** yyyy-MM-dd of last calendar-day sync queue purge (see SyncQueueMaintenance). */
    public static String getSyncQueueDayPurgeKey(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SYNC_QUEUE_DAY_PURGE, "");
    }

    public static void setSyncQueueDayPurgeKey(Context ctx, String yyyyMmDd) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SYNC_QUEUE_DAY_PURGE, yyyyMmDd)
                .apply();
    }

    /** Set by a check-only update run when the server reports a newer release. */
    public static void setUpdateAvailable(Context ctx, int versionCode, @Nullable String versionName) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UPDATE_AVAILABLE, true)
                .putInt(KEY_UPDATE_AVAILABLE_VERSION_CODE, versionCode)
                .putString(KEY_UPDATE_AVAILABLE_VERSION_NAME, versionName)
                .apply();
    }

    public static void clearUpdateAvailable(Context ctx) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_UPDATE_AVAILABLE, false)
                .remove(KEY_UPDATE_AVAILABLE_VERSION_CODE)
                .remove(KEY_UPDATE_AVAILABLE_VERSION_NAME)
                .apply();
    }

    public static boolean isUpdateAvailable(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_UPDATE_AVAILABLE, false);
    }

    public static int getUpdateAvailableVersionCode(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_UPDATE_AVAILABLE_VERSION_CODE, 0);
    }

    @Nullable
    public static String getUpdateAvailableVersionName(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_UPDATE_AVAILABLE_VERSION_NAME, null);
    }

    public static void setLastUpdateResult(Context ctx, String result) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_UPDATE_RESULT, result)
                .apply();
    }

    @Nullable
    public static String getLastUpdateResult(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_UPDATE_RESULT, null);
    }

    /** When a FULL (nightly / install-now) update run last reached the server. Drives the 23:59 fallback. */
    public static void setLastNightlyUpdateRunAt(Context ctx, long millis) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_NIGHTLY_UPDATE_RUN_AT, millis)
                .apply();
    }

    public static long getLastNightlyUpdateRunAt(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_NIGHTLY_UPDATE_RUN_AT, 0L);
    }

    public static void setLastUploadSuccessAt(Context ctx, long millis) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_UPLOAD_SUCCESS_AT, millis)
                .apply();
    }

    public static long getLastUploadSuccessAt(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_UPLOAD_SUCCESS_AT, 0L);
    }

    /** Consecutive failed Settings PIN attempts; reset on successful login. */
    public static int getSettingsFailedLogins(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_SETTINGS_FAILED_LOGINS, 0);
    }

    public static void setSettingsFailedLogins(Context ctx, int count) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SETTINGS_FAILED_LOGINS, count)
                .commit();
    }

    public static long getSettingsLockedUntil(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_SETTINGS_LOCKED_UNTIL, 0L);
    }

    public static void setSettingsLockedUntil(Context ctx, long millis) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_SETTINGS_LOCKED_UNTIL, millis)
                .commit();
    }

}