package com.ayb.busticketpos;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_queue")
public class SyncJob {
    @PrimaryKey(autoGenerate = true) public long id;
    public String endpoint; // e.g. "/api/sync/insert_ticket_day.php"
    public String payloadJson;
    public int retryCount;           // optional, for backoff logic
    public long createdAt;           // System.currentTimeMillis()
    public String lastError;         // nullable
}