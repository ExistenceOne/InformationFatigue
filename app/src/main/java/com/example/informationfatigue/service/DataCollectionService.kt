package com.example.informationfatigue.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.informationfatigue.R
import com.example.informationfatigue.collector.DataAggregator
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
        private const val PREFS_NAME = "service_prefs"
        private const val KEY_IS_RUNNING = "is_running"
        private const val KEY_T1 = "screen_on_t1"
        private const val KEY_T2 = "screen_off_t2"
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
            context.stopService(Intent(context, DataCollectionService::class.java))
        }

        fun isRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_IS_RUNNING, false)
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var usageCollector: UsageDataCollector
    private lateinit var repository: DataRepository
    private lateinit var deviceId: String

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON  -> handleScreenOn()
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        usageCollector = UsageDataCollector(applicationContext)
        repository = DataRepository(applicationContext)
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IS_RUNNING, true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        if (intent?.action == ACTION_START_FRESH) {
            // Fresh start: if screen is currently ON set T1, clear any stale T2
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) {
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putLong(KEY_T1, System.currentTimeMillis())
                    .putLong(KEY_T2, 0L)
                    .apply()
                Log.d(TAG, "Fresh start with screen ON, T1 set")
            } else {
                // Screen is off at start; clear T1/T2 so next ON starts fresh
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putLong(KEY_T1, 0L)
                    .putLong(KEY_T2, 0L)
                    .apply()
            }
        }

        return START_STICKY
    }

    private fun handleScreenOn() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val t1 = prefs.getLong(KEY_T1, 0L)
        val t2 = prefs.getLong(KEY_T2, 0L)
        val now = System.currentTimeMillis()

        Log.d(TAG, "Screen ON: T1=$t1 T2=$t2")

        // If a complete session [T1, T2] exists, collect it
        if (t1 > 0L && t2 > t1) {
            collectSession(t1, t2)
        }

        // Start new session: T1 = now, clear T2
        prefs.edit()
            .putLong(KEY_T1, now)
            .putLong(KEY_T2, 0L)
            .apply()
    }

    private fun handleScreenOff() {
        val now = System.currentTimeMillis()
        Log.d(TAG, "Screen OFF: T2=$now")
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_T2, now).apply()
    }

    private fun collectSession(t1: Long, t2: Long) {
        scope.launch {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "InformationFatigue:Collection"
                )
                wakeLock.acquire(30_000L)
                try {
                    val usageData = usageCollector.collect(t1, t2)
                    val record = DataAggregator.aggregate(deviceId, t1, t2, usageData)
                    repository.insert(record)
                    Log.d(TAG, "Session saved: on=${record.screen_on_timestamp_dt} off=${record.screen_off_timestamp_dt} gap=${record.off_and_on_gap}s")
                } finally {
                    if (wakeLock.isHeld) wakeLock.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Collection error", e)
            }
        }
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        job.cancel()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IS_RUNNING, false).apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
