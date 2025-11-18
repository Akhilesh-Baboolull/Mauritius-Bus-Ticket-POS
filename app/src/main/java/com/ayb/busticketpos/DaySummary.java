package com.ayb.busticketpos;

import java.util.List;

public class DaySummary {
    public int dayId;
    public String date;
    public String busNo;
    public int shiftNo; // Shift number for this date (1,2,...)
    public List<TripSummary> tripSummaries;
    public DayWaybillEntity waybill;
}
