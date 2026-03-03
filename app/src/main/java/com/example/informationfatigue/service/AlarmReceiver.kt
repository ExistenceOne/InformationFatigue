package com.example.informationfatigue.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * Receives alarm broadcasts and triggers data collection in the
 * DataCollectionService. This ensures collection happens even when
 * the app is dozing.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val REQUEST_CODE = 1234

        fun getPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm received, triggering collection")

        // Acquire wake lock to ensure collection completes
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "InformationFatigue:AlarmWake"
        )
        wakeLock.acquire(60_000L) // 1 minute max

        try {
            // Start service with COLLECT action
            val serviceIntent = Intent(context, DataCollectionService::class.java).apply {
                action = DataCollectionService.ACTION_COLLECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}
