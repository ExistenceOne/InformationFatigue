package com.example.informationfatigue.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.informationfatigue.R
import com.example.informationfatigue.ui.history.HistoryActivity
import com.example.informationfatigue.ui.weekly.WeeklySummaryActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var logAdapter: LogAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnStartCollection: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvRecentCount: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var btnExport: MaterialButton
    private lateinit var btnHistory: MaterialButton
    private lateinit var btnWeeklySummary: MaterialButton
    private lateinit var btnClearLogs: MaterialButton

    // Today summary views
    private lateinit var tvTodayScreenTime: TextView
    private lateinit var tvTodaySessionCount: TextView
    private lateinit var tvTodayAvgSwitchPerHour: TextView
    private lateinit var tvTodayAppSwitches: TextView
    private lateinit var tvTodayThresholdMessage: TextView
    private lateinit var layoutTodayStats: LinearLayout

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: notification permission result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()

        btnStartCollection = findViewById(R.id.btnStartCollection)
        progressBar = findViewById(R.id.progressCollection)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        tvRecentCount = findViewById(R.id.tvRecentCount)
        tvTotalCount = findViewById(R.id.tvTotalCount)
        btnExport = findViewById(R.id.btnExport)
        btnHistory = findViewById(R.id.btnHistory)
        btnWeeklySummary = findViewById(R.id.btnWeeklySummary)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        recyclerView = findViewById(R.id.recyclerView)

        tvTodayScreenTime = findViewById(R.id.tvTodayScreenTime)
        tvTodaySessionCount = findViewById(R.id.tvTodayNotifications)
        tvTodayAvgSwitchPerHour = findViewById(R.id.tvTodayNotifFreq)
        tvTodayAppSwitches = findViewById(R.id.tvTodayAppSwitches)
        tvTodayThresholdMessage = findViewById(R.id.tvTodayThresholdMessage)
        layoutTodayStats = findViewById(R.id.layoutTodayStats)

        // Edge-to-edge insets: top = status bar, bottom = nav bar
        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        logAdapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = logAdapter

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.recentCollectedRecords.observe(this) { records ->
            logAdapter.submitList(records)
            tvRecentCount.text = getString(R.string.recent_count_format, records.size)
            if (records.isNotEmpty()) recyclerView.scrollToPosition(0)
        }

        viewModel.totalRecordCount.observe(this) { count ->
            tvTotalCount.text = getString(R.string.total_count_format, count)
        }

        viewModel.todaySummary.observe(this) { summary ->
            if (summary.isReady) {
                layoutTodayStats.visibility = View.VISIBLE
                tvTodayThresholdMessage.visibility = View.GONE

                tvTodayScreenTime.text = formatHourMinute(summary.avgAppUsageSec)
                tvTodaySessionCount.text = "%.1f개".format(summary.avgUniqueAppCount)
                tvTodayAvgSwitchPerHour.text = "%.1f회/시간".format(summary.avgSwitchPerHour)
                tvTodayAppSwitches.text = "%.1f%%".format(summary.avgConcentrationRatio * 100f)
            } else {
                layoutTodayStats.visibility = View.GONE
                tvTodayThresholdMessage.visibility = View.VISIBLE
                tvTodayThresholdMessage.text = getString(
                    R.string.today_threshold_message,
                    summary.minSessionRequired,
                    summary.sessionCount
                )
            }
        }

        viewModel.collectionProgress.observe(this) { progress ->
            progressBar.progress = progress
            tvProgressPercent.text = getString(R.string.collection_progress_format, progress)
        }

        viewModel.isCollecting.observe(this) { isCollecting ->
            btnStartCollection.isEnabled = !isCollecting
            btnStartCollection.text = if (isCollecting) {
                getString(R.string.collecting)
            } else {
                getString(R.string.start_collection)
            }
        }

        btnStartCollection.setOnClickListener {
            viewModel.collectUsageEventsSinceLastRecord()
            Toast.makeText(this, R.string.collection_started, Toast.LENGTH_SHORT).show()
        }

        btnExport.setOnClickListener {
            viewModel.getAllRecordsSync { records ->
                runOnUiThread { CsvExporter.exportAndShare(this, records) }
            }
        }

        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnWeeklySummary.setOnClickListener {
            startActivity(Intent(this, WeeklySummaryActivity::class.java))
        }

        btnClearLogs.setOnClickListener {
            val count = viewModel.totalRecordCount.value ?: 0
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_logs_title)
                .setMessage(getString(R.string.clear_logs_message, count))
                .setPositiveButton(R.string.clear_logs_confirm) { _, _ ->
                    viewModel.deleteAll()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun formatHourMinute(seconds: Float): String {
        val rounded = seconds.toLong()
        val hours = rounded / 3600L
        val minutes = (rounded % 3600L) / 60L
        return getString(R.string.hour_minute_format, hours, minutes)
    }
}
