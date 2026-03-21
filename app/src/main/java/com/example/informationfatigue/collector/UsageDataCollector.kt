package com.example.informationfatigue.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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

    data class ScreenSession(
        val startTime: Long,
        val endTime: Long
    )

    data class UsageData(
        val appSwitchCount: Int,
        val uniqueAppsCount: Int,
        val appCount: Int,
        val durationSumSec: Float,
        val durationMeanSec: Float,
        val uniqueDurationMaxSec: Float,
        val foregroundAppsAndDurations: List<Pair<String, Float>>,
        val uniqueForegroundAppsAndDurations: List<Pair<String, Float>>,
        val categoriesAndDurations: List<Pair<Int, Float>>
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

    fun collectScreenSessions(
        startTime: Long,
        endTime: Long,
        onProgress: ((Int) -> Unit)? = null
    ): List<ScreenSession> {
        if (endTime <= startTime) {
            onProgress?.invoke(100)
            return emptyList()
        }

        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()

        val events = usageStatsManager.queryEvents(startTime, endTime) ?: return emptyList()
        val timeline = mutableListOf<UsageEvents.Event>()

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            timeline.add(event)
        }

        if (timeline.isEmpty()) {
            onProgress?.invoke(100)
            return emptyList()
        }

        timeline.sortBy { it.timeStamp }

        val sessions = mutableListOf<ScreenSession>()
        var currentStart: Long? = null
        val total = timeline.size

        for ((index, event) in timeline.withIndex()) {
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (currentStart == null) {
                        currentStart = event.timeStamp
                    }
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                UsageEvents.Event.DEVICE_STARTUP -> {
                    val start = currentStart
                    val end = event.timeStamp
                    if (start != null && end > start) {
                        sessions.add(ScreenSession(start, end))
                    }
                    currentStart = null
                }
            }

            val progress = (((index + 1).toFloat() / total.toFloat()) * 100f).toInt().coerceIn(0, 100)
            onProgress?.invoke(progress)
        }

        // Close an open session at query end if no terminating event arrived.
        val openStart = currentStart
        if (openStart != null && endTime > openStart) {
            sessions.add(ScreenSession(openStart, endTime))
        }

        onProgress?.invoke(100)
        return sessions
    }

    fun collect(startTime: Long, endTime: Long): UsageData {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return UsageData(0, 0, 0, 0f, 0f, 0f, emptyList(), emptyList(), emptyList())

        val events = usageStatsManager.queryEvents(startTime - LOOKBACK_MS, endTime)
            ?: return UsageData(0, 0, 0, 0f, 0f, 0f, emptyList(), emptyList(), emptyList())

        val sessions = mutableListOf<AppSession>()
        // pkg -> the timestamp of its last ACTIVITY_RESUMED (may be before startTime)
        val resumedMap = mutableMapOf<String, Long>()

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    resumedMap[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val resumeTime = resumedMap.remove(pkg) ?: continue
                    val clippedStart = maxOf(resumeTime, startTime)
                    val clippedEnd   = minOf(event.timeStamp, endTime)
                    // 투명 액티비티, 백그라운드 처리 방지: 1초 이상만 수집
                    if (clippedEnd - clippedStart >= 1000L) {
                        sessions.add(AppSession(pkg, clippedStart, clippedEnd))
                    }
                }
            }
        }

        // Close sessions still open at endTime
        for ((packageName, resumeTime) in resumedMap) {
            val clippedStart = maxOf(resumeTime, startTime)
            if (endTime - clippedStart >= 1000L) {
                sessions.add(AppSession(packageName, clippedStart, endTime))
            }
        }

        val sortedSessions = sessions.sortedBy { it.startTime }

        // 연속된 동일 패키지명 활동 병합
        class MergedSession(val packageName: String, val durationMs: Long)
        val mergedSessions = mutableListOf<MergedSession>()
        for (session in sortedSessions) {
            val dur = session.endTime - session.startTime
            if (mergedSessions.isNotEmpty() && mergedSessions.last().packageName == session.packageName) {
                val last = mergedSessions.removeAt(mergedSessions.size - 1)
                mergedSessions.add(MergedSession(last.packageName, last.durationMs + dur))
            } else {
                mergedSessions.add(MergedSession(session.packageName, dur))
            }
        }

        val uniqueApps = mergedSessions.map { it.packageName }.toSet()
        val appCount = mergedSessions.size
        val switchCount = maxOf(0, mergedSessions.size - 1)

        val sumSec = (mergedSessions.sumOf { it.durationMs } / 1000.0).toFloat()
        val meanSec = if (mergedSessions.isNotEmpty()) sumSec / mergedSessions.size else 0f

        val foregroundAppsAndDurations = mergedSessions
            .map { Pair(it.packageName, it.durationMs / 1000f) }

        val uniqueForegroundAppsAndDurations = mergedSessions
            .groupBy { it.packageName }
            .map { (pkg, list) -> Pair(pkg, list.sumOf { it.durationMs / 1000.0 }.toFloat()) }
            .sortedByDescending { it.second }

        val uniqueDurationMaxSec = if (uniqueForegroundAppsAndDurations.isNotEmpty()) {
            uniqueForegroundAppsAndDurations.maxOf { it.second }
        } else {
            0f
        }

        val pm = context.packageManager
        val categoriesMap = mutableMapOf<Int, Float>()
        for ((pkg, duration) in uniqueForegroundAppsAndDurations) {
            val category = try {
                pm.getApplicationInfo(pkg, 0).category
            } catch (e: Exception) {
                -1 // ApplicationInfo.CATEGORY_UNDEFINED
            }
            categoriesMap[category] = (categoriesMap[category] ?: 0f) + duration
        }
        val categoriesAndDurations = categoriesMap
            .map { Pair(it.key, it.value) }
            .sortedByDescending { it.second }

        return UsageData(
            appSwitchCount = switchCount,
            uniqueAppsCount = uniqueApps.size,
            appCount = appCount,
            durationSumSec = sumSec,
            durationMeanSec = meanSec,
            uniqueDurationMaxSec = uniqueDurationMaxSec,
            foregroundAppsAndDurations = foregroundAppsAndDurations,
            uniqueForegroundAppsAndDurations = uniqueForegroundAppsAndDurations,
            categoriesAndDurations = categoriesAndDurations
        )
    }
}
