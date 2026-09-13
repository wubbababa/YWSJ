package com.example.apps

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.MainActivity
import com.example.ui.theme.MinimalBg
import com.example.ui.theme.MinimalPurpleLight
import com.example.ui.theme.MinimalPurplePrimary
import com.example.ui.theme.MinimalTextMain
import com.example.ui.theme.MinimalTextMuted
import com.example.ui.theme.MinimalTileBg
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LauncherScreen()
            }
        }
    }
}

@Composable
private fun LauncherScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val store = remember { AppControlStore(appContext) }
    val repository = remember { InstalledAppRepository(appContext) }
    val coroutineScope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<LauncherAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("") }
    var hasUsageAccess by remember { mutableStateOf(repository.hasUsageAccess()) }
    var isDefaultHome by remember { mutableStateOf(context.isDefaultHome()) }

    fun reloadApps() {
        coroutineScope.launch {
            isLoading = true
            try {
                apps = repository.loadLauncherApps(store)
                statusMessage = ""
            } catch (e: Exception) {
                apps = emptyList()
                statusMessage = "读取应用列表失败：${e.message.orEmpty()}"
            } finally {
                hasUsageAccess = repository.hasUsageAccess()
                isDefaultHome = context.isDefaultHome()
                isLoading = false
            }
        }
    }

    val homeRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultHome = context.isDefaultHome()
        reloadApps()
    }

    LifecycleResumeEffect(Unit) {
        reloadApps()
        onPauseOrDispose { }
    }

    BackHandler(enabled = isDefaultHome) {
        // A home screen must not finish itself when the user presses Back.
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
                    .padding(top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = MinimalPurplePrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "常用优先",
                        color = MinimalTextMain,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "按启动次数和使用时长自动排序",
                        color = MinimalTextMuted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { openNetworkWhitelist(context) }) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "联网白名单",
                        tint = MinimalPurplePrimary
                    )
                }
                IconButton(onClick = { openDetoxSettings(context) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = MinimalPurplePrimary
                    )
                }
            }

            if (!isDefaultHome) {
                HomeRoleCard(
                    onRequestHomeRole = {
                        requestHomeRole(context, homeRoleLauncher)
                    }
                )
            }

            if (!hasUsageAccess) {
                UsageAccessCard(
                    onRequestUsageAccess = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )
            }

            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MinimalPurplePrimary)
                    }
                }

                apps.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "未找到可启动的应用",
                            color = MinimalTextMuted
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 88.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = apps,
                            key = { it.packageName }
                        ) { app ->
                            LauncherAppTile(
                                app = app,
                                onClick = {
                                    launchApp(context, store, app)
                                    statusMessage = ""
                                    reloadApps()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRoleCard(onRequestHomeRole: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalPurpleLight)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "将本页设为默认桌面",
                color = MinimalTextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "设为 Home 后，每次返回桌面都会看到按使用频率排序的应用。",
                color = MinimalTextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onRequestHomeRole) {
                Text("设为默认桌面")
            }
        }
    }
}

@Composable
private fun UsageAccessCard(onRequestUsageAccess: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MinimalTileBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "本地启动次数已可用于排序；开启使用记录权限后，还会参考最近 30 天的启动次数和使用时长。",
                color = MinimalTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onRequestUsageAccess) {
                Text("开启")
            }
        }
    }
}

@Composable
private fun LauncherAppTile(
    app: LauncherAppInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MinimalTileBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(50.dp)
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = app.label,
            color = MinimalTextMain,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = frequencyLabel(app),
            color = MinimalTextMuted,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

private fun frequencyLabel(app: LauncherAppInfo): String {
    if (app.launchCount > 0) return "${app.launchCount} 次"
    if (app.usageTimeMillis <= 0L) return "未使用"
    val minutes = app.usageTimeMillis / 60_000L
    return when {
        minutes < 1L -> "<1 分钟"
        minutes < 60L -> "$minutes 分钟"
        else -> "${minutes / 60L} 小时"
    }
}

private fun launchApp(
    context: Context,
    store: AppControlStore,
    app: LauncherAppInfo
) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        ?: return
    store.recordAppLaunch(app.packageName)
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    context.startActivity(launchIntent)
}

private fun requestHomeRole(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            return
        }
    }
    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
}

private fun Context.isDefaultHome(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
    }
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName == packageName
}

private fun openNetworkWhitelist(context: Context) {
    context.startActivity(Intent(context, NetworkWhitelistActivity::class.java))
}

private fun openDetoxSettings(context: Context) {
    context.startActivity(Intent(context, MainActivity::class.java))
}
