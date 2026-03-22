package com.example.informationfatigue.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import io.github.kdroidfilter.storekit.gplay.scrapper.services.getGooglePlayApplicationInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * Collects app usage data using UsageStatsManager.queryEvents().
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
        val uniqueDurationMaxSec: Float,
        val foregroundAppsAndDurations: List<Pair<String, Float>>,
        val uniqueForegroundAppsAndDurations: List<Pair<String, Float>>,
        val genresAndDurations: List<Pair<String, Float>>
    ) {
        val categoriesAndDurations: List<Pair<String, Float>>
            get() = genresAndDurations
    }

    private data class AppSession(
        val packageName: String,
        val startTime: Long,
        val endTime: Long
    )

    private data class MergedSession(
        val packageName: String,
        var durationMs: Long
    )

    companion object {
        private const val TAG = "UsageDataCollector"
        private const val LOOKBACK_MS = 5_000L
        private const val OTHER_GENRE_ID = "OTHER"

        private val SUPPORTED_GENRE_IDS = listOf(
            "ART_AND_DESIGN", "AUTO_AND_VEHICLES", "ANDROID_WEAR", "BEAUTY",
            "BOOKS_AND_REFERENCE", "BUSINESS", "COMICS", "COMMUNICATION",
            "DATING", "EDUCATION", "ENTERTAINMENT", "EVENTS", "FINANCE",
            "FOOD_AND_DRINK", "HEALTH_AND_FITNESS", "HOUSE_AND_HOME",
            "LIBRARIES_AND_DEMO", "LIFESTYLE", "MAPS_AND_NAVIGATION",
            "MEDICAL", "MUSIC_AND_AUDIO", "NEWS_AND_MAGAZINES", "PARENTING",
            "PERSONALIZATION", "PHOTOGRAPHY", "PRODUCTIVITY", "SHOPPING",
            "SOCIAL", "SPORTS", "TOOLS", "TRAVEL_AND_LOCAL", "VIDEO_PLAYERS",
            "WATCH_FACE", "WEATHER", "GAME", "GAME_ACTION", "GAME_ADVENTURE",
            "GAME_ARCADE", "GAME_BOARD", "GAME_CARD", "GAME_CASINO",
            "GAME_CASUAL", "GAME_EDUCATIONAL", "GAME_MUSIC", "GAME_PUZZLE",
            "GAME_RACING", "GAME_ROLE_PLAYING", "GAME_SIMULATION",
            "GAME_SPORTS", "GAME_STRATEGY", "GAME_TRIVIA", "GAME_WORD",
            "FAMILY", OTHER_GENRE_ID
        )
    }

    private val appGenreCache = AppGenreCache(context.applicationContext)

    private data class SessionWithGenre(
        val packageName: String,
        val durationMs: Long,
        val genreId: String
    )

    private fun emptyUsageData(): UsageData {
        return UsageData(
            appSwitchCount = 0,
            uniqueAppsCount = 0,
            appCount = 0,
            durationSumSec = 0f,
            uniqueDurationMaxSec = 0f,
            foregroundAppsAndDurations = emptyList(),
            uniqueForegroundAppsAndDurations = emptyList(),
            genresAndDurations = SUPPORTED_GENRE_IDS.map { it to 0f }
        )
    }

    private suspend fun throttle() {
        val delayTime = 5000L + Random.nextLong(3001)
        delay(delayTime)
    }

    private fun resolveGenreId(packageName: String): String? {
        if (appGenreCache.has(packageName)) {
            val cached = appGenreCache.get(packageName)
            Log.d(TAG, "Cache hit for $packageName: $cached")
            return cached
        }

        return runBlocking {
            try {
                Log.d(TAG, "Scraping Play Store for $packageName...")
                throttle()
                val appInfo = getGooglePlayApplicationInfo(packageName, "ko", "kr")
                val resolved = appInfo.genreId.takeIf { it.isNotBlank() } ?: OTHER_GENRE_ID
                Log.d(TAG, "Scraped $packageName: $resolved")
                appGenreCache.put(packageName, resolved)
                resolved
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scrape $packageName: ${e.message}")
                appGenreCache.putNotTarget(packageName)
                null
            }
        }
    }

    fun collectScreenSessions(
        startTime: Long,
        endTime: Long,
        onProgress: ((Int) -> Unit)? = null
    ): List<ScreenSession> {
        Log.d(TAG, "collectScreenSessions: $startTime to $endTime")
        if (endTime <= startTime) {
            onProgress?.invoke(100)
            return emptyList()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val events = usageStatsManager.queryEvents(startTime, endTime) ?: return emptyList()
        val timeline = mutableListOf<UsageEvents.Event>()

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            timeline.add(event)
        }

        if (timeline.isEmpty()) {
            Log.d(TAG, "No usage events found in range.")
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
                    if (currentStart == null) currentStart = event.timeStamp
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    val start = currentStart
                    val end = event.timeStamp
                    if (start != null && end > start) {
                        sessions.add(ScreenSession(start, end))
                    }
                    currentStart = null
                }
            }
            onProgress?.invoke((((index + 1).toFloat() / total) * 100f).toInt().coerceIn(0, 100))
        }

        val openStart = currentStart
        if (openStart != null && endTime > openStart) {
            sessions.add(ScreenSession(openStart, endTime))
        }

        Log.d(TAG, "Found ${sessions.size} screen sessions.")
        onProgress?.invoke(100)
        return sessions
    }

    fun collect(startTime: Long, endTime: Long): UsageData {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyUsageData()

        val queryStartTime = maxOf(0L, startTime - LOOKBACK_MS)
        val events = usageStatsManager.queryEvents(queryStartTime, endTime) ?: return emptyUsageData()

        val sessions = mutableListOf<AppSession>()
        val resumedMap = mutableMapOf<String, Long>()

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedMap[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val resumeTime = resumedMap.remove(pkg) ?: continue
                    val clippedStart = maxOf(resumeTime, startTime)
                    val clippedEnd = minOf(event.timeStamp, endTime)
                    if (clippedEnd - clippedStart >= 1000L) {
                        sessions.add(AppSession(pkg, clippedStart, clippedEnd))
                    }
                }
            }
        }

        for ((packageName, resumeTime) in resumedMap) {
            val clippedStart = maxOf(resumeTime, startTime)
            if (endTime - clippedStart >= 1000L) {
                sessions.add(AppSession(packageName, clippedStart, endTime))
            }
        }

        val sortedSessions = sessions.sortedBy { it.startTime }
        val mergedSessions = mutableListOf<MergedSession>()

        for (session in sortedSessions) {
            val dur = session.endTime - session.startTime
            if (mergedSessions.isNotEmpty() && mergedSessions.last().packageName == session.packageName) {
                mergedSessions.last().durationMs += dur
            } else {
                mergedSessions.add(MergedSession(session.packageName, dur))
            }
        }

        val sessionsWithGenre = mergedSessions.mapNotNull { merged ->
            val genreId = resolveGenreId(merged.packageName) ?: return@mapNotNull null
            SessionWithGenre(merged.packageName, merged.durationMs, if (genreId in SUPPORTED_GENRE_IDS) genreId else OTHER_GENRE_ID)
        }

        if (sessionsWithGenre.isEmpty()) return emptyUsageData()

        val uniqueApps = sessionsWithGenre.map { it.packageName }.toSet()
        val sumSec = (sessionsWithGenre.sumOf { it.durationMs } / 1000.0).toFloat()
        val foregroundAppsAndDurations = sessionsWithGenre.map { it.packageName to it.durationMs / 1000f }
        val uniqueForegroundAppsAndDurations = sessionsWithGenre.groupBy { it.packageName }
            .map { (pkg, list) -> pkg to list.sumOf { it.durationMs / 1000.0 }.toFloat() }
            .sortedByDescending { it.second }

        val genresMap = SUPPORTED_GENRE_IDS.associateWith { 0f }.toMutableMap()
        for (session in sessionsWithGenre) {
            genresMap[session.genreId] = (genresMap[session.genreId] ?: 0f) + (session.durationMs / 1000f)
        }

        return UsageData(
            appSwitchCount = maxOf(0, sessionsWithGenre.size - 1),
            uniqueAppsCount = uniqueApps.size,
            appCount = sessionsWithGenre.size,
            durationSumSec = sumSec,
            uniqueDurationMaxSec = uniqueForegroundAppsAndDurations.firstOrNull()?.second ?: 0f,
            foregroundAppsAndDurations = foregroundAppsAndDurations,
            uniqueForegroundAppsAndDurations = uniqueForegroundAppsAndDurations,
            genresAndDurations = SUPPORTED_GENRE_IDS.map { it to (genresMap[it] ?: 0f) }
        )
    }
}
