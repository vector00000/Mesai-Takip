package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED
}

/**
 * Destek işlemi modeli.
 * GÜVENLİK KURALI: Kesinlikle kart numarası, CVV, SKT, şifre gibi hassas ödeme bilgileri barındırmaz.
 * Sadece ödeme sağlayıcısından dönen güvenli işlem durumu ve referans kimlikleri saklanır.
 */
@Entity(tableName = "support_payments")
data class SupportPayment(
    @PrimaryKey
    val paymentId: String,
    val iyzicoToken: String = "",
    val amount: Double,
    val currency: String = "TRY",
    val status: String = PaymentStatus.PENDING.name,
    val provider: String = "iyzico",
    val providerTransactionId: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val completedAtEpochMs: Long = 0L
)
