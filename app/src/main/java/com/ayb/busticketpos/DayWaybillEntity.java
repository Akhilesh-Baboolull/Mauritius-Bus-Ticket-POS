package com.ayb.busticketpos;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static androidx.room.ForeignKey.CASCADE;

@Entity(
        tableName = "day_waybill",
        foreignKeys = @ForeignKey(
                entity = TicketDayEntity.class,
                parentColumns = "id",
                childColumns = "dayId",
                onDelete = CASCADE
        ),
    indices = @Index(value = "dayId", unique = true)
)
public class DayWaybillEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public int dayId;

    @Nullable
    public Integer totalReceipt = 0;

    @Nullable
    public Integer otherReceipt = 0;

    @Nullable
    public Float diesel = 0f;

    @Nullable
    public Float wages = 0f;

    @Nullable
    public Float wellMeal = 0f;

    @Nullable
    public Float maintenance = 0f;

    @Nullable
    public Float otherExpenses = 0f;
}
