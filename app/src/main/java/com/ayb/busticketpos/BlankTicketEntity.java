package com.ayb.busticketpos;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Entity representing a blank ticket record.
 * Each entry tracks when the ticket was created (datetime)
 * and optionally which trip it was assigned to.
 */
@Entity(tableName = "blank_tickets")
public class BlankTicketEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Date/time the blank ticket was created or logged */
    @NonNull
    public String datetime;

    /** Optional trip ID reference (null = not assigned yet) */
    public int tripId;

    // --- Convenience constructor ---
    public BlankTicketEntity(@NonNull String datetime, int tripId) {
        this.datetime = datetime;
        this.tripId = tripId;
    }

    // --- Getters and setters (optional) ---
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(@NonNull String datetime) {
        this.datetime = datetime;
    }

    public int getTripId() {
        return tripId;
    }

    public void setTripId(int tripId) {
        this.tripId = tripId;
    }
}
