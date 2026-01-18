package com.ayb.busticketpos;

public class BgState {

    public final boolean dayActive;
    public final boolean tripActive;

    public final Integer dayId;       // local DB day id (nullable)
    public final String busNo;         // nullable
    public final Integer tripNo;       // nullable
    public final String currentRoute;  // nullable

    public BgState(boolean dayActive,
                   boolean tripActive,
                   Integer dayId,
                   String busNo,
                   Integer tripNo,
                   String currentRoute) {

        this.dayActive = dayActive;
        this.tripActive = tripActive;
        this.dayId = dayId;
        this.busNo = busNo;
        this.tripNo = tripNo;
        this.currentRoute = currentRoute;
    }

    public static BgState inactive() {
        return new BgState(false, false, null, null, null, null);
    }

//    public BgState withTripInactive() {
//        return new BgState(this.dayActive, false, this.dayId, this.busNo, null, null);
//    }
}
