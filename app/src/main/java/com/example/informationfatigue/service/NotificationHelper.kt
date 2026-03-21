package com.example.informationfatigue.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.informationfatigue.R

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "collection_status_channel"
        private const val CHANNEL_NAME = "데이터 수집 상태"
        private const val CHANNEL_DESC = "데이터 수집 진행률 및 결과 알림"
        private const val NOTIFICATION_ID = 12001
    }

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannel()
    }

    fun showStarted() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_collecting_title))
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showCompleted(insertedCount: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_complete_title))
            .setContentText(context.getString(R.string.notification_complete_text, insertedCount))
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showFailed() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_failed_title))
            .setContentText(context.getString(R.string.notification_failed_text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESC
        }
        manager.createNotificationChannel(channel)
    }
}
