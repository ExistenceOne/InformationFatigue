package com.example.informationfatigue.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlin.math.sqrt

/**
 * Collects app usage data using UsageStatsManager.queryEvents().
 *
 * Root cause handled here:
 * Android fires ACTIVITY_RESUMED *before* dispatching ACTION_SCREEN_ON, so by
 * the time our BroadcastReceiver records T1 = SystemClock.now(), the RESUMED
 * event already has a timestamp slightly earlier than T1.  Querying exactly
 * [T1, T2] therefore misses that RESUMED and produces 0 apps / 0 duration.
 *
 * Fix: query [T1 - LOOKBACK_MS, T2], clip every session to [T1, T2], and
 * prepend the "starting app" (the app already in foreground at T1) to the
 * foreground sequence so that app_switch_count is also correct.
 */
class UsageDataCollector(private val context: Context) {

    data class UsageData(
        val appSwitchCount: Int,
        val uniqueAppsCount: Int,
        val avgAppSessionSec: Float,
        val stdAppSessionSec: Float
    )

    private data class AppSession(
        val packageName: String,
        val startTime: Long,
        val endTime: Long
    )

    companion object {
        // How far before T1 to extend the query.
        // ACTIVITY_RESUMED typically fires a few hundred ms before ACTION_SCREEN_ON
        // is delivered; 5 s is conservative but still a tiny query window.
        private const val LOOKBACK_MS = 5_000L
    }

    fun collect(startTime: Long, endTime: Long): UsageData {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return UsageData(0, 0, 0f, 0f)

        val events = usageStatsManager.queryEvents(startTime - LOOKBACK_MS, endTime)
            ?: return UsageData(0, 0, 0f, 0f)

        val sessions = mutableListOf<AppSession>()
        // pkg -> the timestamp of its last ACTIVITY_RESUMED (may be before startTime)
        val resumedMap = mutableMapOf<String, Long>()
        // foreground sequence within [startTime, endTime] only (for switch counting)
        val foregroundSequence = mutableListOf<String>()
        // the last app to receive RESUMED *before* startTime = already-in-foreground app
        var startingApp: String? = null

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    resumedMap[pkg] = event.timeStamp
                    if (event.timeStamp >= startTime) {
                        foregroundSequence.add(pkg)
                    } else {
                        // Keep updating so we end up with the most-recent pre-T1 app
                        startingApp = pkg
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val resumeTime = resumedMap.remove(pkg) ?: continue
                    // Clip to the actual screen-on window
                    val clippedStart = maxOf(resumeTime, startTime)
                    val clippedEnd   = minOf(event.timeStamp, endTime)
                    if (clippedEnd > clippedStart) {
                        sessions.add(AppSession(pkg, clippedStart, clippedEnd))
                    }
                }
            }
        }

        // Close sessions still open at endTime
        // (app stayed in foreground until the screen turned off)
        for ((pkg, resumeTime) in resumedMap) {
            val clippedStart = maxOf(resumeTime, startTime)
            if (endTime > clippedStart) {
                sessions.add(AppSession(pkg, clippedStart, endTime))
            }
        }

        // Prepend the app that was already in foreground at T1 so that
        // the first real switch (startingApp → nextApp) is counted correctly.
        if (startingApp != null &&
            (foregroundSequence.isEmpty() || foregroundSequence.first() != startingApp)
        ) {
            foregroundSequence.add(0, startingApp!!)
        }

        val uniqueApps = sessions.map { it.packageName }.toSet()

        var switchCount = 0
        for (i in 1 until foregroundSequence.size) {
            if (foregroundSequence[i] != foregroundSequence[i - 1]) switchCount++
        }

        val durationsMs = sessions.map { (it.endTime - it.startTime).toDouble() }
        val avgSec = if (durationsMs.isNotEmpty()) {
            (durationsMs.average() / 1000.0).toFloat()
        } else {
            0f
        }
        val stdSec = if (durationsMs.size > 1) {
            val mean = durationsMs.average()
            val variance = durationsMs.sumOf { (it - mean) * (it - mean) } / durationsMs.size
            (sqrt(variance) / 1000.0).toFloat()
        } else {
            0f
        }

        return UsageData(
            appSwitchCount = switchCount,
            uniqueAppsCount = uniqueApps.size,
            avgAppSessionSec = avgSec,
            stdAppSessionSec = stdSec
        )
    }
}
