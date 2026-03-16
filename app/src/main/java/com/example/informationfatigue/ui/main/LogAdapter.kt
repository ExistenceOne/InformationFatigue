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

class LogAdapter : ListAdapter<DataRecord, LogAdapter.LogViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<DataRecord>() {
        override fun areItemsTheSame(oldItem: DataRecord, newItem: DataRecord): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DataRecord, newItem: DataRecord): Boolean =
            oldItem == newItem
    }

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTimeRange: TextView = view.findViewById(R.id.tvTimeRange)
        val tvSessionDuration: TextView = view.findViewById(R.id.tvSessionDuration)
        val tvAppSwitch: TextView = view.findViewById(R.id.tvAppSwitch)
        val tvUniqueApps: TextView = view.findViewById(R.id.tvUniqueApps)
        val tvAppSwitchPerHour: TextView = view.findViewById(R.id.tvAppSwitchPerHour)
        val tvAppDurationMean: TextView = view.findViewById(R.id.tvAppDurationMean)
        val tvConcentrationRatio: TextView = view.findViewById(R.id.tvConcentrationRatio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val record = getItem(position)
        val ctx = holder.itemView.context

        holder.tvTimeRange.text = "${record.screen_on_timestamp_dt} ~ ${record.screen_off_timestamp_dt}"
        holder.tvSessionDuration.text = ctx.getString(R.string.session_duration_format, record.screen_duration)
        holder.tvAppSwitch.text = ctx.getString(R.string.app_switch_format, record.foreground_app_switch_count)
        holder.tvUniqueApps.text = ctx.getString(R.string.unique_apps_format, record.unique_foreground_app_count)
        holder.tvAppSwitchPerHour.text = ctx.getString(R.string.app_switch_per_hour_format, record.foreground_app_switch_per_hour)
        holder.tvAppDurationMean.text = ctx.getString(R.string.app_duration_mean_format, record.foreground_app_duration_mean)
        holder.tvConcentrationRatio.text = ctx.getString(R.string.app_duration_std_format, record.concentration_ratio)
    }
}
