
package com.ayb.busticketpos;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tariff_ranges")
public class TariffRange {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int minStages;
    public int maxStages;

    public int adult;
    public int child;
    public int student;
}
