package com.ayb.busticketpos;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface ReportCountDao {

    @Insert
    void insertCount(ReportsEntity reportsCount);

    @Query("SELECT * FROM reportsCount WHERE dayId = :day_id")
    ReportsEntity getReportsCountForDay(int day_id);

    @Query("SELECT " +
            "IFNULL(SUM(current_trip_report), 0) AS current_trip_report, " +
            "IFNULL(SUM(all_trip_reports), 0) AS all_trip_reports, " +
            "IFNULL(SUM(custom_report), 0) AS custom_report, " +
            "IFNULL(SUM(summary_report), 0) AS summary_report " +
            "FROM reportsCount WHERE date = :date")
    ReportsCountSummary getReportsCountForDate(String date);

    @Query("UPDATE reportsCount SET current_trip_report = :count WHERE dayId = :day_id")
    void recordCurrentTripCount(int count, int day_id);

    @Query("UPDATE reportsCount SET all_trip_reports = :count WHERE dayId = :day_id")
    void recordAllTripsCount(int count, int day_id);

    @Query("UPDATE reportsCount SET custom_report = :count WHERE dayId = :day_id")
    void recordCustomTripCount(int count, int day_id);

    @Query("UPDATE reportsCount SET summary_report = :count WHERE dayId = :day_id")
    void recordSummaryReportCount(int count, int day_id);

}
