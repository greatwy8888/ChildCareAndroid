package com.example.childcare

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.setPadding
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
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
            text = "普通 APK 原型：查看今日应用前台时长、休息倒计时，并配置家长 TOTP 动态验证码。普通 APK 无法可靠拦截 Home 键或替代系统锁屏。"
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
            text = "设置家长动态密码"
            setOnClickListener { setupParentTotp() }
        })
        layout.addView(Button(this).apply {
            text = "验证家长动态密码"
            setOnClickListener { verifyParentTotp() }
        })
        layout.addView(Button(this).apply {
            text = "设置每日使用时长提醒阈值（需家长验证码）"
            setOnClickListener { verifyParentTotp(forSettings = true) }
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

    private fun securePrefs() = EncryptedSharedPreferences.create(
        this,
        "parent_security",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun setupParentTotp() {
        try {
            val prefs = securePrefs()
            if (prefs.contains(KEY_TOTP_SECRET)) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("家长动态密码已设置")
                    .setMessage("本机已保存加密的 TOTP 密钥。如需重新绑定，需要先通过家长验证流程；当前版本暂不支持重置。")
                    .setPositiveButton("知道了", null)
                    .show()
                return
            }
            val bytes = ByteArray(20)
            SecureRandom().nextBytes(bytes)
            val secret = base32Encode(bytes)
            prefs.edit().putString(KEY_TOTP_SECRET, secret).apply()
            android.app.AlertDialog.Builder(this)
                .setTitle("绑定家长验证器")
                .setMessage(
                    "请在家长自己的身份验证器 App 中，手动添加基于时间的一次性密码（TOTP），填写下方密钥：\n\n" +
                        secret + "\n\n每 30 秒生成一个 6 位验证码。请勿把密钥发给孩子或公开分享。密钥只在本次设置时显示，请先保存好。"
                )
                .setPositiveButton("已保存", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法安全保存密钥，请检查设备安全存储。", Toast.LENGTH_LONG).show()
        }
    }

    private fun verifyParentTotp(forSettings: Boolean = false) {
        val input = EditText(this).apply {
            hint = "输入 6 位动态验证码"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine(true)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("家长验证")
            .setMessage("打开家长身份验证器，输入当前 6 位验证码。")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("验证", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val secret = try {
                            securePrefs().getString(KEY_TOTP_SECRET, null)
                        } catch (_: Exception) { null }
                        if (secret == null) {
                            Toast.makeText(this, "请先设置家长动态密码。", Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        val ok = try {
                            Totp.verify(base32Decode(secret), input.text.toString().trim())
                        } catch (_: Exception) { false }
                        if (ok) {
                            dialog.dismiss()
                            if (forSettings) {
                                promptDailyUsageThreshold()
                            } else {
                                android.app.AlertDialog.Builder(this)
                                    .setTitle("验证成功")
                                    .setMessage("家长身份验证通过。当前版本的验证码用于保护设置入口；普通 APK 仍不能可靠阻止孩子退出或卸载应用。")
                                    .setPositiveButton("知道了", null)
                                    .show()
                            }
                        } else {
                            input.error = "验证码错误或已过期"
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun promptDailyUsageThreshold() {
        val prefs = getSharedPreferences("usage_settings", MODE_PRIVATE)
        val input = EditText(this).apply {
            hint = "例如 120（分钟）"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(prefs.getInt(KEY_DAILY_LIMIT_MINUTES, 120).toString())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("每日使用时长提醒阈值")
            .setMessage("设置 30–600 分钟。该阈值用于本应用打开时的提醒，不会在后台自动锁屏，也不是纯游戏时长。")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val minutes = input.text.toString().toIntOrNull()
                        if (minutes == null || minutes !in 30..600) {
                            input.error = "请输入 30 到 600 之间的整数"
                            return@setOnClickListener
                        }
                        prefs.edit().putInt(KEY_DAILY_LIMIT_MINUTES, minutes).apply()
                        dialog.dismiss()
                        refreshStatus()
                        Toast.makeText(this, "已保存每日提醒阈值：$minutes 分钟", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.show()
            }
    }

    private fun base32Encode(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(alphabet[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    private fun base32Decode(value: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = value.uppercase(Locale.US).trim().trimEnd('=').replace(" ", "")
        val out = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (char in cleaned) {
            val v = alphabet.indexOf(char)
            require(v >= 0) { "Invalid Base32 secret" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.add(((buffer shr (bits - 8)) and 0xff).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
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
        val limit = getSharedPreferences("usage_settings", MODE_PRIVATE)
            .getInt(KEY_DAILY_LIMIT_MINUTES, 120)
        val overLimit = minutes >= limit
        status.text = "使用情况访问：已授权\n今日应用前台时长合计（系统统计估算）：${minutes / 60} 小时 ${minutes % 60} 分钟\n每日提醒阈值：$limit 分钟\n" +
            (if (overLimit) "已达到阈值，请休息。此提醒仅在打开本应用时刷新。" else "距离提醒阈值约 ${limit - minutes} 分钟。") +
            "\n注意：这是所有应用的估算合计，不等同于纯游戏时长；不会自动限制使用。"
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

    companion object {
        private const val KEY_TOTP_SECRET = "totp_secret_base32"
        private const val KEY_DAILY_LIMIT_MINUTES = "daily_limit_minutes"
    }
}
