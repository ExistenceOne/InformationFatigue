package com.example.informationfatigue.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var tvCountdown: TextView
    private lateinit var btnToggleService: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var btnHistory: MaterialButton

    // Today summary views
    private lateinit var tvTodayScreenTime: TextView
    private lateinit var tvTodayNotifications: TextView
    private lateinit var tvTodayNotifFreq: TextView
    private lateinit var tvTodayAppSwitches: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000L)
        }
    }

    // POST_NOTIFICATIONS permission launcher (API 33+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "알림 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request POST_NOTIFICATIONS for API 33+
        requestNotificationPermissionIfNeeded()

        // Views
        statusCard = findViewById(R.id.statusCard)
        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        tvCountdown = findViewById(R.id.tvCountdown)
        btnToggleService = findViewById(R.id.btnToggleService)
        btnExport = findViewById(R.id.btnExport)
        btnHistory = findViewById(R.id.btnHistory)
        recyclerView = findViewById(R.id.recyclerView)

        // Today summary views
        tvTodayScreenTime = findViewById(R.id.tvTodayScreenTime)
        tvTodayNotifications = findViewById(R.id.tvTodayNotifications)
        tvTodayNotifFreq = findViewById(R.id.tvTodayNotifFreq)
        tvTodayAppSwitches = findViewById(R.id.tvTodayAppSwitches)

        // Handle bottom navigation bar (edge-to-edge enforced on API 35+)
        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom)
            insets
        }

        // RecyclerView setup
        logAdapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = logAdapter

        // ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.allRecords.observe(this) { records ->
            logAdapter.submitList(records)
            if (records.isNotEmpty()) {
                recyclerView.scrollToPosition(0)
            }
        }

        // Today summary
        viewModel.todaySummary.observe(this) { summary ->
            tvTodayScreenTime.text = formatDuration(summary.totalScreenTimeMs)
            tvTodayNotifications.text = "${summary.totalNotifications}건"
            tvTodayNotifFreq.text = "%.1f건/시간".format(summary.notifPerHour)
            tvTodayAppSwitches.text = "${summary.totalAppSwitches}회"
        }

        // Service toggle
        btnToggleService.setOnClickListener {
            if (DataCollectionService.isRunning(this)) {
                DataCollectionService.stop(this)
            } else {
                DataCollectionService.start(this)
            }
            updateServiceStatus()
        }

        // CSV export
        btnExport.setOnClickListener {
            viewModel.getAllRecordsSync { records ->
                runOnUiThread {
                    CsvExporter.exportAndShare(this, records)
                }
            }
        }

        // History
        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        handler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(countdownRunnable)
    }

    private fun updateCountdown() {
        val nextMs = DataCollectionService.getNextCollectionTimeMs(this)
        if (nextMs == 0L || !DataCollectionService.isRunning(this)) {
            tvCountdown.text = getString(R.string.countdown_stopped)
            return
        }
        val remainMs = nextMs - System.currentTimeMillis()
        if (remainMs <= 0) {
            tvCountdown.text = getString(R.string.countdown_format, 0, 0)
            return
        }
        val minutes = (remainMs / 60_000).toInt()
        val seconds = ((remainMs % 60_000) / 1000).toInt()
        tvCountdown.text = getString(R.string.countdown_format, minutes, seconds)
    }

    private fun updateServiceStatus() {
        val isRunning = DataCollectionService.isRunning(this)

        if (isRunning) {
            tvStatus.text = getString(R.string.service_running)
            statusDot.setBackgroundResource(R.drawable.circle_green)
            btnToggleService.text = getString(R.string.stop)
        } else {
            tvStatus.text = getString(R.string.service_stopped)
            statusDot.setBackgroundResource(R.drawable.circle_red)
            btnToggleService.text = getString(R.string.start)
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
