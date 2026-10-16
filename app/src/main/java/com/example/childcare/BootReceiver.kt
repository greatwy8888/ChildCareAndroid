package com.example.childcare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Ordinary apps cannot force a lock screen on boot; this receiver only records boot receipt. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("ChildCare", "Boot completed; open app to resume persisted settings.")
        }
    }
}
