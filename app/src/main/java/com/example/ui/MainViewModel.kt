package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MesaiTakipApp
import com.example.data.model.ActiveOvertime
import com.example.data.model.AppNotification
import com.example.data.model.AppSettings
import com.example.data.model.BudgetCategory
import com.example.data.model.BudgetTransaction
import com.example.data.model.BudgetTransactionType
import com.example.data.model.NotificationTargetRoute
import com.example.data.model.OvertimeRecord
import com.example.data.model.OvertimeType
import com.example.data.model.PayrollArchiveReport
import com.example.data.model.RateTariffRule
import com.example.data.repository.OvertimeRepository
import com.example.util.AchievementCalculator
import com.example.util.AlarmScheduler
import com.example.util.BackupManager
import com.example.util.CsvExporter
import com.example.util.ExportHelper
import com.example.util.PayrollArchiveExporter
import com.example.util.PdfReportGenerator
import com.example.util.TimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

enum class NavTab(val title: String) {
    HOME("Ana Sayfa"),
    HISTORY("Geçmiş"),
    REPORTS("Raporlar"),
    SETTINGS("Ayarlar")
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    application: Application,
    private val repository: OvertimeRepository,
    val documentArchiveRepository: com.example.data.repository.DocumentArchiveRepository? = null
) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .map { it ?: AppSettings() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    val activeOvertime: StateFlow<ActiveOvertime> = repository.activeOvertimeFlow
        .map { it ?: ActiveOvertime() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ActiveOvertime()
        )

    val allRecords: StateFlow<List<OvertimeRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userDocuments: StateFlow<List<com.example.data.model.PayrollDocument>> =
        (documentArchiveRepository?.getDocumentsForCurrentUserFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val budgetTransactions: StateFlow<List<BudgetTransaction>> = repository.allBudgetTransactionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val tariffRules: StateFlow<List<RateTariffRule>> = repository.allTariffRulesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val archivedPayrollReports: StateFlow<List<PayrollArchiveReport>> = repository.allArchivedReportsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val notifications: StateFlow<List<AppNotification>> = repository.allNotificationsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unreadNotificationCount: StateFlow<Int> = repository.unreadNotificationCountFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    private val auditPrefs = application.getSharedPreferences("mesai_smart_audit_prefs", Context.MODE_PRIVATE)
    private val _dismissedAuditKeys = MutableStateFlow<Set<String>>(
        auditPrefs.getStringSet("dismissed_keys", emptySet())?.toSet() ?: emptySet()
    )
    val dismissedAuditKeys: StateFlow<Set<String>> = _dismissedAuditKeys.asStateFlow()

    private val _currentTab = MutableStateFlow(NavTab.HOME)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now(TimeUtils.ISTANBUL_ZONE))
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth.asStateFlow()

    val monthlyRecords: StateFlow<List<OvertimeRecord>> = _selectedYearMonth.flatMapLatest { ym ->
        val prefix = ym.toString()
        repository.getRecordsForMonth(prefix)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _liveElapsedSeconds = MutableStateFlow(0L)
    val liveElapsedSeconds: StateFlow<Long> = _liveElapsedSeconds.asStateFlow()

    private val _liveEarnings = MutableStateFlow(0.0)
    val liveEarnings: StateFlow<Double> = _liveEarnings.asStateFlow()

    private val _retroactivePromptTime = MutableStateFlow<String?>(null)
    val retroactivePromptTime: StateFlow<String?> = _retroactivePromptTime.asStateFlow()

    private val _isDocumentArchiveOpen = MutableStateFlow(false)
    val isDocumentArchiveOpen: StateFlow<Boolean> = _isDocumentArchiveOpen.asStateFlow()

    private val _isAchievementsOpen = MutableStateFlow(false)
    val isAchievementsOpen: StateFlow<Boolean> = _isAchievementsOpen.asStateFlow()

    private val _isNotificationCenterOpen = MutableStateFlow(false)
    val isNotificationCenterOpen: StateFlow<Boolean> = _isNotificationCenterOpen.asStateFlow()

    private val _isBudgetScreenOpen = MutableStateFlow(false)
    val isBudgetScreenOpen: StateFlow<Boolean> = _isBudgetScreenOpen.asStateFlow()

    private val _isTariffScreenOpen = MutableStateFlow(false)
    val isTariffScreenOpen: StateFlow<Boolean> = _isTariffScreenOpen.asStateFlow()

    private val _isSmartAuditScreenOpen = MutableStateFlow(false)
    val isSmartAuditScreenOpen: StateFlow<Boolean> = _isSmartAuditScreenOpen.asStateFlow()

    private fun closeAllSubScreens() {
        _isDocumentArchiveOpen.value = false
        _isAchievementsOpen.value = false
        _isNotificationCenterOpen.value = false
        _isBudgetScreenOpen.value = false
        _isTariffScreenOpen.value = false
        _isSmartAuditScreenOpen.value = false
    }

    fun openDocumentArchiveScreen() {
        closeAllSubScreens()
        _isDocumentArchiveOpen.value = true
    }

    fun closeDocumentArchiveScreen() {
        _isDocumentArchiveOpen.value = false
    }

    fun openAchievementsScreen() {
        closeAllSubScreens()
        _isAchievementsOpen.value = true
    }

    fun closeAchievementsScreen() {
        _isAchievementsOpen.value = false
    }

    fun openNotificationCenterScreen() {
        closeAllSubScreens()
        _isNotificationCenterOpen.value = true
    }

    fun closeNotificationCenterScreen() {
        _isNotificationCenterOpen.value = false
    }

    fun openBudgetScreen() {
        closeAllSubScreens()
        _isBudgetScreenOpen.value = true
    }

    fun closeBudgetScreen() {
        _isBudgetScreenOpen.value = false
    }

    fun openTariffScreen() {
        closeAllSubScreens()
        _isTariffScreenOpen.value = true
    }

    fun closeTariffScreen() {
        _isTariffScreenOpen.value = false
    }

    fun openSmartAuditScreen() {
        closeAllSubScreens()
        _isSmartAuditScreenOpen.value = true
    }

    fun closeSmartAuditScreen() {
        _isSmartAuditScreenOpen.value = false
    }

    fun navigateFromNotification(targetRouteStr: String) {
        val route = NotificationTargetRoute.fromName(targetRouteStr)
        closeAllSubScreens()
        when (route) {
            NotificationTargetRoute.HOME -> _currentTab.value = NavTab.HOME
            NotificationTargetRoute.HISTORY -> _currentTab.value = NavTab.HISTORY
            NotificationTargetRoute.REPORTS -> _currentTab.value = NavTab.REPORTS
            NotificationTargetRoute.SETTINGS -> _currentTab.value = NavTab.SETTINGS
            NotificationTargetRoute.ACHIEVEMENTS -> _isAchievementsOpen.value = true
            NotificationTargetRoute.PAYROLL_ARCHIVE -> _isDocumentArchiveOpen.value = true
            NotificationTargetRoute.BUDGET -> _isBudgetScreenOpen.value = true
            NotificationTargetRoute.SMART_AUDIT -> _isSmartAuditScreenOpen.value = true
        }
    }

    private var tickerJob: Job? = null
    private var retroactiveDismissedForSession = false

    init {
        viewModelScope.launch {
            activeOvertime.collect { active ->
                if (active.isActive && active.startTimeEpochMs > 0L) {
                    val rate = if (active.hourlyRate > 0.0) active.hourlyRate
                    else settings.value.getEffectiveRate(TimeUtils.isWeekend())
                    startLiveTicker(active.startTimeEpochMs, rate, active.breakMinutes)
                } else {
                    stopLiveTicker()
                }
            }
        }

        // Yeni başarı kazanıldığında Bildirim Merkezi'ne tek seferlik bildirim oluştur
        viewModelScope.launch {
            combine(allRecords, settings, userDocuments, archivedPayrollReports) { recs, set, docs, reports ->
                val firstDocDate = docs.minByOrNull { it.documentDate }?.documentDate
                val firstRepDate = reports.minByOrNull { it.createdDateStr }?.createdDateStr
                AchievementCalculator.calculateSummary(
                    allRecords = recs,
                    selectedYearMonth = _selectedYearMonth.value,
                    settings = set,
                    archivedDocumentsCount = docs.size,
                    firstArchivedDocumentDate = firstDocDate,
                    archivedReportsCount = reports.size,
                    firstArchivedReportDate = firstRepDate
                ).allBadges
            }.collect { allBadges ->
                repository.checkAndNotifyUnlockedAchievements(allBadges)
            }
        }

        viewModelScope.launch {
            repository.checkAndTriggerAutomaticOvertime()
            repository.evaluateAndDispatchAutomaticReminders()
            while (isActive) {
                delay(10000)
                if (!activeOvertime.value.isActive) {
                    repository.checkAndTriggerAutomaticOvertime()
                }
            }
        }
    }

    private fun startLiveTicker(startTimeEpochMs: Long, hourlyRate: Double, breakMinutes: Int) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            val safeRate = if (hourlyRate > 0.0) hourlyRate
            else settings.value.getEffectiveRate(TimeUtils.isWeekend())
            val breakSeconds = (breakMinutes.coerceAtLeast(0) * 60L)
            while (isActive) {
                val now = TimeUtils.nowEpochMs()
                val grossSeconds = kotlin.math.max(0L, (now - startTimeEpochMs) / 1000L)
                val netSeconds = kotlin.math.max(0L, grossSeconds - breakSeconds)
                _liveElapsedSeconds.value = netSeconds
                val effectiveStartForEarnings = now - (netSeconds * 1000L)
                _liveEarnings.value = TimeUtils.calculateEarnedAmountLive(effectiveStartForEarnings, safeRate, now)
                delay(1000)
            }
        }
    }

    private fun stopLiveTicker() {
        tickerJob?.cancel()
        tickerJob = null
        _liveElapsedSeconds.value = 0L
        _liveEarnings.value = 0.0
    }

    fun selectTab(tab: NavTab) {
        closeAllSubScreens()
        _currentTab.value = tab
    }

    fun startOvertime(customStartTimeStr: String? = null, requestedType: OvertimeType? = null) {
        viewModelScope.launch {
            val nowEpoch = TimeUtils.nowEpochMs()
            repository.startOvertime(
                startTimeEpochMs = nowEpoch,
                isAutomatic = false,
                customStartTimeStr = customStartTimeStr,
                requestedOvertimeType = requestedType
            )
            _retroactivePromptTime.value = null
        }
    }

    fun updateActiveBreakMinutes(breakMinutes: Int) {
        viewModelScope.launch {
            repository.updateActiveBreakMinutes(breakMinutes)
        }
    }

    fun stopOvertime(note: String = "", breakMinutes: Int? = null) {
        viewModelScope.launch {
            repository.stopOvertime(note = note, overrideBreakMinutes = breakMinutes)
            stopLiveTicker()
        }
    }

    fun startRetroactive(startTimeStr: String) {
        viewModelScope.launch {
            val todayStr = TimeUtils.todayDateStr()
            val startEpochMs = TimeUtils.toEpochMs(todayStr, startTimeStr)
            repository.startOvertime(
                startTimeEpochMs = startEpochMs,
                isAutomatic = true,
                customStartTimeStr = startTimeStr
            )
            _retroactivePromptTime.value = null
            retroactiveDismissedForSession = true
        }
    }

    fun dismissRetroactivePrompt() {
        _retroactivePromptTime.value = null
        retroactiveDismissedForSession = true
    }

    fun toggleSkipToday() {
        viewModelScope.launch {
            val todayStr = TimeUtils.todayDateStr()
            val isCurrentlySkipped = settings.value.skipDate == todayStr
            repository.setSkipToday(!isCurrentlySkipped)
            if (isCurrentlySkipped) {
                repository.checkAndTriggerAutomaticOvertime()
            }
        }
    }

    fun toggleAutoGlobal(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoGlobalEnabled(enabled)
            if (enabled) {
                repository.checkAndTriggerAutomaticOvertime()
            }
        }
    }

    fun updateSettings(updated: AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(updated)
            repository.checkAndTriggerAutomaticOvertime()
            repository.evaluateAndDispatchAutomaticReminders()
        }
    }

    fun completeInitialSetup(
        weekdayRate: Double,
        weekendRate: Double,
        weekdayStartTime: String,
        saturdayEnabled: Boolean,
        saturdayStartTime: String,
        sundayEnabled: Boolean,
        sundayStartTime: String
    ) {
        viewModelScope.launch {
            val updated = settings.value.copy(
                isSetupCompleted = true,
                weekdayRate = weekdayRate,
                weekendRate = weekendRate,
                weekdayStartTime = weekdayStartTime,
                autoSaturday = saturdayEnabled,
                saturdayStartTime = saturdayStartTime,
                autoSunday = sundayEnabled,
                sundayStartTime = sundayStartTime
            )
            repository.updateSettings(updated)
            repository.checkAndTriggerAutomaticOvertime()
        }
    }

    fun checkAutoOvertime() {
        viewModelScope.launch {
            repository.checkAndTriggerAutomaticOvertime()
            repository.evaluateAndDispatchAutomaticReminders()
        }
    }

    fun addManualRecord(
        dateStr: String,
        startTimeStr: String,
        endTimeStr: String,
        hourlyRate: Double,
        overtimeType: OvertimeType,
        note: String,
        breakMinutes: Int = 0
    ) {
        viewModelScope.launch {
            val safeBreak = breakMinutes.coerceAtLeast(0)
            val netDurationMinutes = TimeUtils.calculateManualMinutesWithBreak(startTimeStr, endTimeStr, safeBreak)
            val totalAmount = TimeUtils.calculateEarnedAmount(netDurationMinutes, hourlyRate)
            val startEpoch = TimeUtils.toEpochMs(dateStr, startTimeStr)
            val crossedMidnight = !TimeUtils.parseTime(endTimeStr).isAfter(TimeUtils.parseTime(startTimeStr))
            val endEpoch = TimeUtils.toEpochMs(dateStr, endTimeStr, nextDay = crossedMidnight)

            val record = OvertimeRecord(
                date = dateStr,
                startTimeEpochMs = startEpoch,
                endTimeEpochMs = endEpoch,
                startTimeStr = startTimeStr,
                endTimeStr = endTimeStr,
                durationMinutes = netDurationMinutes,
                hourlyRate = hourlyRate,
                totalAmount = totalAmount,
                overtimeType = overtimeType.name,
                isAutomatic = false,
                note = note,
                breakMinutes = safeBreak
            )
            repository.insertManualRecord(record)
        }
    }

    fun updateRecord(
        id: Long,
        dateStr: String,
        startTimeStr: String,
        endTimeStr: String,
        hourlyRate: Double,
        overtimeType: OvertimeType,
        note: String,
        breakMinutes: Int = 0
    ) {
        viewModelScope.launch {
            val safeBreak = breakMinutes.coerceAtLeast(0)
            val netDurationMinutes = TimeUtils.calculateManualMinutesWithBreak(startTimeStr, endTimeStr, safeBreak)
            val totalAmount = TimeUtils.calculateEarnedAmount(netDurationMinutes, hourlyRate)
            val startEpoch = TimeUtils.toEpochMs(dateStr, startTimeStr)
            val crossedMidnight = !TimeUtils.parseTime(endTimeStr).isAfter(TimeUtils.parseTime(startTimeStr))
            val endEpoch = TimeUtils.toEpochMs(dateStr, endTimeStr, nextDay = crossedMidnight)

            val record = OvertimeRecord(
                id = id,
                date = dateStr,
                startTimeEpochMs = startEpoch,
                endTimeEpochMs = endEpoch,
                startTimeStr = startTimeStr,
                endTimeStr = endTimeStr,
                durationMinutes = netDurationMinutes,
                hourlyRate = hourlyRate,
                totalAmount = totalAmount,
                overtimeType = overtimeType.name,
                isAutomatic = false,
                note = note,
                breakMinutes = safeBreak
            )
            repository.updateRecord(record)
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteRecord(id)
        }
    }

    // --- Gelir & Gider Bütçe İşlemleri ---
    fun addBudgetTransaction(
        title: String,
        amount: Double,
        type: BudgetTransactionType,
        category: BudgetCategory,
        dateStr: String,
        note: String
    ) {
        viewModelScope.launch {
            val ym = if (dateStr.length >= 7) dateStr.take(7) else _selectedYearMonth.value.toString()
            val tx = BudgetTransaction(
                title = title.trim().ifEmpty { category.labelTr },
                amount = amount,
                type = type.name,
                category = category.name,
                dateStr = dateStr,
                yearMonth = ym,
                note = note.trim()
            )
            repository.insertBudgetTransaction(tx)
        }
    }

    fun updateBudgetTransaction(tx: BudgetTransaction) {
        viewModelScope.launch {
            repository.updateBudgetTransaction(tx)
        }
    }

    fun deleteBudgetTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteBudgetTransaction(id)
        }
    }

    // --- Çoklu Ücret Tarifesi İşlemleri ---
    fun saveTariffRule(
        rule: RateTariffRule,
        allowOverlapping: Boolean = false,
        onResult: (Result<Long>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.insertOrUpdateTariffRule(rule, allowOverlapping)
            onResult(res)
        }
    }

    fun deleteTariffRule(id: Long) {
        viewModelScope.launch {
            repository.deleteTariffRule(id)
        }
    }

    // --- Kişisel Maaş Programlama Raporu Arşivi ---
    fun generateAndSaveMonthlyPayrollReport(
        yearMonth: YearMonth = _selectedYearMonth.value,
        onComplete: (PayrollArchiveReport) -> Unit = {}
    ) {
        viewModelScope.launch {
            val report = repository.createAndSaveMonthlyPayrollArchive(yearMonth)
            onComplete(report)
        }
    }

    fun shareArchivedReportPdf(context: Context, report: PayrollArchiveReport) {
        viewModelScope.launch {
            try {
                val file = if (report.pdfFilePath.isNotBlank() && File(report.pdfFilePath).exists()) {
                    File(report.pdfFilePath)
                } else {
                    PayrollArchiveExporter.exportSnapshotToPdf(context, report)
                }
                ExportHelper.shareFile(
                    context = context,
                    file = file,
                    mimeType = "application/pdf",
                    title = report.reportTitle
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun shareArchivedReportCsv(context: Context, report: PayrollArchiveReport) {
        viewModelScope.launch {
            try {
                val file = if (report.csvFilePath.isNotBlank() && File(report.csvFilePath).exists()) {
                    File(report.csvFilePath)
                } else {
                    PayrollArchiveExporter.exportSnapshotToCsv(context, report)
                }
                ExportHelper.shareFile(
                    context = context,
                    file = file,
                    mimeType = "text/csv",
                    title = "${report.reportTitle} (CSV)"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteArchivedReport(id: Long) {
        viewModelScope.launch {
            repository.deletePayrollArchiveReport(id)
        }
    }

    // --- Bildirim Merkezi İşlemleri ---
    fun markNotificationRead(id: Long, isRead: Boolean = true) {
        viewModelScope.launch {
            repository.markNotificationRead(id, isRead)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead()
        }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteNotification(id)
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    // --- Akıllı Eksik Kayıt Kontrolü Uyarı Yönetimi ---
    fun dismissAuditWarning(warningKey: String) {
        val updated = _dismissedAuditKeys.value + warningKey
        _dismissedAuditKeys.value = updated
        auditPrefs.edit().putStringSet("dismissed_keys", updated).apply()
    }

    fun resetDismissedAuditWarnings() {
        _dismissedAuditKeys.value = emptySet()
        auditPrefs.edit().remove("dismissed_keys").apply()
    }

    fun changeMonth(offset: Long) {
        _selectedYearMonth.value = _selectedYearMonth.value.plusMonths(offset)
    }

    fun setMonth(yearMonth: YearMonth) {
        _selectedYearMonth.value = yearMonth
    }

    fun exportMonthlyPdf(context: Context, yearMonth: YearMonth, records: List<OvertimeRecord>) {
        viewModelScope.launch {
            try {
                val currentSettings = repository.getSettings()
                val pdfFile = PdfReportGenerator.generateMonthlyPdf(
                    context = context,
                    records = records,
                    yearMonth = yearMonth,
                    settings = currentSettings
                )
                ExportHelper.shareFile(
                    context = context,
                    file = pdfFile,
                    mimeType = "application/pdf",
                    title = "Mesai Çizelgesi - ${yearMonth.year}/${yearMonth.monthValue}"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportMonthlyCsv(context: Context, yearMonth: YearMonth, records: List<OvertimeRecord>) {
        viewModelScope.launch {
            try {
                val csvFile = CsvExporter.generateCsv(
                    context = context,
                    records = records,
                    yearMonth = yearMonth
                )
                ExportHelper.shareFile(
                    context = context,
                    file = csvFile,
                    mimeType = "text/csv",
                    title = "Mesai Dökümü (CSV) - ${yearMonth.year}/${yearMonth.monthValue}"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportAllCsv(context: Context) {
        viewModelScope.launch {
            try {
                val allList = repository.getAllRecordsList()
                val csvFile = CsvExporter.generateCsv(
                    context = context,
                    records = allList,
                    yearMonth = null
                )
                ExportHelper.shareFile(
                    context = context,
                    file = csvFile,
                    mimeType = "text/csv",
                    title = "Tüm Mesai Geçmişi (CSV)"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createBackup(context: Context) {
        viewModelScope.launch {
            try {
                val currentSettings = repository.getSettings()
                val allList = repository.getAllRecordsList()
                val backupFile = BackupManager.createBackupFile(
                    context = context,
                    settings = currentSettings,
                    records = allList
                )
                ExportHelper.shareFile(
                    context = context,
                    file = backupFile,
                    mimeType = "application/json",
                    title = "Mesai Takip Yedek Dosyası"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun restoreBackup(
        context: Context,
        uri: Uri,
        clearExisting: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val parseResult = BackupManager.parseBackupFromUri(context, uri)
            parseResult.fold(
                onSuccess = { data ->
                    try {
                        val count = repository.restoreData(
                            records = data.records,
                            restoredSettings = data.settings,
                            clearExisting = clearExisting
                        )
                        onResult(true, "$count adet mesai kaydı ve ayarlar başarıyla geri yüklendi.")
                    } catch (e: Exception) {
                        onResult(false, "Veritabanına aktarılırken hata oluştu: ${e.message}")
                    }
                },
                onFailure = { error ->
                    onResult(false, error.message ?: "Yedek dosyası okunamadı.")
                }
            )
        }
    }

    fun updateMonthlyGoals(targetDays: Int, targetHours: Double) {
        viewModelScope.launch {
            val current = repository.getSettings()
            val updated = current.copy(
                monthlyTargetDays = targetDays.coerceIn(1, 31),
                monthlyTargetHours = targetHours.coerceIn(1.0, 300.0)
            )
            repository.updateSettings(updated)
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = application as MesaiTakipApp
                    return MainViewModel(
                        application = app,
                        repository = app.repository,
                        documentArchiveRepository = app.documentArchiveRepository
                    ) as T
                }
            }
    }
}
