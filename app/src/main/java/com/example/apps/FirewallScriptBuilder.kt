package com.example.apps

internal object FirewallScriptBuilder {
    const val CHAIN_NAME = "DETOX_WHITELIST"
    const val FIREWALL_OK_MARKER = "DETOX_FIREWALL_OK"
    const val FIREWALL_DISABLED_MARKER = "DETOX_FIREWALL_DISABLED"

    fun buildApplyScript(allowedUids: List<Int>): String = buildString {
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

    fun buildDisableScript(): String = buildString {
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
}