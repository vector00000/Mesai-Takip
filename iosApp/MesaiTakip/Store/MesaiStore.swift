import Foundation
import UserNotifications

final class MesaiStore: ObservableObject {
    @Published var settings: AppSettings = AppSettings() {
        didSet { saveCodable(settings, key: "mesai_settings") }
    }
    @Published var records: [OvertimeRecord] = [] {
        didSet {
            saveCodable(records, key: "mesai_records")
            evaluateAchievementsAndNotifications()
        }
    }
    @Published var activeOvertime: ActiveOvertime = ActiveOvertime() {
        didSet { saveCodable(activeOvertime, key: "mesai_active") }
    }
    @Published var budgetTransactions: [BudgetTransaction] = [] {
        didSet { saveCodable(budgetTransactions, key: "mesai_budget") }
    }
    @Published var tariffRules: [RateTariffRule] = [] {
        didSet { saveCodable(tariffRules, key: "mesai_tariffs") }
    }
    @Published var archivedReports: [PayrollArchiveReport] = [] {
        didSet {
            saveCodable(archivedReports, key: "mesai_archived_reports")
            evaluateAchievementsAndNotifications()
        }
    }
    @Published var notifications: [AppNotification] = [] {
        didSet { saveCodable(notifications, key: "mesai_notifications") }
    }
    @Published var selectedYearMonth: String = MesaiStore.currentYearMonthString()

    init() {
        settings = loadCodable(AppSettings.self, key: "mesai_settings") ?? AppSettings()
        records = loadCodable([OvertimeRecord].self, key: "mesai_records") ?? []
        activeOvertime = loadCodable(ActiveOvertime.self, key: "mesai_active") ?? ActiveOvertime()
        budgetTransactions = loadCodable([BudgetTransaction].self, key: "mesai_budget") ?? []
        tariffRules = loadCodable([RateTariffRule].self, key: "mesai_tariffs") ?? []
        archivedReports = loadCodable([PayrollArchiveReport].self, key: "mesai_archived_reports") ?? []
        notifications = loadCodable([AppNotification].self, key: "mesai_notifications") ?? []

        evaluateAutomaticReminders()
        evaluateAchievementsAndNotifications()
    }

    static func currentYearMonthString() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM"
        return f.string(from: Date())
    }

    static func todayDateString() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }

    static func currentTimeString() -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f.string(from: Date())
    }

    static func currentDateTimeString() -> String {
        let f = DateFormatter()
        f.dateFormat = "dd.MM.yyyy HH:mm"
        return f.string(from: Date())
    }

    // MARK: - Mesai Başlangıç-Bitiş Sayacı (Özellik 6)
    @discardableResult
    func startOvertime(type: OvertimeType = .weekday) -> Bool {
        guard !activeOvertime.isActive else { return false }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let dateStr = Self.todayDateString()
        let timeStr = Self.currentTimeString()
        let rate = resolveHourlyRate(dateStr: dateStr, type: type)

        activeOvertime = ActiveOvertime(
            isActive: true,
            startTimeEpochMs: nowMs,
            startTimeStr: timeStr,
            hourlyRate: rate,
            overtimeType: type.rawValue,
            dateStr: dateStr,
            breakMinutes: 0
        )
        return true
    }

    func updateActiveBreakMinutes(_ minutes: Int) {
        guard activeOvertime.isActive else { return }
        activeOvertime.breakMinutes = max(0, min(480, minutes))
    }

    func stopOvertime(note: String = "") {
        guard activeOvertime.isActive else { return }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let grossMinutes = max(1, Int((nowMs - activeOvertime.startTimeEpochMs) / 60000))
        let netMinutes = max(1, grossMinutes - max(0, activeOvertime.breakMinutes))
        let totalAmount = (Double(netMinutes) / 60.0) * activeOvertime.hourlyRate

        let record = OvertimeRecord(
            date: activeOvertime.dateStr.isEmpty ? Self.todayDateString() : activeOvertime.dateStr,
            startTimeEpochMs: activeOvertime.startTimeEpochMs,
            endTimeEpochMs: nowMs,
            startTimeStr: activeOvertime.startTimeStr,
            endTimeStr: Self.currentTimeString(),
            durationMinutes: netMinutes,
            breakMinutes: activeOvertime.breakMinutes,
            hourlyRate: activeOvertime.hourlyRate,
            totalAmount: totalAmount,
            overtimeType: activeOvertime.overtimeType,
            note: note
        )
        records.insert(record, at: 0)
        activeOvertime = ActiveOvertime()
    }

    // MARK: - Manuel Mesai Kaydı Ekleme & Düzenleme
    func addManualRecord(
        date: String,
        startTimeStr: String,
        endTimeStr: String,
        breakMinutes: Int,
        type: OvertimeType,
        customRate: Double?,
        note: String
    ) {
        let grossMinutes = Self.calculateDurationMinutes(start: startTimeStr, end: endTimeStr)
        let netMinutes = max(1, grossMinutes - max(0, breakMinutes))
        let rate = customRate ?? resolveHourlyRate(dateStr: date, type: type)
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)

        let record = OvertimeRecord(
            date: date,
            startTimeEpochMs: nowMs - Int64(grossMinutes * 60000),
            endTimeEpochMs: nowMs,
            startTimeStr: startTimeStr,
            endTimeStr: endTimeStr,
            durationMinutes: netMinutes,
            breakMinutes: max(0, breakMinutes),
            hourlyRate: rate,
            totalAmount: (Double(netMinutes) / 60.0) * rate,
            overtimeType: type.rawValue,
            note: note
        )
        records.insert(record, at: 0)
    }

    func deleteRecord(_ id: UUID) {
        records.removeAll { $0.id == id }
    }

    static func calculateDurationMinutes(start: String, end: String) -> Int {
        let sParts = start.split(separator: ":").compactMap { Int($0) }
        let eParts = end.split(separator: ":").compactMap { Int($0) }
        guard sParts.count == 2, eParts.count == 2 else { return 60 }
        let sTotal = sParts[0] * 60 + sParts[1]
        var eTotal = eParts[0] * 60 + eParts[1]
        if eTotal <= sTotal {
            eTotal += 24 * 60 // Gece yarısını geçen vardiya desteği
        }
        return max(1, eTotal - sTotal)
    }

    // MARK: - Çoklu Ücret Tarifesi Motoru (Özellik 5)
    func resolveHourlyRate(dateStr: String, type: OvertimeType) -> Double {
        if let matchingRule = tariffRules.first(where: {
            $0.isActive && dateStr >= $0.startDateStr && dateStr <= $0.endDateStr
        }) {
            return matchingRule.rate(for: type)
        }
        return settings.rate(for: type)
    }

    func hasTariffConflict(_ candidate: RateTariffRule) -> [RateTariffRule] {
        tariffRules.filter { existing in
            existing.id != candidate.id &&
            existing.isActive &&
            !(candidate.endDateStr < existing.startDateStr || candidate.startDateStr > existing.endDateStr)
        }
    }

    // MARK: - Gelir & Gider Bütçe Mutabakatı (Özellik 3 - Çift Sayım Önleme)
    func calculateBudgetSummary(for yearMonth: String) -> MonthlyBudgetSummary {
        let monthRecords = records.filter { $0.date.hasPrefix(yearMonth) }
        let monthTx = budgetTransactions.filter { $0.yearMonth == yearMonth }

        let baseSalary = settings.baseSalary
        let overtimeTotal = monthRecords.reduce(0.0) { $0 + $1.totalAmount }
        let advanceTotal = monthTx
            .filter { $0.type == .income && $0.category == .advance }
            .reduce(0.0) { $0 + $1.amount }

        // Maaş aktarımı (SALARY_TRANSFER) ve mesai geliri (OVERTIME_INCOME) zaten maaş hakedişinde
        // yer aldığı için ikinci kez gelir toplamına eklenmez!
        let extraIncome = monthTx
            .filter {
                $0.type == .income &&
                $0.category != .salaryTransfer &&
                $0.category != .advance &&
                $0.category != .overtimeIncome
            }
            .reduce(0.0) { $0 + $1.amount }

        let netSalaryAfterAdvance = max(0.0, baseSalary + overtimeTotal - advanceTotal)
        let totalReconciledIncome = baseSalary + overtimeTotal + extraIncome
        let totalExpense = monthTx
            .filter { $0.type == .expense }
            .reduce(0.0) { $0 + $1.amount }

        return MonthlyBudgetSummary(
            yearMonth: yearMonth,
            baseSalaryEntitlement: baseSalary,
            overtimeEarningsEntitlement: overtimeTotal,
            advanceReceivedTotal: advanceTotal,
            netRemainingSalaryAfterAdvance: netSalaryAfterAdvance,
            additionalIncomeTotal: extraIncome,
            totalReconciledIncome: totalReconciledIncome,
            totalExpense: totalExpense,
            netRemainingBalance: totalReconciledIncome - totalExpense
        )
    }

    // MARK: - Kişisel Maaş Programlama Raporu Arşivi (Özellik 4)
    @discardableResult
    func createAndSaveMonthlyArchive(for yearMonth: String) -> PayrollArchiveReport {
        let monthRecords = records.filter { $0.date.hasPrefix(yearMonth) }
        let budget = calculateBudgetSummary(for: yearMonth)
        let workingDays = Set(monthRecords.map { $0.date }).count
        let totalNetMinutes = monthRecords.reduce(0) { $0 + $1.durationMinutes }
        let totalBreakMinutes = monthRecords.reduce(0) { $0 + $1.breakMinutes }

        let report = PayrollArchiveReport(
            yearMonth: yearMonth,
            periodLabel: "\(yearMonth) Dönemi",
            reportTitle: "\(yearMonth) Kişisel Maaş Programlama Raporu",
            createdDateStr: Self.currentDateTimeString(),
            snapshotBaseSalary: settings.baseSalary,
            snapshotNormalRate: settings.normalHourlyRate,
            snapshotWeekdayRate: settings.weekdayRate,
            snapshotWeekendRate: settings.weekendRate,
            snapshotNightRate: settings.nightShiftRate,
            snapshotHolidayRate: settings.officialHolidayRate,
            totalWorkingDays: workingDays,
            totalRecordsCount: monthRecords.count,
            totalNetMinutes: totalNetMinutes,
            totalBreakMinutes: totalBreakMinutes,
            totalOvertimeEarnings: budget.overtimeEarningsEntitlement,
            advanceDeducted: budget.advanceReceivedTotal,
            netPayableTotal: budget.netRemainingSalaryAfterAdvance
        )
        archivedReports.insert(report, at: 0)
        return report
    }

    // MARK: - Akıllı Eksik Kayıt Kontrolü (Özellik 7)
    func auditRecords() -> [AuditWarning] {
        let dismissed = Set(settings.dismissedAuditWarningKeysCsv.split(separator: ",").map(String.init))
        var warnings: [AuditWarning] = []

        for r in records {
            if r.endTimeStr.trimmingCharacters(in: .whitespaces).isEmpty {
                let key = "MISSING_END_\(r.id.uuidString)"
                if !dismissed.contains(key) {
                    warnings.append(AuditWarning(
                        warningKey: key,
                        recordId: r.id,
                        dateStr: r.date,
                        title: "Bitiş Saati Girilmemiş Kayıt",
                        description: "\(r.date) tarihli mesai kaydında bitiş saati eksik görünüyor."
                    ))
                }
            }
            if r.durationMinutes > 720 {
                let key = "LONG_SHIFT_\(r.id.uuidString)"
                if !dismissed.contains(key) {
                    warnings.append(AuditWarning(
                        warningKey: key,
                        recordId: r.id,
                        dateStr: r.date,
                        title: "Olağan Dışı Uzun Çalışma Süresi",
                        description: "\(r.date) tarihinde \(r.durationMinutes / 60) saat \(r.durationMinutes % 60) dk mesai kaydedilmiş."
                    ))
                }
            }
        }
        return warnings
    }

    func dismissWarning(_ key: String) {
        var current = Set(settings.dismissedAuditWarningKeysCsv.split(separator: ",").map(String.init))
        current.insert(key)
        settings.dismissedAuditWarningKeysCsv = current.joined(separator: ",")
    }

    // MARK: - Bildirim Merkezi & Tekilleştirme (Özellik 2 & 8 / 65)
    @discardableResult
    func recordNotification(
        eventKey: String,
        type: NotificationType,
        title: String,
        message: String,
        targetTabIndex: Int = 0
    ) -> Bool {
        guard !notifications.contains(where: { $0.eventKey == eventKey }) else { return false }
        let item = AppNotification(
            eventKey: eventKey,
            type: type,
            title: title,
            message: message,
            targetTabIndex: targetTabIndex,
            dateTimeStr: Self.currentDateTimeString(),
            isRead: false
        )
        notifications.insert(item, at: 0)

        if settings.notificationsEnabled {
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = message
            let req = UNNotificationRequest(identifier: eventKey, content: content, trigger: nil)
            UNUserNotificationCenter.current().add(req, withCompletionHandler: nil)
        }
        return true
    }

    func evaluateAutomaticReminders() {
        let today = Self.todayDateString()
        if settings.reminderOvertimeLogEnabled && !records.contains(where: { $0.date == today }) {
            recordNotification(
                eventKey: "DAILY_LOG_\(today)",
                type: .overtimeReminder,
                title: "Günlük Mesai Kaydı Hatırlatması",
                message: "Bugünkü mesai kaydınızı veya aktif mesai sayacınızı kontrol etmeyi unutmayın.",
                targetTabIndex: 0
            )
        }
    }

    // MARK: - Başarı ve Kilometre Taşları (Özellik 9 / 68)
    func calculateAchievements() -> [AchievementBadge] {
        // Aynı gün birden fazla kayıt yalnızca 1 farklı çalışma günü sayılır!
        let distinctDays = Array(Set(records.map { $0.date })).sorted()
        let distinctDaysCount = distinctDays.count
        let ym = selectedYearMonth
        let monthDaysCount = Set(records.filter { $0.date.hasPrefix(ym) }.map { $0.date }).count

        return [
            AchievementBadge(
                id: "FIRST_VALID_RECORD",
                title: "İlk Mesai Kaydı",
                conditionDescription: "İlk doğrulanmış mesai kaydınızı oluşturun.",
                category: "Kayıt Düzeni",
                isUnlocked: !records.isEmpty,
                unlockedDateStr: distinctDays.first,
                currentProgress: min(1, records.count),
                targetProgress: 1
            ),
            AchievementBadge(
                id: "DISTINCT_DAYS_7",
                title: "7 Farklı Çalışma Günü",
                conditionDescription: "7 farklı takvim gününde mesai kaydı oluşturun.",
                category: "Kilometre Taşı",
                isUnlocked: distinctDaysCount >= 7,
                unlockedDateStr: distinctDaysCount >= 7 ? distinctDays[6] : nil,
                currentProgress: min(7, distinctDaysCount),
                targetProgress: 7
            ),
            AchievementBadge(
                id: "DISTINCT_DAYS_30",
                title: "30 Farklı Çalışma Günü",
                conditionDescription: "30 farklı takvim gününde düzenli mesai kaydı oluşturun.",
                category: "Kilometre Taşı",
                isUnlocked: distinctDaysCount >= 30,
                unlockedDateStr: distinctDaysCount >= 30 ? distinctDays[29] : nil,
                currentProgress: min(30, distinctDaysCount),
                targetProgress: 30
            ),
            AchievementBadge(
                id: "DISTINCT_DAYS_100",
                title: "100 Farklı Çalışma Günü",
                conditionDescription: "100 farklı takvim gününde çalışma kaydı geçmişine ulaşın.",
                category: "Kilometre Taşı",
                isUnlocked: distinctDaysCount >= 100,
                unlockedDateStr: distinctDaysCount >= 100 ? distinctDays[99] : nil,
                currentProgress: min(100, distinctDaysCount),
                targetProgress: 100
            ),
            AchievementBadge(
                id: "FIRST_MONTHLY_REPORT",
                title: "İlk Aylık Çalışma Raporu",
                conditionDescription: "Kişisel Maaş Programlama Raporu arşivine ilk aylık raporunuzu kaydedin.",
                category: "Raporlama & Arşiv",
                isUnlocked: !archivedReports.isEmpty,
                unlockedDateStr: archivedReports.last?.createdDateStr,
                currentProgress: min(1, archivedReports.count),
                targetProgress: 1
            ),
            AchievementBadge(
                id: "PERSONAL_GOAL_ACHIEVER",
                title: "Aylık Çalışma Hedefi",
                conditionDescription: "Aylık çalışma günü hedefinizi (\(settings.monthlyTargetDays) gün) tamamlayın.",
                category: "Hedef Takibi",
                isUnlocked: monthDaysCount >= settings.monthlyTargetDays,
                unlockedDateStr: monthDaysCount >= settings.monthlyTargetDays ? Self.todayDateString() : nil,
                currentProgress: min(settings.monthlyTargetDays, monthDaysCount),
                targetProgress: max(1, settings.monthlyTargetDays)
            )
        ]
    }

    private func evaluateAchievementsAndNotifications() {
        guard settings.reminderAchievementEnabled else { return }
        for badge in calculateAchievements() where badge.isUnlocked {
            recordNotification(
                eventKey: "ACHIEVEMENT_\(badge.id)",
                type: .achievementUnlocked,
                title: "Yeni Başarı Kazanıldı: \(badge.title)",
                message: badge.conditionDescription,
                targetTabIndex: 3
            )
        }
    }

    // MARK: - Kalıcı Depolama Yardımcıları
    private func saveCodable<T: Encodable>(_ value: T, key: String) {
        if let data = try? JSONEncoder().encode(value) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    private func loadCodable<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }
}
