package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.AppSettings
import com.example.data.model.BudgetCategory
import com.example.data.model.BudgetTransaction
import com.example.data.model.BudgetTransactionType
import com.example.data.model.OvertimeRecord
import com.example.data.model.OvertimeType
import com.example.data.model.PayrollArchiveReport
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.round

data class SnapshotRecordRow(
    val date: String,
    val startTimeStr: String,
    val endTimeStr: String,
    val durationMinutes: Int,
    val breakMinutes: Int,
    val hourlyRate: Double,
    val totalAmount: Double,
    val overtimeType: String,
    val note: String
)

object PayrollArchiveExporter {

    /**
     * Mevcut uygulama kayıtlarından (mesai kayıtları, maaş ayarları, ücret tarifeleri ve avanslar)
     * o aya ait dondurulmuş (snapshot) "Kişisel Maaş Programlama Raporu" nesnesini üretir.
     * Sonradan ücret ayarları değişse bile bu rapordaki tutarlar ve tarifeler sabit kalır.
     */
    fun buildMonthlyArchiveSnapshot(
        yearMonth: YearMonth,
        monthRecords: List<OvertimeRecord>,
        settings: AppSettings,
        monthTransactions: List<BudgetTransaction> = emptyList(),
        activeTariffName: String = "Standart Kurumsal Ücret Tarifesi"
    ): PayrollArchiveReport {
        val ymStr = yearMonth.toString()
        val periodLabel = TimeUtils.formatMonthYear(yearMonth)
        val sortedRecords = monthRecords.sortedWith(compareBy<OvertimeRecord> { it.date }.thenBy { it.startTimeEpochMs })

        val recordsJsonArray = JSONArray()
        for (rec in sortedRecords) {
            val obj = JSONObject().apply {
                put("date", rec.date)
                put("startTimeStr", rec.startTimeStr)
                put("endTimeStr", rec.endTimeStr)
                put("durationMinutes", rec.durationMinutes)
                put("breakMinutes", rec.breakMinutes)
                put("hourlyRate", rec.hourlyRate)
                put("totalAmount", rec.totalAmount)
                put("overtimeType", rec.overtimeType)
                put("note", rec.note)
            }
            recordsJsonArray.put(obj)
        }

        val totalRecordsCount = sortedRecords.size
        val totalWorkingDays = sortedRecords.map { it.date }.distinct().size
        val totalNetMinutes = sortedRecords.sumOf { it.durationMinutes }
        val totalBreakMinutes = sortedRecords.sumOf { it.breakMinutes }
        val totalOvertimeEarnings = round2(sortedRecords.sumOf { it.totalAmount })

        val advanceTotal = round2(
            monthTransactions.filter {
                BudgetTransactionType.fromString(it.type) == BudgetTransactionType.INCOME &&
                    BudgetCategory.fromName(it.category) == BudgetCategory.ADVANCE
            }.sumOf { max(0.0, it.amount) }
        )

        val baseSalary = max(0.0, settings.baseSalary)
        val netPayable = round2(max(0.0, baseSalary + totalOvertimeEarnings - advanceTotal))

        val normalRate = settings.getEffectiveNormalRate()
        val weekdayRate = settings.getEffectiveWeekdayRate()
        val weekendRate = settings.getEffectiveWeekendRate()
        val nightRate = settings.getEffectiveNightShiftRate()
        val holidayRate = settings.getEffectiveOfficialHolidayRate()

        val tariffSummary = "$activeTariffName | Normal: ${TimeUtils.formatCurrency(normalRate)}, " +
            "H.İçi: ${TimeUtils.formatCurrency(weekdayRate)}, " +
            "H.Sonu: ${TimeUtils.formatCurrency(weekendRate)}, " +
            "Gece: ${TimeUtils.formatCurrency(nightRate)}, " +
            "Resmî Tatil: ${TimeUtils.formatCurrency(holidayRate)}"

        return PayrollArchiveReport(
            yearMonth = ymStr,
            reportTitle = "$periodLabel Kişisel Maaş Programlama Raporu",
            createdDateStr = TimeUtils.formatDate(LocalDate.now(TimeUtils.ISTANBUL_ZONE)),
            employeeName = settings.employeeName,
            workplaceName = settings.workplaceName,
            snapshotBaseSalary = baseSalary,
            snapshotNormalRate = normalRate,
            snapshotWeekdayRate = weekdayRate,
            snapshotWeekendRate = weekendRate,
            snapshotNightRate = nightRate,
            snapshotHolidayRate = holidayRate,
            appliedTariffSummary = tariffSummary,
            totalRecordsCount = totalRecordsCount,
            totalWorkingDays = totalWorkingDays,
            totalNetWorkMinutes = totalNetMinutes,
            totalBreakMinutes = totalBreakMinutes,
            totalOvertimeEarnings = totalOvertimeEarnings,
            totalAdvanceDeducted = advanceTotal,
            netTotalPayable = netPayable,
            recordsSnapshotJson = recordsJsonArray.toString()
        )
    }

    fun parseSnapshotRecords(jsonStr: String): List<SnapshotRecordRow> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<SnapshotRecordRow>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    SnapshotRecordRow(
                        date = o.optString("date", ""),
                        startTimeStr = o.optString("startTimeStr", ""),
                        endTimeStr = o.optString("endTimeStr", ""),
                        durationMinutes = o.optInt("durationMinutes", 0),
                        breakMinutes = o.optInt("breakMinutes", 0),
                        hourlyRate = o.optDouble("hourlyRate", 0.0),
                        totalAmount = o.optDouble("totalAmount", 0.0),
                        overtimeType = o.optString("overtimeType", "WEEKDAY"),
                        note = o.optString("note", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Arşivlenmiş "Kişisel Maaş Programlama Raporu" nesnesini dondurulmuş (snapshot)
     * verileriyle CSV/Excel dosyasına aktarır.
     */
    fun exportSnapshotToCsv(context: Context, report: PayrollArchiveReport): File {
        val safeYm = report.yearMonth.replace("-", "_")
        val fileName = "kisisel_maas_programlama_raporu_${safeYm}_${report.id.coerceAtLeast(1)}.csv"
        val file = File(ExportHelper.getExportsDir(context), fileName)
        val rows = parseSnapshotRecords(report.recordsSnapshotJson)

        FileOutputStream(file).use { fos ->
            // UTF-8 BOM
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write("BELGE TÜRÜ;Kişisel Maaş Programlama Raporu (Kayıtlardan Üretilmiştir)\n")
                writer.write("Rapor Başlığı;${report.reportTitle}\n")
                writer.write("Dönem;${report.yearMonth}\n")
                writer.write("Oluşturulma Tarihi;${report.createdDateStr}\n")
                writer.write("Çalışan;${report.employeeName.ifBlank { "Belirtilmedi" }}\n")
                writer.write("İş Yeri;${report.workplaceName.ifBlank { "Belirtilmedi" }}\n")
                writer.write("Saklanan Ücret Tarifeleri;${report.appliedTariffSummary}\n\n")

                val headers = listOf(
                    "Tarih",
                    "Mesai Türü",
                    "Başlangıç",
                    "Bitiş",
                    "Mola (Dk)",
                    "Net Süre (Dk)",
                    "Kullanılan Saat Ücreti (TL)",
                    "Tutar (TL)",
                    "Açıklama"
                )
                writer.write(headers.joinToString(";") + "\n")

                for (r in rows) {
                    val typeLabel = OvertimeType.fromString(r.overtimeType).displayName
                    val line = listOf(
                        r.date,
                        typeLabel,
                        r.startTimeStr,
                        r.endTimeStr,
                        r.breakMinutes.toString(),
                        r.durationMinutes.toString(),
                        "%.2f".format(java.util.Locale.US, r.hourlyRate).replace('.', ','),
                        "%.2f".format(java.util.Locale.US, r.totalAmount).replace('.', ','),
                        "\"${r.note.replace("\"", "\"\"")}\""
                    )
                    writer.write(line.joinToString(";") + "\n")
                }

                writer.write("\n")
                writer.write("ÖZET KALEMİ;TUTAR / DEĞER\n")
                writer.write("Toplam Çalışma Günü;${report.totalWorkingDays} Gün (${report.totalRecordsCount} Kayıt)\n")
                writer.write("Toplam Net Mesai Süresi;${TimeUtils.formatDuration(report.totalNetWorkMinutes)}\n")
                writer.write("Toplam Mola Süresi;${report.totalBreakMinutes} dk\n")
                writer.write("Dondurulmuş Taban Maaş;${TimeUtils.formatCurrency(report.snapshotBaseSalary)}\n")
                writer.write("Toplam Fazla Mesai Kazancı;${TimeUtils.formatCurrency(report.totalOvertimeEarnings)}\n")
                writer.write("Düşülen Maaş Avansı;${TimeUtils.formatCurrency(report.totalAdvanceDeducted)}\n")
                writer.write("NET TOPLAM HAKEDİŞ;${TimeUtils.formatCurrency(report.netTotalPayable)}\n")
                writer.flush()
            }
        }
        return file
    }

    /**
     * Arşivlenmiş "Kişisel Maaş Programlama Raporu" nesnesini dondurulmuş (snapshot)
     * verileriyle A4 PDF belgesine dönüştürür.
     */
    fun exportSnapshotToPdf(context: Context, report: PayrollArchiveReport): File {
        val safeYm = report.yearMonth.replace("-", "_")
        val fileName = "kisisel_maas_programlama_raporu_${safeYm}_${report.id.coerceAtLeast(1)}.pdf"
        val file = File(ExportHelper.getExportsDir(context), fileName)
        val rows = parseSnapshotRecords(report.recordsSnapshotJson)

        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        var y = 45f
        paint.color = Color.rgb(30, 58, 138)
        canvas.drawRect(40f, y, 555f, y + 4f, paint)
        y += 22f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        paint.color = Color.rgb(15, 23, 42)
        canvas.drawText("KİŞİSEL MAAŞ PROGRAMLAMA RAPORU", 40f, y, paint)
        y += 16f

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 9.5f
        paint.color = Color.rgb(71, 85, 105)
        canvas.drawText("${report.reportTitle}  |  Arşiv Tarihi: ${report.createdDateStr}", 40f, y, paint)
        y += 18f

        // Tarife kutusu
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRoundRect(RectF(40f, y, 555f, y + 46f), 6f, 6f, paint)
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 8.5f
        canvas.drawText("Çalışan: ${report.employeeName.ifBlank { "Belirtilmedi" }}   |   İş Yeri: ${report.workplaceName.ifBlank { "Belirtilmedi" }}", 50f, y + 16f, paint)
        canvas.drawText("Saklanan Tarifeler: H.İçi ${TimeUtils.formatCurrency(report.snapshotWeekdayRate)} | H.Sonu ${TimeUtils.formatCurrency(report.snapshotWeekendRate)} | Gece ${TimeUtils.formatCurrency(report.snapshotNightRate)} | Tatil ${TimeUtils.formatCurrency(report.snapshotHolidayRate)}", 50f, y + 34f, paint)
        y += 58f

        // Özet satırı
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        paint.color = Color.rgb(30, 58, 138)
        canvas.drawText("Taban Maaş: ${TimeUtils.formatCurrency(report.snapshotBaseSalary)}   +   Mesai: ${TimeUtils.formatCurrency(report.totalOvertimeEarnings)}   -   Avans: ${TimeUtils.formatCurrency(report.totalAdvanceDeducted)}   =   Net Hakediş: ${TimeUtils.formatCurrency(report.netTotalPayable)}", 40f, y, paint)
        y += 18f

        // Tablo başlığı
        paint.color = Color.rgb(226, 232, 240)
        canvas.drawRect(40f, y, 555f, y + 20f, paint)
        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 8.5f
        canvas.drawText("Tarih", 46f, y + 14f, paint)
        canvas.drawText("Tür", 110f, y + 14f, paint)
        canvas.drawText("Saat", 195f, y + 14f, paint)
        canvas.drawText("Mola", 275f, y + 14f, paint)
        canvas.drawText("Net Süre", 320f, y + 14f, paint)
        canvas.drawText("Saat Ücr.", 395f, y + 14f, paint)
        canvas.drawText("Tutar", 475f, y + 14f, paint)
        y += 22f

        paint.typeface = Typeface.DEFAULT
        for (row in rows.take(28)) {
            if (y > 760f) break
            paint.color = Color.rgb(30, 41, 59)
            canvas.drawText(row.date, 46f, y + 12f, paint)
            canvas.drawText(OvertimeType.fromString(row.overtimeType).displayName, 110f, y + 12f, paint)
            canvas.drawText("${row.startTimeStr}-${row.endTimeStr}", 195f, y + 12f, paint)
            canvas.drawText("${row.breakMinutes} dk", 275f, y + 12f, paint)
            canvas.drawText(TimeUtils.formatDurationShort(row.durationMinutes), 320f, y + 12f, paint)
            canvas.drawText(TimeUtils.formatCurrency(row.hourlyRate), 395f, y + 12f, paint)
            canvas.drawText(TimeUtils.formatCurrency(row.totalAmount), 475f, y + 12f, paint)
            y += 17f
        }

        paint.color = Color.rgb(100, 116, 139)
        paint.textSize = 7.5f
        canvas.drawText("Not: Bu belge mevcut uygulama kayıtlarından üretilmiş Kişisel Maaş Programlama Raporudur.", 40f, 815f, paint)

        document.finishPage(page)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0
}
