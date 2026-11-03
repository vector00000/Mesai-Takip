package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rate_tariff_rules",
    indices = [
        Index(value = ["startDateStr", "endDateStr"])
    ]
)
data class RateTariffRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String, // Örn: "2026 Yılı 2. Dönem Ücret Tarifesi"
    val startDateStr: String, // "yyyy-MM-dd"
    val endDateStr: String, // "yyyy-MM-dd"
    val normalHourlyRate: Double, // Normal mesai saatlik ücreti
    val weekdayOvertimeRate: Double, // Hafta içi fazla mesai saatlik ücreti
    val weekendRate: Double, // Hafta sonu saatlik ücreti
    val nightShiftRate: Double, // Gece vardiyası saatlik ücreti
    val officialHolidayRate: Double, // Resmî tatil saatlik ücreti
    val applyDaysCsv: String = "1,2,3,4,5,6,7", // Hangi günler geçerli (1=Pzt..7=Paz)
    val nightStartHour: Int = 20, // Gece vardiyası başlangıç saati (örn: 20:00)
    val nightEndHour: Int = 6, // Gece vardiyası bitiş saati (örn: 06:00)
    val isActive: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    fun getRateForType(type: OvertimeType): Double {
        return when (type) {
            OvertimeType.NORMAL -> normalHourlyRate
            OvertimeType.WEEKDAY -> weekdayOvertimeRate
            OvertimeType.WEEKEND -> weekendRate
            OvertimeType.NIGHT_SHIFT -> nightShiftRate
            OvertimeType.OFFICIAL_HOLIDAY -> officialHolidayRate
        }
    }

    fun getApplicableDaysSet(): Set<Int> {
        return applyDaysCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
            .ifEmpty { (1..7).toSet() }
    }
}
