package com.ayb.busticketpos;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

/**
 * A stage belonging to a specific route.
 */
@Entity(
        tableName = "stages",
        foreignKeys = @ForeignKey(
                entity = RouteEntity.class,
                parentColumns = "routeId",
                childColumns = "routeId",
                onDelete    = ForeignKey.CASCADE
        )
)
public class StageEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;  // Auto-incremented primary key

    @NonNull public String routeId;
    public int stageOrder;
    public int loopIndex;
    public int    stageNo;
    public String stageName;
}
