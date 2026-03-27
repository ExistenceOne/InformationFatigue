package com.example.informationfatigue.ui.weekly

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.informationfatigue.R

class WeeklySummaryActivity : AppCompatActivity() {

    private lateinit var viewModel: WeeklySummaryViewModel

    private lateinit var tvThisWeekSessionCount: TextView
    private lateinit var tvThisWeekAppUsage: TextView
    private lateinit var tvThisWeekSwitchPerHour: TextView
    private lateinit var tvThisWeekUniqueApps: TextView
    private lateinit var tvThisWeekConcentration: TextView
    private lateinit var tvThisWeekThresholdMessage: TextView
    private lateinit var layoutThisWeekStats: LinearLayout
    private lateinit var layoutThisWeekTopApps: LinearLayout

    private lateinit var tvLastWeekSessionCount: TextView
    private lateinit var tvLastWeekAppUsage: TextView
    private lateinit var tvLastWeekSwitchPerHour: TextView
    private lateinit var tvLastWeekUniqueApps: TextView
    private lateinit var tvLastWeekConcentration: TextView
    private lateinit var tvLastWeekThresholdMessage: TextView
    private lateinit var layoutLastWeekStats: LinearLayout
    private lateinit var layoutLastWeekTopApps: LinearLayout

    private val defaultAppIcon: Drawable? by lazy {
        ContextCompat.getDrawable(this, android.R.drawable.sym_def_app_icon)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly_summary)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.weekly_summary_screen_title)
        }

        bindViews()
        applyInsets()

        viewModel = ViewModelProvider(this)[WeeklySummaryViewModel::class.java]
        viewModel.weeklyComparison.observe(this) { state ->
            bindWeeklyCard(
                summary = state.thisWeek,
                tvSessionCount = tvThisWeekSessionCount,
                tvAppUsage = tvThisWeekAppUsage,
                tvSwitchPerHour = tvThisWeekSwitchPerHour,
                tvUniqueApps = tvThisWeekUniqueApps,
                tvConcentration = tvThisWeekConcentration,
                tvThreshold = tvThisWeekThresholdMessage,
                statsLayout = layoutThisWeekStats,
                topAppContainer = layoutThisWeekTopApps
            )

            bindWeeklyCard(
                summary = state.lastWeek,
                tvSessionCount = tvLastWeekSessionCount,
                tvAppUsage = tvLastWeekAppUsage,
                tvSwitchPerHour = tvLastWeekSwitchPerHour,
                tvUniqueApps = tvLastWeekUniqueApps,
                tvConcentration = tvLastWeekConcentration,
                tvThreshold = tvLastWeekThresholdMessage,
                statsLayout = layoutLastWeekStats,
                topAppContainer = layoutLastWeekTopApps
            )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun bindViews() {
        tvThisWeekSessionCount = findViewById(R.id.tvThisWeekSessionCount)
        tvThisWeekAppUsage = findViewById(R.id.tvThisWeekAppUsage)
        tvThisWeekSwitchPerHour = findViewById(R.id.tvThisWeekSwitchPerHour)
        tvThisWeekUniqueApps = findViewById(R.id.tvThisWeekUniqueApps)
        tvThisWeekConcentration = findViewById(R.id.tvThisWeekConcentration)
        tvThisWeekThresholdMessage = findViewById(R.id.tvThisWeekThresholdMessage)
        layoutThisWeekStats = findViewById(R.id.layoutThisWeekStats)
        layoutThisWeekTopApps = findViewById(R.id.layoutThisWeekTopApps)

        tvLastWeekSessionCount = findViewById(R.id.tvLastWeekSessionCount)
        tvLastWeekAppUsage = findViewById(R.id.tvLastWeekAppUsage)
        tvLastWeekSwitchPerHour = findViewById(R.id.tvLastWeekSwitchPerHour)
        tvLastWeekUniqueApps = findViewById(R.id.tvLastWeekUniqueApps)
        tvLastWeekConcentration = findViewById(R.id.tvLastWeekConcentration)
        tvLastWeekThresholdMessage = findViewById(R.id.tvLastWeekThresholdMessage)
        layoutLastWeekStats = findViewById(R.id.layoutLastWeekStats)
        layoutLastWeekTopApps = findViewById(R.id.layoutLastWeekTopApps)
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.weeklyRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }
    }

    private fun bindWeeklyCard(
        summary: WeeklyMetricSummary,
        tvSessionCount: TextView,
        tvAppUsage: TextView,
        tvSwitchPerHour: TextView,
        tvUniqueApps: TextView,
        tvConcentration: TextView,
        tvThreshold: TextView,
        statsLayout: LinearLayout,
        topAppContainer: LinearLayout
    ) {
        tvSessionCount.text = getString(R.string.weekly_session_count_format, summary.sessionCount)

        if (summary.isReady) {
            statsLayout.visibility = View.VISIBLE
            tvThreshold.visibility = View.GONE

            tvAppUsage.text = formatDurationHoursMinutes(summary.avgAppUsageSec)
            tvSwitchPerHour.text = getString(R.string.weekly_switch_per_hour_format, summary.avgSwitchPerHour)
            tvUniqueApps.text = getString(R.string.weekly_unique_apps_format, summary.avgUniqueAppCount)
            tvConcentration.text = getString(
                R.string.weekly_concentration_format,
                summary.avgConcentrationRatio * 100f
            )
        } else {
            statsLayout.visibility = View.GONE
            tvThreshold.visibility = View.VISIBLE
            tvThreshold.text = getString(
                R.string.weekly_threshold_message,
                summary.minSessionRequired,
                summary.sessionCount
            )
        }

        bindTopApps(topAppContainer, summary.topApps)
    }

    private fun bindTopApps(container: LinearLayout, apps: List<TopAppStat>) {
        container.removeAllViews()

        if (apps.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.weekly_top_app_empty)
                setTextColor(ContextCompat.getColor(this@WeeklySummaryActivity, android.R.color.darker_gray))
                textSize = 13f
            }
            container.addView(emptyView)
            return
        }

        val inflater = LayoutInflater.from(this)
        apps.forEachIndexed { index, app ->
            val row = inflater.inflate(R.layout.item_top_app, container, false)
            val ivIcon = row.findViewById<ImageView>(R.id.ivTopAppIcon)
            val tvLabel = row.findViewById<TextView>(R.id.tvTopAppLabel)
            val tvCount = row.findViewById<TextView>(R.id.tvTopAppCount)

            val resolved = resolveAppInfo(app.packageName)
            ivIcon.setImageDrawable(resolved.icon ?: defaultAppIcon)
            tvLabel.text = getString(R.string.weekly_top_app_label_format, index + 1, resolved.label)
            tvCount.text = getString(R.string.weekly_top_app_count_format, app.count)

            container.addView(row)
        }
    }

    private fun formatDurationHoursMinutes(seconds: Float): String {
        val rounded = seconds.toLong()
        val hours = rounded / 3600L
        val minutes = (rounded % 3600L) / 60L
        return getString(R.string.hour_minute_format, hours, minutes)
    }

    private fun resolveAppInfo(packageName: String): AppInfo {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(info).toString()
            val icon = packageManager.getApplicationIcon(info)
            AppInfo(label = label, icon = icon)
        } catch (_: PackageManager.NameNotFoundException) {
            AppInfo(label = packageName, icon = defaultAppIcon)
        }
    }

    private data class AppInfo(
        val label: String,
        val icon: Drawable?
    )
}
