package com.example.childcare

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding

class MainActivity : ComponentActivity() {
    private var locked = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // A recreated activity must respect an existing, still-valid unlock window.
        // Do not briefly show the guard or prompt for TOTP while authorization is active.
        val deadline = prefs().getLong(KEY_DEADLINE, 0L)
        if (deadline > System.currentTimeMillis()) {
            locked = false
            finish()
            return
        }

        showGuard()
        showPasswordPrompt()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Launcher and timer-notification intents must not end an authorized
        // usage window early. The persisted deadline is the source of truth.
        val deadline = prefs().getLong(KEY_DEADLINE, 0L)
        if (deadline > System.currentTimeMillis()) {
            locked = false
            moveTaskToBack(true)
            return
        }

        showGuard()
        showPasswordPrompt()
    }

    override fun onResume() {
        super.onResume()
        val deadline = prefs().getLong(KEY_DEADLINE, 0L)
        if (deadline > 0L && deadline <= System.currentTimeMillis()) {
            // The service leaves the expired deadline in place because Android may
            // silently block a background activity launch without throwing.
            // Consume the deadline here, when the lock activity is actually resumed.
            if (!locked) {
                locked = true
                showGuard()
                showPasswordPrompt()
            }
            prefs().edit().remove(KEY_DEADLINE).apply()
        }
        if (locked) {
            applyImmersive()
            enforceKioskIfManaged()
        }
    }

    @Deprecated("Keep the guard visible while locked")
    override fun onBackPressed() {
        if (locked) Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
        else super.onBackPressed()
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun showGuard() {
        locked = true
        applyImmersive()
        enforceKioskIfManaged()
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                // Only Device Owner can enforce this. A regular APK cannot block system uninstall.
                dpm.setUninstallBlocked(admin(), packageName, true)
            } catch (_: Exception) { }
        }

        val pageBackground = Color.rgb(10, 17, 35)
        val cardBackground = Color.rgb(22, 34, 61)
        val accent = Color.rgb(130, 190, 255)
        val muted = Color.rgb(190, 205, 230)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(26))
            setBackgroundColor(pageBackground)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24))
            background = roundedBackground(cardBackground, 28, Color.rgb(48, 67, 103))
        }
        val iconCircle = TextView(this).apply {
            text = "✦"
            textSize = 38f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = roundedBackground(Color.rgb(32, 51, 85), 40)
        }
        card.addView(iconCircle, LinearLayout.LayoutParams(dp(82), dp(82)).apply {
            bottomMargin = dp(24)
        })

        card.addView(TextView(this).apply {
            text = "友情提示："
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(0, 0, 0, dp(10))
        }, LinearLayout.LayoutParams(-1, -2))

        card.addView(TextView(this).apply {
            text = "王梓淇请休息一会"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(14))
        }, LinearLayout.LayoutParams(-1, -2))

        card.addView(View(this).apply {
            setBackgroundColor(Color.rgb(58, 78, 115))
        }, LinearLayout.LayoutParams(dp(56), dp(2)).apply {
            bottomMargin = dp(18)
        })

        card.addView(TextView(this).apply {
            text = "请放下手机，休息一下眼睛"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, 0, 0, dp(8))
        }, LinearLayout.LayoutParams(-1, -2))

        card.addView(TextView(this).apply {
            text = "输入密码继续使用"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(143, 160, 191))
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "健康使用手机 · 保护视力"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(104, 124, 158))
            setPadding(0, dp(22), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun enforceKioskIfManaged() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(packageName)) return
        try {
            dpm.setLockTaskPackages(admin(), arrayOf(packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Disable Home/Overview and other system UI features while kiosk lock task is active.
                dpm.setLockTaskFeatures(admin(), DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            dpm.setStatusBarDisabled(admin(), true)
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (dpm.isLockTaskPermitted(packageName) &&
                activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask()
            }
        } catch (_: Exception) {
            // Keep the guard UI available even if a device policy operation is rejected.
        }
    }

    private fun applyImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    
private fun showSettingsDialog(onClosed: (() -> Unit)? = null) {
    val current = prefs().getInt(
        KEY_DURATION_MINUTES,
        DEFAULT_DURATION_MINUTES
    ).coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)

    val input = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setSingleLine(true)
        setText(current.toString())
        hint = "5 至 180 分钟"
    }

    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), dp(8))

        addView(input)

        addView(TextView(this@MainActivity).apply {
            text = "设置解锁时长，范围 5–180 分钟，默认 40 分钟。"
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, dp(12), 0, 0)
        })
    }

    val dialog = AlertDialog.Builder(this)
        .setTitle("⚙ 解锁时长设置")
        .setView(container)
        .setNegativeButton("取消", null)
        .setPositiveButton("保存", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener {
                val minutes = input.text.toString().toIntOrNull()

                if (minutes == null ||
                    minutes !in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES
                ) {
                    input.error = "请输入 5 至 180 之间的分钟数"
                    return@setOnClickListener
                }

                prefs().edit()
                    .putInt(KEY_DURATION_MINUTES, minutes)
                    .apply()

                Toast.makeText(
                    this,
                    "已保存：$minutes 分钟",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
    }

    dialog.setOnDismissListener {
        onClosed?.invoke()
    }

    dialog.show()
}

    private fun showPasswordPrompt() {
        val input = EditText(this).apply {
            hint = "输入密码"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("王梓淇请休息一会")
            .setMessage(
    "验证通过后可使用手机 ${
        prefs().getInt(KEY_DURATION_MINUTES, DEFAULT_DURATION_MINUTES)
            .coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
    } 分钟。"
)
            .setView(input)
            .setCancelable(false)
            .setNeutralButton("⚙ 设置", null)
            .setPositiveButton("解锁", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                showSettingsPasswordPrompt {
                    dialog.dismiss()
                    showSettingsDialog {
                        if (locked) showPasswordPrompt()
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = input.text.toString()
                if (entered.length != 6 || !entered.all { it in '0'..'9' } || !verifyTotp(entered)) {
                    input.error = "动态密码错误或已过期"
                    return@setOnClickListener
                }
                dialog.dismiss()
                unlockForDuration()
            }
        }
        dialog.show()
    }

    /**
     * Settings are protected by the same TOTP check as unlocking.
     * The existing lock screen remains visible until verification succeeds.
     */
    private fun showSettingsPasswordPrompt(onVerified: () -> Unit) {
        val input = EditText(this).apply {
            hint = "输入 6 位动态密码"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("家长验证")
            .setMessage("进入设置前，请输入 Google Authenticator 动态密码。")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("验证", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = input.text.toString()
                if (entered.length != 6 ||
                    !entered.all { it in '0'..'9' } ||
                    !verifyTotp(entered)
                ) {
                    input.error = "动态密码错误或已过期"
                    return@setOnClickListener
                }
                dialog.dismiss()
                onVerified()
            }
        }
        dialog.show()
    }

    private fun verifyTotp(code: String): Boolean {
        val secret = "3XJSD7D2YTLONRMPETS6NG5VP4M6JPXU"
        return Totp.verifyBase32(secret, code, System.currentTimeMillis() / 1000L)
    }

    private fun unlockForDuration() {
        val durationMinutes = prefs().getInt(
            KEY_DURATION_MINUTES,
            DEFAULT_DURATION_MINUTES
        ).coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
        val deadline = System.currentTimeMillis() + durationMinutes * 60_000L

        // Start the timer before releasing kiosk restrictions. If starting the
        // service throws, keep the guard active instead of leaving an untimed unlock.
        prefs().edit().putLong(KEY_DEADLINE, deadline).apply()
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, UnlockTimerService::class.java)
                    .putExtra(UnlockTimerService.EXTRA_DEADLINE, deadline)
            )
        } catch (_: Exception) {
            prefs().edit().remove(KEY_DEADLINE).apply()
            locked = true
            showGuard()
            applyImmersive()
            enforceKioskIfManaged()
            Toast.makeText(this, "计时服务启动失败，手机保持锁定状态", Toast.LENGTH_LONG).show()
            return
        }

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                dpm.setStatusBarDisabled(admin(), false)
                if ((getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
                    stopLockTask()
                }
            } catch (_: Exception) { }
        }
        locked = false
        Toast.makeText(this, "已解锁 $durationMinutes 分钟", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    private fun admin() = ComponentName(this, AdminReceiver::class.java)

companion object {
    const val PREFS = "minimal_guard"
    const val KEY_DEADLINE = "unlock_deadline"

    const val KEY_DURATION_MINUTES = "unlock_duration_minutes"
    const val DEFAULT_DURATION_MINUTES = 40
    const val MIN_DURATION_MINUTES = 5
    const val MAX_DURATION_MINUTES = 180
}
}
