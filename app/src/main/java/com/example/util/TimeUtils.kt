package com.example.util

import com.example.data.model.OvertimeType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

object TimeUtils {
    val ISTANBUL_ZONE: ZoneId = try { ZoneId.systemDefault() } catch (e: Exception) { ZoneId.of("Europe/Istanbul") }
    val TURKISH_LOCALE: Locale = Locale.forLanguageTag("tr-TR")

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy EEEE", TURKISH_LOCALE)
    private val MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", TURKISH_LOCALE)
    private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM EEEE", TURKISH_LOCALE)

    private val CURRENCY_FORMAT = NumberFormat.getNumberInstance(TURKISH_LOCALE).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun now(): LocalDateTime {
        return LocalDateTime.now(ISTANBUL_ZONE)
    }

    fun nowEpochMs(): Long {
        return Instant.now().toEpochMilli()
    }

    fun todayDateStr(): String {
        return LocalDate.now(ISTANBUL_ZONE).format(DATE_FORMATTER)
    }

    fun parseDate(dateStr: String): LocalDate {
        return LocalDate.parse(dateStr, DATE_FORMATTER)
    }

    fun formatDate(date: LocalDate): String {
        return date.format(DATE_FORMATTER)
    }

    fun formatTodayFull(): String {
        return LocalDate.now(ISTANBUL_ZONE).format(FULL_DATE_FORMATTER)
    }

    fun formatFullDate(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, DATE_FORMATTER)
            date.format(FULL_DATE_FORMATTER)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatShortDate(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, DATE_FORMATTER)
            date.format(SHORT_DATE_FORMATTER)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatDateTurkish(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr, DATE_FORMATTER)
            date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", TURKISH_LOCALE))
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatMonthYear(yearMonth: YearMonth): String {
        return yearMonth.format(MONTH_YEAR_FORMATTER).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(TURKISH_LOCALE) else it.toString()
        }
    }

    fun formatTime(time: LocalTime): String {
        return time.format(TIME_FORMATTER)
    }

    fun formatTime(epochMs: Long): String {
        return Instant.ofEpochMilli(epochMs)
            .atZone(ISTANBUL_ZONE)
            .toLocalTime()
            .format(TIME_FORMATTER)
    }

    fun parseTime(timeStr: String): LocalTime {
        val clean = timeStr.trim()
        val parts = clean.split(":")
        if (parts.size >= 2) {
            val hour = parts[0].trim().toIntOrNull() ?: 18
            val minute = parts[1].trim().toIntOrNull() ?: 0
            return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        }
        return try {
            LocalTime.parse(clean, TIME_FORMATTER)
        } catch (e: Exception) {
            LocalTime.of(18, 0)
        }
    }

    fun isWeekend(date: LocalDate = LocalDate.now(ISTANBUL_ZONE)): Boolean {
        val dow = date.dayOfWeek
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
    }

    fun getOvertimeType(date: LocalDate = LocalDate.now(ISTANBUL_ZONE)): OvertimeType {
        return if (isWeekend(date)) OvertimeType.WEEKEND else OvertimeType.WEEKDAY
    }

    fun formatCurrency(amount: Double): String {
        val bd = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
        return "${CURRENCY_FORMAT.format(bd.toDouble())} TL"
    }

    fun formatDuration(minutes: Int): String {
        val hrs = minutes / 60
        val mins = minutes % 60
        return when {
            hrs > 0 && mins > 0 -> "${hrs} saat ${mins} dakika"
            hrs > 0 -> "${hrs} saat"
            else -> "${mins} dakika"
        }
    }

    fun formatDurationShort(minutes: Int): String {
        val hrs = minutes / 60
        val mins = minutes % 60
        return when {
            hrs > 0 && mins > 0 -> "${hrs} sa ${mins} dk"
            hrs > 0 -> "${hrs} sa"
            else -> "${mins} dk"
        }
    }

    fun formatDurationWithSeconds(totalSeconds: Long): String {
        val hrs = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return String.format(Locale.ROOT, "%02d saat %02d dakika %02d sn", hrs, mins, secs)
    }

    fun calculateElapsedMinutes(startEpochMs: Long, endEpochMs: Long = nowEpochMs()): Int {
        val diffMs = max(0L, endEpochMs - startEpochMs)
        return (diffMs / 60000L).toInt()
    }

    /**
     * Accurate monetary calculation using BigDecimal with HALF_UP rounding at 2 decimal places.
     * Prevents floating point errors like 199.99999999 TL.
     * Formula: (minutes * hourlyRate) / 60
     */
    fun calculateEarnedAmount(minutes: Int, hourlyRate: Double): Double {
        if (minutes <= 0 || hourlyRate <= 0.0) return 0.0
        val rateBd = BigDecimal.valueOf(hourlyRate)
        val minsBd = BigDecimal.valueOf(minutes.toLong())
        val sixty = BigDecimal.valueOf(60)

        val total = minsBd.multiply(rateBd).divide(sixty, 2, RoundingMode.HALF_UP)
        return total.toDouble()
    }

    /**
     * Accurate live earnings calculation ticking by milliseconds.
     * Formula: (diffMs * hourlyRate) / 3600000 ms
     */
    fun calculateEarnedAmountLive(startEpochMs: Long, hourlyRate: Double, nowEpoch: Long = nowEpochMs()): Double {
        val diffMs = max(0L, nowEpoch - startEpochMs)
        if (diffMs <= 0L || hourlyRate <= 0.0) return 0.0

        val diffMsBd = BigDecimal.valueOf(diffMs)
        val rateBd = BigDecimal.valueOf(hourlyRate)
        val msInHour = BigDecimal.valueOf(3600000L)

        val total = diffMsBd.multiply(rateBd).divide(msInHour, 2, RoundingMode.HALF_UP)
        return total.toDouble()
    }

    /**
     * Calculates duration in minutes from start "HH:mm" to end "HH:mm".
     * If end time is before or equal to start time, handles overnight shift (crossing midnight).
     */
    fun calculateManualMinutes(startTimeStr: String, endTimeStr: String): Int {
        val start = parseTime(startTimeStr)
        val end = parseTime(endTimeStr)

        val startMins = start.hour * 60 + start.minute
        var endMins = end.hour * 60 + end.minute

        if (endMins <= startMins) {
            // Crossed midnight: add 24 hours (1440 minutes)
            endMins += 24 * 60
        }
        return endMins - startMins
    }

    /**
     * Başlangıç ve bitiş saati arasındaki brüt süreden mola süresini (dakika) düşerek
     * net çalışma süresini hesaplar. Gece yarısını geçen vardiyaları doğru işler.
     */
    fun calculateManualMinutesWithBreak(
        startTimeStr: String,
        endTimeStr: String,
        breakMinutes: Int = 0
    ): Int {
        val gross = calculateManualMinutes(startTimeStr, endTimeStr)
        val safeBreak = breakMinutes.coerceIn(0, max(0, gross - 1))
        return max(1, gross - safeBreak)
    }

    /**
     * Gerçek başlangıç ve bitiş zaman damgalarından (epoch ms) mola süresini düşerek
     * net çalışma süresini (dakika) hesaplar.
     */
    fun calculateNetElapsedMinutes(
        startEpochMs: Long,
        endEpochMs: Long = nowEpochMs(),
        breakMinutes: Int = 0
    ): Int {
        val diffMs = max(0L, endEpochMs - startEpochMs)
        val grossMinutes = max(1, kotlin.math.round(diffMs / 60000.0).toInt())
        val safeBreak = breakMinutes.coerceIn(0, max(0, grossMinutes - 1))
        return max(1, grossMinutes - safeBreak)
    }

    /**
     * Calculates epoch millis for a given date ("yyyy-MM-dd") and time ("HH:mm").
     * Supports month and year boundaries when nextDay is true.
     */
    fun toEpochMs(dateStr: String, timeStr: String, nextDay: Boolean = false): Long {
        var date = parseDate(dateStr)
        if (nextDay) {
            date = date.plusDays(1)
        }
        val time = parseTime(timeStr)
        val zonedDateTime = LocalDateTime.of(date, time).atZone(ISTANBUL_ZONE)
        return zonedDateTime.toInstant().toEpochMilli()
    }
}
