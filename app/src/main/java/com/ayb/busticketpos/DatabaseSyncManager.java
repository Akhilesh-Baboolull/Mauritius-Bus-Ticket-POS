package com.ayb.busticketpos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseSyncManager {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static volatile boolean isRunning = false;

    public static void startSync(@NonNull Context ctx, @NonNull AppDatabase db) {
        synchronized (DatabaseSyncManager.class) {
            if (isRunning) {
                Log.w("DB_SYNC", "Sync already running — skipping");
                return;
            }
            isRunning = true;
        }

        EXEC.execute(() -> {
            Handler main = new Handler(Looper.getMainLooper());
            boolean allSuccess = true;
            Gson gson = new Gson();

            try {
                // ---- IDs ----
                int userId, machineId;
                try {
                    userId = Integer.parseInt(PrefsSecure.getTenantId(ctx));
                    machineId = Integer.parseInt(PrefsSecure.getMachineId(ctx));
                } catch (Exception e) {
                    Log.e("DB_SYNC", "Machine not registered / invalid IDs", e);
                    main.post(() -> Toast.makeText(ctx, "Machine Not Registered!", Toast.LENGTH_LONG).show());
                    return;
                }

                if (!PrefsCache.shouldFetchAgain(ctx)) {
                    Log.d("DB_SYNC", "Up to date — skipping");
                    return;
                }

                Log.d("DB_SYNC", "Starting lightweight DB sync...");

                // ---- Clear existing (safe: small datasets) ----
                try {
                    db.routeDao().clearAll();
                    db.stageDao().clearAll();
                    db.tariffDao().clearAll();
                    db.busDao().clearAll();
                } catch (Exception clearEx) {
                    Log.w("DB_SYNC", "Table clear failed (continuing anyway)", clearEx);
                }

                // ---- Payload (reused) ----
                Map<String, Object> payload = new HashMap<>();
                payload.put("user_id", userId);
                payload.put("machine_id", machineId);
                String body = gson.toJson(payload);

                // ---- Tariffs ----
                try {
                    String tariffsJson = HttpUtil.postJson("https://cloud.aybway.com/api/master_tariffs.php", body);
                    if (tariffsJson != null && !tariffsJson.isEmpty()) {
                        Type tariffType = new TypeToken<List<TariffRange>>() {}.getType();
                        List<TariffRange> tariffs = gson.fromJson(tariffsJson, tariffType);
                        if (tariffs != null && !tariffs.isEmpty()) {
                            db.tariffDao().insertAll(tariffs);
                            Log.d("DB_SYNC", "Tariffs: " + tariffs.size());
                        } else {
                            Log.w("DB_SYNC", "Tariffs empty or null");
                        }
                    } else {
                        Log.w("DB_SYNC", "Tariff fetch returned empty response");
                    }
                } catch (Throwable e) {
                    allSuccess = false;
                    Log.e("DB_SYNC", "Tariff fetch failed", e);
                    main.post(() ->
                            Toast.makeText(ctx, "Tariff fetch failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }

                // ---- Routes + Stages ----
                try {
                    String routesJson = HttpUtil.postJson("https://cloud.aybway.com/api/routes.php", body);
                    if (routesJson != null && !routesJson.isEmpty()) {
                        Type rwsType = new TypeToken<List<AppDatabase.RouteWithStages>>() {}.getType();
                        List<AppDatabase.RouteWithStages> rwsList = gson.fromJson(routesJson, rwsType);

                        List<RouteEntity> routes = new ArrayList<>();
                        List<StageEntity> stages = new ArrayList<>();

                        if (rwsList != null) {
                            for (AppDatabase.RouteWithStages rws : rwsList) {
                                if (rws == null) continue;
                                RouteEntity route = new RouteEntity();
                                route.routeId = rws.routeId;
                                route.routeName = rws.routeName;
                                routes.add(route);

                                if (rws.stages != null) {
                                    for (AppDatabase.StagePojo sp : rws.stages) {
                                        if (sp == null) continue;
                                        StageEntity stage = new StageEntity();
                                        stage.routeId = rws.routeId;
                                        stage.stageOrder = sp.stageOrder;
                                        stage.loopIndex = sp.loopIndex;
                                        stage.stageNo = sp.stageNo;
                                        stage.stageName = sp.stageName;
                                        stages.add(stage);
                                    }
                                }
                            }
                        }

                        if (!routes.isEmpty()) db.routeDao().insertAll(routes);
                        if (!stages.isEmpty()) db.stageDao().insertAll(stages);
                        Log.d("DB_SYNC", "Routes=" + routes.size() + " Stages=" + stages.size());
                    } else {
                        Log.w("DB_SYNC", "Routes fetch returned empty response");
                    }
                } catch (Throwable e) {
                    allSuccess = false;
                    Log.e("DB_SYNC", "Routes fetch failed", e);
                    main.post(() ->
                            Toast.makeText(ctx, "Routes fetch failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }

                // ---- Buses ----
                try {
                    String busesJson = HttpUtil.postJson("https://cloud.aybway.com/api/buses.php", body);
                    if (busesJson != null && !busesJson.isEmpty()) {
                        Type busType = new TypeToken<List<BusEntity>>() {}.getType();
                        List<BusEntity> buses = gson.fromJson(busesJson, busType);
                        if (buses != null && !buses.isEmpty()) {
                            db.busDao().insertAll(buses);
                            Log.d("DB_SYNC", "Buses: " + buses.size());
                        } else {
                            Log.w("DB_SYNC", "Buses empty or null");
                        }
                    } else {
                        Log.w("DB_SYNC", "Bus fetch returned empty response");
                    }
                } catch (Throwable e) {
                    allSuccess = false;
                    Log.e("DB_SYNC", "Bus fetch failed", e);
                    main.post(() ->
                            Toast.makeText(ctx, "Bus fetch failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }

                if (allSuccess) {
                    PrefsCache.saveLastFetchTime(ctx, System.currentTimeMillis());
                    Log.d("DB_SYNC", "DB sync complete.");
                    main.post(() -> Toast.makeText(ctx, "Sync completed successfully", Toast.LENGTH_SHORT).show());
                } else {
                    main.post(() -> Toast.makeText(ctx, "Sync completed with some errors", Toast.LENGTH_SHORT).show());
                }

            } catch (Throwable t) {
                // 🔒 Absolute safety net — catch anything unexpected
                Log.e("DB_SYNC", "Unexpected sync error", t);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(ctx, "Sync failed: " + t.getMessage(), Toast.LENGTH_LONG).show()
                );
            } finally {
                isRunning = false;
            }
        });
    }
}
