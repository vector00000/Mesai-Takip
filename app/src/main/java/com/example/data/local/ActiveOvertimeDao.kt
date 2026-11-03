package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ActiveOvertime
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveOvertimeDao {
    @Query("SELECT * FROM active_overtime WHERE id = 1")
    fun getActiveOvertimeFlow(): Flow<ActiveOvertime?>

    @Query("SELECT * FROM active_overtime WHERE id = 1")
    suspend fun getActiveOvertime(): ActiveOvertime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(activeOvertime: ActiveOvertime)

    @Update
    suspend fun update(activeOvertime: ActiveOvertime)
}
