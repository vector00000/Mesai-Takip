package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ForgottenOvertimeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CHECK_FORGOTTEN_OVERTIME = "com.example.mesaitakip.ACTION_CHECK_FORGOTTEN_OVERTIME"
        const val EXTRA_HOURS = "extra_hours"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val active = db.activeOvertimeDao().getActiveOvertime()
                val settings = db.settingsDao().getSettings()

                if (active != null && active.isActive) {
                    val alertEnabled = settings?.forgottenAlertEnabled ?: true
                    if (alertEnabled) {
                        val hours = intent?.getIntExtra(EXTRA_HOURS, settings?.forgottenAlertHours ?: 5)
                            ?: (settings?.forgottenAlertHours ?: 5)
                        NotificationHelper.showForgottenOvertimeAlertNotification(
                            context = context,
                            hoursElapsed = hours,
                            startTimeStr = active.startTimeStr
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
