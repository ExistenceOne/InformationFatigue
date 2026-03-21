package com.example.informationfatigue.worker

import android.content.Context
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.informationfatigue.collector.DataAggregator
import com.example.informationfatigue.collector.UsageDataCollector
import com.example.informationfatigue.data.DataRepository
import com.example.informationfatigue.service.NotificationHelper

class DailyDataCollectionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = DataRepository(applicationContext)
        val usageCollector = UsageDataCollector(applicationContext)
        val notificationHelper = NotificationHelper(applicationContext)
        val deviceId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        return try {
            notificationHelper.showStarted()
            
            val nowMs = System.currentTimeMillis()
            val latestOffSec = repository.getLatestScreenOffUnix()
            val startMs = if (latestOffSec != null) {
                latestOffSec * 1000L
            } else {
                0L
            }

            val sessions = usageCollector.collectScreenSessions(startMs, nowMs)
            var insertedCount = 0

            sessions.forEach { session ->
                val usageData = usageCollector.collect(session.startTime, session.endTime)
                if (usageData.uniqueAppsCount > 0) {
                    val record = DataAggregator.aggregate(
                        deviceId = deviceId,
                        screenOnMs = session.startTime,
                        screenOffMs = session.endTime,
                        usageData = usageData
                    )
                    repository.insert(record)
                    insertedCount += 1
                }
            }

            notificationHelper.showCompleted(insertedCount)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
