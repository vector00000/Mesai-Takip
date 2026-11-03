package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_overtime")
data class ActiveOvertime(
    @PrimaryKey
    val id: Int = 1,
    val isActive: Boolean = false,
    val startTimeEpochMs: Long = 0L,
    val startTimeStr: String = "", // "HH:mm"
    val hourlyRate: Double = 0.0,
    val overtimeType: String = "WEEKDAY",
    val isAutomatic: Boolean = false,
    val dateStr: String = "", // "yyyy-MM-dd"
    val breakMinutes: Int = 0 // Aktif mesaiye eklenen mola süresi (dakika)
)
