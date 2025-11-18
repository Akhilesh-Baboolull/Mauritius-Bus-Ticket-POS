package com.ayb.busticketpos;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TariffDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TariffRange> ranges);

    /** LiveData for the full list of tariff ranges */
    @Query("SELECT * FROM tariff_ranges ORDER BY minStages")
    LiveData<List<TariffRange>> getAllTariffsLive();

    // single‐line or concatenated string literal instead of """…"""
    @Query("SELECT * FROM tariff_ranges " +
            "WHERE :stages BETWEEN minStages AND maxStages " +
            "LIMIT 1")
    TariffRange findByStageCount(int stages);

    @Query("SELECT DISTINCT student FROM tariff_ranges ORDER BY student")
    List<Integer> getDistinctStudentTariffs();

    @Query("SELECT DISTINCT adult FROM tariff_ranges ORDER BY adult")
    List<Integer> getDistinctAdultTariffs();

    @Query("SELECT DISTINCT child FROM tariff_ranges ORDER BY child")
    List<Integer> getDistinctChildTariffs();

    @Query("SELECT * FROM tariff_ranges")
    List<TariffRange> getAllSync();

    @Query("SELECT * FROM tariff_ranges WHERE adult   = :fare LIMIT 1")
    TariffRange findByAdultFare(int fare);

    @Query("SELECT * FROM tariff_ranges WHERE student = :fare LIMIT 1")
    TariffRange findByStudentFare(int fare);

    @Query("SELECT * FROM tariff_ranges WHERE child   = :fare LIMIT 1")
    TariffRange findByChildFare(int fare);

    @Query("DELETE FROM tariff_ranges")
    void clearAll();
}
