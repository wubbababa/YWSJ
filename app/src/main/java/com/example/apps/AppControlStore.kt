package com.example.apps

import android.content.Context

class AppControlStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getLaunchCount(packageName: String): Int {
        return preferences.getInt(launchCountKey(packageName), 0)
    }

    fun getLastLaunchTime(packageName: String): Long {
        return preferences.getLong(lastLaunchKey(packageName), 0L)
    }

    fun recordAppLaunch(packageName: String, launchedAt: Long = System.currentTimeMillis()) {
        val count = getLaunchCount(packageName) + 1
        preferences.edit()
            .putInt(launchCountKey(packageName), count)
            .putLong(lastLaunchKey(packageName), launchedAt)
            .apply()
    }

    fun getNetworkWhitelist(): Set<String> {
        return preferences.getStringSet(KEY_NETWORK_WHITELIST, emptySet())
            ?.toSet()
            .orEmpty()
    }

    /**
     * Persists the whitelist only after the root rules were applied successfully.
     * Pending checkbox changes must stay local to the UI until then.
     */
    fun saveActiveFirewallState(enabled: Boolean, packageNames: Set<String>) {
        preferences.edit()
            .putBoolean(KEY_FIREWALL_ENABLED, enabled)
            .putStringSet(KEY_NETWORK_WHITELIST, HashSet(packageNames))
            .apply()
    }

    fun isFirewallEnabled(): Boolean {
        return preferences.getBoolean(KEY_FIREWALL_ENABLED, false)
    }

    fun setFirewallEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_FIREWALL_ENABLED, enabled)
            .apply()
    }

    fun setLastFirewallStatus(message: String, isError: Boolean) {
        preferences.edit()
            .putString(KEY_FIREWALL_MESSAGE, message)
            .putBoolean(KEY_FIREWALL_STATUS_ERROR, isError)
            .apply()
    }

    fun getLastFirewallMessage(): String {
        return preferences.getString(KEY_FIREWALL_MESSAGE, "").orEmpty()
    }

    fun getLastFirewallStatusIsError(): Boolean {
        return preferences.getBoolean(KEY_FIREWALL_STATUS_ERROR, false)
    }

    private fun launchCountKey(packageName: String) = "launch_count_$packageName"

    private fun lastLaunchKey(packageName: String) = "last_launch_$packageName"

    private companion object {
        const val PREFERENCES_NAME = "app_control_settings"
        const val KEY_NETWORK_WHITELIST = "network_whitelist"
        const val KEY_FIREWALL_ENABLED = "firewall_enabled"
        const val KEY_FIREWALL_MESSAGE = "firewall_message"
        const val KEY_FIREWALL_STATUS_ERROR = "firewall_status_error"
    }
}
