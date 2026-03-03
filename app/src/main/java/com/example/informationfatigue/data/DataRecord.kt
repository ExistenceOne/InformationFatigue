package com.example.informationfatigue.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_records")
data class DataRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val start_time: Long,           // Unix ms
    val end_time: Long,             // Unix ms
    val screen_on_count: Int,
    val screen_off_count: Int,
    val screen_on_duration_ms: Long,
    val max_consecutive_ms: Long,
    val avg_app_session_ms: Long,
    val app_switch_count: Int,
    val distraction_index: Float,
    val unique_apps_count: Int,
    val notification_interaction_count: Int,
    val cumulative_screen_time_ms: Long
)
