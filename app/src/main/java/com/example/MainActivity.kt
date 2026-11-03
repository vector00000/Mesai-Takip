package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.NavTab
import com.example.ui.components.BottomNavBar
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as? MesaiTakipApp
            val isPremium by app?.billingManager?.isPremium?.collectAsStateWithLifecycle()
                ?: androidx.compose.runtime.remember { mutableStateOf(false) }
            val selectedPalette by com.example.ui.theme.ThemePreferences.selectedPalette.collectAsStateWithLifecycle()
            val effectivePalette = if (selectedPalette.requiresPremium && !isPremium) {
                com.example.ui.theme.AppThemePalette.CLASSIC_BLUE
            } else {
                selectedPalette
            }
            MyApplicationTheme(palette = effectivePalette) {
                MainAppRoot(
                    isPremium = isPremium
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as? MesaiTakipApp
        if (app != null) {
            app.billingManager.queryActivePurchases()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.repository.checkAndTriggerAutomaticOvertime()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

@Composable
fun MainAppRoot(
    isPremium: Boolean = false,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.provideFactory(
        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    ))
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Request Notification Permission on Android 13+ (API 33+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val app = context.applicationContext as? MesaiTakipApp
            app?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val active = it.repository.getActiveOvertime()
                        if (active.isActive) {
                            NotificationHelper.showOvertimeStartedNotification(
                                context = context,
                                startTimeStr = active.startTimeStr,
                                isAutomatic = active.isAutomatic
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkAutoOvertime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeOvertime by viewModel.activeOvertime.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val userDocuments by viewModel.userDocuments.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedYearMonth by viewModel.selectedYearMonth.collectAsStateWithLifecycle()
    val monthlyRecords by viewModel.monthlyRecords.collectAsStateWithLifecycle()
    val liveElapsedSeconds by viewModel.liveElapsedSeconds.collectAsStateWithLifecycle()
    val liveEarnings by viewModel.liveEarnings.collectAsStateWithLifecycle()
    val retroactivePromptTime by viewModel.retroactivePromptTime.collectAsStateWithLifecycle()
    val isDocumentArchiveOpen by viewModel.isDocumentArchiveOpen.collectAsStateWithLifecycle()
    val isAchievementsOpen by viewModel.isAchievementsOpen.collectAsStateWithLifecycle()
    val isNotificationCenterOpen by viewModel.isNotificationCenterOpen.collectAsStateWithLifecycle()
    val isBudgetScreenOpen by viewModel.isBudgetScreenOpen.collectAsStateWithLifecycle()
    val isTariffScreenOpen by viewModel.isTariffScreenOpen.collectAsStateWithLifecycle()
    val isSmartAuditScreenOpen by viewModel.isSmartAuditScreenOpen.collectAsStateWithLifecycle()

    val appNotifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val budgetTransactions by viewModel.budgetTransactions.collectAsStateWithLifecycle()
    val tariffRules by viewModel.tariffRules.collectAsStateWithLifecycle()
    val archivedPayrollReports by viewModel.archivedPayrollReports.collectAsStateWithLifecycle()
    val dismissedAuditKeys by viewModel.dismissedAuditKeys.collectAsStateWithLifecycle()

    val auditWarningCount = androidx.compose.runtime.remember(allRecords, activeOvertime, settings, dismissedAuditKeys) {
        com.example.util.SmartRecordAuditor.auditRecords(
            records = allRecords,
            activeOvertime = activeOvertime,
            settings = settings,
            dismissedKeys = dismissedAuditKeys
        ).size
    }

    if (isNotificationCenterOpen) {
        com.example.ui.screens.NotificationCenterScreen(
            notifications = appNotifications,
            settings = settings,
            onMarkRead = { id, read -> viewModel.markNotificationRead(id, read) },
            onMarkAllRead = { viewModel.markAllNotificationsRead() },
            onDeleteNotification = { id -> viewModel.deleteNotification(id) },
            onClearAll = { viewModel.clearAllNotifications() },
            onNavigateToTarget = { targetRoute -> viewModel.navigateFromNotification(targetRoute) },
            onSaveSettings = { updated -> viewModel.updateSettings(updated) },
            onNavigateBack = { viewModel.closeNotificationCenterScreen() }
        )
    } else if (isBudgetScreenOpen) {
        com.example.ui.screens.BudgetTrackingScreen(
            selectedYearMonth = selectedYearMonth,
            transactions = budgetTransactions,
            overtimeRecords = allRecords,
            settings = settings,
            onMonthChange = { offset -> viewModel.changeMonth(offset) },
            onAddTransaction = { title, amount, type, category, dateStr, note ->
                viewModel.addBudgetTransaction(title, amount, type, category, dateStr, note)
            },
            onUpdateTransaction = { tx -> viewModel.updateBudgetTransaction(tx) },
            onDeleteTransaction = { id -> viewModel.deleteBudgetTransaction(id) },
            onNavigateBack = { viewModel.closeBudgetScreen() }
        )
    } else if (isTariffScreenOpen || isSmartAuditScreenOpen) {
        com.example.ui.screens.TariffAndAuditScreen(
            initialTabIndex = if (isSmartAuditScreenOpen) 1 else 0,
            settings = settings,
            tariffRules = tariffRules,
            allRecords = allRecords,
            activeOvertime = activeOvertime,
            dismissedAuditKeys = dismissedAuditKeys,
            onSaveSettings = { updated -> viewModel.updateSettings(updated) },
            onSaveTariffRule = { rule, cb -> viewModel.saveTariffRule(rule, false, cb) },
            onDeleteTariffRule = { id -> viewModel.deleteTariffRule(id) },
            onDismissAuditWarning = { key -> viewModel.dismissAuditWarning(key) },
            onResetDismissedAuditWarnings = { viewModel.resetDismissedAuditWarnings() },
            onNavigateToHistoryForEdit = {
                viewModel.closeTariffScreen()
                viewModel.closeSmartAuditScreen()
                viewModel.selectTab(NavTab.HISTORY)
            },
            onNavigateBack = {
                viewModel.closeTariffScreen()
                viewModel.closeSmartAuditScreen()
            }
        )
    } else if (isDocumentArchiveOpen) {
        com.example.ui.screens.DocumentArchiveScreen(
            isPremium = isPremium,
            selectedYearMonth = selectedYearMonth,
            documents = userDocuments,
            repository = viewModel.documentArchiveRepository,
            onMonthChange = { offset -> viewModel.changeMonth(offset) },
            onNavigateBack = { viewModel.closeDocumentArchiveScreen() },
            archivedReports = archivedPayrollReports,
            onGenerateArchiveReport = { viewModel.generateAndSaveMonthlyPayrollReport(selectedYearMonth) },
            onExportArchivedPdf = { report -> viewModel.shareArchivedReportPdf(context, report) },
            onExportArchivedCsv = { report -> viewModel.shareArchivedReportCsv(context, report) },
            onDeleteArchivedReport = { id -> viewModel.deleteArchivedReport(id) }
        )
    } else if (isAchievementsOpen) {
        com.example.ui.screens.AchievementsStatsScreen(
            isPremium = isPremium,
            selectedYearMonth = selectedYearMonth,
            allRecords = allRecords,
            settings = settings,
            userDocuments = userDocuments,
            onMonthChange = { offset -> viewModel.changeMonth(offset) },
            onSelectMonth = { ym -> viewModel.setMonth(ym) },
            onUpdateMonthlyGoals = { days, hours -> viewModel.updateMonthlyGoals(days, hours) },
            onNavigateBack = { viewModel.closeAchievementsScreen() },
            archivedReportsCount = archivedPayrollReports.size,
            firstArchivedReportDate = archivedPayrollReports.minByOrNull { it.createdAtEpochMs }?.createdDateStr
        )
    } else if (!settings.isSetupCompleted) {
        // If first launch and setup not completed yet, show SetupScreen
        SetupScreen(
            onComplete = { wdRate, weRate, wdTime, satEnabled, satTime, sunEnabled, sunTime ->
                viewModel.completeInitialSetup(
                    weekdayRate = wdRate,
                    weekendRate = weRate,
                    weekdayStartTime = wdTime,
                    saturdayEnabled = satEnabled,
                    saturdayStartTime = satTime,
                    sundayEnabled = sunEnabled,
                    sundayStartTime = sunTime
                )
            }
        )
    } else {
        // Back button navigation handling
        BackHandler(enabled = currentTab != NavTab.HOME) {
            viewModel.selectTab(NavTab.HOME)
        }

        Scaffold(
            bottomBar = {
                BottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            },
            contentWindowInsets = WindowInsets.systemBars,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding()
                    )
            ) {
                Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                    when (tab) {
                        NavTab.HOME -> {
                            HomeScreen(
                                settings = settings,
                                activeOvertime = activeOvertime,
                                allRecords = allRecords,
                                liveElapsedSeconds = liveElapsedSeconds,
                                liveEarnings = liveEarnings,
                                retroactivePromptTime = retroactivePromptTime,
                                onStartOvertime = { viewModel.startOvertime() },
                                onStopOvertime = { viewModel.stopOvertime() },
                                onStartRetroactive = { time -> viewModel.startRetroactive(time) },
                                onDismissRetroactive = { viewModel.dismissRetroactivePrompt() },
                                onToggleSkipToday = { viewModel.toggleSkipToday() },
                                onToggleAutoGlobal = { viewModel.toggleAutoGlobal(it) },
                                onSaveSettings = { updated -> viewModel.updateSettings(updated) },
                                onAddRecord = { date, start, end, rate, type, note ->
                                    viewModel.addManualRecord(date, start, end, rate, type, note)
                                },
                                onAddRecordWithBreak = { date, start, end, rate, type, note, breakMins ->
                                    viewModel.addManualRecord(date, start, end, rate, type, note, breakMins)
                                },
                                onUpdateActiveBreakMinutes = { mins -> viewModel.updateActiveBreakMinutes(mins) },
                                unreadNotificationCount = unreadNotificationCount,
                                auditWarningCount = auditWarningCount,
                                onOpenNotificationCenter = { viewModel.openNotificationCenterScreen() },
                                onOpenBudgetScreen = { viewModel.openBudgetScreen() },
                                onOpenPayrollArchiveScreen = { viewModel.openDocumentArchiveScreen() },
                                onOpenAchievementsScreen = { viewModel.openAchievementsScreen() },
                                onOpenTariffScreen = { viewModel.openTariffScreen() },
                                onOpenSmartAuditScreen = { viewModel.openSmartAuditScreen() }
                            )
                        }

                        NavTab.HISTORY -> {
                            HistoryScreen(
                                records = allRecords,
                                settings = settings,
                                isPremium = isPremium,
                                onAddRecord = { date, start, end, rate, type, note ->
                                    viewModel.addManualRecord(date, start, end, rate, type, note)
                                },
                                onUpdateRecord = { id, date, start, end, rate, type, note ->
                                    viewModel.updateRecord(id, date, start, end, rate, type, note)
                                },
                                onDeleteRecord = { id -> viewModel.deleteRecord(id) },
                                onExportAllCsv = { viewModel.exportAllCsv(context) }
                            )
                        }

                        NavTab.REPORTS -> {
                            ReportsScreen(
                                selectedYearMonth = selectedYearMonth,
                                monthlyRecords = monthlyRecords,
                                allRecords = allRecords,
                                settings = settings,
                                isPremium = isPremium,
                                onMonthChange = { offset -> viewModel.changeMonth(offset) },
                                onSelectMonth = { ym -> viewModel.setMonth(ym) },
                                onExportPdf = { viewModel.exportMonthlyPdf(context, selectedYearMonth, monthlyRecords) },
                                onExportCsv = { viewModel.exportMonthlyCsv(context, selectedYearMonth, monthlyRecords) },
                                onOpenDocumentArchive = { viewModel.openDocumentArchiveScreen() },
                                onOpenAchievements = { viewModel.openAchievementsScreen() },
                                onOpenBudgetTracking = { viewModel.openBudgetScreen() },
                                onOpenTariffAndAudit = { viewModel.openTariffScreen() },
                                onOpenNotificationCenter = { viewModel.openNotificationCenterScreen() }
                            )
                        }

                        NavTab.SETTINGS -> {
                            SettingsScreen(
                                settings = settings,
                                isPremium = isPremium,
                                onSaveSettings = { updated -> viewModel.updateSettings(updated) },
                                onBackupCreate = { viewModel.createBackup(context) },
                                onBackupRestore = { uri, clearExisting ->
                                    viewModel.restoreBackup(context, uri, clearExisting) { _, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                unreadNotificationCount = unreadNotificationCount,
                                onOpenNotificationCenter = { viewModel.openNotificationCenterScreen() },
                                onOpenTariffAndAudit = { viewModel.openTariffScreen() },
                                onOpenBudgetTracking = { viewModel.openBudgetScreen() },
                                onOpenAchievements = { viewModel.openAchievementsScreen() },
                                onOpenDocumentArchive = { viewModel.openDocumentArchiveScreen() }
                            )
                        }
                    }
                }
            }
        }
    }
}

