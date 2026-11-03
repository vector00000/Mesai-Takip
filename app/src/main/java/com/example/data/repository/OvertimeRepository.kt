package com.example.data.repository

import android.content.Context
import com.example.data.local.ActiveOvertimeDao
import com.example.data.local.BudgetDao
import com.example.data.local.NotificationCenterDao
import com.example.data.local.OvertimeDao
import com.example.data.local.PayrollArchiveDao
import com.example.data.local.RateTariffDao
import com.example.data.local.SettingsDao
import com.example.data.model.ActiveOvertime
import com.example.data.model.AppNotification
import com.example.data.model.AppSettings
import com.example.data.model.BudgetTransaction
import com.example.data.model.NotificationTargetRoute
import com.example.data.model.NotificationType
import com.example.data.model.OvertimeRecord
import com.example.data.model.OvertimeType
import com.example.data.model.PayrollArchiveReport
import com.example.data.model.RateTariffRule
import com.example.util.AchievementBadge
import com.example.util.AlarmScheduler
import com.example.util.NotificationHelper
import com.example.util.PayrollArchiveExporter
import com.example.util.SmartRecordAuditor
import com.example.util.TariffConflictWarning
import com.example.util.TariffRuleEngine
import com.example.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.max

class OvertimeRepository(
    private val context: Context,
    private val overtimeDao: OvertimeDao,
    private val settingsDao: SettingsDao,
    private val activeOvertimeDao: ActiveOvertimeDao,
    private val budgetDao: BudgetDao? = null,
    private val rateTariffDao: RateTariffDao? = null,
    private val payrollArchiveDao: PayrollArchiveDao? = null,
    private val notificationCenterDao: NotificationCenterDao? = null
) {
    private val mutex = Mutex()

    val allRecords: Flow<List<OvertimeRecord>> = overtimeDao.getAllRecords()
    val settingsFlow: Flow<AppSettings?> = settingsDao.getSettingsFlow()
    val activeOvertimeFlow: Flow<ActiveOvertime?> = activeOvertimeDao.getActiveOvertimeFlow()

    val allBudgetTransactionsFlow: Flow<List<BudgetTransaction>> =
        budgetDao?.getAllTransactionsFlow() ?: flowOf(emptyList())

    val allTariffRulesFlow: Flow<List<RateTariffRule>> =
        rateTariffDao?.getAllTariffRulesFlow() ?: flowOf(emptyList())

    val allArchivedReportsFlow: Flow<List<PayrollArchiveReport>> =
        payrollArchiveDao?.getAllArchivedReportsFlow() ?: flowOf(emptyList())

    val allNotificationsFlow: Flow<List<AppNotification>> =
        notificationCenterDao?.getAllNotificationsFlow() ?: flowOf(emptyList())

    val unreadNotificationCountFlow: Flow<Int> =
        notificationCenterDao?.getUnreadCountFlow() ?: flowOf(0)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            checkAndRestoreFromAutoBackup()
        }
    }

    private suspend fun checkAndRestoreFromAutoBackup() {
        try {
            val recordsInDb = overtimeDao.getAllRecordsList()
            val backupFile = File(context.filesDir, "auto_backup_overtime_records.json")
            val sp = context.getSharedPreferences("mesai_records_permanent_backup", Context.MODE_PRIVATE)
            val spJson = sp.getString("backup_records_json", null)

            if (recordsInDb.isEmpty()) {
                var restored = false
                if (!spJson.isNullOrBlank()) {
                    try {
                        val array = JSONArray(spJson)
                        val list = mutableListOf<OvertimeRecord>()
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            list.add(
                                OvertimeRecord(
                                    date = item.getString("date"),
                                    startTimeEpochMs = item.getLong("startTimeEpochMs"),
                                    endTimeEpochMs = item.getLong("endTimeEpochMs"),
                                    startTimeStr = item.getString("startTimeStr"),
                                    endTimeStr = item.getString("endTimeStr"),
                                    durationMinutes = item.getInt("durationMinutes"),
                                    hourlyRate = item.getDouble("hourlyRate"),
                                    totalAmount = item.getDouble("totalAmount"),
                                    overtimeType = item.optString("overtimeType", "WEEKDAY"),
                                    isAutomatic = item.optBoolean("isAutomatic", false),
                                    note = item.optString("note", ""),
                                    createdAtEpochMs = item.optLong("createdAtEpochMs", System.currentTimeMillis()),
                                    breakMinutes = item.optInt("breakMinutes", 0)
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            overtimeDao.insertRecords(list)
                            restored = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (!restored && backupFile.exists()) {
                    val uri = android.net.Uri.fromFile(backupFile)
                    val parseResult = com.example.util.BackupManager.parseBackupFromUri(context, uri)
                    if (parseResult.isSuccess) {
                        val data = parseResult.getOrThrow()
                        if (data.records.isNotEmpty()) {
                            overtimeDao.insertRecords(data.records)
                        }
                    }
                }
            } else {
                syncInternalAutoBackup(recordsInDb)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncInternalAutoBackup(records: List<OvertimeRecord>) {
        try {
            val recordsArray = JSONArray()
            for (rec in records) {
                val item = JSONObject().apply {
                    put("date", rec.date)
                    put("startTimeEpochMs", rec.startTimeEpochMs)
                    put("endTimeEpochMs", rec.endTimeEpochMs)
                    put("startTimeStr", rec.startTimeStr)
                    put("endTimeStr", rec.endTimeStr)
                    put("durationMinutes", rec.durationMinutes)
                    put("hourlyRate", rec.hourlyRate)
                    put("totalAmount", rec.totalAmount)
                    put("overtimeType", rec.overtimeType)
                    put("isAutomatic", rec.isAutomatic)
                    put("note", rec.note)
                    put("createdAtEpochMs", rec.createdAtEpochMs)
                    put("breakMinutes", rec.breakMinutes)
                }
                recordsArray.put(item)
            }

            val sp = context.getSharedPreferences("mesai_records_permanent_backup", Context.MODE_PRIVATE)
            sp.edit().putString("backup_records_json", recordsArray.toString()).apply()

            val backupFile = File(context.filesDir, "auto_backup_overtime_records.json")
            val rootJson = JSONObject().apply {
                put("app", "Mesai Takip")
                put("version", 3)
                put("backupTimestamp", System.currentTimeMillis())
                put("recordCount", records.size)
                put("records", recordsArray)
            }
            FileOutputStream(backupFile).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(rootJson.toString(2))
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun triggerAutoBackupSync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val records = overtimeDao.getAllRecordsList()
                syncInternalAutoBackup(records)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getRecordsForMonth(yearMonthPrefix: String): Flow<List<OvertimeRecord>> {
        return overtimeDao.getRecordsForMonth(yearMonthPrefix)
    }

    suspend fun getSettings(): AppSettings {
        return withContext(Dispatchers.IO) {
            val existing = settingsDao.getSettings()
            if (existing != null) {
                existing
            } else {
                val defaultSettings = AppSettings()
                settingsDao.insertOrUpdate(defaultSettings)
                defaultSettings
            }
        }
    }

    suspend fun updateSettings(settings: AppSettings) {
        withContext(Dispatchers.IO) {
            settingsDao.insertOrUpdate(settings)
            AlarmScheduler.scheduleNextAlarm(context, settings)

            val active = activeOvertimeDao.getActiveOvertime()
            if (active != null && active.isActive) {
                if (settings.forgottenAlertEnabled) {
                    AlarmScheduler.scheduleForgottenOvertimeAlert(
                        context = context,
                        startTimeEpochMs = active.startTimeEpochMs,
                        alertHours = settings.forgottenAlertHours
                    )
                } else {
                    AlarmScheduler.cancelForgottenOvertimeAlert(context)
                    NotificationHelper.cancelForgottenNotification(context)
                }
            }
        }
    }

    suspend fun getActiveOvertime(): ActiveOvertime {
        return withContext(Dispatchers.IO) {
            activeOvertimeDao.getActiveOvertime() ?: run {
                val defaultActive = ActiveOvertime()
                activeOvertimeDao.insertOrUpdate(defaultActive)
                defaultActive
            }
        }
    }

    /**
     * Aktif devam eden mesaiye mola süresi ekler veya mola süresini günceller.
     */
    suspend fun updateActiveBreakMinutes(breakMinutes: Int) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = activeOvertimeDao.getActiveOvertime()
            if (current != null && current.isActive) {
                activeOvertimeDao.insertOrUpdate(
                    current.copy(breakMinutes = breakMinutes.coerceAtLeast(0))
                )
            }
        }
    }

    /**
     * Starts an overtime session. Protected with a mutex to prevent race conditions.
     * Aynı anda iki aktif mesai kaydının açılmasını kesin olarak engeller.
     */
    suspend fun startOvertime(
        startTimeEpochMs: Long = TimeUtils.nowEpochMs(),
        isAutomatic: Boolean = false,
        customStartTimeStr: String? = null,
        targetDate: LocalDate = LocalDate.now(TimeUtils.ISTANBUL_ZONE),
        requestedOvertimeType: OvertimeType? = null
    ): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = activeOvertimeDao.getActiveOvertime()
            if (current != null && current.isActive) {
                // Already running, do not duplicate
                return@withContext false
            }

            val settings = getSettings()
            val startTimeStr = customStartTimeStr ?: TimeUtils.formatTime(startTimeEpochMs)
            val dateStr = TimeUtils.formatDate(targetDate)
            val activeRules = rateTariffDao?.getActiveTariffRulesList() ?: emptyList()

            val resolved = TariffRuleEngine.resolveRate(
                dateStr = dateStr,
                startTimeStr = startTimeStr,
                requestedType = requestedOvertimeType,
                settings = settings,
                activeRules = activeRules
            )

            val newActive = ActiveOvertime(
                id = 1,
                isActive = true,
                startTimeEpochMs = startTimeEpochMs,
                startTimeStr = startTimeStr,
                hourlyRate = resolved.hourlyRate,
                overtimeType = resolved.appliedType.name,
                isAutomatic = isAutomatic,
                dateStr = dateStr,
                breakMinutes = 0
            )
            activeOvertimeDao.insertOrUpdate(newActive)

            var postedSystem = false
            if (settings.notificationsEnabled) {
                postedSystem = NotificationHelper.showOvertimeStartedNotification(
                    context = context,
                    startTimeStr = startTimeStr,
                    isAutomatic = isAutomatic
                )
            }

            recordNotificationEvent(
                eventKey = "OVERTIME_START_${dateStr}_$startTimeEpochMs",
                type = NotificationType.OVERTIME_REMINDER,
                title = if (isAutomatic) "Otomatik Mesai Başlatıldı" else "Mesai Sayacı Başlatıldı",
                message = "$dateStr tarihinde saat $startTimeStr itibarıyla mesai sayacı başlatıldı (Saatlik: ${TimeUtils.formatCurrency(resolved.hourlyRate)}).",
                targetRoute = NotificationTargetRoute.HOME,
                tryPostSystemNotification = false,
                alreadyPostedSystem = postedSystem
            )

            if (settings.forgottenAlertEnabled) {
                AlarmScheduler.scheduleForgottenOvertimeAlert(
                    context = context,
                    startTimeEpochMs = startTimeEpochMs,
                    alertHours = settings.forgottenAlertHours
                )
            }
            true
        }
    }

    suspend fun checkAndTriggerAutomaticOvertime(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = getSettings()
            if (!settings.autoGlobalEnabled) {
                return@withContext false
            }

            val today = LocalDate.now(TimeUtils.ISTANBUL_ZONE)
            val todayStr = TimeUtils.formatDate(today)

            if (settings.skipDate == todayStr) {
                return@withContext false
            }

            val scheduledTime = AlarmScheduler.getScheduleForDate(today, settings) ?: return@withContext false

            val currentActive = activeOvertimeDao.getActiveOvertime()
            if (currentActive != null && currentActive.isActive) {
                return@withContext false
            }

            val todayRecords = overtimeDao.getRecordsForDate(todayStr)
            if (todayRecords.isNotEmpty()) {
                return@withContext false
            }

            val zone = TimeUtils.ISTANBUL_ZONE
            val now = LocalDateTime.now(zone)
            val currentLocalTime = now.toLocalTime()

            if (currentLocalTime.isBefore(scheduledTime)) {
                return@withContext false
            }

            val minutesPast = java.time.Duration.between(scheduledTime, currentLocalTime).toMinutes()
            if (minutesPast > 120L) {
                return@withContext false
            }

            val timeStr = TimeUtils.formatTime(scheduledTime)
            val scheduledStartEpochMs = LocalDateTime.of(today, scheduledTime).atZone(zone).toInstant().toEpochMilli()

            val activeRules = rateTariffDao?.getActiveTariffRulesList() ?: emptyList()
            val resolved = TariffRuleEngine.resolveRate(
                dateStr = todayStr,
                startTimeStr = timeStr,
                requestedType = null,
                settings = settings,
                activeRules = activeRules
            )

            val newActive = ActiveOvertime(
                id = 1,
                isActive = true,
                startTimeEpochMs = scheduledStartEpochMs,
                startTimeStr = timeStr,
                hourlyRate = resolved.hourlyRate,
                overtimeType = resolved.appliedType.name,
                isAutomatic = true,
                dateStr = todayStr,
                breakMinutes = 0
            )
            activeOvertimeDao.insertOrUpdate(newActive)

            var postedSystem = false
            if (settings.notificationsEnabled) {
                postedSystem = NotificationHelper.showOvertimeStartedNotification(
                    context = context,
                    startTimeStr = timeStr,
                    isAutomatic = true
                )
            }

            recordNotificationEvent(
                eventKey = "AUTO_OVERTIME_START_${todayStr}_$timeStr",
                type = NotificationType.OVERTIME_REMINDER,
                title = "Otomatik Mesai Başladı",
                message = "$todayStr günü planlanan saat $timeStr itibarıyla mesainiz otomatik olarak başlatıldı.",
                targetRoute = NotificationTargetRoute.HOME,
                tryPostSystemNotification = false,
                alreadyPostedSystem = postedSystem
            )

            if (settings.forgottenAlertEnabled) {
                AlarmScheduler.scheduleForgottenOvertimeAlert(
                    context = context,
                    startTimeEpochMs = scheduledStartEpochMs,
                    alertHours = settings.forgottenAlertHours
                )
            }

            AlarmScheduler.scheduleNextAlarm(context, settings)
            return@withContext true
        }
    }

    /**
     * Stops the active overtime session, deducts breakMinutes from gross elapsed time,
     * and writes the completed net record to Room database.
     */
    suspend fun stopOvertime(
        endTimeEpochMs: Long = TimeUtils.nowEpochMs(),
        note: String = "",
        overrideBreakMinutes: Int? = null
    ): OvertimeRecord? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = activeOvertimeDao.getActiveOvertime()
            if (current == null || !current.isActive) {
                NotificationHelper.cancelNotification(context)
                AlarmScheduler.cancelForgottenOvertimeAlert(context)
                NotificationHelper.cancelForgottenNotification(context)
                return@withContext null
            }

            val startEpoch = current.startTimeEpochMs
            val endEpoch = max(startEpoch + 1000L, endTimeEpochMs)
            val grossMinutes = max(1, kotlin.math.round((endEpoch - startEpoch) / 60000.0).toInt())
            val requestedBreak = (overrideBreakMinutes ?: current.breakMinutes).coerceAtLeast(0)
            val effectiveBreak = requestedBreak.coerceAtMost(max(0, grossMinutes - 1))
            val netDurationMinutes = max(1, grossMinutes - effectiveBreak)

            val effectiveRate = if (current.hourlyRate > 0.0) current.hourlyRate else getSettings().getEffectiveRate(TimeUtils.isWeekend())
            val totalAmount = if (effectiveBreak > 0) {
                TimeUtils.calculateEarnedAmount(netDurationMinutes, effectiveRate)
            } else {
                TimeUtils.calculateEarnedAmountLive(startEpoch, effectiveRate, endEpoch)
            }
            val endTimeStr = TimeUtils.formatTime(endEpoch)
            val recordDateStr = current.dateStr.ifEmpty { TimeUtils.todayDateStr() }

            val record = OvertimeRecord(
                date = recordDateStr,
                startTimeEpochMs = startEpoch,
                endTimeEpochMs = endEpoch,
                startTimeStr = current.startTimeStr.ifEmpty { TimeUtils.formatTime(startEpoch) },
                endTimeStr = endTimeStr,
                durationMinutes = netDurationMinutes,
                hourlyRate = effectiveRate,
                totalAmount = totalAmount,
                overtimeType = current.overtimeType,
                isAutomatic = current.isAutomatic,
                note = note,
                breakMinutes = effectiveBreak
            )

            val newId = overtimeDao.insertRecord(record)

            // Reset active state
            activeOvertimeDao.insertOrUpdate(ActiveOvertime(id = 1, isActive = false, breakMinutes = 0))

            NotificationHelper.cancelNotification(context)
            AlarmScheduler.cancelForgottenOvertimeAlert(context)
            NotificationHelper.cancelForgottenNotification(context)

            val settings = getSettings()
            AlarmScheduler.scheduleNextAlarm(context, settings)

            val savedRecord = record.copy(id = newId)
            val allList = overtimeDao.getAllRecordsList()
            syncInternalAutoBackup(allList)

            recordNotificationEvent(
                eventKey = "OVERTIME_STOP_${newId}_${endEpoch}",
                type = NotificationType.OVERTIME_REMINDER,
                title = "Mesai Tamamlandı ve Kaydedildi",
                message = "$recordDateStr (${record.startTimeStr}-${record.endTimeStr}) net ${TimeUtils.formatDurationShort(netDurationMinutes)} mesai kaydınız (${TimeUtils.formatCurrency(totalAmount)}) kaydedildi.",
                targetRoute = NotificationTargetRoute.HISTORY,
                tryPostSystemNotification = false
            )

            savedRecord
        }
    }

    suspend fun insertManualRecord(record: OvertimeRecord): Long {
        return withContext(Dispatchers.IO) {
            val id = overtimeDao.insertRecord(record)
            syncInternalAutoBackup(overtimeDao.getAllRecordsList())
            id
        }
    }

    suspend fun updateRecord(record: OvertimeRecord) {
        withContext(Dispatchers.IO) {
            overtimeDao.updateRecord(record)
            syncInternalAutoBackup(overtimeDao.getAllRecordsList())
        }
    }

    suspend fun deleteRecord(recordId: Long) {
        withContext(Dispatchers.IO) {
            overtimeDao.deleteRecordById(recordId)
            syncInternalAutoBackup(overtimeDao.getAllRecordsList())
        }
    }

    // --- Gelir ve Gider Takibi (Bütçe) İşlemleri ---
    suspend fun insertBudgetTransaction(transaction: BudgetTransaction): Long {
        return withContext(Dispatchers.IO) {
            budgetDao?.insertTransaction(transaction) ?: 0L
        }
    }

    suspend fun updateBudgetTransaction(transaction: BudgetTransaction) {
        withContext(Dispatchers.IO) {
            budgetDao?.updateTransaction(transaction)
        }
    }

    suspend fun deleteBudgetTransaction(id: Long) {
        withContext(Dispatchers.IO) {
            budgetDao?.deleteTransactionById(id)
        }
    }

    // --- Çoklu Ücret Tarifesi İşlemleri ---
    suspend fun findTariffConflicts(candidate: RateTariffRule): List<TariffConflictWarning> {
        return withContext(Dispatchers.IO) {
            val existing = rateTariffDao?.getAllTariffRulesList() ?: emptyList()
            TariffRuleEngine.findConflicts(candidate, existing)
        }
    }

    suspend fun insertOrUpdateTariffRule(
        rule: RateTariffRule,
        allowOverlapping: Boolean = false
    ): Result<Long> {
        return withContext(Dispatchers.IO) {
            val existing = rateTariffDao?.getAllTariffRulesList() ?: emptyList()
            val conflicts = TariffRuleEngine.findConflicts(rule, existing)
            if (conflicts.isNotEmpty() && !allowOverlapping) {
                return@withContext Result.failure(
                    IllegalStateException(conflicts.joinToString("\n") { it.message })
                )
            }
            val id = if (rule.id == 0L) {
                rateTariffDao?.insertTariffRule(rule) ?: 0L
            } else {
                rateTariffDao?.updateTariffRule(rule)
                rule.id
            }
            Result.success(id)
        }
    }

    suspend fun deleteTariffRule(id: Long) {
        withContext(Dispatchers.IO) {
            rateTariffDao?.deleteTariffRuleById(id)
        }
    }

    // --- Kişisel Maaş Programlama Raporu Arşivi (Snapshot) İşlemleri ---
    suspend fun createAndSaveMonthlyPayrollArchive(yearMonth: YearMonth): PayrollArchiveReport {
        return withContext(Dispatchers.IO) {
            val ymStr = yearMonth.toString()
            val allList = overtimeDao.getAllRecordsList()
            val monthRecords = allList.filter { it.date.startsWith(ymStr) }
            val settings = getSettings()
            val monthTx = budgetDao?.getTransactionsForMonthList(ymStr) ?: emptyList()
            val activeRules = rateTariffDao?.getActiveTariffRulesList() ?: emptyList()
            val activeTariffName = activeRules.firstOrNull()?.name ?: "Standart Kurumsal Ücret Tarifesi"

            val draftSnapshot = PayrollArchiveExporter.buildMonthlyArchiveSnapshot(
                yearMonth = yearMonth,
                monthRecords = monthRecords,
                settings = settings,
                monthTransactions = monthTx,
                activeTariffName = activeTariffName
            )
            val id = payrollArchiveDao?.insertArchivedReport(draftSnapshot) ?: 1L
            val savedWithId = draftSnapshot.copy(id = id)

            val pdfFile = try {
                PayrollArchiveExporter.exportSnapshotToPdf(context, savedWithId)
            } catch (_: Exception) {
                null
            }
            val csvFile = try {
                PayrollArchiveExporter.exportSnapshotToCsv(context, savedWithId)
            } catch (_: Exception) {
                null
            }

            val finalReport = savedWithId.copy(
                pdfFilePath = pdfFile?.absolutePath ?: "",
                csvFilePath = csvFile?.absolutePath ?: ""
            )
            payrollArchiveDao?.insertArchivedReport(finalReport)

            recordNotificationEvent(
                eventKey = "MONTHLY_REPORT_ARCHIVED_${ymStr}_$id",
                type = NotificationType.MONTHLY_SUMMARY,
                title = "Kişisel Maaş Programlama Raporu Arşivlendi",
                message = "${finalReport.reportTitle} oluşturuldu ve kullanılan ücret tarifeleriyle birlikte arşive kaydedildi.",
                targetRoute = NotificationTargetRoute.PAYROLL_ARCHIVE,
                tryPostSystemNotification = settings.notificationsEnabled && settings.reminderMonthlySummaryEnabled
            )

            finalReport
        }
    }

    suspend fun deletePayrollArchiveReport(id: Long) {
        withContext(Dispatchers.IO) {
            payrollArchiveDao?.deleteArchivedReportById(id)
        }
    }

    // --- Bildirim Merkezi ve Otomatik Hatırlatmalar ---
    /**
     * Aynı `eventKey` ile mükerrer bildirim oluşturulmasını kesin olarak engeller.
     * Gerçek sistem bildirimi izni yoksa veya gönderilemediyse `wasPostedToSystem = false` olarak kaydeder;
     * asla gönderilmiş gibi göstermez. Uygulama içi Bildirim Merkezi izin olmasa da çalışır.
     */
    suspend fun recordNotificationEvent(
        eventKey: String,
        type: NotificationType,
        title: String,
        message: String,
        targetRoute: NotificationTargetRoute = NotificationTargetRoute.HOME,
        tryPostSystemNotification: Boolean = true,
        alreadyPostedSystem: Boolean = false
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val dao = notificationCenterDao ?: return@withContext false
            val existing = dao.getNotificationByEventKey(eventKey)
            if (existing != null) {
                return@withContext false // Mükerrer bildirim engellendi
            }

            val settings = getSettings()
            val typeAllowed = isNotificationTypeEnabled(type, settings)
            if (!typeAllowed) {
                return@withContext false
            }

            var postedToSystem = alreadyPostedSystem
            if (!postedToSystem && tryPostSystemNotification && settings.notificationsEnabled) {
                val notifId = (eventKey.hashCode() and 0x7FFFFFFF).coerceAtLeast(3000)
                postedToSystem = NotificationHelper.postReminderOrCenterSystemNotification(
                    context = context,
                    notificationId = notifId,
                    title = title,
                    message = message,
                    targetRoute = targetRoute.name
                )
            }

            val now = TimeUtils.now()
            val dateTimeStr = "${TimeUtils.formatDate(now.toLocalDate())} ${TimeUtils.formatTime(now.toLocalTime())}"
            val item = AppNotification(
                eventKey = eventKey,
                type = type.name,
                title = title,
                message = message,
                targetRoute = targetRoute.name,
                dateTimeStr = dateTimeStr,
                createdAtEpochMs = TimeUtils.nowEpochMs(),
                isRead = false,
                wasPostedToSystem = postedToSystem
            )
            val insertedId = dao.insertNotificationIgnoreDuplicate(item)
            insertedId > 0L
        }
    }

    fun isNotificationTypeEnabled(type: NotificationType, settings: AppSettings): Boolean {
        return when (type) {
            NotificationType.OVERTIME_REMINDER -> settings.reminderOvertimeEntryEnabled
            NotificationType.SALARY_REMINDER -> settings.reminderSalaryDayEnabled
            NotificationType.UPCOMING_SHIFT -> settings.reminderShiftStartEnabled
            NotificationType.MISSING_RECORD_ALERT -> settings.reminderMissingRecordEnabled
            NotificationType.MONTHLY_SUMMARY -> settings.reminderMonthlySummaryEnabled
            NotificationType.ACHIEVEMENT_UNLOCKED -> settings.reminderAchievementEnabled
            NotificationType.SYSTEM_BACKUP -> true
        }
    }

    /**
     * Otomatik hatırlatmaları (Maaş Günü, Vardiya Başlangıcı, Mesai Kaydı Girme Hatırlatması,
     * Eksik/Hatalı Kayıt Uyarısı) denetler ve mükerrerlik olmadan Bildirim Merkezi'ne işler.
     */
    suspend fun evaluateAndDispatchAutomaticReminders(
        today: LocalDate = LocalDate.now(TimeUtils.ISTANBUL_ZONE)
    ) {
        withContext(Dispatchers.IO) {
            val settings = getSettings()
            val todayStr = TimeUtils.formatDate(today)
            val allList = overtimeDao.getAllRecordsList()
            val active = getActiveOvertime()

            // 1. Maaş günü hatırlatması
            if (settings.reminderSalaryDayEnabled && settings.baseSalary > 0.0) {
                val maxDay = today.lengthOfMonth()
                val targetSalaryDay = settings.salaryDayOfMonth.coerceIn(1, maxDay)
                if (today.dayOfMonth == targetSalaryDay) {
                    recordNotificationEvent(
                        eventKey = "SALARY_DAY_$todayStr",
                        type = NotificationType.SALARY_REMINDER,
                        title = "Bugün Maaş Gününüz!",
                        message = "$todayStr tarihi maaş ödeme gününüz. Maaş ve mesai hakedişinizi Gelir-Gider bütçenizde ve Bordro Arşivinde görüntüleyebilirsiniz.",
                        targetRoute = NotificationTargetRoute.BUDGET,
                        tryPostSystemNotification = settings.notificationsEnabled
                    )
                }
            }

            // 2. Yaklaşan vardiya / mesai başlangıç hatırlatması
            val scheduledTime = AlarmScheduler.getScheduleForDate(today, settings)
            if (settings.reminderShiftStartEnabled && scheduledTime != null && !active.isActive) {
                val nowTime = TimeUtils.now().toLocalTime()
                val minsUntilStart = java.time.Duration.between(nowTime, scheduledTime).toMinutes()
                if (minsUntilStart in 1..settings.shiftReminderLeadMinutes.toLong()) {
                    recordNotificationEvent(
                        eventKey = "SHIFT_UPCOMING_${todayStr}_${TimeUtils.formatTime(scheduledTime)}",
                        type = NotificationType.UPCOMING_SHIFT,
                        title = "Yaklaşan Mesai Başlangıcı (${TimeUtils.formatTime(scheduledTime)})",
                        message = "Planlanan mesai başlangıcınıza $minsUntilStart dakika kaldı.",
                        targetRoute = NotificationTargetRoute.HOME,
                        tryPostSystemNotification = settings.notificationsEnabled
                    )
                }
            }

            // 3. Mesai kaydı girme hatırlatması (Belirlenen hatırlatma saati geçtiyse ve bugün kayıt yoksa)
            if (settings.reminderOvertimeEntryEnabled && scheduledTime != null && !active.isActive) {
                val reminderTime = try { TimeUtils.parseTime(settings.reminderTimeStr) } catch (_: Exception) { java.time.LocalTime.of(20, 30) }
                val nowTime = TimeUtils.now().toLocalTime()
                val todayHasRecord = allList.any { it.date == todayStr }
                if (!todayHasRecord && nowTime.isAfter(reminderTime)) {
                    recordNotificationEvent(
                        eventKey = "OVERTIME_ENTRY_REMINDER_$todayStr",
                        type = NotificationType.OVERTIME_REMINDER,
                        title = "Bugünkü Mesai Kaydınızı Girdiniz mi?",
                        message = "$todayStr günü için henüz mesai kaydı bulunmuyor. Fazla mesai yaptıysanız kaydınızı ekleyebilirsiniz.",
                        targetRoute = NotificationTargetRoute.HOME,
                        tryPostSystemNotification = settings.notificationsEnabled
                    )
                }
            }

            // 4. Eksik veya çakışan çalışma kaydı uyarısı
            if (settings.reminderMissingRecordEnabled) {
                val findings = SmartRecordAuditor.auditRecords(
                    records = allList,
                    activeOvertime = active,
                    settings = settings
                ).filter { !it.issueType.isAdvisoryOnly }

                if (findings.isNotEmpty()) {
                    val topFinding = findings.first()
                    recordNotificationEvent(
                        eventKey = "MISSING_RECORD_ALERT_${topFinding.warningKey}",
                        type = NotificationType.MISSING_RECORD_ALERT,
                        title = topFinding.title,
                        message = topFinding.description,
                        targetRoute = NotificationTargetRoute.SMART_AUDIT,
                        tryPostSystemNotification = settings.notificationsEnabled
                    )
                }
            }
        }
    }

    /**
     * Kazanılan yeni başarıları kontrol eder ve aynı başarı için asla tekrar bildirim göndermez.
     */
    suspend fun checkAndNotifyUnlockedAchievements(badges: List<AchievementBadge>) {
        withContext(Dispatchers.IO) {
            val settings = getSettings()
            if (!settings.reminderAchievementEnabled) return@withContext

            val sp = context.getSharedPreferences("mesai_achievements_notified_prefs", Context.MODE_PRIVATE)
            val notifiedSet = sp.getStringSet("notified_badge_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            var changed = false

            for (badge in badges) {
                if (badge.isUnlocked && badge.id !in notifiedSet) {
                    notifiedSet.add(badge.id)
                    changed = true
                    recordNotificationEvent(
                        eventKey = "ACHIEVEMENT_UNLOCKED_${badge.id}",
                        type = NotificationType.ACHIEVEMENT_UNLOCKED,
                        title = "Yeni Başarı Kazanıldı: ${badge.title}",
                        message = "${badge.conditionDescription} (${badge.unlockedDateStr ?: TimeUtils.todayDateStr()})",
                        targetRoute = NotificationTargetRoute.ACHIEVEMENTS,
                        tryPostSystemNotification = settings.notificationsEnabled
                    )
                }
            }

            if (changed) {
                sp.edit().putStringSet("notified_badge_ids", notifiedSet).apply()
            }
        }
    }

    suspend fun markNotificationRead(id: Long, isRead: Boolean = true) {
        withContext(Dispatchers.IO) {
            notificationCenterDao?.setNotificationReadState(id, isRead)
        }
    }

    suspend fun markAllNotificationsRead() {
        withContext(Dispatchers.IO) {
            notificationCenterDao?.markAllAsRead()
        }
    }

    suspend fun deleteNotification(id: Long) {
        withContext(Dispatchers.IO) {
            notificationCenterDao?.deleteNotificationById(id)
        }
    }

    suspend fun clearAllNotifications() {
        withContext(Dispatchers.IO) {
            notificationCenterDao?.clearAllNotifications()
        }
    }

    suspend fun setSkipToday(skip: Boolean) {
        withContext(Dispatchers.IO) {
            val current = getSettings()
            val todayStr = TimeUtils.todayDateStr()
            val updated = current.copy(
                skipDate = if (skip) todayStr else ""
            )
            settingsDao.insertOrUpdate(updated)
            AlarmScheduler.scheduleNextAlarm(context, updated)
        }
    }

    suspend fun setAutoGlobalEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val current = getSettings()
            val updated = current.copy(autoGlobalEnabled = enabled)
            settingsDao.insertOrUpdate(updated)
            AlarmScheduler.scheduleNextAlarm(context, updated)
        }
    }

    suspend fun getTodayRecords(): List<OvertimeRecord> {
        return withContext(Dispatchers.IO) {
            overtimeDao.getRecordsForDate(TimeUtils.todayDateStr())
        }
    }

    suspend fun getAllRecordsList(): List<OvertimeRecord> {
        return withContext(Dispatchers.IO) {
            overtimeDao.getAllRecordsList()
        }
    }

    suspend fun restoreData(
        records: List<OvertimeRecord>,
        restoredSettings: AppSettings?,
        clearExisting: Boolean
    ): Int {
        return withContext(Dispatchers.IO) {
            if (clearExisting) {
                overtimeDao.deleteAllRecords()
            }
            val cleanRecords = records.map {
                if (clearExisting) it else it.copy(id = 0)
            }
            overtimeDao.insertRecords(cleanRecords)

            if (restoredSettings != null) {
                settingsDao.insertOrUpdate(restoredSettings.copy(id = 1))
                AlarmScheduler.scheduleNextAlarm(context, restoredSettings)
            }
            syncInternalAutoBackup(overtimeDao.getAllRecordsList())
            recordNotificationEvent(
                eventKey = "SYSTEM_RESTORE_${System.currentTimeMillis() / 60000L}",
                type = NotificationType.SYSTEM_BACKUP,
                title = "Yedek Geri Yükleme Tamamlandı",
                message = "${cleanRecords.size} adet mesai kaydı başarıyla geri yüklendi.",
                targetRoute = NotificationTargetRoute.HISTORY,
                tryPostSystemNotification = false
            )
            cleanRecords.size
        }
    }
}
