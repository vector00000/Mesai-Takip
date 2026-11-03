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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveOvertime
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import com.example.ui.components.PulseDot
import com.example.ui.components.SalaryCountdownCard
import com.example.ui.theme.MoneyEmerald
import com.example.util.AlarmScheduler
import com.example.util.TimeUtils
import java.time.LocalDate

@Composable
fun HomeScreen(
    settings: AppSettings,
    activeOvertime: ActiveOvertime,
    allRecords: List<OvertimeRecord>,
    liveElapsedSeconds: Long,
    liveEarnings: Double,
    retroactivePromptTime: String?,
    onStartOvertime: () -> Unit,
    onStopOvertime: () -> Unit,
    onStartRetroactive: (String) -> Unit,
    onDismissRetroactive: () -> Unit,
    onToggleSkipToday: () -> Unit,
    onToggleAutoGlobal: (Boolean) -> Unit,
    onSaveSettings: (AppSettings) -> Unit = {},
    onAddRecord: ((date: String, start: String, end: String, rate: Double, type: com.example.data.model.OvertimeType, note: String) -> Unit)? = null,
    onAddRecordWithBreak: ((date: String, start: String, end: String, rate: Double, type: com.example.data.model.OvertimeType, note: String, breakMinutes: Int) -> Unit)? = null,
    onUpdateActiveBreakMinutes: (Int) -> Unit = {},
    unreadNotificationCount: Int = 0,
    auditWarningCount: Int = 0,
    onOpenNotificationCenter: () -> Unit = {},
    onOpenBudgetScreen: () -> Unit = {},
    onOpenPayrollArchiveScreen: () -> Unit = {},
    onOpenAchievementsScreen: () -> Unit = {},
    onOpenTariffScreen: () -> Unit = {},
    onOpenSmartAuditScreen: () -> Unit = {}
) {
    var showManualAddDialog by remember { mutableStateOf(false) }

    if (showManualAddDialog && (onAddRecord != null || onAddRecordWithBreak != null)) {
        RecordAddDialog(
            settings = settings,
            onDismiss = { showManualAddDialog = false },
            onSave = { date, start, end, rate, type, note ->
                if (onAddRecordWithBreak != null) {
                    onAddRecordWithBreak(date, start, end, rate, type, note, 0)
                } else {
                    onAddRecord?.invoke(date, start, end, rate, type, note)
                }
                showManualAddDialog = false
            }
        )
    }

    val today = remember { LocalDate.now(TimeUtils.ISTANBUL_ZONE) }
    val todayFormatted = remember { TimeUtils.formatTodayFull() }
    val todayStr = remember { TimeUtils.todayDateStr() }
    val isTodaySkipped = settings.skipDate == todayStr

    // Calculate completed overtime today (if any)
    val todayRecords = remember(allRecords, todayStr) {
        allRecords.filter { it.date == todayStr }
    }
    val todayCompletedMinutes = remember(todayRecords) {
        todayRecords.sumOf { it.durationMinutes }
    }
    val todayCompletedEarnings = remember(todayRecords) {
        todayRecords.sumOf { it.totalAmount }
    }

    // Determine next scheduled start text
    val nextScheduledText = remember(settings, isTodaySkipped) {
        if (!settings.autoGlobalEnabled) {
            "Otomatik mesai kapalı"
        } else if (isTodaySkipped) {
            "Bugün için mesai atlandı"
        } else {
            val next = AlarmScheduler.findNextTriggerDateTime(settings)
            if (next != null) {
                val isNextToday = next.toLocalDate() == today
                val dayLabel = if (isNextToday) "Bugün" else TimeUtils.formatShortDate(next.toLocalDate().toString())
                "Sonraki otomatik başlangıç: $dayLabel ${TimeUtils.formatTime(next.toLocalTime())}"
            } else {
                "Planlanmış otomatik mesai bulunmuyor"
            }
        }
    }

    // Retroactive Overtime AlertDialog
    if (retroactivePromptTime != null) {
        AlertDialog(
            onDismissRequest = onDismissRetroactive,
            icon = {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Otomatik Mesai Başlangıcı",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Bugünkü mesainiz saat $retroactivePromptTime'de başlamış olarak kaydedilsin mi?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { onStartRetroactive(retroactivePromptTime) },
                    modifier = Modifier.testTag("btn_confirm_retroactive")
                ) {
                    Text("EVET, $retroactivePromptTime'DEN BAŞLAT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissRetroactive,
                    modifier = Modifier.testTag("btn_dismiss_retroactive")
                ) {
                    Text("HAYIR")
                }
            }
        )
    }

    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val areNotificationsEnabled = remember {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Üstte Sabit (Sticky) Kurumsal Premium Banner (Ana ekran kaydırılırken üstte sabit kalır)
        Surface(
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sticky_premium_banner_container")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                com.example.ui.components.CorporatePremiumBanner()
            }
        }

        // Kaydırılabilir Ana Ekran İçeriği
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("home_scrollable_content")
        ) {
            if (!areNotificationsEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bildirim İzni Gerekli",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Mesai başladığında ve hatırlatmalarda bildirim almak için dokunun.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Clean, Light App Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MESAİ TAKİP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = todayFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Status Indicator Badge + Bildirim Merkezi Girişi
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onOpenNotificationCenter,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("btn_open_notification_center")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Bildirim Merkezi",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        if (unreadNotificationCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = CircleShape,
                                modifier = Modifier.testTag("badge_unread_notifications")
                            ) {
                                Text(
                                    text = unreadNotificationCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = if (activeOvertime.isActive) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    border = if (activeOvertime.isActive) androidx.compose.foundation.BorderStroke(1.dp, MoneyEmerald.copy(alpha = 0.5f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeOvertime.isActive) {
                            PulseDot(size = 8.dp, color = MoneyEmerald)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Canlı Mesai",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mesai Yok",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // HERO CARD: Primary Overtime Tracker Card (Crisp, High Contrast & Clean)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hero_overtime_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (activeOvertime.isActive) {
                    // --- ACTIVE OVERTIME VIEW ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Başlangıç: ${activeOvertime.startTimeStr}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val activeRate = if (activeOvertime.hourlyRate > 0.0) activeOvertime.hourlyRate
                                             else settings.getEffectiveRate(TimeUtils.isWeekend(today))
                            Text(
                                text = "${TimeUtils.formatCurrency(activeRate)}/sa",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "BİRİKEN MESAİ KAZANCI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Big Vibrant Live Earnings
                    Text(
                        text = TimeUtils.formatCurrency(liveEarnings),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 38.sp
                        ),
                        color = MoneyEmerald,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Duration Counter + Mola Süresi Yönetimi
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                                text = "Net Süre: ${TimeUtils.formatDurationWithSeconds(liveElapsedSeconds)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mola Ekleme / Düşme Satırı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mola: ${activeOvertime.breakMinutes} dk",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("text_active_break_minutes")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { onUpdateActiveBreakMinutes(activeOvertime.breakMinutes + 15) },
                            modifier = Modifier.testTag("btn_break_plus_15")
                        ) {
                            Text("+15 dk Mola", fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { onUpdateActiveBreakMinutes(activeOvertime.breakMinutes + 30) },
                            modifier = Modifier.testTag("btn_break_plus_30")
                        ) {
                            Text("+30 dk", fontWeight = FontWeight.Bold)
                        }
                        if (activeOvertime.breakMinutes > 0) {
                            TextButton(
                                onClick = { onUpdateActiveBreakMinutes(0) },
                                modifier = Modifier.testTag("btn_break_reset")
                            ) {
                                Text("Sıfırla", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // STOP BUTTON
                    Button(
                        onClick = onStopOvertime,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("btn_stop_overtime"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MESAİYİ DURDUR",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                } else {
                    // --- IDLE / INACTIVE VIEW ---
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Şu Anda Mesai Yok",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = nextScheduledText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        val currentRate = settings.getEffectiveRate(TimeUtils.isWeekend(today))
                        val dayTypeLabel = if (TimeUtils.isWeekend(today)) "Hafta Sonu" else "Hafta İçi"
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MoneyEmerald
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tarife: $dayTypeLabel ${TimeUtils.formatCurrency(currentRate)}/sa",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // START BUTTON
                    Button(
                        onClick = onStartOvertime,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("btn_start_overtime"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MESAİYİ BAŞLAT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CLEAN STREAMLINED CONTROLS CARD (Compact & Symmetrical)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Otomatik Mesai Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Column {
                        Text(
                            text = "Otomatik Mesai",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (settings.autoGlobalEnabled) "Aktif" else "Kapalı",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (settings.autoGlobalEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = settings.autoGlobalEnabled,
                        onCheckedChange = { onToggleAutoGlobal(it) },
                        modifier = Modifier.testTag("switch_auto_global")
                    )
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Bugün Mesai Yok Toggle Pill
                Surface(
                    onClick = onToggleSkipToday,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .testTag("btn_skip_today"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTodaySkipped) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isTodaySkipped) Icons.Filled.Block else Icons.Filled.AlarmOff,
                            contentDescription = null,
                            tint = if (isTodaySkipped) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Bugün Mesai Yok",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isTodaySkipped) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTodaySkipped) "Mesai Atlandı" else "Tek Gün Kapat",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTodaySkipped) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Manual Overtime Hours Input Button
        if (onAddRecord != null) {
            OutlinedButton(
                onClick = { showManualAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_quick_manual_hour"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "✍️ Kendi Mesai Saatini Yazarak Ekle",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Salary Countdown & Total Payout Card
        SalaryCountdownCard(
            settings = settings,
            allRecords = allRecords,
            activeOvertime = activeOvertime,
            liveEarnings = liveEarnings,
            liveElapsedSeconds = liveElapsedSeconds,
            onSaveSettings = onSaveSettings
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Today's Completed Overtime Summary (if any records exist for today)
        if (todayRecords.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MoneyEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Bugün Tamamlanan Mesailer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${todayRecords.size} Kayıt",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Toplam Süre",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeUtils.formatDurationShort(todayCompletedMinutes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Kazanılan Ücret",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeUtils.formatCurrency(todayCompletedEarnings),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MoneyEmerald
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Information / Rates card (Light & Crisp)
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Hafta İçi Tarifesi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${TimeUtils.formatCurrency(settings.weekdayRate)}/sa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Hafta Sonu Tarifesi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${TimeUtils.formatCurrency(settings.weekendRate)}/sa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hızlı Erişim & Yönetim Modülleri Kartı (Tüm Yeni Özelliklere Tek Dokunuşla Erişim)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_home_quick_modules"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Yönetim, Bütçe ve Denetim Modülleri",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenBudgetScreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_home_open_budget"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Gelir & Gider Bütçesi",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenPayrollArchiveScreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_home_open_payroll_archive"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Maaş Raporu Arşivi",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenTariffScreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_home_open_tariffs"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Çoklu Ücret Tarifeleri",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenSmartAuditScreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_home_open_smart_audit"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val auditLabel = if (auditWarningCount > 0) {
                            "Akıllı Kayıt Kontrolü ($auditWarningCount)"
                        } else {
                            "Akıllı Kayıt Kontrolü"
                        }
                        Text(
                            text = auditLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenAchievementsScreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_home_open_achievements"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Başarı & Kilometre Taşları",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenNotificationCenter,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_home_open_notifications"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        val notifLabel = if (unreadNotificationCount > 0) {
                            "Bildirim Merkezi ($unreadNotificationCount)"
                        } else {
                            "Bildirim Merkezi"
                        }
                        Text(
                            text = notifLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
