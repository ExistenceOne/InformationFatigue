package com.example.informationfatigue.ui.main

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.example.informationfatigue.collector.DataAggregator
import com.example.informationfatigue.collector.UsageDataCollector
import com.example.informationfatigue.data.DataRecord
import com.example.informationfatigue.data.DataRepository
import com.example.informationfatigue.service.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

data class TodaySummary(
    val totalScreenTimeSec: Long = 0L,  // sum of screen_duration (seconds)
    val sessionCount: Int = 0,
    val avgSwitchPerHour: Float = 0f,
    val totalAppSwitches: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataRepository(application)
    private val usageCollector = UsageDataCollector(application)
    private val notificationHelper = NotificationHelper(application)
    private val deviceId: String =
        Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)

    private val _isCollecting = MutableLiveData(false)
    val isCollecting: LiveData<Boolean> = _isCollecting

    private val _collectionProgress = MutableLiveData(0)
    val collectionProgress: LiveData<Int> = _collectionProgress

    private val _recentCollectedRecords = MutableLiveData<List<DataRecord>>(emptyList())
    val recentCollectedRecords: LiveData<List<DataRecord>> = _recentCollectedRecords

    val allRecords: LiveData<List<DataRecord>> = repository.allRecords
    val totalRecordCount: LiveData<Int> = allRecords.map { it.size }

    companion object {
        private const val TAG = "MainViewModel"
    }

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
            totalScreenTimeSec += records[i].screen_duration
            totalAppSwitches += records[i].foreground_app_switch_count
            switchPerHourSum += records[i].foreground_app_switch_per_hour
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

    fun collectUsageEventsSinceLastRecord() {
        if (_isCollecting.value == true) return

        viewModelScope.launch(Dispatchers.IO) {
            _isCollecting.postValue(true)
            _collectionProgress.postValue(0)
            notificationHelper.showStarted()

            try {
                val nowMs = System.currentTimeMillis()
                val latestOffSec = repository.getLatestScreenOffUnix()
                val startMs = if (latestOffSec != null) {
                    latestOffSec * 1000L
                } else {
                    // At initial collection, start from 0 to collect all available history (~7 days)
                    0L
                }

                // 1. Session discovery (roughly 5% of total work)
                val sessions = usageCollector.collectScreenSessions(startMs, nowMs) { progress ->
                    val normalized = (progress.toFloat() * 0.05f).toInt()
                    _collectionProgress.postValue(normalized)
                }

                if (sessions.isEmpty()) {
                    _collectionProgress.postValue(100)
                    _isCollecting.postValue(false)
                    notificationHelper.showCompleted(0)
                    return@launch
                }

                val inserted = mutableListOf<DataRecord>()
                val totalSessions = sessions.size

                // 2. Scraping and collecting data (remaining 95% of work)
                for ((sessionIndex, session) in sessions.withIndex()) {
                    val sessionBaseProgress = 5 + (sessionIndex.toFloat() / totalSessions * 95f)
                    val sessionWorkWeight = 95f / totalSessions

                    val usageData = usageCollector.collect(session.startTime, session.endTime) { appIndex, totalApps ->
                        val appProgressWithinSession = if (totalApps > 0) {
                            (appIndex.toFloat() / totalApps) * sessionWorkWeight
                        } else {
                            0f
                        }
                        val currentProgress = (sessionBaseProgress + appProgressWithinSession).toInt()
                        _collectionProgress.postValue(currentProgress.coerceIn(0, 99))
                    }
                    
                    if (usageData.uniqueAppsCount > 0) {
                        val record = DataAggregator.aggregate(
                            deviceId = deviceId,
                            screenOnMs = session.startTime,
                            screenOffMs = session.endTime,
                            usageData = usageData
                        )
                        repository.insert(record)
                        inserted.add(record)
                    }
                }

                _recentCollectedRecords.postValue(
                    inserted.sortedByDescending { it.screen_on_timestamp_unix }
                )
                _collectionProgress.postValue(100)
                notificationHelper.showCompleted(inserted.size)
            } catch (e: Exception) {
                Log.e(TAG, "Collection stopped due to error: ${e.message}", e)
                notificationHelper.showFailed()
                // Partial progress is kept because successful sessions are already in DB.
                // Next collection will start from the end of the last successful session.
            } finally {
                _isCollecting.postValue(false)
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            repository.deleteAll()
            _recentCollectedRecords.postValue(emptyList())
        }
    }
}
