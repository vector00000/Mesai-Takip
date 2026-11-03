package com.example.util

import com.example.data.model.ActiveOvertime
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import java.time.LocalDate

enum class AuditIssueType(val labelTr: String, val isAdvisoryOnly: Boolean = false) {
    MISSING_END_TIME("Bitiş Saati Eksik / Açık Unutulan Mesai"),
    OVERLAPPING_RECORDS("Çakışan Çalışma Kayıtları"),
    TIME_INCONSISTENCY("Başlangıç ve Bitiş Saati Tutarsızlığı"),
    UNUSUALLY_LONG_DURATION("Olağan Dışı Uzun Çalışma Süresi"),
    UNUSUALLY_SHORT_DURATION("Olağan Dışı Kısa Çalışma Süresi"),
    SCHEDULE_COMPARISON_NOTE("Beklenen Vardiya / Süre Karşılaştırması", isAdvisoryOnly = true)
}

data class AuditFinding(
    val warningKey: String, // Kullanıcı uyarıyı kapattığında saklanan benzersiz anahtar
    val issueType: AuditIssueType,
    val recordId: Long?, // Düzenlenebilir kayıt kimliği (varsa)
    val dateStr: String,
    val title: String,
    val description: String,
    val suggestedActionText: String
)

object SmartRecordAuditor {

    const val MAX_REASONABLE_OVERTIME_MINUTES = 720 // 12 saat
    const val MIN_REASONABLE_OVERTIME_MINUTES = 15 // 15 dakika

    /**
     * Mevcut kayıtları hiçbir şekilde otomatik olarak değiştirmeden veya silmeden inceler.
     * Bitiş saati girilmemiş, çakışan, tutarsız, olağan dışı uzun/kısa kayıtları ve
     * beklenen çalışma süresi karşılaştırmalarını açıklayıcı uyarılar olarak döner.
     */
    fun auditRecords(
        records: List<OvertimeRecord>,
        activeOvertime: ActiveOvertime? = null,
        settings: AppSettings = AppSettings(),
        dismissedKeys: Set<String> = emptySet(),
        nowEpochMs: Long = TimeUtils.nowEpochMs()
    ): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()

        // 1. Aktif mesai çok uzun süredir (örn. >= 10 saat) açık unutulmuşsa
        if (activeOvertime != null && activeOvertime.isActive && activeOvertime.startTimeEpochMs > 0L) {
            val elapsedMinutes = ((nowEpochMs - activeOvertime.startTimeEpochMs) / 60000L).toInt()
            if (elapsedMinutes >= 600) {
                val key = "AUDIT_ACTIVE_LONG_${activeOvertime.startTimeEpochMs}"
                if (key !in dismissedKeys) {
                    findings.add(
                        AuditFinding(
                            warningKey = key,
                            issueType = AuditIssueType.MISSING_END_TIME,
                            recordId = null,
                            dateStr = activeOvertime.dateStr.ifBlank { TimeUtils.todayDateStr() },
                            title = "Açık Unutulmuş Aktif Mesai Olabilir",
                            description = "${activeOvertime.dateStr} tarihinde saat ${activeOvertime.startTimeStr}'de başlayan mesai ${TimeUtils.formatDurationShort(elapsedMinutes)} süredir devam ediyor. Bitiş saati henüz kaydedilmedi.",
                            suggestedActionText = "Ana ekrandan mesaiyi bitirebilir veya süreyi kontrol edebilirsiniz."
                        )
                    )
                }
            }
        }

        // 2. Kayıt bazlı kontroller
        for (rec in records) {
            // 2a. Bitiş saati boş veya eksik mi?
            if (rec.endTimeStr.isBlank() || rec.startTimeStr.isBlank()) {
                val key = "AUDIT_MISSING_TIME_${rec.id}"
                if (key !in dismissedKeys) {
                    findings.add(
                        AuditFinding(
                            warningKey = key,
                            issueType = AuditIssueType.MISSING_END_TIME,
                            recordId = rec.id,
                            dateStr = rec.date,
                            title = "Başlangıç veya Bitiş Saati Eksik",
                            description = "${rec.date} tarihli kayıtta giriş (${rec.startTimeStr.ifBlank { "Yok" }}) veya çıkış (${rec.endTimeStr.ifBlank { "Yok" }}) saati eksik görünüyor.",
                            suggestedActionText = "Kaydı düzenleyerek eksik saati tamamlayın."
                        )
                    )
                }
                continue
            }

            // 2b. Başlangıç ve bitiş zaman damgası / süre tutarsızlığı
            val expectedGrossMinutes = TimeUtils.calculateManualMinutes(rec.startTimeStr, rec.endTimeStr)
            val expectedNetMinutes = (expectedGrossMinutes - rec.breakMinutes.coerceAtLeast(0)).coerceAtLeast(0)
            val timeInconsistent = rec.endTimeEpochMs <= rec.startTimeEpochMs ||
                rec.durationMinutes <= 0 ||
                rec.breakMinutes >= expectedGrossMinutes ||
                kotlin.math.abs(rec.durationMinutes - expectedNetMinutes) > 5

            if (timeInconsistent) {
                val key = "AUDIT_INCONSISTENT_${rec.id}_${rec.durationMinutes}"
                if (key !in dismissedKeys) {
                    findings.add(
                        AuditFinding(
                            warningKey = key,
                            issueType = AuditIssueType.TIME_INCONSISTENCY,
                            recordId = rec.id,
                            dateStr = rec.date,
                            title = "Saat Aralığı ve Süre Tutarsızlığı",
                            description = "${rec.date} (${rec.startTimeStr} - ${rec.endTimeStr}, Mola: ${rec.breakMinutes} dk) kaydında kayıtlı net süre (${rec.durationMinutes} dk) ile hesaplanan süre ($expectedNetMinutes dk) uyuşmuyor.",
                            suggestedActionText = "Kaydı düzenleyerek saatleri ve mola süresini doğrulayın."
                        )
                    )
                }
            }

            // 2c. Olağan dışı uzun çalışma süresi (> 12 saat / 720 dk)
            if (rec.durationMinutes > MAX_REASONABLE_OVERTIME_MINUTES) {
                val key = "AUDIT_LONG_${rec.id}_${rec.durationMinutes}"
                if (key !in dismissedKeys) {
                    findings.add(
                        AuditFinding(
                            warningKey = key,
                            issueType = AuditIssueType.UNUSUALLY_LONG_DURATION,
                            recordId = rec.id,
                            dateStr = rec.date,
                            title = "Olağan Dışı Uzun Mesai Süresi (${TimeUtils.formatDurationShort(rec.durationMinutes)})",
                            description = "${rec.date} tarihli mesai kaydı 12 saatin üzerinde (${TimeUtils.formatDuration(rec.durationMinutes)}). Çıkış saati geç girilmiş veya vardiya birleşmiş olabilir.",
                            suggestedActionText = "Vardiyanız doğruysa uyarıyı kapatabilir veya saati düzenleyebilirsiniz."
                        )
                    )
                }
            }

            // 2d. Olağan dışı kısa çalışma süresi (1..14 dk)
            if (rec.durationMinutes in 1 until MIN_REASONABLE_OVERTIME_MINUTES) {
                val key = "AUDIT_SHORT_${rec.id}_${rec.durationMinutes}"
                if (key !in dismissedKeys) {
                    findings.add(
                        AuditFinding(
                            warningKey = key,
                            issueType = AuditIssueType.UNUSUALLY_SHORT_DURATION,
                            recordId = rec.id,
                            dateStr = rec.date,
                            title = "Olağan Dışı Kısa Mesai Kaydı (${rec.durationMinutes} dk)",
                            description = "${rec.date} tarihli kayıt yalnızca ${rec.durationMinutes} dakika sürüyor (${rec.startTimeStr} - ${rec.endTimeStr}). Yanlışlıkla açılıp kapatılmış olabilir.",
                            suggestedActionText = "Hatalıysa düzenleyebilir veya doğruysa uyarıyı kapatabilirsiniz."
                        )
                    )
                }
            }

            // 2e. Beklenen vardiya başlangıç saati ile karşılaştırma (kesin hata sayılmaz, bilgi amaçlı)
            val parsedDate = try { LocalDate.parse(rec.date) } catch (_: Exception) { null }
            if (parsedDate != null) {
                val expectedStart = AlarmScheduler.getScheduleForDate(parsedDate, settings)
                if (expectedStart != null) {
                    val actualStart = try { TimeUtils.parseTime(rec.startTimeStr) } catch (_: Exception) { null }
                    if (actualStart != null) {
                        val diffMins = kotlin.math.abs(
                            (actualStart.hour * 60 + actualStart.minute) -
                                (expectedStart.hour * 60 + expectedStart.minute)
                        )
                        if (diffMins in 90..360) {
                            val key = "AUDIT_SCHED_DIFF_${rec.id}_${rec.startTimeStr}"
                            if (key !in dismissedKeys) {
                                findings.add(
                                    AuditFinding(
                                        warningKey = key,
                                        issueType = AuditIssueType.SCHEDULE_COMPARISON_NOTE,
                                        recordId = rec.id,
                                        dateStr = rec.date,
                                        title = "Planlanan Vardiya Saatinden Farklı Başlangıç",
                                        description = "${rec.date} günü planlanan mesai başlangıcı ${TimeUtils.formatTime(expectedStart)} iken kayıt ${rec.startTimeStr} olarak girilmiş (Bu bir hata olmayabilir, bilgilendirme amaçlıdır).",
                                        suggestedActionText = "Farklı vardiyada çalıştıysanız uyarıyı kapatabilirsiniz."
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Çakışan (Overlapping) çalışma kayıtlarının tespiti
        val sortedByStart = records
            .filter { it.endTimeEpochMs > it.startTimeEpochMs }
            .sortedBy { it.startTimeEpochMs }

        for (i in 0 until sortedByStart.size) {
            val a = sortedByStart[i]
            for (j in i + 1 until sortedByStart.size) {
                val b = sortedByStart[j]
                if (b.startTimeEpochMs >= a.endTimeEpochMs) break

                // Zaman aralıkları kesişiyor: b.startTimeEpochMs < a.endTimeEpochMs
                val minId = minOf(a.id, b.id)
                val maxId = maxOf(a.id, b.id)
                val key = "AUDIT_OVERLAP_${minId}_${maxId}"
                if (key !in dismissedKeys) {
                    findings.add(
                        AuditFinding(
                            warningKey = key,
                            issueType = AuditIssueType.OVERLAPPING_RECORDS,
                            recordId = b.id,
                            dateStr = b.date,
                            title = "Çakışan Çalışma Kayıtları Tespit Edildi",
                            description = "${a.date} (${a.startTimeStr}-${a.endTimeStr}) ile ${b.date} (${b.startTimeStr}-${b.endTimeStr}) kayıtlarının saat aralıkları birbiriyle çakışıyor.",
                            suggestedActionText = "Çakışan kayıtlardan birini düzenleyin veya uyarıyı kapatın."
                        )
                    )
                }
            }
        }

        return findings
    }
}
