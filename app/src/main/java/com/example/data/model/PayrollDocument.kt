package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PayrollDocumentType(val labelTr: String, val badgeText: String) {
    PAYSLIP("Maaş Bordrosu", "Bordro"),
    OVERTIME_SHEET("Mesai Çizelgesi", "Mesai Belgesi"),
    BANK_RECEIPT("Banka Dekontu", "Dekont"),
    LEAVE_DOCUMENT("İzin / Görev Belgesi", "İzin Belgesi"),
    CONTRACT_OTHER("Sözleşme / Diğer", "Diğer");

    companion object {
        fun fromName(name: String?): PayrollDocumentType {
            return entries.find { it.name == name } ?: PAYSLIP
        }
    }
}

@Entity(
    tableName = "payroll_documents",
    indices = [
        Index(value = ["ownerAccountId", "yearMonth"]),
        Index(value = ["ownerAccountId", "documentType"])
    ]
)
data class PayrollDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerAccountId: String,
    val title: String,
    val documentType: String = PayrollDocumentType.PAYSLIP.name,
    val yearMonth: String, // "yyyy-MM" ör. "2026-10"
    val documentDate: String, // "yyyy-MM-dd" ör. "2026-10-15"
    val description: String = "",
    val fileName: String,
    val storedRelativePath: String,
    val mimeType: String, // "application/pdf", "image/jpeg", "image/png", "image/webp"
    val fileSizeBytes: Long,
    val sha256Checksum: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
