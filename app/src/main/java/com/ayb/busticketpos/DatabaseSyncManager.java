package com.ayb.busticketpos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    private static final String TAG = "DB_SYNC";
    private static final String MSG_SYNC_OK = "Sync completed successfully";
    private static final String MSG_SYNC_FAILED = "Failed to Sync";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DB_SYNC");
        return t;
    });
    private static volatile boolean isRunning = false;

    /** Optional UI callback (runs on main thread). */
    public interface SyncListener {
        void onComplete(boolean success, @Nullable String message);
    }

    public static void startSync(@NonNull Context ctx, @NonNull AppDatabase db) {
        startSync(ctx, db, false, null);
    }

    /** Manual refresh from Menu (logo tap): always fetch; keep old data if fetch fails. */
    public static void forceSync(@NonNull Context ctx, @Nullable SyncListener listener) {
        PrefsCache.saveLastFetchTime(ctx, 0L);
        startSync(ctx.getApplicationContext(), AppDatabase.getInstance(ctx), true, listener);
    }

    public static void startSync(@NonNull Context ctx, @NonNull AppDatabase db,
                                 boolean force, @Nullable SyncListener listener) {
        synchronized (DatabaseSyncManager.class) {
            if (isRunning) {
                Log.w(TAG, "Sync already running — skipping");
                notifyListener(listener, false, "Sync already in progress");
                return;
            }
            isRunning = true;
        }

        EXEC.execute(() -> {
            Handler main = new Handler(Looper.getMainLooper());
            boolean success = false;
            String userMessage = null;
            boolean showCompletionToast = false;

            try {
                int userId;
                int machineId;
                try {
                    userId = Integer.parseInt(PrefsSecure.getTenantId(ctx));
                    machineId = Integer.parseInt(PrefsSecure.getMachineId(ctx));
                } catch (Exception e) {
                    Log.e(TAG, "Machine not registered / invalid IDs", e);
                    main.post(() -> Toast.makeText(ctx, "Machine Not Registered!", Toast.LENGTH_LONG).show());
                    return;
                }

                if (!force && !PrefsCache.shouldFetchAgain(ctx)) {
                    Log.d(TAG, "Up to date — skipping");
                    success = true;
                    return;
                }

                showCompletionToast = true;
                Log.i(TAG, "Fetching master data from server (no local delete until success)...");

                Map<String, Object> payload = new HashMap<>();
                payload.put("user_id", userId);
                payload.put("machine_id", machineId);
                String body = new Gson().toJson(payload);

                String fetchError = null;

                List<TariffRange> tariffs = fetchTariffs(body);
                if (tariffs == null) {
                    fetchError = "tariffs";
                }

                RoutesStagesBundle routesStages = null;
                if (fetchError == null) {
                    routesStages = fetchRoutesAndStages(body);
                    if (routesStages == null) {
                        fetchError = "routes/stages";
                    }
                }

                List<BusEntity> buses = null;
                if (fetchError == null) {
                    buses = fetchBuses(body);
                    if (buses == null) {
                        fetchError = "buses";
                    }
                }

                if (fetchError != null) {
                    Log.w(TAG, "Master data fetch failed (" + fetchError + ") — keeping existing DB");
                    userMessage = MSG_SYNC_FAILED;
                    return;
                }

                if (!isValidMasterData(tariffs, routesStages.routes, routesStages.stages, buses)) {
                    Log.w(TAG, "Master data incomplete from server — keeping existing DB");
                    userMessage = MSG_SYNC_FAILED;
                    return;
                }

                final List<TariffRange> tariffsFinal = tariffs;
                final List<RouteEntity> routesFinal = routesStages.routes;
                final List<StageEntity> stagesFinal = routesStages.stages;
                final List<BusEntity> busesFinal = buses;

                db.runInTransaction(() -> {
                    db.routeDao().clearAll();
                    db.stageDao().clearAll();
                    db.tariffDao().clearAll();
                    db.busDao().clearAll();
                    db.tariffDao().insertAll(tariffsFinal);
                    db.routeDao().insertAll(routesFinal);
                    db.stageDao().insertAll(stagesFinal);
                    db.busDao().insertAll(busesFinal);
                });

                PrefsCache.saveLastFetchTime(ctx, System.currentTimeMillis());
                success = true;
                userMessage = MSG_SYNC_OK;
                Log.i(TAG, "Master data replaced: tariffs=" + tariffsFinal.size()
                        + " routes=" + routesFinal.size()
                        + " stages=" + stagesFinal.size()
                        + " buses=" + busesFinal.size());

            } catch (Throwable t) {
                Log.e(TAG, "Unexpected sync error — keeping existing DB", t);
                userMessage = MSG_SYNC_FAILED;
                showCompletionToast = true;
            } finally {
                isRunning = false;
                boolean finalSuccess = success;
                String finalMessage = userMessage;
                if (showCompletionToast && finalMessage != null) {
                    main.post(() -> Toast.makeText(ctx, finalMessage, Toast.LENGTH_SHORT).show());
                }
                if (listener != null) {
                    main.post(() -> listener.onComplete(finalSuccess, finalMessage));
                }
            }
        });
    }

    private static void notifyListener(@Nullable SyncListener listener, boolean success, @Nullable String message) {
        if (listener == null) return;
        new Handler(Looper.getMainLooper()).post(() -> listener.onComplete(success, message));
    }

    private static boolean isValidMasterData(List<TariffRange> tariffs,
                                            List<RouteEntity> routes,
                                            List<StageEntity> stages,
                                            List<BusEntity> buses) {
        return tariffs != null && !tariffs.isEmpty()
                && routes != null && !routes.isEmpty()
                && stages != null && !stages.isEmpty()
                && buses != null && !buses.isEmpty();
    }

    @Nullable
    private static List<TariffRange> fetchTariffs(String body) {
        try {
            String json = HttpUtil.postJson(ApiEndpoints.MASTER_TARIFFS, body);
            if (json == null || json.trim().isEmpty()) {
                Log.w(TAG, "Tariff fetch: empty response");
                return null;
            }
            Type type = new TypeToken<List<TariffRange>>() {}.getType();
            List<TariffRange> list = new Gson().fromJson(json, type);
            if (list == null || list.isEmpty()) {
                Log.w(TAG, "Tariff fetch: no rows");
                return null;
            }
            return list;
        } catch (Throwable e) {
            Log.e(TAG, "Tariff fetch failed", e);
            return null;
        }
    }

    private static final class RoutesStagesBundle {
        final List<RouteEntity> routes;
        final List<StageEntity> stages;

        RoutesStagesBundle(List<RouteEntity> routes, List<StageEntity> stages) {
            this.routes = routes;
            this.stages = stages;
        }
    }

    @Nullable
    private static RoutesStagesBundle fetchRoutesAndStages(String body) {
        try {
            String json = HttpUtil.postJson(ApiEndpoints.ROUTES, body);
            if (json == null || json.trim().isEmpty()) {
                Log.w(TAG, "Routes fetch: empty response");
                return null;
            }
            Type type = new TypeToken<List<AppDatabase.RouteWithStages>>() {}.getType();
            List<AppDatabase.RouteWithStages> rwsList = new Gson().fromJson(json, type);

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

            if (routes.isEmpty() || stages.isEmpty()) {
                Log.w(TAG, "Routes fetch: routes=" + routes.size() + " stages=" + stages.size());
                return null;
            }
            return new RoutesStagesBundle(routes, stages);
        } catch (Throwable e) {
            Log.e(TAG, "Routes fetch failed", e);
            return null;
        }
    }

    @Nullable
    private static List<BusEntity> fetchBuses(String body) {
        try {
            String json = HttpUtil.postJson(ApiEndpoints.BUSES, body);
            if (json == null || json.trim().isEmpty()) {
                Log.w(TAG, "Bus fetch: empty response");
                return null;
            }
            Type type = new TypeToken<List<BusEntity>>() {}.getType();
            List<BusEntity> list = new Gson().fromJson(json, type);
            if (list == null || list.isEmpty()) {
                Log.w(TAG, "Bus fetch: no rows");
                return null;
            }
            return list;
        } catch (Throwable e) {
            Log.e(TAG, "Bus fetch failed", e);
            return null;
        }
    }

    public static boolean isSyncRunning() {
        return isRunning;
    }
}
