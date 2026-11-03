package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import com.example.data.model.OvertimeType
import com.example.ui.components.TimePickerDialog
import com.example.ui.components.findActivity
import com.example.ui.theme.MoneyEmerald
import com.example.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<OvertimeRecord>,
    settings: AppSettings,
    isPremium: Boolean = false,
    onAddRecord: (
        dateStr: String,
        startTimeStr: String,
        endTimeStr: String,
        hourlyRate: Double,
        overtimeType: OvertimeType,
        note: String
    ) -> Unit,
    onUpdateRecord: (
        id: Long,
        dateStr: String,
        startTimeStr: String,
        endTimeStr: String,
        hourlyRate: Double,
        overtimeType: OvertimeType,
        note: String
    ) -> Unit,
    onDeleteRecord: (id: Long) -> Unit,
    onExportAllCsv: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as? com.example.MesaiTakipApp
    val billingManager = app?.billingManager
    val subscriptionStatus by billingManager?.subscriptionStatus?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(com.example.data.billing.SubscriptionStatus.INACTIVE) }
    val formattedPrice by billingManager?.formattedPrice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<String?>(null) }
    val billingPeriodText by billingManager?.billingPeriodText?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf("Aylık (Otomatik Yenilenir)") }

    var showPremiumCsvDialog by remember { mutableStateOf(false) }
    var recordToEdit by remember { mutableStateOf<OvertimeRecord?>(null) }
    var recordToDelete by remember { mutableStateOf<OvertimeRecord?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showPremiumCsvDialog) {
        com.example.ui.components.GooglePlaySubscriptionDialog(
            isPremium = isPremium,
            subscriptionStatus = subscriptionStatus,
            formattedPrice = formattedPrice,
            billingPeriodText = billingPeriodText,
            featureContextTitle = "Tüm Mesai Geçmişini Excel (CSV) Olarak Dışa Aktarma",
            onDismiss = { showPremiumCsvDialog = false },
            onSubscribeClick = {
                val act = context.findActivity()
                if (act != null && billingManager != null) {
                    billingManager.launchSubscriptionFlow(act)
                }
            },
            onManageSubscription = {
                billingManager?.openPlayStoreSubscriptionManagement(context)
            },
            onRestorePurchases = {
                billingManager?.queryActivePurchases(showFeedbackMessage = true)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Geçmiş Mesailer",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (onExportAllCsv != null && records.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (isPremium) {
                                    onExportAllCsv()
                                } else {
                                    showPremiumCsvDialog = true
                                }
                            },
                            modifier = Modifier.testTag("btn_export_all_csv")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TableChart,
                                contentDescription = "Tüm Geçmişi Excel (CSV) Olarak Paylaş",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_overtime")
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Mesai Ekle")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Henüz mesai kaydı bulunmuyor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Otomatik başlatılan ya da manuel eklenen tüm mesaileriniz burada listelenir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(records, key = { it.id }) { record ->
                    OvertimeRecordCard(
                        record = record,
                        onClick = { recordToEdit = record },
                        onDeleteClick = { recordToDelete = record }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Edit Modal Dialog
    if (recordToEdit != null) {
        RecordEditDialog(
            record = recordToEdit!!,
            settings = settings,
            onDismiss = { recordToEdit = null },
            onSave = { date, start, end, rate, type, note ->
                onUpdateRecord(recordToEdit!!.id, date, start, end, rate, type, note)
                recordToEdit = null
            },
            onDelete = {
                val toDelete = recordToEdit
                recordToEdit = null
                recordToDelete = toDelete
            }
        )
    }

    // Add Modal Dialog
    if (showAddDialog) {
        RecordAddDialog(
            settings = settings,
            onDismiss = { showAddDialog = false },
            onSave = { date, start, end, rate, type, note ->
                onAddRecord(date, start, end, rate, type, note)
                showAddDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Mesai Kaydını Sil",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Bu mesai kaydını silmek istediğinize emin misiniz?",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = recordToDelete!!.id
                        recordToDelete = null
                        onDeleteRecord(id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("btn_confirm_delete")
                ) {
                    Text("Evet, Sil", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun OvertimeRecordCard(
    record: OvertimeRecord,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("record_card_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Date and Overtime Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TimeUtils.formatShortDate(record.date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = if (record.overtimeType.contains("WEEKEND", ignoreCase = true) ||
                        record.overtimeType.contains("Hafta Sonu", ignoreCase = true)
                    ) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (record.overtimeType.contains("WEEKEND", ignoreCase = true) ||
                            record.overtimeType.contains("Hafta Sonu", ignoreCase = true)
                        ) "Hafta Sonu" else "Hafta İçi",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (record.overtimeType.contains("WEEKEND", ignoreCase = true) ||
                            record.overtimeType.contains("Hafta Sonu", ignoreCase = true)
                        ) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body: Times, Duration and Total TL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${record.startTimeStr} - ${record.endTimeStr}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = TimeUtils.formatDurationShort(record.durationMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = TimeUtils.formatCurrency(record.totalAmount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MoneyEmerald
                    )
                    Text(
                        text = "${TimeUtils.formatCurrency(record.hourlyRate)}/sa",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun RecordEditDialog(
    record: OvertimeRecord,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (
        dateStr: String,
        startTimeStr: String,
        endTimeStr: String,
        hourlyRate: Double,
        overtimeType: OvertimeType,
        note: String
    ) -> Unit,
    onDelete: () -> Unit
) {
    var dateStr by remember { mutableStateOf(record.date) }
    var startTimeStr by remember { mutableStateOf(record.startTimeStr) }
    var endTimeStr by remember { mutableStateOf(record.endTimeStr) }
    var hourlyRateText by remember { mutableStateOf(record.hourlyRate.toString()) }
    var overtimeType by remember {
        mutableStateOf(OvertimeType.fromString(record.overtimeType))
    }
    var note by remember { mutableStateOf(record.note) }

    var timePickerTarget by remember { mutableStateOf<String?>(null) }

    if (timePickerTarget != null) {
        val initial = if (timePickerTarget == "start") startTimeStr else endTimeStr
        TimePickerDialog(
            title = if (timePickerTarget == "start") "Başlangıç Saati" else "Bitiş Saati",
            initialTime = initial,
            onDismiss = { timePickerTarget = null },
            onConfirm = {
                if (timePickerTarget == "start") startTimeStr = it else endTimeStr = it
                timePickerTarget = null
            }
        )
    }

    var customHoursText by remember { mutableStateOf("") }

    val calculatedMinutes = remember(startTimeStr, endTimeStr, customHoursText) {
        val parsedH = customHoursText.replace(',', '.').toDoubleOrNull()
        if (parsedH != null && parsedH > 0) {
            (parsedH * 60.0).let { kotlin.math.round(it) }.toInt()
        } else {
            try {
                TimeUtils.calculateManualMinutes(startTimeStr, endTimeStr)
            } catch (e: Exception) {
                record.durationMinutes
            }
        }
    }

    val calculatedTotal = remember(calculatedMinutes, hourlyRateText) {
        val rate = hourlyRateText.replace(',', '.').toDoubleOrNull() ?: record.hourlyRate
        TimeUtils.calculateEarnedAmount(calculatedMinutes, rate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = true
        ),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mesai Kaydını Düzenle",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tarih TextField
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Tarih (yyyy-AA-gg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Time Pickers Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { timePickerTarget = "start" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Başlangıç",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = startTimeStr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { timePickerTarget = "end" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Bitiş",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = endTimeStr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // VEYA Doğrudan Mesai Saati Yazma
                OutlinedTextField(
                    value = customHoursText,
                    onValueChange = { input ->
                        customHoursText = input
                        val h = input.replace(',', '.').toDoubleOrNull()
                        if (h != null && h > 0) {
                            val startLt = try { java.time.LocalTime.parse(startTimeStr) } catch (_: Exception) { java.time.LocalTime.of(18, 0) }
                            val endLt = startLt.plusMinutes((h * 60).toLong())
                            endTimeStr = String.format(java.util.Locale.ROOT, "%02d:%02d", endLt.hour, endLt.minute)
                        }
                    },
                    label = { Text("VEYA Mesai Saati Yaz (Örn: 2 veya 3.5 sa)") },
                    placeholder = { Text("Örn: 2 veya 3.5") },
                    supportingText = { Text("Kendi mesai saatinizi direkt yazarak güncelleyebilirsiniz") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_custom_hours"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Saatlik Ücret
                OutlinedTextField(
                    value = hourlyRateText,
                    onValueChange = { hourlyRateText = it },
                    label = { Text("Saatlik Ücret (TL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Hafta İçi / Hafta Sonu Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isWeekday = overtimeType == OvertimeType.WEEKDAY
                    FilterChip(
                        selected = isWeekday,
                        onClick = {
                            overtimeType = OvertimeType.WEEKDAY
                            hourlyRateText = settings.weekdayRate.toString()
                        },
                        label = { Text("Hafta İçi") },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = !isWeekday,
                        onClick = {
                            overtimeType = OvertimeType.WEEKEND
                            hourlyRateText = settings.weekendRate.toString()
                        },
                        label = { Text("Hafta Sonu") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Preview calculation box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Hesaplanan Süre",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = TimeUtils.formatDurationShort(calculatedMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Toplam Ücret",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = TimeUtils.formatCurrency(calculatedTotal),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MoneyEmerald
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rate = hourlyRateText.replace(',', '.').toDoubleOrNull() ?: record.hourlyRate
                    onSave(dateStr, startTimeStr, endTimeStr, rate, overtimeType, note)
                },
                modifier = Modifier.testTag("btn_save_record")
            ) {
                Text("Kaydet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun RecordAddDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (
        dateStr: String,
        startTimeStr: String,
        endTimeStr: String,
        hourlyRate: Double,
        overtimeType: OvertimeType,
        note: String
    ) -> Unit
) {
    val defaultType = remember { TimeUtils.getOvertimeType() }
    val defaultRate = remember(defaultType, settings) {
        if (defaultType == OvertimeType.WEEKEND) settings.weekendRate else settings.weekdayRate
    }

    var dateStr by remember { mutableStateOf(TimeUtils.todayDateStr()) }
    var startTimeStr by remember { mutableStateOf("18:00") }
    var endTimeStr by remember { mutableStateOf("22:00") }
    var customHoursText by remember { mutableStateOf("") }
    var hourlyRateText by remember {
        mutableStateOf(if (defaultRate % 1.0 == 0.0) defaultRate.toLong().toString() else defaultRate.toString())
    }
    var overtimeType by remember { mutableStateOf(defaultType) }
    var note by remember { mutableStateOf("") }

    var timePickerTarget by remember { mutableStateOf<String?>(null) }

    if (timePickerTarget != null) {
        val initial = if (timePickerTarget == "start") startTimeStr else endTimeStr
        TimePickerDialog(
            title = if (timePickerTarget == "start") "Başlangıç Saati" else "Bitiş Saati",
            initialTime = initial,
            onDismiss = { timePickerTarget = null },
            onConfirm = {
                if (timePickerTarget == "start") startTimeStr = it else endTimeStr = it
                customHoursText = ""
                timePickerTarget = null
            }
        )
    }

    val calculatedMinutes = remember(startTimeStr, endTimeStr, customHoursText) {
        val parsedH = customHoursText.replace(',', '.').toDoubleOrNull()
        if (parsedH != null && parsedH > 0) {
            (parsedH * 60.0).let { kotlin.math.round(it) }.toInt()
        } else {
            try {
                TimeUtils.calculateManualMinutes(startTimeStr, endTimeStr)
            } catch (e: Exception) {
                240
            }
        }
    }

    val calculatedTotal = remember(calculatedMinutes, hourlyRateText) {
        val rate = hourlyRateText.replace(',', '.').toDoubleOrNull() ?: defaultRate
        TimeUtils.calculateEarnedAmount(calculatedMinutes, rate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = true
        ),
        title = {
            Text(
                text = "Manuel Mesai Ekle",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Tarih (yyyy-AA-gg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Time Pickers Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { timePickerTarget = "start" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Başlangıç",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = startTimeStr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { timePickerTarget = "end" },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Bitiş",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = endTimeStr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // VEYA Doğrudan Mesai Saati Yazma
                OutlinedTextField(
                    value = customHoursText,
                    onValueChange = { input ->
                        customHoursText = input
                        val h = input.replace(',', '.').toDoubleOrNull()
                        if (h != null && h > 0) {
                            val startLt = try { java.time.LocalTime.parse(startTimeStr) } catch (_: Exception) { java.time.LocalTime.of(18, 0) }
                            val endLt = startLt.plusMinutes((h * 60).toLong())
                            endTimeStr = String.format(java.util.Locale.ROOT, "%02d:%02d", endLt.hour, endLt.minute)
                        }
                    },
                    label = { Text("VEYA Mesai Saati Yaz (Örn: 2 veya 3.5 sa)") },
                    placeholder = { Text("Örn: 2 veya 3.5") },
                    supportingText = { Text("Kendi mesai saatinizi direkt yazarak hesaplatabilirsiniz") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_custom_hours"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = hourlyRateText,
                    onValueChange = { hourlyRateText = it },
                    label = { Text("Saatlik Ücret (TL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Hafta İçi / Hafta Sonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isWeekday = overtimeType == OvertimeType.WEEKDAY
                    FilterChip(
                        selected = isWeekday,
                        onClick = {
                            overtimeType = OvertimeType.WEEKDAY
                            hourlyRateText = settings.weekdayRate.toString()
                        },
                        label = { Text("Hafta İçi") },
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = !isWeekday,
                        onClick = {
                            overtimeType = OvertimeType.WEEKEND
                            hourlyRateText = settings.weekendRate.toString()
                        },
                        label = { Text("Hafta Sonu") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Hesaplanan Süre",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = TimeUtils.formatDurationShort(calculatedMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Toplam Ücret",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = TimeUtils.formatCurrency(calculatedTotal),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MoneyEmerald
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rate = hourlyRateText.replace(',', '.').toDoubleOrNull() ?: defaultRate
                    onSave(dateStr, startTimeStr, endTimeStr, rate, overtimeType, note)
                },
                modifier = Modifier.testTag("btn_save_new_record")
            ) {
                Text("Ekle", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}
