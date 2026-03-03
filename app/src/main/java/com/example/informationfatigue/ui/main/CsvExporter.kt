package com.example.informationfatigue.ui.main

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.informationfatigue.R
import com.example.informationfatigue.data.DataRecord
import java.io.File
import java.io.FileWriter

/**
 * Exports DataRecord list to CSV and shares via FileProvider.
 */
object CsvExporter {

    private const val CSV_FILE_NAME = "information_fatigue_data.csv"
    private const val EXPORTS_DIR = "exports"

    private val CSV_HEADER = listOf(
        "id",
        "start_time",
        "end_time",
        "screen_on_count",
        "screen_off_count",
        "screen_on_duration_ms",
        "max_consecutive_ms",
        "avg_app_session_ms",
        "app_switch_count",
        "distraction_index",
        "unique_apps_count",
        "notification_interaction_count",
        "cumulative_screen_time_ms"
    ).joinToString(",")

    /**
     * Export records to CSV file and return the file, or null on error.
     */
    fun exportToCsv(context: Context, records: List<DataRecord>): File? {
        if (records.isEmpty()) {
            Toast.makeText(context, R.string.csv_no_data, Toast.LENGTH_SHORT).show()
            return null
        }

        return try {
            val exportDir = File(context.filesDir, EXPORTS_DIR)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val file = File(exportDir, CSV_FILE_NAME)
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

    /**
     * Export and share the CSV file via an intent.
     */
    fun exportAndShare(context: Context, records: List<DataRecord>) {
        val file = exportToCsv(context, records) ?: return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.csv_share_title))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.csv_share_title))
        context.startActivity(chooser)
    }

    private fun recordToCsvLine(record: DataRecord): String {
        return listOf(
            record.id,
            record.start_time,
            record.end_time,
            record.screen_on_count,
            record.screen_off_count,
            record.screen_on_duration_ms,
            record.max_consecutive_ms,
            record.avg_app_session_ms,
            record.app_switch_count,
            record.distraction_index,
            record.unique_apps_count,
            record.notification_interaction_count,
            record.cumulative_screen_time_ms
        ).joinToString(",")
    }
}
