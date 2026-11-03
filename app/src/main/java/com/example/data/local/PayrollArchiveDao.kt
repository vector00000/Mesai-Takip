package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PayrollArchiveReport
import kotlinx.coroutines.flow.Flow

@Dao
interface PayrollArchiveDao {
    @Query("SELECT * FROM payroll_archive_reports ORDER BY yearMonth DESC, createdAtEpochMs DESC")
    fun getAllArchivedReportsFlow(): Flow<List<PayrollArchiveReport>>

    @Query("SELECT * FROM payroll_archive_reports ORDER BY yearMonth DESC, createdAtEpochMs DESC")
    suspend fun getAllArchivedReportsList(): List<PayrollArchiveReport>

    @Query("SELECT * FROM payroll_archive_reports WHERE yearMonth = :yearMonth ORDER BY createdAtEpochMs DESC")
    suspend fun getArchivedReportsForMonth(yearMonth: String): List<PayrollArchiveReport>

    @Query("SELECT * FROM payroll_archive_reports WHERE id = :id LIMIT 1")
    suspend fun getArchivedReportById(id: Long): PayrollArchiveReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArchivedReport(report: PayrollArchiveReport): Long

    @Query("DELETE FROM payroll_archive_reports WHERE id = :id")
    suspend fun deleteArchivedReportById(id: Long)
}
