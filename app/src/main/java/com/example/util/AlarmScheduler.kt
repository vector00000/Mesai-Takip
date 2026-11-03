package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.AppSettings
import com.example.receiver.OvertimeAlarmReceiver
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {
    const val ALARM_REQUEST_CODE = 2001
    const val FORGOTTEN_ALARM_REQUEST_CODE = 2002

    fun canScheduleExact(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    fun scheduleNextAlarm(context: Context, settings: AppSettings) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (!settings.autoGlobalEnabled) {
            cancelAlarm(context)
            return
        }

        val nextTriggerTime = findNextTriggerDateTime(settings) ?: run {
            cancelAlarm(context)
            return
        }

        val triggerEpochMs = nextTriggerTime.atZone(TimeUtils.ISTANBUL_ZONE).toInstant().toEpochMilli()

        val intent = Intent(context, OvertimeAlarmReceiver::class.java).apply {
            action = OvertimeAlarmReceiver.ACTION_AUTO_START_OVERTIME
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerEpochMs, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, OvertimeAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleForgottenOvertimeAlert(
        context: Context,
        startTimeEpochMs: Long,
        alertHours: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val alertEpochMs = startTimeEpochMs + (alertHours * 3600 * 1000L)

        // If the target alert time is already in the past, don't schedule or schedule immediately
        val now = TimeUtils.nowEpochMs()
        val triggerAt = kotlin.math.max(now + 5000L, alertEpochMs)

        val intent = Intent(context, com.example.receiver.ForgottenOvertimeReceiver::class.java).apply {
            action = com.example.receiver.ForgottenOvertimeReceiver.ACTION_CHECK_FORGOTTEN_OVERTIME
            putExtra(com.example.receiver.ForgottenOvertimeReceiver.EXTRA_HOURS, alertHours)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            FORGOTTEN_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelForgottenOvertimeAlert(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.example.receiver.ForgottenOvertimeReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            FORGOTTEN_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Determines whether the given day is enabled in settings and returns the configured LocalTime.
     */
    fun getScheduleForDate(date: LocalDate, settings: AppSettings): LocalTime? {
        val dateStr = TimeUtils.formatDate(date)
        if (settings.skipDate == dateStr) {
            return null // "Bugün Mesai Yok" was enabled for this date
        }

        return when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> if (settings.autoMonday) parseTimeSafe(settings.weekdayStartTime) else null
            DayOfWeek.TUESDAY -> if (settings.autoTuesday) parseTimeSafe(settings.weekdayStartTime) else null
            DayOfWeek.WEDNESDAY -> if (settings.autoWednesday) parseTimeSafe(settings.weekdayStartTime) else null
            DayOfWeek.THURSDAY -> if (settings.autoThursday) parseTimeSafe(settings.weekdayStartTime) else null
            DayOfWeek.FRIDAY -> if (settings.autoFriday) parseTimeSafe(settings.weekdayStartTime) else null
            DayOfWeek.SATURDAY -> if (settings.autoSaturday) parseTimeSafe(settings.saturdayStartTime) else null
            DayOfWeek.SUNDAY -> if (settings.autoSunday) parseTimeSafe(settings.sundayStartTime) else null
            null -> null
        }
    }

    private fun parseTimeSafe(timeStr: String): LocalTime {
        return try {
            TimeUtils.parseTime(timeStr)
        } catch (e: Exception) {
            LocalTime.of(18, 0)
        }
    }

    /**
     * Searches up to 8 days in the future to find the next active scheduled slot.
     */
    fun findNextTriggerDateTime(settings: AppSettings): LocalDateTime? {
        if (!settings.autoGlobalEnabled) {
            return null
        }
        val now = TimeUtils.now()
        val today = now.toLocalDate()

        for (offset in 0..7) {
            val checkDate = today.plusDays(offset.toLong())
            val scheduledTime = getScheduleForDate(checkDate, settings) ?: continue
            val scheduledDateTime = LocalDateTime.of(checkDate, scheduledTime)

            if (scheduledDateTime.isAfter(now)) {
                return scheduledDateTime
            }
        }
        return null
    }
}
