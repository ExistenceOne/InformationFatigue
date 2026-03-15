package com.example.informationfatigue.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_records")
data class DataRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val device_id: String,
    val screen_on_timestamp_unix: Long,   // Unix seconds
    val screen_on_timestamp_dt: String,   // "yyyy-MM-dd HH:mm:ss"
    val screen_off_timestamp_unix: Long,  // Unix seconds
    val screen_off_timestamp_dt: String,  // "yyyy-MM-dd HH:mm:ss"
    val off_and_on_gap: Long,             // seconds (screen_off - screen_on)
    val app_switch_count: Int,
    val unique_app_count: Int,
    val app_duration_mean: Float,         // seconds
    val app_duration_std: Float,          // seconds
    val app_switch_per_hour: Float        // app_switch_count / (off_and_on_gap / 3600)
)
