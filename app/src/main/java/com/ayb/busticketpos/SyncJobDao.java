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

  /** Update reports first, then tickets/day/trip; positioning last. */
    @Query("SELECT * FROM sync_queue ORDER BY " +
            "CASE endpoint " +
            "WHEN :reportUrl THEN 0 " +
            "WHEN :createDay THEN 1 " +
            "WHEN :startTrip THEN 2 " +
            "WHEN :recordTicket THEN 3 " +
            "WHEN :endTrip THEN 4 " +
            "WHEN :endDay THEN 5 " +
            "WHEN :blankTicket THEN 6 " +
            "ELSE 10 END, id ASC LIMIT :limit")
    List<SyncJob> fetchBatchPrioritized(
            String reportUrl,
            String createDay,
            String startTrip,
            String recordTicket,
            String endTrip,
            String endDay,
            String blankTicket,
            int limit);

    @Query("DELETE FROM sync_queue WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :err WHERE id = :id")
    void markFailed(long id, String err);

    @Query("SELECT COUNT(*) FROM sync_queue")
    int count();

    /** Newest first, for the Settings "pending items" viewer. */
    @Query("SELECT * FROM sync_queue ORDER BY id DESC LIMIT :limit")
    List<SyncJob> fetchNewest(int limit);

    @Query("DELETE FROM sync_queue WHERE endpoint = :endpoint AND retryCount >= :maxRetries")
    int deleteOverRetries(String endpoint, int maxRetries);

    @Query("DELETE FROM sync_queue WHERE endpoint = :endpoint AND id NOT IN " +
            "(SELECT id FROM sync_queue WHERE endpoint = :endpoint ORDER BY id DESC LIMIT :keep)")
    int prunePositioningKeepLatest(String endpoint, int keep);

    @Query("DELETE FROM sync_queue WHERE endpoint = :endpoint AND id IN " +
            "(SELECT id FROM sync_queue WHERE endpoint = :endpoint ORDER BY id ASC LIMIT :limit)")
    int deleteOldestByEndpoint(String endpoint, int limit);

    @Query("DELETE FROM sync_queue WHERE id IN " +
            "(SELECT id FROM sync_queue ORDER BY id ASC LIMIT :limit)")
    int deleteOldestAny(int limit);

    @Query("DELETE FROM sync_queue WHERE endpoint = :endpoint")
    int deleteAllByEndpoint(String endpoint);

    /** Remove jobs older than cutoff (exclusive): createdAt &lt; cutoffMs */
    @Query("DELETE FROM sync_queue WHERE createdAt < :cutoffMs")
    int deleteOlderThan(long cutoffMs);

    @Query("DELETE FROM sync_queue")
    int deleteAll();
}
