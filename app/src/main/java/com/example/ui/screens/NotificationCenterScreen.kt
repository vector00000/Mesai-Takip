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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.AppNotification
import com.example.data.model.AppSettings
import com.example.data.model.NotificationType
import com.example.ui.theme.MoneyEmerald
import com.example.util.NotificationHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationCenterScreen(
    notifications: List<AppNotification>,
    settings: AppSettings,
    onMarkRead: (Long, Boolean) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteNotification: (Long) -> Unit,
    onClearAll: () -> Unit,
    onNavigateToTarget: (String) -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val systemNotificationsPermitted = remember(notifications) {
        NotificationHelper.canPostSystemNotifications(context)
    }

    var onlyUnread by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf<NotificationType?>(null) }
    var showReminderSettingsDialog by remember { mutableStateOf(false) }

    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

    val filteredList = remember(notifications, onlyUnread, selectedTypeFilter) {
        notifications.filter { item ->
            val unreadMatch = if (onlyUnread) !item.isRead else true
            val typeMatch = if (selectedTypeFilter != null) {
                NotificationType.fromName(item.type) == selectedTypeFilter
            } else true
            unreadMatch && typeMatch
        }
    }

    if (showReminderSettingsDialog) {
        ReminderPreferencesDialog(
            settings = settings,
            onDismiss = { showReminderSettingsDialog = false },
            onSave = { updated ->
                onSaveSettings(updated)
                showReminderSettingsDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("screen_notification_center"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            // Üst Başlık Çubuğu
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
                        modifier = Modifier.testTag("btn_back_from_notifications")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri Dön"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bildirim Merkezi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (unreadCount > 0) "$unreadCount okunmamış bildirim" else "Tüm bildirimler okundu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showReminderSettingsDialog = true },
                        modifier = Modifier.testTag("btn_open_reminder_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Hatırlatma Ayarları",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (unreadCount > 0) {
                        IconButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.testTag("btn_mark_all_read")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Tümünü Okundu İşaretle",
                                tint = MoneyEmerald
                            )
                        }
                    }
                    if (notifications.isNotEmpty()) {
                        IconButton(
                            onClick = onClearAll,
                            modifier = Modifier.testTag("btn_clear_all_notifications")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Tümünü Temizle",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // İzin Durumu Bilgilendirme Şeridi
            if (!systemNotificationsPermitted) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("banner_system_notification_permission_info")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cihaz bildirim izni kapalı olsa bile uygulama içi Bildirim Merkezi çalışmaya devam eder.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // Filtre Çipleri
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = !onlyUnread && selectedTypeFilter == null,
                        onClick = {
                            onlyUnread = false
                            selectedTypeFilter = null
                        },
                        label = { Text("Tümü (${notifications.size})") },
                        modifier = Modifier.testTag("chip_filter_all")
                    )
                    FilterChip(
                        selected = onlyUnread,
                        onClick = { onlyUnread = !onlyUnread },
                        label = { Text("Okunmamış ($unreadCount)") },
                        modifier = Modifier.testTag("chip_filter_unread")
                    )
                    NotificationType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedTypeFilter == type,
                            onClick = {
                                selectedTypeFilter = if (selectedTypeFilter == type) null else type
                            },
                            label = { Text(type.badgeText) }
                        )
                    }
                }
            }

            HorizontalDivider()

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .testTag("empty_notification_center_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (onlyUnread) "Okunmamış bildiriminiz bulunmuyor" else "Henüz bildirim kaydı bulunmuyor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Mesai hatırlatmaları, maaş günü uyarıları, eksik kayıt denetimleri ve başarı bildirimleri burada listelenir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { showReminderSettingsDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hatırlatma Tercihlerini Düzenle")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        NotificationItemCard(
                            item = item,
                            onClick = {
                                if (!item.isRead) {
                                    onMarkRead(item.id, true)
                                }
                                onNavigateToTarget(item.targetRoute)
                            },
                            onToggleRead = { onMarkRead(item.id, !item.isRead) },
                            onDelete = { onDeleteNotification(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItemCard(
    item: AppNotification,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit
) {
    val type = NotificationType.fromName(item.type)
    val (icon, tintColor) = remember(type) { notificationTypeVisuals(type) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("notification_item_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (item.isRead) 1.dp else 1.5.dp,
            color = if (item.isRead) MaterialTheme.colorScheme.outlineVariant
            else tintColor.copy(alpha = 0.6f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isRead) MaterialTheme.colorScheme.surface
            else tintColor.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(tintColor.copy(alpha = 0.14f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = type.labelTr,
                        tint = tintColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = tintColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = type.badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = tintColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.dateTimeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!item.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(tintColor)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onToggleRead,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_toggle_read_${item.id}")
                ) {
                    Icon(
                        imageVector = if (item.isRead) Icons.Filled.MarkEmailUnread else Icons.Filled.MarkEmailRead,
                        contentDescription = if (item.isRead) "Okunmadı İşaretle" else "Okundu İşaretle",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_delete_notif_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Bildirimi Sil",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (item.wasPostedToSystem) "Durum: Cihaz bildirimi + Uygulama içi kayıt"
                else "Durum: Yalnızca uygulama içi Bildirim Merkezi kaydı",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun notificationTypeVisuals(type: NotificationType): Pair<ImageVector, Color> {
    return when (type) {
        NotificationType.OVERTIME_REMINDER -> Icons.Filled.AccessTime to Color(0xFF2563EB)
        NotificationType.SALARY_REMINDER -> Icons.Filled.AccountBalanceWallet to Color(0xFF059669)
        NotificationType.UPCOMING_SHIFT -> Icons.Filled.Schedule to Color(0xFF4F46E5)
        NotificationType.MISSING_RECORD_ALERT -> Icons.Filled.Warning to Color(0xFFD97706)
        NotificationType.MONTHLY_SUMMARY -> Icons.Filled.Assessment to Color(0xFF0284C7)
        NotificationType.ACHIEVEMENT_UNLOCKED -> Icons.Filled.EmojiEvents to Color(0xFF9333EA)
        NotificationType.SYSTEM_BACKUP -> Icons.Filled.Backup to Color(0xFF475569)
    }
}

@Composable
private fun ReminderPreferencesDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var overtimeEntry by remember { mutableStateOf(settings.reminderOvertimeEntryEnabled) }
    var salaryDay by remember { mutableStateOf(settings.reminderSalaryDayEnabled) }
    var shiftStart by remember { mutableStateOf(settings.reminderShiftStartEnabled) }
    var missingRecord by remember { mutableStateOf(settings.reminderMissingRecordEnabled) }
    var monthlySummary by remember { mutableStateOf(settings.reminderMonthlySummaryEnabled) }
    var achievement by remember { mutableStateOf(settings.reminderAchievementEnabled) }
    var reminderTime by remember { mutableStateOf(settings.reminderTimeStr) }
    var leadMinsText by remember { mutableStateOf(settings.shiftReminderLeadMinutes.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Otomatik Hatırlatma ve Bildirim Ayarları",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReminderToggleRow("Mesai Kaydı Girme Hatırlatması", overtimeEntry) { overtimeEntry = it }
                ReminderToggleRow("Maaş Günü Hatırlatması", salaryDay) { salaryDay = it }
                ReminderToggleRow("Vardiya Başlangıç Hatırlatması", shiftStart) { shiftStart = it }
                ReminderToggleRow("Eksik Çalışma Kaydı Uyarısı", missingRecord) { missingRecord = it }
                ReminderToggleRow("Aylık Çalışma Özeti Bildirimi", monthlySummary) { monthlySummary = it }
                ReminderToggleRow("Başarı & Kilometre Taşı Bildirimi", achievement) { achievement = it }

                OutlinedTextField(
                    value = reminderTime,
                    onValueChange = { reminderTime = it },
                    label = { Text("Günlük Kayıt Hatırlatma Saati (SS:dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = leadMinsText,
                    onValueChange = { leadMinsText = it },
                    label = { Text("Vardiya Öncesi Hatırlatma Süresi (Dakika)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeLead = leadMinsText.toIntOrNull()?.coerceIn(5, 180) ?: settings.shiftReminderLeadMinutes
                    val safeTime = if (reminderTime.contains(":")) reminderTime.trim() else settings.reminderTimeStr
                    onSave(
                        settings.copy(
                            reminderOvertimeEntryEnabled = overtimeEntry,
                            reminderSalaryDayEnabled = salaryDay,
                            reminderShiftStartEnabled = shiftStart,
                            reminderMissingRecordEnabled = missingRecord,
                            reminderMonthlySummaryEnabled = monthlySummary,
                            reminderAchievementEnabled = achievement,
                            reminderTimeStr = safeTime,
                            shiftReminderLeadMinutes = safeLead
                        )
                    )
                },
                modifier = Modifier.testTag("btn_save_reminder_prefs")
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

@Composable
private fun ReminderToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
