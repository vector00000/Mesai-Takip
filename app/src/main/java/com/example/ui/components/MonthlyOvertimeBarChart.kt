package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OvertimeRecord
import com.example.ui.theme.MoneyEmerald
import com.example.ui.theme.OvertimeAmber
import com.example.ui.theme.PrimaryLight
import com.example.util.TimeUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

enum class ChartViewMode(val title: String) {
    LAST_SIX_MONTHS("Son 6 Ay"),
    THIS_MONTH_WEEKS("Haftalık")
}

data class MonthBarData(
    val yearMonth: YearMonth,
    val label: String,
    val weekdayMinutes: Int,
    val weekendMinutes: Int,
    val totalMinutes: Int,
    val totalEarnings: Double,
    val shiftCount: Int
) {
    val totalHours: Float get() = totalMinutes / 60f
    val weekdayHours: Float get() = weekdayMinutes / 60f
    val weekendHours: Float get() = weekendMinutes / 60f
}

data class WeekBarData(
    val weekNumber: Int,
    val label: String,
    val dateRangeLabel: String,
    val weekdayMinutes: Int,
    val weekendMinutes: Int,
    val totalMinutes: Int,
    val totalEarnings: Double,
    val shiftCount: Int
) {
    val totalHours: Float get() = totalMinutes / 60f
    val weekdayHours: Float get() = weekdayMinutes / 60f
    val weekendHours: Float get() = weekendMinutes / 60f
}

/**
 * Modern, interactive Bar Chart component for visualizing monthly and weekly overtime hours.
 */
@Composable
fun MonthlyOvertimeBarChart(
    allRecords: List<OvertimeRecord>,
    selectedYearMonth: YearMonth,
    onSelectMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(ChartViewMode.LAST_SIX_MONTHS) }
    var highlightedMonth by remember(selectedYearMonth) { mutableStateOf<YearMonth?>(selectedYearMonth) }
    var highlightedWeekIndex by remember(selectedYearMonth) { mutableStateOf<Int?>(null) }

    // 1. Calculate Monthly Data for the last 6 months (ending with selectedYearMonth)
    val monthlyData = remember(allRecords, selectedYearMonth) {
        val months = (5 downTo 0).map { offset ->
            selectedYearMonth.minusMonths(offset.toLong())
        }

        months.map { ym ->
            val prefix = ym.toString() // "yyyy-MM"
            val monthRecords = allRecords.filter { it.date.startsWith(prefix) }

            val weekdayRecords = monthRecords.filter {
                !it.overtimeType.contains("WEEKEND", ignoreCase = true) &&
                !it.overtimeType.contains("Hafta Sonu", ignoreCase = true)
            }
            val weekendRecords = monthRecords.filter {
                it.overtimeType.contains("WEEKEND", ignoreCase = true) ||
                it.overtimeType.contains("Hafta Sonu", ignoreCase = true)
            }

            val weekdayMins = weekdayRecords.sumOf { it.durationMinutes }
            val weekendMins = weekendRecords.sumOf { it.durationMinutes }
            val totalMins = weekdayMins + weekendMins
            val totalEarn = monthRecords.sumOf { it.totalAmount }

            val trLocale = Locale.forLanguageTag("tr-TR")
            val monthName = ym.month.getDisplayName(TextStyle.SHORT, trLocale).replace(".", "")
            val yearSuffix = "'${ym.year.toString().takeLast(2)}"

            MonthBarData(
                yearMonth = ym,
                label = "$monthName $yearSuffix",
                weekdayMinutes = weekdayMins,
                weekendMinutes = weekendMins,
                totalMinutes = totalMins,
                totalEarnings = totalEarn,
                shiftCount = monthRecords.size
            )
        }
    }

    // 2. Calculate Weekly Data for selectedYearMonth
    val weeklyData = remember(allRecords, selectedYearMonth) {
        val prefix = selectedYearMonth.toString()
        val monthRecords = allRecords.filter { it.date.startsWith(prefix) }
        val daysInMonth = selectedYearMonth.lengthOfMonth()

        // 5 standard blocks of 7 days: 1-7, 8-14, 15-21, 22-28, 29-end
        val weeks = listOf(
            Triple(1, 1, 7),
            Triple(2, 8, 14),
            Triple(3, 15, 21),
            Triple(4, 22, 28),
            Triple(5, 29, daysInMonth)
        ).filter { it.second <= daysInMonth }

        weeks.map { (weekNum, startDay, endDay) ->
            val actualEnd = minOf(endDay, daysInMonth)
            val weekRecords = monthRecords.filter { rec ->
                val day = try {
                    LocalDate.parse(rec.date).dayOfMonth
                } catch (e: Exception) {
                    0
                }
                day in startDay..actualEnd
            }

            val weekdayRecords = weekRecords.filter {
                !it.overtimeType.contains("WEEKEND", ignoreCase = true) &&
                !it.overtimeType.contains("Hafta Sonu", ignoreCase = true)
            }
            val weekendRecords = weekRecords.filter {
                it.overtimeType.contains("WEEKEND", ignoreCase = true) ||
                it.overtimeType.contains("Hafta Sonu", ignoreCase = true)
            }

            val weekdayMins = weekdayRecords.sumOf { it.durationMinutes }
            val weekendMins = weekendRecords.sumOf { it.durationMinutes }

            WeekBarData(
                weekNumber = weekNum,
                label = "$weekNum. Hafta",
                dateRangeLabel = "$startDay-$actualEnd ${selectedYearMonth.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("tr-TR")).replace(".", "")}",
                weekdayMinutes = weekdayMins,
                weekendMinutes = weekendMins,
                totalMinutes = weekdayMins + weekendMins,
                totalEarnings = weekRecords.sumOf { it.totalAmount },
                shiftCount = weekRecords.size
            )
        }
    }

    // Summary statistics for 6 months
    val maxMonthlyHours = remember(monthlyData) {
        max(monthlyData.maxOfOrNull { it.totalHours } ?: 0f, 5f)
    }
    val avgMonthlyHours = remember(monthlyData) {
        val totalMins = monthlyData.sumOf { it.totalMinutes }
        (totalMins / 60f) / monthlyData.size.coerceAtLeast(1)
    }
    val bestMonth = remember(monthlyData) {
        monthlyData.maxByOrNull { it.totalMinutes }
    }

    val maxWeeklyHours = remember(weeklyData) {
        max(weeklyData.maxOfOrNull { it.totalHours } ?: 0f, 5f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_overtime_bar_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Title and Mode Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.QueryStats,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Mesai Saatleri Grafiği",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (viewMode == ChartViewMode.LAST_SIX_MONTHS) "Son 6 aylık mesai dağılımı" else "${TimeUtils.formatMonthYear(selectedYearMonth)} haftalık dağılımı",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mode Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChartViewMode.values().forEach { mode ->
                    val isSelected = viewMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                            )
                            .clickable {
                                viewMode = mode
                                highlightedWeekIndex = null
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(
                    title = "6 Aylık Ort.",
                    value = String.format(Locale.US, "%.1f sa", avgMonthlyHours),
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    title = "En Yüksek",
                    value = if (bestMonth != null && bestMonth.totalMinutes > 0) {
                        String.format(Locale.US, "%.1f sa", bestMonth.totalHours)
                    } else {
                        "0.0 sa"
                    },
                    modifier = Modifier.weight(1f)
                )
                val currentMonthTotal = monthlyData.find { it.yearMonth == selectedYearMonth }?.totalHours ?: 0f
                StatPill(
                    title = "Seçili Ay",
                    value = String.format(Locale.US, "%.1f sa", currentMonthTotal),
                    isHighlighted = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = PrimaryLight, label = "Hafta İçi")
                Spacer(modifier = Modifier.width(16.dp))
                LegendItem(color = OvertimeAmber, label = "Hafta Sonu")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bar Chart Area
            if (viewMode == ChartViewMode.LAST_SIX_MONTHS) {
                MonthlyBarsView(
                    monthlyData = monthlyData,
                    maxHours = maxMonthlyHours,
                    selectedYearMonth = selectedYearMonth,
                    highlightedMonth = highlightedMonth,
                    onBarClick = { ym ->
                        highlightedMonth = ym
                        if (ym != selectedYearMonth) {
                            onSelectMonth(ym)
                        }
                    }
                )
            } else {
                WeeklyBarsView(
                    weeklyData = weeklyData,
                    maxHours = maxWeeklyHours,
                    highlightedIndex = highlightedWeekIndex,
                    onBarClick = { idx ->
                        highlightedWeekIndex = if (highlightedWeekIndex == idx) null else idx
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Detail Card for Highlighted Element
            if (viewMode == ChartViewMode.LAST_SIX_MONTHS) {
                val activeData = monthlyData.find { it.yearMonth == highlightedMonth }
                if (activeData != null) {
                    SelectedMonthDetailBox(
                        data = activeData,
                        isCurrentSelection = activeData.yearMonth == selectedYearMonth
                    )
                }
            } else {
                val activeWeek = highlightedWeekIndex?.let { weeklyData.getOrNull(it) }
                if (activeWeek != null) {
                    SelectedWeekDetailBox(data = activeWeek)
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthlyBarsView(
    monthlyData: List<MonthBarData>,
    maxHours: Float,
    selectedYearMonth: YearMonth,
    highlightedMonth: YearMonth?,
    onBarClick: (YearMonth) -> Unit
) {
    val chartHeight = 170.dp
    val safeMax = ceil(maxHours.coerceAtLeast(4f) / 5f) * 5f // round up to multiple of 5

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            // Background horizontal grid guide lines (at 100%, 66%, 33%)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // Bars Row
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                monthlyData.forEach { item ->
                    val isSelectedMonth = item.yearMonth == selectedYearMonth
                    val isHighlighted = item.yearMonth == highlightedMonth

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onBarClick(item.yearMonth)
                            }
                            .testTag("bar_month_${item.yearMonth}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Value label above bar
                        Text(
                            text = if (item.totalMinutes > 0) {
                                String.format(Locale.US, "%.1f", item.totalHours)
                            } else {
                                "-"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = if (isSelectedMonth) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelectedMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // The Stacked Bar
                        val availableBarHeight = chartHeight - 50.dp
                        val totalFraction = (item.totalHours / safeMax).coerceIn(0f, 1f)
                        val animatedTotalFrac by animateFloatAsState(
                            targetValue = totalFraction,
                            animationSpec = tween(durationMillis = 600),
                            label = "monthly_bar_${item.yearMonth}"
                        )

                        val weekdayFraction = if (item.totalHours > 0) (item.weekdayHours / item.totalHours) else 1f
                        val weekendFraction = if (item.totalHours > 0) (item.weekendHours / item.totalHours) else 0f

                        Box(
                            modifier = Modifier
                                .width(if (isSelectedMonth) 26.dp else 22.dp)
                                .height(availableBarHeight)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (animatedTotalFrac > 0.01f) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animatedTotalFrac)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                ) {
                                    // Weekend portion (top)
                                    if (weekendFraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(weekendFraction.coerceAtLeast(0.01f))
                                                .background(OvertimeAmber)
                                        )
                                    }
                                    // Weekday portion (bottom)
                                    if (weekdayFraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(weekdayFraction.coerceAtLeast(0.01f))
                                                .background(if (isSelectedMonth) PrimaryLight else PrimaryLight.copy(alpha = 0.85f))
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Month Label Below Bar
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelectedMonth) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = if (isSelectedMonth) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelectedMonth) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarsView(
    weeklyData: List<WeekBarData>,
    maxHours: Float,
    highlightedIndex: Int?,
    onBarClick: (Int) -> Unit
) {
    val chartHeight = 170.dp
    val safeMax = ceil(maxHours.coerceAtLeast(3f) / 3f) * 3f

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
        ) {
            // Background horizontal grid lines
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // Weekly Bars Row
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyData.forEachIndexed { index, item ->
                    val isHighlighted = highlightedIndex == index

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onBarClick(index)
                            }
                            .testTag("bar_week_${item.weekNumber}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Value label above bar
                        Text(
                            text = if (item.totalMinutes > 0) {
                                String.format(Locale.US, "%.1f", item.totalHours)
                            } else {
                                "-"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val availableBarHeight = chartHeight - 50.dp
                        val totalFraction = (item.totalHours / safeMax).coerceIn(0f, 1f)
                        val animatedTotalFrac by animateFloatAsState(
                            targetValue = totalFraction,
                            animationSpec = tween(durationMillis = 600),
                            label = "weekly_bar_${item.weekNumber}"
                        )

                        val weekdayFraction = if (item.totalHours > 0) (item.weekdayHours / item.totalHours) else 1f
                        val weekendFraction = if (item.totalHours > 0) (item.weekendHours / item.totalHours) else 0f

                        Box(
                            modifier = Modifier
                                .width(if (isHighlighted) 28.dp else 22.dp)
                                .height(availableBarHeight)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (animatedTotalFrac > 0.01f) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animatedTotalFrac)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                ) {
                                    if (weekendFraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(weekendFraction.coerceAtLeast(0.01f))
                                                .background(OvertimeAmber)
                                        )
                                    }
                                    if (weekdayFraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(weekdayFraction.coerceAtLeast(0.01f))
                                                .background(PrimaryLight)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = "${item.weekNumber}.Hft",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                                color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedMonthDetailBox(
    data: MonthBarData,
    isCurrentSelection: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isCurrentSelection) androidx.compose.foundation.BorderStroke(1.dp, PrimaryLight.copy(alpha = 0.4f)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = TimeUtils.formatMonthYear(data.yearMonth),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (isCurrentSelection) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Aktif Rapor",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${data.shiftCount} Mesai",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Toplam Mesai",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = TimeUtils.formatDuration(data.totalMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Hafta İçi / Hafta Sonu",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${TimeUtils.formatDuration(data.weekdayMinutes)} / ${TimeUtils.formatDuration(data.weekendMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Toplam Kazanç",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = TimeUtils.formatCurrency(data.totalEarnings),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MoneyEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedWeekDetailBox(data: WeekBarData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${data.label} (${data.dateRangeLabel})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${data.shiftCount} Mesai",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Haftalık Mesai",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = TimeUtils.formatDuration(data.totalMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Haftalık Kazanç",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = TimeUtils.formatCurrency(data.totalEarnings),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MoneyEmerald
                    )
                }
            }
        }
    }
}
