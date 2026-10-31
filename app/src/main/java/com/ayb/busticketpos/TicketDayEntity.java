package com.ayb.busticketpos;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "ticket_days"
//        indices = {@Index(value = {"date", "busNo"}, unique = true)}
)
public class TicketDayEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String date;

    @NonNull
    public String busNo;

    public String start_time;

    public String end_time;

    /** tbl_ticket_days.id once create_day succeeded; 0 = not on the server yet. */
    @ColumnInfo(defaultValue = "0")
    public int serverId;
}

