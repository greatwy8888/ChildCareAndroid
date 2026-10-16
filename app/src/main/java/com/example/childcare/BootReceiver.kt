package com.example.childcare

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .edit().remove(MainActivity.KEY_DEADLINE).apply()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            try {
                val admin = ComponentName(context, AdminReceiver::class.java)
                dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
                dpm.setStatusBarDisabled(admin, true)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to apply device-owner policy", e)
            }
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try { context.startActivity(launch) }
            catch (e: Exception) { Log.w(TAG, "System blocked boot activity launch", e) }
        }
    }
    companion object { private const val TAG = "MinimalGuardBoot" }
}
