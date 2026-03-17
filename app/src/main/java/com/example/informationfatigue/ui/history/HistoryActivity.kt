package com.example.informationfatigue.ui.history

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.informationfatigue.R
import com.example.informationfatigue.ui.main.LogAdapter
import com.example.informationfatigue.ui.main.MainViewModel

class HistoryActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var logAdapter: LogAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Action bar back navigation
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.history)
        }

        recyclerView = findViewById(R.id.recyclerViewHistory)
        logAdapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = logAdapter

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val p8 = (8 * resources.displayMetrics.density).toInt()
            v.setPadding(p8, p8, p8, p8 + bars.bottom)
            insets
        }

        // Reuse same ViewModel — shows ALL records (no filtering)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.allRecords.observe(this) { records ->
            logAdapter.submitList(records)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
