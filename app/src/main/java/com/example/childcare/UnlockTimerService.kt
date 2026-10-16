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

class UnlockTimerService : Service() {
    private var timer: CountDownTimer? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "使用计时", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stored = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getLong(MainActivity.KEY_DEADLINE, 0L)
        val deadline = intent?.getLongExtra(EXTRA_DEADLINE, stored) ?: stored
        if (deadline <= System.currentTimeMillis()) {
            relock()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        timer?.cancel()
        timer = object : CountDownTimer(deadline - System.currentTimeMillis(), 1000L) {
            override fun onTick(millisUntilFinished: Long) = Unit
            override fun onFinish() = relock()
        }.start()
        return START_STICKY
    }

    private fun relock() {
        getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .edit().remove(MainActivity.KEY_DEADLINE).apply()
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try { startActivity(launch) }
        catch (e: Exception) { Log.e(TAG, "System blocked relock activity launch", e) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("极简屏幕保护")
            .setContentText("家长授权的 40 分钟使用时段正在进行")
            .setContentIntent(pending)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_DEADLINE = "deadline"
        private const val CHANNEL = "usage_timer"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "MinimalGuardTimer"
    }
}
