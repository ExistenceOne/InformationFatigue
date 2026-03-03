package com.example.informationfatigue.ui.onboarding

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.informationfatigue.R
import com.example.informationfatigue.ui.main.MainActivity
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var indicatorLayout: LinearLayout
    private lateinit var adapter: OnboardingPagerAdapter

    private val steps = listOf(
        OnboardingStep.USAGE_ACCESS,
        OnboardingStep.NOTIFICATION_ACCESS,
        OnboardingStep.BATTERY_OPTIMIZATION,
        OnboardingStep.EXACT_ALARM
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If all required permissions are granted, go directly to main
        if (areRequiredPermissionsGranted()) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)
        indicatorLayout = findViewById(R.id.indicatorLayout)

        adapter = OnboardingPagerAdapter(steps)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false // Disable swipe, use buttons

        setupIndicators()
        updateUI(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUI(position)
            }
        })

        btnNext.setOnClickListener {
            val current = viewPager.currentItem
            if (current < steps.size - 1) {
                viewPager.currentItem = current + 1
            } else {
                // Last step — complete onboarding
                completeOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            val current = viewPager.currentItem
            if (current < steps.size - 1) {
                viewPager.currentItem = current + 1
            } else {
                completeOnboarding()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission status for the current page
        adapter.notifyDataSetChanged()
        updateUI(viewPager.currentItem)
    }

    private fun updateUI(position: Int) {
        val step = steps[position]
        val isGranted = isPermissionGranted(step)
        val isRequired = step.isRequired
        val isLast = position == steps.size - 1

        // Update button text
        btnNext.text = if (isLast) getString(R.string.done) else getString(R.string.next)

        // Show skip for optional steps that aren't granted
        btnSkip.visibility = if (!isRequired && !isGranted) View.VISIBLE else View.GONE

        // For required steps, disable "Next" until permission is granted
        if (isRequired) {
            btnNext.isEnabled = isGranted
        } else {
            btnNext.isEnabled = true
        }

        // If it's the last step and "Done" — enable only if required steps are met
        if (isLast) {
            btnNext.isEnabled = areRequiredPermissionsGranted()
            btnSkip.visibility = if (!areRequiredPermissionsGranted()) View.GONE else
                if (!isGranted) View.VISIBLE else View.GONE
        }

        updateIndicators(position)
    }

    private fun setupIndicators() {
        indicatorLayout.removeAllViews()
        for (i in steps.indices) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                    setMargins(8, 0, 8, 0)
                }
                setBackgroundResource(R.drawable.ic_pending)
            }
            indicatorLayout.addView(dot)
        }
    }

    private fun updateIndicators(currentPosition: Int) {
        for (i in 0 until indicatorLayout.childCount) {
            val dot = indicatorLayout.getChildAt(i)
            val alpha = if (i == currentPosition) 1.0f else 0.4f
            dot.alpha = alpha
        }
    }

    private fun completeOnboarding() {
        // Mark onboarding as completed
        getSharedPreferences("onboarding_prefs", MODE_PRIVATE)
            .edit().putBoolean("completed", true).apply()
        // Service is NOT started here — user must press Start in MainActivity
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun areRequiredPermissionsGranted(): Boolean {
        return isPermissionGranted(OnboardingStep.USAGE_ACCESS) &&
                isPermissionGranted(OnboardingStep.NOTIFICATION_ACCESS)
    }

    private fun isPermissionGranted(step: OnboardingStep): Boolean {
        return when (step) {
            OnboardingStep.USAGE_ACCESS -> isUsageAccessGranted()
            OnboardingStep.NOTIFICATION_ACCESS -> isNotificationAccessGranted()
            OnboardingStep.BATTERY_OPTIMIZATION -> isBatteryOptimizationIgnored()
            OnboardingStep.EXACT_ALARM -> isExactAlarmAllowed()
        }
    }

    private fun isUsageAccessGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationAccessGranted(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isExactAlarmAllowed(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Not needed below API 31
        }
    }

    // ────── Pager Adapter ──────

    inner class OnboardingPagerAdapter(
        private val steps: List<OnboardingStep>
    ) : RecyclerView.Adapter<OnboardingPagerAdapter.StepViewHolder>() {

        inner class StepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvStepNumber: TextView = view.findViewById(R.id.tvStepNumber)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val tvDescription: TextView = view.findViewById(R.id.tvDescription)
            val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
            val ivStatus: ImageView = view.findViewById(R.id.ivStatus)
            val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.page_onboarding, parent, false)
            return StepViewHolder(view)
        }

        override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
            val step = steps[position]
            val granted = isPermissionGranted(step)

            holder.tvStepNumber.text = getString(R.string.step_format, position + 1, steps.size)
            holder.tvTitle.text = step.getTitle(this@OnboardingActivity)
            holder.tvDescription.text = step.getDescription(this@OnboardingActivity)

            // Icon
            holder.ivIcon.setImageResource(R.drawable.ic_launcher_foreground)

            // Status icon
            holder.ivStatus.setImageResource(
                if (granted) R.drawable.ic_check_circle else R.drawable.ic_pending
            )

            // For exact alarm on API < 31, show "not needed" text
            if (step == OnboardingStep.EXACT_ALARM && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                holder.tvDescription.text = getString(R.string.onboarding_step4_not_needed)
                holder.btnAction.visibility = View.GONE
                holder.ivStatus.setImageResource(R.drawable.ic_check_circle)
                return
            }

            // Action button
            holder.btnAction.visibility = if (granted) View.GONE else View.VISIBLE
            holder.btnAction.setOnClickListener {
                openSettingsForStep(step)
            }
        }

        override fun getItemCount(): Int = steps.size
    }

    private fun openSettingsForStep(step: OnboardingStep) {
        val intent = when (step) {
            OnboardingStep.USAGE_ACCESS -> {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            }
            OnboardingStep.NOTIFICATION_ACCESS -> {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            OnboardingStep.BATTERY_OPTIMIZATION -> {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }
            OnboardingStep.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                } else {
                    return
                }
            }
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

enum class OnboardingStep(val isRequired: Boolean) {
    USAGE_ACCESS(isRequired = true),
    NOTIFICATION_ACCESS(isRequired = true),
    BATTERY_OPTIMIZATION(isRequired = false),
    EXACT_ALARM(isRequired = false);

    fun getTitle(context: Context): String {
        return when (this) {
            USAGE_ACCESS -> context.getString(R.string.onboarding_step1_title)
            NOTIFICATION_ACCESS -> context.getString(R.string.onboarding_step2_title)
            BATTERY_OPTIMIZATION -> context.getString(R.string.onboarding_step3_title)
            EXACT_ALARM -> context.getString(R.string.onboarding_step4_title)
        }
    }

    fun getDescription(context: Context): String {
        return when (this) {
            USAGE_ACCESS -> context.getString(R.string.onboarding_step1_desc)
            NOTIFICATION_ACCESS -> context.getString(R.string.onboarding_step2_desc)
            BATTERY_OPTIMIZATION -> context.getString(R.string.onboarding_step3_desc)
            EXACT_ALARM -> context.getString(R.string.onboarding_step4_desc)
        }
    }
}
