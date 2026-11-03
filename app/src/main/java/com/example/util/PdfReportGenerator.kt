package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

object PdfReportGenerator {

    private const val PAGE_WIDTH = 595 // A4 standard width in points
    private const val PAGE_HEIGHT = 842 // A4 standard height in points
    private const val MARGIN_X = 40f
    private const val CONTENT_WIDTH = 515f
    private const val MARGIN_RIGHT = MARGIN_X + CONTENT_WIDTH

    fun generateMonthlyPdf(
        context: Context,
        records: List<OvertimeRecord>,
        yearMonth: YearMonth,
        settings: AppSettings
    ): File {
        val fileName = "mesai_cizelgesi_${yearMonth.year}_${"%02d".format(yearMonth.monthValue)}.pdf"
        val file = File(ExportHelper.getExportsDir(context), fileName)

        val document = PdfDocument()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val trLocale = Locale.forLanguageTag("tr-TR")

        val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, trLocale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(trLocale) else it.toString() }
        val periodStr = "$monthName ${yearMonth.year}"

        val totalMinutes = records.sumOf { it.durationMinutes }
        val totalAmount = records.sumOf { it.totalAmount }
        val weekdayRecords = records.filter {
            !it.overtimeType.contains("WEEKEND", ignoreCase = true) &&
            !it.overtimeType.contains("Hafta Sonu", ignoreCase = true)
        }
        val weekendRecords = records.filter {
            it.overtimeType.contains("WEEKEND", ignoreCase = true) ||
            it.overtimeType.contains("Hafta Sonu", ignoreCase = true)
        }
        val weekdayMinutes = weekdayRecords.sumOf { it.durationMinutes }
        val weekendMinutes = weekendRecords.sumOf { it.durationMinutes }

        // Pagination setup: up to 18 records on first page (due to header & summary), 26 on subsequent pages
        val recordsPerPageFirst = 16
        val recordsPerPageOther = 25
        val sortedRecords = records.sortedBy { it.startTimeEpochMs }

        var currentRecordIndex = 0
        var pageNumber = 1
        val totalPages = if (sortedRecords.size <= recordsPerPageFirst) {
            1
        } else {
            1 + Math.ceil((sortedRecords.size - recordsPerPageFirst).toDouble() / recordsPerPageOther).toInt()
        }

        while (currentRecordIndex < sortedRecords.size || pageNumber == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var currentY = 40f

            if (pageNumber == 1) {
                // Document Header Banner
                currentY = drawHeader(canvas, paint, periodStr, settings, trLocale)

                // Summary Stats Cards
                currentY = drawSummaryCards(
                    canvas, paint, currentY,
                    totalMinutes, totalAmount,
                    weekdayMinutes, weekendMinutes
                )
            } else {
                // Small Header on continuation pages
                currentY = drawSubPageHeader(canvas, paint, periodStr, pageNumber, totalPages)
            }

            // Table Header
            currentY = drawTableHeader(canvas, paint, currentY)

            // Table Rows for this page
            val rowsForThisPage = if (pageNumber == 1) recordsPerPageFirst else recordsPerPageOther
            val endIndex = Math.min(currentRecordIndex + rowsForThisPage, sortedRecords.size)

            var rowIndex = 0
            for (i in currentRecordIndex until endIndex) {
                val record = sortedRecords[i]
                val isEven = rowIndex % 2 == 0
                currentY = drawTableRow(canvas, paint, currentY, record, isEven)
                rowIndex++
            }
            currentRecordIndex = endIndex

            // If this is the last page, draw Table Total & Signatures
            if (pageNumber == totalPages) {
                currentY = drawTableTotal(canvas, paint, currentY, totalMinutes, totalAmount, settings)

                // Signatures block at bottom
                drawSignaturesBlock(canvas, paint, settings)
            }

            // Page Number Footer
            drawPageFooter(canvas, paint, pageNumber, totalPages)

            document.finishPage(page)
            pageNumber++

            if (currentRecordIndex >= sortedRecords.size) break
        }

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return file
    }

    private fun drawHeader(
        canvas: Canvas,
        paint: Paint,
        periodStr: String,
        settings: AppSettings,
        locale: Locale
    ): Float {
        var y = 45f

        // Top decorative bar
        paint.color = Color.rgb(30, 58, 138) // Deep Blue
        canvas.drawRect(MARGIN_X, y, MARGIN_RIGHT, y + 4f, paint)
        y += 24f

        // Document Title
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 18f
        paint.color = Color.rgb(15, 23, 42)
        canvas.drawText("FAZLA MESAİ TAKİP ÇİZELGESİ", MARGIN_X, y, paint)

        // Subtitle / Period
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 11f
        paint.color = Color.rgb(71, 85, 105)
        val todayStr = TimeUtils.formatDate(LocalDate.now(TimeUtils.ISTANBUL_ZONE))
        canvas.drawText("Dönem: $periodStr   |   Düzenleme Tarihi: $todayStr", MARGIN_X, y + 16f, paint)
        y += 36f

        // Info Box (Employee & Workplace)
        val employeeName = settings.employeeName.ifEmpty { "Belirtilmedi" }
        val workplace = settings.workplaceName.ifEmpty { "Belirtilmedi" }

        paint.color = Color.rgb(241, 245, 249) // Slate 100
        val infoRect = RectF(MARGIN_X, y, MARGIN_RIGHT, y + 36f)
        canvas.drawRoundRect(infoRect, 6f, 6f, paint)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Çalışan Adı Soyadı:", MARGIN_X + 12f, y + 16f, paint)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(employeeName, MARGIN_X + 115f, y + 16f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("İş Yeri / Birim:", MARGIN_X + 280f, y + 16f, paint)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(workplace, MARGIN_X + 360f, y + 16f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Saat Ücreti:", MARGIN_X + 12f, y + 30f, paint)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Hafta İçi: %.2f TL  |  Hafta Sonu: %.2f TL".format(locale, settings.weekdayRate, settings.weekendRate), MARGIN_X + 115f, y + 30f, paint)

        return y + 46f
    }

    private fun drawSubPageHeader(
        canvas: Canvas,
        paint: Paint,
        periodStr: String,
        pageNumber: Int,
        totalPages: Int
    ): Float {
        val y = 45f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 12f
        paint.color = Color.rgb(30, 58, 138)
        canvas.drawText("FAZLA MESAİ TAKİP ÇİZELGESİ - $periodStr (Devam)", MARGIN_X, y, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 9f
        paint.color = Color.rgb(100, 116, 139)
        canvas.drawText("Sayfa $pageNumber / $totalPages", MARGIN_RIGHT - 70f, y, paint)

        paint.color = Color.rgb(203, 213, 225)
        canvas.drawLine(MARGIN_X, y + 8f, MARGIN_RIGHT, y + 8f, paint)

        return y + 20f
    }

    private fun drawSummaryCards(
        canvas: Canvas,
        paint: Paint,
        startY: Float,
        totalMinutes: Int,
        totalAmount: Double,
        weekdayMinutes: Int,
        weekendMinutes: Int
    ): Float {
        val cardWidth = (CONTENT_WIDTH - 24f) / 4f
        val cardHeight = 44f

        val cards = listOf(
            Triple("Toplam Mesai", "%d sa %02d dk".format(totalMinutes / 60, totalMinutes % 60), Color.rgb(30, 58, 138)),
            Triple("Hafta İçi", "%d sa %02d dk".format(weekdayMinutes / 60, weekdayMinutes % 60), Color.rgb(51, 65, 85)),
            Triple("Hafta Sonu", "%d sa %02d dk".format(weekendMinutes / 60, weekendMinutes % 60), Color.rgb(51, 65, 85)),
            Triple("Toplam Kazanç", TimeUtils.formatCurrency(totalAmount), Color.rgb(5, 150, 105))
        )

        for (i in cards.indices) {
            val (title, value, color) = cards[i]
            val left = MARGIN_X + i * (cardWidth + 8f)
            val rect = RectF(left, startY, left + cardWidth, startY + cardHeight)

            paint.color = Color.rgb(248, 250, 252)
            canvas.drawRoundRect(rect, 4f, 4f, paint)

            paint.color = Color.rgb(226, 232, 240)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(rect, 4f, 4f, paint)
            paint.style = Paint.Style.FILL

            // Top label
            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8f
            paint.color = Color.rgb(100, 116, 139)
            canvas.drawText(title, left + 8f, startY + 14f, paint)

            // Big Value
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 11f
            paint.color = color
            canvas.drawText(value, left + 8f, startY + 32f, paint)
        }

        return startY + cardHeight + 16f
    }

    private fun drawTableHeader(canvas: Canvas, paint: Paint, y: Float): Float {
        val height = 22f
        paint.color = Color.rgb(241, 245, 249) // Table Header Fill
        canvas.drawRect(MARGIN_X, y, MARGIN_RIGHT, y + height, paint)

        paint.color = Color.rgb(203, 213, 225)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(MARGIN_X, y, MARGIN_RIGHT, y + height, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        paint.color = Color.rgb(51, 65, 85)

        val colY = y + 14f
        canvas.drawText("Tarih", MARGIN_X + 6f, colY, paint)
        canvas.drawText("Mesai Türü", MARGIN_X + 75f, colY, paint)
        canvas.drawText("Giriş", MARGIN_X + 145f, colY, paint)
        canvas.drawText("Çıkış", MARGIN_X + 195f, colY, paint)
        canvas.drawText("Süre", MARGIN_X + 245f, colY, paint)
        canvas.drawText("Saat Ücr.", MARGIN_X + 315f, colY, paint)
        canvas.drawText("Tutar", MARGIN_X + 380f, colY, paint)
        canvas.drawText("Not", MARGIN_X + 445f, colY, paint)

        return y + height
    }

    private fun drawTableRow(
        canvas: Canvas,
        paint: Paint,
        y: Float,
        record: OvertimeRecord,
        isEven: Boolean
    ): Float {
        val height = 18f

        if (isEven) {
            paint.color = Color.rgb(255, 255, 255)
        } else {
            paint.color = Color.rgb(248, 250, 252) // subtle zebra
        }
        canvas.drawRect(MARGIN_X, y, MARGIN_RIGHT, y + height, paint)

        // Bottom border
        paint.color = Color.rgb(241, 245, 249)
        paint.strokeWidth = 0.5f
        canvas.drawLine(MARGIN_X, y + height, MARGIN_RIGHT, y + height, paint)

        // Text
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 8.5f
        paint.color = Color.rgb(30, 41, 59)

        val textY = y + 12.5f
        canvas.drawText(record.date, MARGIN_X + 6f, textY, paint)

        val isWeekend = record.overtimeType.contains("WEEKEND", ignoreCase = true) ||
                record.overtimeType.contains("Hafta Sonu", ignoreCase = true)
        val typeStr = if (isWeekend) "Hafta Sonu" else "Hafta İçi"
        if (isWeekend) {
            paint.color = Color.rgb(180, 83, 9) // Amber
        } else {
            paint.color = Color.rgb(51, 65, 85)
        }
        canvas.drawText(typeStr, MARGIN_X + 75f, textY, paint)

        paint.color = Color.rgb(30, 41, 59)
        canvas.drawText(record.startTimeStr, MARGIN_X + 145f, textY, paint)
        canvas.drawText(record.endTimeStr, MARGIN_X + 195f, textY, paint)

        val durationStr = "%d sa %02d dk".format(record.durationMinutes / 60, record.durationMinutes % 60)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(durationStr, MARGIN_X + 245f, textY, paint)
        paint.typeface = Typeface.DEFAULT

        canvas.drawText("%.2f TL".format(Locale.US, record.hourlyRate).replace('.', ','), MARGIN_X + 315f, textY, paint)

        paint.color = Color.rgb(5, 150, 105) // Green
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(TimeUtils.formatCurrency(record.totalAmount), MARGIN_X + 380f, textY, paint)
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.rgb(100, 116, 139)

        val truncatedNote = if (record.note.length > 14) record.note.take(13) + "…" else record.note
        canvas.drawText(truncatedNote, MARGIN_X + 445f, textY, paint)

        return y + height
    }

    private fun drawTableTotal(
        canvas: Canvas,
        paint: Paint,
        y: Float,
        totalMinutes: Int,
        totalAmount: Double,
        settings: AppSettings
    ): Float {
        var nextY = y
        val height = 24f
        paint.color = Color.rgb(241, 245, 249)
        canvas.drawRect(MARGIN_X, nextY, MARGIN_RIGHT, nextY + height, paint)

        paint.color = Color.rgb(203, 213, 225)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(MARGIN_X, nextY, MARGIN_RIGHT, nextY + height, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9.5f
        paint.color = Color.rgb(15, 23, 42)

        val textY = nextY + 15.5f
        canvas.drawText("TOPLAM MESAİ:", MARGIN_X + 120f, textY, paint)

        val totalHoursStr = "%d sa %02d dk".format(totalMinutes / 60, totalMinutes % 60)
        canvas.drawText(totalHoursStr, MARGIN_X + 245f, textY, paint)

        paint.color = Color.rgb(5, 150, 105)
        canvas.drawText(TimeUtils.formatCurrency(totalAmount), MARGIN_X + 380f, textY, paint)

        nextY += height + 3f

        if (settings.baseSalary > 0.0) {
            val h2 = 22f
            paint.color = Color.rgb(255, 255, 255)
            canvas.drawRect(MARGIN_X, nextY, MARGIN_RIGHT, nextY + h2, paint)

            paint.color = Color.rgb(226, 232, 240)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f
            canvas.drawRect(MARGIN_X, nextY, MARGIN_RIGHT, nextY + h2, paint)
            paint.style = Paint.Style.FILL

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 9f
            paint.color = Color.rgb(71, 85, 105)
            canvas.drawText("Taban Net Maaş:", MARGIN_X + 120f, nextY + 15f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.rgb(15, 23, 42)
            canvas.drawText(TimeUtils.formatCurrency(settings.baseSalary), MARGIN_X + 380f, nextY + 15f, paint)

            nextY += h2 + 3f

            val h3 = 24f
            paint.color = Color.rgb(236, 253, 245)
            canvas.drawRect(MARGIN_X, nextY, MARGIN_RIGHT, nextY + h3, paint)

            paint.color = Color.rgb(16, 185, 129)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRect(MARGIN_X, nextY, MARGIN_RIGHT, nextY + h3, paint)
            paint.style = Paint.Style.FILL

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 9.5f
            paint.color = Color.rgb(6, 95, 70)
            canvas.drawText("TAHMİNİ TOPLAM HAKEDİŞ (Maaş + Mesai):", MARGIN_X + 16f, nextY + 16f, paint)

            val grandTotal = settings.baseSalary + totalAmount
            paint.textSize = 10f
            paint.color = Color.rgb(5, 150, 105)
            canvas.drawText(TimeUtils.formatCurrency(grandTotal), MARGIN_X + 380f, nextY + 16f, paint)

            nextY += h3
        }

        return nextY + 16f
    }

    private fun drawSignaturesBlock(canvas: Canvas, paint: Paint, settings: AppSettings) {
        val y = 690f
        val boxWidth = 235f
        val boxHeight = 100f

        // Employee Signature Box (Left)
        val leftBox = RectF(MARGIN_X, y, MARGIN_X + boxWidth, y + boxHeight)
        paint.color = Color.rgb(248, 250, 252)
        canvas.drawRoundRect(leftBox, 6f, 6f, paint)

        paint.color = Color.rgb(226, 232, 240)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(leftBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        paint.color = Color.rgb(30, 58, 138)
        canvas.drawText("ÇALIŞAN BEYANI & İMZA", MARGIN_X + 12f, y + 16f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 7.5f
        paint.color = Color.rgb(100, 116, 139)
        canvas.drawText("Yukarıdaki mesai süreleri tarafımca fiilen yapılmıştır.", MARGIN_X + 12f, y + 28f, paint)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 8.5f
        val empName = settings.employeeName.ifEmpty { "...................................................." }
        canvas.drawText("Ad Soyad: $empName", MARGIN_X + 12f, y + 50f, paint)
        canvas.drawText("Tarih: ...... / ...... / 20.....", MARGIN_X + 12f, y + 68f, paint)
        canvas.drawText("İmza: ", MARGIN_X + 12f, y + 86f, paint)

        // Employer / Manager Signature Box (Right)
        val rightLeft = MARGIN_RIGHT - boxWidth
        val rightBox = RectF(rightLeft, y, MARGIN_RIGHT, y + boxHeight)
        paint.color = Color.rgb(248, 250, 252)
        canvas.drawRoundRect(rightBox, 6f, 6f, paint)

        paint.color = Color.rgb(226, 232, 240)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rightBox, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 9f
        paint.color = Color.rgb(30, 58, 138)
        canvas.drawText("BİRİM YETKİLİSİ ONAYI", rightLeft + 12f, y + 16f, paint)

        paint.typeface = Typeface.DEFAULT
        paint.textSize = 7.5f
        paint.color = Color.rgb(100, 116, 139)
        canvas.drawText("Mesai kayıtları ve hesaplanan tutarlar onaylanmıştır.", rightLeft + 12f, y + 28f, paint)

        paint.color = Color.rgb(15, 23, 42)
        paint.textSize = 8.5f
        canvas.drawText("Yetkili Adı: ....................................................", rightLeft + 12f, y + 50f, paint)
        canvas.drawText("Tarih: ...... / ...... / 20.....", rightLeft + 12f, y + 68f, paint)
        canvas.drawText("İmza / Kaşe: ", rightLeft + 12f, y + 86f, paint)
    }

    private fun drawPageFooter(canvas: Canvas, paint: Paint, pageNumber: Int, totalPages: Int) {
        val y = 815f
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 8f
        paint.color = Color.rgb(148, 163, 184)
        canvas.drawText("Mesai Takip Sistemi ile otomatik üretilmiştir.", MARGIN_X, y, paint)

        val pageStr = "Sayfa $pageNumber / $totalPages"
        val pageTextWidth = paint.measureText(pageStr)
        canvas.drawText(pageStr, MARGIN_RIGHT - pageTextWidth, y, paint)
    }
}
