package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import com.example.data.model.PayrollDocument
import com.example.ui.components.GooglePlaySubscribeCard
import com.example.ui.theme.MoneyEmerald
import com.example.util.AchievementBadge
import com.example.util.AchievementCalculator
import com.example.util.PeriodComparison
import com.example.util.TimeUtils
import java.time.YearMonth
import kotlin.math.abs

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AchievementsStatsScreen(
    isPremium: Boolean,
    selectedYearMonth: YearMonth,
    allRecords: List<OvertimeRecord>,
    settings: AppSettings,
    userDocuments: List<PayrollDocument>,
    onMonthChange: (Long) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onUpdateMonthlyGoals: (Int, Double) -> Unit,
    onNavigateBack: () -> Unit,
    archivedReportsCount: Int = 0,
    firstArchivedReportDate: String? = null
) {
    BackHandler { onNavigateBack() }

    var showEditGoalDialog by remember { mutableStateOf(false) }
    var selectedChartMode by remember { mutableIntStateOf(0) } // 0: Çalışma Günü, 1: Kayıt Sayısı, 2: Süre (Saat)
    var selectedStatusFilter by remember { mutableIntStateOf(0) } // 0: Tümü, 1: Kazanılanlar, 2: Henüz Kazanılmayanlar
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    val summary = remember(allRecords, selectedYearMonth, settings, userDocuments, archivedReportsCount, firstArchivedReportDate) {
        val firstDocDate = userDocuments.minByOrNull { it.documentDate }?.documentDate ?: firstArchivedReportDate
        AchievementCalculator.calculateSummary(
            allRecords = allRecords,
            selectedYearMonth = selectedYearMonth,
            settings = settings,
            archivedDocumentsCount = userDocuments.size + archivedReportsCount,
            firstArchivedDocumentDate = firstDocDate
        )
    }

    val allCombinedBadges = remember(summary) {
        summary.allBadges.ifEmpty { summary.badges }
    }

    val categories = remember(allCombinedBadges) {
        allCombinedBadges.map { it.category }.distinct()
    }

    val filteredBadges = remember(allCombinedBadges, selectedStatusFilter, selectedCategoryFilter) {
        allCombinedBadges.filter { badge ->
            val statusMatches = when (selectedStatusFilter) {
                1 -> badge.isUnlocked
                2 -> !badge.isUnlocked
                else -> true
            }
            val categoryMatches = selectedCategoryFilter == null || badge.category == selectedCategoryFilter
            statusMatches && categoryMatches
        }
    }

    if (showEditGoalDialog) {
        EditMonthlyGoalsDialog(
            initialTargetDays = summary.targetDays,
            initialTargetHours = summary.targetHours,
            onDismiss = { showEditGoalDialog = false },
            onSave = { days, hours ->
                showEditGoalDialog = false
                onUpdateMonthlyGoals(days, hours)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_from_achievements")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri Dön"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kişisel Başarılar ve İstatistikler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Hedef takibi, yıllık karşılaştırma ve rozetler",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isPremium) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_achievements_premium_locked"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.EmojiEvents,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Başarı ve Kilometre Taşları",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tüm başarı rozetleriniz ve çalışma kilometre taşlarınız aşağıda listelenmektedir.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Dönem Seçici
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onMonthChange(-1) },
                                    modifier = Modifier.testTag("btn_achievements_prev_month")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Önceki Ay"
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = TimeUtils.formatMonthYear(selectedYearMonth),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${summary.unlockedBadgesCount} / ${summary.badges.size} Rozet Kazanıldı",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MoneyEmerald,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                IconButton(
                                    onClick = { onMonthChange(1) },
                                    modifier = Modifier.testTag("btn_achievements_next_month")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Sonraki Ay"
                                    )
                                }
                            }
                        }
                    }

                    // 1. Aylık Çalışma Günü ve Tamamlanan Kayıt Kartları
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("card_stat_working_days"),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${summary.monthlyWorkingDaysCount} Gün",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Aylık Çalışma Günü",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("card_stat_completed_records"),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = null,
                                        tint = MoneyEmerald,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${summary.monthlyCompletedRecordsCount} Kayıt",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Tamamlanan Mesai",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Eksik veya Geçersiz Kayıt Filtresi Bilgisi
                    if (summary.excludedInvalidRecordsCount > 0) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("banner_excluded_invalid_records")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Veri bütünlüğü koruması: Süresi 0 veya eksik olan ${summary.excludedInvalidRecordsCount} geçersiz kayıt istatistik ve başarı hesabına dahil edilmedi.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // 2. Kullanıcının Belirlediği Aylık Çalışma Hedefleri & İlerleme Çubukları
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_monthly_goals"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
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
                                                    imageVector = Icons.Filled.Flag,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Aylık Çalışma Hedeflerim",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Kişisel hedef takibi ve ilerleme durumu",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { showEditGoalDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("btn_edit_monthly_goals")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hedef Belirle", style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Gün Hedefi İlerleme Çubuğu
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Aylık Kayıt Günü Hedefi (${summary.monthlyWorkingDaysCount} / ${summary.targetDays} gün)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "%${summary.daysGoalPercent}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (summary.daysGoalReached) MoneyEmerald else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (summary.daysGoalPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .testTag("progress_goal_days"),
                                    color = if (summary.daysGoalReached) MoneyEmerald else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Saat Hedefi İlerleme Çubuğu
                                val formattedCurrentHours = String.format(
                                    java.util.Locale.forLanguageTag("tr-TR"),
                                    "%.1f",
                                    summary.monthlyTotalHours
                                )
                                val formattedTargetHours = String.format(
                                    java.util.Locale.forLanguageTag("tr-TR"),
                                    "%.1f",
                                    summary.targetHours
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Aylık Süre Takip Hedefi ($formattedCurrentHours / $formattedTargetHours saat)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "%${summary.hoursGoalPercent}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (summary.hoursGoalReached) MoneyEmerald else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (summary.hoursGoalPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .testTag("progress_goal_hours"),
                                    color = if (summary.hoursGoalReached) MoneyEmerald else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }

                    // 3. Aylık ve Yıllık İstatistik Grafikleri
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_yearly_statistics_chart"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BarChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${selectedYearMonth.year} Yılı Aylık İstatistik Grafiği",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Ay üzerine dokunarak ilgili dönemi seçebilirsiniz",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = selectedChartMode == 0,
                                        onClick = { selectedChartMode = 0 },
                                        label = { Text("Çalışma Günü") },
                                        modifier = Modifier.testTag("chip_chart_mode_days")
                                    )
                                    FilterChip(
                                        selected = selectedChartMode == 1,
                                        onClick = { selectedChartMode = 1 },
                                        label = { Text("Kayıt Sayısı") },
                                        modifier = Modifier.testTag("chip_chart_mode_records")
                                    )
                                    FilterChip(
                                        selected = selectedChartMode == 2,
                                        onClick = { selectedChartMode = 2 },
                                        label = { Text("Süre (Saat)") },
                                        modifier = Modifier.testTag("chip_chart_mode_hours")
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                val maxVal = summary.yearlyMonthlyBreakdown.maxOfOrNull { pt ->
                                    when (selectedChartMode) {
                                        0 -> pt.workingDays.toDouble()
                                        1 -> pt.completedRecords.toDouble()
                                        else -> pt.totalHours
                                    }
                                }?.coerceAtLeast(1.0) ?: 1.0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    summary.yearlyMonthlyBreakdown.forEach { pt ->
                                        val value = when (selectedChartMode) {
                                            0 -> pt.workingDays.toDouble()
                                            1 -> pt.completedRecords.toDouble()
                                            else -> pt.totalHours
                                        }
                                        val ratio = (value / maxVal).toFloat().coerceIn(0.06f, 1f)
                                        val isSelectedMonth = pt.monthValue == selectedYearMonth.monthValue

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable {
                                                    onSelectMonth(YearMonth.of(selectedYearMonth.year, pt.monthValue))
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom
                                        ) {
                                            if (value > 0) {
                                                Text(
                                                    text = if (selectedChartMode == 2) {
                                                        String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.0f", value)
                                                    } else {
                                                        value.toInt().toString()
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    fontWeight = if (isSelectedMonth) FontWeight.ExtraBold else FontWeight.Normal,
                                                    color = if (isSelectedMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(14.dp)
                                                    .fillMaxHeight(ratio * 0.72f)
                                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                    .background(
                                                        when {
                                                            isSelectedMonth -> MaterialTheme.colorScheme.primary
                                                            value > 0.0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                                        }
                                                    )
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = pt.monthShortLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelectedMonth) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = if (isSelectedMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Önceki Ay ve Yıllarla Karşılaştırma
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_period_comparisons"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Önceki Ay ve Yıllarla Karşılaştırma",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                PeriodComparisonSection(
                                    title = "Önceki Ay ile Karşılaştırma",
                                    comparison = summary.previousMonthComparison,
                                    testTag = "section_prev_month_comparison"
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(12.dp))

                                PeriodComparisonSection(
                                    title = "Önceki Yıl ile Karşılaştırma",
                                    comparison = summary.previousYearComparison,
                                    testTag = "section_prev_year_comparison"
                                )
                            }
                        }
                    }

                    // 5. Kişisel Çalışma Kapasitesi Özeti (Özellik 68)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_personal_capacity_summary"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.BarChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Kişisel Çalışma Kapasitesi ve Düzen Analizi",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val avgDailyHours = if (summary.totalValidDaysAllTime > 0) {
                                        (summary.yearlyMonthlyBreakdown.sumOf { it.totalMinutes } / 60.0) / summary.totalValidDaysAllTime.coerceAtLeast(1)
                                    } else 0.0
                                    val totalRecs = (summary.totalValidRecordsAllTime + summary.excludedInvalidRecordsCount).coerceAtLeast(1)
                                    val cleanRatio = if (summary.totalValidRecordsAllTime == 0 && summary.excludedInvalidRecordsCount == 0) 100
                                    else ((summary.totalValidRecordsAllTime.toDouble() / totalRecs) * 100.0).toInt().coerceIn(0, 100)
                                    ComparisonMetricBox(
                                        label = "Toplam Kayıtlı Gün",
                                        currentText = "${summary.totalValidDaysAllTime} Farklı Gün",
                                        previousText = "Aylık: ${summary.monthlyWorkingDaysCount} gün",
                                        diffText = "Gerçek Kayıt",
                                        modifier = Modifier.weight(1f)
                                    )
                                    ComparisonMetricBox(
                                        label = "Günlük Ortalama",
                                        currentText = String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.1f Saat", avgDailyHours),
                                        previousText = "Toplam: ${summary.totalValidRecordsAllTime} kayıt",
                                        diffText = "Dengeli Tempo",
                                        modifier = Modifier.weight(1f)
                                    )
                                    ComparisonMetricBox(
                                        label = "Kayıt Düzeni",
                                        currentText = "%$cleanRatio",
                                        previousText = "Eksiksiz Kayıt",
                                        diffText = "Kalite Puanı",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // 6. Rozetler ve Kilometre Taşları Başlığı & Filtreler
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.EmojiEvents,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Başarılar ve Kilometre Taşları (${allCombinedBadges.count { it.isUnlocked }} / ${allCombinedBadges.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "Başarı sistemi gereksiz fazla mesai yapmayı teşvik etmez; düzenli kayıt tutma, kişisel hedef takibi ve veri bütünlüğü ölçütlerine dayanır.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                // Kazanılan / Henüz Kazanılmayan Filtresi
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedStatusFilter == 0,
                                        onClick = { selectedStatusFilter = 0 },
                                        label = { Text("Tümü (${allCombinedBadges.size})") },
                                        modifier = Modifier.testTag("chip_badges_all")
                                    )
                                    FilterChip(
                                        selected = selectedStatusFilter == 1,
                                        onClick = { selectedStatusFilter = 1 },
                                        label = { Text("Kazanılan (${allCombinedBadges.count { it.isUnlocked }})") },
                                        modifier = Modifier.testTag("chip_badges_unlocked")
                                    )
                                    FilterChip(
                                        selected = selectedStatusFilter == 2,
                                        onClick = { selectedStatusFilter = 2 },
                                        label = { Text("Kilitli (${allCombinedBadges.count { !it.isUnlocked }})") },
                                        modifier = Modifier.testTag("chip_badges_locked")
                                    )
                                }

                                // Kategori Filtresi
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedCategoryFilter == null,
                                        onClick = { selectedCategoryFilter = null },
                                        label = { Text("Tüm Kategoriler", fontSize = 11.sp) }
                                    )
                                    categories.forEach { cat ->
                                        FilterChip(
                                            selected = selectedCategoryFilter == cat,
                                            onClick = {
                                                selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                                            },
                                            label = { Text(cat, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Rozet Kartları Listesi
                    items(filteredBadges, key = { it.id }) { badge ->
                        AchievementBadgeCard(badge = badge)
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
        }
    }
}

@Composable
private fun PeriodComparisonSection(
    title: String,
    comparison: PeriodComparison,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${comparison.currentLabel} vs ${comparison.previousLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ComparisonMetricBox(
                label = "Çalışma Günü",
                currentText = "${comparison.currentWorkingDays} gün",
                previousText = "Önceki: ${comparison.previousWorkingDays} gün",
                diffText = formatSignedInt(comparison.workingDaysDiff, "gün"),
                modifier = Modifier.weight(1f)
            )
            ComparisonMetricBox(
                label = "Kayıt Sayısı",
                currentText = "${comparison.currentRecordsCount} kayıt",
                previousText = "Önceki: ${comparison.previousRecordsCount} kayıt",
                diffText = formatSignedInt(comparison.recordsDiff, "kayıt"),
                modifier = Modifier.weight(1f)
            )
            ComparisonMetricBox(
                label = "Toplam Süre",
                currentText = String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.1f sa", comparison.currentHours),
                previousText = String.format(java.util.Locale.forLanguageTag("tr-TR"), "Önceki: %.1f sa", comparison.previousHours),
                diffText = formatSignedDouble(comparison.hoursDiff, "sa"),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ComparisonMetricBox(
    label: String,
    currentText: String,
    previousText: String,
    diffText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = previousText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = diffText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AchievementBadgeCard(badge: AchievementBadge) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_badge_${badge.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (badge.isUnlocked) {
            BorderStroke(1.5.dp, MoneyEmerald.copy(alpha = 0.6f))
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (badge.isUnlocked) {
                        MoneyEmerald.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (badge.isUnlocked) Icons.Filled.WorkspacePremium else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (badge.isUnlocked) MoneyEmerald else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = badge.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (badge.isUnlocked) {
                                MoneyEmerald.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        ) {
                            Text(
                                text = badge.category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (badge.isUnlocked) MoneyEmerald else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Koşul: ${badge.conditionDescription}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (badge.isUnlocked && !badge.unlockedDateStr.isNullOrBlank()) {
                        "Kazanıldı • Tarih: ${TimeUtils.formatDateTurkish(badge.unlockedDateStr)}"
                    } else {
                        "İlerleme: ${badge.currentProgress} / ${badge.targetProgress}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (badge.isUnlocked) MoneyEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "%${badge.progressPercent}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (badge.isUnlocked) MoneyEmerald else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { badge.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (badge.isUnlocked) MoneyEmerald else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun EditMonthlyGoalsDialog(
    initialTargetDays: Int,
    initialTargetHours: Double,
    onDismiss: () -> Unit,
    onSave: (Int, Double) -> Unit
) {
    var daysText by remember { mutableStateOf(initialTargetDays.toString()) }
    var hoursText by remember { mutableStateOf(initialTargetHours.toInt().toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Aylık Çalışma Hedeflerini Belirle",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Aylık düzenli kayıt tutma günü ve takip saati hedeflerinizi kendi çalışma düzeninize göre belirleyin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = daysText,
                    onValueChange = {
                        daysText = it
                        errorText = null
                    },
                    label = { Text("Aylık Kayıt Günü Hedefi (1 - 31 gün)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_goal_target_days")
                )

                OutlinedTextField(
                    value = hoursText,
                    onValueChange = {
                        hoursText = it
                        errorText = null
                    },
                    label = { Text("Aylık Süre Takip Hedefi (Saat)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_goal_target_hours")
                )

                errorText?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = daysText.trim().toIntOrNull()
                    val h = hoursText.trim().replace(',', '.').toDoubleOrNull()
                    if (d == null || d !in 1..31) {
                        errorText = "Lütfen 1 ile 31 arasında geçerli bir gün hedefi girin."
                        return@Button
                    }
                    if (h == null || h <= 0.0 || h > 300.0) {
                        errorText = "Lütfen 1 ile 300 saat arasında geçerli bir süre hedefi girin."
                        return@Button
                    }
                    onSave(d, h)
                },
                modifier = Modifier.testTag("btn_save_monthly_goals")
            ) {
                Text("Hedefleri Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

private fun formatSignedInt(diff: Int, unit: String): String {
    return when {
        diff > 0 -> "+$diff $unit"
        diff < 0 -> "-${abs(diff)} $unit"
        else -> "Değişim yok"
    }
}

private fun formatSignedDouble(diff: Double, unit: String): String {
    val formatted = String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.1f", abs(diff))
    return when {
        diff > 0.05 -> "+$formatted $unit"
        diff < -0.05 -> "-$formatted $unit"
        else -> "Değişim yok"
    }
}
