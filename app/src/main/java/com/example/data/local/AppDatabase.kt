package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ActiveOvertime
import com.example.data.model.AppNotification
import com.example.data.model.AppSettings
import com.example.data.model.BudgetTransaction
import com.example.data.model.OvertimeRecord
import com.example.data.model.PayrollArchiveReport
import com.example.data.model.PayrollDocument
import com.example.data.model.RateTariffRule

@Database(
    entities = [
        OvertimeRecord::class,
        AppSettings::class,
        ActiveOvertime::class,
        com.example.data.model.SupportPayment::class,
        PayrollDocument::class,
        BudgetTransaction::class,
        RateTariffRule::class,
        PayrollArchiveReport::class,
        AppNotification::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun overtimeDao(): OvertimeDao
    abstract fun settingsDao(): SettingsDao
    abstract fun activeOvertimeDao(): ActiveOvertimeDao
    abstract fun supportPaymentDao(): SupportPaymentDao
    abstract fun payrollDocumentDao(): PayrollDocumentDao
    abstract fun budgetDao(): BudgetDao
    abstract fun rateTariffDao(): RateTariffDao
    abstract fun payrollArchiveDao(): PayrollArchiveDao
    abstract fun notificationCenterDao(): NotificationCenterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun safeExec(db: SupportSQLiteDatabase, sql: String) {
            try {
                db.execSQL(sql)
            } catch (_: Exception) {
                // Column/table/index already exists
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN employeeName TEXT NOT NULL DEFAULT ''")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN workplaceName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS support_payments (
                        paymentId TEXT NOT NULL PRIMARY KEY,
                        iyzicoToken TEXT NOT NULL DEFAULT '',
                        amount REAL NOT NULL,
                        currency TEXT NOT NULL DEFAULT 'TRY',
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        provider TEXT NOT NULL DEFAULT 'iyzico',
                        providerTransactionId TEXT NOT NULL DEFAULT '',
                        createdAtEpochMs INTEGER NOT NULL,
                        completedAtEpochMs INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                safeExec(db, "ALTER TABLE support_payments ADD COLUMN iyzicoToken TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN baseSalary REAL NOT NULL DEFAULT 0.0")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN salaryDayOfMonth INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN monthlyWorkHours REAL NOT NULL DEFAULT 225.0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN monthlyTargetDays INTEGER NOT NULL DEFAULT 10")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN monthlyTargetHours REAL NOT NULL DEFAULT 20.0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payroll_documents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ownerAccountId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        documentType TEXT NOT NULL DEFAULT 'PAYSLIP',
                        yearMonth TEXT NOT NULL,
                        documentDate TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        fileName TEXT NOT NULL,
                        storedRelativePath TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        fileSizeBytes INTEGER NOT NULL,
                        sha256Checksum TEXT NOT NULL DEFAULT '',
                        createdAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_payroll_documents_ownerAccountId_yearMonth ON payroll_documents (ownerAccountId, yearMonth)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_payroll_documents_ownerAccountId_documentType ON payroll_documents (ownerAccountId, documentType)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Mola süresi sütunları (mevcut verileri koruyarak varsayılan 0 ile eklenir)
                safeExec(db, "ALTER TABLE overtime_records ADD COLUMN breakMinutes INTEGER NOT NULL DEFAULT 0")
                safeExec(db, "ALTER TABLE active_overtime ADD COLUMN breakMinutes INTEGER NOT NULL DEFAULT 0")

                // 2. AppSettings çoklu tarife ve otomatik hatırlatma sütunları
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN normalHourlyRate REAL NOT NULL DEFAULT 150.0")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN nightShiftRate REAL NOT NULL DEFAULT 250.0")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN officialHolidayRate REAL NOT NULL DEFAULT 300.0")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN reminderOvertimeEntryEnabled INTEGER NOT NULL DEFAULT 1")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN reminderSalaryDayEnabled INTEGER NOT NULL DEFAULT 1")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN reminderShiftStartEnabled INTEGER NOT NULL DEFAULT 1")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN reminderMissingRecordEnabled INTEGER NOT NULL DEFAULT 1")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN reminderMonthlySummaryEnabled INTEGER NOT NULL DEFAULT 1")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN reminderAchievementEnabled INTEGER NOT NULL DEFAULT 1")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN reminderTimeStr TEXT NOT NULL DEFAULT '20:30'")
                safeExec(db, "ALTER TABLE app_settings ADD COLUMN shiftReminderLeadMinutes INTEGER NOT NULL DEFAULT 30")

                // 3. Gelir ve Gider Takibi tablosu
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS budget_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        category TEXT NOT NULL,
                        dateStr TEXT NOT NULL,
                        yearMonth TEXT NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        createdAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budget_transactions_yearMonth ON budget_transactions (yearMonth)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_budget_transactions_type_category ON budget_transactions (type, category)")

                // 4. Çoklu Ücret Tarifesi Kuralları tablosu
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rate_tariff_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        startDateStr TEXT NOT NULL,
                        endDateStr TEXT NOT NULL,
                        normalHourlyRate REAL NOT NULL,
                        weekdayOvertimeRate REAL NOT NULL,
                        weekendRate REAL NOT NULL,
                        nightShiftRate REAL NOT NULL,
                        officialHolidayRate REAL NOT NULL,
                        applyDaysCsv TEXT NOT NULL DEFAULT '1,2,3,4,5,6,7',
                        nightStartHour INTEGER NOT NULL DEFAULT 20,
                        nightEndHour INTEGER NOT NULL DEFAULT 6,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rate_tariff_rules_startDateStr_endDateStr ON rate_tariff_rules (startDateStr, endDateStr)")

                // 5. Kişisel Maaş Programlama Raporu Arşivi (Snapshot) tablosu
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payroll_archive_reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        yearMonth TEXT NOT NULL,
                        reportTitle TEXT NOT NULL,
                        createdDateStr TEXT NOT NULL,
                        employeeName TEXT NOT NULL DEFAULT '',
                        workplaceName TEXT NOT NULL DEFAULT '',
                        snapshotBaseSalary REAL NOT NULL DEFAULT 0.0,
                        snapshotNormalRate REAL NOT NULL DEFAULT 0.0,
                        snapshotWeekdayRate REAL NOT NULL DEFAULT 0.0,
                        snapshotWeekendRate REAL NOT NULL DEFAULT 0.0,
                        snapshotNightRate REAL NOT NULL DEFAULT 0.0,
                        snapshotHolidayRate REAL NOT NULL DEFAULT 0.0,
                        appliedTariffSummary TEXT NOT NULL DEFAULT '',
                        totalRecordsCount INTEGER NOT NULL DEFAULT 0,
                        totalWorkingDays INTEGER NOT NULL DEFAULT 0,
                        totalNetWorkMinutes INTEGER NOT NULL DEFAULT 0,
                        totalBreakMinutes INTEGER NOT NULL DEFAULT 0,
                        totalOvertimeEarnings REAL NOT NULL DEFAULT 0.0,
                        totalAdvanceDeducted REAL NOT NULL DEFAULT 0.0,
                        netTotalPayable REAL NOT NULL DEFAULT 0.0,
                        recordsSnapshotJson TEXT NOT NULL DEFAULT '[]',
                        pdfFilePath TEXT NOT NULL DEFAULT '',
                        csvFilePath TEXT NOT NULL DEFAULT '',
                        createdAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_archive_reports_yearMonth ON payroll_archive_reports (yearMonth)")

                // 6. Bildirim Merkezi tablosu
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_notifications (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventKey TEXT NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        targetRoute TEXT NOT NULL DEFAULT 'HOME',
                        dateTimeStr TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        isRead INTEGER NOT NULL DEFAULT 0,
                        wasPostedToSystem INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_app_notifications_eventKey ON app_notifications (eventKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_notifications_isRead_createdAtEpochMs ON app_notifications (isRead, createdAtEpochMs)")
            }
        }

        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_2_7 = object : Migration(2, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_3_7 = object : Migration(3, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_4_7 = object : Migration(4, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mesai_takip_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_1_4, MIGRATION_2_4, MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5,
                        MIGRATION_1_6, MIGRATION_2_6, MIGRATION_3_6, MIGRATION_4_6,
                        MIGRATION_1_7, MIGRATION_2_7, MIGRATION_3_7, MIGRATION_4_7, MIGRATION_5_7
                    )
                    .fallbackToDestructiveMigrationOnDowngrade(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
