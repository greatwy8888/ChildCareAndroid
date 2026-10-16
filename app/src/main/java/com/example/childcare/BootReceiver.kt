package com.example.childcare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Best-effort boot entry point for a regular APK.
 *
 * Android 10+ restricts launching activities from the background, and some OEM
 * builds (including some Honor/MagicOS versions) may block it. Therefore this
 * receiver never treats an automatic UI launch as guaranteed. The user should
 * enable the app's Auto-launch/background-running permissions in system settings.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Startup broadcast received: ${intent.action}")

                // Best-effort attempt to reopen the app after a reboot/update.
                // Android may block this background activity launch; the app must
                // still be launchable normally from its launcher icon.
                val launchIntent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    try {
                        context.startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "System blocked automatic app launch", e)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ChildCareBoot"
    }
}
