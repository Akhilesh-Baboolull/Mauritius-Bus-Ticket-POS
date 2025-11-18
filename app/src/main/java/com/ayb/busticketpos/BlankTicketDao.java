package com.ayb.busticketpos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import java.util.List;

/**
 * DAO for managing blank ticket records.
 */
@Dao
public interface BlankTicketDao {

    /** Insert a new blank ticket and return its ID */
    @Insert
    long insert(BlankTicketEntity blankTicket);

    /** Get all blank tickets (latest first) */
    @Query("SELECT * FROM blank_tickets ORDER BY id DESC")
    List<BlankTicketEntity> getAll();

    /** Delete a specific blank ticket */
    @Delete
    void delete(BlankTicketEntity blankTicket);

    /** Clear all blank tickets */
    @Query("DELETE FROM blank_tickets")
    void clearAll();

    @Query("SELECT COUNT(*) FROM blank_tickets WHERE tripId = :tripId")
    int getCountForTrip(int tripId);
}
