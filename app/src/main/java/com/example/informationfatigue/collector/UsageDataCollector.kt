package com.example.informationfatigue.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Collects app usage data using UsageStatsManager.queryEvents().
 * Queries the last 10 minutes of events to compute:
 * - app_switch_count
 * - unique_apps_count
 * - avg_app_session_ms
 */
class UsageDataCollector(private val context: Context) {

    data class UsageData(
        val appSwitchCount: Int,
        val uniqueAppsCount: Int,
        val avgAppSessionMs: Long
    )

    private data class AppSession(
        val packageName: String,
        val startTime: Long,
        val endTime: Long
    )

    /**
     * Query usage events from [startTime, endTime] and compute aggregated data.
     */
    fun collect(startTime: Long, endTime: Long): UsageData {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return UsageData(0, 0, 0L)

        val events = usageStatsManager.queryEvents(startTime, endTime)
            ?: return UsageData(0, 0, 0L)

        val sessions = mutableListOf<AppSession>()
        // Track the last RESUMED event per package
        val resumedMap = mutableMapOf<String, Long>()
        // Track foreground app transitions for switch count
        val foregroundSequence = mutableListOf<String>()

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // Formerly MOVE_TO_FOREGROUND
                    resumedMap[pkg] = event.timeStamp
                    foregroundSequence.add(pkg)
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    // Formerly MOVE_TO_BACKGROUND
                    val start = resumedMap.remove(pkg)
                    if (start != null) {
                        sessions.add(AppSession(pkg, start, event.timeStamp))
                    }
                }
            }
        }

        // Close any open sessions at the end time
        for ((pkg, start) in resumedMap) {
            sessions.add(AppSession(pkg, start, endTime))
        }

        // Unique apps
        val uniqueApps = sessions.map { it.packageName }.toSet()

        // App switch count: number of times consecutive foreground apps differ
        var switchCount = 0
        for (i in 1 until foregroundSequence.size) {
            if (foregroundSequence[i] != foregroundSequence[i - 1]) {
                switchCount++
            }
        }

        // Average app session duration
        val totalSessionDuration = sessions.sumOf { it.endTime - it.startTime }
        val avgSessionMs = if (sessions.isNotEmpty()) {
            totalSessionDuration / sessions.size
        } else {
            0L
        }

        return UsageData(
            appSwitchCount = switchCount,
            uniqueAppsCount = uniqueApps.size,
            avgAppSessionMs = avgSessionMs
        )
    }
}
