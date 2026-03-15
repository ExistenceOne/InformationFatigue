package com.example.informationfatigue.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.informationfatigue.R
import com.example.informationfatigue.service.DataCollectionService
import com.example.informationfatigue.ui.history.HistoryActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var logAdapter: LogAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView
    private lateinit var btnToggleService: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var btnHistory: MaterialButton

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

        statusCard = findViewById(R.id.statusCard)
        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        btnToggleService = findViewById(R.id.btnToggleService)
        btnExport = findViewById(R.id.btnExport)
        btnHistory = findViewById(R.id.btnHistory)
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
        viewModel.allRecords.observe(this) { records ->
            logAdapter.submitList(records)
            if (records.isNotEmpty()) recyclerView.scrollToPosition(0)
        }

        viewModel.todaySummary.observe(this) { summary ->
            tvTodayScreenTime.text = formatDuration(summary.totalScreenTimeSec)
            tvTodaySessionCount.text = "${summary.sessionCount}세션"
            tvTodayAvgSwitchPerHour.text = "%.1f회/시간".format(summary.avgSwitchPerHour)
            tvTodayAppSwitches.text = "${summary.totalAppSwitches}회"
        }

        btnToggleService.setOnClickListener {
            if (DataCollectionService.isRunning(this)) {
                DataCollectionService.stop(this)
                applyServiceUI(running = false)
                Toast.makeText(this, R.string.service_stopped, Toast.LENGTH_SHORT).show()
            } else {
                DataCollectionService.start(this)
                applyServiceUI(running = true)
                Toast.makeText(this, R.string.service_running, Toast.LENGTH_SHORT).show()
            }
        }

        btnExport.setOnClickListener {
            viewModel.getAllRecordsSync { records ->
                runOnUiThread { CsvExporter.exportAndShare(this, records) }
            }
        }

        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun applyServiceUI(running: Boolean) {
        if (running) {
            tvStatus.text = getString(R.string.service_running)
            statusDot.setBackgroundResource(R.drawable.circle_green)
            btnToggleService.text = getString(R.string.stop)
        } else {
            tvStatus.text = getString(R.string.service_stopped)
            statusDot.setBackgroundResource(R.drawable.circle_red)
            btnToggleService.text = getString(R.string.start)
        }
    }

    private fun updateServiceStatus() {
        applyServiceUI(DataCollectionService.isRunning(this))
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
