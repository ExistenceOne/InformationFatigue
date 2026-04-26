package com.example.informationfatigue.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import io.github.kdroidfilter.storekit.gplay.scrapper.services.getGooglePlayApplicationInfo
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Collects app usage data using UsageStatsManager.queryEvents().
 */
class UsageDataCollector(private val context: Context) {

    data class ScreenSession(
        val startTime: Long,
        val endTime: Long,
        val stateSeedStartTime: Long = startTime
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

    private data class PackageActiveState(
        var activeCount: Int = 0,
        var activeStartTime: Long? = null
    )

    private data class AppCollectionResult(
        val sessions: List<AppSession>,
        val resumedPackageSequence: List<String>
    )

    companion object {
        private const val TAG = "UsageDataCollector"
        private const val LOOKBACK_MS = 5_000L
        private const val AFK_TIMEOUT_MS = 30 * 60 * 1000L
        private const val MIN_SESSION_DURATION_MS = 1_000L
        private const val OTHER_GENRE_ID = "OTHER"

        private val HCI_EVENT_TYPES = setOf(
            UsageEvents.Event.SCREEN_INTERACTIVE,
            UsageEvents.Event.ACTIVITY_RESUMED,
            UsageEvents.Event.USER_INTERACTION,
            UsageEvents.Event.KEYGUARD_HIDDEN
        )

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

    private suspend fun resolveGenreId(packageName: String): String? {
        if (appGenreCache.has(packageName)) {
            val cached = appGenreCache.get(packageName)
            Log.d(TAG, "Cache hit for $packageName: $cached")
            return cached
        }

        return try {
            Log.d(TAG, "Scraping Play Store for $packageName...")
            throttle()
            val appInfo = getGooglePlayApplicationInfo(packageName, "ko", "kr")
            val resolved = appInfo.genreId.takeIf { it.isNotBlank() } ?: OTHER_GENRE_ID
            Log.d(TAG, "Scraped $packageName: $resolved")
            appGenreCache.put(packageName, resolved)
            resolved
        } catch (e: Exception) {
            if (isNotFoundException(e)) {
                Log.w(TAG, "App not found (404) for $packageName: ${e.message}")
                appGenreCache.putNotTarget(packageName)
                null
            } else {
                Log.e(TAG, "Scraping failed for $packageName (not 404): ${e.message}", e)
                // Re-throw for reasons other than 404 to stop collection
                throw e
            }
        }
    }

    private fun isNotFoundException(e: Exception): Boolean {
        val msg = e.message ?: ""
        // Heuristic to detect 404 errors. Ktor's ClientRequestException usually contains "404" in message.
        return e.javaClass.simpleName.contains("ClientRequestException") && msg.contains("404") ||
                msg.contains("404 Not Found") ||
                msg.contains("not found", ignoreCase = true)
    }

    private fun isHciEvent(eventType: Int): Boolean = eventType in HCI_EVENT_TYPES

    private fun countSwitchesWithinPackages(
        resumedPackageSequence: List<String>,
        allowedPackages: Set<String>
    ): Int {
        if (resumedPackageSequence.isEmpty() || allowedPackages.isEmpty()) return 0

        val filtered = resumedPackageSequence.filter { it in allowedPackages }
        if (filtered.size < 2) return 0

        return filtered.zipWithNext().count { (prev, next) -> prev != next }
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
        var isScreenInteractive = false
        var currentStart: Long? = null
        var stateSeedStart: Long? = null
        var lastHciAt: Long? = null
        var isAfkPaused = false
        val total = timeline.size

        fun addScreenSessionIfValid(sessionStart: Long?, sessionEnd: Long, seedStart: Long?) {
            if (sessionStart == null || seedStart == null) return
            if (sessionEnd <= sessionStart) return
            sessions.add(
                ScreenSession(
                    startTime = sessionStart,
                    endTime = sessionEnd,
                    stateSeedStartTime = seedStart
                )
            )
        }

        fun pauseForAfkIfNeeded(now: Long) {
            if (!isScreenInteractive || isAfkPaused) return
            val lastHci = lastHciAt ?: return
            val afkStart = lastHci + AFK_TIMEOUT_MS
            if (now >= afkStart) {
                addScreenSessionIfValid(currentStart, afkStart, stateSeedStart)
                currentStart = null
                isAfkPaused = true
            }
        }

        for ((index, event) in timeline.withIndex()) {
            pauseForAfkIfNeeded(event.timeStamp)

            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (!isScreenInteractive) {
                        isScreenInteractive = true
                        currentStart = event.timeStamp
                        stateSeedStart = event.timeStamp
                    } else if (isAfkPaused) {
                        currentStart = event.timeStamp
                        isAfkPaused = false
                    } else if (currentStart == null) {
                        currentStart = event.timeStamp
                    }
                    lastHciAt = event.timeStamp
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    if (isScreenInteractive && !isAfkPaused) {
                        addScreenSessionIfValid(currentStart, event.timeStamp, stateSeedStart)
                    }
                    isScreenInteractive = false
                    currentStart = null
                    stateSeedStart = null
                    lastHciAt = null
                    isAfkPaused = false
                }
                else -> {
                    if (isHciEvent(event.eventType)) {
                        if (isAfkPaused) {
                            currentStart = event.timeStamp
                            isAfkPaused = false
                        } else if (isScreenInteractive && currentStart == null) {
                            currentStart = event.timeStamp
                        }

                        // While not AFK, ScreenSession starts only at SCREEN_INTERACTIVE.
                        if (isScreenInteractive) {
                            lastHciAt = event.timeStamp
                        }
                    }
                }
            }
            onProgress?.invoke((((index + 1).toFloat() / total) * 100f).toInt().coerceIn(0, 100))
        }

        pauseForAfkIfNeeded(endTime)

        val openStart = currentStart
        if (isScreenInteractive && !isAfkPaused && openStart != null && endTime > openStart) {
            addScreenSessionIfValid(openStart, endTime, stateSeedStart)
        }

        Log.d(TAG, "Found ${sessions.size} screen sessions.")
        onProgress?.invoke(100)
        return sessions
    }

    suspend fun collect(
        session: ScreenSession,
        onAppProgress: ((Int, Int) -> Unit)? = null
    ): UsageData {
        return collectInternal(
            startTime = session.startTime,
            endTime = session.endTime,
            stateSeedStartTime = session.stateSeedStartTime,
            onAppProgress = onAppProgress
        )
    }

    suspend fun collect(
        startTime: Long,
        endTime: Long,
        onAppProgress: ((Int, Int) -> Unit)? = null
    ): UsageData {
        return collectInternal(
            startTime = startTime,
            endTime = endTime,
            stateSeedStartTime = startTime,
            onAppProgress = onAppProgress
        )
    }

    private suspend fun collectInternal(
        startTime: Long,
        endTime: Long,
        stateSeedStartTime: Long,
        onAppProgress: ((Int, Int) -> Unit)? = null
    ): UsageData {
        if (endTime <= startTime) return emptyUsageData()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyUsageData()

        val normalizedSeed = minOf(stateSeedStartTime, startTime)
        val queryStartTime = maxOf(0L, normalizedSeed - LOOKBACK_MS)
        val events = usageStatsManager.queryEvents(queryStartTime, endTime) ?: return emptyUsageData()

        val appCollectionResult = collectAppSessions(
            events = events,
            startTime = startTime,
            endTime = endTime
        )

        val sortedSessions = appCollectionResult.sessions.sortedBy { it.startTime }
        val mergedSessions = mutableListOf<MergedSession>()

        for (session in sortedSessions) {
            val dur = session.endTime - session.startTime
            if (mergedSessions.isNotEmpty() && mergedSessions.last().packageName == session.packageName) {
                mergedSessions.last().durationMs += dur
            } else {
                mergedSessions.add(MergedSession(session.packageName, dur))
            }
        }

        val sessionsWithGenre = mutableListOf<SessionWithGenre>()
        for ((index, merged) in mergedSessions.withIndex()) {
            onAppProgress?.invoke(index, mergedSessions.size)
            val genreId = resolveGenreId(merged.packageName)
            if (genreId != null) {
                sessionsWithGenre.add(
                    SessionWithGenre(
                        merged.packageName,
                        merged.durationMs,
                        if (genreId in SUPPORTED_GENRE_IDS) genreId else OTHER_GENRE_ID
                    )
                )
            }
        }
        onAppProgress?.invoke(mergedSessions.size, mergedSessions.size)

        if (sessionsWithGenre.isEmpty()) return emptyUsageData()

        val playStoreQueryablePackages = sessionsWithGenre.map { it.packageName }.toSet()
        val playStoreSwitchCount = countSwitchesWithinPackages(
            resumedPackageSequence = appCollectionResult.resumedPackageSequence,
            allowedPackages = playStoreQueryablePackages
        )

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
            appSwitchCount = playStoreSwitchCount,
            uniqueAppsCount = uniqueApps.size,
            appCount = sessionsWithGenre.size,
            durationSumSec = sumSec,
            uniqueDurationMaxSec = uniqueForegroundAppsAndDurations.firstOrNull()?.second ?: 0f,
            foregroundAppsAndDurations = foregroundAppsAndDurations,
            uniqueForegroundAppsAndDurations = uniqueForegroundAppsAndDurations,
            genresAndDurations = SUPPORTED_GENRE_IDS.map { it to (genresMap[it] ?: 0f) }
        )
    }

    private fun collectAppSessions(
        events: UsageEvents,
        startTime: Long,
        endTime: Long
    ): AppCollectionResult {
        val sessions = mutableListOf<AppSession>()
        val packageStates = mutableMapOf<String, PackageActiveState>()
        val resumedPackageSequence = mutableListOf<String>()
        var lastResumedPackage: String? = null

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (event.timeStamp >= startTime) {
                        if (lastResumedPackage != pkg) {
                            resumedPackageSequence.add(pkg)
                        }
                    }
                    lastResumedPackage = pkg

                    val state = packageStates.getOrPut(pkg) { PackageActiveState() }
                    if (state.activeCount == 0) {
                        state.activeStartTime = event.timeStamp
                    }
                    state.activeCount += 1
                }
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val state = packageStates[pkg] ?: continue
                    if (state.activeCount <= 0) continue

                    state.activeCount -= 1
                    if (state.activeCount == 0) {
                        val activeStart = state.activeStartTime ?: continue
                        val clippedStart = maxOf(activeStart, startTime)
                        val clippedEnd = minOf(event.timeStamp, endTime)
                        if (clippedEnd - clippedStart >= MIN_SESSION_DURATION_MS) {
                            sessions.add(AppSession(pkg, clippedStart, clippedEnd))
                        }
                        state.activeStartTime = null
                    }
                }
            }
        }

        for ((packageName, state) in packageStates) {
            val activeStart = state.activeStartTime ?: continue
            if (state.activeCount > 0) {
                val clippedStart = maxOf(activeStart, startTime)
                if (endTime - clippedStart >= MIN_SESSION_DURATION_MS) {
                    sessions.add(AppSession(packageName, clippedStart, endTime))
                }
            }
        }

        return AppCollectionResult(
            sessions = sessions,
            resumedPackageSequence = resumedPackageSequence
        )
    }
}
