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
    val totalScreenTimeMs: Long = 0L,
    val totalNotifications: Int = 0,
    val notifPerHour: Float = 0f,
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
        val todayStart = cal.timeInMillis
        val records = allList
            .filter { it.start_time >= todayStart }
            .sortedBy { it.start_time }
        computeTodaySummary(records)
    }

    private fun computeTodaySummary(records: List<DataRecord>): TodaySummary {
        if (records.isEmpty()) return TodaySummary()

        val fourHoursMs = 4 * 60 * 60 * 1000L

        // 가장 최근의 "연속 4시간 이상 screen-off 간격" 이후부터를 현재 세션으로 본다
        var sessionStartIndex = 0
        for (i in 0 until records.size - 1) {
            val gap = records[i + 1].start_time - records[i].end_time
            if (gap >= fourHoursMs) sessionStartIndex = i + 1
        }

        var totalScreenTimeMs = 0L
        var totalNotifications = 0
        var totalAppSwitches = 0

        for (i in sessionStartIndex until records.size) {
            totalScreenTimeMs += records[i].screen_on_duration_ms
            totalNotifications += records[i].notification_interaction_count
            totalAppSwitches += records[i].app_switch_count
        }

        // 세션 경과 시간으로 알림 빈도 계산
        val sessionDurationMs = records.last().end_time - records[sessionStartIndex].start_time
        val sessionHours = sessionDurationMs / 3_600_000.0
        val notifPerHour = if (sessionHours > 0) (totalNotifications / sessionHours).toFloat() else 0f

        return TodaySummary(
            totalScreenTimeMs = totalScreenTimeMs,
            totalNotifications = totalNotifications,
            notifPerHour = notifPerHour,
            totalAppSwitches = totalAppSwitches
        )
    }

    fun getAllRecordsSync(callback: (List<DataRecord>) -> Unit) {
        viewModelScope.launch {
            val records = repository.getAll()
            callback(records)
        }
    }
}
