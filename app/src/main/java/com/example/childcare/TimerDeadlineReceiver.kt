package com.example.childcare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Backup deadline path if the foreground timer service is stopped.
 * This posts a recovery notification and attempts to launch the guard, but Android
 * may defer alarms and/or block background activity launches.
 */
class TimerDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val deadline = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getLong(MainActivity.KEY_DEADLINE, 0L)
        val now = System.currentTimeMillis()
        if (deadline <= 0L) {
            Log.i(TAG, "Fallback alarm received without an active deadline")
            return
        }
        if (deadline > now) {
            Log.i(TAG, "Fallback alarm fired early; deadline remains in the future")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val pending = PendingIntent.getBroadcast(
                context, UnlockTimerService.EXPIRED_NOTIFICATION_ID,
                Intent(context, TimerDeadlineReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, deadline, pending)
            return
        }

        Log.i(TAG, "Deadline expired; posting recovery notification")
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel("usage_timer", "使用计时", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            context, UnlockTimerService.EXPIRED_NOTIFICATION_ID, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            UnlockTimerService.EXPIRED_NOTIFICATION_ID,
            NotificationCompat.Builder(context, "usage_timer")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("休息时间到了")
                .setContentText("请点击通知返回 Lock Screen；系统可能限制自动切换")
                .setContentIntent(pending)
                .setAutoCancel(false)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
        try {
            context.startActivity(launch)
            Log.i(TAG, "Fallback requested guard activity launch")
        } catch (e: Exception) {
            Log.w(TAG, "Background activity launch blocked; notification remains", e)
        }
    }

    companion object {
        private const val TAG = "MinimalGuardAlarm"
    }
}
