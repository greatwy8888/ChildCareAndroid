package com.example.childcare

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import java.util.Locale

/**
 * Device-owner screen guard. A regular APK can only provide a best-effort UI;
 * device-owner provisioning is required for kiosk enforcement.
 */
class MainActivity : ComponentActivity() {
    private lateinit var root: LinearLayout
    private var locked = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterProtectionMode()
        if (!securePrefs().contains(KEY_SECRET)) showFirstRunSetup()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enterProtectionMode()
    }

    override fun onResume() {
        super.onResume()
        if (locked) applyImmersiveMode()
        if (isDeviceOwner() && !locked) {
            val until = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_UNLOCK_UNTIL, 0L)
            if (until <= System.currentTimeMillis()) enterProtectionMode()
        }
    }

    @Deprecated("Deprecated in Android API; overridden to keep the protection screen visible")
    override fun onBackPressed() {
        if (locked) {
            Toast.makeText(this, "请输入家长动态验证码解锁", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }

    private fun enterProtectionMode() {
        locked = true
        applyImmersiveMode()
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                dpm.setLockTaskPackages(adminComponent(), arrayOf(packageName))
                dpm.setStatusBarDisabled(adminComponent(), true)
                if (dpm.isLockTaskPermitted(packageName)) startLockTask()
            } catch (_: Exception) {
                // Keep the full-screen UI; setup instructions explain provisioning.
            }
        }
        renderProtectionScreen()
    }

    private fun applyImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun securePrefs() = EncryptedSharedPreferences.create(
        this,
        "screen_guard",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun renderProtectionScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36)
            setBackgroundColor(android.graphics.Color.rgb(12, 20, 42))
        }
        root.addView(TextView(this).apply {
            text = "✦"
            textSize = 64f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(145, 198, 255))
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "屏幕保护"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 12, 0, 8)
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "请休息片刻，再继续使用"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(194, 207, 231))
            setPadding(0, 0, 0, 36)
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "输入动态验证码"
            setOnClickListener { promptCode() }
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun showFirstRunSetup() {
        val secret = base32Encode(ByteArray(20).also { SecureRandom().nextBytes(it) })
        securePrefs().edit().putString(KEY_SECRET, secret).apply()
        AlertDialog.Builder(this)
            .setTitle("首次设置家长验证码")
            .setMessage(
                "请在家长自己的 Google Authenticator 中添加“基于时间的一次性密码（TOTP）”，手动输入密钥：\n\n" +
                    secret +
                    "\n\n请将密钥安全保存在家长处。密钥丢失后，设备所有者模式下可能需要恢复出厂设置才能恢复设备使用。"
            )
            .setCancelable(false)
            .setPositiveButton("已添加") { _, _ -> renderProtectionScreen() }
            .show()
    }

    private fun promptCode() {
        val input = EditText(this).apply {
            hint = "6 位动态验证码"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("家长验证码")
            .setMessage("输入 Google Authenticator 当前显示的 6 位验证码。")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("验证", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val secret = securePrefs().getString(KEY_SECRET, null)
                val valid = try {
                    secret != null && Totp.verify(base32Decode(secret), input.text.toString().trim())
                } catch (_: Exception) { false }
                if (valid) {
                    dialog.dismiss()
                    unlockForFortyMinutes()
                } else {
                    input.error = "验证码错误或已过期"
                }
            }
        }
        dialog.show()
    }

    private fun unlockForFortyMinutes() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                dpm.setStatusBarDisabled(adminComponent(), false)
                if (isLockTaskModeActive()) stopLockTask()
            } catch (_: Exception) { }
        }
        locked = false
        val deadline = System.currentTimeMillis() + UNLOCK_MS
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_UNLOCK_UNTIL, deadline).apply()
        val service = Intent(this, UnlockTimerService::class.java).putExtra(UnlockTimerService.EXTRA_DEADLINE, deadline)
        try {
            androidx.core.content.ContextCompat.startForegroundService(this, service)
        } catch (_: Exception) {
            Toast.makeText(this, "无法启动计时服务；请检查系统后台运行权限。", Toast.LENGTH_LONG).show()
        }
        Toast.makeText(this, "验证成功，手机已解锁 40 分钟", Toast.LENGTH_LONG).show()
        // Return to the home screen so the child can use other apps normally.
        moveTaskToBack(true)
    }

    private fun isLockTaskModeActive(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun isDeviceOwner() =
        (getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isDeviceOwnerApp(packageName)

    private fun adminComponent() = ComponentName(this, AdminReceiver::class.java)

    override fun onDestroy() {
        super.onDestroy()
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

    companion object {
        const val PREFS = "screen_guard_state"
        const val KEY_UNLOCK_UNTIL = "unlock_until_wall_clock"
        private const val KEY_SECRET = "totp_secret_base32"
        private const val UNLOCK_MS = 40 * 60 * 1000L
    }
}
