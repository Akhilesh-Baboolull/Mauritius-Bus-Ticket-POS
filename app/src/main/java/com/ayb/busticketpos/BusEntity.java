// src/main/java/com/ayb/busticketpos/BusEntity.java
package com.ayb.busticketpos;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Represents a bus identified by its plate/number.
 */
@Entity(tableName = "bus_numbers")
public class BusEntity {
    /** e.g. "BUS-001" */
    @PrimaryKey
    @NonNull
    public String busNo;
}
