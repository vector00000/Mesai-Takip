package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.receiver.OvertimeActionReceiver

object NotificationHelper {
    const val CHANNEL_ID = "mesai_tracking_channel_v2"
    const val FORGOTTEN_CHANNEL_ID = "mesai_forgotten_channel_v2"
    const val REMINDERS_CHANNEL_ID = "mesai_reminders_center_channel_v1"
    const val NOTIFICATION_ID = 1001
    const val FORGOTTEN_NOTIFICATION_ID = 1002
    const val EXTRA_TARGET_ROUTE = "extra_notification_target_route"

    fun canPostSystemNotifications(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!permGranted) return false
            }
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: Exception) {
            false
        }
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Mesai Takip Bildirimleri"
            val descriptionText = "Otomatik mesai başlangıcı ve aktif mesai durumu bildirimleri"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val forgottenName = "Unutulan Mesai Uyarısı"
            val forgottenDesc = "Uzun süren mesailerde hatırlatma bildirimleri"
            val forgottenChannel = NotificationChannel(
                FORGOTTEN_CHANNEL_ID,
                forgottenName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = forgottenDesc
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val remindersName = "Hatırlatmalar ve Başarı Bildirimleri"
            val remindersDesc = "Maaş günü, vardiya, eksik kayıt ve başarı hatırlatmaları"
            val remindersChannel = NotificationChannel(
                REMINDERS_CHANNEL_ID,
                remindersName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = remindersDesc
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(forgottenChannel)
            notificationManager.createNotificationChannel(remindersChannel)
        }
    }

    fun showOvertimeStartedNotification(
        context: Context,
        startTimeStr: String,
        isAutomatic: Boolean = true
    ): Boolean {
        if (!canPostSystemNotifications(context)) return false
        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, OvertimeActionReceiver::class.java).apply {
            action = OvertimeActionReceiver.ACTION_STOP_OVERTIME
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isAutomatic) "Mesai Başladı" else "Mesai Devam Ediyor"
        val contentText = if (isAutomatic) {
            "Mesainiz saat $startTimeStr itibarıyla otomatik olarak başlatıldı."
        } else {
            "Mesainiz saat $startTimeStr itibarıyla başlatıldı."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_overtime)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(mainPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "MESAİYİ DURDUR",
                stopPendingIntent
            )
            .setAutoCancel(false)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun postReminderOrCenterSystemNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        targetRoute: String = "HOME"
    ): Boolean {
        if (!canPostSystemNotifications(context)) return false
        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_ROUTE, targetRoute)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_overtime)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun cancelNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.cancel(FORGOTTEN_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    fun showForgottenOvertimeAlertNotification(
        context: Context,
        hoursElapsed: Int,
        startTimeStr: String
    ): Boolean {
        if (!canPostSystemNotifications(context)) return false
        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            10,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, OvertimeActionReceiver::class.java).apply {
            action = OvertimeActionReceiver.ACTION_STOP_OVERTIME
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FORGOTTEN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_overtime)
            .setContentTitle("⚠️ Hâlâ mesaide misiniz?")
            .setContentText("Mesainiz $hoursElapsed saattir devam ediyor (Başlangıç: $startTimeStr). Açık unuttuysanız şimdi durdurabilirsiniz.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Mesainiz $hoursElapsed saattir devam ediyor (Başlangıç: $startTimeStr). Sayacı açık unuttuysanız aşağıdaki butona dokunarak hemen durdurabilirsiniz.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(mainPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "MESAİYİ DURDUR",
                stopPendingIntent
            )
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(FORGOTTEN_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun cancelForgottenNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(FORGOTTEN_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }
}
