// SyncClient.java
package com.ayb.busticketpos;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Executors;

public final class SyncClient {
    private SyncClient(){}

    public interface Callback {
        void onSuccess(JSONObject resp);
        void onFail(String reason);
    }

    private static int safeInt(String s) throws NumberFormatException {
        return Integer.parseInt(s);
    }

    /** Create Day */
    public static void createDay(Context ctx, String prefsDate_ddMMyy, String busNo, Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int userId    = safeInt(PrefsSecure.getTenantId(ctx));
                int machineId = safeInt(PrefsSecure.getMachineId(ctx));

                String date       = DateFmt.prefsToServerDate(prefsDate_ddMMyy); // yyyy-MM-dd
                String start_time = DateFmt.nowServerDateTime();                  // yyyy-MM-dd HH:mm:ss

                JSONObject payload = new JSONObject()
                        .put("user_id", userId)
                        .put("machine_id", machineId)
                        .put("date", date)
                        .put("bus_no", busNo)
                        .put("start_time", start_time);

                try {
                    String respStr = HttpUtil.postJson(ApiEndpoints.CREATE_DAY, payload.toString());
                    JSONObject resp = new JSONObject(respStr);
                    if ("success".equalsIgnoreCase(resp.optString("status"))) {
                        int serverDayId = resp.optInt("id", 0);
                        if (serverDayId > 0) {
                            Prefs.saveCurrentServerDayID(ctx, serverDayId);
                        }
                        if (cb != null) cb.onSuccess(resp);
                    } else {
                        // server responded fail → enqueue
                        LogSink.logTerminalFailure(ctx, ApiEndpoints.CREATE_DAY, payload.toString(),
                                resp.optString("message", "server fail"));
                        if (cb != null) cb.onFail(resp.optString("message", "server fail"));
                    }
                } catch (Exception netOrParse) {
                    // no feedback → enqueue
                    SyncQueueUtil.enqueue(ctx, ApiEndpoints.CREATE_DAY, payload, netOrParse.getMessage());
                    if (cb != null) cb.onFail(netOrParse.getMessage());
                }

            } catch (Exception e) {
                if (cb != null) cb.onFail(e.getMessage());
            }
        });
    }

    /** End Day */
    public static void endDay(Context ctx,
                              int dayId,
                              String prefsDate_ddMMyy,
                              String busNo,
                              int totalReceipt,
                              int otherReceipt,
                              float diesel,
                              float wages,
                              float wellMeal,
                              float maintenance,
                              float otherExpenses,
                              Callback cb) {

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int userId    = safeInt(PrefsSecure.getTenantId(ctx));
                int machineId = safeInt(PrefsSecure.getMachineId(ctx));

                String date     = DateFmt.prefsToServerDate(prefsDate_ddMMyy);
                String end_time = DateFmt.nowServerDateTime();

                JSONObject payload = new JSONObject()
                        .put("day_id", dayId)
                        .put("user_id", userId)
                        .put("machine_id", machineId)
                        .put("date", date)
                        .put("bus_no", busNo)
                        .put("end_time", end_time)
                        .put("total_receipt", totalReceipt)
                        .put("other_receipt", otherReceipt)
                        .put("diesel", diesel)
                        .put("wages", wages)
                        .put("well_meal", wellMeal)
                        .put("maintenance", maintenance)
                        .put("other_expenses", otherExpenses);

                try {
                    String respStr = HttpUtil.postJson(ApiEndpoints.END_DAY, payload.toString());
                    JSONObject resp = new JSONObject(respStr);
                    if ("success".equalsIgnoreCase(resp.optString("status"))) {
                        if (cb != null) cb.onSuccess(resp);
                    } else {
                        LogSink.logTerminalFailure(ctx, ApiEndpoints.END_DAY, payload.toString(),
                                resp.optString("message", "server fail"));
                        if (cb != null) cb.onFail(resp.optString("message", "server fail"));
                    }
                } catch (Exception netOrParse) {
                    SyncQueueUtil.enqueue(ctx, ApiEndpoints.END_DAY, payload, netOrParse.getMessage());
                    if (cb != null) cb.onFail(netOrParse.getMessage());
                }
            } catch (JSONException | NumberFormatException e) {
                if (cb != null) cb.onFail(e.getMessage());
            }
        });
    }

    /** Start Trip */
    public static void startTrip(Context ctx,
                                 int dayId,
                                 int tripNo,
                                 String routeName,
                                 String routeDirection,
                                 Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int userId    = safeInt(PrefsSecure.getTenantId(ctx));
                int machineId = safeInt(PrefsSecure.getMachineId(ctx));
                String startTime = DateFmt.nowServerTime(); // "HH:mm:ss"

                JSONObject payload = new JSONObject()
                        .put("user_id", userId)
                        .put("day_id", dayId)
                        .put("machine_id", machineId)
                        .put("trip_no", tripNo)
                        .put("route_name", routeName)
                        .put("route_direction", routeDirection)
                        .put("start_time", startTime);

                try {
                    String respStr = HttpUtil.postJson(ApiEndpoints.START_TRIP, payload.toString());
                    JSONObject resp = new JSONObject(respStr);
                    if ("success".equalsIgnoreCase(resp.optString("status"))) {
                        int tripId = resp.optInt("trip_id", 0);
                        if (tripId > 0) {
                            Prefs.saveCurrentServerTripID(ctx, tripId);
                        }
                        if (cb != null) cb.onSuccess(resp);
                    } else {
                        // server responded fail → enqueue
                        LogSink.logTerminalFailure(ctx, ApiEndpoints.CREATE_DAY, payload.toString(),
                                resp.optString("message", "server fail"));
                        if (cb != null) cb.onFail(resp.optString("message", "server fail"));
                    }
                } catch (Exception netOrParse) {
                    // no feedback / non-200 → enqueue
                    SyncQueueUtil.enqueue(ctx, ApiEndpoints.START_TRIP, payload, netOrParse.getMessage());
                    if (cb != null) cb.onFail(netOrParse.getMessage());
                }

            } catch (Exception e) {
                if (cb != null) cb.onFail(e.getMessage());
            }
        });
    }

    /** End Trip */
    public static void endTrip(Context ctx,
                               int tripId,
                               int dayId,
                               int tripNo,
                               Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int userId    = safeInt(PrefsSecure.getTenantId(ctx));
                int machineId = safeInt(PrefsSecure.getMachineId(ctx));
                String endTime = DateFmt.nowServerTime(); // "HH:mm:ss"

                JSONObject payload = new JSONObject()
                        .put("user_id", userId)
                        .put("trip_id", tripId)
                        .put("day_id", dayId)
                        .put("machine_id", machineId)
                        .put("trip_no", tripNo)
                        .put("end_time", endTime);

                try {
                    String respStr = HttpUtil.postJson(ApiEndpoints.END_TRIP, payload.toString());
                    JSONObject resp = new JSONObject(respStr);
                    if ("success".equalsIgnoreCase(resp.optString("status"))) {
                        if (cb != null) cb.onSuccess(resp);
                    } else {
                        LogSink.logTerminalFailure(ctx, ApiEndpoints.END_TRIP, payload.toString(),
                                resp.optString("message", "server fail"));
                        if (cb != null) cb.onFail(resp.optString("message", "server fail"));
                    }
                } catch (Exception netOrParse) {
                    SyncQueueUtil.enqueue(ctx, ApiEndpoints.END_TRIP, payload, netOrParse.getMessage());
                    if (cb != null) cb.onFail(netOrParse.getMessage());
                }
            } catch (Exception e) {
                if (cb != null) cb.onFail(e.getMessage());
            }
        });
    }

    /** Record Ticket */
    public static void recordTicket(
            Context ctx,
            String ticketNo,
            String dateYmd,           // "yyyy-MM-dd"
            String timeHms,           // "HH:mm:ss"
            String startStage,
            String ticketType,        // "TO" or "UP TO"
            String endStage,
            String fareType,          // "Adult" | "Student" | "Child"
            int amount,               // send as int
            Callback cb
    ) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int userId    = safeInt(PrefsSecure.getTenantId(ctx));
                int machineId = safeInt(PrefsSecure.getMachineId(ctx));
                int tripId    = Prefs.getCurrentServerTripID(ctx); // server trip id saved on startTrip success

                // Build payload
                JSONObject payload = new JSONObject()
                        .put("user_id", userId)
                        .put("machine_id", machineId)
                        .put("trip_id", tripId)
                        .put("ticket_no", ticketNo)
                        .put("date", dateYmd)
                        .put("time", timeHms)
                        .put("start_stage", startStage)
                        .put("ticket_type", ticketType)
                        .put("end_stage", endStage)
                        .put("fare_type", fareType)
                        .put("amount", amount);

                try {
                    String respStr = HttpUtil.postJson(ApiEndpoints.RECORD_TICKET, payload.toString());
                    JSONObject resp = new JSONObject(respStr);

                    if ("success".equalsIgnoreCase(resp.optString("status"))) {
                        if (cb != null) cb.onSuccess(resp);
                    } else {
                        // HTTP 200 but server says fail → log terminal, do NOT retry
                        LogSink.logTerminalFailure(
                                ctx,
                                ApiEndpoints.RECORD_TICKET,
                                payload.toString(),
                                resp.optString("message", "server fail")
                        );
                        if (cb != null) cb.onFail(resp.optString("message", "server fail"));
                    }
                } catch (Exception netOrParse) {
                    // Network/HTTP/parsing error → enqueue for retry
                    SyncQueueUtil.enqueue(
                            ctx,
                            ApiEndpoints.RECORD_TICKET,
                            payload,
                            netOrParse.getMessage()
                    );
                    if (cb != null) cb.onFail(netOrParse.getMessage());
                }

            } catch (Exception e) {
                if (cb != null) cb.onFail(e.getMessage());
            }
        });
    }

    /** Push Blank Ticket (e.g. on printer paper-out) */
    public static void recordBlankTicket(Context ctx,
                                         String datetimeYmdHms,   // "yyyy-MM-dd HH:mm:ss"
                                         Callback cb) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int userId    = safeInt(PrefsSecure.getTenantId(ctx));
                int machineId = safeInt(PrefsSecure.getMachineId(ctx));
                int tripId    = Prefs.getCurrentServerTripID(ctx);

                // 🧱 Build JSON payload
                JSONObject payload = new JSONObject()
                        .put("user_id", userId)
                        .put("machine_id", machineId)
                        .put("trip_id", tripId)
                        .put("datetime", datetimeYmdHms);

                try {
                    // 🌐 Direct attempt
                    String respStr = HttpUtil.postJson(ApiEndpoints.BLANK_TICKET, payload.toString());
                    JSONObject resp = new JSONObject(respStr);

                    if ("success".equalsIgnoreCase(resp.optString("status"))) {
                        if (cb != null) cb.onSuccess(resp);
                    } else {
                        // Server replied with fail → log & no retry
                        LogSink.logTerminalFailure(
                                ctx,
                                ApiEndpoints.BLANK_TICKET,
                                payload.toString(),
                                resp.optString("message", "server fail")
                        );
                        if (cb != null) cb.onFail(resp.optString("message", "server fail"));
                    }

                } catch (Exception netOrParse) {
                    // 🌐 Network or JSON parse error → enqueue for later retry
                    SyncQueueUtil.enqueue(ctx, ApiEndpoints.BLANK_TICKET, payload, netOrParse.getMessage());
                    if (cb != null) cb.onFail(netOrParse.getMessage());
                }

            } catch (Exception e) {
                if (cb != null) cb.onFail(e.getMessage());
            }
        });
    }



}
