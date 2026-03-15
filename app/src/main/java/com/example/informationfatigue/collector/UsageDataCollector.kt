package com.example.informationfatigue.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlin.math.sqrt

/**
 * Collects app usage data using UsageStatsManager.queryEvents().
 * Queries events in [startTime, endTime] to compute:
 * - app_switch_count
 * - unique_app_count
 * - app_duration_mean (seconds)
 * - app_duration_std  (seconds)
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

    fun collect(startTime: Long, endTime: Long): UsageData {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return UsageData(0, 0, 0f, 0f)

        val events = usageStatsManager.queryEvents(startTime, endTime)
            ?: return UsageData(0, 0, 0f, 0f)

        val sessions = mutableListOf<AppSession>()
        val resumedMap = mutableMapOf<String, Long>()
        val foregroundSequence = mutableListOf<String>()

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    resumedMap[pkg] = event.timeStamp
                    foregroundSequence.add(pkg)
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = resumedMap.remove(pkg)
                    if (start != null) {
                        sessions.add(AppSession(pkg, start, event.timeStamp))
                    }
                }
            }
        }

        // Close any open sessions at endTime
        for ((pkg, start) in resumedMap) {
            sessions.add(AppSession(pkg, start, endTime))
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
