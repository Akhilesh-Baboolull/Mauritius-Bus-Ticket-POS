package com.ayb.busticketpos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.room.*;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.Executors;

@Database(
        entities = {
                RouteEntity.class,
                StageEntity.class,
                TariffRange.class,
                BusEntity.class,
                TicketDayEntity.class,
                TripEntity.class,
                TicketEntity.class,
                DayWaybillEntity.class,
                SyncJob.class,
                BlankTicketEntity.class,
                ReportsEntity.class
        },
        version = 22,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract RouteDao routeDao();
    public abstract StageDao stageDao();
    public abstract TariffDao tariffDao();
    public abstract BusDao busDao();
    public abstract TicketDao ticketDao();
    public abstract DayWaybillDao dayWaybillDao();
    public abstract SyncJobDao syncJobDao();
    public abstract BlankTicketDao blankTicketDao();
    public abstract ReportCountDao reportCountDao();

    private static volatile AppDatabase INSTANCE;

    private static final Object FETCH_LOCK = new Object();
    protected static volatile boolean isFetching = false;


    public static AppDatabase getInstance(final Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "busticketpos.db"
                            )
                            .fallbackToDestructiveMigration()
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);

                                    DatabaseSyncManager.startSync(ctx, INSTANCE);
                                }
                            }).build();
                }
            }
        }
        return INSTANCE;
    }

    public static void forceRefresh(Context ctx) {
        // Reset last fetch time
        PrefsCache.saveLastFetchTime(ctx, 0L);

        // Set INSTANCE to null so onOpen() callback gets triggered again
        INSTANCE = null;

        // Now re-initialize DB
        getInstance(ctx);
    }

    // Used to match JSON for route/stage
    static class RouteWithStages {
        String routeId;
        String routeName;
        List<StagePojo> stages;
    }

    static class StagePojo {
        public int stageOrder;
        public int loopIndex;
        int stageNo;
        String stageName;
    }
}
