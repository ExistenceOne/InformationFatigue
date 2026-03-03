package com.example.informationfatigue.collector

import com.example.informationfatigue.data.DataRecord

/**
 * Aggregates data from ScreenEventTracker, UsageDataCollector, and
 * NotificationMonitorService into a single DataRecord.
 */
object DataAggregator {

    fun aggregate(
        startTime: Long,
        endTime: Long,
        screenSnapshot: ScreenEventTracker.ScreenSnapshot,
        usageData: UsageDataCollector.UsageData,
        notificationCount: Int
    ): DataRecord {
        val distractionIndex = if (screenSnapshot.screenOnCount > 0) {
            usageData.appSwitchCount.toFloat() / screenSnapshot.screenOnCount.toFloat()
        } else {
            0f
        }

        return DataRecord(
            start_time = startTime,
            end_time = endTime,
            screen_on_count = screenSnapshot.screenOnCount,
            screen_off_count = screenSnapshot.screenOffCount,
            screen_on_duration_ms = screenSnapshot.screenOnDurationMs,
            max_consecutive_ms = screenSnapshot.maxConsecutiveMs,
            avg_app_session_ms = usageData.avgAppSessionMs,
            app_switch_count = usageData.appSwitchCount,
            distraction_index = distractionIndex,
            unique_apps_count = usageData.uniqueAppsCount,
            notification_interaction_count = notificationCount,
            cumulative_screen_time_ms = screenSnapshot.cumulativeScreenTimeMs
        )
    }
}
