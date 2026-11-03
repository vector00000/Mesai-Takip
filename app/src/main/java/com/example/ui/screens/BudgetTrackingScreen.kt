package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AppSettings
import com.example.data.model.BudgetCategory
import com.example.data.model.BudgetTransaction
import com.example.data.model.BudgetTransactionType
import com.example.data.model.OvertimeRecord
import com.example.ui.theme.MoneyEmerald
import com.example.util.BudgetCalculator
import com.example.util.TimeUtils
import java.time.YearMonth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetTrackingScreen(
    selectedYearMonth: YearMonth,
    transactions: List<BudgetTransaction>,
    overtimeRecords: List<OvertimeRecord>,
    settings: AppSettings,
    onMonthChange: (Long) -> Unit,
    onAddTransaction: (String, Double, BudgetTransactionType, BudgetCategory, String, String) -> Unit,
    onUpdateTransaction: (BudgetTransaction) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val summary = remember(selectedYearMonth, transactions, overtimeRecords, settings) {
        BudgetCalculator.calculateMonthlySummary(
            yearMonth = selectedYearMonth,
            allTransactions = transactions,
            allOvertimeRecords = overtimeRecords,
            settings = settings
        )
    }

    val currentMonthTransactions = remember(selectedYearMonth, transactions) {
        val ymStr = selectedYearMonth.toString()
        transactions.filter { it.yearMonth == ymStr }
    }

    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<BudgetTransaction?>(null) }
    var initialAddType by remember { mutableStateOf(BudgetTransactionType.INCOME) }

    if (showAddOrEditDialog) {
        BudgetTransactionDialog(
            initialType = editingTransaction?.let { BudgetTransactionType.fromString(it.type) } ?: initialAddType,
            editingItem = editingTransaction,
            defaultDateStr = "${selectedYearMonth}-15",
            onDismiss = {
                showAddOrEditDialog = false
                editingTransaction = null
            },
            onSave = { title, amount, type, category, dateStr, note ->
                val existing = editingTransaction
                if (existing != null) {
                    onUpdateTransaction(
                        existing.copy(
                            title = title,
                            amount = amount,
                            type = type.name,
                            category = category.name,
                            dateStr = dateStr,
                            yearMonth = if (dateStr.length >= 7) dateStr.take(7) else selectedYearMonth.toString(),
                            note = note
                        )
                    )
                } else {
                    onAddTransaction(title, amount, type, category, dateStr, note)
                }
                showAddOrEditDialog = false
                editingTransaction = null
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("screen_budget_tracking"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            // Üst Çubuk
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_from_budget")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri Dön")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gelir ve Gider Takibi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Maaş, avans, ek gelir ve kişisel bütçe yönetimi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dönem Seçici
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onMonthChange(-1) }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Önceki Ay")
                        }
                        Text(
                            text = TimeUtils.formatMonthYear(selectedYearMonth),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { onMonthChange(1) }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Sonraki Ay")
                        }
                    }
                }

                // Ana Aylık Gelir / Gider / Kalan Tutar Özeti Kartı
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_budget_monthly_summary"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Kalan Net Tutar (${TimeUtils.formatMonthYear(selectedYearMonth)})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = TimeUtils.formatCurrency(summary.netRemainingBalance),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (summary.netRemainingBalance >= 0) MoneyEmerald else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("text_budget_net_balance")
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MoneyEmerald.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Aylık Toplam Gelir", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = TimeUtils.formatCurrency(summary.totalReconciledIncome),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MoneyEmerald,
                                        modifier = Modifier.testTag("text_budget_total_income")
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Aylık Toplam Gider", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = TimeUtils.formatCurrency(summary.totalExpense),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.testTag("text_budget_total_expense")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Maaş Hesabı & Avans Mahsubu (Çift Sayım Koruması)
                        Text(
                            text = "Maaş Hakedişi & Avans Mahsup Özeti (Çift Sayım Önleme)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        BudgetRowItem("Taban Maaş Hakedişi", TimeUtils.formatCurrency(summary.baseSalaryEntitlement))
                        BudgetRowItem("Fazla Mesai Kazancı", TimeUtils.formatCurrency(summary.overtimeEarningsEntitlement))
                        BudgetRowItem("Alınan Maaş Avansı", TimeUtils.formatCurrency(summary.advanceReceivedTotal))
                        BudgetRowItem("Avans Sonrası Kalan Maaş", TimeUtils.formatCurrency(summary.netRemainingSalaryAfterAdvance))
                        BudgetRowItem("Prim / Diğer Ek Gelirler", TimeUtils.formatCurrency(summary.additionalIncomeTotal))

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Geçmiş Ayla Karşılaştırma: Önceki ay net kalan ${TimeUtils.formatCurrency(summary.previousMonthNetBalance)} (Fark: ${if (summary.netBalanceDiffFromPreviousMonth >= 0) "+" else ""}${TimeUtils.formatCurrency(summary.netBalanceDiffFromPreviousMonth)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Hızlı Gelir / Gider Ekleme Butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            editingTransaction = null
                            initialAddType = BudgetTransactionType.INCOME
                            showAddOrEditDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_add_income")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gelir / Avans Ekle")
                    }
                    OutlinedButton(
                        onClick = {
                            editingTransaction = null
                            initialAddType = BudgetTransactionType.EXPENSE
                            showAddOrEditDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_add_expense")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gider Ekle")
                    }
                }

                // Bütçe Kayıtları Listesi
                Text(
                    text = "Dönem Kayıtları (${currentMonthTransactions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (currentMonthTransactions.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Bu ay için henüz manuel gelir veya gider kaydı eklenmedi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    currentMonthTransactions.forEach { tx ->
                        val isIncome = BudgetTransactionType.fromString(tx.type) == BudgetTransactionType.INCOME
                        val cat = BudgetCategory.fromName(tx.category)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("budget_tx_item_${tx.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isIncome) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isIncome) MoneyEmerald else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${cat.labelTr} • ${tx.dateStr}${if (tx.note.isNotBlank()) " • ${tx.note}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${if (isIncome) "+" else "-"}${TimeUtils.formatCurrency(tx.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isIncome) MoneyEmerald else MaterialTheme.colorScheme.error
                                )
                                IconButton(
                                    onClick = {
                                        editingTransaction = tx
                                        showAddOrEditDialog = true
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Düzenle", modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = { onDeleteTransaction(tx.id) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("btn_delete_budget_${tx.id}")
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Sil",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRowItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetTransactionDialog(
    initialType: BudgetTransactionType,
    editingItem: BudgetTransaction?,
    defaultDateStr: String,
    onDismiss: () -> Unit,
    onSave: (String, Double, BudgetTransactionType, BudgetCategory, String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    val availableCategories = remember(selectedType) {
        if (selectedType == BudgetTransactionType.INCOME) BudgetCategory.incomeCategories()
        else BudgetCategory.expenseCategories()
    }
    var selectedCategory by remember(selectedType) {
        mutableStateOf(
            editingItem?.let { BudgetCategory.fromName(it.category) }
                ?.takeIf { it.transactionType == selectedType }
                ?: availableCategories.first()
        )
    }
    var titleText by remember { mutableStateOf(editingItem?.title ?: "") }
    var amountText by remember { mutableStateOf(editingItem?.amount?.toString() ?: "") }
    var dateText by remember { mutableStateOf(editingItem?.dateStr ?: defaultDateStr) }
    var noteText by remember { mutableStateOf(editingItem?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingItem != null) "Kaydı Düzenle" else "Yeni Gelir / Gider Kaydı",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == BudgetTransactionType.INCOME,
                        onClick = { selectedType = BudgetTransactionType.INCOME },
                        label = { Text("Gelir") }
                    )
                    FilterChip(
                        selected = selectedType == BudgetTransactionType.EXPENSE,
                        onClick = { selectedType = BudgetTransactionType.EXPENSE },
                        label = { Text("Gider") }
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.labelTr, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Başlık (Örn: Kira, Elektrik, Avans)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_budget_title")
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Tutar (TL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_budget_amount")
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Tarih (YYYY-AA-GG)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Açıklama / Not") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onSave(
                            titleText.ifBlank { selectedCategory.labelTr },
                            amt,
                            selectedType,
                            selectedCategory,
                            dateText.ifBlank { TimeUtils.todayDateStr() },
                            noteText
                        )
                    }
                },
                modifier = Modifier.testTag("btn_confirm_save_budget")
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
