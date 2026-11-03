package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BudgetTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget_transactions ORDER BY dateStr DESC, createdAtEpochMs DESC")
    fun getAllTransactionsFlow(): Flow<List<BudgetTransaction>>

    @Query("SELECT * FROM budget_transactions ORDER BY dateStr DESC, createdAtEpochMs DESC")
    suspend fun getAllTransactionsList(): List<BudgetTransaction>

    @Query("SELECT * FROM budget_transactions WHERE yearMonth = :yearMonth ORDER BY dateStr DESC, createdAtEpochMs DESC")
    fun getTransactionsForMonthFlow(yearMonth: String): Flow<List<BudgetTransaction>>

    @Query("SELECT * FROM budget_transactions WHERE yearMonth = :yearMonth ORDER BY dateStr DESC, createdAtEpochMs DESC")
    suspend fun getTransactionsForMonthList(yearMonth: String): List<BudgetTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: BudgetTransaction): Long

    @Update
    suspend fun updateTransaction(transaction: BudgetTransaction)

    @Query("DELETE FROM budget_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)
}
