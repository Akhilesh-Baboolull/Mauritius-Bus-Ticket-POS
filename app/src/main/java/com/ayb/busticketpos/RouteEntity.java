// src/main/java/com/ayb/busticketpos/RouteEntity.java
package com.ayb.busticketpos;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "routes")
public class RouteEntity {
    /** e.g. "29_240" */
    @PrimaryKey
    @NonNull
    public String routeId;

    /** e.g. "29/240" */
    public String routeName;
}
