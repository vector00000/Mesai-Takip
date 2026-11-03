package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NotificationType(val labelTr: String, val badgeText: String) {
    OVERTIME_REMINDER("Mesai Başlangıç/Bitiş Hatırlatması", "Mesai"),
    SALARY_REMINDER("Maaş Günü Hatırlatması", "Maaş Günü"),
    UPCOMING_SHIFT("Yaklaşan Vardiya Bildirimi", "Vardiya"),
    MISSING_RECORD_ALERT("Eksik / Hatalı Kayıt Uyarısı", "Kayıt Denetimi"),
    MONTHLY_SUMMARY("Aylık Çalışma Özeti", "Aylık Özet"),
    ACHIEVEMENT_UNLOCKED("Başarı ve Kilometre Taşı", "Başarı"),
    SYSTEM_BACKUP("Sistem ve Yedekleme Durumu", "Sistem");

    companion object {
        fun fromName(name: String?): NotificationType {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: OVERTIME_REMINDER
        }
    }
}

enum class NotificationTargetRoute {
    HOME,
    HISTORY,
    REPORTS,
    ACHIEVEMENTS,
    PAYROLL_ARCHIVE,
    BUDGET,
    SMART_AUDIT,
    SETTINGS;

    companion object {
        fun fromName(name: String?): NotificationTargetRoute {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: HOME
        }
    }
}

@Entity(
    tableName = "app_notifications",
    indices = [
        Index(value = ["eventKey"], unique = true),
        Index(value = ["isRead", "createdAtEpochMs"])
    ]
)
data class AppNotification(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventKey: String, // Aynı olay için mükerrer bildirimi engelleyen benzersiz anahtar
    val type: String, // NotificationType.name
    val title: String,
    val message: String,
    val targetRoute: String = NotificationTargetRoute.HOME.name,
    val dateTimeStr: String, // "yyyy-MM-dd HH:mm"
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val wasPostedToSystem: Boolean = false // Gerçek cihaz bildirimi olarak yayınlandı mı?
)
