package com.example.informationfatigue.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.PowerManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks screen ON/OFF events in real-time via BroadcastReceiver.
 *
 * Screen status enum:
 *   1 = ON (interactive, not doze)
 *   2 = OFF (not interactive, not doze)
 *   3 = DOZE (device idle mode active, interactive — rare)
 *   4 = DOZE_SUSPEND (device idle mode active, not interactive)
 *
 * Status 3 and 4 are excluded from screen-on duration calculations.
 * cumulative_screen_time resets after 4 consecutive hours of screen OFF (status 2).
 */
class ScreenEventTracker(private val context: Context) {

    data class ScreenEvent(
        val timestamp: Long,   // Unix ms
        val status: Int        // 1=ON, 2=OFF, 3=DOZE, 4=DOZE_SUSPEND
    )

    data class ScreenSnapshot(
        val screenOnCount: Int,
        val screenOffCount: Int,
        val screenOnDurationMs: Long,
        val maxConsecutiveMs: Long,
        val cumulativeScreenTimeMs: Long
    )

    private val events = CopyOnWriteArrayList<ScreenEvent>()
    private var currentStatus: Int = 2 // assume OFF initially
    private var lastStatusChangeTime: Long = System.currentTimeMillis()

    // Cumulative screen time state — persisted via SharedPreferences
    private val prefs: SharedPreferences =
        context.getSharedPreferences("screen_tracker_prefs", Context.MODE_PRIVATE)
    private var cumulativeScreenTimeMs: Long
        get() = prefs.getLong("cumulative_screen_time_ms", 0L)
        set(value) { prefs.edit().putLong("cumulative_screen_time_ms", value).apply() }
    private var lastOffStartTime: Long
        get() = prefs.getLong("last_off_start_time", 0L)
        set(value) { prefs.edit().putLong("last_off_start_time", value).apply() }

    companion object {
        private const val FOUR_HOURS_MS = 4 * 60 * 60 * 1000L
    }

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            val newStatus = resolveStatus()

            recordTransition(now, newStatus)
        }
    }

    private val dozeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            val newStatus = resolveStatus()

            recordTransition(now, newStatus)
        }
    }

    @Synchronized
    private fun recordTransition(now: Long, newStatus: Int) {
        if (newStatus == currentStatus) return

        val prevStatus = currentStatus
        val duration = now - lastStatusChangeTime

        // If previous status was ON (1), add to cumulative
        if (prevStatus == 1 && duration > 0) {
            cumulativeScreenTimeMs += duration
        }

        // If transitioning to OFF (2), record off start time
        if (newStatus == 2) {
            lastOffStartTime = now
        }

        // Check if we should reset cumulative (4h continuous OFF)
        if (prevStatus == 2 && newStatus != 2) {
            val offDuration = now - lastOffStartTime
            if (offDuration >= FOUR_HOURS_MS) {
                cumulativeScreenTimeMs = 0L
            }
        }

        events.add(ScreenEvent(now, newStatus))
        currentStatus = newStatus
        lastStatusChangeTime = now
    }

    private fun resolveStatus(): Int {
        val isInteractive = powerManager.isInteractive
        val isDoze = powerManager.isDeviceIdleMode
        return when {
            isDoze && isInteractive -> 3   // DOZE but interactive (rare)
            isDoze && !isInteractive -> 4  // DOZE_SUSPEND
            isInteractive -> 1             // ON
            else -> 2                      // OFF
        }
    }

    fun register() {
        // Determine initial state
        currentStatus = resolveStatus()
        lastStatusChangeTime = System.currentTimeMillis()

        // If currently OFF and no prior off start, set it
        if (currentStatus == 2 && lastOffStartTime == 0L) {
            lastOffStartTime = lastStatusChangeTime
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(screenReceiver, screenFilter)

        val dozeFilter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        context.registerReceiver(dozeReceiver, dozeFilter)
    }

    fun unregister() {
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        try {
            context.unregisterReceiver(dozeReceiver)
        } catch (_: Exception) {}
    }

    /**
     * Returns a snapshot of screen events within the window [windowStartMs, now].
     * Also checks for 4h OFF cumulative reset before computing.
     */
    @Synchronized
    fun getSnapshot(windowStartMs: Long): ScreenSnapshot {
        val now = System.currentTimeMillis()

        // Check 4h continuous OFF reset
        if (currentStatus == 2) {
            val offDuration = now - lastOffStartTime
            if (offDuration >= FOUR_HOURS_MS) {
                cumulativeScreenTimeMs = 0L
            }
        }

        // Collect events within the window
        val windowEvents = events.filter { it.timestamp in windowStartMs..now }

        // Determine the status at the start of the window
        val eventsBeforeWindow = events.filter { it.timestamp < windowStartMs }
        val statusAtWindowStart = eventsBeforeWindow.lastOrNull()?.status ?: currentStatus

        // Build timeline: add synthetic start event
        val timeline = mutableListOf<ScreenEvent>()
        timeline.add(ScreenEvent(windowStartMs, statusAtWindowStart))
        timeline.addAll(windowEvents)
        // Add synthetic end event at current status
        timeline.add(ScreenEvent(now, currentStatus))

        var screenOnCount = 0
        var screenOffCount = 0
        var screenOnDurationMs = 0L
        var maxConsecutiveMs = 0L
        var currentConsecutive = 0L

        for (i in 0 until timeline.size - 1) {
            val event = timeline[i]
            val nextEvent = timeline[i + 1]
            val segDuration = nextEvent.timestamp - event.timestamp

            when (event.status) {
                1 -> { // ON
                    screenOnDurationMs += segDuration
                    currentConsecutive += segDuration
                }
                2 -> { // OFF
                    if (currentConsecutive > maxConsecutiveMs) {
                        maxConsecutiveMs = currentConsecutive
                    }
                    currentConsecutive = 0L
                }
                3, 4 -> {
                    // Doze — excluded from ON duration
                    // Also break consecutive ON streak
                    if (currentConsecutive > maxConsecutiveMs) {
                        maxConsecutiveMs = currentConsecutive
                    }
                    currentConsecutive = 0L
                }
            }
        }
        // Final check for consecutive
        if (currentConsecutive > maxConsecutiveMs) {
            maxConsecutiveMs = currentConsecutive
        }

        // Count ON/OFF transitions
        for (event in windowEvents) {
            when (event.status) {
                1 -> screenOnCount++
                2 -> screenOffCount++
            }
        }

        // Add current segment to cumulative if screen is ON right now
        var cumulative = cumulativeScreenTimeMs
        if (currentStatus == 1) {
            cumulative += (now - lastStatusChangeTime)
        }

        return ScreenSnapshot(
            screenOnCount = screenOnCount,
            screenOffCount = screenOffCount,
            screenOnDurationMs = screenOnDurationMs,
            maxConsecutiveMs = maxConsecutiveMs,
            cumulativeScreenTimeMs = cumulative
        )
    }

    /**
     * Returns true if the screen is currently ON (status 1).
     */
    fun isScreenOn(): Boolean = currentStatus == 1

    /**
     * Prune events older than the given timestamp.
     */
    fun pruneEventsBefore(timestampMs: Long) {
        events.removeAll { it.timestamp < timestampMs }
    }
}
