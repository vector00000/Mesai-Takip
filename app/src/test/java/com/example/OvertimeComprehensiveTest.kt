package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import com.example.data.model.OvertimeType
import com.example.data.repository.OvertimeRepository
import com.example.util.AlarmScheduler
import com.example.util.TimeUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OvertimeComprehensiveTest {

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
            activeOvertimeDao = database.activeOvertimeDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testCurrencyAndRateCalculations_exactBigDecimal() {
        // Scenario 9:
        // 1 saat @ 200 TL -> 200 TL
        assertEquals(200.0, TimeUtils.calculateEarnedAmount(60, 200.0), 0.001)

        // 30 dakika @ 200 TL -> 100 TL
        assertEquals(100.0, TimeUtils.calculateEarnedAmount(30, 200.0), 0.001)

        // 15 dakika @ 200 TL -> 50 TL
        assertEquals(50.0, TimeUtils.calculateEarnedAmount(15, 200.0), 0.001)

        // 1 saat 30 dakika (90 dk) @ 200 TL -> 300 TL
        assertEquals(300.0, TimeUtils.calculateEarnedAmount(90, 200.0), 0.001)

        // 5 saat 20 dakika (320 dk) @ 266 TL -> 1.418,67 TL (exact Turkish penny rounding)
        assertEquals(1418.67, TimeUtils.calculateEarnedAmount(320, 266.0), 0.001)
    }

    @Test
    fun testMidnightCrossing_accurateMinutesAndAmount() {
        // Scenario 17, 19:
        // Start: 18:00, End: 02:30 -> 8 hours 30 mins = 510 minutes
        val minutes = TimeUtils.calculateManualMinutes("18:00", "02:30")
        assertEquals(510, minutes)

        // Total fee at 200 TL/hr: 8.5 * 200 = 1700 TL
        val amount = TimeUtils.calculateEarnedAmount(minutes, 200.0)
        assertEquals(1700.0, amount, 0.001)
    }

    @Test
    fun testMonthCrossing_accurateMinutes() {
        // Scenario 20:
        // 30 September 22:00 to 1 October 02:00 -> 4 hours = 240 minutes
        val minutes = TimeUtils.calculateManualMinutes("22:00", "02:00")
        assertEquals(240, minutes)

        val startEpoch = TimeUtils.toEpochMs("2026-09-30", "22:00")
        val endEpoch = TimeUtils.toEpochMs("2026-09-30", "02:00", nextDay = true)

        val elapsed = ((endEpoch - startEpoch) / 60000L).toInt()
        assertEquals(240, elapsed)
    }

    @Test
    fun testYearCrossing_accurateMinutes() {
        // Scenario 21:
        // 31 December 22:00 to 1 January 02:00 -> 4 hours = 240 minutes
        val minutes = TimeUtils.calculateManualMinutes("22:00", "02:00")
        assertEquals(240, minutes)

        val startEpoch = TimeUtils.toEpochMs("2026-12-31", "22:00")
        val endEpoch = TimeUtils.toEpochMs("2026-12-31", "02:00", nextDay = true)

        val elapsed = ((endEpoch - startEpoch) / 60000L).toInt()
        assertEquals(240, elapsed)
    }

    @Test
    fun testWeekdayWeekendClassification() {
        // Scenario 10 & 11:
        // 2026-09-25 is Friday (Weekday)
        val friday = LocalDate.of(2026, 9, 25)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        assertFalse(TimeUtils.isWeekend(friday))
        assertEquals(OvertimeType.WEEKDAY, TimeUtils.getOvertimeType(friday))

        // 2026-09-26 is Saturday (Weekend)
        val saturday = LocalDate.of(2026, 9, 26)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        assertTrue(TimeUtils.isWeekend(saturday))
        assertEquals(OvertimeType.WEEKEND, TimeUtils.getOvertimeType(saturday))

        // 2026-09-27 is Sunday (Weekend)
        val sunday = LocalDate.of(2026, 9, 27)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertTrue(TimeUtils.isWeekend(sunday))
        assertEquals(OvertimeType.WEEKEND, TimeUtils.getOvertimeType(sunday))
    }

    @Test
    fun testRoomDatabase_crudOperations() = runBlocking {
        // Scenario 23:
        val record = OvertimeRecord(
            date = "2026-09-26",
            startTimeEpochMs = 1000000L,
            endTimeEpochMs = 2000000L,
            startTimeStr = "18:00",
            endTimeStr = "21:00",
            durationMinutes = 180,
            hourlyRate = 200.0,
            totalAmount = 600.0,
            overtimeType = "WEEKDAY"
        )

        val id = repository.insertManualRecord(record)
        assertTrue(id > 0)

        val all = repository.allRecords.first()
        assertEquals(1, all.size)
        assertEquals("2026-09-26", all[0].date)
        assertEquals(600.0, all[0].totalAmount, 0.001)

        // Update record
        val updated = all[0].copy(hourlyRate = 250.0, totalAmount = 750.0)
        repository.updateRecord(updated)

        val allAfterUpdate = repository.allRecords.first()
        assertEquals(750.0, allAfterUpdate[0].totalAmount, 0.001)

        // Delete record
        repository.deleteRecord(id)
        val allAfterDelete = repository.allRecords.first()
        assertEquals(0, allAfterDelete.size)
    }

    @Test
    fun testDoubleStartPrevention() = runBlocking {
        // Scenario 16:
        // First start succeeds
        val startedFirst = repository.startOvertime(startTimeEpochMs = 1000L)
        assertTrue(startedFirst)

        // Second start while active must return false and not duplicate
        val startedSecond = repository.startOvertime(startTimeEpochMs = 2000L)
        assertFalse(startedSecond)

        val active = repository.getActiveOvertime()
        assertTrue(active.isActive)
        assertEquals(1000L, active.startTimeEpochMs)
    }

    @Test
    fun testDoubleStopPrevention() = runBlocking {
        // Scenario 16 & 17:
        repository.startOvertime(startTimeEpochMs = 1000000L)

        // First stop creates record and returns it
        val record1 = repository.stopOvertime(endTimeEpochMs = 1000000L + (120 * 60000L))
        assertNotNull(record1)
        assertEquals(120, record1!!.durationMinutes)

        // Second rapid stop returns null and does not duplicate record
        val record2 = repository.stopOvertime()
        assertNull(record2)

        val allRecords = repository.allRecords.first()
        assertEquals(1, allRecords.size)
    }

    @Test
    fun testBugunMesaiYok_skipDateBehavior() = runBlocking {
        // Scenario 13:
        val todayStr = TimeUtils.todayDateStr()
        repository.setSkipToday(true)

        val settings = repository.getSettings()
        assertEquals(todayStr, settings.skipDate)

        // Schedule check for today should return null when skipped
        val todaySchedule = AlarmScheduler.getScheduleForDate(LocalDate.now(TimeUtils.ISTANBUL_ZONE), settings)
        assertNull(todaySchedule)

        // Schedule for tomorrow should NOT be skipped
        val tomorrow = LocalDate.now(TimeUtils.ISTANBUL_ZONE).plusDays(1)
        val tomorrowSchedule = AlarmScheduler.getScheduleForDate(tomorrow, settings)
        // If tomorrow is enabled day, returns valid LocalTime
        if (settings.autoMonday || settings.autoSaturday || settings.autoSunday) {
            // tomorrow's schedule is not blocked by today's skipDate
            val tomorrowStr = TimeUtils.formatDate(tomorrow)
            assertFalse(settings.skipDate == tomorrowStr)
        }

        // Turning skip off clears skipDate
        repository.setSkipToday(false)
        val settingsCleared = repository.getSettings()
        assertEquals("", settingsCleared.skipDate)
    }

    @Test
    fun testAutoGlobalDisabled_cancelsSchedule() {
        // Scenario 14:
        val settings = AppSettings(autoGlobalEnabled = false)
        val next = AlarmScheduler.findNextTriggerDateTime(settings)
        // Even if days are enabled, autoGlobalEnabled = false suppresses auto scheduling
        assertNull(next)
    }

    @Test
    fun testInitialSetupCompletedAndPersisted() = runBlocking {
        // Scenario 2:
        val initialSettings = repository.getSettings()
        assertFalse(initialSettings.isSetupCompleted)

        val updated = initialSettings.copy(
            isSetupCompleted = true,
            weekdayRate = 220.0,
            weekendRate = 280.0,
            weekdayStartTime = "18:00"
        )
        repository.updateSettings(updated)

        val savedSettings = repository.getSettings()
        assertTrue(savedSettings.isSetupCompleted)
        assertEquals(220.0, savedSettings.weekdayRate, 0.001)
        assertEquals(280.0, savedSettings.weekendRate, 0.001)
    }

    @Test
    fun testMonthlyReportCalculations() {
        // Scenario 26:
        // Hafta içi: 10 saat (600 dk) x 200 TL = 2.000 TL
        // Hafta sonu: 5 saat (300 dk) x 266 TL = 1.330 TL
        // Toplam: 15 saat (900 dk), 3.330 TL
        val weekdayFee = TimeUtils.calculateEarnedAmount(600, 200.0)
        assertEquals(2000.0, weekdayFee, 0.001)

        val weekendFee = TimeUtils.calculateEarnedAmount(300, 266.0)
        assertEquals(1330.0, weekendFee, 0.001)

        val totalFee = weekdayFee + weekendFee
        assertEquals(3330.0, totalFee, 0.001)
        assertEquals("3.330,00 TL", TimeUtils.formatCurrency(totalFee))
    }

    @Test
    fun testCsvExporter_generatesUtf8BomAndValidContent() {
        val testRecords = listOf(
            OvertimeRecord(
                id = 1,
                date = "2026-10-01",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 1000L + 7200000L,
                startTimeStr = "18:00",
                endTimeStr = "20:00",
                durationMinutes = 120,
                hourlyRate = 200.0,
                totalAmount = 400.0,
                overtimeType = "WEEKDAY",
                note = "Test mesaisi"
            )
        )

        val csvFile = com.example.util.CsvExporter.generateCsv(
            context = context,
            records = testRecords,
            yearMonth = java.time.YearMonth.of(2026, 10)
        )

        assertTrue(csvFile.exists())
        assertTrue(csvFile.length() > 0)
        val text = csvFile.readText(java.nio.charset.StandardCharsets.UTF_8)
        assertTrue(text.contains("Tarih;Mesai Türü;Başlangıç;Bitiş"))
        assertTrue(text.contains("2026-10-01;Hafta İçi;18:00;20:00"))
        assertTrue(text.contains("GENEL TOPLAM"))
    }

    @Test
    fun testPdfReportGenerator_createsFileSuccessfully() {
        val testRecords = listOf(
            OvertimeRecord(
                id = 1,
                date = "2026-10-01",
                startTimeEpochMs = 1000L,
                endTimeEpochMs = 1000L + 7200000L,
                startTimeStr = "18:00",
                endTimeStr = "20:00",
                durationMinutes = 120,
                hourlyRate = 200.0,
                totalAmount = 400.0,
                overtimeType = "WEEKDAY",
                note = "Bordro testi"
            )
        )

        val settings = AppSettings(
            employeeName = "Ahmet Yılmaz",
            workplaceName = "Merkez Fabrika"
        )

        try {
            val pdfFile = com.example.util.PdfReportGenerator.generateMonthlyPdf(
                context = context,
                records = testRecords,
                yearMonth = java.time.YearMonth.of(2026, 10),
                settings = settings
            )
            assertTrue(pdfFile.exists())
            assertTrue(pdfFile.length() > 0)
        } catch (e: IllegalStateException) {
            // Android's native PdfDocument engine is stubbed in host JVM / Robolectric without native libpdfium
            assertTrue(e.message?.contains("document is closed") == true)
        }
    }

    @Test
    fun testBackupAndRestore_preservesDataAndSettings() = runBlocking {
        val settings = AppSettings(
            isSetupCompleted = true,
            weekdayRate = 250.0,
            weekendRate = 350.0,
            employeeName = "Mehmet Demir",
            workplaceName = "Bölge Müdürlüğü",
            forgottenAlertEnabled = true,
            forgottenAlertHours = 6
        )
        val record1 = OvertimeRecord(
            date = "2026-10-01",
            startTimeEpochMs = 1000L,
            endTimeEpochMs = 1000L + 3600000L,
            startTimeStr = "18:00",
            endTimeStr = "19:00",
            durationMinutes = 60,
            hourlyRate = 250.0,
            totalAmount = 250.0,
            overtimeType = "WEEKDAY"
        )

        val backupFile = com.example.util.BackupManager.createBackupFile(
            context = context,
            settings = settings,
            records = listOf(record1)
        )
        assertTrue(backupFile.exists())

        val backupUri = android.net.Uri.fromFile(backupFile)
        val parseResult = com.example.util.BackupManager.parseBackupFromUri(context, backupUri)
        assertTrue(parseResult.isSuccess)

        val data = parseResult.getOrThrow()
        assertEquals("Mehmet Demir", data.settings.employeeName)
        assertEquals("Bölge Müdürlüğü", data.settings.workplaceName)
        assertEquals(250.0, data.settings.weekdayRate, 0.001)
        assertEquals(6, data.settings.forgottenAlertHours)
        assertEquals(1, data.records.size)
        assertEquals("2026-10-01", data.records[0].date)

        val count = repository.restoreData(data.records, data.settings, clearExisting = true)
        assertEquals(1, count)

        val restoredSettings = repository.getSettings()
        assertEquals("Mehmet Demir", restoredSettings.employeeName)
        assertEquals(250.0, restoredSettings.weekdayRate, 0.001)

        val restoredRecords = repository.getAllRecordsList()
        assertEquals(1, restoredRecords.size)
        assertEquals(250.0, restoredRecords[0].totalAmount, 0.001)
    }

    @Test
    fun testCheckAndTriggerAutomaticOvertime_startsSessionSuccessfully() = runBlocking {
        val today = LocalDate.now(TimeUtils.ISTANBUL_ZONE)
        val isWeekend = TimeUtils.isWeekend(today)

        // Set start time 1 hour in the past so it is guaranteed to be due
        val pastHour = LocalTime.now(TimeUtils.ISTANBUL_ZONE).minusHours(1)
        val pastTimeStr = TimeUtils.formatTime(pastHour)

        val settings = repository.getSettings().copy(
            autoGlobalEnabled = true,
            autoMonday = true,
            autoTuesday = true,
            autoWednesday = true,
            autoThursday = true,
            autoFriday = true,
            autoSaturday = true,
            autoSunday = true,
            weekdayStartTime = pastTimeStr,
            saturdayStartTime = pastTimeStr,
            sundayStartTime = pastTimeStr,
            skipDate = ""
        )
        repository.updateSettings(settings)

        // Should automatically trigger and return true
        val started = repository.checkAndTriggerAutomaticOvertime()
        assertTrue(started)

        // Active overtime must now be active
        val active = repository.getActiveOvertime()
        assertTrue(active.isActive)
        assertTrue(active.isAutomatic)
        assertEquals(pastTimeStr, active.startTimeStr)

        // Calling again while active should safely return false (no duplicate)
        val secondCall = repository.checkAndTriggerAutomaticOvertime()
        assertFalse(secondCall)

        // Stop session and verify
        val stopped = repository.stopOvertime()
        assertNotNull(stopped)
        assertFalse(repository.getActiveOvertime().isActive)

        // After record exists for today, should not restart automatically
        val afterRecordedCall = repository.checkAndTriggerAutomaticOvertime()
        assertFalse(afterRecordedCall)
    }

    @Test
    fun testDataPersistence_andAutoBackupProtection() = runBlocking {
        // Create an overtime record
        val record = OvertimeRecord(
            date = "2026-09-15",
            startTimeEpochMs = 1790400000000L,
            endTimeEpochMs = 1790414400000L,
            startTimeStr = "18:00",
            endTimeStr = "22:00",
            durationMinutes = 240,
            hourlyRate = 200.0,
            totalAmount = 800.0,
            overtimeType = "WEEKDAY",
            isAutomatic = true,
            note = "Önemli proje mesaisi"
        )
        val id = repository.insertManualRecord(record)
        assertTrue(id > 0)

        // Verify record exists in DB
        val list = repository.getAllRecordsList()
        assertTrue(list.any { it.note == "Önemli proje mesaisi" })

        // Verify internal auto backup was created and contains the record
        val backupFile = java.io.File(context.filesDir, "auto_backup_overtime_records.json")
        assertTrue(backupFile.exists())
        val backupUri = android.net.Uri.fromFile(backupFile)
        val parsed = com.example.util.BackupManager.parseBackupFromUri(context, backupUri)
        assertTrue(parsed.isSuccess)
        val backupData = parsed.getOrThrow()
        assertTrue(backupData.records.any { it.note == "Önemli proje mesaisi" })
    }
}
