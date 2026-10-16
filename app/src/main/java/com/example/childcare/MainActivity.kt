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
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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
        if (prefs().getString(KEY_HASH, null) == null) showPasswordSetup()
        else showPasswordPrompt()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showGuard()
        if (prefs().getString(KEY_HASH, null) == null) showPasswordSetup()
        else showPasswordPrompt()
    }

    override fun onResume() {
        super.onResume()
        if (locked) applyImmersive()
    }

    @Deprecated("Keep the guard visible while locked")
    override fun onBackPressed() {
        if (locked) Toast.makeText(this, "请输入密码解锁", Toast.LENGTH_SHORT).show()
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
            text = "休息一下"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 16, 0, 10)
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "请输入家长设置的密码继续使用"
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

    private fun showPasswordSetup() {
        val password = EditText(this).apply {
            hint = "设置家长密码（至少 6 位）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val confirm = EditText(this).apply {
            hint = "再次输入密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24)
            addView(password)
            addView(confirm)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("首次设置家长密码")
            .setMessage("密码只保存为本机派生摘要，请家长妥善保管。")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p = password.text.toString()
                if (p.length < 6) {
                    password.error = "至少 6 位"
                    return@setOnClickListener
                }
                if (p != confirm.text.toString()) {
                    confirm.error = "两次输入不一致"
                    return@setOnClickListener
                }
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                prefs().edit()
                    .putString(KEY_SALT, salt.joinToString("") { "%02x".format(it) })
                    .putString(KEY_HASH, derive(p, salt))
                    .apply()
                dialog.dismiss()
                showGuard()
                showPasswordPrompt()
            }
        }
        dialog.show()
    }

    private fun showPasswordPrompt() {
        val input = EditText(this).apply {
            hint = "输入家长密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("屏幕保护")
            .setMessage("验证通过后可使用手机 40 分钟。")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("解锁", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val salt = decodeHex(prefs().getString(KEY_SALT, "") ?: "")
                val expected = prefs().getString(KEY_HASH, "") ?: ""
                val actual = derive(input.text.toString(), salt)
                if (salt.isNotEmpty() && MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())) {
                    dialog.dismiss()
                    unlockForFortyMinutes()
                } else {
                    input.error = "密码错误"
                }
            }
        }
        dialog.show()
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
        private const val KEY_SALT = "password_salt"
        private const val KEY_HASH = "password_hash"
        private const val UNLOCK_MS = 40 * 60 * 1000L
    }
}
