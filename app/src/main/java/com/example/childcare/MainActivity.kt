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
        showGuard()
        showPasswordPrompt()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showGuard()
        showPasswordPrompt()
    }

    override fun onResume() {
        super.onResume()
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

    private fun showPasswordPrompt() {
        val input = EditText(this).apply {
            hint = "输入密码"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("王梓淇请休息一会")
            .setMessage("验证通过后可使用手机 40 分钟。")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("解锁", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = input.text.toString()
                if (entered.length != 6 || !entered.all { it in '0'..'9' } || !verifyTotp(entered)) {
                    input.error = "动态密码错误或已过期"
                    return@setOnClickListener
                }
                dialog.dismiss()
                unlockForFortyMinutes()
            }
        }
        dialog.show()
    }

    private fun verifyTotp(code: String): Boolean {
        val secret = "3XJSD7D2YTLONRMPETS6NG5VP4M6JPXU"
        val key = decodeBase32(secret) ?: return false
        val now = System.currentTimeMillis() / 1000L
        return (-1L..1L).any { offset ->
            totpCode(key, now / 30L + offset) == code
        }
    }

    private fun totpCode(key: ByteArray, counter: Long): String {
        val data = java.nio.ByteBuffer.allocate(8).putLong(counter).array()
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    private fun decodeBase32(input: String): ByteArray? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.filterNot { it.isWhitespace() || it == '=' }.uppercase(java.util.Locale.ROOT)
        if (cleaned.isEmpty() || cleaned.any { alphabet.indexOf(it) < 0 }) return null
        val output = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (ch in cleaned) {
            buffer = (buffer shl 5) or alphabet.indexOf(ch)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                output.write((buffer shr bits) and 0xff)
            }
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun unlockForFortyMinutes() {
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
        val deadline = System.currentTimeMillis() + UNLOCK_MS
        prefs().edit().putLong(KEY_DEADLINE, deadline).apply()
        try {
            ContextCompat.startForegroundService(
                this, Intent(this, UnlockTimerService::class.java).putExtra(UnlockTimerService.EXTRA_DEADLINE, deadline)
            )
        } catch (_: Exception) {
            Toast.makeText(this, "计时服务启动失败，请检查后台运行权限", Toast.LENGTH_LONG).show()
        }
        Toast.makeText(this, "已解锁 40 分钟", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    private fun admin() = ComponentName(this, AdminReceiver::class.java)

    companion object {
        const val PREFS = "minimal_guard"
        const val KEY_DEADLINE = "unlock_deadline"
        private const val UNLOCK_MS = 40 * 60 * 1000L
    }
}
