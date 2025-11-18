package com.ayb.busticketpos;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import com.ayb.busticketpos.TripEntity;

@Entity(
        tableName = "tickets",
        foreignKeys = @ForeignKey(
                entity = TripEntity.class,
                parentColumns = "tripId",
                childColumns = "tripId",
                onDelete = ForeignKey.CASCADE
        )
)
public class TicketEntity {
    @PrimaryKey(autoGenerate = true)
    public int ticketId;

    @NonNull
    public String ticketNo;

    public int tripId;
    public String time;
    public String startStage;

    public String ticketType;

    public String endStage;
    public String fareType;   // "Adult", "Student", "Child"
    public int amount;
}
