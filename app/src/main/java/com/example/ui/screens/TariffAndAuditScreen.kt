package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.ActiveOvertime
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import com.example.data.model.RateTariffRule
import com.example.ui.theme.MoneyEmerald
import com.example.util.AuditFinding
import com.example.util.SmartRecordAuditor
import com.example.util.TariffRuleEngine
import com.example.util.TimeUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TariffAndAuditScreen(
    initialTabIndex: Int = 0, // 0: Çoklu Ücret Tarifeleri, 1: Akıllı Eksik Kayıt Kontrolü
    settings: AppSettings,
    tariffRules: List<RateTariffRule>,
    allRecords: List<OvertimeRecord>,
    activeOvertime: ActiveOvertime,
    dismissedAuditKeys: Set<String>,
    onSaveSettings: (AppSettings) -> Unit,
    onSaveTariffRule: (RateTariffRule, (Result<Long>) -> Unit) -> Unit,
    onDeleteTariffRule: (Long) -> Unit,
    onDismissAuditWarning: (String) -> Unit,
    onResetDismissedAuditWarnings: () -> Unit,
    onNavigateToHistoryForEdit: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    var selectedTab by remember { mutableIntStateOf(initialTabIndex) }
    var showAddTariffDialog by remember { mutableStateOf(false) }
    var conflictErrorMessage by remember { mutableStateOf<String?>(null) }

    val auditFindings = remember(allRecords, activeOvertime, settings, dismissedAuditKeys) {
        SmartRecordAuditor.auditRecords(
            records = allRecords,
            activeOvertime = activeOvertime,
            settings = settings,
            dismissedKeys = dismissedAuditKeys
        )
    }

    if (showAddTariffDialog) {
        AddTariffRuleDialog(
            settings = settings,
            existingRules = tariffRules,
            conflictMessage = conflictErrorMessage,
            onDismiss = {
                showAddTariffDialog = false
                conflictErrorMessage = null
            },
            onSave = { rule ->
                onSaveTariffRule(rule) { res ->
                    res.fold(
                        onSuccess = {
                            showAddTariffDialog = false
                            conflictErrorMessage = null
                        },
                        onFailure = { err ->
                            conflictErrorMessage = err.message
                        }
                    )
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("screen_tariff_and_audit"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
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
                        modifier = Modifier.testTag("btn_back_from_tariff_audit")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri Dön")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedTab == 0) "Çoklu Ücret Tarifeleri" else "Akıllı Eksik Kayıt Kontrolü",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Tarih aralıklı ücret kuralları ve akıllı kayıt denetimi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Sekme Seçici
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Ücret Tarifeleri (${tariffRules.size})") },
                    modifier = Modifier.testTag("tab_tariff_rules")
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Akıllı Kayıt Kontrolü (${auditFindings.size})") },
                    modifier = Modifier.testTag("tab_smart_audit")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedTab == 0) {
                    DefaultTariffRatesCard(
                        settings = settings,
                        onSaveSettings = onSaveSettings
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tarih Aralıklı Özel Tarifeler",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                conflictErrorMessage = null
                                showAddTariffDialog = true
                            },
                            modifier = Modifier.testTag("btn_open_add_tariff")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Yeni Tarife Ekle")
                        }
                    }

                    if (tariffRules.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = "Henüz tarih aralıklı ek ücret tarifesi tanımlanmadı. Yukarıdaki varsayılan saatlik ücretler uygulanmaktadır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        tariffRules.forEach { rule ->
                            val diffs = remember(rule, settings) {
                                TariffRuleEngine.compareWithSettings(rule, settings)
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tariff_rule_card_${rule.id}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rule.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "Geçerlilik: ${rule.startDateStr} — ${rule.endDateStr}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = { onDeleteTariffRule(rule.id) }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Tarifeyi Sil",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    diffs.forEach { diff ->
                                        val sign = if (diff.diffAmount >= 0) "+" else ""
                                        Text(
                                            text = "• ${diff.overtimeType.displayName}: ${TimeUtils.formatCurrency(diff.tariffRate)} (Mevcuttan fark: $sign${TimeUtils.formatCurrency(diff.diffAmount)} / %$sign${diff.diffPercent})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Akıllı Eksik Kayıt Kontrolü Sekmesi
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Akıllı Kayıt Denetim İlkeleri",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sistem; eksik bitiş saatlerini, çakışan kayıtları, olağan dışı uzun/kısa süreleri ve saat tutarsızlıklarını tespit eder. Kayıtlarınız asla otomatik olarak değiştirilmez; dilediğiniz kaydı düzenleyebilir veya uyarıyı kapatabilirsiniz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (dismissedAuditKeys.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = onResetDismissedAuditWarnings) {
                                    Text("Kapatılan Uyarıları Tekrar Göster (${dismissedAuditKeys.size})")
                                }
                            }
                        }
                    }

                    if (auditFindings.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_audit_all_clean"),
                            colors = CardDefaults.cardColors(containerColor = MoneyEmerald.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MoneyEmerald,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Tüm Çalışma Kayıtlarınız Tutarlı!",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MoneyEmerald
                                    )
                                    Text(
                                        text = "Çakışan, eksik bitiş saatli veya tutarsız mesai kaydı tespit edilmedi.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    } else {
                        auditFindings.forEach { finding ->
                            AuditFindingCard(
                                finding = finding,
                                onEditRecord = onNavigateToHistoryForEdit,
                                onDismissWarning = { onDismissAuditWarning(finding.warningKey) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultTariffRatesCard(
    settings: AppSettings,
    onSaveSettings: (AppSettings) -> Unit
) {
    var normalText by remember(settings) { mutableStateOf(settings.normalHourlyRate.toString()) }
    var weekdayText by remember(settings) { mutableStateOf(settings.weekdayRate.toString()) }
    var weekendText by remember(settings) { mutableStateOf(settings.weekendRate.toString()) }
    var nightText by remember(settings) { mutableStateOf(settings.nightShiftRate.toString()) }
    var holidayText by remember(settings) { mutableStateOf(settings.officialHolidayRate.toString()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_default_five_tariffs"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "5'li Saatlik Ücret Tarifesi (Varsayılan Tutarlar)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Önceki mesai kayıtları ve arşivlenmiş raporlar kendi kayıtlı ücretlerini korur; buradaki değişiklikler geçmişi bozmaz.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = normalText,
                    onValueChange = { normalText = it },
                    label = { Text("Normal Mesai (TL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = weekdayText,
                    onValueChange = { weekdayText = it },
                    label = { Text("H.İçi Fazla Mesai") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weekendText,
                    onValueChange = { weekendText = it },
                    label = { Text("Hafta Sonu (TL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = nightText,
                    onValueChange = { nightText = it },
                    label = { Text("Gece Vardiyası (TL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = holidayText,
                onValueChange = { holidayText = it },
                label = { Text("Resmî Tatil Mesai Ücreti (TL)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSaveSettings(
                        settings.copy(
                            normalHourlyRate = normalText.replace(',', '.').toDoubleOrNull() ?: settings.normalHourlyRate,
                            weekdayRate = weekdayText.replace(',', '.').toDoubleOrNull() ?: settings.weekdayRate,
                            weekendRate = weekendText.replace(',', '.').toDoubleOrNull() ?: settings.weekendRate,
                            nightShiftRate = nightText.replace(',', '.').toDoubleOrNull() ?: settings.nightShiftRate,
                            officialHolidayRate = holidayText.replace(',', '.').toDoubleOrNull() ?: settings.officialHolidayRate
                        )
                    )
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("btn_save_five_tariffs")
            ) {
                Text("Ücret Tutarlarını Güncelle")
            }
        }
    }
}

@Composable
private fun AuditFindingCard(
    finding: AuditFinding,
    onEditRecord: () -> Unit,
    onDismissWarning: () -> Unit
) {
    val borderColor = if (finding.issueType.isAdvisoryOnly) Color(0xFF0284C7) else Color(0xFFD97706)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audit_finding_${finding.warningKey}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = finding.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${finding.issueType.labelTr} • ${finding.dateStr}",
                        style = MaterialTheme.typography.labelSmall,
                        color = borderColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = finding.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = finding.suggestedActionText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismissWarning,
                    modifier = Modifier.testTag("btn_dismiss_audit_${finding.warningKey}")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Uyarıyı Kapat")
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedButton(
                    onClick = onEditRecord,
                    modifier = Modifier.testTag("btn_edit_audit_${finding.warningKey}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kaydı İncele / Düzenle")
                }
            }
        }
    }
}

@Composable
private fun AddTariffRuleDialog(
    settings: AppSettings,
    existingRules: List<RateTariffRule>,
    conflictMessage: String?,
    onDismiss: () -> Unit,
    onSave: (RateTariffRule) -> Unit
) {
    var name by remember { mutableStateOf("Yeni Dönem Ücret Tarifesi") }
    var startDate by remember { mutableStateOf("2026-10-01") }
    var endDate by remember { mutableStateOf("2026-12-31") }
    var normalRate by remember { mutableStateOf(settings.normalHourlyRate.toString()) }
    var weekdayRate by remember { mutableStateOf(settings.weekdayRate.toString()) }
    var weekendRate by remember { mutableStateOf(settings.weekendRate.toString()) }
    var nightRate by remember { mutableStateOf(settings.nightShiftRate.toString()) }
    var holidayRate by remember { mutableStateOf(settings.officialHolidayRate.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tarih Aralıklı Ücret Tarifesi Ekle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!conflictMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tariff_conflict_warning_box")
                    ) {
                        Text(
                            text = conflictMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tarife Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Başlangıç (YYYY-AA-GG)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("Bitiş (YYYY-AA-GG)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = normalRate,
                        onValueChange = { normalRate = it },
                        label = { Text("Normal (TL)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weekdayRate,
                        onValueChange = { weekdayRate = it },
                        label = { Text("H.İçi Mesai (TL)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = weekendRate,
                        onValueChange = { weekendRate = it },
                        label = { Text("H.Sonu (TL)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = nightRate,
                        onValueChange = { nightRate = it },
                        label = { Text("Gece (TL)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = holidayRate,
                    onValueChange = { holidayRate = it },
                    label = { Text("Resmî Tatil (TL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rule = RateTariffRule(
                        name = name.ifBlank { "Özel Ücret Tarifesi" },
                        startDateStr = startDate.trim(),
                        endDateStr = endDate.trim(),
                        normalHourlyRate = normalRate.replace(',', '.').toDoubleOrNull() ?: settings.normalHourlyRate,
                        weekdayOvertimeRate = weekdayRate.replace(',', '.').toDoubleOrNull() ?: settings.weekdayRate,
                        weekendRate = weekendRate.replace(',', '.').toDoubleOrNull() ?: settings.weekendRate,
                        nightShiftRate = nightRate.replace(',', '.').toDoubleOrNull() ?: settings.nightShiftRate,
                        officialHolidayRate = holidayRate.replace(',', '.').toDoubleOrNull() ?: settings.officialHolidayRate
                    )
                    onSave(rule)
                },
                modifier = Modifier.testTag("btn_confirm_save_tariff")
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
