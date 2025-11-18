package com.ayb.busticketpos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface DayWaybillDao {
    @Insert
    void insert(DayWaybillEntity entity);

    @Update
    void update(DayWaybillEntity entity);

    @Query("SELECT * FROM day_waybill WHERE dayId = :dayId LIMIT 1")
    DayWaybillEntity getForDay(int dayId);

}
