package com.example.apps

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FirewallScriptBuilderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun applyScriptWhitelistsConfiguredUidsAndDropsEverythingElse() {
        val script = FirewallScriptBuilder.buildApplyScript(listOf(12345))

        assertTrue(script.contains("--uid-owner 0-9999 -j RETURN"))
        assertTrue(script.contains("--uid-owner 12345 -j RETURN"))
        assertTrue(script.contains("--uid-owner 10000-2147483647 -j DROP"))
        assertTrue(script.contains("iptables -w -I OUTPUT 1 -j DETOX_WHITELIST"))
        assertTrue(script.contains("ip6tables -w -I OUTPUT 1 -j DETOX_WHITELIST"))
        assertTrue(script.contains("fail IP6TABLES_RULE_FAILED"))
    }

    @Test
    fun applyScriptExecutesAndHooksBothIpVersions() {
        assumeFalse(isWindows())
        val result = runFirewallScript(
            script = FirewallScriptBuilder.buildApplyScript(listOf(12345)),
            failIp6UidRule = false
        )

        assertEquals(
            "exit=${result.exitCode}\noutput=${result.output}\nlog=${result.log}",
            0,
            result.exitCode
        )
        assertTrue(result.output.contains(FirewallScriptBuilder.FIREWALL_OK_MARKER))
        assertTrue(result.log.contains("--uid-owner 12345 -j RETURN"))
        assertTrue(result.log.contains("--uid-owner 10000-2147483647 -j DROP"))
        assertTrue(result.log.contains("ip6tables -w -I OUTPUT 1 -j DETOX_WHITELIST"))
        assertTrue(result.ipv4State.exists())
        assertTrue(result.ipv6State.exists())
    }

    @Test
    fun ipv6FailureRollsBackIpv4AndIpv6Registrations() {
        assumeFalse(isWindows())
        val result = runFirewallScript(
            script = FirewallScriptBuilder.buildApplyScript(listOf(12345)),
            failIp6UidRule = true
        )

        assertTrue(result.exitCode != 0)
        assertTrue(result.output.contains("IP6TABLES_RULE_FAILED"))
        assertFalse(result.ipv4State.exists())
        assertFalse(result.ipv6State.exists())
        assertTrue(result.log.contains("iptables -w -D OUTPUT -j DETOX_WHITELIST"))
    }

    private fun runFirewallScript(
        script: String,
        failIp6UidRule: Boolean
    ): ScriptResult {
        val root = temporaryFolder.newFolder("script-${System.nanoTime()}")
        val log = File(root, "commands.log")
        val ipv4State = File(root, "ipv4.state")
        val ipv6State = File(root, "ipv6.state")

        val wrapper = buildString {
            append("id() { echo 0; }\n")
            append(
                fakeFirewallFunction(
                    name = "iptables",
                    stateVariable = "FAKE_IPTABLES_STATE",
                    failUidRule = false
                )
            )
            append(
                fakeFirewallFunction(
                    name = "ip6tables",
                    stateVariable = "FAKE_IP6TABLES_STATE",
                    failUidRule = failIp6UidRule
                )
            )
            append(script)
        }.replace("\r\n", "\n")

        val scriptFile = File(root, "firewall-test.sh").apply { writeText(wrapper) }
        val processBuilder = ProcessBuilder("/bin/sh", "-x", scriptFile.absolutePath)
        processBuilder.environment()["FAKE_LOG"] = log.absolutePath
        processBuilder.environment()["FAKE_IPTABLES_STATE"] = ipv4State.absolutePath
        processBuilder.environment()["FAKE_IP6TABLES_STATE"] = ipv6State.absolutePath
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return ScriptResult(
            exitCode = exitCode,
            output = output,
            log = if (log.exists()) log.readText() else "",
            ipv4State = ipv4State,
            ipv6State = ipv6State
        )
    }

    private fun fakeFirewallFunction(
        name: String,
        stateVariable: String,
        failUidRule: Boolean
    ): String {
        val failureCase = if (failUidRule) {
            """
    *"--uid-owner 12345 -j RETURN"*)
      return 1
      ;;
"""
        } else {
            ""
        }
        return """
$name() {
  echo "$name ${'$'}*" >> "${'$'}FAKE_LOG"
  state="${'$'}{$stateVariable}"
  case "${'$'}*" in
$failureCase    *"-C OUTPUT -j DETOX_WHITELIST"*)
      [ -f "${'$'}state" ] && return 0 || return 1
      ;;
    *"-I OUTPUT 1 -j DETOX_WHITELIST"*)
      : > "${'$'}state"
      return 0
      ;;
    *"-D OUTPUT -j DETOX_WHITELIST"*)
      rm -f "${'$'}state"
      return 0
      ;;
    *)
      return 0
      ;;
  esac
}
"""
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").orEmpty().lowercase().contains("win")
    }

    private data class ScriptResult(
        val exitCode: Int,
        val output: String,
        val log: String,
        val ipv4State: File,
        val ipv6State: File
    )
}