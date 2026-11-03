import Foundation

// MARK: - Mesai Türleri (Çoklu Ücret Tarifesi Desteği)
enum OvertimeType: String, Codable, CaseIterable, Identifiable {
    case normal = "NORMAL"
    case weekday = "WEEKDAY"
    case weekend = "WEEKEND"
    case nightShift = "NIGHT_SHIFT"
    case officialHoliday = "OFFICIAL_HOLIDAY"

    var id: String { rawValue }

    var labelTr: String {
        switch self {
        case .normal: return "Normal Mesai"
        case .weekday: return "Hafta İçi Fazla Mesai"
        case .weekend: return "Hafta Sonu Mesaisi"
        case .nightShift: return "Gece Vardiyası"
        case .officialHoliday: return "Resmî Tatil Mesaisi"
        }
    }
}

// MARK: - Mesai Kaydı
struct OvertimeRecord: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var date: String // "yyyy-MM-dd"
    var startTimeEpochMs: Int64
    var endTimeEpochMs: Int64
    var startTimeStr: String // "HH:mm"
    var endTimeStr: String // "HH:mm"
    var durationMinutes: Int // Net çalışma süresi (mola düşülmüş)
    var breakMinutes: Int = 0 // Mola süresi (dk)
    var hourlyRate: Double // Kayıt anındaki saatlik ücret (sonradan tarife değişse de bozulmaz)
    var totalAmount: Double
    var overtimeType: String
    var note: String = ""

    var grossDurationMinutes: Int {
        durationMinutes + max(0, breakMinutes)
    }
}

// MARK: - Aktif Mesai Sayacı (Kalıcı Depolama)
struct ActiveOvertime: Codable, Equatable {
    var isActive: Bool = false
    var startTimeEpochMs: Int64 = 0
    var startTimeStr: String = ""
    var hourlyRate: Double = 0.0
    var overtimeType: String = OvertimeType.weekday.rawValue
    var dateStr: String = ""
    var breakMinutes: Int = 0
}

// MARK: - Uygulama Ayarları & Otomatik Hatırlatmalar
struct AppSettings: Codable, Equatable {
    var baseSalary: Double = 35000.0
    var normalHourlyRate: Double = 155.0
    var weekdayRate: Double = 235.0
    var weekendRate: Double = 310.0
    var nightShiftRate: Double = 275.0
    var officialHolidayRate: Double = 465.0
    var salaryPaymentDay: Int = 15
    var expectedDailyWorkHours: Double = 8.0
    var monthlyTargetDays: Int = 10
    var monthlyTargetHours: Double = 20.0

    // Otomatik Hatırlatmalar (Özellik 2)
    var notificationsEnabled: Bool = true
    var reminderOvertimeLogEnabled: Bool = true
    var reminderSalaryDayEnabled: Bool = true
    var reminderShiftStartEnabled: Bool = true
    var reminderMissingRecordEnabled: Bool = true
    var reminderAchievementEnabled: Bool = true
    var reminderTimeStr: String = "20:00"
    var shiftReminderLeadMinutes: Int = 30
    var autoStartTimeStr: String = "18:00"
    var dismissedAuditWarningKeysCsv: String = ""

    func rate(for type: OvertimeType) -> Double {
        switch type {
        case .normal: return normalHourlyRate > 0 ? normalHourlyRate : weekdayRate
        case .weekday: return weekdayRate
        case .weekend: return weekendRate
        case .nightShift: return nightShiftRate > 0 ? nightShiftRate : weekdayRate
        case .officialHoliday: return officialHolidayRate > 0 ? officialHolidayRate : weekendRate
        }
    }
}

// MARK: - Bütçe Gelir & Gider Takibi (Özellik 3)
enum BudgetTransactionType: String, Codable, CaseIterable {
    case income = "INCOME"
    case expense = "EXPENSE"

    var labelTr: String {
        self == .income ? "Gelir" : "Gider"
    }
}

enum BudgetCategory: String, Codable, CaseIterable, Identifiable {
    case salaryTransfer = "SALARY_TRANSFER"
    case advance = "ADVANCE"
    case overtimeIncome = "OVERTIME_INCOME"
    case bonusAdditional = "BONUS_ADDITIONAL"
    case otherIncome = "OTHER_INCOME"

    case rent = "RENT"
    case bill = "BILL"
    case debtLoan = "DEBT_LOAN"
    case groceryFood = "GROCERY_FOOD"
    case transport = "TRANSPORT"
    case personal = "PERSONAL"
    case otherExpense = "OTHER_EXPENSE"

    var id: String { rawValue }

    var labelTr: String {
        switch self {
        case .salaryTransfer: return "Maaş Aktarımı"
        case .advance: return "Maaş Avansı"
        case .overtimeIncome: return "Mesai Geliri"
        case .bonusAdditional: return "Prim / Ek Gelir"
        case .otherIncome: return "Diğer Gelir"
        case .rent: return "Kira"
        case .bill: return "Fatura (Elektrik/Su/İnternet)"
        case .debtLoan: return "Borç / Kredi Ödemesi"
        case .groceryFood: return "Market & Gıda"
        case .transport: return "Ulaşım & Yakıt"
        case .personal: return "Kişisel Harcama"
        case .otherExpense: return "Diğer Gider"
        }
    }

    var transactionType: BudgetTransactionType {
        switch self {
        case .salaryTransfer, .advance, .overtimeIncome, .bonusAdditional, .otherIncome:
            return .income
        default:
            return .expense
        }
    }
}

struct BudgetTransaction: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var title: String
    var amount: Double
    var type: BudgetTransactionType
    var category: BudgetCategory
    var dateStr: String // "yyyy-MM-dd"
    var yearMonth: String // "yyyy-MM"
    var note: String = ""
}

struct MonthlyBudgetSummary {
    let yearMonth: String
    let baseSalaryEntitlement: Double
    let overtimeEarningsEntitlement: Double
    let advanceReceivedTotal: Double
    let netRemainingSalaryAfterAdvance: Double
    let additionalIncomeTotal: Double
    let totalReconciledIncome: Double
    let totalExpense: Double
    let netRemainingBalance: Double
}

// MARK: - Dönemlik Ücret Tarifesi (Özellik 5)
struct RateTariffRule: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var name: String
    var startDateStr: String // "yyyy-MM-dd"
    var endDateStr: String // "yyyy-MM-dd"
    var normalHourlyRate: Double
    var weekdayOvertimeRate: Double
    var weekendRate: Double
    var nightShiftRate: Double
    var officialHolidayRate: Double
    var isActive: Bool = true

    func rate(for type: OvertimeType) -> Double {
        switch type {
        case .normal: return normalHourlyRate
        case .weekday: return weekdayOvertimeRate
        case .weekend: return weekendRate
        case .nightShift: return nightShiftRate
        case .officialHoliday: return officialHolidayRate
        }
    }
}

// MARK: - Kişisel Maaş Programlama Raporu Arşivi (Özellik 4)
struct PayrollArchiveReport: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var yearMonth: String
    var periodLabel: String
    var reportTitle: String
    var createdDateStr: String
    var snapshotBaseSalary: Double
    var snapshotNormalRate: Double
    var snapshotWeekdayRate: Double
    var snapshotWeekendRate: Double
    var snapshotNightRate: Double
    var snapshotHolidayRate: Double
    var totalWorkingDays: Int
    var totalRecordsCount: Int
    var totalNetMinutes: Int
    var totalBreakMinutes: Int
    var totalOvertimeEarnings: Double
    var advanceDeducted: Double
    var netPayableTotal: Double
}

// MARK: - Bildirim Merkezi (Özellik 8 / 65)
enum NotificationType: String, Codable, CaseIterable, Identifiable {
    case overtimeReminder = "OVERTIME_REMINDER"
    case salaryReminder = "SALARY_REMINDER"
    case upcomingShift = "UPCOMING_SHIFT"
    case missingRecordAlert = "MISSING_RECORD_ALERT"
    case monthlySummary = "MONTHLY_SUMMARY"
    case achievementUnlocked = "ACHIEVEMENT_UNLOCKED"
    case systemBackup = "SYSTEM_BACKUP"

    var id: String { rawValue }

    var badgeText: String {
        switch self {
        case .overtimeReminder: return "Mesai"
        case .salaryReminder: return "Maaş Günü"
        case .upcomingShift: return "Vardiya"
        case .missingRecordAlert: return "Kayıt Denetimi"
        case .monthlySummary: return "Aylık Özet"
        case .achievementUnlocked: return "Başarı"
        case .systemBackup: return "Sistem"
        }
    }
}

struct AppNotification: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var eventKey: String
    var type: NotificationType
    var title: String
    var message: String
    var targetTabIndex: Int = 0
    var dateTimeStr: String
    var isRead: Bool = false
}

// MARK: - Akıllı Eksik Kayıt Kontrolü (Özellik 7)
struct AuditWarning: Identifiable, Equatable {
    var id: String { warningKey }
    let warningKey: String
    let recordId: UUID?
    let dateStr: String
    let title: String
    let description: String
}

// MARK: - Başarı ve Kilometre Taşları (Özellik 9 / 68)
struct AchievementBadge: Identifiable, Equatable {
    let id: String
    let title: String
    let conditionDescription: String
    let category: String
    let isUnlocked: Bool
    let unlockedDateStr: String?
    let currentProgress: Int
    let targetProgress: Int

    var progressPercent: Int {
        guard targetProgress > 0 else { return 0 }
        return min(100, Int((Double(currentProgress) / Double(targetProgress)) * 100.0))
    }
}
