package com.example.informationfatigue.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.informationfatigue.R
import com.example.informationfatigue.service.DataCollectionService
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
        btnToggleService = findViewById(R.id.btnToggleService)
        btnExport = findViewById(R.id.btnExport)
        recyclerView = findViewById(R.id.recyclerView)

        // RecyclerView setup
        logAdapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = logAdapter

        // ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.allRecords.observe(this) { records ->
            logAdapter.submitList(records)
            // Scroll to top when new data arrives
            if (records.isNotEmpty()) {
                recyclerView.scrollToPosition(0)
            }
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
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
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
}
