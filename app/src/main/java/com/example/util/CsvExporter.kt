package com.example.util

import android.content.Context
import com.example.data.model.OvertimeRecord
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.YearMonth

object CsvExporter {

    fun generateCsv(
        context: Context,
        records: List<OvertimeRecord>,
        yearMonth: YearMonth? = null
    ): File {
        val fileName = if (yearMonth != null) {
            "mesai_cizelgesi_${yearMonth.year}_${"%02d".format(yearMonth.monthValue)}.csv"
        } else {
            "mesai_gecmisi_tum_kayitlar_${System.currentTimeMillis()}.csv"
        }

        val file = File(ExportHelper.getExportsDir(context), fileName)
        FileOutputStream(file).use { fos ->
            // Write UTF-8 BOM so Microsoft Excel and Google Sheets render Turkish characters perfectly
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // CSV Headers
                val headers = listOf(
                    "Tarih",
                    "Mesai Türü",
                    "Başlangıç",
                    "Bitiş",
                    "Süre (Saat)",
                    "Süre (Toplam Dk)",
                    "Saatlik Ücret (TL)",
                    "Toplam Tutar (TL)",
                    "Kayıt Tipi",
                    "Açıklama / Not"
                )
                writer.write(headers.joinToString(";") + "\n")

                for (record in records) {
                    val hoursStr = "%d sa %02d dk".format(record.durationMinutes / 60, record.durationMinutes % 60)
                    val typeStr = if (record.overtimeType.contains("WEEKEND", ignoreCase = true) ||
                        record.overtimeType.contains("Hafta Sonu", ignoreCase = true)) {
                        "Hafta Sonu"
                    } else {
                        "Hafta İçi"
                    }
                    val methodStr = if (record.isAutomatic) "Otomatik" else "Manuel"
                    val sanitizedNote = record.note.replace("\"", "\"\"").replace("\n", " ")

                    val row = listOf(
                        record.date,
                        typeStr,
                        record.startTimeStr,
                        record.endTimeStr,
                        hoursStr,
                        record.durationMinutes.toString(),
                        "%.2f".format(java.util.Locale.US, record.hourlyRate).replace('.', ','),
                        "%.2f".format(java.util.Locale.US, record.totalAmount).replace('.', ','),
                        methodStr,
                        "\"$sanitizedNote\""
                    )
                    writer.write(row.joinToString(";") + "\n")
                }

                // Summary Row
                val totalMinutes = records.sumOf { it.durationMinutes }
                val totalAmount = records.sumOf { it.totalAmount }
                val totalHoursStr = "%d sa %02d dk".format(totalMinutes / 60, totalMinutes % 60)

                writer.write("\n")
                val summaryRow = listOf(
                    "GENEL TOPLAM",
                    "",
                    "",
                    "",
                    totalHoursStr,
                    totalMinutes.toString(),
                    "",
                    "%.2f".format(java.util.Locale.US, totalAmount).replace('.', ','),
                    "",
                    ""
                )
                writer.write(summaryRow.joinToString(";") + "\n")
                writer.flush()
            }
        }
        return file
    }
}
