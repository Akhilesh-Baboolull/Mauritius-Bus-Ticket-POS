package com.ayb.busticketpos;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;
@Dao
public interface SyncJobDao {
    @Insert
    long insert(SyncJob job);

    @Query("SELECT * FROM sync_queue ORDER BY id ASC LIMIT :limit")
    List<SyncJob> fetchBatch(int limit);

    @Query("DELETE FROM sync_queue WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :err WHERE id = :id")
    void markFailed(long id, String err);

    @Query("SELECT COUNT(*) FROM sync_queue")
    int count();
}