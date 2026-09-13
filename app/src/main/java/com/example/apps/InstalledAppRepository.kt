package com.example.apps

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class LauncherAppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val launchCount: Int,
    val usageTimeMillis: Long,
    val lastUsedTime: Long
)

data class NetworkAppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isSystem: Boolean
)

class InstalledAppRepository(private val context: Context) {
    private val packageManager = context.packageManager
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsageAccess(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun loadLauncherApps(store: AppControlStore): List<LauncherAppInfo> {
        return withContext(Dispatchers.IO) {
            val usageByPackage = if (hasUsageAccess()) {
                queryUsageByPackage()
            } else {
                emptyMap()
            }

            @Suppress("DEPRECATION")
            val resolvedApps = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0
            )

            resolvedApps.asSequence()
                .mapNotNull { resolveInfo ->
                    buildLauncherApp(resolveInfo, usageByPackage, store)
                }
                .distinctBy { it.packageName }
                .sortedWith(
                    compareByDescending<LauncherAppInfo> { it.launchCount }
                        .thenByDescending { it.usageTimeMillis }
                        .thenByDescending { it.lastUsedTime }
                        .thenBy { it.label.lowercase(Locale.getDefault()) }
                )
                .toList()
        }
    }

    suspend fun loadNetworkApps(): List<NetworkAppInfo> {
        return withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val applications = packageManager.getInstalledApplications(
                PackageManager.GET_META_DATA
            )

            applications.asSequence()
                .mapNotNull { appInfo -> buildNetworkApp(appInfo) }
                .sortedWith(
                    compareBy<NetworkAppInfo> { it.isSystem }
                        .thenBy { it.label.lowercase(Locale.getDefault()) }
                )
                .toList()
        }
    }

    private fun buildLauncherApp(
        resolveInfo: ResolveInfo,
        usageByPackage: Map<String, UsageStatsSummary>,
        store: AppControlStore
    ): LauncherAppInfo? {
        val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return null
        val packageName = appInfo.packageName
        if (packageName == context.packageName) return null

        val usage = usageByPackage[packageName] ?: UsageStatsSummary()
        return try {
            LauncherAppInfo(
                packageName = packageName,
                label = resolveInfo.loadLabel(packageManager).toString(),
                icon = iconToImageBitmap(resolveInfo.loadIcon(packageManager)),
                launchCount = maxOf(
                    usage.launchCount,
                    store.getLaunchCount(packageName)
                ),
                usageTimeMillis = usage.totalTimeInForeground,
                lastUsedTime = maxOf(usage.lastTimeUsed, store.getLastLaunchTime(packageName))
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun buildNetworkApp(appInfo: ApplicationInfo): NetworkAppInfo? {
        val packageName = appInfo.packageName
        if (packageName == context.packageName) return null
        if (!requestsInternet(packageName)) return null

        val systemFlags = ApplicationInfo.FLAG_SYSTEM or
            ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        val isSystem = appInfo.flags and systemFlags != 0

        return try {
            NetworkAppInfo(
                packageName = packageName,
                label = appInfo.loadLabel(packageManager).toString(),
                icon = iconToImageBitmap(appInfo.loadIcon(packageManager)),
                isSystem = isSystem
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun requestsInternet(packageName: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS
            )
            packageInfo.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun queryUsageByPackage(): Map<String, UsageStatsSummary> {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - USAGE_WINDOW_MILLIS
        val summaries = mutableMapOf<String, UsageStatsSummary>()

        @Suppress("DEPRECATION")
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ).orEmpty()

        stats.forEach { stat ->
            val current = summaries[stat.packageName] ?: UsageStatsSummary()
            summaries[stat.packageName] = UsageStatsSummary(
                totalTimeInForeground = current.totalTimeInForeground +
                    stat.totalTimeInForeground.coerceAtLeast(0L),
                lastTimeUsed = maxOf(current.lastTimeUsed, stat.lastTimeUsed),
                launchCount = current.launchCount
            )
        }

        val events = usageStatsManager.queryEvents(startTime, endTime) ?: return summaries
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (!event.isLaunchEvent()) continue
            val packageName = event.packageName ?: continue
            val current = summaries[packageName] ?: UsageStatsSummary()
            summaries[packageName] = current.copy(launchCount = current.launchCount + 1)
        }
        return summaries
    }

    @Suppress("DEPRECATION")
    private fun UsageEvents.Event.isLaunchEvent(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            eventType == UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }

    private fun iconToImageBitmap(drawable: Drawable): ImageBitmap {
        return drawable.toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX).asImageBitmap()
    }

    private data class UsageStatsSummary(
        val totalTimeInForeground: Long = 0L,
        val lastTimeUsed: Long = 0L,
        val launchCount: Int = 0
    )

    private companion object {
        const val USAGE_WINDOW_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val ICON_SIZE_PX = 128
    }
}
