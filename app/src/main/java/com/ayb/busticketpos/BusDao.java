// src/main/java/com/ayb/busticketpos/BusDao.java
package com.ayb.busticketpos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BusDao {
    /** Insert or replace the fleet of buses */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<BusEntity> buses);

    /** Fetch every bus number */
    @Query("SELECT * FROM bus_numbers")
    List<BusEntity> getAllBusesLive();

    /** Clear out the table */
    @Query("DELETE FROM bus_numbers")
    void clearAll();
}
