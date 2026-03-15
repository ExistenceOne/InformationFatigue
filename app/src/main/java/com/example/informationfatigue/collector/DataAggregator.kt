package com.example.informationfatigue.collector

import com.example.informationfatigue.data.DataRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Assembles a DataRecord from a screen ON/OFF session and its usage data.
 *
 * @param deviceId     Android ID of the device
 * @param screenOnMs   Timestamp when screen turned ON (Unix ms, T1)
 * @param screenOffMs  Timestamp when screen turned OFF (Unix ms, T2)
 * @param usageData    App usage stats for the [screenOnMs, screenOffMs] window
 */
object DataAggregator {

    private val dtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun aggregate(
        deviceId: String,
        screenOnMs: Long,
        screenOffMs: Long,
        usageData: UsageDataCollector.UsageData
    ): DataRecord {
        val onUnix = screenOnMs / 1000
        val offUnix = screenOffMs / 1000
        val gapSec = offUnix - onUnix

        val switchPerHour = if (gapSec > 0) {
            usageData.appSwitchCount.toFloat() / (gapSec / 3600f)
        } else {
            0f
        }

        return DataRecord(
            device_id = deviceId,
            screen_on_timestamp_unix = onUnix,
            screen_on_timestamp_dt = dtFormat.format(Date(screenOnMs)),
            screen_off_timestamp_unix = offUnix,
            screen_off_timestamp_dt = dtFormat.format(Date(screenOffMs)),
            off_and_on_gap = gapSec,
            app_switch_count = usageData.appSwitchCount,
            unique_app_count = usageData.uniqueAppsCount,
            app_duration_mean = usageData.avgAppSessionSec,
            app_duration_std = usageData.stdAppSessionSec,
            app_switch_per_hour = switchPerHour
        )
    }
}
