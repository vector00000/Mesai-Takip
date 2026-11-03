package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Her ay için mevcut uygulama kayıtlarından üretilen ve kullanılan ücret tarifeleriyle
 * birlikte dondurularak (snapshot) saklanan "Kişisel Maaş Programlama Raporu" arşivi.
 * Sonradan ücret ayarları değişse bile arşivlenmiş raporun tutarları ve tarifeleri korunur.
 */
@Entity(
    tableName = "payroll_archive_reports",
    indices = [
        Index(value = ["yearMonth"])
    ]
)
data class PayrollArchiveReport(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val yearMonth: String, // "yyyy-MM" örn. "2026-10"
    val reportTitle: String, // Örn: "Ekim 2026 Kişisel Maaş Programlama Raporu"
    val createdDateStr: String, // "yyyy-MM-dd"
    val employeeName: String = "",
    val workplaceName: String = "",
    // Arşiv anındaki dondurulmuş (snapshot) maaş ve ücret tarifeleri
    val snapshotBaseSalary: Double = 0.0,
    val snapshotNormalRate: Double = 0.0,
    val snapshotWeekdayRate: Double = 0.0,
    val snapshotWeekendRate: Double = 0.0,
    val snapshotNightRate: Double = 0.0,
    val snapshotHolidayRate: Double = 0.0,
    val appliedTariffSummary: String = "",
    // Dönem çalışma ve hakediş toplamları
    val totalRecordsCount: Int = 0,
    val totalWorkingDays: Int = 0,
    val totalNetWorkMinutes: Int = 0,
    val totalBreakMinutes: Int = 0,
    val totalOvertimeEarnings: Double = 0.0,
    val totalAdvanceDeducted: Double = 0.0,
    val netTotalPayable: Double = 0.0,
    // Kayıtların dondurulmuş JSON kopyası (ücretler sonradan değişse bile bu rapor bozulmaz)
    val recordsSnapshotJson: String = "[]",
    val pdfFilePath: String = "",
    val csvFilePath: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
