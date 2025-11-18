package com.ayb.busticketpos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TicketDao {

    @Insert
    void insertTrip(TripEntity trip);

    @Insert
    void insertTicket(TicketEntity ticket);

    @Query("SELECT * FROM tickets WHERE tripId = :tripId")
    List<TicketEntity> getTicketsForTrip(int tripId);

    @Query("SELECT SUM(amount) FROM tickets WHERE tripId = :tripId")
    int getTotalAmountForTrip(int tripId);

    @Query("SELECT COUNT(*) FROM tickets WHERE tripId = :tripId AND fareType = :type")
    int getTicketCountByType(int tripId, String type);

    @Insert
    long insertDay(TicketDayEntity day);

    @Query("UPDATE ticket_days SET end_time = :time WHERE id = :id")
    void updateEndTime(String time, int id);

    @Query("SELECT * FROM ticket_days WHERE date = :date AND busNo = :busNo LIMIT 1")
    TicketDayEntity getDay(String date, String busNo);

    @Query("SELECT * FROM trips ORDER BY tripId DESC LIMIT 1")
    TripEntity getLastTrip();

    @Query("UPDATE trips SET end_time = :date WHERE tripId = :id")
    void endTrip(String date, int id);

    @Query(" SELECT SUM(t.amount) FROM tickets t INNER JOIN trips tr ON tr.tripId = t.tripId INNER JOIN ticket_days d ON d.id = tr.dayId WHERE d.date = :date AND d.busNo = :busNo")
    Integer getTotalAmountForDate(String date, String busNo);

    @Query("SELECT IFNULL(SUM(t.amount), 0) FROM tickets t " +
            "INNER JOIN trips tr ON tr.tripId = t.tripId " +
            "INNER JOIN ticket_days d ON d.id = tr.dayId " +
            "WHERE d.id = :day_id")
    int getTotalAmountForDayId(int day_id);

    @Query("SELECT SUM(t.amount) FROM tickets t INNER JOIN trips tr ON tr.tripId = t.tripId WHERE tr.dayId = (SELECT MAX(id) FROM ticket_days WHERE busNo =:busNo)")
    Integer getTotalAmountForLastShift(String busNo);

    @Query("SELECT * FROM trips tr WHERE tr.dayId = :dayId")
    List<TripEntity> getTripsForDay(int dayId);

    @Query("SELECT * FROM ticket_days ORDER BY id DESC LIMIT 1")
    TicketDayEntity getLastDay();

    @Query("SELECT MAX(tripNo) FROM trips WHERE dayId = :dayId")
    Integer getLastTripNoForDay(int dayId);

    @Query("SELECT td.busNo from trips t INNER JOIN ticket_days td ON t.dayId = td.id where t.tripId = :tripId")
    String getBusNoFromTrip(int tripId);

    @Query("SELECT direction from trips where tripId = :tripId")
    String getDirectionFromTrip(int tripId);

    @Query("SELECT route from trips where tripId = :tripId")
    String getRouteFromTrip(int tripId);

    @Query("SELECT tripId from trips WHERE dayId = :dayId AND tripNo = :tripNo")
    int getTripId(int dayId, int tripNo);

    @Query("SELECT * FROM ticket_days WHERE date = :date ORDER BY id ASC")
    List<TicketDayEntity> getDaysForDate(String date);

}
