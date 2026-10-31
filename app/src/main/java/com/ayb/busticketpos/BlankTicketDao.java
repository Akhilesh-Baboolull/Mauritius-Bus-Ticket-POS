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

    @Query("SELECT * FROM blank_tickets WHERE id = :id LIMIT 1")
    BlankTicketEntity getById(long id);

    @Query("UPDATE blank_tickets SET uploaded = 1 WHERE id = :id")
    void markUploaded(long id);

    /** Legacy queue mapping: latest local blank ticket logged at this time. */
    @Query("SELECT * FROM blank_tickets WHERE datetime = :datetime ORDER BY id DESC LIMIT 1")
    BlankTicketEntity findLatestByDatetime(String datetime);

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
