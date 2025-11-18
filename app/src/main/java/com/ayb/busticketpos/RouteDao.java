package com.ayb.busticketpos;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RouteEntity> routes);

    @Query("DELETE FROM routes")
    void clearAll();

    @Query("SELECT * FROM routes ORDER BY routeName")
    LiveData<List<RouteEntity>> getAllRoutesLive();

    /** Synchronous query for Trip initialization */
    @Query("SELECT * FROM routes ORDER BY routeName")
    List<RouteEntity> getAllRoutesSync();

    @Query("SELECT COUNT(*) FROM routes")
    int countAll();
}
