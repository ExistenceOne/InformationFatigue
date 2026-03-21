package com.example.informationfatigue.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_records")
data class DataRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val device_id: String,
    val screen_on_timestamp_unix: Long,          // Unix seconds
    val screen_on_timestamp_dt: String,          // "yyyy-MM-dd HH:mm:ss"
    val screen_off_timestamp_unix: Long,         // Unix seconds
    val screen_off_timestamp_dt: String,         // "yyyy-MM-dd HH:mm:ss"
    val screen_duration: Long,                   // screen_off - screen_on (seconds)
    val unique_foreground_app_count: Int,
    val foreground_app_count: Int,
    val foreground_app_switch_count: Int,
    val foreground_app_switch_per_hour: Float,   // foreground_app_switch_count / (foreground_app_duration_sum / 3600)
    val foreground_app_duration_sum: Float,      // seconds
    val unique_foreground_app_duration_max: Float, // seconds (top app duration among unique apps)
    val concentration_ratio: Float,              // unique_foreground_app_duration_max / foreground_app_duration_sum
    val foreground_apps_and_durations: String,   // "pkg1:dur1|pkg2:dur2" — ordered by execution
    val unique_foreground_apps_and_durations: String, // "pkg1:dur1|pkg2:dur2" — unique, sorted by duration desc
    val genres_and_durations: String             // "genreId1:dur1|genreId2:dur2" — includes zero-duration genres
)
