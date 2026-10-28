package com.ayb.busticketpos;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;

public final class PositioningClient {
    private static final String TAG = "PositioningClient";
    private PositioningClient(){}

    // 🧠 Shared background executor for sending location data
    private static final java.util.concurrent.ExecutorService EXEC =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "PositioningClient-Worker");
                t.setPriority(Thread.MIN_PRIORITY);
                t.setDaemon(true); // make it lightweight and exit-friendly
                return t;
            });


    public interface Callback {
        void onSuccess(JSONObject resp);
        void onFail(String reason);
    }

    public static void send(Context ctx,
                            String dateYmdHms,
                            int tenantId,
                            int machineId,
                            String busNo,
                            @Nullable String latLng,
                            @Nullable String currentRoute,
                            @Nullable Integer tripNo,
                            @Nullable Integer batteryLevel,
                            Callback cb) {

        EXEC.execute(() -> {
            JSONObject payload = null;
            try {
                payload = new JSONObject()
                        .put("date", dateYmdHms)
                        .put("tenant_id", tenantId)
                        .put("machine_id", machineId)
                        .put("busNo", busNo == null ? JSONObject.NULL : busNo)
                        .put("location", latLng == null || latLng.trim().isEmpty() ? JSONObject.NULL : latLng.trim())
                        .put("current_route", currentRoute == null || currentRoute.trim().isEmpty() ? JSONObject.NULL : currentRoute)
                        .put("tripNo", tripNo == null ? JSONObject.NULL : tripNo)
                        .put("battery_level", batteryLevel == null ? JSONObject.NULL : batteryLevel);

                Log.i(TAG, "Sending location to server: date=" + dateYmdHms + " machineId=" + machineId
                        + " busNo=" + busNo + " route=" + currentRoute + " tripNo=" + tripNo
                        + " battery=" + batteryLevel + " location=" + (latLng != null ? latLng : "null"));
                String respStr = HttpUtil.postJson(ApiEndpoints.POSITIONING, payload.toString());
                JSONObject resp = HttpUtil.parseJsonObjectLenient(respStr);
                if ("success".equalsIgnoreCase(resp.optString("status"))) {
                    Log.i(TAG, "Location sent successfully to server");
                    if (cb != null) cb.onSuccess(resp);
                } else {
                    LogSink.logTerminalFailure(ctx, ApiEndpoints.POSITIONING,
                            payload.toString(), resp.optString("message", "server fail"));
                    if (cb != null) cb.onFail(resp.optString("message", "server fail"));
                }
            } catch (Exception e) {
                // Do not queue every failed ping while offline — that filled sync_queue and OOM'd the device.
                // Latest location is sent on the next successful interval when online.
                AppLog.w(TAG, "send skipped (offline or server error): " + e.getMessage());
                if (cb != null) cb.onFail(e.getMessage());
            }
        });
    }

}
