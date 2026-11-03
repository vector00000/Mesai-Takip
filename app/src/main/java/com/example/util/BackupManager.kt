package com.example.util

import android.content.Context
import android.net.Uri
import com.example.data.model.AppSettings
import com.example.data.model.OvertimeRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

data class BackupData(
    val settings: AppSettings,
    val records: List<OvertimeRecord>
)

object BackupManager {

    fun createBackupFile(
        context: Context,
        settings: AppSettings,
        records: List<OvertimeRecord>
    ): File {
        val fileName = "mesai_takip_yedek_${System.currentTimeMillis()}.json"
        val file = File(ExportHelper.getExportsDir(context), fileName)

        val rootJson = JSONObject().apply {
            put("app", "Mesai Takip")
            put("version", 1)
            put("backupTimestamp", System.currentTimeMillis())
            put("recordCount", records.size)

            val settingsJson = JSONObject().apply {
                put("isSetupCompleted", settings.isSetupCompleted)
                put("weekdayRate", settings.weekdayRate)
                put("weekendRate", settings.weekendRate)
                put("weekdayStartTime", settings.weekdayStartTime)
                put("saturdayStartTime", settings.saturdayStartTime)
                put("sundayStartTime", settings.sundayStartTime)
                put("autoGlobalEnabled", settings.autoGlobalEnabled)
                put("autoMonday", settings.autoMonday)
                put("autoTuesday", settings.autoTuesday)
                put("autoWednesday", settings.autoWednesday)
                put("autoThursday", settings.autoThursday)
                put("autoFriday", settings.autoFriday)
                put("autoSaturday", settings.autoSaturday)
                put("autoSunday", settings.autoSunday)
                put("notificationsEnabled", settings.notificationsEnabled)
                put("skipDate", settings.skipDate)
                put("forgottenAlertEnabled", settings.forgottenAlertEnabled)
                put("forgottenAlertHours", settings.forgottenAlertHours)
                put("employeeName", settings.employeeName)
                put("workplaceName", settings.workplaceName)
                put("baseSalary", settings.baseSalary)
                put("salaryDayOfMonth", settings.salaryDayOfMonth)
            }
            put("settings", settingsJson)

            val recordsArray = JSONArray()
            for (rec in records) {
                val item = JSONObject().apply {
                    put("date", rec.date)
                    put("startTimeEpochMs", rec.startTimeEpochMs)
                    put("endTimeEpochMs", rec.endTimeEpochMs)
                    put("startTimeStr", rec.startTimeStr)
                    put("endTimeStr", rec.endTimeStr)
                    put("durationMinutes", rec.durationMinutes)
                    put("hourlyRate", rec.hourlyRate)
                    put("totalAmount", rec.totalAmount)
                    put("overtimeType", rec.overtimeType)
                    put("isAutomatic", rec.isAutomatic)
                    put("note", rec.note)
                    put("createdAtEpochMs", rec.createdAtEpochMs)
                }
                recordsArray.put(item)
            }
            put("records", recordsArray)
        }

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(rootJson.toString(2))
                writer.flush()
            }
        }

        return file
    }

    fun parseBackupFromUri(context: Context, uri: Uri): Result<BackupData> {
        return try {
            val content = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        content.append(line)
                        line = reader.readLine()
                    }
                }
            } ?: return Result.failure(Exception("Dosya okunamadı."))

            val rootJson = JSONObject(content.toString())

            val settingsJson = rootJson.optJSONObject("settings")
            val settings = if (settingsJson != null) {
                AppSettings(
                    id = 1,
                    isSetupCompleted = settingsJson.optBoolean("isSetupCompleted", true),
                    weekdayRate = settingsJson.optDouble("weekdayRate", 200.0),
                    weekendRate = settingsJson.optDouble("weekendRate", 266.0),
                    weekdayStartTime = settingsJson.optString("weekdayStartTime", "18:00"),
                    saturdayStartTime = settingsJson.optString("saturdayStartTime", "13:00"),
                    sundayStartTime = settingsJson.optString("sundayStartTime", "09:00"),
                    autoGlobalEnabled = settingsJson.optBoolean("autoGlobalEnabled", true),
                    autoMonday = settingsJson.optBoolean("autoMonday", true),
                    autoTuesday = settingsJson.optBoolean("autoTuesday", true),
                    autoWednesday = settingsJson.optBoolean("autoWednesday", true),
                    autoThursday = settingsJson.optBoolean("autoThursday", true),
                    autoFriday = settingsJson.optBoolean("autoFriday", true),
                    autoSaturday = settingsJson.optBoolean("autoSaturday", false),
                    autoSunday = settingsJson.optBoolean("autoSunday", false),
                    notificationsEnabled = settingsJson.optBoolean("notificationsEnabled", true),
                    skipDate = settingsJson.optString("skipDate", ""),
                    forgottenAlertEnabled = settingsJson.optBoolean("forgottenAlertEnabled", true),
                    forgottenAlertHours = settingsJson.optInt("forgottenAlertHours", 5),
                    employeeName = settingsJson.optString("employeeName", ""),
                    workplaceName = settingsJson.optString("workplaceName", ""),
                    baseSalary = settingsJson.optDouble("baseSalary", 0.0),
                    salaryDayOfMonth = settingsJson.optInt("salaryDayOfMonth", 1)
                )
            } else {
                AppSettings()
            }

            val recordsArray = rootJson.optJSONArray("records") ?: JSONArray()
            val recordsList = mutableListOf<OvertimeRecord>()

            for (i in 0 until recordsArray.length()) {
                val item = recordsArray.getJSONObject(i)
                val rec = OvertimeRecord(
                    id = 0,
                    date = item.optString("date", TimeUtils.todayDateStr()),
                    startTimeEpochMs = item.optLong("startTimeEpochMs", 0L),
                    endTimeEpochMs = item.optLong("endTimeEpochMs", 0L),
                    startTimeStr = item.optString("startTimeStr", "18:00"),
                    endTimeStr = item.optString("endTimeStr", "20:00"),
                    durationMinutes = item.optInt("durationMinutes", 120),
                    hourlyRate = item.optDouble("hourlyRate", 200.0),
                    totalAmount = item.optDouble("totalAmount", 400.0),
                    overtimeType = item.optString("overtimeType", "WEEKDAY"),
                    isAutomatic = item.optBoolean("isAutomatic", false),
                    note = item.optString("note", ""),
                    createdAtEpochMs = item.optLong("createdAtEpochMs", System.currentTimeMillis())
                )
                recordsList.add(rec)
            }

            Result.success(BackupData(settings = settings, records = recordsList))
        } catch (e: Exception) {
            Result.failure(Exception("Yedek dosyası ayrıştırılamadı: ${e.localizedMessage ?: e.message}"))
        }
    }
}
