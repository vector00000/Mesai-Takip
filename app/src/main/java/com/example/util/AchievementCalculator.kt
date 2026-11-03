package com.example.util

import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

data class MonthlyStatPoint(
    val monthValue: Int, // 1..12
    val monthShortLabel: String, // "Oca", "Şub", ...
    val workingDays: Int,
    val completedRecords: Int,
    val totalMinutes: Int,
    val totalAmount: Double
) {
    val totalHours: Double get() = totalMinutes / 60.0
}

data class PeriodComparison(
    val currentLabel: String,
    val previousLabel: String,
    val currentWorkingDays: Int,
    val previousWorkingDays: Int,
    val currentRecordsCount: Int,
    val previousRecordsCount: Int,
    val currentHours: Double,
    val previousHours: Double,
    val currentEarnings: Double,
    val previousEarnings: Double
) {
    val workingDaysDiff: Int get() = currentWorkingDays - previousWorkingDays
    val recordsDiff: Int get() = currentRecordsCount - previousRecordsCount
    val hoursDiff: Double get() = currentHours - previousHours
    val earningsDiff: Double get() = currentEarnings - previousEarnings

    val workingDaysChangePercent: Int?
        get() = if (previousWorkingDays > 0) {
            (((currentWorkingDays - previousWorkingDays).toDouble() / previousWorkingDays) * 100.0).roundToInt()
        } else null
}

data class AchievementBadge(
    val id: String,
    val title: String,
    val conditionDescription: String,
    val category: String,
    val isUnlocked: Boolean,
    val unlockedDateStr: String?, // "yyyy-MM-dd"
    val currentProgress: Int,
    val targetProgress: Int
) {
    val progressFraction: Float
        get() = if (targetProgress <= 0) 0f else (currentProgress.toFloat() / targetProgress.toFloat()).coerceIn(0f, 1f)

    val progressPercent: Int
        get() = (progressFraction * 100f).roundToInt().coerceIn(0, 100)
}

data class PersonalAchievementSummary(
    val selectedYearMonth: YearMonth,
    val monthlyWorkingDaysCount: Int,
    val monthlyCompletedRecordsCount: Int,
    val monthlyTotalMinutes: Int,
    val monthlyTotalAmount: Double,
    val monthlyNotedRecordsCount: Int,
    val targetDays: Int,
    val targetHours: Double,
    val daysGoalPercent: Int,
    val hoursGoalPercent: Int,
    val daysGoalReached: Boolean,
    val hoursGoalReached: Boolean,
    val totalValidRecordsAllTime: Int,
    val totalValidDaysAllTime: Int,
    val excludedInvalidRecordsCount: Int,
    val yearlyMonthlyBreakdown: List<MonthlyStatPoint>,
    val previousMonthComparison: PeriodComparison,
    val previousYearComparison: PeriodComparison,
    val badges: List<AchievementBadge>,
    val milestoneBadges: List<AchievementBadge> = emptyList()
) {
    val monthlyTotalHours: Double get() = monthlyTotalMinutes / 60.0
    val unlockedBadgesCount: Int get() = badges.count { it.isUnlocked }
    val allBadges: List<AchievementBadge> get() = badges + milestoneBadges
    val unlockedAllBadgesCount: Int get() = allBadges.count { it.isUnlocked }
}

object AchievementCalculator {

    private val DATE_REGEX = Regex("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$")
    private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    private val MONTH_SHORT_NAMES_TR = listOf(
        "Oca", "Şub", "Mar", "Nis", "May", "Haz",
        "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"
    )

    /**
     * Eksik veya geçersiz kayıtları başarı ve istatistik hesabına dahil etmez.
     */
    fun isValidCompletedRecord(record: OvertimeRecord): Boolean {
        val cleanDate = record.date.trim()
        if (!DATE_REGEX.matches(cleanDate)) return false
        try {
            LocalDate.parse(cleanDate)
        } catch (_: Exception) {
            return false
        }

        if (!TIME_REGEX.matches(record.startTimeStr.trim())) return false
        if (!TIME_REGEX.matches(record.endTimeStr.trim())) return false
        if (record.durationMinutes <= 0 || record.durationMinutes > 1440) return false
        if (record.endTimeEpochMs <= record.startTimeEpochMs) return false
        if (record.hourlyRate <= 0.0 || record.hourlyRate.isNaN() || record.hourlyRate.isInfinite()) return false
        if (record.totalAmount < 0.0 || record.totalAmount.isNaN() || record.totalAmount.isInfinite()) return false

        return true
    }

    /**
     * Kullanıcının gerçek mesai kayıtlarına ve belirlediği hedeflere göre
     * tüm kişisel istatistikleri, karşılaştırmaları ve rozetleri hesaplar.
     * Aynı gün birden fazla mesai kaydı girilmesi, farklı çalışma günü (`totalValidDaysAllTime`)
     * sayısını yalnızca 1 gün olarak artırır.
     */
    fun calculateSummary(
        allRecords: List<OvertimeRecord>,
        selectedYearMonth: YearMonth,
        settings: AppSettings,
        archivedDocumentsCount: Int = 0,
        firstArchivedDocumentDate: String? = null,
        archivedReportsCount: Int = 0,
        firstArchivedReportDate: String? = null
    ): PersonalAchievementSummary {
        val validRecords = allRecords
            .filter { isValidCompletedRecord(it) }
            .sortedWith(compareBy<OvertimeRecord> { it.date }.thenBy { it.startTimeEpochMs })

        val excludedCount = (allRecords.size - validRecords.size).coerceAtLeast(0)

        val ymPrefix = selectedYearMonth.toString() // "yyyy-MM"
        val currentMonthRecords = validRecords.filter { it.date.startsWith(ymPrefix) }

        val monthlyWorkingDaysCount = currentMonthRecords.map { it.date }.distinct().size
        val monthlyCompletedRecordsCount = currentMonthRecords.size
        val monthlyTotalMinutes = currentMonthRecords.sumOf { it.durationMinutes }
        val monthlyTotalHours = monthlyTotalMinutes / 60.0
        val monthlyTotalAmount = currentMonthRecords.sumOf { it.totalAmount }
        val monthlyNotedRecordsCount = currentMonthRecords.count { it.note.trim().length >= 3 }

        val targetDays = settings.monthlyTargetDays.coerceIn(1, 31)
        val targetHours = settings.monthlyTargetHours.coerceIn(1.0, 300.0)

        val daysGoalPercent = ((monthlyWorkingDaysCount.toDouble() / targetDays.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        val hoursGoalPercent = ((monthlyTotalHours / targetHours) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)

        val daysGoalReached = monthlyWorkingDaysCount >= targetDays
        val hoursGoalReached = monthlyTotalHours >= targetHours - 1e-6

        // Yıllık 12 aylık grafik verisi
        val selectedYear = selectedYearMonth.year
        val yearlyMonthlyBreakdown = (1..12).map { month ->
            val prefix = "%04d-%02d".format(selectedYear, month)
            val mRecords = validRecords.filter { it.date.startsWith(prefix) }
            MonthlyStatPoint(
                monthValue = month,
                monthShortLabel = MONTH_SHORT_NAMES_TR[month - 1],
                workingDays = mRecords.map { it.date }.distinct().size,
                completedRecords = mRecords.size,
                totalMinutes = mRecords.sumOf { it.durationMinutes },
                totalAmount = mRecords.sumOf { it.totalAmount }
            )
        }

        // Önceki ay ile karşılaştırma
        val prevYearMonth = selectedYearMonth.minusMonths(1)
        val prevMonthPrefix = prevYearMonth.toString()
        val prevMonthRecords = validRecords.filter { it.date.startsWith(prevMonthPrefix) }
        val previousMonthComparison = PeriodComparison(
            currentLabel = TimeUtils.formatMonthYear(selectedYearMonth),
            previousLabel = TimeUtils.formatMonthYear(prevYearMonth),
            currentWorkingDays = monthlyWorkingDaysCount,
            previousWorkingDays = prevMonthRecords.map { it.date }.distinct().size,
            currentRecordsCount = monthlyCompletedRecordsCount,
            previousRecordsCount = prevMonthRecords.size,
            currentHours = monthlyTotalHours,
            previousHours = prevMonthRecords.sumOf { it.durationMinutes } / 60.0,
            currentEarnings = monthlyTotalAmount,
            previousEarnings = prevMonthRecords.sumOf { it.totalAmount }
        )

        // Önceki yıl ile karşılaştırma
        val prevYear = selectedYear - 1
        val currentYearPrefix = "%04d-".format(selectedYear)
        val prevYearPrefix = "%04d-".format(prevYear)
        val currentYearRecords = validRecords.filter { it.date.startsWith(currentYearPrefix) }
        val prevYearRecords = validRecords.filter { it.date.startsWith(prevYearPrefix) }
        val previousYearComparison = PeriodComparison(
            currentLabel = "$selectedYear Yılı",
            previousLabel = "$prevYear Yılı",
            currentWorkingDays = currentYearRecords.map { it.date }.distinct().size,
            previousWorkingDays = prevYearRecords.map { it.date }.distinct().size,
            currentRecordsCount = currentYearRecords.size,
            previousRecordsCount = prevYearRecords.size,
            currentHours = currentYearRecords.sumOf { it.durationMinutes } / 60.0,
            previousHours = prevYearRecords.sumOf { it.durationMinutes } / 60.0,
            currentEarnings = currentYearRecords.sumOf { it.totalAmount },
            previousEarnings = prevYearRecords.sumOf { it.totalAmount }
        )

        val totalArchiveCount = archivedDocumentsCount + archivedReportsCount
        val earliestArchiveDate = listOfNotNull(firstArchivedDocumentDate, firstArchivedReportDate).minOrNull()

        val badges = computeEthicalBadges(
            validRecords = validRecords,
            currentMonthRecords = currentMonthRecords,
            targetDays = targetDays,
            archivedDocumentsCount = totalArchiveCount,
            firstArchivedDocumentDate = earliestArchiveDate
        )

        val milestoneBadges = computeMilestoneBadges(
            validRecords = validRecords,
            excludedInvalidCount = excludedCount,
            archivedReportsCount = totalArchiveCount,
            firstArchivedReportDate = earliestArchiveDate
        )

        return PersonalAchievementSummary(
            selectedYearMonth = selectedYearMonth,
            monthlyWorkingDaysCount = monthlyWorkingDaysCount,
            monthlyCompletedRecordsCount = monthlyCompletedRecordsCount,
            monthlyTotalMinutes = monthlyTotalMinutes,
            monthlyTotalAmount = monthlyTotalAmount,
            monthlyNotedRecordsCount = monthlyNotedRecordsCount,
            targetDays = targetDays,
            targetHours = targetHours,
            daysGoalPercent = daysGoalPercent,
            hoursGoalPercent = hoursGoalPercent,
            daysGoalReached = daysGoalReached,
            hoursGoalReached = hoursGoalReached,
            totalValidRecordsAllTime = validRecords.size,
            totalValidDaysAllTime = validRecords.map { it.date }.distinct().size,
            excludedInvalidRecordsCount = excludedCount,
            yearlyMonthlyBreakdown = yearlyMonthlyBreakdown,
            previousMonthComparison = previousMonthComparison,
            previousYearComparison = previousYearComparison,
            badges = badges,
            milestoneBadges = milestoneBadges
        )
    }

    /**
     * Özellik 68 kilometre taşları:
     * - 7 farklı günde mesai kaydı oluşturma
     * - 30 farklı günde mesai kaydı oluşturma
     * - 100 farklı günde mesai kaydı oluşturma
     * - İlk aylık çalışma raporunu oluşturma
     * - Eksik kayıtların kontrol edilmesi ve kayıtların düzenli tutulması
     */
    private fun computeMilestoneBadges(
        validRecords: List<OvertimeRecord>,
        excludedInvalidCount: Int,
        archivedReportsCount: Int,
        firstArchivedReportDate: String?
    ): List<AchievementBadge> {
        // Farklı günler listesi (Aynı gün birden fazla kayıt yalnızca 1 farklı çalışma günü sayılır!)
        val distinctSortedDates = validRecords.map { it.date }.distinct().sorted()
        val distinctDaysCount = distinctSortedDates.size

        // 1. 7 Farklı Çalışma Günü Kilometre Taşı
        val badge7Days = AchievementBadge(
            id = "DISTINCT_DAYS_7",
            title = "7 Farklı Çalışma Günü",
            conditionDescription = "7 farklı takvim gününde geçerli çalışma kaydı oluşturun.",
            category = "Kilometre Taşı",
            isUnlocked = distinctDaysCount >= 7,
            unlockedDateStr = distinctSortedDates.getOrNull(6),
            currentProgress = distinctDaysCount.coerceAtMost(7),
            targetProgress = 7
        )

        // 2. 30 Farklı Çalışma Günü Kilometre Taşı
        val badge30Days = AchievementBadge(
            id = "DISTINCT_DAYS_30",
            title = "30 Farklı Çalışma Günü",
            conditionDescription = "30 farklı takvim gününde düzenli çalışma kaydı oluşturun.",
            category = "Kilometre Taşı",
            isUnlocked = distinctDaysCount >= 30,
            unlockedDateStr = distinctSortedDates.getOrNull(29),
            currentProgress = distinctDaysCount.coerceAtMost(30),
            targetProgress = 30
        )

        // 3. 100 Farklı Çalışma Günü Kilometre Taşı
        val badge100Days = AchievementBadge(
            id = "DISTINCT_DAYS_100",
            title = "100 Farklı Çalışma Günü",
            conditionDescription = "100 farklı takvim gününde çalışma kaydı geçmişine ulaşın.",
            category = "Kilometre Taşı",
            isUnlocked = distinctDaysCount >= 100,
            unlockedDateStr = distinctSortedDates.getOrNull(99),
            currentProgress = distinctDaysCount.coerceAtMost(100),
            targetProgress = 100
        )

        // 4. İlk Aylık Çalışma Raporu Oluşturma
        val badgeFirstMonthlyReport = AchievementBadge(
            id = "FIRST_MONTHLY_REPORT",
            title = "İlk Aylık Çalışma Raporu",
            conditionDescription = "Kişisel Maaş Programlama Raporu arşivine ilk aylık raporunuzu kaydedin.",
            category = "Raporlama & Arşiv",
            isUnlocked = archivedReportsCount >= 1,
            unlockedDateStr = if (archivedReportsCount >= 1) firstArchivedReportDate else null,
            currentProgress = archivedReportsCount.coerceAtMost(1),
            targetProgress = 1
        )

        // 5. Eksiksiz ve Hatasız Kayıt Denetimi
        val isCleanAudit = validRecords.size >= 3 && excludedInvalidCount == 0
        val badgeCleanAudit = AchievementBadge(
            id = "CLEAN_AUDIT_RECORDS",
            title = "Eksiksiz & Hatasız Kayıt Düzeni",
            conditionDescription = "Hiçbir eksik veya hatalı saat kaydı olmadan en az 3 doğrulanmış mesai kaydı tutun.",
            category = "Veri Bütünlüğü",
            isUnlocked = isCleanAudit,
            unlockedDateStr = if (isCleanAudit) validRecords.getOrNull(2)?.date else null,
            currentProgress = if (excludedInvalidCount == 0) validRecords.size.coerceAtMost(3) else 0,
            targetProgress = 3
        )

        return listOf(
            badge7Days,
            badge30Days,
            badge100Days,
            badgeFirstMonthlyReport,
            badgeCleanAudit
        )
    }

    /**
     * Başarı sistemi kullanıcıları gereksiz fazla mesai yapmaya teşvik etmez.
     * Rozetler düzenli kayıt tutma, hedef takibi, veri bütünlüğü ve dengeli planlama ölçütlerine dayanır.
     */
    private fun computeEthicalBadges(
        validRecords: List<OvertimeRecord>,
        currentMonthRecords: List<OvertimeRecord>,
        targetDays: Int,
        archivedDocumentsCount: Int,
        firstArchivedDocumentDate: String?
    ): List<AchievementBadge> {
        // 1. İlk Adım: İlk geçerli mesai kaydını oluşturma
        val firstRecord = validRecords.firstOrNull()
        val badgeFirstRecord = AchievementBadge(
            id = "FIRST_VALID_RECORD",
            title = "İlk Adım: Düzenli Kayıt",
            conditionDescription = "Doğrulanmış ilk mesai kaydınızı eksiksiz olarak oluşturun.",
            category = "Kayıt Düzeni",
            isUnlocked = firstRecord != null,
            unlockedDateStr = firstRecord?.date,
            currentProgress = validRecords.size.coerceAtMost(1),
            targetProgress = 1
        )

        // 2. Detaylı Kayıt Tutucu: En az 5 mesai kaydına açıklama/not ekleyerek şeffaf veri tutma
        val notedRecords = validRecords.filter { it.note.trim().length >= 3 }
        val fifthNotedRecord = notedRecords.getOrNull(4)
        val badgeDetailedLogger = AchievementBadge(
            id = "DETAILED_NOTE_LOGGER",
            title = "Şeffaf Veri & Not Tutucu",
            conditionDescription = "Veri bütünlüğü için en az 5 mesai kaydına açıklayıcı çalışma notu ekleyin.",
            category = "Veri Bütünlüğü",
            isUnlocked = notedRecords.size >= 5,
            unlockedDateStr = fifthNotedRecord?.date,
            currentProgress = notedRecords.size.coerceAtMost(5),
            targetProgress = 5
        )

        // 3. Düzenli Takipçi: Aynı ay içinde 5 farklı günde düzenli mesai kaydı tutma
        val recordsByMonth = validRecords.groupBy { it.date.take(7) }
        var fiveDayUnlockDate: String? = null
        var maxDistinctDaysInAnyMonth = 0
        for ((_, monthList) in recordsByMonth.entries.sortedBy { it.key }) {
            val distinctDates = monthList.map { it.date }.distinct().sorted()
            if (distinctDates.size > maxDistinctDaysInAnyMonth) {
                maxDistinctDaysInAnyMonth = distinctDates.size
            }
            if (fiveDayUnlockDate == null && distinctDates.size >= 5) {
                fiveDayUnlockDate = distinctDates[4]
            }
        }
        val badgeConsistentTracker = AchievementBadge(
            id = "MONTHLY_CONSISTENT_5_DAYS",
            title = "Düzenli Takip Alışkanlığı",
            conditionDescription = "Bir ay içerisinde en az 5 farklı günde kayıtlarınızı aksatmadan işleyin.",
            category = "Kayıt Düzeni",
            isUnlocked = fiveDayUnlockDate != null,
            unlockedDateStr = fiveDayUnlockDate,
            currentProgress = maxDistinctDaysInAnyMonth.coerceAtMost(5),
            targetProgress = 5
        )

        // 4. Aylık Hedef Takipçisi: Kullanıcının kendi belirlediği aylık kayıt günü hedefine ulaşma
        var targetReachedDate: String? = null
        val currentMonthDistinctDates = currentMonthRecords.map { it.date }.distinct().sorted()
        for ((_, monthList) in recordsByMonth.entries.sortedBy { it.key }) {
            val distinctDates = monthList.map { it.date }.distinct().sorted()
            if (distinctDates.size >= targetDays) {
                targetReachedDate = distinctDates[targetDays - 1]
                break
            }
        }
        val badgeGoalTracker = AchievementBadge(
            id = "PERSONAL_GOAL_ACHIEVER",
            title = "Kişisel Hedef Takipçisi",
            conditionDescription = "Belirlediğiniz aylık kayıt günü hedefine ($targetDays gün) ulaşın.",
            category = "Hedef Takibi",
            isUnlocked = targetReachedDate != null,
            unlockedDateStr = targetReachedDate,
            currentProgress = currentMonthDistinctDates.size.coerceAtMost(targetDays),
            targetProgress = targetDays
        )

        // 5. Dengeli Çalışma & Kayıt: Aşırı mesai yapmadan (kayıt başına <= 180 dk / 3 saat)
        // en az 5 dengeli mesai kaydı tamamlama (fazla mesaiye teşvik etmeyen koruyucu ölçüt)
        val balancedRecords = validRecords.filter { it.durationMinutes in 15..180 }
        val fifthBalanced = balancedRecords.getOrNull(4)
        val badgeBalancedPlanner = AchievementBadge(
            id = "BALANCED_WORK_LIFE_LOGGER",
            title = "Dengeli Çalışma Bilinci",
            conditionDescription = "Günlük makul süreyi (3 saat) aşmayan en az 5 dengeli mesai kaydı tutun.",
            category = "Dengeli Planlama",
            isUnlocked = balancedRecords.size >= 5,
            unlockedDateStr = fifthBalanced?.date,
            currentProgress = balancedRecords.size.coerceAtMost(5),
            targetProgress = 5
        )

        // 6. Çok Aylık İstikrar: En az 3 farklı ayda düzenli kayıt geçmişi oluşturma
        val distinctMonths = recordsByMonth.keys.sorted()
        val thirdMonthUnlockDate = if (distinctMonths.size >= 3) {
            recordsByMonth[distinctMonths[2]]?.firstOrNull()?.date
        } else null
        val badgeMultiMonth = AchievementBadge(
            id = "THREE_MONTH_CONTINUITY",
            title = "Üç Aylık Arşiv İstikrarı",
            conditionDescription = "En az 3 farklı ay boyunca mesai çizelgenizi düzenli olarak kayıt altına alın.",
            category = "Kayıt Düzeni",
            isUnlocked = thirdMonthUnlockDate != null,
            unlockedDateStr = thirdMonthUnlockDate,
            currentProgress = distinctMonths.size.coerceAtMost(3),
            targetProgress = 3
        )

        // 7. Belge Arşivi Düzeni: Maaş bordrosu veya resmi mesai belgesi arşivleme
        val badgeDocumentOrganizer = AchievementBadge(
            id = "PAYROLL_ARCHIVE_ORGANIZER",
            title = "Kurumsal Belge Arşivcisi",
            conditionDescription = "Maaş Bordrosu ve Belge Arşivi'ne ilk resmi bordro veya belgenizi ekleyin.",
            category = "Veri Bütünlüğü",
            isUnlocked = archivedDocumentsCount >= 1,
            unlockedDateStr = if (archivedDocumentsCount >= 1) firstArchivedDocumentDate else null,
            currentProgress = archivedDocumentsCount.coerceAtMost(1),
            targetProgress = 1
        )

        return listOf(
            badgeFirstRecord,
            badgeDetailedLogger,
            badgeConsistentTracker,
            badgeGoalTracker,
            badgeBalancedPlanner,
            badgeMultiMonth,
            badgeDocumentOrganizer
        )
    }
}
