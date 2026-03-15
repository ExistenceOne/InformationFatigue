package com.example.informationfatigue.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.example.informationfatigue.data.DataRecord
import com.example.informationfatigue.data.DataRepository
import kotlinx.coroutines.launch
import java.util.Calendar

data class TodaySummary(
    val totalScreenTimeSec: Long = 0L,  // sum of off_and_on_gap (seconds)
    val sessionCount: Int = 0,
    val avgSwitchPerHour: Float = 0f,
    val totalAppSwitches: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository(application)

    val allRecords: LiveData<List<DataRecord>> = repository.allRecords

    /** 오늘(자정 이후) 수집 기록을 연속 4시간 sleep 경계로 잘라 집계 */
    val todaySummary: LiveData<TodaySummary> = allRecords.map { allList ->
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartSec = cal.timeInMillis / 1000
        val records = allList
            .filter { it.screen_on_timestamp_unix >= todayStartSec }
            .sortedBy { it.screen_on_timestamp_unix }
        computeTodaySummary(records)
    }

    private fun computeTodaySummary(records: List<DataRecord>): TodaySummary {
        if (records.isEmpty()) return TodaySummary()

        val fourHoursSec = 4 * 60 * 60L

        // Find the latest "4 hour gap" boundary to define current session
        var sessionStartIndex = 0
        for (i in 0 until records.size - 1) {
            val gap = records[i + 1].screen_on_timestamp_unix - records[i].screen_off_timestamp_unix
            if (gap >= fourHoursSec) sessionStartIndex = i + 1
        }

        var totalScreenTimeSec = 0L
        var totalAppSwitches = 0
        var switchPerHourSum = 0f

        for (i in sessionStartIndex until records.size) {
            totalScreenTimeSec += records[i].off_and_on_gap
            totalAppSwitches += records[i].app_switch_count
            switchPerHourSum += records[i].app_switch_per_hour
        }

        val count = records.size - sessionStartIndex
        val avgSwitchPerHour = if (count > 0) switchPerHourSum / count else 0f

        return TodaySummary(
            totalScreenTimeSec = totalScreenTimeSec,
            sessionCount = count,
            avgSwitchPerHour = avgSwitchPerHour,
            totalAppSwitches = totalAppSwitches
        )
    }

    fun getAllRecordsSync(callback: (List<DataRecord>) -> Unit) {
        viewModelScope.launch {
            val records = repository.getAll()
            callback(records)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }
}
