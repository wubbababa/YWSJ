package com.example.apps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.ui.theme.MinimalBg
import com.example.ui.theme.MinimalPurpleLight
import com.example.ui.theme.MinimalPurplePrimary
import com.example.ui.theme.MinimalTextMain
import com.example.ui.theme.MinimalTextMuted
import com.example.ui.theme.MinimalTileBg
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class NetworkWhitelistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                NetworkWhitelistScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun NetworkWhitelistScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val store = remember { AppControlStore(appContext) }
    val repository = remember { InstalledAppRepository(appContext) }
    val firewallManager = remember { FirewallManager(appContext) }
    val coroutineScope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<NetworkAppInfo>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf(store.getNetworkWhitelist()) }
    var firewallEnabled by remember { mutableStateOf(store.isFirewallEnabled()) }
    var hasPendingChanges by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isApplying by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf(store.getLastFirewallMessage()) }
    var statusIsError by remember { mutableStateOf(store.getLastFirewallStatusIsError()) }

    LifecycleResumeEffect(Unit) {
        coroutineScope.launch {
            isLoading = true
            try {
                apps = repository.loadNetworkApps()
            } catch (e: Exception) {
                statusMessage = "读取应用列表失败：${e.message.orEmpty()}"
                statusIsError = true
            } finally {
                isLoading = false
            }

            firewallEnabled = store.isFirewallEnabled()
            if (!hasPendingChanges && !isApplying) {
                selectedPackages = store.getNetworkWhitelist()
            }
            if (statusMessage.isBlank()) {
                statusMessage = store.getLastFirewallMessage()
                statusIsError = store.getLastFirewallStatusIsError()
            }
        }
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MinimalBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = MinimalTextMain
                    )
                }
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MinimalPurplePrimary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.size(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "联网白名单",
                        color = MinimalTextMain,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "只允许勾选的应用访问网络",
                        color = MinimalTextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            FirewallStatusCard(
                enabled = firewallEnabled,
                hasPendingChanges = hasPendingChanges,
                isApplying = isApplying,
                statusMessage = statusMessage,
                statusIsError = statusIsError,
                onApply = {
                    if (!isApplying) {
                        coroutineScope.launch {
                            val packagesToApply = selectedPackages
                            isApplying = true
                            statusMessage = "正在请求 Root 权限并应用规则..."
                            statusIsError = false

                            val result = firewallManager.applyWhitelist(packagesToApply)
                            statusMessage = result.message
                            statusIsError = !result.success
                            store.setLastFirewallStatus(result.message, !result.success)

                            if (result.success) {
                                store.saveActiveFirewallState(true, packagesToApply)
                                firewallEnabled = true
                                selectedPackages = packagesToApply
                                hasPendingChanges = false
                            }
                            isApplying = false
                        }
                    }
                },
                onDisable = {
                    if (!isApplying) {
                        coroutineScope.launch {
                            isApplying = true
                            statusMessage = "正在关闭网络限制..."
                            statusIsError = false

                            val result = firewallManager.disable()
                            statusMessage = result.message
                            statusIsError = !result.success
                            store.setLastFirewallStatus(result.message, !result.success)

                            if (result.success) {
                                store.setFirewallEnabled(false)
                                firewallEnabled = false
                                hasPendingChanges = false
                            }
                            isApplying = false
                        }
                    }
                }
            )

            Text(
                text = "已选择 ${selectedPackages.size} 个应用",
                color = MinimalTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MinimalPurplePrimary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = apps,
                        key = { it.packageName }
                    ) { app ->
                        NetworkAppRow(
                            app = app,
                            checked = selectedPackages.contains(app.packageName),
                            enabled = !isApplying,
                            onCheckedChange = { checked ->
                                selectedPackages = if (checked) {
                                    selectedPackages + app.packageName
                                } else {
                                    selectedPackages - app.packageName
                                }
                                hasPendingChanges = true
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FirewallStatusCard(
    enabled: Boolean,
    hasPendingChanges: Boolean,
    isApplying: Boolean,
    statusMessage: String,
    statusIsError: Boolean,
    onApply: () -> Unit,
    onDisable: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MinimalPurpleLight else MinimalTileBg
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when {
                    enabled && hasPendingChanges -> "白名单已开启，有修改待应用"
                    enabled -> "白名单已开启"
                    hasPendingChanges -> "白名单未开启，选择待应用"
                    else -> "白名单未开启"
                },
                color = MinimalTextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "此功能通过 Root 权限按 UID 写入 iptables/ip6tables 规则。系统核心进程和本应用始终放行；其他应用（包括系统应用）只有勾选后才会放行。",
                color = MinimalTextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            if (statusMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text = statusMessage,
                    color = if (statusIsError) MaterialTheme.colorScheme.error else MinimalTextMain,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onApply,
                    enabled = !isApplying
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (enabled) "应用更改" else "开启白名单")
                    }
                }
                OutlinedButton(
                    onClick = onDisable,
                    enabled = !isApplying && enabled
                ) {
                    Text("关闭限制")
                }
            }
        }
    }
}

@Composable
private fun NetworkAppRow(
    app: NetworkAppInfo,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MinimalTileBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.size(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                color = MinimalTextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (app.isSystem) "系统应用" else app.packageName,
                color = MinimalTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
