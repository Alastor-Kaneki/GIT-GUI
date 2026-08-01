package com.alastorkaneki.gitgui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class StartupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        DownloadLog.append(this, "StartupActivity.onCreate entered")
        super.onCreate(savedInstanceState)
        DownloadLog.append(this, "StartupActivity super.onCreate completed")
        CrashStore.install(applicationContext)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val report = CrashStore.read(this)
        if (report == null) {
            DownloadLog.append(this, "No stored Java crash report; launching MainActivity")
            launchMain()
        } else {
            DownloadLog.append(this, "Stored Java crash report found; showing recovery screen")
            showRecovery(report)
        }
    }

    private fun launchMain() {
        DownloadLog.append(this, "Calling startActivity(MainActivity)")
        startActivity(Intent(this, MainActivity::class.java))
        DownloadLog.append(this, "startActivity(MainActivity) returned")
        finish()
        DownloadLog.append(this, "StartupActivity.finish called")
    }

    private fun retry(disableAnimations: Boolean) {
        DownloadLog.append(this, "Retry requested; disableAnimations=$disableAnimations")
        if (disableAnimations) {
            getSharedPreferences("git_gui_preferences", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("rainbow_enabled", false)
                .commit()
        }
        CrashStore.clear(this)
        launchMain()
    }

    private fun showRecovery(report: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "GIT GUI recovered a startup crash"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "A persistent diagnostic log is also being written to Downloads/${DownloadLog.FILE_NAME}."
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(14))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val reportView = TextView(this).apply {
            text = report
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(220, 255, 220))
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(8, 8, 8))
        }
        root.addView(ScrollView(this).apply {
            addView(reportView)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            bottomMargin = dp(12)
        })

        root.addView(Button(this).apply {
            text = "Copy crash report"
            setOnClickListener {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("GIT GUI crash", report))
                Toast.makeText(this@StartupActivity, "Crash report copied", Toast.LENGTH_SHORT).show()
            }
        }, buttonLayout())

        root.addView(Button(this).apply {
            text = "Disable animations and retry"
            setOnClickListener { retry(true) }
        }, buttonLayout())

        root.addView(Button(this).apply {
            text = "Retry normally"
            setOnClickListener { retry(false) }
        }, buttonLayout())

        setContentView(root)
        DownloadLog.append(this, "Recovery screen content view installed")
    }

    private fun buttonLayout() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(8)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
