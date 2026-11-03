package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.data.local.AppDatabase
import com.example.data.repository.OvertimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OvertimeAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_AUTO_START_OVERTIME = "com.example.mesaitakip.ACTION_AUTO_START_OVERTIME"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MesaiTakip:AutoStartWakeLock"
        )
        wakeLock?.acquire(30000L)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repository = OvertimeRepository(
                    context = context,
                    overtimeDao = db.overtimeDao(),
                    settingsDao = db.settingsDao(),
                    activeOvertimeDao = db.activeOvertimeDao()
                )

                repository.checkAndTriggerAutomaticOvertime()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                wakeLock?.let {
                    if (it.isHeld) it.release()
                }
                pendingResult.finish()
            }
        }
    }
}
