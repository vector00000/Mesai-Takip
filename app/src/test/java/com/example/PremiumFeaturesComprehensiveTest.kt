package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import com.example.data.model.PayrollDocumentType
import com.example.data.repository.DocumentArchiveRepository
import com.example.util.AchievementCalculator
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
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PremiumFeaturesComprehensiveTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private var activeUserId: String = "acct_user_alpha"
    private var isUserPremium: Boolean = true

    private lateinit var archiveRepository: DocumentArchiveRepository

    // Geçerli PDF (%PDF-1.7) ve PNG başlık baytları
    private val validPdfBytes = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".toByteArray()
    private val validPngBytes = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte()
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        activeUserId = "acct_user_alpha"
        isUserPremium = true

        archiveRepository = DocumentArchiveRepository(
            context = context,
            payrollDocumentDao = database.payrollDocumentDao(),
            accountIdProvider = { activeUserId },
            premiumEntitlementChecker = { isUserPremium }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. Belge Ekleme, Görüntüleme ve Silme Senaryoları (PDF & Fotoğraf)
    @Test
    fun `1_document add view and delete lifecycle works for PDF and PNG`() = runBlocking {
        val addPdfResult = archiveRepository.addDocumentFromBytes(
            bytes = validPdfBytes,
            originalFileName = "ekim_2026_bordro.pdf",
            rawMimeType = "application/pdf",
            title = "Ekim 2026 Maaş Bordrosu",
            documentType = PayrollDocumentType.PAYSLIP,
            yearMonth = "2026-10",
            documentDate = "2026-10-15",
            description = "Onaylı resmi maaş bordrosu"
        )
        assertTrue(addPdfResult.isSuccess)
        val savedPdf = addPdfResult.getOrThrow()
        assertEquals("Ekim 2026 Maaş Bordrosu", savedPdf.title)
        assertEquals("2026-10", savedPdf.yearMonth)
        assertEquals("application/pdf", savedPdf.mimeType)
        assertTrue(savedPdf.sha256Checksum.isNotBlank())

        val addPngResult = archiveRepository.addDocumentFromBytes(
            bytes = validPngBytes,
            originalFileName = "mesai_cizelgesi.png",
            rawMimeType = "image/png",
            title = "Ekim Mesai Çizelgesi Fotoğrafı",
            documentType = PayrollDocumentType.OVERTIME_SHEET,
            yearMonth = "2026-10",
            documentDate = "2026-10-16",
            description = "İmzalı çizelge"
        )
        assertTrue(addPngResult.isSuccess)

        // Listeleme kontrolü
        val monthDocs = archiveRepository.getDocumentsForMonthFlow("2026-10").first()
        assertEquals(2, monthDocs.size)

        // Görüntüleme için güvenli dosya erişimi
        val viewResult = archiveRepository.getAuthorizedDocumentFile(savedPdf.id)
        assertTrue(viewResult.isSuccess)
        val (docMeta, storedFile) = viewResult.getOrThrow()
        assertEquals(savedPdf.id, docMeta.id)
        assertTrue(storedFile.exists())
        assertEquals(validPdfBytes.size.toLong(), storedFile.length())

        // Silme işlemi (hem DB hem diskten silinmeli)
        val deleteResult = archiveRepository.deleteDocument(savedPdf.id)
        assertTrue(deleteResult.isSuccess)
        assertFalse(storedFile.exists())

        val remainingDocs = archiveRepository.getDocumentsForMonthFlow("2026-10").first()
        assertEquals(1, remainingDocs.size)
    }

    // 2. Başka Kullanıcıların Belgelerine Erişilememesi (Kullanıcı İzolasyonu Testi)
    @Test
    fun `2_cross user isolation prevents another user from reading listing or deleting documents`() = runBlocking {
        // Kullanıcı A belge ekler
        activeUserId = "acct_user_alpha"
        isUserPremium = true
        val userADoc = archiveRepository.addDocumentFromBytes(
            bytes = validPdfBytes,
            originalFileName = "gizli_bordro.pdf",
            rawMimeType = "application/pdf",
            title = "Alpha Maaş Bordrosu",
            documentType = PayrollDocumentType.PAYSLIP,
            yearMonth = "2026-10",
            documentDate = "2026-10-15"
        ).getOrThrow()

        // Oturum Kullanıcı B'ye geçtiğinde:
        activeUserId = "acct_user_bravo"

        // 1) Kullanıcı B kendi listesinde Kullanıcı A'nın belgesini göremez
        val userBList = archiveRepository.getDocumentsForCurrentUserFlow().first()
        assertTrue(userBList.isEmpty())

        // 2) Kullanıcı B, Kullanıcı A'nın belge ID'sini bilse dahi görüntüleyemez
        val unauthorizedRead = archiveRepository.getAuthorizedDocumentFile(userADoc.id)
        assertTrue(unauthorizedRead.isFailure)
        assertTrue(unauthorizedRead.exceptionOrNull() is SecurityException)

        // 3) Kullanıcı B, Kullanıcı A'nın belgesini silemez
        val unauthorizedDelete = archiveRepository.deleteDocument(userADoc.id)
        assertTrue(unauthorizedDelete.isFailure)
        assertTrue(unauthorizedDelete.exceptionOrNull() is SecurityException)

        // 4) Kullanıcı A tekrar kendi oturumunda belgesine erişebilir
        activeUserId = "acct_user_alpha"
        val ownerRead = archiveRepository.getAuthorizedDocumentFile(userADoc.id)
        assertTrue(ownerRead.isSuccess)
    }

    // 3. Dosya Türü, Boyutu ve Yükleme Hata Senaryoları
    @Test
    fun `3_document validation rejects empty invalid signature unsupported mime and invalid metadata`() = runBlocking {
        // Boş dosya reddedilmeli
        val emptyFileRes = archiveRepository.addDocumentFromBytes(
            bytes = ByteArray(0),
            originalFileName = "bos.pdf",
            rawMimeType = "application/pdf",
            title = "Boş Belge",
            documentType = PayrollDocumentType.PAYSLIP,
            yearMonth = "2026-10",
            documentDate = "2026-10-15"
        )
        assertTrue(emptyFileRes.isFailure)

        // Sahte uzantılı / bozuk içerikli dosya reddedilmeli (.pdf uzantılı ama PDF başlığı yok)
        val fakePdfRes = archiveRepository.addDocumentFromBytes(
            bytes = "Bu gerçek bir PDF dosyası değildir".toByteArray(),
            originalFileName = "sahte.pdf",
            rawMimeType = "application/pdf",
            title = "Sahte PDF",
            documentType = PayrollDocumentType.PAYSLIP,
            yearMonth = "2026-10",
            documentDate = "2026-10-15"
        )
        assertTrue(fakePdfRes.isFailure)

        // Desteklenmeyen dosya türü (.exe / application/octet-stream) reddedilmeli
        val unsupportedMimeRes = archiveRepository.addDocumentFromBytes(
            bytes = validPdfBytes,
            originalFileName = "zararli.exe",
            rawMimeType = "application/octet-stream",
            title = "Yürütülebilir Dosya",
            documentType = PayrollDocumentType.CONTRACT_OTHER,
            yearMonth = "2026-10",
            documentDate = "2026-10-15"
        )
        assertTrue(unsupportedMimeRes.isFailure)

        // Boş belge adı reddedilmeli
        val blankTitleRes = archiveRepository.addDocumentFromBytes(
            bytes = validPdfBytes,
            originalFileName = "bordro.pdf",
            rawMimeType = "application/pdf",
            title = "   ",
            documentType = PayrollDocumentType.PAYSLIP,
            yearMonth = "2026-10",
            documentDate = "2026-10-15"
        )
        assertTrue(blankTitleRes.isFailure)

        // Hatalı yıl/ay formatı reddedilmeli
        val invalidMonthRes = archiveRepository.addDocumentFromBytes(
            bytes = validPdfBytes,
            originalFileName = "bordro.pdf",
            rawMimeType = "application/pdf",
            title = "Geçerli Başlık",
            documentType = PayrollDocumentType.PAYSLIP,
            yearMonth = "2026-15",
            documentDate = "2026-10-15"
        )
        assertTrue(invalidMonthRes.isFailure)
    }

    // 4. Premium ve Ücretsiz Kullanıcı Erişim Kontrolü
    @Test
    fun `4_free user is blocked from adding viewing or deleting archive documents`() = runBlocking {
        // Önce Premium iken bir belge ekleyelim
        isUserPremium = true
        val doc = archiveRepository.addDocumentFromBytes(
            bytes = validPdfBytes,
            originalFileName = "ekim_bordro.pdf",
            rawMimeType = "application/pdf",
            title = "Ekim Bordro",
            documentType = PayrollDocumentType.PAYSLIP,
            yearMonth = "2026-10",
            documentDate = "2026-10-15"
        ).getOrThrow()

        // Kullanıcının Premium aboneliği sona erdiğinde (isUserPremium = false):
        isUserPremium = false

        val addAttempt = archiveRepository.addDocumentFromBytes(
            bytes = validPdfBytes,
            originalFileName = "kasim_bordro.pdf",
            rawMimeType = "application/pdf",
            title = "Kasım Bordro",
            documentType = PayrollDocumentType.PAYSLIP,
            yearMonth = "2026-11",
            documentDate = "2026-11-15"
        )
        assertTrue(addAttempt.isFailure)
        assertTrue(addAttempt.exceptionOrNull() is SecurityException)

        val viewAttempt = archiveRepository.getAuthorizedDocumentFile(doc.id)
        assertTrue(viewAttempt.isFailure)
        assertTrue(viewAttempt.exceptionOrNull() is SecurityException)

        val deleteAttempt = archiveRepository.deleteDocument(doc.id)
        assertTrue(deleteAttempt.isFailure)
        assertTrue(deleteAttempt.exceptionOrNull() is SecurityException)
    }

    // 5. Kişisel Başarılar ve İstatistikler — Gerçek ve Sınır Verilerle Hesaplama Testi
    @Test
    fun `5_achievement calculator filters invalid records and computes accurate stats comparisons and badges`() {
        val settings = AppSettings(
            monthlyTargetDays = 5,
            monthlyTargetHours = 10.0
        )

        val records = listOf(
            // Ekim 2026 için 5 farklı günde geçerli ve dengeli kayıtlar (her biri 2 saat = 120 dk, notlu)
            OvertimeRecord(
                id = 1, date = "2026-10-02", startTimeEpochMs = 1000L, endTimeEpochMs = 7_201_000L,
                startTimeStr = "18:00", endTimeStr = "20:00", durationMinutes = 120,
                hourlyRate = 250.0, totalAmount = 500.0, overtimeType = "WEEKDAY", note = "Proje teslimi"
            ),
            OvertimeRecord(
                id = 2, date = "2026-10-05", startTimeEpochMs = 1000L, endTimeEpochMs = 7_201_000L,
                startTimeStr = "18:00", endTimeStr = "20:00", durationMinutes = 120,
                hourlyRate = 250.0, totalAmount = 500.0, overtimeType = "WEEKDAY", note = "Rapor kontrolü"
            ),
            OvertimeRecord(
                id = 3, date = "2026-10-08", startTimeEpochMs = 1000L, endTimeEpochMs = 7_201_000L,
                startTimeStr = "18:00", endTimeStr = "20:00", durationMinutes = 120,
                hourlyRate = 250.0, totalAmount = 500.0, overtimeType = "WEEKDAY", note = "Müşteri toplantısı"
            ),
            OvertimeRecord(
                id = 4, date = "2026-10-12", startTimeEpochMs = 1000L, endTimeEpochMs = 7_201_000L,
                startTimeStr = "18:00", endTimeStr = "20:00", durationMinutes = 120,
                hourlyRate = 250.0, totalAmount = 500.0, overtimeType = "WEEKDAY", note = "Sistem bakımı"
            ),
            OvertimeRecord(
                id = 5, date = "2026-10-15", startTimeEpochMs = 1000L, endTimeEpochMs = 7_201_000L,
                startTimeStr = "18:00", endTimeStr = "20:00", durationMinutes = 120,
                hourlyRate = 250.0, totalAmount = 500.0, overtimeType = "WEEKDAY", note = "Envanter sayımı"
            ),
            // Eylül 2026 (önceki ay) için 2 geçerli kayıt
            OvertimeRecord(
                id = 6, date = "2026-09-10", startTimeEpochMs = 1000L, endTimeEpochMs = 3_601_000L,
                startTimeStr = "18:00", endTimeStr = "19:00", durationMinutes = 60,
                hourlyRate = 250.0, totalAmount = 250.0, overtimeType = "WEEKDAY", note = "Eylül mesaisi"
            ),
            // Ağustos 2026 (3. farklı ay) için 1 geçerli kayıt
            OvertimeRecord(
                id = 7, date = "2026-08-20", startTimeEpochMs = 1000L, endTimeEpochMs = 3_601_000L,
                startTimeStr = "18:00", endTimeStr = "19:00", durationMinutes = 60,
                hourlyRate = 250.0, totalAmount = 250.0, overtimeType = "WEEKDAY", note = "Ağustos mesaisi"
            ),
            // GEÇERSİZ KAYIT 1: Süresi 0 dakika (Başarı hesabına dahil edilmemeli!)
            OvertimeRecord(
                id = 8, date = "2026-10-18", startTimeEpochMs = 5000L, endTimeEpochMs = 5000L,
                startTimeStr = "18:00", endTimeStr = "18:00", durationMinutes = 0,
                hourlyRate = 250.0, totalAmount = 0.0, overtimeType = "WEEKDAY", note = "Hatalı sıfır süre"
            ),
            // GEÇERSİZ KAYIT 2: Bozuk tarih formatı (Başarı hesabına dahil edilmemeli!)
            OvertimeRecord(
                id = 9, date = "gecersiz-tarih", startTimeEpochMs = 1000L, endTimeEpochMs = 7_201_000L,
                startTimeStr = "18:00", endTimeStr = "20:00", durationMinutes = 120,
                hourlyRate = 250.0, totalAmount = 500.0, overtimeType = "WEEKDAY", note = "Bozuk tarih"
            )
        )

        val summary = AchievementCalculator.calculateSummary(
            allRecords = records,
            selectedYearMonth = YearMonth.of(2026, 10),
            settings = settings,
            archivedDocumentsCount = 1,
            firstArchivedDocumentDate = "2026-10-15"
        )

        // Geçersiz 2 kayıt elenmiş olmalı
        assertEquals(2, summary.excludedInvalidRecordsCount)
        assertEquals(7, summary.totalValidRecordsAllTime)

        // Ekim 2026 istatistikleri
        assertEquals(5, summary.monthlyWorkingDaysCount)
        assertEquals(5, summary.monthlyCompletedRecordsCount)
        assertEquals(600, summary.monthlyTotalMinutes) // 10 saat
        assertEquals(100, summary.daysGoalPercent)
        assertEquals(100, summary.hoursGoalPercent)
        assertTrue(summary.daysGoalReached)
        assertTrue(summary.hoursGoalReached)

        // Önceki ay (Eylül 2026) ile karşılaştırma
        assertEquals(5, summary.previousMonthComparison.currentWorkingDays)
        assertEquals(1, summary.previousMonthComparison.previousWorkingDays)
        assertEquals(4, summary.previousMonthComparison.workingDaysDiff)

        // Tüm 7 etik rozetin kazanılmış ve tarihlerinin doğru hesaplanmış olması
        assertEquals(7, summary.unlockedBadgesCount)
        val firstBadge = summary.badges.first { it.id == "FIRST_VALID_RECORD" }
        assertTrue(firstBadge.isUnlocked)
        assertEquals("2026-08-20", firstBadge.unlockedDateStr)

        val archiveBadge = summary.badges.first { it.id == "PAYROLL_ARCHIVE_ORGANIZER" }
        assertTrue(archiveBadge.isUnlocked)
        assertEquals("2026-10-15", archiveBadge.unlockedDateStr)
    }
}
