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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataCollectionService : Service() {

    companion object {
        private const val TAG = "DataCollectionService"
        private const val CHANNEL_ID = "data_collection_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "service_prefs"
        private const val KEY_IS_RUNNING = "is_running"
        private const val KEY_T1 = "screen_on_t1"
        private const val KEY_T2 = "screen_off_t2"
        const val ACTION_START_FRESH     = "com.example.informationfatigue.ACTION_START_FRESH"
        const val ACTION_STOP_GRACEFULLY = "com.example.informationfatigue.ACTION_STOP_GRACEFULLY"

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

        /**
         * Requests a graceful stop: the service will record T2 for any
         * in-progress session, collect it, then stop itself.
         */
        fun stop(context: Context) {
            val intent = Intent(context, DataCollectionService::class.java).apply {
                action = ACTION_STOP_GRACEFULLY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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

        when (intent?.action) {
            ACTION_START_FRESH     -> handleFreshStart()
            ACTION_STOP_GRACEFULLY -> handleGracefulStop()
            // null = START_STICKY restart; screen events drive collection
        }

        return START_STICKY
    }

    // ─── Screen event handlers ────────────────────────────────────────────

    private fun handleScreenOn() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val t1 = prefs.getLong(KEY_T1, 0L)
        val t2 = prefs.getLong(KEY_T2, 0L)
        val now = System.currentTimeMillis()

        Log.d(TAG, "Screen ON: T1=$t1 T2=$t2")

        if (t1 > 0L && t2 > t1) {
            collectSession(t1, t2)
        }

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

    // ─── Start / Stop handlers ────────────────────────────────────────────

    private fun handleFreshStart() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_T1, System.currentTimeMillis())
                .putLong(KEY_T2, 0L)
                .apply()
            Log.d(TAG, "Fresh start, screen ON → T1 set")
        } else {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_T1, 0L)
                .putLong(KEY_T2, 0L)
                .apply()
        }
    }

    /**
     * Graceful stop:
     * 1. If screen is ON, snapshot now as T2 for the current session.
     * 2. If a complete session [T1, T2] exists, collect it with NonCancellable
     *    to ensure it finishes even as the job is being torn down.
     * 3. Clear T1/T2 immediately to prevent a concurrent screen-ON event from
     *    double-collecting the same session.
     * 4. Call stopSelf() after collection (or immediately if nothing to collect).
     */
    private fun handleGracefulStop() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val now = System.currentTimeMillis()

        val t1 = prefs.getLong(KEY_T1, 0L)
        val t2 = if (pm.isInteractive) now else prefs.getLong(KEY_T2, 0L)

        // Clear immediately so no concurrent screen event re-processes this session
        prefs.edit()
            .putLong(KEY_T1, 0L)
            .putLong(KEY_T2, 0L)
            .apply()

        if (t1 > 0L && t2 > t1) {
            Log.d(TAG, "Graceful stop: collecting final session T1=$t1 T2=$t2")
            scope.launch {
                withContext(NonCancellable) {
                    collectSessionInternal(t1, t2)
                }
                stopSelf()
            }
        } else {
            Log.d(TAG, "Graceful stop: no session to collect, stopping now")
            stopSelf()
        }
    }

    // ─── Collection ───────────────────────────────────────────────────────

    /** Launches a background collection for a complete [t1, t2] session. */
    private fun collectSession(t1: Long, t2: Long) {
        scope.launch {
            collectSessionInternal(t1, t2)
        }
    }

    /** Core collection logic — call inside an appropriate coroutine context. */
    private suspend fun collectSessionInternal(t1: Long, t2: Long) {
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
                Log.d(TAG, "Session saved: on=${record.screen_on_timestamp_dt} off=${record.screen_off_timestamp_dt} duration=${record.screen_duration}s")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Collection error", e)
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────

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

    // ─── Lifecycle ────────────────────────────────────────────────────────

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
