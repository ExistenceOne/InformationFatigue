package com.example.informationfatigue.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.informationfatigue.R
import com.example.informationfatigue.collector.DataAggregator
import com.example.informationfatigue.collector.NotificationMonitorService
import com.example.informationfatigue.collector.ScreenEventTracker
import com.example.informationfatigue.collector.UsageDataCollector
import com.example.informationfatigue.data.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DataCollectionService : Service() {

    companion object {
        private const val TAG = "DataCollectionService"
        private const val CHANNEL_ID = "data_collection_channel"
        private const val NOTIFICATION_ID = 1001
        private const val FIVE_MIN_MS = 5 * 60 * 1000L
        private const val TEN_MIN_MS = 10 * 60 * 1000L
        const val ACTION_COLLECT = "com.example.informationfatigue.ACTION_COLLECT"
        const val ACTION_START_FRESH = "com.example.informationfatigue.ACTION_START_FRESH"

        fun start(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_START_FRESH
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java)
            context.stopService(intent)
            // Cancel pending alarms
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = AlarmReceiver.getPendingIntent(context)
            alarmManager.cancel(pi)
        }

        fun isRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("is_running", false)
        }

        fun getNextCollectionTimeMs(context: Context): Long {
            val prefs = context.getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            return prefs.getLong("next_collection_time", 0L)
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var screenTracker: ScreenEventTracker
    private lateinit var usageCollector: UsageDataCollector
    private lateinit var repository: DataRepository

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        screenTracker = ScreenEventTracker(applicationContext)
        screenTracker.register()

        usageCollector = UsageDataCollector(applicationContext)
        repository = DataRepository(applicationContext)

        getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("is_running", true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        when (intent?.action) {
            ACTION_COLLECT -> {
                saveNextCollectionTime()
                performCollection()
            }
            ACTION_START_FRESH -> {
                saveNextCollectionTime()
                AlarmReceiver.scheduleNext(this)
            }
            // null = START_STICKY restart; alarm chain is maintained by AlarmReceiver
        }

        return START_STICKY
    }

    private fun performCollection() {
        scope.launch {
            try {
                // Skip collection when screen is OFF
                if (!screenTracker.isScreenOn()) {
                    Log.d(TAG, "Screen is OFF, skipping collection")
                    return@launch
                }

                val now = System.currentTimeMillis()
                val windowStart = now - TEN_MIN_MS

                // Acquire partial wake lock for the collection
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "InformationFatigue:Collection"
                )
                wakeLock.acquire(30_000L) // 30 second timeout

                try {
                    // 1. Screen data (real-time tracked)
                    val screenSnapshot = screenTracker.getSnapshot(windowStart)

                    // 2. Usage data (batch query)
                    val usageData = usageCollector.collect(windowStart, now)

                    // 3. Notification interaction count
                    val notifCount = NotificationMonitorService.getAndResetCount()

                    // 4. Aggregate
                    val record = DataAggregator.aggregate(
                        startTime = windowStart, // the 10-min window start
                        endTime = now,
                        screenSnapshot = screenSnapshot,
                        usageData = usageData,
                        notificationCount = notifCount
                    )

                    // 5. Save
                    repository.insert(record)

                    // 6. Prune old events (keep last 15 min)
                    screenTracker.pruneEventsBefore(now - TEN_MIN_MS - FIVE_MIN_MS)

                    Log.d(TAG, "Collection completed: $record")
                } finally {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Collection error", e)
            }
        }
    }

    private fun saveNextCollectionTime() {
        val nextTime = System.currentTimeMillis() + FIVE_MIN_MS
        getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            .edit().putLong("next_collection_time", nextTime).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        screenTracker.unregister()
        job.cancel()

        getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("is_running", false).apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
