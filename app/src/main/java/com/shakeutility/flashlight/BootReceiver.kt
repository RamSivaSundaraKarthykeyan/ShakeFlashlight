package com.shakeutility.flashlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            // Check if user had enabled the service before reboot
            val sharedPrefs = context.getSharedPreferences("shake_flashlight_prefs", Context.MODE_PRIVATE)
            val wasServiceEnabled = sharedPrefs.getBoolean("service_enabled", false)

            if (wasServiceEnabled) {
                val serviceIntent = Intent(context, ShakeDetectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}