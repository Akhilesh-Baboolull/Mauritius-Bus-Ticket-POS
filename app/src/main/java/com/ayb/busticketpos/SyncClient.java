// SyncClient.java
package com.ayb.busticketpos;

import android.content.Context;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for server uploads.
 *
 * Day / trip / ticket / blank ticket / end trip / end day are saved to the sync queue only
 * (with LOCAL Room ids) and sent in order by {@link SyncDispatcher}, usually within a few seconds
 * when online. Call these after the local Room insert/update, from the same background thread,
 * so queue order matches the order things happened.
 */
public final class SyncClient {
    private static final String TAG = "SyncClient";

    /** Audit events have their own queue so they never delay ticket / day / trip uploads. */
    private static final ExecutorService EXEC_AUDIT = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SyncClient-audit");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private SyncClient(){}

    public interface Callback {
        void onSuccess(JSONObject resp);
        void onFail(String reason);
    }

    private static int intOrZero(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void queue(Context ctx, String endpoint, JSONObject payload) {
        try {
            SyncQueueUtil.enqueue(ctx.getApplicationContext(), endpoint, payload, null);
        } catch (Exception e) {
            AppLog.e(TAG, "Could not queue " + endpoint, e);
        }
    }

    /** Start Day: local ticket_days row id. */
    public static void createDay(Context ctx, long localDayId) {
        try {
            queue(ctx, ApiEndpoints.CREATE_DAY, SyncPayloads.queued(SyncPayloads.LOCAL_DAY_ID, localDayId));
        } catch (Exception e) {
            AppLog.e(TAG, "createDay queue failed", e);
        }
    }

    /** End Day + waybill. Amounts and end time are fixed now, the server day id is added at send time. */
    public static void endDay(Context ctx,
                              long localDayId,
                              String prefsDate_ddMMyy,
                              String busNo,
                              int totalReceipt,
                              int otherReceipt,
                              float diesel,
                              float wages,
                              float wellMeal,
                              float maintenance,
                              float otherExpenses) {
        try {
            JSONObject payload = SyncPayloads.queued(SyncPayloads.LOCAL_DAY_ID, localDayId)
                    .put("date", DateFmt.prefsToServerDate(prefsDate_ddMMyy))
                    .put("bus_no", busNo)
                    .put("end_time", DateFmt.nowServerDateTime())
                    .put("total_receipt", totalReceipt)
                    .put("other_receipt", otherReceipt)
                    .put("diesel", diesel)
                    .put("wages", wages)
                    .put("well_meal", wellMeal)
                    .put("maintenance", maintenance)
                    .put("other_expenses", otherExpenses);
            queue(ctx, ApiEndpoints.END_DAY, payload);
        } catch (Exception e) {
            AppLog.e(TAG, "endDay queue failed", e);
        }
    }

    /** Start Trip: local trips row id (route, direction, start time are read from the row at send time). */
    public static void startTrip(Context ctx, long localTripId) {
        try {
            queue(ctx, ApiEndpoints.START_TRIP, SyncPayloads.queued(SyncPayloads.LOCAL_TRIP_ID, localTripId));
        } catch (Exception e) {
            AppLog.e(TAG, "startTrip queue failed", e);
        }
    }

    /** End Trip. */
    public static void endTrip(Context ctx, long localTripId, int tripNo, String endTimeHms) {
        try {
            JSONObject payload = SyncPayloads.queued(SyncPayloads.LOCAL_TRIP_ID, localTripId)
                    .put("trip_no", tripNo)
                    .put("end_time", endTimeHms);
            queue(ctx, ApiEndpoints.END_TRIP, payload);
        } catch (Exception e) {
            AppLog.e(TAG, "endTrip queue failed", e);
        }
    }

    /** Record Ticket: local trip id + local ticket id; ticket fields fixed now. */
    public static void recordTicket(
            Context ctx,
            long localTripId,
            long localTicketId,
            String ticketNo,
            String dateYmd,           // "yyyy-MM-dd"
            String timeHms,           // "HH:mm:ss"
            String startStage,
            String ticketType,        // "TO" or "UP TO"
            String endStage,
            String fareType,          // "Adult" | "Student" | "Child"
            int amount
    ) {
        try {
            JSONObject payload = SyncPayloads.queued(SyncPayloads.LOCAL_TRIP_ID, localTripId)
                    .put(SyncPayloads.LOCAL_TICKET_ID, localTicketId)
                    .put("ticket_no", ticketNo)
                    .put("date", dateYmd)
                    .put("time", timeHms)
                    .put("start_stage", startStage)
                    .put("ticket_type", ticketType)
                    .put("end_stage", endStage)
                    .put("fare_type", fareType)
                    .put("amount", amount);
            queue(ctx, ApiEndpoints.RECORD_TICKET, payload);
        } catch (Exception e) {
            AppLog.e(TAG, "recordTicket queue failed", e);
        }
    }

    /** Blank ticket (printer paper-out). Needs a trip; without one it stays on the device only. */
    public static void recordBlankTicket(Context ctx, long localTripId, long localBlankId,
                                         String datetimeYmdHms) {
        if (localTripId <= 0) {
            AppLog.w(TAG, "Blank ticket #" + localBlankId + " has no trip; kept on device only");
            return;
        }
        try {
            JSONObject payload = SyncPayloads.queued(SyncPayloads.LOCAL_TRIP_ID, localTripId)
                    .put(SyncPayloads.LOCAL_BLANK_ID, localBlankId)
                    .put("datetime", datetimeYmdHms);
            queue(ctx, ApiEndpoints.BLANK_TICKET, payload);
        } catch (Exception e) {
            AppLog.e(TAG, "recordBlankTicket queue failed", e);
        }
    }

    /**
     * Send one Settings audit event (built by {@link SettingsAudit}). Queued for retry when offline.
     * The device and tenant IDs are added here so every event carries them.
     */
    public static void sendSettingsAudit(Context ctx, JSONObject event) {
        sendSettingsAudit(ctx, event, true);
    }

    /** @param allowQueue false in the :bg process (no WorkManager there); a failed send is then dropped. */
    public static void sendSettingsAudit(Context ctx, JSONObject event, boolean allowQueue) {
        final Context appCtx = ctx.getApplicationContext();
        EXEC_AUDIT.execute(() -> {
            JSONObject payload;
            try {
                long versionCode = 0;
                try {
                    versionCode = appCtx.getPackageManager()
                            .getPackageInfo(appCtx.getPackageName(), 0)
                            .getLongVersionCode();
                } catch (Exception ignored) {
                }
                payload = new JSONObject(event.toString())
                        .put("tenant_id", intOrZero(PrefsSecure.getTenantId(appCtx)))
                        .put("machine_id", intOrZero(PrefsSecure.getMachineId(appCtx)))
                        .put("app_version_code", versionCode);
            } catch (Throwable e) {
                AppLog.w(TAG, "sendSettingsAudit: payload build failed", e);
                return;
            }

            try {
                String respStr = HttpUtil.postJson(ApiEndpoints.SETTINGS_AUDIT, payload.toString());
                JSONObject resp = HttpUtil.parseJsonObjectLenient(respStr);
                if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                    LogSink.logTerminalFailure(appCtx, ApiEndpoints.SETTINGS_AUDIT,
                            payload.toString(), resp.optString("message", "server fail"));
                }
            } catch (Throwable netOrParse) {
                if (!allowQueue) return;
                try {
                    SyncQueueUtil.enqueue(appCtx, ApiEndpoints.SETTINGS_AUDIT, payload, netOrParse.getMessage());
                } catch (Throwable ignored) {
                }
            }
        });
    }

}
