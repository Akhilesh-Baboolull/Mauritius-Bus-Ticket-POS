package com.ayb.busticketpos;
import static androidx.room.ForeignKey.CASCADE;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "reportsCount",
        foreignKeys = @ForeignKey(
                entity = TicketDayEntity.class,
                parentColumns = "id",
                childColumns = "dayId",
                onDelete = CASCADE
        )
)

public class ReportsEntity {

        @PrimaryKey(autoGenerate = true)
        public int id;

        @NonNull
        public int dayId;

        public String date;
        public int current_trip_report;
        public int all_trip_reports;
        public int custom_report;
        public int summary_report;

}

