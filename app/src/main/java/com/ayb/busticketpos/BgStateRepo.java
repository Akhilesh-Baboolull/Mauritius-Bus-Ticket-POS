package com.ayb.busticketpos;

import android.content.Context;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Operational state for interval positioning in the {@code :bg} process.
 * Uses a file flag from the main process (day started / not) plus Room — stale open
 * {@code ticket_days} rows must not send {@code busNo} before the operator starts the day.
 */
public class BgStateRepo {

    private static final String TAG = "BgStateRepo";
    private BgStateRepo() {}

    public static boolean isDayActive(Context ctx) {
        if (!Prefs.isPositioningDayStarted(ctx)) {
            return false;
        }
        try {
            AppDatabase db = AppDatabase.getInstance(ctx.getApplicationContext());
            TicketDayEntity day = db.ticketDao().getActiveDay();
            return isCurrentOperationalDay(ctx, day);
        } catch (Exception e) {
            Log.w(TAG, "isDayActive failed (use inactive)", e);
            return false;
        }
    }

    public static BgState read(Context ctx) {
        if (!Prefs.isPositioningDayStarted(ctx)) {
            return BgState.inactive();
        }
        try {
            AppDatabase db = AppDatabase.getInstance(ctx.getApplicationContext());
            TicketDao dao = db.ticketDao();

            TicketDayEntity day = dao.getActiveDay();
            if (!isCurrentOperationalDay(ctx, day)) {
                return BgState.inactive();
            }

            String busNo = trimOrNull(day.busNo);
            TripEntity trip = dao.getActiveTripForDay(day.id);
            if (trip == null) {
                return new BgState(true, false, day.id, busNo, null, null);
            }

            return new BgState(
                    true,
                    true,
                    day.id,
                    busNo,
                    trip.tripNo,
                    trimOrNull(trip.route)
            );
        } catch (Exception e) {
            Log.w(TAG, "read failed (use inactive)", e);
            return BgState.inactive();
        }
    }

  /** Operator started day (file) and local open day matches today's operational date. */
    private static boolean isCurrentOperationalDay(Context ctx, TicketDayEntity day) {
        if (day == null) {
            return false;
        }
        String today = todayDdMmYy();
        if (!today.equals(day.date)) {
            return false;
        }
        String published = Prefs.getPositioningDayDate(ctx);
        return published == null || published.equals(day.date);
    }

    private static String todayDdMmYy() {
        return new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date());
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
