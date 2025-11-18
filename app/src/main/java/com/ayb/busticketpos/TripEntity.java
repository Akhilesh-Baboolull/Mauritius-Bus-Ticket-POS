package com.ayb.busticketpos;

import static androidx.room.ForeignKey.CASCADE;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "trips",
        foreignKeys = @ForeignKey(
                entity = TicketDayEntity.class,
                parentColumns = "id",
                childColumns = "dayId",
                onDelete = CASCADE
        )
)
public class TripEntity {
    @PrimaryKey(autoGenerate = true)
    public int tripId;

    @NonNull
    public int dayId;

    public int tripNo;
    public String route;
    public String direction;
    public String start_time;
    public String end_time;
}
