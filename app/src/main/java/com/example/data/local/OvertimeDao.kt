package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.OvertimeRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface OvertimeDao {
    @Query("SELECT * FROM overtime_records ORDER BY startTimeEpochMs DESC")
    fun getAllRecords(): Flow<List<OvertimeRecord>>

    @Query("SELECT * FROM overtime_records ORDER BY startTimeEpochMs DESC")
    suspend fun getAllRecordsList(): List<OvertimeRecord>

    @Query("SELECT * FROM overtime_records WHERE date = :date ORDER BY startTimeEpochMs DESC")
    suspend fun getRecordsForDate(date: String): List<OvertimeRecord>

    @Query("SELECT * FROM overtime_records WHERE date LIKE :yearMonthPrefix || '%' ORDER BY startTimeEpochMs ASC")
    fun getRecordsForMonth(yearMonthPrefix: String): Flow<List<OvertimeRecord>>

    @Query("SELECT * FROM overtime_records WHERE id = :id")
    suspend fun getRecordById(id: Long): OvertimeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: OvertimeRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<OvertimeRecord>)

    @Update
    suspend fun updateRecord(record: OvertimeRecord)

    @Delete
    suspend fun deleteRecord(record: OvertimeRecord)

    @Query("DELETE FROM overtime_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM overtime_records")
    suspend fun deleteAllRecords()
}
