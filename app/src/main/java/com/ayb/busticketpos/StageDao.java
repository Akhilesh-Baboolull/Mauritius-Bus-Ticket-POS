package com.ayb.busticketpos;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface StageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<StageEntity> stages);

    @Query("DELETE FROM stages")
    void clearAll();

    /** Observe stages for a given route */
    @Query("SELECT * FROM stages WHERE routeId = :routeId ORDER BY stageOrder")
    LiveData<List<StageEntity>> getStagesForRouteLive(String routeId);

    /** Synchronous query for Trip direction choices */
    @Query("SELECT * FROM stages WHERE routeId = :routeId ORDER BY stageOrder")
    List<StageEntity> getStagesForRouteSync(String routeId);

    @Query("SELECT COUNT(*) FROM stages")
    int countAll();

}
