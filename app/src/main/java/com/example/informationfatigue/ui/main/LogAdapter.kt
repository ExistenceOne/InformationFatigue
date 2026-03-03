package com.example.informationfatigue.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.informationfatigue.R
import com.example.informationfatigue.data.DataRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : ListAdapter<DataRecord, LogAdapter.LogViewHolder>(DiffCallback) {

    private val timeFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

    object DiffCallback : DiffUtil.ItemCallback<DataRecord>() {
        override fun areItemsTheSame(oldItem: DataRecord, newItem: DataRecord): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DataRecord, newItem: DataRecord): Boolean =
            oldItem == newItem
    }

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimeRange: TextView = view.findViewById(R.id.tvTimeRange)
        val tvScreenOnOff: TextView = view.findViewById(R.id.tvScreenOnOff)
        val tvScreenDuration: TextView = view.findViewById(R.id.tvScreenDuration)
        val tvMaxConsecutive: TextView = view.findViewById(R.id.tvMaxConsecutive)
        val tvAvgApp: TextView = view.findViewById(R.id.tvAvgApp)
        val tvAppSwitch: TextView = view.findViewById(R.id.tvAppSwitch)
        val tvDistraction: TextView = view.findViewById(R.id.tvDistraction)
        val tvUniqueApps: TextView = view.findViewById(R.id.tvUniqueApps)
        val tvNotifications: TextView = view.findViewById(R.id.tvNotifications)
        val tvCumulative: TextView = view.findViewById(R.id.tvCumulative)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val record = getItem(position)
        val ctx = holder.itemView.context

        // Time range
        val startStr = timeFormat.format(Date(record.start_time))
        val endStr = timeFormat.format(Date(record.end_time))
        holder.tvTimeRange.text = "$startStr ~ $endStr"

        // Screen ON/OFF
        holder.tvScreenOnOff.text = ctx.getString(
            R.string.screen_on_off_format, record.screen_on_count, record.screen_off_count
        )

        // Screen duration
        holder.tvScreenDuration.text = ctx.getString(
            R.string.screen_duration_format, formatDuration(record.screen_on_duration_ms)
        )

        // Max consecutive
        holder.tvMaxConsecutive.text = ctx.getString(
            R.string.max_consecutive_format, formatDuration(record.max_consecutive_ms)
        )

        // Avg app session
        holder.tvAvgApp.text = ctx.getString(
            R.string.avg_app_format, formatDuration(record.avg_app_session_ms)
        )

        // App switch
        holder.tvAppSwitch.text = ctx.getString(
            R.string.app_switch_format, record.app_switch_count
        )

        // Distraction index
        holder.tvDistraction.text = ctx.getString(
            R.string.distraction_format, record.distraction_index
        )

        // Unique apps
        holder.tvUniqueApps.text = ctx.getString(
            R.string.unique_apps_format, record.unique_apps_count
        )

        // Notifications
        holder.tvNotifications.text = ctx.getString(
            R.string.notification_format, record.notification_interaction_count
        )

        // Cumulative
        holder.tvCumulative.text = ctx.getString(
            R.string.cumulative_format, formatDuration(record.cumulative_screen_time_ms)
        )
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%dh %dm %ds", hours, minutes, seconds)
        } else if (minutes > 0) {
            String.format("%dm %ds", minutes, seconds)
        } else {
            String.format("%ds", seconds)
        }
    }
}
