package com.example.util

import com.example.data.model.AppSettings
import com.example.data.model.BudgetCategory
import com.example.data.model.BudgetTransaction
import com.example.data.model.BudgetTransactionType
import com.example.data.model.OvertimeRecord
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.round

data class CategoryTotalItem(
    val category: BudgetCategory,
    val totalAmount: Double,
    val count: Int
)

data class MonthlyBudgetSummary(
    val yearMonth: YearMonth,
    // Maaş ve Mesai Hakediş Kırılımı
    val baseSalaryEntitlement: Double,
    val overtimeEarningsEntitlement: Double,
    val grossSalaryAndOvertimeEntitlement: Double,
    val manualSalaryTransfersTotal: Double,
    val advanceReceivedTotal: Double,
    val netRemainingSalaryAfterAdvance: Double,
    // Ek Bütçe Gelirleri (Maaş ve Avans dışındaki prim/ek gelirler)
    val additionalIncomeTotal: Double,
    // Çift Sayım Önlenmiş Toplam Aylık Gelir
    val totalReconciledIncome: Double,
    // Giderler ve Kalan Tutar
    val totalExpense: Double,
    val netRemainingBalance: Double,
    // Kategori özetleri
    val incomeByCategory: List<CategoryTotalItem>,
    val expenseByCategory: List<CategoryTotalItem>,
    // Geçmiş ay ile karşılaştırma
    val previousMonthIncome: Double,
    val previousMonthExpense: Double,
    val previousMonthNetBalance: Double,
    val incomeDiffFromPreviousMonth: Double,
    val expenseDiffFromPreviousMonth: Double,
    val netBalanceDiffFromPreviousMonth: Double
)

object BudgetCalculator {

    /**
     * Maaş hesabı ile kişisel bütçe kayıtlarını birbirinden ayırarak ve
     * aynı maaşın veya avansın iki kez gelir sayılmasını (double counting) engelleyerek
     * aylık gelir-gider özetini hesaplar.
     *
     * Kurallar:
     * 1. Kullanıcının o ayki toplam maaş hakedişi = Taban Maaş (`baseSalary`) + Fazla Mesai (`overtimeEarnings`).
     * 2. Eğer kullanıcı bütçeye manuel `SALARY_TRANSFER` ("Maaş Aktarımı") girdiyse,
     *    taban maaş ile manuel maaş aktarımı iki kez toplanmaz (`max(baseSalary, manualSalaryTransfersTotal)`).
     * 3. Eğer kullanıcı bütçeye `ADVANCE` ("Maaş Avansı") girdiyse, avans maaş hakedişinin
     *    önden ödenmiş bir parçasıdır; maaş gününde kalan ödenecek maaştan düşülür
     *    (`netRemainingSalaryAfterAdvance = max(0, effectiveSalaryAndOvertime - advanceReceivedTotal)`).
     *    Böylece `Toplam Maaş Kaynaklı Gelir = netRemainingSalaryAfterAdvance + advanceReceivedTotal`
     *    olur ve avans asla maaşın üstüne ikinci kez eklenmez!
     */
    fun calculateMonthlySummary(
        yearMonth: YearMonth,
        allTransactions: List<BudgetTransaction>,
        allOvertimeRecords: List<OvertimeRecord>,
        settings: AppSettings
    ): MonthlyBudgetSummary {
        val currentYmStr = yearMonth.toString()
        val prevYm = yearMonth.minusMonths(1)
        val prevYmStr = prevYm.toString()

        val monthTx = allTransactions.filter { it.yearMonth == currentYmStr }
        val monthOvertime = allOvertimeRecords.filter { it.date.startsWith(currentYmStr) }

        val prevMonthTx = allTransactions.filter { it.yearMonth == prevYmStr }
        val prevMonthOvertime = allOvertimeRecords.filter { it.date.startsWith(prevYmStr) }

        val currentCalc = computeSingleMonth(monthTx, monthOvertime, settings)
        val prevCalc = computeSingleMonth(prevMonthTx, prevMonthOvertime, settings)

        val incomeMap = mutableMapOf<BudgetCategory, Pair<Double, Int>>()
        val expenseMap = mutableMapOf<BudgetCategory, Pair<Double, Int>>()

        for (tx in monthTx) {
            val cat = BudgetCategory.fromName(tx.category)
            val type = BudgetTransactionType.fromString(tx.type)
            if (type == BudgetTransactionType.INCOME) {
                val cur = incomeMap[cat] ?: (0.0 to 0)
                incomeMap[cat] = round2(cur.first + tx.amount) to (cur.second + 1)
            } else {
                val cur = expenseMap[cat] ?: (0.0 to 0)
                expenseMap[cat] = round2(cur.first + tx.amount) to (cur.second + 1)
            }
        }

        val incomeByCategory = incomeMap.entries
            .map { CategoryTotalItem(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.totalAmount }

        val expenseByCategory = expenseMap.entries
            .map { CategoryTotalItem(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.totalAmount }

        return MonthlyBudgetSummary(
            yearMonth = yearMonth,
            baseSalaryEntitlement = currentCalc.baseSalary,
            overtimeEarningsEntitlement = currentCalc.overtimeEarnings,
            grossSalaryAndOvertimeEntitlement = currentCalc.effectiveSalaryAndOvertime,
            manualSalaryTransfersTotal = currentCalc.manualSalaryTransfer,
            advanceReceivedTotal = currentCalc.advanceReceived,
            netRemainingSalaryAfterAdvance = currentCalc.netRemainingSalaryAfterAdvance,
            additionalIncomeTotal = currentCalc.additionalIncome,
            totalReconciledIncome = currentCalc.totalReconciledIncome,
            totalExpense = currentCalc.totalExpense,
            netRemainingBalance = currentCalc.netBalance,
            incomeByCategory = incomeByCategory,
            expenseByCategory = expenseByCategory,
            previousMonthIncome = prevCalc.totalReconciledIncome,
            previousMonthExpense = prevCalc.totalExpense,
            previousMonthNetBalance = prevCalc.netBalance,
            incomeDiffFromPreviousMonth = round2(currentCalc.totalReconciledIncome - prevCalc.totalReconciledIncome),
            expenseDiffFromPreviousMonth = round2(currentCalc.totalExpense - prevCalc.totalExpense),
            netBalanceDiffFromPreviousMonth = round2(currentCalc.netBalance - prevCalc.netBalance)
        )
    }

    private data class SingleMonthCore(
        val baseSalary: Double,
        val overtimeEarnings: Double,
        val manualSalaryTransfer: Double,
        val effectiveSalaryAndOvertime: Double,
        val advanceReceived: Double,
        val netRemainingSalaryAfterAdvance: Double,
        val additionalIncome: Double,
        val totalReconciledIncome: Double,
        val totalExpense: Double,
        val netBalance: Double
    )

    private fun computeSingleMonth(
        monthTx: List<BudgetTransaction>,
        monthOvertime: List<OvertimeRecord>,
        settings: AppSettings
    ): SingleMonthCore {
        val baseSalary = max(0.0, settings.baseSalary)
        val overtimeRecordsTotal = round2(monthOvertime.sumOf { max(0.0, it.totalAmount) })

        val incomeTx = monthTx.filter {
            BudgetTransactionType.fromString(it.type) == BudgetTransactionType.INCOME
        }
        val expenseTx = monthTx.filter {
            BudgetTransactionType.fromString(it.type) == BudgetTransactionType.EXPENSE
        }

        val manualSalaryTransfer = round2(
            incomeTx.filter { BudgetCategory.fromName(it.category) == BudgetCategory.SALARY_TRANSFER }
                .sumOf { max(0.0, it.amount) }
        )
        val advanceReceived = round2(
            incomeTx.filter { BudgetCategory.fromName(it.category) == BudgetCategory.ADVANCE }
                .sumOf { max(0.0, it.amount) }
        )
        val manualOvertimeIncome = round2(
            incomeTx.filter { BudgetCategory.fromName(it.category) == BudgetCategory.OVERTIME_INCOME }
                .sumOf { max(0.0, it.amount) }
        )
        val additionalIncome = round2(
            incomeTx.filter {
                val cat = BudgetCategory.fromName(it.category)
                cat != BudgetCategory.SALARY_TRANSFER &&
                    cat != BudgetCategory.ADVANCE &&
                    cat != BudgetCategory.OVERTIME_INCOME
            }.sumOf { max(0.0, it.amount) }
        )

        // Çift sayımı önle:
        // Taban maaş hem ayarlarda hem manuel SALARY_TRANSFER olarak girildiyse büyük olanı baz al (2 kez toplama!)
        val effectiveBaseSalary = max(baseSalary, manualSalaryTransfer)
        // Mesai geliri hem mesai kayıtlarında hem manuel OVERTIME_INCOME olarak girildiyse büyük olanı baz al
        val effectiveOvertime = max(overtimeRecordsTotal, manualOvertimeIncome)

        val effectiveSalaryAndOvertime = round2(effectiveBaseSalary + effectiveOvertime)

        // Avans maaş hakedişinden düşülür; kalan ödenecek maaş + alınan avans = toplam maaş/avans girişi
        val netRemainingSalaryAfterAdvance = round2(max(0.0, effectiveSalaryAndOvertime - advanceReceived))
        val reconciledSalaryAndAdvance = round2(netRemainingSalaryAfterAdvance + advanceReceived)

        val totalReconciledIncome = round2(reconciledSalaryAndAdvance + additionalIncome)
        val totalExpense = round2(expenseTx.sumOf { max(0.0, it.amount) })
        val netBalance = round2(totalReconciledIncome - totalExpense)

        return SingleMonthCore(
            baseSalary = baseSalary,
            overtimeEarnings = effectiveOvertime,
            manualSalaryTransfer = manualSalaryTransfer,
            effectiveSalaryAndOvertime = effectiveSalaryAndOvertime,
            advanceReceived = advanceReceived,
            netRemainingSalaryAfterAdvance = netRemainingSalaryAfterAdvance,
            additionalIncome = additionalIncome,
            totalReconciledIncome = totalReconciledIncome,
            totalExpense = totalExpense,
            netBalance = netBalance
        )
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0
}
