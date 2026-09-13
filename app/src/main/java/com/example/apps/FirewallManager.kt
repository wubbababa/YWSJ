package com.example.apps

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class FirewallResult(
    val success: Boolean,
    val message: String
)

class FirewallManager(private val context: Context) {
    private val packageManager = context.packageManager

    suspend fun isRootAvailable(): Boolean {
        val result = runRootScript("id -u", ROOT_CHECK_TIMEOUT_MILLIS)
        return result.success && result.message.lineSequence().any { it.trim() == "0" }
    }

    suspend fun applyWhitelist(packageNames: Set<String>): FirewallResult {
        val allowedUids = resolveAllowedUids(packageNames)
        val script = FirewallScriptBuilder.buildApplyScript(allowedUids)
        val result = runRootScript(script, APPLY_TIMEOUT_MILLIS)
        return if (result.success &&
            result.message.contains(FirewallScriptBuilder.FIREWALL_OK_MARKER)
        ) {
            FirewallResult(
                success = true,
                message = "已允许白名单中的 ${packageNames.size} 个应用联网；系统核心进程和本应用保持放行。"
            )
        } else {
            FirewallResult(
                success = false,
                message = result.message.ifBlank { "无法应用 Root 网络规则。" }
            )
        }
    }

    suspend fun disable(): FirewallResult {
        val result = runRootScript(
            FirewallScriptBuilder.buildDisableScript(),
            APPLY_TIMEOUT_MILLIS
        )
        return if (result.success &&
            result.message.contains(FirewallScriptBuilder.FIREWALL_DISABLED_MARKER)
        ) {
            FirewallResult(true, "网络限制已关闭，所有应用恢复联网。")
        } else {
            FirewallResult(false, result.message.ifBlank { "无法恢复网络规则。" })
        }
    }

    private fun resolveAllowedUids(packageNames: Set<String>): List<Int> {
        val uids = mutableSetOf(Process.myUid())

        packageNames.forEach { packageName ->
            try {
                @Suppress("DEPRECATION")
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                uids += appInfo.uid
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Whitelisted package is no longer installed: $packageName", e)
            }
        }
        return uids.sorted()
    }

    private suspend fun runRootScript(script: String, timeoutMillis: Long): FirewallResult {
        return withContext(Dispatchers.IO) {
            var process: java.lang.Process? = null
            try {
                val runningProcess = ProcessBuilder("su", "-c", script)
                    .redirectErrorStream(true)
                    .start()
                process = runningProcess

                val output = StringBuilder()
                val reader = Thread {
                    runningProcess.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> output.appendLine(line) }
                    }
                }.apply { start() }

                val finished = runningProcess.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
                if (!finished) {
                    runningProcess.destroyForcibly()
                    reader.join(1_000L)
                    return@withContext FirewallResult(false, "Root 命令执行超时。")
                }

                reader.join(1_000L)
                val text = output.toString().trim().take(MAX_OUTPUT_LENGTH)
                if (runningProcess.exitValue() == 0) {
                    FirewallResult(true, text)
                } else {
                    FirewallResult(false, text.ifBlank { "Root 命令执行失败。" })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Root command failed", e)
                FirewallResult(false, "Root 不可用或已拒绝授权：${e.message.orEmpty()}")
            } finally {
                process?.destroy()
            }
        }
    }

    private companion object {
        const val TAG = "FirewallManager"
        const val ROOT_CHECK_TIMEOUT_MILLIS = 10_000L
        const val APPLY_TIMEOUT_MILLIS = 30_000L
        const val MAX_OUTPUT_LENGTH = 1_200
    }
}