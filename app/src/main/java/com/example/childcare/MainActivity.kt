package com.example.childcare

import android.app.AlertDialog
import android.os.Bundle
import android.os.CountDownTimer
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
 * Minimal TOTP screen-protection prototype.
 * A normal APK cannot reliably lock the whole device or block system navigation.
 */
class MainActivity : ComponentActivity() {
    private var timer: CountDownTimer? = null
    private lateinit var root: LinearLayout
    private lateinit var hint: TextView
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        showProtectionScreen()
        if (!securePrefs().contains(KEY_SECRET)) showFirstRunSetup()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    @Deprecated("Deprecated in Android API; overridden to keep the protection screen visible")
    override fun onBackPressed() {
        if (!unlocked) {
            // No in-app back navigation while the protection screen is locked.
            Toast.makeText(this, "请输入家长动态验证码解锁", Toast.LENGTH_SHORT).show()
        } else {
            super.onBackPressed()
        }
    }

    private fun securePrefs() = EncryptedSharedPreferences.create(
        this,
        "screen_guard",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun showProtectionScreen() {
        unlocked = false
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36)
            setBackgroundColor(android.graphics.Color.rgb(12, 20, 42))
        }
        val emblem = TextView(this).apply {
            text = "✦"
            textSize = 64f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(145, 198, 255))
        }
        val title = TextView(this).apply {
            text = "屏幕保护"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 12, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "请休息片刻，再继续使用"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(194, 207, 231))
            setPadding(0, 0, 0, 36)
        }
        hint = TextView(this).apply {
            text = "请输入家长 Google Authenticator 验证码"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 12, 0, 16)
        }
        val unlock = Button(this).apply {
            text = "输入动态验证码"
            setOnClickListener { promptCode() }
        }
        root.addView(emblem, LinearLayout.LayoutParams(-1, -2))
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(hint, LinearLayout.LayoutParams(-1, -2))
        root.addView(unlock, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun showFirstRunSetup() {
        val bytes = ByteArray(20).also { SecureRandom().nextBytes(it) }
        val secret = base32Encode(bytes)
        securePrefs().edit().putString(KEY_SECRET, secret).apply()
        AlertDialog.Builder(this)
            .setTitle("首次设置家长验证码")
            .setMessage(
                "请先在家长的 Google Authenticator 中添加一个基于时间的一次性密码（TOTP），手动输入以下密钥：\n\n" +
                    secret +
                    "\n\n请妥善保存密钥。设置完成后点击“已添加”，之后只有当前有效的 6 位动态验证码可以解锁。"
            )
            .setCancelable(false)
            .setPositiveButton("已添加") { _, _ -> showProtectionScreen() }
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
                    enterUnlockedPeriod()
                } else {
                    input.error = "验证码错误或已过期"
                }
            }
        }
        dialog.show()
    }

    private fun enterUnlockedPeriod() {
        unlocked = true
        timer?.cancel()
        root.removeAllViews()
        root.gravity = Gravity.CENTER
        val message = TextView(this).apply {
            text = "已解锁\n可正常使用 40 分钟"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        }
        val remaining = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(194, 207, 231))
            setPadding(0, 24, 0, 0)
        }
        root.addView(message, LinearLayout.LayoutParams(-1, -2))
        root.addView(remaining, LinearLayout.LayoutParams(-1, -2))
        timer = object : CountDownTimer(UNLOCK_MS, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val total = millisUntilFinished / 1000
                remaining.text = String.format(Locale.getDefault(), "剩余 %02d:%02d", total / 60, total % 60)
            }
            override fun onFinish() {
                unlocked = false
                showProtectionScreen()
                Toast.makeText(this@MainActivity, "40 分钟已到，请重新输入家长验证码", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    override fun onDestroy() {
        timer?.cancel()
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
        private const val KEY_SECRET = "totp_secret_base32"
        private const val UNLOCK_MS = 40 * 60 * 1000L
    }
}
