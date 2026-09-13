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
        val script = buildApplyScript(allowedUids)
        val result = runRootScript(script, APPLY_TIMEOUT_MILLIS)
        return if (result.success && result.message.contains(FIREWALL_OK_MARKER)) {
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
        val result = runRootScript(buildDisableScript(), APPLY_TIMEOUT_MILLIS)
        return if (result.success && result.message.contains(FIREWALL_DISABLED_MARKER)) {
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

    private fun buildApplyScript(allowedUids: List<Int>): String = buildString {
        append("PATH=/sbin:/system/sbin:/system/bin:/system/xbin:\$PATH\n")
        append("cleanup_firewall() {\n")
        append("  cleanup_ok=1\n")
        append("  if command -v iptables >/dev/null 2>&1; then\n")
        append("    while iptables -w -C OUTPUT -j $CHAIN_NAME >/dev/null 2>&1; do\n")
        append("      iptables -w -D OUTPUT -j $CHAIN_NAME >/dev/null 2>&1 || { cleanup_ok=0; break; }\n")
        append("    done\n")
        append("    iptables -w -F $CHAIN_NAME >/dev/null 2>&1 || true\n")
        append("    iptables -w -X $CHAIN_NAME >/dev/null 2>&1 || true\n")
        append("  fi\n")
        append("  if command -v ip6tables >/dev/null 2>&1; then\n")
        append("    while ip6tables -w -C OUTPUT -j $CHAIN_NAME >/dev/null 2>&1; do\n")
        append("      ip6tables -w -D OUTPUT -j $CHAIN_NAME >/dev/null 2>&1 || { cleanup_ok=0; break; }\n")
        append("    done\n")
        append("    ip6tables -w -F $CHAIN_NAME >/dev/null 2>&1 || true\n")
        append("    ip6tables -w -X $CHAIN_NAME >/dev/null 2>&1 || true\n")
        append("  fi\n")
        append("  [ \"\$cleanup_ok\" -eq 1 ]\n")
        append("}\n")
        append("fail() { cleanup_firewall >/dev/null 2>&1; echo \"\$1\"; exit 1; }\n")
        append("uid=\"\$(id -u 2>/dev/null)\"\n")
        append("[ \"\$uid\" = \"0\" ] || fail ROOT_CHECK_FAILED\n")
        append("command -v iptables >/dev/null 2>&1 || fail IPTABLES_MISSING\n")
        append("cleanup_firewall >/dev/null 2>&1 || fail IPTABLES_CLEANUP_FAILED\n")
        append("iptables -w -N $CHAIN_NAME 2>/dev/null || true\n")
        append("iptables -w -F $CHAIN_NAME || fail IPTABLES_FLUSH_FAILED\n")
        append("iptables -w -A $CHAIN_NAME -o lo -j RETURN || fail IPTABLES_RULE_FAILED\n")
        append("iptables -w -A $CHAIN_NAME -m owner --uid-owner 0-9999 -j RETURN || fail IPTABLES_RULE_FAILED\n")
        allowedUids.forEach { uid ->
            append("iptables -w -A $CHAIN_NAME -m owner --uid-owner $uid -j RETURN || fail IPTABLES_RULE_FAILED\n")
        }
        append("iptables -w -A $CHAIN_NAME -m owner --uid-owner 10000-2147483647 -j DROP || fail IPTABLES_RULE_FAILED\n")
        append("iptables -w -C OUTPUT -j $CHAIN_NAME 2>/dev/null || iptables -w -I OUTPUT 1 -j $CHAIN_NAME || fail IPTABLES_HOOK_FAILED\n")
        append("iptables -w -C OUTPUT -j $CHAIN_NAME >/dev/null 2>&1 || fail IPTABLES_VERIFY_FAILED\n")
        append("if command -v ip6tables >/dev/null 2>&1; then\n")
        append("  ip6tables -w -N $CHAIN_NAME 2>/dev/null || true\n")
        append("  ip6tables -w -F $CHAIN_NAME || fail IP6TABLES_FLUSH_FAILED\n")
        append("  ip6tables -w -A $CHAIN_NAME -o lo -j RETURN || fail IP6TABLES_RULE_FAILED\n")
        append("  ip6tables -w -A $CHAIN_NAME -m owner --uid-owner 0-9999 -j RETURN || fail IP6TABLES_RULE_FAILED\n")
        allowedUids.forEach { uid ->
            append("  ip6tables -w -A $CHAIN_NAME -m owner --uid-owner $uid -j RETURN || fail IP6TABLES_RULE_FAILED\n")
        }
        append("  ip6tables -w -A $CHAIN_NAME -m owner --uid-owner 10000-2147483647 -j DROP || fail IP6TABLES_RULE_FAILED\n")
        append("  ip6tables -w -C OUTPUT -j $CHAIN_NAME 2>/dev/null || ip6tables -w -I OUTPUT 1 -j $CHAIN_NAME || fail IP6TABLES_HOOK_FAILED\n")
        append("  ip6tables -w -C OUTPUT -j $CHAIN_NAME >/dev/null 2>&1 || fail IP6TABLES_VERIFY_FAILED\n")
        append("fi\n")
        append("echo $FIREWALL_OK_MARKER\n")
    }

    private fun buildDisableScript(): String = buildString {
        append("PATH=/sbin:/system/sbin:/system/bin:/system/xbin:\$PATH\n")
        append("disable_ok=1\n")
        append("if command -v iptables >/dev/null 2>&1; then\n")
        append("  while iptables -w -C OUTPUT -j $CHAIN_NAME >/dev/null 2>&1; do\n")
        append("    iptables -w -D OUTPUT -j $CHAIN_NAME >/dev/null 2>&1 || { disable_ok=0; break; }\n")
        append("  done\n")
        append("  iptables -w -F $CHAIN_NAME >/dev/null 2>&1 || true\n")
        append("  iptables -w -X $CHAIN_NAME >/dev/null 2>&1 || true\n")
        append("fi\n")
        append("if command -v ip6tables >/dev/null 2>&1; then\n")
        append("  while ip6tables -w -C OUTPUT -j $CHAIN_NAME >/dev/null 2>&1; do\n")
        append("    ip6tables -w -D OUTPUT -j $CHAIN_NAME >/dev/null 2>&1 || { disable_ok=0; break; }\n")
        append("  done\n")
        append("  ip6tables -w -F $CHAIN_NAME >/dev/null 2>&1 || true\n")
        append("  ip6tables -w -X $CHAIN_NAME >/dev/null 2>&1 || true\n")
        append("fi\n")
        append("[ \"\$disable_ok\" -eq 1 ] || { echo IPTABLES_DISABLE_FAILED; exit 1; }\n")
        append("echo $FIREWALL_DISABLED_MARKER\n")
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
        const val CHAIN_NAME = "DETOX_WHITELIST"
        const val ROOT_CHECK_TIMEOUT_MILLIS = 10_000L
        const val APPLY_TIMEOUT_MILLIS = 30_000L
        const val MAX_OUTPUT_LENGTH = 1_200
        const val FIREWALL_OK_MARKER = "DETOX_FIREWALL_OK"
        const val FIREWALL_DISABLED_MARKER = "DETOX_FIREWALL_DISABLED"
    }
}
