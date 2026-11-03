package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RateTariffRule
import kotlinx.coroutines.flow.Flow

@Dao
interface RateTariffDao {
    @Query("SELECT * FROM rate_tariff_rules ORDER BY startDateStr DESC, id DESC")
    fun getAllTariffRulesFlow(): Flow<List<RateTariffRule>>

    @Query("SELECT * FROM rate_tariff_rules ORDER BY startDateStr DESC, id DESC")
    suspend fun getAllTariffRulesList(): List<RateTariffRule>

    @Query("SELECT * FROM rate_tariff_rules WHERE isActive = 1 ORDER BY startDateStr DESC, id DESC")
    suspend fun getActiveTariffRulesList(): List<RateTariffRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTariffRule(rule: RateTariffRule): Long

    @Update
    suspend fun updateTariffRule(rule: RateTariffRule)

    @Query("DELETE FROM rate_tariff_rules WHERE id = :id")
    suspend fun deleteTariffRuleById(id: Long)
}
