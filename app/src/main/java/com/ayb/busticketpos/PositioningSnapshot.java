package com.ayb.busticketpos;

import android.content.Intent;

import androidx.annotation.Nullable;

/**
 * Operational fields for an immediate positioning ping.
 * Main process passes this in the Intent because SharedPreferences written on the UI
 * process are not visible immediately to the {@code :bg} location process.
 */
public final class PositioningSnapshot {

    public static final String EXTRA_HAS_SNAPSHOT = "pos_has_snapshot";
    public static final String EXTRA_DAY_ACTIVE = "pos_day_active";
    public static final String EXTRA_TRIP_ACTIVE = "pos_trip_active";
    public static final String EXTRA_BUS_NO = "pos_bus_no";
    public static final String EXTRA_ROUTE = "pos_route";
    public static final String EXTRA_TRIP_NO = "pos_trip_no";

    public final boolean dayActive;
    public final boolean tripActive;
    @Nullable public final String busNo;
    @Nullable public final String currentRoute;
    @Nullable public final Integer tripNo;

    public PositioningSnapshot(boolean dayActive,
                               boolean tripActive,
                               @Nullable String busNo,
                               @Nullable String currentRoute,
                               @Nullable Integer tripNo) {
        this.dayActive = dayActive;
        this.tripActive = tripActive;
        this.busNo = busNo;
        this.currentRoute = currentRoute;
        this.tripNo = tripNo;
    }

    public static PositioningSnapshot dayStart(String busNo) {
        return new PositioningSnapshot(true, false, busNo, null, null);
    }

    public static PositioningSnapshot tripStart(String busNo, String routeName, int tripNo) {
        return new PositioningSnapshot(true, true, busNo, routeName, tripNo);
    }

    /** After trip end: day still on, trip off, bus still relevant. */
    public static PositioningSnapshot tripEnded(String busNo) {
        return new PositioningSnapshot(true, false, busNo, null, null);
    }

    public void attachTo(Intent intent) {
        intent.putExtra(EXTRA_HAS_SNAPSHOT, true);
        intent.putExtra(EXTRA_DAY_ACTIVE, dayActive);
        intent.putExtra(EXTRA_TRIP_ACTIVE, tripActive);
        if (busNo != null) intent.putExtra(EXTRA_BUS_NO, busNo);
        if (currentRoute != null) intent.putExtra(EXTRA_ROUTE, currentRoute);
        if (tripNo != null) intent.putExtra(EXTRA_TRIP_NO, tripNo);
    }

    @Nullable
    public static PositioningSnapshot fromIntent(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_HAS_SNAPSHOT, false)) {
            return null;
        }
        boolean dayActive = intent.getBooleanExtra(EXTRA_DAY_ACTIVE, false);
        boolean tripActive = intent.getBooleanExtra(EXTRA_TRIP_ACTIVE, false);
        String bus = intent.getStringExtra(EXTRA_BUS_NO);
        String route = intent.getStringExtra(EXTRA_ROUTE);
        int tripNo = intent.getIntExtra(EXTRA_TRIP_NO, -1);
        return new PositioningSnapshot(
                dayActive,
                tripActive,
                bus,
                route,
                tripNo >= 0 ? tripNo : null
        );
    }
}
