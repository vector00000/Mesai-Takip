package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AppNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationCenterDao {
    @Query("SELECT * FROM app_notifications ORDER BY createdAtEpochMs DESC")
    fun getAllNotificationsFlow(): Flow<List<AppNotification>>

    @Query("SELECT * FROM app_notifications ORDER BY createdAtEpochMs DESC")
    suspend fun getAllNotificationsList(): List<AppNotification>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadCountFlow(): Flow<Int>

    @Query("SELECT * FROM app_notifications WHERE eventKey = :eventKey LIMIT 1")
    suspend fun getNotificationByEventKey(eventKey: String): AppNotification?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotificationIgnoreDuplicate(notification: AppNotification): Long

    @Query("UPDATE app_notifications SET isRead = :isRead WHERE id = :id")
    suspend fun setNotificationReadState(id: Long, isRead: Boolean)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllAsRead()

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: Long)

    @Query("DELETE FROM app_notifications")
    suspend fun clearAllNotifications()
}
