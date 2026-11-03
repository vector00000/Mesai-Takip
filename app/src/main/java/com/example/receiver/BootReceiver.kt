package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.repository.OvertimeRepository
import com.example.util.AlarmScheduler
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val repository = OvertimeRepository(
                        context = context,
                        overtimeDao = db.overtimeDao(),
                        settingsDao = db.settingsDao(),
                        activeOvertimeDao = db.activeOvertimeDao(),
                        budgetDao = db.budgetDao(),
                        rateTariffDao = db.rateTariffDao(),
                        payrollArchiveDao = db.payrollArchiveDao(),
                        notificationCenterDao = db.notificationCenterDao()
                    )

                    val settings = repository.getSettings()

                    // Check if overtime should be auto-started right now
                    repository.checkAndTriggerAutomaticOvertime()

                    // Re-schedule alarms and evaluate planned reminders on device reboot
                    AlarmScheduler.scheduleNextAlarm(context, settings)
                    repository.evaluateAndDispatchAutomaticReminders()

                    // If active overtime was running before phone restart, resume ongoing notification
                    val active = repository.getActiveOvertime()
                    if (active.isActive) {
                        if (settings.notificationsEnabled) {
                            NotificationHelper.showOvertimeStartedNotification(
                                context = context,
                                startTimeStr = active.startTimeStr,
                                isAutomatic = active.isAutomatic
                            )
                        }
                        if (settings.forgottenAlertEnabled) {
                            AlarmScheduler.scheduleForgottenOvertimeAlert(
                                context = context,
                                startTimeEpochMs = active.startTimeEpochMs,
                                alertHours = settings.forgottenAlertHours
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
