package com.ayb.busticketpos;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.*;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.*;

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
        version = 23,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    /** 2.2.0: server ids / uploaded flags for offline-first ordered sync. Keeps all local data. */
    static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE ticket_days ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE trips ADD COLUMN serverId INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tickets ADD COLUMN uploaded INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE blank_tickets ADD COLUMN uploaded INTEGER NOT NULL DEFAULT 0");
        }
    };

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
                            .addMigrations(MIGRATION_22_23)
                            // Never wipe tickets on upgrade from 22+: every version bump needs a Migration.
                            // Only very old schemas (< 22, no migrations written) are still recreated.
                            .fallbackToDestructiveMigrationFrom(true,
                                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21)
                            .fallbackToDestructiveMigrationOnDowngrade(true)
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    // Only run sync when main process opens the DB; :bg opens it for BgStateRepo only.
                                    if (!App.isBgProcess(ctx)) {
                                        DatabaseSyncManager.startSync(ctx, INSTANCE);
                                    }
                                }
                            }).build();
                }
            }
        }
        return INSTANCE;
    }

    /** Force a master-data sync without wiping the DB instance (fetch-first, replace on success). */
    public static void forceRefresh(Context ctx) {
        DatabaseSyncManager.forceSync(ctx, null);
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
