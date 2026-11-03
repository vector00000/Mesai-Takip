package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import com.example.ui.components.MonthlyOvertimeBarChart
import com.example.ui.components.findActivity
import com.example.ui.theme.MoneyEmerald
import com.example.util.TimeUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    selectedYearMonth: YearMonth,
    monthlyRecords: List<OvertimeRecord>,
    allRecords: List<OvertimeRecord> = emptyList(),
    settings: AppSettings,
    isPremium: Boolean = false,
    onMonthChange: (Long) -> Unit,
    onSelectMonth: (YearMonth) -> Unit = {},
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    onOpenDocumentArchive: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onOpenBudgetTracking: () -> Unit = {},
    onOpenTariffAndAudit: () -> Unit = {},
    onOpenNotificationCenter: () -> Unit = {}
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

    var premiumPromptFeature by remember { mutableStateOf<String?>(null) }
    var selectedDayRecords by remember { mutableStateOf<Pair<LocalDate, List<OvertimeRecord>>?>(null) }

    if (premiumPromptFeature != null) {
        com.example.ui.components.GooglePlaySubscriptionDialog(
            isPremium = isPremium,
            subscriptionStatus = subscriptionStatus,
            formattedPrice = formattedPrice,
            billingPeriodText = billingPeriodText,
            featureContextTitle = premiumPromptFeature,
            onDismiss = { premiumPromptFeature = null },
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

    // Aggregate monthly statistics
    val weekdayRecords = remember(monthlyRecords) {
        monthlyRecords.filter {
            !it.overtimeType.contains("WEEKEND", ignoreCase = true) &&
            !it.overtimeType.contains("Hafta Sonu", ignoreCase = true)
        }
    }
    val weekendRecords = remember(monthlyRecords) {
        monthlyRecords.filter {
            it.overtimeType.contains("WEEKEND", ignoreCase = true) ||
            it.overtimeType.contains("Hafta Sonu", ignoreCase = true)
        }
    }

    val totalWeekdayMinutes = remember(weekdayRecords) { weekdayRecords.sumOf { it.durationMinutes } }
    val totalWeekendMinutes = remember(weekendRecords) { weekendRecords.sumOf { it.durationMinutes } }
    val totalMinutes = totalWeekdayMinutes + totalWeekendMinutes

    val totalWeekdayEarnings = remember(weekdayRecords) { weekdayRecords.sumOf { it.totalAmount } }
    val totalWeekendEarnings = remember(weekendRecords) { weekendRecords.sumOf { it.totalAmount } }
    val totalEarnings = totalWeekdayEarnings + totalWeekendEarnings

    // Map records by LocalDate for quick lookup in calendar
    val recordsByDate = remember(monthlyRecords) {
        val map = mutableMapOf<LocalDate, MutableList<OvertimeRecord>>()
        monthlyRecords.forEach { rec ->
            val parsed = try { LocalDate.parse(rec.date) } catch (e: Exception) { null }
            if (parsed != null) {
                map.getOrPut(parsed) { mutableListOf() }.add(rec)
            }
        }
        map
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Aylık Rapor ve Takvim",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Month Selector Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onMonthChange(-1) },
                        modifier = Modifier.testTag("btn_prev_month")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "Önceki Ay"
                        )
                    }

                    Text(
                        text = TimeUtils.formatMonthYear(selectedYearMonth),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = { onMonthChange(1) },
                        modifier = Modifier.testTag("btn_next_month")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Sonraki Ay"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Monthly Summary Card (Requirement #11)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_summary_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "${TimeUtils.formatMonthYear(selectedYearMonth)} Özeti",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toplam Mesai Kazancı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Toplam Mesai Kazancı",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeUtils.formatCurrency(totalEarnings),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MoneyEmerald
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Toplam Mesai",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeUtils.formatDuration(totalMinutes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Hafta İçi Breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Toplam Hafta İçi Mesai",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeUtils.formatDuration(totalWeekdayMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Hafta İçi Kazanç",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeUtils.formatCurrency(totalWeekdayEarnings),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Hafta Sonu Breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Toplam Hafta Sonu Mesai",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeUtils.formatDuration(totalWeekendMinutes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Hafta Sonu Kazanç",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeUtils.formatCurrency(totalWeekendEarnings),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gelişmiş Fazla Mesai Raporları (Son 6 Ay ve Haftalık Trend Analizi) — Premium Özelliği
            if (isPremium) {
                MonthlyOvertimeBarChart(
                    allRecords = allRecords.ifEmpty { monthlyRecords },
                    selectedYearMonth = selectedYearMonth,
                    onSelectMonth = onSelectMonth
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            premiumPromptFeature = "Gelişmiş Fazla Mesai Raporları (Son 6 Ay ve Haftalık Trend Analizi)"
                        }
                        .testTag("card_advanced_reports_locked"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gelişmiş Fazla Mesai Raporları",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Son 6 aylık karşılaştırmalı kazanç/saat grafiği, haftalık trend dağılımı ve en yoğun mesai analizi Mesai Takip Premium abonelerine özeldir.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
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
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                premiumPromptFeature = "Gelişmiş Fazla Mesai Raporları (Son 6 Ay ve Haftalık Trend Analizi)"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_unlock_advanced_reports"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Gelişmiş Raporları Premium ile Aç",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dışa Aktarma ve Paylaşım Kartı (Requirement: PDF & Excel Çizelgesi — Premium Özelliği)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_export_share"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dışa Aktar ve Paylaş",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${TimeUtils.formatMonthYear(selectedYearMonth)} dönemine ait ${monthlyRecords.size} mesai kaydı",
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // PDF Butonu
                    Button(
                        onClick = {
                            if (isPremium) {
                                onExportPdf()
                            } else {
                                premiumPromptFeature = "Resmi PDF Bordro Çizelgesi Dışa Aktarma"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_export_pdf"),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isPremium || monthlyRecords.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPremium) "Resmi PDF Çizelgesi (İmzalı Bordro)" else "Resmi PDF Çizelgesi (Premium)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Excel (CSV) Butonu
                    OutlinedButton(
                        onClick = {
                            if (isPremium) {
                                onExportCsv()
                            } else {
                                premiumPromptFeature = "Excel (CSV) Tablosu Dışa Aktarma"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_export_csv"),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isPremium || monthlyRecords.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPremium) "Excel (CSV) Tablosu İndir & Paylaş" else "Excel (CSV) Tablosu İndir (Premium)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (isPremium && monthlyRecords.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bu ay için henüz mesai kaydı bulunmadığından dışa aktarma devre dışıdır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Kişisel Maaş Programlama Raporu ve Bordro Arşivi
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDocumentArchive() }
                    .testTag("card_open_document_archive"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Maaş Bordrosu ve Belge Arşivi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kişisel maaş programlama raporlarınızı otomatik oluşturup dönem bazında arşivleyin, PDF/CSV olarak dışa aktarın veya belge saklayın.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenDocumentArchive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_document_archive"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bordro ve Rapor Arşivini Aç",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Kişisel Başarılar ve Kilometre Taşları
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAchievements() }
                    .testTag("card_open_achievements_stats"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Başarı ve Kilometre Taşları",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kazanılan rozetlerinizi, 7/30/100 gün çalışma kilometre taşlarınızı, aylık hedeflerinizi ve kişisel çalışma kapasitenizi inceleyin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenAchievements,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_achievements_stats"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Başarılar ve İstatistikleri Aç",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Gelir ve Gider Takibi Kartı
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBudgetTracking() }
                    .testTag("card_open_budget_tracking"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Gelir ve Gider Takibi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Maaş, avans, ek gelir ile kira, fatura ve kişisel giderlerinizi çift sayım olmadan takip edin; aylık net bakiye ve geçmiş ay karşılaştırmalarını görün.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenBudgetTracking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_budget_from_reports"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Gelir ve Gider Takibini Aç",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Çoklu Ücret Tarifesi ve Akıllı Eksik Kayıt Kontrolü Kartı
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTariffAndAudit() }
                    .testTag("card_open_tariff_audit"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Ücret Tarifeleri ve Akıllı Kayıt Denetimi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tarih aralıklı ücret tarifeleri tanımlayın, çakışmaları kontrol edin ve eksik veya olağan dışı mesai kayıtlarını denetleyin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenTariffAndAudit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_tariff_audit_from_reports"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Tarife ve Kayıt Denetimini Aç",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Play ile Abone Ol Kartı
            com.example.ui.components.GooglePlaySubscribeCard()

            Spacer(modifier = Modifier.height(20.dp))

            // Calendar Section (Requirement #12)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Takvim Görünümü",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calendar_view_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Day of week headers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz").forEachIndexed { index, day ->
                            val isWeekend = index >= 5
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isWeekend) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar Days Grid
                    CalendarGrid(
                        yearMonth = selectedYearMonth,
                        recordsByDate = recordsByDate,
                        onDayClick = { date, records ->
                            if (records.isNotEmpty()) {
                                selectedDayRecords = Pair(date, records)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Day Details Dialog
    if (selectedDayRecords != null) {
        val (date, records) = selectedDayRecords!!
        AlertDialog(
            onDismissRequest = { selectedDayRecords = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = TimeUtils.formatFullDate(date.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (records.size > 1) {
                        val dayTotalMinutes = records.sumOf { it.durationMinutes }
                        val dayTotalEarnings = records.sumOf { it.totalAmount }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Günlük Toplam (${records.size} Mesai)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = TimeUtils.formatDurationShort(dayTotalMinutes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = TimeUtils.formatCurrency(dayTotalEarnings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    records.forEach { rec ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Başlangıç: ${rec.startTimeStr}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Bitiş: ${rec.endTimeStr}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mesai Süresi: ${TimeUtils.formatDurationShort(rec.durationMinutes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = TimeUtils.formatCurrency(rec.totalAmount),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MoneyEmerald
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDayRecords = null }) {
                    Text("Kapat", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    recordsByDate: Map<LocalDate, List<OvertimeRecord>>,
    onDayClick: (LocalDate, List<OvertimeRecord>) -> Unit
) {
    val firstDayOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()

    // Day of week: 1 (Mon) to 7 (Sun)
    val startDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1..7

    val totalCells = 42 // 6 weeks * 7 days
    val leadingEmpty = startDayOfWeek - 1

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (week in 0 until 6) {
            val weekStartIndex = week * 7
            if (weekStartIndex >= leadingEmpty + daysInMonth) break

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for (dayIndex in 0 until 7) {
                    val cellIndex = weekStartIndex + dayIndex
                    val dayOfMonth = cellIndex - leadingEmpty + 1

                    if (dayOfMonth in 1..daysInMonth) {
                        val date = yearMonth.atDay(dayOfMonth)
                        val records = recordsByDate[date] ?: emptyList()
                        val hasOvertime = records.isNotEmpty()
                        val isToday = date == LocalDate.now(TimeUtils.ISTANBUL_ZONE)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        hasOvertime -> MaterialTheme.colorScheme.primaryContainer
                                        isToday -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    width = if (isToday) 1.5.dp else 0.dp,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onDayClick(date, records) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (hasOvertime || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (hasOvertime) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasOvertime) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MoneyEmerald)
                                    )
                                }
                            }
                        }
                    } else {
                        // Empty padding cell
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
