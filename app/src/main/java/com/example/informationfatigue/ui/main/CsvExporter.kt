package com.example.informationfatigue.ui.main

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.informationfatigue.R
import com.example.informationfatigue.data.DataRecord
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exports DataRecord list to CSV and shares via FileProvider.
 *
 * Files are written to getExternalFilesDir(null)/exports/ so they are
 * accessible from the device file manager at:
 *   Android/data/com.example.informationfatigue/files/exports/
 *
 * Internal storage (filesDir/exports/) is used as fallback if external is unavailable.
 */
object CsvExporter {

    private const val EXPORTS_DIR = "exports"
    private val FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        .withZone(ZoneId.systemDefault())

    private val CSV_HEADER = listOf(
        "device_id",
        "screen_on_timestamp_unix",
        "screen_on_timestamp_dt",
        "screen_off_timestamp_unix",
        "screen_off_timestamp_dt",
        "screen_duration",
        "unique_foreground_app_count",
        "foreground_app_count",
        "foreground_app_switch_count",
        "foreground_app_switch_per_hour",
        "foreground_app_duration_sum",
        "unique_foreground_app_duration_max",
        "concentration_ratio",
        "foreground_apps_and_durations",
        "unique_foreground_apps_and_durations",
        "genres_and_durations"
    ).joinToString(",")

    fun exportToCsv(context: Context, records: List<DataRecord>): File? {
        if (records.isEmpty()) {
            Toast.makeText(context, R.string.csv_no_data, Toast.LENGTH_SHORT).show()
            return null
        }

        return try {
            // Prefer external files dir (visible in file manager); fall back to internal
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val exportDir = File(baseDir, EXPORTS_DIR).also { it.mkdirs() }

            val deviceId = records.first().device_id
            val firstOn  = records.first().screen_on_timestamp_unix
            val lastOff  = records.last().screen_off_timestamp_unix
            val firstOnStr = FILENAME_FORMATTER.format(Instant.ofEpochSecond(firstOn))
            val lastOffStr = FILENAME_FORMATTER.format(Instant.ofEpochSecond(lastOff))
            val fileName = "${deviceId}_${firstOnStr}_${lastOffStr}.csv"

            val file = File(exportDir, fileName)
            FileWriter(file).use { writer ->
                writer.appendLine(CSV_HEADER)
                for (record in records) {
                    writer.appendLine(recordToCsvLine(record))
                }
            }

            Toast.makeText(context, R.string.csv_exported, Toast.LENGTH_SHORT).show()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportAndShare(context: Context, records: List<DataRecord>) {
        val file = exportToCsv(context, records) ?: return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData is required for the chooser to grant URI read permission
            // to every resolved app (including KakaoTalk, Telegram, etc.)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // NOTE: do NOT set EXTRA_SUBJECT — cloud apps (Drive, OneDrive) use it
            // as the save filename, overriding the actual file name.
        }

        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    private fun recordToCsvLine(record: DataRecord): String {
        return listOf(
            record.device_id,
            record.screen_on_timestamp_unix,
            record.screen_on_timestamp_dt,
            record.screen_off_timestamp_unix,
            record.screen_off_timestamp_dt,
            record.screen_duration,
            record.unique_foreground_app_count,
            record.foreground_app_count,
            record.foreground_app_switch_count,
            record.foreground_app_switch_per_hour,
            record.foreground_app_duration_sum,
            record.unique_foreground_app_duration_max,
            record.concentration_ratio,
            record.foreground_apps_and_durations,
            record.unique_foreground_apps_and_durations,
            record.genres_and_durations
        ).joinToString(",")
    }
}
