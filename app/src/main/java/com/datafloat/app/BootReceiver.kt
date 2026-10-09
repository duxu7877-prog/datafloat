package com.datafloat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restarts tracking after a reboot or an app update if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as App
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.settingsRepo.current().trackingEnabled) {
                    ContextCompat.startForegroundService(app, Intent(app, TrackerService::class.java))
                }
            } catch (e: Exception) {
                // Background-start restrictions on some OEM builds: user can reopen the app.
            } finally {
                pending.finish()
            }
        }
    }
}
