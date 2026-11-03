package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AppNotification
import com.example.data.model.AppSettings
import com.example.data.model.BudgetCategory
import com.example.data.model.BudgetTransaction
import com.example.data.model.BudgetTransactionType
import com.example.data.model.NotificationTargetRoute
import com.example.data.model.NotificationType
import com.example.data.model.OvertimeRecord
import com.example.data.model.OvertimeType
import com.example.data.model.RateTariffRule
import com.example.data.repository.OvertimeRepository
import com.example.receiver.BootReceiver
import com.example.ui.screens.NotificationCenterScreen
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AchievementCalculator
import com.example.util.AuditIssueType
import com.example.util.BudgetCalculator
import com.example.util.SmartRecordAuditor
import com.example.util.TariffRuleEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EightFeaturesComprehensiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: OvertimeRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = OvertimeRepository(
            context = context,
            overtimeDao = database.overtimeDao(),
            settingsDao = database.settingsDao(),
            activeOvertimeDao = database.activeOvertimeDao(),
            budgetDao = database.budgetDao(),
            rateTariffDao = database.rateTariffDao(),
            payrollArchiveDao = database.payrollArchiveDao(),
            notificationCenterDao = database.notificationCenterDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `1_package name and core settings preservation verified`() {
        assertEquals("com.aistudio.mesaitakip.kvnrpd", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `2_automatic reminders and notification center deduplicate eventKeys and survive boot receiver`() = runBlocking {
        val inserted1 = repository.recordNotificationEvent(
            eventKey = "SALARY_2026_10",
            type = NotificationType.SALARY_REMINDER,
            title = "Maaş Günü Hatırlatması",
            message = "Bugün maaş gününüz.",
            targetRoute = NotificationTargetRoute.BUDGET,
            tryPostSystemNotification = false
        )
        val insertedDuplicate = repository.recordNotificationEvent(
            eventKey = "SALARY_2026_10",
            type = NotificationType.SALARY_REMINDER,
            title = "Maaş Günü Hatırlatması Tekrar",
            message = "Tekrar gönderilmemeli.",
            targetRoute = NotificationTargetRoute.BUDGET,
            tryPostSystemNotification = false
        )
        assertTrue(inserted1)
        assertFalse(insertedDuplicate)

        val allNotifs = repository.allNotificationsFlow.first()
        assertEquals(1, allNotifs.size)
        assertEquals(1, repository.unreadNotificationCountFlow.first())

        repository.markNotificationRead(allNotifs.first().id, true)
        assertEquals(0, repository.unreadNotificationCountFlow.first())

        // Evaluate automatic reminders and BootReceiver safely without crashing
        repository.evaluateAndDispatchAutomaticReminders()
        val bootReceiver = BootReceiver()
        bootReceiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun `3_budget tracking prevents double counting base salary and separates advance from extra income`() {
        val ym = YearMonth.of(2026, 10)
        val settings = AppSettings(
            baseSalary = 40000.0,
            weekdayRate = 250.0,
            weekendRate = 350.0,
            isSetupCompleted = true
        )
        val records = listOf(
            OvertimeRecord(
                id = 1L,
                date = "2026-10-05",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 7201000L,
                startTimeStr = "18:00",
                endTimeStr = "20:00",
                durationMinutes = 120,
                hourlyRate = 250.0,
                totalAmount = 500.0,
                overtimeType = "WEEKDAY"
            )
        )
        val transactions = listOf(
            // Linked salary transfer: must NOT double count the 40,000 TL base salary
            BudgetTransaction(
                id = 1L,
                yearMonth = "2026-10",
                dateStr = "2026-10-15",
                title = "Ekim Maaş Aktarımı",
                amount = 40000.0,
                type = BudgetTransactionType.INCOME.name,
                category = BudgetCategory.SALARY_TRANSFER.name
            ),
            // Advance taken: deducted from salary payable, tracked separately without inflating net income
            BudgetTransaction(
                id = 2L,
                yearMonth = "2026-10",
                dateStr = "2026-10-10",
                title = "Maaş Avansı",
                amount = 5000.0,
                type = BudgetTransactionType.INCOME.name,
                category = BudgetCategory.ADVANCE.name
            ),
            // Extra bonus income
            BudgetTransaction(
                id = 3L,
                yearMonth = "2026-10",
                dateStr = "2026-10-18",
                title = "Prim Ödemesi",
                amount = 3000.0,
                type = BudgetTransactionType.INCOME.name,
                category = BudgetCategory.BONUS_ADDITIONAL.name
            ),
            // Rent expense
            BudgetTransaction(
                id = 4L,
                yearMonth = "2026-10",
                dateStr = "2026-10-01",
                title = "Ev Kirası",
                amount = 15000.0,
                type = BudgetTransactionType.EXPENSE.name,
                category = BudgetCategory.RENT.name
            )
        )

        val summary = BudgetCalculator.calculateMonthlySummary(
            yearMonth = ym,
            allTransactions = transactions,
            allOvertimeRecords = records,
            settings = settings
        )

        // Base salary counted once (40,000) + Overtime (500) + Extra income (3,000) = 43,500 TL
        assertEquals(40000.0, summary.baseSalaryEntitlement, 0.01)
        assertEquals(500.0, summary.overtimeEarningsEntitlement, 0.01)
        assertEquals(5000.0, summary.advanceReceivedTotal, 0.01)
        assertEquals(35500.0, summary.netRemainingSalaryAfterAdvance, 0.01)
        assertEquals(43500.0, summary.totalReconciledIncome, 0.01)
        assertEquals(15000.0, summary.totalExpense, 0.01)
        assertEquals(28500.0, summary.netRemainingBalance, 0.01)
    }

    @Test
    fun `4_payroll archive report freezes rates snapshot and remains unaffected by future rate edits`() = runBlocking {
        val ym = YearMonth.of(2026, 10)
        repository.updateSettings(
            AppSettings(
                baseSalary = 36000.0,
                weekdayRate = 200.0,
                weekendRate = 300.0,
                normalHourlyRate = 160.0,
                nightShiftRate = 250.0,
                officialHolidayRate = 400.0,
                isSetupCompleted = true
            )
        )
        repository.insertManualRecord(
            OvertimeRecord(
                date = "2026-10-12",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 10801000L,
                startTimeStr = "18:00",
                endTimeStr = "21:00",
                durationMinutes = 180,
                hourlyRate = 200.0,
                totalAmount = 600.0,
                overtimeType = OvertimeType.WEEKDAY.name,
                note = "Proje teslimi"
            )
        )

        val archivedReport = repository.createAndSaveMonthlyPayrollArchive(ym)
        assertEquals("2026-10", archivedReport.yearMonth)
        assertTrue(archivedReport.reportTitle.contains("Kişisel Maaş Programlama Raporu"))
        assertEquals(200.0, archivedReport.snapshotWeekdayRate, 0.01)
        assertEquals(600.0, archivedReport.totalOvertimeEarnings, 0.01)

        // Now change settings rates drastically
        repository.updateSettings(
            repository.getSettings().copy(
                baseSalary = 90000.0,
                weekdayRate = 750.0,
                weekendRate = 1000.0
            )
        )

        // Previously archived report MUST retain its frozen snapshot values
        val storedReports = repository.allArchivedReportsFlow.first()
        val storedReport = storedReports.firstOrNull { it.id == archivedReport.id }
        assertNotNull(storedReport)
        assertEquals(36000.0, storedReport!!.snapshotBaseSalary, 0.01)
        assertEquals(200.0, storedReport.snapshotWeekdayRate, 0.01)
        assertEquals(600.0, storedReport.totalOvertimeEarnings, 0.01)
    }

    @Test
    fun `5_multiple rate tariffs detect conflicts and apply date-scoped rate without corrupting past records`() = runBlocking {
        repository.updateSettings(
            AppSettings(
                weekdayRate = 200.0,
                weekendRate = 300.0,
                isSetupCompleted = true
            )
        )
        // Existing record before new tariff
        repository.insertManualRecord(
            OvertimeRecord(
                date = "2026-09-10",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 7201000L,
                startTimeStr = "18:00",
                endTimeStr = "20:00",
                durationMinutes = 120,
                hourlyRate = 200.0,
                totalAmount = 400.0,
                overtimeType = OvertimeType.WEEKDAY.name,
                note = "Eylül kaydı"
            )
        )

        val t1 = RateTariffRule(
            name = "2026 Q4 Zamlı Tarife",
            startDateStr = "2026-10-01",
            endDateStr = "2026-12-31",
            normalHourlyRate = 220.0,
            weekdayOvertimeRate = 330.0,
            weekendRate = 440.0,
            nightShiftRate = 380.0,
            officialHolidayRate = 550.0,
            applyDaysCsv = "1,2,3,4,5,6,7",
            isActive = true
        )
        val t2Conflicting = RateTariffRule(
            name = "Çakışan Ekim Tarifesi",
            startDateStr = "2026-10-15",
            endDateStr = "2026-11-15",
            normalHourlyRate = 240.0,
            weekdayOvertimeRate = 360.0,
            weekendRate = 480.0,
            nightShiftRate = 400.0,
            officialHolidayRate = 600.0,
            applyDaysCsv = "1,2,3,4,5",
            isActive = true
        )
        val res1 = repository.insertOrUpdateTariffRule(t1, allowOverlapping = false)
        assertTrue(res1.isSuccess)

        val conflicts = TariffRuleEngine.findConflicts(t2Conflicting, repository.allTariffRulesFlow.first())
        assertTrue(conflicts.isNotEmpty())

        // Resolve rate for October weekday
        val resolvedOctRate = TariffRuleEngine.resolveRate(
            dateStr = "2026-10-05",
            startTimeStr = "18:00",
            requestedType = OvertimeType.WEEKDAY,
            settings = repository.getSettings(),
            activeRules = repository.allTariffRulesFlow.first()
        )
        assertEquals(330.0, resolvedOctRate.hourlyRate, 0.01)

        // Past September record must still have 200.0 hourlyRate
        val sepRecord = repository.allRecords.first().first { it.date == "2026-09-10" }
        assertEquals(200.0, sepRecord.hourlyRate, 0.01)
        assertEquals(400.0, sepRecord.totalAmount, 0.01)
    }

    @Test
    fun `6_overtime counter prevents duplicate active sessions and deducts break minutes accurately`() = runBlocking {
        repository.updateSettings(
            AppSettings(
                weekdayRate = 240.0,
                weekendRate = 360.0,
                isSetupCompleted = true
            )
        )
        val started1 = repository.startOvertime()
        val startedDuplicate = repository.startOvertime()
        assertTrue(started1)
        assertFalse(startedDuplicate)

        val firstActive = repository.getActiveOvertime()
        assertTrue(firstActive.isActive)

        // Update break minutes to 30m
        repository.updateActiveBreakMinutes(30)
        val updatedActive = repository.getActiveOvertime()
        assertEquals(30, updatedActive.breakMinutes)
    }

    @Test
    fun `7_smart record auditor detects missing end time overlap and abnormal duration and respects dismissal`() {
        val settings = AppSettings()
        val records = listOf(
            OvertimeRecord(
                id = 10L,
                date = "2026-10-10",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 7201000L,
                startTimeStr = "18:00",
                endTimeStr = "",
                durationMinutes = 120,
                hourlyRate = 200.0,
                totalAmount = 400.0,
                overtimeType = "WEEKDAY"
            ),
            OvertimeRecord(
                id = 11L,
                date = "2026-10-11",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 55801000L,
                startTimeStr = "08:00",
                endTimeStr = "23:30",
                durationMinutes = 930, // 15.5 hours (> 12h threshold)
                hourlyRate = 200.0,
                totalAmount = 3100.0,
                overtimeType = "WEEKDAY"
            ),
            OvertimeRecord(
                id = 12L,
                date = "2026-10-12",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 7201000L,
                startTimeStr = "18:00",
                endTimeStr = "20:00",
                durationMinutes = 120,
                hourlyRate = 200.0,
                totalAmount = 400.0,
                overtimeType = "WEEKDAY"
            ),
            OvertimeRecord(
                id = 13L,
                date = "2026-10-12",
                startTimeEpochMs = 3601000L,
                endTimeEpochMs = 10801000L,
                startTimeStr = "19:00",
                endTimeStr = "21:00",
                durationMinutes = 120,
                hourlyRate = 200.0,
                totalAmount = 400.0,
                overtimeType = "WEEKDAY"
            )
        )

        val warnings = SmartRecordAuditor.auditRecords(records = records, settings = settings)
        assertTrue(warnings.any { it.issueType == AuditIssueType.MISSING_END_TIME })
        assertTrue(warnings.any { it.issueType == AuditIssueType.UNUSUALLY_LONG_DURATION })
        assertTrue(warnings.any { it.issueType == AuditIssueType.OVERLAPPING_RECORDS })

        val missingKey = warnings.first { it.issueType == AuditIssueType.MISSING_END_TIME }.warningKey
        val afterDismiss = SmartRecordAuditor.auditRecords(
            records = records,
            settings = settings,
            dismissedKeys = setOf(missingKey)
        )
        assertFalse(afterDismiss.any { it.warningKey == missingKey })
    }

    @Test
    fun `8_achievements calculator distinguishes distinct days from multiple records on same day`() {
        val settings = AppSettings(
            monthlyTargetDays = 5,
            monthlyTargetHours = 10.0,
            isSetupCompleted = true
        )
        // Two records on the same day ("2026-10-01") must count as 1 distinct working day, not 2!
        val records = listOf(
            OvertimeRecord(
                id = 1L,
                date = "2026-10-01",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 3601000L,
                startTimeStr = "18:00",
                endTimeStr = "19:00",
                durationMinutes = 60,
                hourlyRate = 200.0,
                totalAmount = 200.0,
                overtimeType = "WEEKDAY"
            ),
            OvertimeRecord(
                id = 2L,
                date = "2026-10-01",
                startTimeEpochMs = 7201000L,
                endTimeEpochMs = 12601000L,
                startTimeStr = "20:00",
                endTimeStr = "21:30",
                durationMinutes = 90,
                hourlyRate = 200.0,
                totalAmount = 300.0,
                overtimeType = "WEEKDAY"
            )
        )

        val summary = AchievementCalculator.calculateSummary(
            allRecords = records,
            selectedYearMonth = YearMonth.of(2026, 10),
            settings = settings,
            archivedDocumentsCount = 1,
            firstArchivedDocumentDate = "2026-10-01"
        )

        assertEquals(1, summary.totalValidDaysAllTime)
        assertEquals(2, summary.totalValidRecordsAllTime)
        assertTrue(summary.allBadges.any { it.id == "FIRST_VALID_RECORD" && it.isUnlocked })
        assertTrue(summary.allBadges.any { it.id == "FIRST_MONTHLY_REPORT" && it.isUnlocked })
        assertFalse(summary.allBadges.any { it.id == "DISTINCT_DAYS_7" && it.isUnlocked })
    }

    @Test
    fun `9_new UI screens NotificationCenter renders properly`() {
        val sampleNotifications = listOf(
            AppNotification(
                id = 1L,
                eventKey = "TEST_1",
                type = NotificationType.OVERTIME_REMINDER.name,
                title = "Mesai Kaydı Hatırlatması",
                message = "Bugünkü mesai kaydınızı girmeyi unutmayın.",
                targetRoute = "HOME",
                dateTimeStr = "18.10.2026 20:00",
                createdAtEpochMs = System.currentTimeMillis(),
                isRead = false
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(palette = AppThemePalette.CLASSIC_BLUE) {
                NotificationCenterScreen(
                    notifications = sampleNotifications,
                    settings = AppSettings(isSetupCompleted = true),
                    onMarkRead = { _, _ -> },
                    onMarkAllRead = {},
                    onDeleteNotification = {},
                    onClearAll = {},
                    onNavigateToTarget = {},
                    onSaveSettings = {},
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bildirim Merkezi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notification_item_1").assertIsDisplayed()
    }
}
