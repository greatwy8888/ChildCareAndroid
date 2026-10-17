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
            Log.i(TAG, "Stored unlock deadline is absent or expired; requesting relock")
            // A service started via startForegroundService() must promote itself
            // promptly even when the saved deadline is already expired.
            startForeground(NOTIFICATION_ID, notification())
            relock()
            return START_NOT_STICKY
        }
        Log.i(TAG, "Usage timer started; remainingMs=${deadline - System.currentTimeMillis()}")
        startForeground(NOTIFICATION_ID, notification())
        timer?.cancel()
        timer = object : CountDownTimer(deadline - System.currentTimeMillis(), 1000L) {
            override fun onTick(millisUntilFinished: Long) = Unit
            override fun onFinish() = relock()
        }.start()
        return START_STICKY
    }

    private fun relock() {
        Log.i(TAG, "Usage timer expired; requesting lock screen activity")
        // Post before requesting the activity to avoid a race where MainActivity
        // resumes and cancels the notification just before it is posted.
        getSystemService(NotificationManager::class.java)
            .notify(EXPIRED_NOTIFICATION_ID, expiryNotification())
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            startActivity(launch)
            // Do not clear the deadline here: some Android/OEM background-launch
            // restrictions can silently block the launch without throwing.
            // MainActivity consumes the expired deadline only after it resumes.
        } catch (e: Exception) {
            Log.e(TAG, "System blocked relock activity launch; keeping expired deadline for recovery", e)
        }
        // Android may silently block the activity launch; the expired deadline
        // remains persisted until MainActivity actually resumes.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun expiryNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this, EXPIRED_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("休息时间到了")
            .setContentText("如果锁屏未自动显示，请点击返回 Lock Screen")
            .setContentIntent(pending)
            .setAutoCancel(false)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun notification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("lock screen")
            .setContentText("家长授权的使用时段正在进行")
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
        const val EXPIRED_NOTIFICATION_ID = 43
        private const val TAG = "MinimalGuardTimer"
    }
}
