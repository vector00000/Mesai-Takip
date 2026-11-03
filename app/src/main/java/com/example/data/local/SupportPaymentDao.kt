package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SupportPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface SupportPaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(payment: SupportPayment)

    @Query("SELECT * FROM support_payments WHERE paymentId = :paymentId")
    suspend fun getPaymentById(paymentId: String): SupportPayment?

    @Query("SELECT * FROM support_payments WHERE iyzicoToken = :token")
    suspend fun getPaymentByIyzicoToken(token: String): SupportPayment?

    @Query("SELECT * FROM support_payments ORDER BY createdAtEpochMs DESC")
    fun getAllPayments(): Flow<List<SupportPayment>>

    @Query("SELECT * FROM support_payments ORDER BY createdAtEpochMs DESC")
    suspend fun getAllPaymentsList(): List<SupportPayment>

    @Query("UPDATE support_payments SET status = :status, providerTransactionId = :providerTxId, completedAtEpochMs = :completedAt WHERE paymentId = :paymentId")
    suspend fun updatePaymentStatus(
        paymentId: String,
        status: String,
        providerTxId: String,
        completedAt: Long
    )

    @Query("SELECT COUNT(*) FROM support_payments WHERE status = 'SUCCESS'")
    suspend fun getSuccessPaymentCount(): Int
}
