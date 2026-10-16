package com.example.childcare

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.setPadding

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
            text = "原型版本：可检查使用情况访问权限。普通 APK 无法可靠拦截 Home 键或替代系统锁屏。"
            textSize = 16f
            setPadding(0, 24, 0, 24)
        })
        status = TextView(this).apply { textSize = 16f }
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
            text = "休息 5 分钟演示"
            setOnClickListener { showBreakDemo() }
        })
        setContentView(layout)
        refreshStatus()
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun refreshStatus() {
        status.text = if (hasUsageAccess()) "使用情况访问：已授权（原型尚未自动执行游戏额度）" else "使用情况访问：未授权，请点击上方按钮在系统设置中授权。"
    }

    private fun showBreakDemo() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("休息时间")
            .setMessage("请放下手机，远眺并休息 5 分钟。此原型只演示提醒，尚未强制倒计时。")
            .setPositiveButton("知道了", null)
            .create()
        dialog.show()
    }
}
