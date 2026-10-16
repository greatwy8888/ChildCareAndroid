package com.example.childcare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground timer keeps the deadline alive while other apps are in use.
 * When the deadline expires, it brings the protection Activity to the front.
 */
class UnlockTimerService : Service() {
    private var timer: CountDownTimer? = null
    private var deadline = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stored = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getLong(MainActivity.KEY_UNLOCK_UNTIL, 0L)
        deadline = intent?.getLongExtra(EXTRA_DEADLINE, stored) ?: stored
        if (deadline <= System.currentTimeMillis()) {
            relockAndStop()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(deadline))
        timer?.cancel()
        timer = object : CountDownTimer(deadline - System.currentTimeMillis(), 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                // Deadline is persisted; service recreation resumes from the same deadline.
            }
            override fun onFinish() { relockAndStop() }
        }.start()
        return START_STICKY
    }

    private fun relockAndStop() {
        getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .edit().remove(MainActivity.KEY_UNLOCK_UNTIL).apply()
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            startActivity(launch)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to bring protection screen forward; tap the notification to reopen it", e)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(until: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("动态验证码屏幕保护")
            .setContentText("家长授权的 40 分钟使用时段正在进行")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "解锁计时", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_DEADLINE = "deadline"
        private const val CHANNEL_ID = "unlock_timer"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "UnlockTimer"
    }
}
