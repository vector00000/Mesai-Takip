package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.repository.OvertimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OvertimeActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STOP_OVERTIME = "com.example.mesaitakip.ACTION_STOP_OVERTIME"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP_OVERTIME) {
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
                    repository.stopOvertime()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
