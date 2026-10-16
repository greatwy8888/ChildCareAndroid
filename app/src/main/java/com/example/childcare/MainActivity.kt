package com.example.childcare

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.InputFilter
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
        if (locked) applyImmersive()
    }

    @Deprecated("Keep the guard visible while locked")
    override fun onBackPressed() {
        if (locked) Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
        else super.onBackPressed()
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun showGuard() {
        locked = true
        applyImmersive()
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                dpm.setLockTaskPackages(admin(), arrayOf(packageName))
                dpm.setStatusBarDisabled(admin(), true)
                if (dpm.isLockTaskPermitted(packageName) &&
                    (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                    startLockTask()
                }
            } catch (_: Exception) { }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36)
            setBackgroundColor(android.graphics.Color.rgb(12, 20, 42))
        }
        root.addView(TextView(this).apply {
            text = "✦"
            textSize = 70f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(145, 198, 255))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "王梓淇请休息一会"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 16, 0, 10)
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "输入密码继续使用"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(194, 207, 231))
            setPadding(0, 0, 0, 20)
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
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
            .setMessage("验证通过后可使用手机 40 分钟。请输入谷歌验证器的 6 位动态密码。")
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
        // Fixed Base32 secret, with whitespace/newlines removed.
        val secret = "3XJSD7D2YTLONRMPETS6NG5VP4M6JPXU"
        val key = decodeBase32(secret) ?: return false
        val now = System.currentTimeMillis() / 1000L
        // Allow the adjacent 30-second time steps to tolerate small device clock drift.
        return (-1L..1L).any { offset ->
            val counter = now / 30L + offset
            totpCode(key, counter) == code
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

    private fun derive(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                .joinToString("") { "%02x".format(it) }
        } finally { spec.clearPassword() }
    }

    private fun decodeHex(hex: String): ByteArray = try {
        if (hex.length % 2 != 0) byteArrayOf() else hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    } catch (_: Exception) { byteArrayOf() }

    private fun admin() = ComponentName(this, AdminReceiver::class.java)

    companion object {
        const val PREFS = "minimal_guard"
        const val KEY_DEADLINE = "unlock_deadline"
        private const val UNLOCK_MS = 40 * 60 * 1000L
    }
}
