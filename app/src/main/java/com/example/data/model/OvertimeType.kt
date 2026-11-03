package com.example.data.model

enum class OvertimeType(val displayName: String) {
    NORMAL("Normal Mesai"),
    WEEKDAY("Hafta İçi"),
    WEEKEND("Hafta Sonu"),
    NIGHT_SHIFT("Gece Vardiyası"),
    OFFICIAL_HOLIDAY("Resmî Tatil");

    companion object {
        fun fromString(value: String): OvertimeType {
            val clean = value.trim()
            return when {
                clean.equals("NORMAL", ignoreCase = true) ||
                    clean.contains("Normal", ignoreCase = true) -> NORMAL
                clean.equals("NIGHT_SHIFT", ignoreCase = true) ||
                    clean.contains("Gece", ignoreCase = true) -> NIGHT_SHIFT
                clean.equals("OFFICIAL_HOLIDAY", ignoreCase = true) ||
                    clean.contains("Tatil", ignoreCase = true) -> OFFICIAL_HOLIDAY
                clean.equals("WEEKEND", ignoreCase = true) ||
                    clean.contains("Hafta Sonu", ignoreCase = true) -> WEEKEND
                else -> WEEKDAY
            }
        }
    }
}
