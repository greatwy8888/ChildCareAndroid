package com.example.childcare

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.setPadding
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32)
        }
        layout.addView(TextView(this).apply {
            text = "儿童护眼与游戏时间管理"
            textSize = 24f
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(TextView(this).apply {
            text = "当前为普通 APK 原型。可查看系统统计的今日应用前台时长，并启动 5 分钟休息倒计时。普通 APK 无法可靠拦截 Home 键或替代系统锁屏。"
            textSize = 16f
            setPadding(0, 24, 0, 24)
        })
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 12, 0, 12)
        }
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "打开使用情况访问设置"
            setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        })
        layout.addView(Button(this).apply {
            text = "检查权限并刷新"
            setOnClickListener { refreshStatus() }
        })
        layout.addView(Button(this).apply {
            text = "开始休息 5 分钟倒计时"
            setOnClickListener { showBreakTimer() }
        })
        setContentView(layout)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun refreshStatus() {
        if (!hasUsageAccess()) {
            status.text = "使用情况访问：未授权。点击上方按钮，在系统设置中找到本应用并允许访问。"
            return
        }
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val totalMs = manager.queryAndAggregateUsageStats(start, now)
            .values.sumOf { it.totalTimeInForeground.coerceAtLeast(0L) }
        val minutes = totalMs / 60_000
        status.text = "使用情况访问：已授权\n今日应用前台时长合计（系统统计估算）：${minutes / 60} 小时 ${minutes % 60} 分钟\n注意：此统计不等同于纯游戏时长，当前尚未自动限制额度。"
    }

    private fun showBreakTimer() {
        val message = TextView(this).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(24)
            text = "05:00\n请放下手机，远眺并休息。"
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("护眼休息")
            .setView(message)
            .setCancelable(false)
            .setPositiveButton("休息完成", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }
        dialog.show()
        val finishButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        val timer = object : CountDownTimer(5 * 60 * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                message.text = String.format(
                    Locale.getDefault(),
                    "%02d:%02d\n请放下手机，远眺并休息。",
                    seconds / 60,
                    seconds % 60
                )
            }

            override fun onFinish() {
                message.text = "休息完成！\n请继续保持适当距离和良好坐姿。"
                finishButton.isEnabled = true
            }
        }
        dialog.setOnDismissListener { timer.cancel() }
        timer.start()
    }
}
