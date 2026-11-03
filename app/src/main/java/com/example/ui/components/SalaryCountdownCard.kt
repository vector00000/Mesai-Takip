package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import com.example.ui.theme.MoneyEmerald
import com.example.ui.theme.MoneyEmeraldLight
import com.example.ui.theme.OvertimeAmber
import com.example.ui.theme.PrimaryLight
import com.example.util.TimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SalaryCountdownCard(
    settings: AppSettings,
    allRecords: List<OvertimeRecord>,
    onSaveSettings: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
    activeOvertime: com.example.data.model.ActiveOvertime? = null,
    liveEarnings: Double = 0.0,
    liveElapsedSeconds: Long = 0L
) {
    var showEditDialog by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now(TimeUtils.ISTANBUL_ZONE) }
    val salaryDay = settings.salaryDayOfMonth.coerceIn(1, 31)

    // Calculate current cycle dates and remaining days
    val currentMonthSalaryDate = remember(today, salaryDay) {
        val daysInCurrentMonth = today.lengthOfMonth()
        today.withDayOfMonth(min(salaryDay, daysInCurrentMonth))
    }

    val (nextSalaryDate, prevSalaryDate) = remember(today, salaryDay, currentMonthSalaryDate) {
        when {
            today.isBefore(currentMonthSalaryDate) -> {
                val prevMonth = today.minusMonths(1)
                val prevDate = prevMonth.withDayOfMonth(min(salaryDay, prevMonth.lengthOfMonth()))
                Pair(currentMonthSalaryDate, prevDate)
            }
            today.isEqual(currentMonthSalaryDate) -> {
                val nextMonth = today.plusMonths(1)
                val nextDate = nextMonth.withDayOfMonth(min(salaryDay, nextMonth.lengthOfMonth()))
                Pair(currentMonthSalaryDate, currentMonthSalaryDate)
            }
            else -> {
                val nextMonth = today.plusMonths(1)
                val nextDate = nextMonth.withDayOfMonth(min(salaryDay, nextMonth.lengthOfMonth()))
                Pair(nextDate, currentMonthSalaryDate)
            }
        }
    }

    val isSalaryDayToday = today.isEqual(currentMonthSalaryDate)
    val daysRemaining = remember(today, nextSalaryDate, isSalaryDayToday) {
        if (isSalaryDayToday) 0L else ChronoUnit.DAYS.between(today, nextSalaryDate)
    }

    val cycleProgress = remember(today, prevSalaryDate, nextSalaryDate, isSalaryDayToday) {
        if (isSalaryDayToday) {
            1f
        } else {
            val totalDays = ChronoUnit.DAYS.between(prevSalaryDate, nextSalaryDate).coerceAtLeast(1)
            val passedDays = ChronoUnit.DAYS.between(prevSalaryDate, today).coerceAtLeast(0)
            (passedDays.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
        }
    }

    // Maaş dönemine göre mesai kayıtlarını filtrele (Örn. 12 Eylül - 12 Ekim arası)
    val cycleRecords = remember(allRecords, prevSalaryDate, nextSalaryDate, today) {
        allRecords.filter { record ->
            val recordDate = try { LocalDate.parse(record.date) } catch (_: Exception) { null }
            if (recordDate != null) {
                (recordDate.isEqual(prevSalaryDate) || recordDate.isAfter(prevSalaryDate)) && recordDate.isBefore(nextSalaryDate)
            } else {
                val prefix = "${today.year}-${"%02d".format(today.monthValue)}"
                record.date.startsWith(prefix)
            }
        }
    }

    val completedOvertimeEarnings = remember(cycleRecords) {
        cycleRecords.sumOf { it.totalAmount }
    }
    val completedOvertimeMinutes = remember(cycleRecords) {
        cycleRecords.sumOf { it.durationMinutes }
    }

    // Aktif canlı mesai varsa dönem toplamına canlı olarak eklenir
    val isLiveActive = activeOvertime?.isActive == true
    val activeMinutes = if (isLiveActive) (liveElapsedSeconds / 60L).toInt() else 0
    val activeAmount = if (isLiveActive) liveEarnings else 0.0

    val currentMonthOvertimeEarnings = completedOvertimeEarnings + activeAmount
    val currentMonthOvertimeMinutes = completedOvertimeMinutes + activeMinutes

    val baseSalary = settings.baseSalary
    val totalEstimatedEarnings = baseSalary + currentMonthOvertimeEarnings

    val trLocale = Locale.forLanguageTag("tr-TR")
    val nextSalaryFormatted = remember(nextSalaryDate) {
        nextSalaryDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", trLocale))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("salary_countdown_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Icon, Title & Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Maaş & Hakediş Sayacı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Her ayın ${settings.salaryDayOfMonth}. günü maaş",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.testTag("btn_edit_salary_settings")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Maaşı ve Günü Düzenle",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Countdown Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (isSalaryDayToday) MoneyEmeraldLight
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSalaryDayToday) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Celebration,
                                    contentDescription = null,
                                    tint = MoneyEmerald,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Bugün Maaş Günü! 🎉",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MoneyEmerald
                                )
                            }
                        } else {
                            Column {
                                Text(
                                    text = "Maaş Gününüze",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$daysRemaining Gün Kaldı",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Sonraki Maaş",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = nextSalaryFormatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress bar for the salary cycle
                    LinearProgressIndicator(
                        progress = { cycleProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (isSalaryDayToday) MoneyEmerald else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Maaş Döngüsü",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "%${(cycleProgress * 100).roundToInt()} tamamlandı",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Breakdown Table: Taban Maaş + Mesai = Tahmini Toplam
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Taban Maaş Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEditDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Taban Net Maaş",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (baseSalary <= 0.0) {
                                Text(
                                    text = "Maaşınızı girmek için dokunun ⚙️",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = if (baseSalary > 0.0) TimeUtils.formatCurrency(baseSalary) else "Tanımlanmadı",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (baseSalary > 0.0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bu Ayki Mesai Kazancı Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isLiveActive) "Dönem Mesai Kazancı (Canlı Dahil)" else "Dönem Mesai Kazancı",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isLiveActive) {
                                    "${TimeUtils.formatDuration(currentMonthOvertimeMinutes)}  •  Canlı: +${TimeUtils.formatCurrency(activeAmount)}"
                                } else {
                                    TimeUtils.formatDuration(currentMonthOvertimeMinutes)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLiveActive) MoneyEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isLiveActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }

                        Text(
                            text = "+ ${TimeUtils.formatCurrency(currentMonthOvertimeEarnings)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MoneyEmerald
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Toplam Tahmini Hakediş Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tahmini Toplam Hakediş",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Maaş günü elinize geçecek tahmini tutar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = TimeUtils.formatCurrency(totalEstimatedEarnings),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MoneyEmerald
                        )
                    }
                }
            }
        }
    }

    // Quick Edit Salary Settings Dialog
    if (showEditDialog) {
        var baseSalaryInput by remember {
            mutableStateOf(
                if (settings.baseSalary > 0.0) {
                    if (settings.baseSalary % 1.0 == 0.0) settings.baseSalary.toLong().toString() else settings.baseSalary.toString()
                } else ""
            )
        }
        var salaryDayInput by remember { mutableStateOf(settings.salaryDayOfMonth.toString()) }
        var monthlyWorkHoursInput by remember {
            mutableStateOf(
                if (settings.monthlyWorkHours > 0.0) {
                    if (settings.monthlyWorkHours % 1.0 == 0.0) settings.monthlyWorkHours.toLong().toString() else settings.monthlyWorkHours.toString()
                } else "225"
            )
        }
        var autoCalculateRates by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = true
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Maaş & Bordro Ayarları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Her çalışanın maaşı ve maaş günü farklıdır. Kendi taban maaşınızı ve maaş alma gününüzü belirleyin:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Base Salary Input
                    OutlinedTextField(
                        value = baseSalaryInput,
                        onValueChange = { baseSalaryInput = it },
                        label = { Text("Aylık Net Taban Maaş (₺)") },
                        placeholder = { Text("Örn: 35000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_base_salary")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Salary Day Input (1-31)
                    OutlinedTextField(
                        value = salaryDayInput,
                        onValueChange = { salaryDayInput = it },
                        label = { Text("Maaş Günü (Ayın kaçı?)") },
                        placeholder = { Text("1 - 31 arası, örn: 15 veya 1") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_salary_day")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Monthly Work Hours Input
                    OutlinedTextField(
                        value = monthlyWorkHoursInput,
                        onValueChange = { monthlyWorkHoursInput = it },
                        label = { Text("Aylık Çalışma Saati (saat)") },
                        placeholder = { Text("Yasal 225 saat veya örn: 180, 200") },
                        supportingText = { Text("Kendi çalışma saatinize göre mesai hesaplanır") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_work_hours")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Smart Hourly Rate Calculator Toggle / Info
                    val parsedSalary = baseSalaryInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val parsedHours = (monthlyWorkHoursInput.replace(',', '.').toDoubleOrNull() ?: 225.0).coerceAtLeast(1.0)
                    if (parsedSalary > 0) {
                        val baseHourly = parsedSalary / parsedHours
                        val calcWeekdayRate = (baseHourly * 1.5).let { kotlin.math.round(it * 100.0) / 100.0 }
                        val calcWeekendRate = (baseHourly * 2.0).let { kotlin.math.round(it * 100.0) / 100.0 }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { autoCalculateRates = !autoCalculateRates }
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Calculate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Kendi Çalışma Saatinizle Hesapla (${parsedHours.toInt()} sa)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Normal: ${TimeUtils.formatCurrency(baseHourly)}/sa  •  Hafta içi: ${TimeUtils.formatCurrency(calcWeekdayRate)}/sa  •  Hafta sonu: ${TimeUtils.formatCurrency(calcWeekendRate)}/sa",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (autoCalculateRates) "✓ Saatlik mesai ücretleriniz bu saat ve maaşa göre güncellenecek"
                                           else "Dokunarak saatlik mesai tarifelerinizi bu hesaba göre eşitleyin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (autoCalculateRates) MoneyEmerald else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val salary = baseSalaryInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                        val day = (salaryDayInput.toIntOrNull() ?: 1).coerceIn(1, 31)
                        val workHours = (monthlyWorkHoursInput.replace(',', '.').toDoubleOrNull() ?: settings.monthlyWorkHours).coerceAtLeast(1.0)

                        var updated = settings.copy(
                            baseSalary = salary,
                            salaryDayOfMonth = day,
                            monthlyWorkHours = workHours
                        )

                        if (autoCalculateRates && salary > 0) {
                            val baseHourly = salary / workHours
                            val weekday = (baseHourly * 1.5).let { kotlin.math.round(it * 100.0) / 100.0 }
                            val weekend = (baseHourly * 2.0).let { kotlin.math.round(it * 100.0) / 100.0 }
                            updated = updated.copy(
                                weekdayRate = weekday,
                                weekendRate = weekend
                            )
                        }

                        onSaveSettings(updated)
                        showEditDialog = false
                    },
                    modifier = Modifier.testTag("btn_save_salary_settings")
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
