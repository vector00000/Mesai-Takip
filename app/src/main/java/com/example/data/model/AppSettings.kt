package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val isSetupCompleted: Boolean = false,
    val weekdayRate: Double = 200.0,
    val weekendRate: Double = 266.0,
    val weekdayStartTime: String = "18:00",
    val saturdayStartTime: String = "13:00",
    val sundayStartTime: String = "09:00",
    val autoGlobalEnabled: Boolean = true,
    val autoMonday: Boolean = true,
    val autoTuesday: Boolean = true,
    val autoWednesday: Boolean = true,
    val autoThursday: Boolean = true,
    val autoFriday: Boolean = true,
    val autoSaturday: Boolean = false,
    val autoSunday: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val skipDate: String = "", // "yyyy-MM-dd" for "Bugün Mesai Yok" feature
    val forgottenAlertEnabled: Boolean = true,
    val forgottenAlertHours: Int = 5,
    val employeeName: String = "",
    val workplaceName: String = "",
    val baseSalary: Double = 0.0,
    val salaryDayOfMonth: Int = 1,
    val monthlyWorkHours: Double = 225.0,
    val monthlyTargetDays: Int = 10,
    val monthlyTargetHours: Double = 20.0,
    // Çoklu ücret tarifesi varsayılan oranları
    val normalHourlyRate: Double = 150.0,
    val nightShiftRate: Double = 250.0,
    val officialHolidayRate: Double = 300.0,
    // Otomatik hatırlatma ve Bildirim Merkezi tercihleri
    val reminderOvertimeEntryEnabled: Boolean = true,
    val reminderSalaryDayEnabled: Boolean = true,
    val reminderShiftStartEnabled: Boolean = true,
    val reminderMissingRecordEnabled: Boolean = true,
    val reminderMonthlySummaryEnabled: Boolean = true,
    val reminderAchievementEnabled: Boolean = true,
    val reminderTimeStr: String = "20:30",
    val shiftReminderLeadMinutes: Int = 30
) {
    fun getEffectiveNormalRate(): Double {
        if (normalHourlyRate > 0.0) return normalHourlyRate
        val hours = if (monthlyWorkHours > 0.0) monthlyWorkHours else 225.0
        if (baseSalary > 0.0) {
            val calc = (baseSalary / hours).let { kotlin.math.round(it * 100.0) / 100.0 }
            if (calc > 0.0) return calc
        }
        return 150.0
    }

    fun getEffectiveWeekdayRate(): Double {
        if (weekdayRate > 0.0) return weekdayRate
        val hours = if (monthlyWorkHours > 0.0) monthlyWorkHours else 225.0
        if (baseSalary > 0.0) {
            val calc = (baseSalary / hours * 1.5).let { kotlin.math.round(it * 100.0) / 100.0 }
            if (calc > 0.0) return calc
        }
        return 200.0
    }

    fun getEffectiveWeekendRate(): Double {
        if (weekendRate > 0.0) return weekendRate
        val hours = if (monthlyWorkHours > 0.0) monthlyWorkHours else 225.0
        if (baseSalary > 0.0) {
            val calc = (baseSalary / hours * 2.0).let { kotlin.math.round(it * 100.0) / 100.0 }
            if (calc > 0.0) return calc
        }
        return 266.67
    }

    fun getEffectiveNightShiftRate(): Double {
        if (nightShiftRate > 0.0) return nightShiftRate
        val hours = if (monthlyWorkHours > 0.0) monthlyWorkHours else 225.0
        if (baseSalary > 0.0) {
            val calc = (baseSalary / hours * 1.75).let { kotlin.math.round(it * 100.0) / 100.0 }
            if (calc > 0.0) return calc
        }
        return 250.0
    }

    fun getEffectiveOfficialHolidayRate(): Double {
        if (officialHolidayRate > 0.0) return officialHolidayRate
        val hours = if (monthlyWorkHours > 0.0) monthlyWorkHours else 225.0
        if (baseSalary > 0.0) {
            val calc = (baseSalary / hours * 2.5).let { kotlin.math.round(it * 100.0) / 100.0 }
            if (calc > 0.0) return calc
        }
        return 300.0
    }

    fun getEffectiveRate(isWeekend: Boolean): Double {
        return if (isWeekend) getEffectiveWeekendRate() else getEffectiveWeekdayRate()
    }

    fun getEffectiveRateForType(type: OvertimeType): Double {
        return when (type) {
            OvertimeType.NORMAL -> getEffectiveNormalRate()
            OvertimeType.WEEKDAY -> getEffectiveWeekdayRate()
            OvertimeType.WEEKEND -> getEffectiveWeekendRate()
            OvertimeType.NIGHT_SHIFT -> getEffectiveNightShiftRate()
            OvertimeType.OFFICIAL_HOLIDAY -> getEffectiveOfficialHolidayRate()
        }
    }
}
