package com.example.informationfatigue.ui.weekly

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.example.informationfatigue.data.DataRecord
import com.example.informationfatigue.data.DataRepository
import java.util.Calendar

data class TopAppStat(
    val packageName: String,
    val count: Int
)

data class WeeklyMetricSummary(
    val sessionCount: Int = 0,
    val minSessionRequired: Int = 50,
    val isReady: Boolean = false,
    val avgAppUsageSec: Float = 0f,
    val avgSwitchPerHour: Float = 0f,
    val avgUniqueAppCount: Float = 0f,
    val avgConcentrationRatio: Float = 0f,
    val topApps: List<TopAppStat> = emptyList()
)

data class WeeklyComparisonUiState(
    val thisWeek: WeeklyMetricSummary = WeeklyMetricSummary(),
    val lastWeek: WeeklyMetricSummary = WeeklyMetricSummary()
)

class WeeklySummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository(application)

    val weeklyComparison: LiveData<WeeklyComparisonUiState> = repository.allRecords.map { allRecords ->
        val thisWeekRange = weekRangeSec(weekOffset = 0)
        val lastWeekRange = weekRangeSec(weekOffset = -1)

        val thisWeekRecords = allRecords.filter {
            it.screen_on_timestamp_unix >= thisWeekRange.first && it.screen_on_timestamp_unix < thisWeekRange.second
        }
        val lastWeekRecords = allRecords.filter {
            it.screen_on_timestamp_unix >= lastWeekRange.first && it.screen_on_timestamp_unix < lastWeekRange.second
        }

        WeeklyComparisonUiState(
            thisWeek = computeWeeklyMetricSummary(thisWeekRecords),
            lastWeek = computeWeeklyMetricSummary(lastWeekRecords)
        )
    }

    private fun weekRangeSec(weekOffset: Int): Pair<Long, Long> {
        val startCal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }

        val endCal = Calendar.getInstance().apply {
            timeInMillis = startCal.timeInMillis
            add(Calendar.WEEK_OF_YEAR, 1)
        }

        return (startCal.timeInMillis / 1000L) to (endCal.timeInMillis / 1000L)
    }

    private fun computeWeeklyMetricSummary(records: List<DataRecord>): WeeklyMetricSummary {
        val minSessionRequired = 50
        val topApps = computeTopApps(records)
        if (records.size < minSessionRequired) {
            return WeeklyMetricSummary(
                sessionCount = records.size,
                minSessionRequired = minSessionRequired,
                isReady = false,
                topApps = topApps
            )
        }

        return WeeklyMetricSummary(
            sessionCount = records.size,
            minSessionRequired = minSessionRequired,
            isReady = true,
            avgAppUsageSec = records.map { it.foreground_app_duration_sum }.average().toFloat(),
            avgSwitchPerHour = records.map { it.foreground_app_switch_per_hour }.average().toFloat(),
            avgUniqueAppCount = records.map { it.unique_foreground_app_count.toFloat() }.average().toFloat(),
            avgConcentrationRatio = records.map { it.concentration_ratio }.average().toFloat(),
            topApps = topApps
        )
    }

    private fun computeTopApps(records: List<DataRecord>): List<TopAppStat> {
        val counts = mutableMapOf<String, Int>()
        for (record in records) {
            val firstPackage = parseFirstUniquePackage(record.unique_foreground_apps_and_durations) ?: continue
            counts[firstPackage] = (counts[firstPackage] ?: 0) + 1
        }

        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(3)
            .map { TopAppStat(packageName = it.key, count = it.value) }
    }

    private fun parseFirstUniquePackage(serialized: String): String? {
        if (serialized.isBlank()) return null

        val firstEntry = serialized.split('|').firstOrNull()?.trim().orEmpty()
        if (firstEntry.isEmpty()) return null

        val delimiterIndex = firstEntry.indexOf(':')
        if (delimiterIndex <= 0) return null

        return firstEntry.substring(0, delimiterIndex)
    }
}
