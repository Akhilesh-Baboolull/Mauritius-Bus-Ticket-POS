package com.ayb.busticketpos;

import android.content.Context;

public class BgStateRepo {

    private BgStateRepo() {}

    /**
     * True if there is an active day (end_time null/empty).
     * This replaces Prefs day_status in the :bg process.
     */
    public static boolean isDayActive(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx.getApplicationContext());
        TicketDao dao = db.ticketDao();
        return dao.hasActiveDay() == 1;
    }

    /**
     * Reads current operational state from Room.
     * Rules:
     * - If day inactive: dayActive=false, tripActive=false, busNo/tripNo/route null.
     * - If day active but trip inactive: dayActive=true, tripActive=false, busNo set, tripNo/route null.
     * - If trip active: dayActive=true, tripActive=true, busNo + tripNo + route set.
     */
    public static BgState read(Context ctx) {
        AppDatabase db = AppDatabase.getInstance(ctx.getApplicationContext());
        TicketDao dao = db.ticketDao();

        // 1) Find active day
        TicketDayEntity day = dao.getActiveDay();
        if (day == null) {
            return BgState.inactive();
        }

        Integer dayId = day.id;
        String busNo = trimOrNull(day.busNo);

        // 2) Find active trip for that day
        TripEntity trip = dao.getActiveTripForDay(dayId);

        if (trip == null) {
            // Day active, trip inactive
            return new BgState(true, false, dayId, busNo, null, null);
        }

        Integer tripNo = trip.tripNo; // allow null if DB has null
        String route = trimOrNull(trip.route);

        // Trip active implies day active
        return new BgState(true, true, dayId, busNo, tripNo, route);
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
