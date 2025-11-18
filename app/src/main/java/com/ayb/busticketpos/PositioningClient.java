package com.ayb.busticketpos;

import android.content.Context;

import androidx.annotation.Nullable;

import org.json.JSONObject;

public final class PositioningClient {
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
            try {
                JSONObject payload = new JSONObject()
                        .put("date", dateYmdHms)
                        .put("tenant_id", tenantId)
                        .put("machine_id", machineId)
                        .put("busNo", busNo)
                        .put("location", latLng == null || latLng.trim().isEmpty() ? JSONObject.NULL : latLng.trim())
                        .put("current_route", currentRoute == null || currentRoute.trim().isEmpty() ? JSONObject.NULL : currentRoute)
                        .put("tripNo", tripNo == null ? JSONObject.NULL : tripNo)
                        .put("battery_level", batteryLevel == null ? JSONObject.NULL : batteryLevel);

                try {
                    String respStr = HttpUtil.postJson(ApiEndpoints.BASE + "/positioning.php", payload.toString());
                    JSONObject resp = new JSONObject(respStr);
                    if ("success".equalsIgnoreCase(resp.optString("status"))) {
                        if (cb != null) cb.onSuccess(resp);
                    } else {
                        LogSink.logTerminalFailure(ctx, ApiEndpoints.BASE + "/positioning.php",
                                payload.toString(), resp.optString("message", "server fail"));
                        if (cb != null) cb.onFail(resp.optString("message", "server fail"));
                    }
                } catch (Exception netOrParse) {
                    SyncQueueUtil.enqueue(ctx, ApiEndpoints.BASE + "/positioning.php", payload, netOrParse.getMessage());
                    if (cb != null) cb.onFail(netOrParse.getMessage());
                }
            } catch (Exception e) {
                if (cb != null) cb.onFail(e.getMessage());
            }
        });
    }

}
