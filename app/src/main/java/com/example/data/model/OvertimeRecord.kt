package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "overtime_records")
data class OvertimeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // "yyyy-MM-dd" e.g. "2026-09-26"
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val startTimeStr: String, // "HH:mm" e.g. "18:00"
    val endTimeStr: String, // "HH:mm" e.g. "22:30"
    val durationMinutes: Int, // Net çalışma süresi (mola düşülmüş), örn. 270 (4s 30dk)
    val hourlyRate: Double, // Kayıt anındaki saatlik ücret (sonradan tarife değişse de bozulmaz)
    val totalAmount: Double, // Net süre üzerinden hesaplanan tutar
    val overtimeType: String, // "NORMAL", "WEEKDAY", "WEEKEND", "NIGHT_SHIFT", "OFFICIAL_HOLIDAY"
    val isAutomatic: Boolean = false,
    val note: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val breakMinutes: Int = 0 // Mola süresi (dakika)
) {
    val grossDurationMinutes: Int
        get() = durationMinutes + breakMinutes.coerceAtLeast(0)
}
