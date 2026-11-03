package com.example.ui.screens

import android.net.Uri
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppSettings
import com.example.ui.components.TimePickerDialog
import com.example.ui.components.findActivity
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.ThemePreferences
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SettingsSection(val title: String, val icon: ImageVector) {
    RATES("Tarifeler & Saatler", Icons.Filled.Payments),
    PROFILE_ALERTS("Profil & Uyarılar", Icons.Filled.Notifications),
    BACKUP("Yedekleme", Icons.Filled.Backup)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    isPremium: Boolean = false,
    onSaveSettings: (AppSettings) -> Unit,
    onBackupCreate: () -> Unit = {},
    onBackupRestore: (Uri, Boolean) -> Unit = { _, _ -> },
    unreadNotificationCount: Int = 0,
    onOpenNotificationCenter: () -> Unit = {},
    onOpenTariffAndAudit: () -> Unit = {},
    onOpenBudgetTracking: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenDocumentArchive: () -> Unit = {}
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
    val selectedPalette by ThemePreferences.selectedPalette.collectAsStateWithLifecycle()

    var premiumThemeFeatureTitle by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf(SettingsSection.RATES) }

    if (premiumThemeFeatureTitle != null) {
        com.example.ui.components.GooglePlaySubscriptionDialog(
            isPremium = isPremium,
            subscriptionStatus = subscriptionStatus,
            formattedPrice = formattedPrice,
            billingPeriodText = billingPeriodText,
            featureContextTitle = premiumThemeFeatureTitle,
            onDismiss = { premiumThemeFeatureTitle = null },
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

    var weekdayRateText by remember(settings) {
        mutableStateOf(if (settings.weekdayRate > 0.0) {
            if (settings.weekdayRate % 1.0 == 0.0) settings.weekdayRate.toLong().toString() else settings.weekdayRate.toString()
        } else "")
    }
    var weekendRateText by remember(settings) {
        mutableStateOf(if (settings.weekendRate > 0.0) {
            if (settings.weekendRate % 1.0 == 0.0) settings.weekendRate.toLong().toString() else settings.weekendRate.toString()
        } else "")
    }

    var weekdayStartTime by remember(settings) { mutableStateOf(settings.weekdayStartTime) }
    var saturdayStartTime by remember(settings) { mutableStateOf(settings.saturdayStartTime) }
    var sundayStartTime by remember(settings) { mutableStateOf(settings.sundayStartTime) }

    var autoMonday by remember(settings) { mutableStateOf(settings.autoMonday) }
    var autoTuesday by remember(settings) { mutableStateOf(settings.autoTuesday) }
    var autoWednesday by remember(settings) { mutableStateOf(settings.autoWednesday) }
    var autoThursday by remember(settings) { mutableStateOf(settings.autoThursday) }
    var autoFriday by remember(settings) { mutableStateOf(settings.autoFriday) }
    var autoSaturday by remember(settings) { mutableStateOf(settings.autoSaturday) }
    var autoSunday by remember(settings) { mutableStateOf(settings.autoSunday) }

    var notificationsEnabled by remember(settings) { mutableStateOf(settings.notificationsEnabled) }

    var forgottenAlertEnabled by remember(settings) { mutableStateOf(settings.forgottenAlertEnabled) }
    var forgottenAlertHours by remember(settings) { mutableStateOf(settings.forgottenAlertHours) }

    var employeeName by remember(settings) { mutableStateOf(settings.employeeName) }
    var workplaceName by remember(settings) { mutableStateOf(settings.workplaceName) }
    var baseSalaryText by remember(settings) {
        mutableStateOf(if (settings.baseSalary > 0.0) {
            if (settings.baseSalary % 1.0 == 0.0) settings.baseSalary.toLong().toString() else settings.baseSalary.toString()
        } else "")
    }
    var salaryDayText by remember(settings) { mutableStateOf(settings.salaryDayOfMonth.toString()) }
    var monthlyWorkHoursText by remember(settings) {
        mutableStateOf(if (settings.monthlyWorkHours > 0.0) {
            if (settings.monthlyWorkHours % 1.0 == 0.0) settings.monthlyWorkHours.toLong().toString() else settings.monthlyWorkHours.toString()
        } else "225")
    }
    var normalHourlyRateText by remember(settings) {
        mutableStateOf(if (settings.normalHourlyRate > 0.0) {
            if (settings.normalHourlyRate % 1.0 == 0.0) settings.normalHourlyRate.toLong().toString() else settings.normalHourlyRate.toString()
        } else "")
    }
    var nightShiftRateText by remember(settings) {
        mutableStateOf(if (settings.nightShiftRate > 0.0) {
            if (settings.nightShiftRate % 1.0 == 0.0) settings.nightShiftRate.toLong().toString() else settings.nightShiftRate.toString()
        } else "")
    }
    var holidayRateText by remember(settings) {
        mutableStateOf(if (settings.officialHolidayRate > 0.0) {
            if (settings.officialHolidayRate % 1.0 == 0.0) settings.officialHolidayRate.toLong().toString() else settings.officialHolidayRate.toString()
        } else "")
    }
    var shiftLeadMinutesText by remember(settings) {
        mutableStateOf(settings.shiftReminderLeadMinutes.toString())
    }

    var reminderOvertimeEntryEnabled by remember(settings) { mutableStateOf(settings.reminderOvertimeEntryEnabled) }
    var reminderOvertimeEntryTime by remember(settings) { mutableStateOf(settings.reminderTimeStr) }
    var reminderSalaryDayEnabled by remember(settings) { mutableStateOf(settings.reminderSalaryDayEnabled) }
    var reminderShiftStartEnabled by remember(settings) { mutableStateOf(settings.reminderShiftStartEnabled) }
    var reminderShiftStartTime by remember(settings) { mutableStateOf("${settings.shiftReminderLeadMinutes} dk önce") }
    var reminderMissingRecordEnabled by remember(settings) { mutableStateOf(settings.reminderMissingRecordEnabled) }
    var achievementNotificationsEnabled by remember(settings) { mutableStateOf(settings.reminderAchievementEnabled) }

    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    var timePickerTarget by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    if (timePickerTarget != null) {
        val (title, initial) = when (timePickerTarget) {
            "weekday" -> Pair("Hafta İçi Başlangıç Saati", weekdayStartTime)
            "saturday" -> Pair("Cumartesi Başlangıç Saati", saturdayStartTime)
            "sunday" -> Pair("Pazar Başlangıç Saati", sundayStartTime)
            else -> Pair("Saat Seçin", "18:00")
        }

        TimePickerDialog(
            title = title,
            initialTime = initial,
            onDismiss = { timePickerTarget = null },
            onConfirm = { selected ->
                when (timePickerTarget) {
                    "weekday" -> weekdayStartTime = selected
                    "saturday" -> saturdayStartTime = selected
                    "sunday" -> sundayStartTime = selected
                }
                timePickerTarget = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Ayarlar & Yapılandırma",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Tarife, bildirim ve veri yönetimi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick = {
                            val wd = weekdayRateText.replace(',', '.').toDoubleOrNull() ?: settings.weekdayRate
                            val we = weekendRateText.replace(',', '.').toDoubleOrNull() ?: settings.weekendRate
                            val normRate = normalHourlyRateText.replace(',', '.').toDoubleOrNull() ?: settings.normalHourlyRate
                            val nightRate = nightShiftRateText.replace(',', '.').toDoubleOrNull() ?: settings.nightShiftRate
                            val holRate = holidayRateText.replace(',', '.').toDoubleOrNull() ?: settings.officialHolidayRate
                            val leadMins = (shiftLeadMinutesText.toIntOrNull() ?: settings.shiftReminderLeadMinutes).coerceIn(5, 180)
                            val baseSal = baseSalaryText.replace(',', '.').toDoubleOrNull() ?: settings.baseSalary
                            val salDay = (salaryDayText.toIntOrNull() ?: settings.salaryDayOfMonth).coerceIn(1, 31)
                            val workHours = (monthlyWorkHoursText.replace(',', '.').toDoubleOrNull() ?: settings.monthlyWorkHours).coerceAtLeast(1.0)
                            val updated = settings.copy(
                                weekdayRate = wd,
                                weekendRate = we,
                                normalHourlyRate = normRate,
                                nightShiftRate = nightRate,
                                officialHolidayRate = holRate,
                                shiftReminderLeadMinutes = leadMins,
                                baseSalary = baseSal,
                                salaryDayOfMonth = salDay,
                                monthlyWorkHours = workHours,
                                weekdayStartTime = weekdayStartTime,
                                saturdayStartTime = saturdayStartTime,
                                sundayStartTime = sundayStartTime,
                                autoMonday = autoMonday,
                                autoTuesday = autoTuesday,
                                autoWednesday = autoWednesday,
                                autoThursday = autoThursday,
                                autoFriday = autoFriday,
                                autoSaturday = autoSaturday,
                                autoSunday = autoSunday,
                                notificationsEnabled = notificationsEnabled,
                                forgottenAlertEnabled = forgottenAlertEnabled,
                                forgottenAlertHours = forgottenAlertHours,
                                reminderOvertimeEntryEnabled = reminderOvertimeEntryEnabled,
                                reminderTimeStr = reminderOvertimeEntryTime.trim().ifBlank { "20:30" },
                                reminderSalaryDayEnabled = reminderSalaryDayEnabled,
                                reminderShiftStartEnabled = reminderShiftStartEnabled,
                                reminderMissingRecordEnabled = reminderMissingRecordEnabled,
                                reminderAchievementEnabled = achievementNotificationsEnabled,
                                employeeName = employeeName.trim(),
                                workplaceName = workplaceName.trim()
                            )
                            onSaveSettings(updated)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Ayarlar başarıyla kaydedildi")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_save_settings"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ayarları Kaydet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Segmented Category Tabs (Eliminating Clutter)
            TabRow(
                selectedTabIndex = selectedSection.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                SettingsSection.values().forEach { section ->
                    val isSelected = selectedSection == section
                    Tab(
                        selected = isSelected,
                        onClick = { selectedSection = section },
                        text = {
                            Text(
                                text = section.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                when (selectedSection) {
                    SettingsSection.RATES -> {
                        // Section 0: Base Salary & Salary Day (Personalized Payout)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_salary_settings"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Maaş & Hakediş Ayarları",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Her çalışanın maaşı farklıdır. Taban net maaşınızı ve maaş alma gününüzü girerek ay sonu toplam net hakedişinizi ana ekranda takip edin.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = baseSalaryText,
                                        onValueChange = { baseSalaryText = it },
                                        label = { Text("Aylık Taban Net Maaş (₺)") },
                                        placeholder = { Text("Örn: 35000") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .testTag("settings_input_base_salary"),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = salaryDayText,
                                        onValueChange = { salaryDayText = it },
                                        label = { Text("Maaş Günü") },
                                        placeholder = { Text("1-31") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(0.7f)
                                            .testTag("settings_input_salary_day"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = monthlyWorkHoursText,
                                    onValueChange = { monthlyWorkHoursText = it },
                                    label = { Text("Aylık Çalışma Saati (saat)") },
                                    placeholder = { Text("Yasal 225 saat veya örn: 180, 200") },
                                    supportingText = { Text("Kendi çalışma saatinizi yazın (Örn: 225, 200, 180 sa)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_input_work_hours"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                val parsedBase = baseSalaryText.replace(',', '.').toDoubleOrNull() ?: 0.0
                                val parsedHours = (monthlyWorkHoursText.replace(',', '.').toDoubleOrNull() ?: 225.0).coerceAtLeast(1.0)
                                if (parsedBase > 0) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val baseHourly = parsedBase / parsedHours
                                    val calcWd = (baseHourly * 1.5).let { kotlin.math.round(it * 100.0) / 100.0 }
                                    val calcWe = (baseHourly * 2.0).let { kotlin.math.round(it * 100.0) / 100.0 }
                                    val wdStr = if (calcWd % 1.0 == 0.0) calcWd.toLong().toString() else calcWd.toString()
                                    val weStr = if (calcWe % 1.0 == 0.0) calcWe.toLong().toString() else calcWe.toString()
                                    val baseHourlyStr = if (baseHourly % 1.0 == 0.0) baseHourly.toLong().toString() else String.format(Locale.ROOT, "%.2f", baseHourly)

                                    OutlinedButton(
                                        onClick = {
                                            weekdayRateText = wdStr
                                            weekendRateText = weStr
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "Maaş & Saatimden Mesaileri Hesapla ($wdStr ₺ / $weStr ₺)",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Text(
                                        text = "Normal Saat Ücretiniz: $baseHourlyStr TL/sa (${parsedHours.toInt()} saat esas alınarak)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 1: Hourly Rates
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Payments,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Saatlik Mesai Ücretleri",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = weekdayRateText,
                                        onValueChange = { weekdayRateText = it },
                                        label = { Text("Hafta İçi (TL/sa)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_weekday_rate"),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = weekendRateText,
                                        onValueChange = { weekendRateText = it },
                                        label = { Text("Hafta Sonu (TL/sa)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_weekend_rate"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = normalHourlyRateText,
                                        onValueChange = { normalHourlyRateText = it },
                                        label = { Text("Normal Saatlik (TL/sa)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_normal_hourly_rate"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = nightShiftRateText,
                                        onValueChange = { nightShiftRateText = it },
                                        label = { Text("Gece Vardiyası (TL/sa)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_night_shift_rate"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = holidayRateText,
                                        onValueChange = { holidayRateText = it },
                                        label = { Text("Resmî Tatil (TL/sa)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_official_holiday_rate"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = shiftLeadMinutesText,
                                        onValueChange = { shiftLeadMinutesText = it },
                                        label = { Text("Vardiya Hatırlatma (dk)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_expected_daily_hours"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = onOpenTariffAndAudit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_settings_open_tariff_audit"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Tarih Aralıklı Çoklu Tarifeleri ve Eksik Kayıtları Yönet",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 2: Start Times
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Otomatik Başlangıç Saatleri",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                TimeSelectRow(
                                    label = "Hafta İçi (Pzt - Cum)",
                                    time = weekdayStartTime,
                                    onClick = { timePickerTarget = "weekday" },
                                    testTag = "btn_time_weekday"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                TimeSelectRow(
                                    label = "Cumartesi",
                                    time = saturdayStartTime,
                                    onClick = { timePickerTarget = "saturday" },
                                    testTag = "btn_time_saturday"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                TimeSelectRow(
                                    label = "Pazar",
                                    time = sundayStartTime,
                                    onClick = { timePickerTarget = "sunday" },
                                    testTag = "btn_time_sunday"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 3: Daily Switches
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Otomatik Başlatılacak Günler",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                DaySwitchRow("Pazartesi", autoMonday) { autoMonday = it }
                                DaySwitchRow("Salı", autoTuesday) { autoTuesday = it }
                                DaySwitchRow("Çarşamba", autoWednesday) { autoWednesday = it }
                                DaySwitchRow("Perşembe", autoThursday) { autoThursday = it }
                                DaySwitchRow("Cuma", autoFriday) { autoFriday = it }
                                DaySwitchRow("Cumartesi", autoSaturday) { autoSaturday = it }
                                DaySwitchRow("Pazar", autoSunday) { autoSunday = it }
                            }
                        }
                    }

                    SettingsSection.PROFILE_ALERTS -> {
                        // Section 1: Employee info for PDF
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Badge,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Çalışan & Bordro Bilgileri",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Resmi PDF mesai çizelgesi ve onay formunda yer alır",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = employeeName,
                                    onValueChange = { employeeName = it },
                                    label = { Text("Çalışan Adı Soyadı") },
                                    placeholder = { Text("Örn. Ahmet Yılmaz") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_employee_name"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = workplaceName,
                                    onValueChange = { workplaceName = it },
                                    label = { Text("İş Yeri / Şirket / Departman") },
                                    placeholder = { Text("Örn. XYZ Lojistik A.Ş. - Depo Birimi") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_workplace_name"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 2: Notifications switch
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Mesai Bildirimleri",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Mesai başladığında ve durdurma butonlu bildirim",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Switch(
                                    checked = notificationsEnabled,
                                    onCheckedChange = { notificationsEnabled = it },
                                    modifier = Modifier.testTag("switch_notifications")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 3: Forgotten Overtime Alert
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.NotificationImportant,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Unutulan Mesai Uyarısı",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Sayaç açık unutulduğunda hatırlatma bildirimi gönderir",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = forgottenAlertEnabled,
                                        onCheckedChange = { forgottenAlertEnabled = it },
                                        modifier = Modifier.testTag("switch_forgotten_alert")
                                    )
                                }

                                if (forgottenAlertEnabled) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Kaç saat sonra uyarı gönderilsin?",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val hourOptions = listOf(3, 4, 5, 6, 8)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        hourOptions.forEach { h ->
                                            FilterChip(
                                                selected = forgottenAlertHours == h,
                                                onClick = { forgottenAlertHours = h },
                                                label = { Text("$h Saat") },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Otomatik Hatırlatmalar ve Bildirim Merkezi",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Mesai Kaydı Girme Hatırlatması", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Saat: $reminderOvertimeEntryTime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = reminderOvertimeEntryEnabled,
                                        onCheckedChange = { reminderOvertimeEntryEnabled = it },
                                        modifier = Modifier.testTag("switch_settings_overtime_reminder")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Maaş Günü Hatırlatması", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Her ayın ${salaryDayText}. günü bildirim", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = reminderSalaryDayEnabled,
                                        onCheckedChange = { reminderSalaryDayEnabled = it },
                                        modifier = Modifier.testTag("switch_settings_salary_reminder")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Vardiya Başlangıç Hatırlatması", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Saat: $reminderShiftStartTime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = reminderShiftStartEnabled,
                                        onCheckedChange = { reminderShiftStartEnabled = it },
                                        modifier = Modifier.testTag("switch_settings_shift_reminder")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Eksik Çalışma Kaydı Uyarısı", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Bitiş saati eksik veya çakışan kayıtlar için", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = reminderMissingRecordEnabled,
                                        onCheckedChange = { reminderMissingRecordEnabled = it },
                                        modifier = Modifier.testTag("switch_settings_missing_record_reminder")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Başarı ve Kilometre Taşı Bildirimleri", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Yeni rozet kazanıldığında bildirim gönder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = achievementNotificationsEnabled,
                                        onCheckedChange = { achievementNotificationsEnabled = it },
                                        modifier = Modifier.testTag("switch_settings_achievement_reminder")
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = onOpenNotificationCenter,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_settings_open_notification_center"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (unreadNotificationCount > 0) "Bildirim Merkezini Aç ($unreadNotificationCount Okunmamış)" else "Bildirim Merkezini ve Saatlerini Aç",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section 4: Özel Tema ve Görünüm Seçenekleri (Premium Özelliği)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_custom_theme_options"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Özel Tema ve Görünüm Seçenekleri",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Ferah açık tasarımı koruyan kurumsal renk paletleri",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!isPremium) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "PREMIUM",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val effectiveSelected = if (selectedPalette.requiresPremium && !isPremium) {
                                    AppThemePalette.CLASSIC_BLUE
                                } else {
                                    selectedPalette
                                }

                                AppThemePalette.entries.forEach { palette ->
                                    val isCurrent = effectiveSelected == palette
                                    val isLocked = palette.requiresPremium && !isPremium

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                val applied = ThemePreferences.selectPalette(context, palette, isPremium)
                                                if (!applied) {
                                                    premiumThemeFeatureTitle = "Özel Tema ve Görünüm Seçenekleri (${palette.title})"
                                                }
                                            }
                                            .testTag("theme_option_${palette.name.lowercase()}"),
                                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(palette.primaryColor)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = palette.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }

                                            when {
                                                isCurrent -> {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Seçili",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                isLocked -> {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Premium",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

                    SettingsSection.BACKUP -> {
                        // Mesai Takip Premium Abonelik Kartı
                        com.example.ui.components.GooglePlaySubscribeCard()

                        Spacer(modifier = Modifier.height(14.dp))

                        // Section: Backup & Restore
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Backup,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Veri Yedekleme ve Geri Yükleme",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Cihaz değişikliğinde tüm verilerinizi JSON formatında aktarın",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onBackupCreate,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("btn_backup_create"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Backup,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Yedek Oluştur", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { filePickerLauncher.launch("*/*") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("btn_backup_restore"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SettingsBackupRestore,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Geri Yükle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Legal / Information Card
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "İş Kanunu Fazla Mesai Bilgilendirmesi",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "4857 Sayılı İş Kanunu uyarınca haftalık 45 saati aşan çalışmalar fazla mesai sayılır. Saatlik fazla çalışma ücreti normal saat ücretinin en az %50 fazlasıdır. Yıllık toplam fazla mesai süresi 270 saati aşamaz.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Mesai Takip v31.0 (Sürüm 31)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Veriler Çift Katmanlı Otomatik Korumalı",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Restore Confirmation Dialog
        if (showRestoreConfirmDialog && pendingRestoreUri != null) {
            AlertDialog(
                onDismissRequest = {
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.SettingsBackupRestore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text(
                        text = "Yedekten Geri Yükle",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Seçilen yedek dosyasındaki mesai kayıtları ve ayarlar yüklenecektir.\n\nMevcut verileri koruyarak üstüne eklemek mi istersiniz, yoksa mevcut kayıtları silip temiz bir geri yükleme mi yapmak istersiniz?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val uri = pendingRestoreUri
                            if (uri != null) {
                                onBackupRestore(uri, false)
                            }
                            showRestoreConfirmDialog = false
                            pendingRestoreUri = null
                        }
                    ) {
                        Text("Üstüne Ekle")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showRestoreConfirmDialog = false
                                pendingRestoreUri = null
                            }
                        ) {
                            Text("İptal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val uri = pendingRestoreUri
                                if (uri != null) {
                                    onBackupRestore(uri, true)
                                }
                                showRestoreConfirmDialog = false
                                pendingRestoreUri = null
                            }
                        ) {
                            Text("Tümünü Sıfırla ve Yükle", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun TimeSelectRow(
    label: String,
    time: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag(testTag)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun DaySwitchRow(
    dayName: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
