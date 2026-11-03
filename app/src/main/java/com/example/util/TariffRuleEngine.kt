package com.example.util

import com.example.data.model.AppSettings
import com.example.data.model.OvertimeType
import com.example.data.model.RateTariffRule
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.round

data class TariffConflictWarning(
    val existingRuleId: Long,
    val existingRuleName: String,
    val overlappingStartDate: String,
    val overlappingEndDate: String,
    val overlappingDays: Set<Int>,
    val message: String
)

data class TariffDifferenceItem(
    val overtimeType: OvertimeType,
    val defaultRate: Double,
    val tariffRate: Double,
    val diffAmount: Double,
    val diffPercent: Double
)

data class ResolvedTariffRate(
    val hourlyRate: Double,
    val appliedType: OvertimeType,
    val matchedRuleId: Long?,
    val matchedRuleName: String
)

object TariffRuleEngine {

    /**
     * İki tarife kuralının tarih aralığı ve uygulanan günler bakımından çakışıp çakışmadığını denetler.
     * Aynı çalışma saatine birden fazla tarifenin yanlışlıkla uygulanmasını engeller.
     */
    fun findConflicts(
        candidate: RateTariffRule,
        existingRules: List<RateTariffRule>
    ): List<TariffConflictWarning> {
        if (!candidate.isActive) return emptyList()
        val candStart = try { LocalDate.parse(candidate.startDateStr) } catch (_: Exception) { return emptyList() }
        val candEnd = try { LocalDate.parse(candidate.endDateStr) } catch (_: Exception) { return emptyList() }
        if (candEnd.isBefore(candStart)) return emptyList()

        val candDays = candidate.getApplicableDaysSet()
        val warnings = mutableListOf<TariffConflictWarning>()

        for (existing in existingRules) {
            if (!existing.isActive) continue
            if (candidate.id != 0L && candidate.id == existing.id) continue

            val exStart = try { LocalDate.parse(existing.startDateStr) } catch (_: Exception) { continue }
            val exEnd = try { LocalDate.parse(existing.endDateStr) } catch (_: Exception) { continue }

            val dateOverlaps = !candEnd.isBefore(exStart) && !candStart.isAfter(exEnd)
            if (!dateOverlaps) continue

            val exDays = existing.getApplicableDaysSet()
            val commonDays = candDays.intersect(exDays)
            if (commonDays.isNotEmpty()) {
                val overlapStart = if (candStart.isAfter(exStart)) candStart else exStart
                val overlapEnd = if (candEnd.isBefore(exEnd)) candEnd else exEnd
                val dayNames = commonDays.sorted().joinToString(", ") { dayNumberToShortName(it) }
                warnings.add(
                    TariffConflictWarning(
                        existingRuleId = existing.id,
                        existingRuleName = existing.name,
                        overlappingStartDate = overlapStart.toString(),
                        overlappingEndDate = overlapEnd.toString(),
                        overlappingDays = commonDays,
                        message = "'${existing.name}' tarifesi ile $overlapStart – $overlapEnd tarihleri arasında ($dayNames) çakışma tespit edildi."
                    )
                )
            }
        }
        return warnings
    }

    /**
     * Verilen tarih, başlangıç saati ve mesai türü için geçerli olan saatlik ücreti çözümler.
     * Çakışma durumunda yalnızca en güncel aktif kuralı seçer ve aynı saate iki tarifeyi asla çarpmaz.
     */
    fun resolveRate(
        dateStr: String,
        startTimeStr: String?,
        requestedType: OvertimeType?,
        settings: AppSettings,
        activeRules: List<RateTariffRule>,
        isOfficialHoliday: Boolean = false
    ): ResolvedTariffRate {
        val date = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            LocalDate.now(TimeUtils.ISTANBUL_ZONE)
        }
        val dayOfWeekNumber = date.dayOfWeek.value // 1 (Pzt) .. 7 (Paz)

        val matchedRule = activeRules
            .filter { it.isActive }
            .firstOrNull { rule ->
                val start = try { LocalDate.parse(rule.startDateStr) } catch (_: Exception) { null }
                val end = try { LocalDate.parse(rule.endDateStr) } catch (_: Exception) { null }
                if (start == null || end == null) {
                    false
                } else {
                    !date.isBefore(start) && !date.isAfter(end) &&
                        rule.getApplicableDaysSet().contains(dayOfWeekNumber)
                }
            }

        val effectiveType = when {
            isOfficialHoliday -> OvertimeType.OFFICIAL_HOLIDAY
            requestedType != null -> requestedType
            else -> determineAutoOvertimeType(date, startTimeStr, matchedRule)
        }

        return if (matchedRule != null) {
            val rate = matchedRule.getRateForType(effectiveType).let {
                if (it > 0.0) it else settings.getEffectiveRateForType(effectiveType)
            }
            ResolvedTariffRate(
                hourlyRate = rate,
                appliedType = effectiveType,
                matchedRuleId = matchedRule.id,
                matchedRuleName = matchedRule.name
            )
        } else {
            ResolvedTariffRate(
                hourlyRate = settings.getEffectiveRateForType(effectiveType),
                appliedType = effectiveType,
                matchedRuleId = null,
                matchedRuleName = "Varsayılan Kurumsal Tarife"
            )
        }
    }

    fun determineAutoOvertimeType(
        date: LocalDate,
        startTimeStr: String?,
        rule: RateTariffRule? = null
    ): OvertimeType {
        if (!startTimeStr.isNullOrBlank()) {
            val time = try { TimeUtils.parseTime(startTimeStr) } catch (_: Exception) { LocalTime.of(18, 0) }
            val nightStart = rule?.nightStartHour ?: 20
            val nightEnd = rule?.nightEndHour ?: 6
            val isNight = if (nightStart > nightEnd) {
                time.hour >= nightStart || time.hour < nightEnd
            } else {
                time.hour in nightStart until nightEnd
            }
            if (isNight && rule != null && rule.nightShiftRate > 0.0) {
                return OvertimeType.NIGHT_SHIFT
            }
        }
        return if (TimeUtils.isWeekend(date)) OvertimeType.WEEKEND else OvertimeType.WEEKDAY
    }

    /**
     * Yeni bir tarifenin mevcut varsayılan ücret ayarlarından farklarını raporlar.
     */
    fun compareWithSettings(
        rule: RateTariffRule,
        settings: AppSettings
    ): List<TariffDifferenceItem> {
        return OvertimeType.entries.map { type ->
            val defRate = settings.getEffectiveRateForType(type)
            val newRate = rule.getRateForType(type)
            val diff = round((newRate - defRate) * 100.0) / 100.0
            val pct = if (defRate > 0.0) {
                round(((newRate - defRate) / defRate) * 1000.0) / 10.0
            } else 0.0
            TariffDifferenceItem(
                overtimeType = type,
                defaultRate = defRate,
                tariffRate = newRate,
                diffAmount = diff,
                diffPercent = pct
            )
        }
    }

    fun dayNumberToShortName(day: Int): String {
        return when (day) {
            1 -> "Pzt"
            2 -> "Sal"
            3 -> "Çar"
            4 -> "Per"
            5 -> "Cum"
            6 -> "Cmt"
            7 -> "Paz"
            else -> "Gün"
        }
    }
}
