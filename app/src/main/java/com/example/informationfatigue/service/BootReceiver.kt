package com.example.informationfatigue.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts the DataCollectionService after a device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, restarting service")

            // Check if service was previously running
            val prefs = context.getSharedPreferences("service_prefs", Context.MODE_PRIVATE)
            val wasRunning = prefs.getBoolean("is_running", false)

            if (wasRunning) {
                DataCollectionService.start(context)
            }
        }
    }
}
