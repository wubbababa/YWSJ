package com.example.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FirewallBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = AppControlStore(appContext)
                if (!store.isFirewallEnabled()) return@launch

                val result = FirewallManager(appContext).applyWhitelist(
                    store.getNetworkWhitelist()
                )
                store.setLastFirewallStatus(result.message, !result.success)
                if (!result.success) {
                    Log.e(TAG, "Failed to restore firewall after boot: ${result.message}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "FirewallBootReceiver"
    }
}
