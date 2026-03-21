package com.example.informationfatigue.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    private lateinit var btnClearLogs: MaterialButton

    // Today summary views
    private lateinit var tvTodayScreenTime: TextView
    private lateinit var tvTodaySessionCount: TextView
    private lateinit var tvTodayAvgSwitchPerHour: TextView
    private lateinit var tvTodayAppSwitches: TextView

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
        btnClearLogs = findViewById(R.id.btnClearLogs)
        recyclerView = findViewById(R.id.recyclerView)

        tvTodayScreenTime = findViewById(R.id.tvTodayScreenTime)
        tvTodaySessionCount = findViewById(R.id.tvTodayNotifications)
        tvTodayAvgSwitchPerHour = findViewById(R.id.tvTodayNotifFreq)
        tvTodayAppSwitches = findViewById(R.id.tvTodayAppSwitches)

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
            tvTodayScreenTime.text = formatDuration(summary.totalScreenTimeSec)
            tvTodaySessionCount.text = "${summary.sessionCount}세션"
            tvTodayAvgSwitchPerHour.text = "%.1f회/시간".format(summary.avgSwitchPerHour)
            tvTodayAppSwitches.text = "${summary.totalAppSwitches}회"
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

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%dh %dm %ds", hours, minutes, secs)
        } else if (minutes > 0) {
            String.format("%dm %ds", minutes, secs)
        } else {
            String.format("%ds", secs)
        }
    }
}
