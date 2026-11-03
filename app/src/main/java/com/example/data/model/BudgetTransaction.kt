package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BudgetTransactionType(val labelTr: String) {
    INCOME("Gelir"),
    EXPENSE("Gider");

    companion object {
        fun fromString(value: String?): BudgetTransactionType {
            return if (value.equals("EXPENSE", ignoreCase = true) || value.equals("Gider", ignoreCase = true)) {
                EXPENSE
            } else {
                INCOME
            }
        }
    }
}

enum class BudgetCategory(
    val labelTr: String,
    val transactionType: BudgetTransactionType,
    val isSalaryTransfer: Boolean = false,
    val isAdvancePayment: Boolean = false
) {
    // Gelir Kategorileri
    SALARY_TRANSFER("Maaş Aktarımı", BudgetTransactionType.INCOME, isSalaryTransfer = true),
    ADVANCE("Maaş Avansı", BudgetTransactionType.INCOME, isAdvancePayment = true),
    OVERTIME_INCOME("Mesai Geliri", BudgetTransactionType.INCOME),
    BONUS_ADDITIONAL("Prim / Ek Gelir", BudgetTransactionType.INCOME),
    OTHER_INCOME("Diğer Gelir", BudgetTransactionType.INCOME),

    // Gider Kategorileri
    RENT("Kira", BudgetTransactionType.EXPENSE),
    BILL("Fatura (Elektrik/Su/İnternet)", BudgetTransactionType.EXPENSE),
    DEBT_LOAN("Borç / Kredi Ödemesi", BudgetTransactionType.EXPENSE),
    GROCERY_FOOD("Market & Gıda", BudgetTransactionType.EXPENSE),
    TRANSPORT("Ulaşım & Yakıt", BudgetTransactionType.EXPENSE),
    PERSONAL("Kişisel Harcama", BudgetTransactionType.EXPENSE),
    OTHER_EXPENSE("Diğer Gider", BudgetTransactionType.EXPENSE);

    companion object {
        fun fromName(name: String?): BudgetCategory {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: OTHER_EXPENSE
        }

        fun incomeCategories(): List<BudgetCategory> =
            entries.filter { it.transactionType == BudgetTransactionType.INCOME }

        fun expenseCategories(): List<BudgetCategory> =
            entries.filter { it.transactionType == BudgetTransactionType.EXPENSE }
    }
}

@Entity(
    tableName = "budget_transactions",
    indices = [
        Index(value = ["yearMonth"]),
        Index(value = ["type", "category"])
    ]
)
data class BudgetTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: String, // "INCOME" veya "EXPENSE"
    val category: String, // BudgetCategory.name
    val dateStr: String, // "yyyy-MM-dd"
    val yearMonth: String, // "yyyy-MM"
    val note: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
