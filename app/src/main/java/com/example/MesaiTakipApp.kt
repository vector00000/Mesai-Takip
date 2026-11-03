package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.OvertimeRepository
import com.example.util.AlarmScheduler
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MesaiTakipApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: OvertimeRepository by lazy {
        OvertimeRepository(
            context = this,
            overtimeDao = database.overtimeDao(),
            settingsDao = database.settingsDao(),
            activeOvertimeDao = database.activeOvertimeDao(),
            budgetDao = database.budgetDao(),
            rateTariffDao = database.rateTariffDao(),
            payrollArchiveDao = database.payrollArchiveDao(),
            notificationCenterDao = database.notificationCenterDao()
        )
    }
    val billingManager: com.example.data.billing.BillingManager by lazy {
        com.example.data.billing.BillingManager(context = this)
    }
    val documentArchiveRepository: com.example.data.repository.DocumentArchiveRepository by lazy {
        com.example.data.repository.DocumentArchiveRepository(
            context = this,
            payrollDocumentDao = database.payrollDocumentDao(),
            accountIdProvider = { billingManager.getCurrentUserAccountId() },
            premiumEntitlementChecker = { billingManager.hasVerifiedPremiumEntitlement() }
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        com.example.ui.theme.ThemePreferences.init(this)

        // Initialize Google Play Billing connection for subscriptions
        billingManager.startConnection()

        // Ensure alarm is scheduled according to user settings and check if overtime should auto-start now
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.checkAndTriggerAutomaticOvertime()
                val settings = repository.getSettings()
                AlarmScheduler.scheduleNextAlarm(this@MesaiTakipApp, settings)
                repository.evaluateAndDispatchAutomaticReminders()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
