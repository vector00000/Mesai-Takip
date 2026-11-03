package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PayrollDocument
import com.example.data.model.PayrollDocumentType
import com.example.data.repository.DocumentArchiveRepository
import com.example.ui.components.GooglePlaySubscribeCard
import com.example.ui.theme.MoneyEmerald
import com.example.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentArchiveScreen(
    isPremium: Boolean,
    selectedYearMonth: YearMonth,
    documents: List<PayrollDocument>,
    repository: DocumentArchiveRepository?,
    onMonthChange: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    archivedReports: List<com.example.data.model.PayrollArchiveReport> = emptyList(),
    onGenerateArchiveReport: () -> Unit = {},
    onExportArchivedPdf: (com.example.data.model.PayrollArchiveReport) -> Unit = {},
    onExportArchivedCsv: (com.example.data.model.PayrollArchiveReport) -> Unit = {},
    onDeleteArchivedReport: (Long) -> Unit = {}
) {
    BackHandler { onNavigateBack() }

    val coroutineScope = rememberCoroutineScope()

    var filterAllMonths by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<PayrollDocumentType?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var documentToDelete by remember { mutableStateOf<PayrollDocument?>(null) }
    var previewTarget by remember { mutableStateOf<Pair<PayrollDocument, File>?>(null) }
    var documentToSaveAs by remember { mutableStateOf<PayrollDocument?>(null) }
    var selectedArchivedReport by remember { mutableStateOf<com.example.data.model.PayrollArchiveReport?>(null) }

    var isBusy by remember { mutableStateOf(false) }
    var feedbackError by remember { mutableStateOf<String?>(null) }
    var feedbackSuccess by remember { mutableStateOf<String?>(null) }

    // SAF Save-As (İndir / Dışa Aktar)
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val doc = documentToSaveAs
        documentToSaveAs = null
        if (destUri != null && doc != null && repository != null) {
            isBusy = true
            feedbackError = null
            coroutineScope.launch {
                val res = repository.exportDocumentToUri(doc.id, destUri)
                isBusy = false
                res.fold(
                    onSuccess = {
                        feedbackSuccess = "\"${it.title}\" belgesi seçilen konuma güvenle indirildi."
                    },
                    onFailure = { err ->
                        feedbackError = err.message ?: "Belge dışa aktarılamadı."
                    }
                )
            }
        }
    }

    val filteredDocuments = remember(documents, selectedYearMonth, filterAllMonths, searchQuery, selectedTypeFilter) {
        val ymStr = selectedYearMonth.toString()
        val q = searchQuery.trim().lowercase()
        documents.filter { doc ->
            val matchesMonth = filterAllMonths || doc.yearMonth == ymStr
            val matchesType = selectedTypeFilter == null || doc.documentType == selectedTypeFilter?.name
            val matchesQuery = q.isEmpty() ||
                doc.title.lowercase().contains(q) ||
                doc.description.lowercase().contains(q) ||
                doc.fileName.lowercase().contains(q) ||
                PayrollDocumentType.fromName(doc.documentType).labelTr.lowercase().contains(q)
            matchesMonth && matchesType && matchesQuery
        }
    }

    // Silme onay diyaloğu
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Belgeyi Sil",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "\"${doc.title}\" (${doc.fileName}) belgesini arşivinizden kalıcı olarak silmek istediğinize emin misiniz? Bu işlem geri alınamaz."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = doc
                        documentToDelete = null
                        if (repository != null) {
                            isBusy = true
                            feedbackError = null
                            coroutineScope.launch {
                                val result = repository.deleteDocument(target.id)
                                isBusy = false
                                result.fold(
                                    onSuccess = {
                                        feedbackSuccess = "\"${target.title}\" belgesi arşivden silindi."
                                    },
                                    onFailure = { err ->
                                        feedbackError = err.message ?: "Belge silinirken hata oluştu."
                                    }
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_delete_document")
                ) {
                    Text("Evet, Kalıcı Olarak Sil")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { documentToDelete = null },
                    modifier = Modifier.testTag("btn_cancel_delete_document")
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Belge Görüntüleme (PDF / Fotoğraf) Diyaloğu
    previewTarget?.let { (doc, file) ->
        DocumentPreviewDialog(
            document = doc,
            file = file,
            onDismiss = { previewTarget = null },
            onShare = {
                if (repository != null) {
                    coroutineScope.launch {
                        val shareRes = repository.shareOrExportDocument(doc.id)
                        shareRes.onFailure { err ->
                            feedbackError = err.message ?: "Belge paylaşılamadı."
                        }
                    }
                }
            },
            onDownload = {
                documentToSaveAs = doc
                saveDocumentLauncher.launch(doc.fileName)
            }
        )
    }

    // Yeni Belge Ekleme Diyaloğu
    if (showAddDialog && repository != null) {
        AddPayrollDocumentDialog(
            initialYearMonth = selectedYearMonth,
            onDismiss = { showAddDialog = false },
            onSubmit = { uri, title, docType, yearMonthStr, dateStr, desc ->
                showAddDialog = false
                isBusy = true
                feedbackError = null
                feedbackSuccess = null
                coroutineScope.launch {
                    val res = repository.addDocumentFromUri(
                        uri = uri,
                        title = title,
                        documentType = docType,
                        yearMonth = yearMonthStr,
                        documentDate = dateStr,
                        description = desc
                    )
                    isBusy = false
                    res.fold(
                        onSuccess = { saved ->
                            feedbackSuccess = "\"${saved.title}\" belgesi (${formatFileSize(saved.fileSizeBytes)}) güvenli arşive eklendi."
                        },
                        onFailure = { err ->
                            feedbackError = err.message ?: "Belge yüklenirken hata oluştu."
                        }
                    )
                }
            }
        )
    }

    // Arşivlenmiş Kişisel Maaş Programlama Raporu Detay Diyaloğu
    selectedArchivedReport?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedArchivedReport = null },
            title = {
                Column {
                    Text(
                        text = report.reportTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Dönem: ${report.yearMonth} • Arşiv Tarihi: ${report.createdDateStr}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Not: Resmî bordro olduğu doğrulanmayan belgeler kişisel maaş programlama raporu olarak adlandırılmıştır. Bu rapor oluşturulurken kullanılan ücret tarifeleri dondurulmuştur; sonraki tarife değişikliklerinden etkilenmez.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = "Çalışma ve Maaş Hesaplama Özeti",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("• Çalışılan Gün: ${report.totalWorkingDays} gün (${report.totalRecordsCount} kayıt)", style = MaterialTheme.typography.bodySmall)
                    Text("• Net Mesai Süresi: ${String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.1f", report.totalNetWorkMinutes / 60.0)} saat (Mola: ${report.totalBreakMinutes} dk)", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("• Baz Maaş (Snapshot): ${TimeUtils.formatCurrency(report.snapshotBaseSalary)}", style = MaterialTheme.typography.bodySmall)
                    Text("• Fazla Mesai Kazancı: ${TimeUtils.formatCurrency(report.totalOvertimeEarnings)}", style = MaterialTheme.typography.bodySmall)
                    Text("• Avans Kesintisi: -${TimeUtils.formatCurrency(report.totalAdvanceDeducted)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "• Net Hesaplanan Tutar: ${TimeUtils.formatCurrency(report.netTotalPayable)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MoneyEmerald
                    )
                    HorizontalDivider()
                    Text(
                        text = "Dondurulmuş Ücret Tarifesi Bilgisi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = report.appliedTariffSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExportArchivedPdf(report) },
                            modifier = Modifier.weight(1f).testTag("btn_dialog_export_archived_pdf")
                        ) {
                            Text("PDF Yazdır/Kaydet", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { onExportArchivedCsv(report) },
                            modifier = Modifier.weight(1f).testTag("btn_dialog_export_archived_csv")
                        ) {
                            Text("CSV/Excel Aktar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedArchivedReport = null }) {
                    Text("Kapat")
                }
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
                        modifier = Modifier.testTag("btn_back_from_archive")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri Dön"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Maaş Bordrosu ve Belge Arşivi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Kişisel şifreli & izole belge kasası",
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

            if (!isPremium) {
                // Hem Kişisel Maaş Programlama Raporu Arşivi (Ücretsiz & Kayıtlardan Otomatik) hem de Belge Kasası Bilgisi
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PayrollArchiveReportsSection(
                        selectedYearMonth = selectedYearMonth,
                        filterAllMonths = filterAllMonths,
                        archivedReports = archivedReports,
                        onMonthChange = onMonthChange,
                        onGenerateReport = {
                            onGenerateArchiveReport()
                            feedbackSuccess = "${TimeUtils.formatMonthYear(selectedYearMonth)} kişisel maaş programlama raporu oluşturuldu ve arşivlendi."
                        },
                        onOpenReportDetail = { selectedArchivedReport = it },
                        onExportPdf = onExportArchivedPdf,
                        onExportCsv = onExportArchivedCsv,
                        onDeleteReport = onDeleteArchivedReport
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_archive_premium_locked"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Ek Belge Kasası (PDF / Fotoğraf Yükleme)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Mesai Takip Premium Özelliği",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Kişisel maaş programlama raporlarınızı yukarıdan ücretsiz oluşturup PDF/CSV olarak indirebilirsiniz. Harici PDF/fotoğraf belge yükleme kasası Premium aboneliğe dahildir.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    GooglePlaySubscribeCard(
                        featureContextTitle = "Maaş Bordrosu ve Belge Arşivi"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        PayrollArchiveReportsSection(
                            selectedYearMonth = selectedYearMonth,
                            filterAllMonths = filterAllMonths,
                            archivedReports = archivedReports,
                            onMonthChange = onMonthChange,
                            onGenerateReport = {
                                onGenerateArchiveReport()
                                feedbackSuccess = "${TimeUtils.formatMonthYear(selectedYearMonth)} kişisel maaş programlama raporu oluşturuldu ve arşivlendi."
                            },
                            onOpenReportDetail = { selectedArchivedReport = it },
                            onExportPdf = onExportArchivedPdf,
                            onExportCsv = onExportArchivedCsv,
                            onDeleteReport = onDeleteArchivedReport
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        // Yıl ve Ay Seçici + Tüm Dönemler Geçişi
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            filterAllMonths = false
                                            onMonthChange(-1)
                                        },
                                        modifier = Modifier.testTag("btn_archive_prev_month")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "Önceki Ay"
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (filterAllMonths) "Tüm Yıl ve Aylar" else TimeUtils.formatMonthYear(selectedYearMonth),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${filteredDocuments.size} belge listeleniyor",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            filterAllMonths = false
                                            onMonthChange(1)
                                        },
                                        modifier = Modifier.testTag("btn_archive_next_month")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Sonraki Ay"
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Security,
                                            contentDescription = null,
                                            tint = MoneyEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Yalnızca sizin hesabınıza özel izole depolama",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilterChip(
                                        selected = filterAllMonths,
                                        onClick = { filterAllMonths = !filterAllMonths },
                                        label = { Text("Tüm Aylar") },
                                        modifier = Modifier.testTag("chip_archive_all_months")
                                    )
                                }
                            }
                        }
                    }

                    // Arama ve Belge Türü Filtreleri + Yeni Belge Ekle Butonu
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_search_documents"),
                                    placeholder = { Text("Belge adı, açıklama veya dosya ara...") },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Temizle")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedTypeFilter == null,
                                    onClick = { selectedTypeFilter = null },
                                    label = { Text("Tümü") },
                                    modifier = Modifier.testTag("chip_doc_type_ALL")
                                )
                                PayrollDocumentType.entries.forEach { docType ->
                                    FilterChip(
                                        selected = selectedTypeFilter == docType,
                                        onClick = {
                                            selectedTypeFilter = if (selectedTypeFilter == docType) null else docType
                                        },
                                        label = { Text(docType.badgeText) },
                                        modifier = Modifier.testTag("chip_doc_type_${docType.name}")
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    feedbackError = null
                                    feedbackSuccess = null
                                    showAddDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("btn_open_add_document_dialog"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Yeni Maaş Bordrosu veya Belge Ekle (PDF / Fotoğraf)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Yükleniyor / Hata / Başarı Geri Bildirimleri
                    if (isBusy) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Belge işlemi güvenli depolama alanında gerçekleştiriliyor...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    feedbackError?.let { errorMsg ->
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("banner_archive_error"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = errorMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { feedbackError = null }) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Kapat")
                                    }
                                }
                            }
                        }
                    }

                    feedbackSuccess?.let { successMsg ->
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("banner_archive_success"),
                                colors = CardDefaults.cardColors(containerColor = MoneyEmerald.copy(alpha = 0.14f)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MoneyEmerald
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = successMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { feedbackSuccess = null }) {
                                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Kapat")
                                    }
                                }
                            }
                        }
                    }

                    // Boş Durum veya Belge Listesi
                    if (filteredDocuments.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_archive_empty_state"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank() || selectedTypeFilter != null) {
                                            "Arama kriterlerinize uygun belge bulunamadı"
                                        } else {
                                            "${TimeUtils.formatMonthYear(selectedYearMonth)} dönemi için henüz belge eklenmedi"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Maaş bordronuzu, imzalı mesai çizelgenizi veya banka dekontunuzu PDF ya da fotoğraf olarak ekleyip her zaman güvenle erişebilirsiniz.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredDocuments, key = { it.id }) { doc ->
                            val docType = PayrollDocumentType.fromName(doc.documentType)
                            val isPdf = doc.mimeType == "application/pdf"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_document_${doc.id}"),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isPdf) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            },
                                            modifier = Modifier.size(46.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Image,
                                                    contentDescription = null,
                                                    tint = if (isPdf) {
                                                        MaterialTheme.colorScheme.error
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    }
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
                                                    text = doc.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = docType.labelTr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tarih: ${TimeUtils.formatDateTurkish(doc.documentDate)} • Dönem: ${doc.yearMonth} • ${formatFileSize(doc.fileSizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Dosya: ${doc.fileName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            if (doc.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = doc.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (repository != null) {
                                                    coroutineScope.launch {
                                                        val res = repository.getAuthorizedDocumentFile(doc.id)
                                                        res.fold(
                                                            onSuccess = { pair -> previewTarget = pair },
                                                            onFailure = { err ->
                                                                feedbackError = err.message ?: "Belge görüntülenemedi."
                                                            }
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("btn_view_document_${doc.id}"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Visibility,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Görüntüle", style = MaterialTheme.typography.labelMedium)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                documentToSaveAs = doc
                                                saveDocumentLauncher.launch(doc.fileName)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("btn_export_document_${doc.id}"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("İndir", style = MaterialTheme.typography.labelMedium)
                                        }

                                        IconButton(
                                            onClick = {
                                                if (repository != null) {
                                                    coroutineScope.launch {
                                                        val res = repository.shareOrExportDocument(doc.id)
                                                        res.onFailure { err ->
                                                            feedbackError = err.message ?: "Belge paylaşılamadı."
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("btn_share_document_${doc.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Share,
                                                contentDescription = "Paylaş",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = { documentToDelete = doc },
                                            modifier = Modifier.testTag("btn_delete_document_${doc.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Sil",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPayrollDocumentDialog(
    initialYearMonth: YearMonth,
    onDismiss: () -> Unit,
    onSubmit: (
        uri: Uri,
        title: String,
        documentType: PayrollDocumentType,
        yearMonth: String,
        documentDate: String,
        description: String
    ) -> Unit
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedSourceLabel by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PayrollDocumentType.PAYSLIP) }
    var yearMonthText by remember { mutableStateOf(initialYearMonth.toString()) }
    var dateText by remember {
        mutableStateOf(
            if (initialYearMonth == YearMonth.now(TimeUtils.ISTANBUL_ZONE)) {
                TimeUtils.todayDateStr()
            } else {
                "${initialYearMonth}-15"
            }
        )
    }
    var description by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            selectedSourceLabel = uri.lastPathSegment ?: "Seçilen PDF / Görüntü Belgesi"
            if (title.isBlank()) {
                title = "${selectedType.labelTr} (${yearMonthText})"
            }
            validationError = null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            selectedSourceLabel = "Seçilen Fotoğraf Belgesi"
            if (title.isBlank()) {
                title = "${selectedType.labelTr} (${yearMonthText})"
            }
            validationError = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Maaş Bordrosu veya Belge Ekle",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PDF veya fotoğraf (JPG, PNG, WEBP — maks. 15 MB) formatındaki bordro ve mesai belgelerinizi seçin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            documentPickerLauncher.launch(
                                arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_pick_pdf_or_file"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF / Dosya", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_pick_photo_media"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fotoğraf Seç", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (selectedSourceLabel != null) {
                    Surface(
                        color = MoneyEmerald.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = MoneyEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dosya hazır: $selectedSourceLabel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        validationError = null
                    },
                    label = { Text("Belge Adı *") },
                    placeholder = { Text("Örn: Ekim 2026 Maaş Bordrosu") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_document_title")
                )

                Text(
                    text = "Belge Türü",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PayrollDocumentType.entries.forEach { docType ->
                        FilterChip(
                            selected = selectedType == docType,
                            onClick = { selectedType = docType },
                            label = { Text(docType.labelTr, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = yearMonthText,
                        onValueChange = {
                            yearMonthText = it
                            validationError = null
                        },
                        label = { Text("Dönem (YYYY-AA) *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_document_year_month")
                    )

                    OutlinedTextField(
                        value = dateText,
                        onValueChange = {
                            dateText = it
                            if (it.length >= 7 && it.matches(Regex("^\\d{4}-\\d{2}.*"))) {
                                yearMonthText = it.take(7)
                            }
                            validationError = null
                        },
                        label = { Text("Tarih (YYYY-AA-GG) *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_document_date")
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama (İsteğe Bağlı)") },
                    placeholder = { Text("Örn: Fazla mesai farkı dahil net maaş bordrosu") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_document_description")
                )

                validationError?.let { err ->
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
                    val uri = selectedUri
                    if (uri == null) {
                        validationError = "Lütfen önce yüklenecek PDF veya fotoğraf dosyasını seçin."
                        return@Button
                    }
                    if (title.trim().isBlank()) {
                        validationError = "Lütfen belge adı girin."
                        return@Button
                    }
                    onSubmit(
                        uri,
                        title.trim(),
                        selectedType,
                        yearMonthText.trim(),
                        dateText.trim(),
                        description.trim()
                    )
                },
                modifier = Modifier.testTag("btn_save_document")
            ) {
                Text("Arşive Kaydet")
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
private fun DocumentPreviewDialog(
    document: PayrollDocument,
    file: File,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pageCount by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoadingPreview by remember { mutableStateOf(true) }
    var previewError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file, currentPage) {
        isLoadingPreview = true
        previewError = null
        withContext(Dispatchers.IO) {
            try {
                if (document.mimeType == "application/pdf") {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            pageCount = renderer.pageCount.coerceAtLeast(1)
                            val safeIndex = currentPage.coerceIn(0, pageCount - 1)
                            renderer.openPage(safeIndex).use { page ->
                                val width = (page.width * 2).coerceIn(400, 1600)
                                val height = (page.height * 2).coerceIn(400, 2200)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                renderedBitmap = bmp
                            }
                        }
                    }
                } else {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        pageCount = 1
                        renderedBitmap = bmp
                    } else {
                        previewError = "Görüntü önizlemesi oluşturulamadı."
                    }
                }
            } catch (e: Exception) {
                previewError = "Belge önizlemesi yüklenirken hata oluştu: ${e.message}"
            } finally {
                isLoadingPreview = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${PayrollDocumentType.fromName(document.documentType).labelTr} • ${TimeUtils.formatDateTurkish(document.documentDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoadingPreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (previewError != null) {
                    Text(
                        text = previewError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    renderedBitmap?.let { bmp ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 380.dp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = document.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (document.mimeType == "application/pdf" && pageCount > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0
                            ) {
                                Text("Önceki")
                            }
                            Text(
                                text = "Sayfa ${currentPage + 1} / $pageCount",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = { if (currentPage < pageCount - 1) currentPage++ },
                                enabled = currentPage < pageCount - 1
                            ) {
                                Text("Sonraki")
                            }
                        }
                    }
                }

                if (document.description.isNotBlank()) {
                    Text(
                        text = document.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDownload) {
                    Icon(imageVector = Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("İndir")
                }
                Button(onClick = onShare) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paylaş / Dışa Aktar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.2f MB", mb)
}

@Composable
private fun PayrollArchiveReportsSection(
    selectedYearMonth: YearMonth,
    filterAllMonths: Boolean,
    archivedReports: List<com.example.data.model.PayrollArchiveReport>,
    onMonthChange: (Long) -> Unit,
    onGenerateReport: () -> Unit,
    onOpenReportDetail: (com.example.data.model.PayrollArchiveReport) -> Unit,
    onExportPdf: (com.example.data.model.PayrollArchiveReport) -> Unit,
    onExportCsv: (com.example.data.model.PayrollArchiveReport) -> Unit,
    onDeleteReport: (Long) -> Unit
) {
    val filteredReports = remember(archivedReports, selectedYearMonth, filterAllMonths) {
        if (filterAllMonths) {
            archivedReports
        } else {
            val ymStr = selectedYearMonth.toString()
            archivedReports.filter { it.yearMonth == ymStr }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_payroll_archive_reports_section"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onMonthChange(-1) },
                    modifier = Modifier.testTag("btn_payroll_report_prev_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Önceki Ay"
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Kişisel Maaş Programlama Raporu Arşivi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Dönem: ${TimeUtils.formatMonthYear(selectedYearMonth)} • ${filteredReports.size} Arşivli Rapor",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onMonthChange(1) },
                    modifier = Modifier.testTag("btn_payroll_report_next_month")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Sonraki Ay"
                    )
                }
            }

            Text(
                text = "Mevcut uygulama kayıtlarınızdan otomatik olarak aylık çalışma ve maaş programlama tablosu üretin. Resmî bordro olduğu doğrulanmayan belgeler kişisel maaş programlama raporu olarak adlandırılmıştır ve kullanılan ücret tarifeleriyle birlikte dondurularak arşivlenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onGenerateReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_generate_payroll_report"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${TimeUtils.formatMonthYear(selectedYearMonth)} Raporunu Oluştur ve Arşivle",
                    fontWeight = FontWeight.Bold
                )
            }

            if (filteredReports.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Bu dönem için henüz arşivlenmiş kişisel maaş programlama raporu bulunmuyor. Yukarıdaki butonla mevcut kayıtlarınızdan anında oluşturabilirsiniz.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                filteredReports.forEach { report ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReportDetail(report) }
                            .testTag("card_archived_report_${report.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = report.reportTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Arşiv Tarihi: ${report.createdDateStr} • ${report.totalWorkingDays} Gün • ${String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.1f", report.totalNetWorkMinutes / 60.0)} sa Mesai",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = TimeUtils.formatCurrency(report.netTotalPayable),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MoneyEmerald
                                )
                            }

                            Text(
                                text = "Dondurulmuş Tarife: ${report.appliedTariffSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenReportDetail(report) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_open_archived_report_${report.id}"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Aç", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onExportPdf(report) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_export_archived_pdf_${report.id}"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("PDF", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onExportCsv(report) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_export_archived_csv_${report.id}"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("CSV", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(
                                    onClick = { onDeleteReport(report.id) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("btn_delete_archived_report_${report.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Raporu Sil",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
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

