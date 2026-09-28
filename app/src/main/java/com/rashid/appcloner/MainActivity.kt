package com.rashid.appcloner

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rashid.appcloner.data.InstalledAppRepository
import com.rashid.appcloner.data.PreferencesStore
import com.rashid.appcloner.domain.AppTheme
import com.rashid.appcloner.domain.CloneCompatibilityChecker
import com.rashid.appcloner.domain.CloneNameRules
import com.rashid.appcloner.domain.Compatibility
import com.rashid.appcloner.domain.InstalledApp
import com.rashid.appcloner.domain.UserPreferences
import com.rashid.appcloner.ui.AppClonerTheme

private val Ink = Color(0xFF0A1225)
private val Lavender = Color(0xFFAAA1FF)
private val Mint = Color(0xFF4DDBB0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = PreferencesStore(this)
        setContent {
            var prefs by remember { mutableStateOf(store.read()) }
            AppClonerTheme(prefs.theme) {
                ClonerApp(
                    prefs = prefs,
                    onPreferencesChange = { prefs = it; store.update(it) },
                    appsLoader = { InstalledAppRepository(this).loadLaunchableApps() },
                    onLaunch = { app ->
                        val launch = packageManager.getLaunchIntentForPackage(app.packageName)
                        if (launch != null) runCatching { startActivity(launch) }
                            .onFailure { Toast.makeText(this, "Could not open ${app.label}.", Toast.LENGTH_SHORT).show() }
                        else Toast.makeText(this, "No launch activity found.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

private enum class Tab(val title: String) { HOME("Home"), CLONES("My Clones"), SETTINGS("Settings") }
private enum class AppFilter(val title: String) { ALL("All Apps"), COMPATIBLE("Cloneable"), CLONED("Cloned") }

@Composable
private fun ClonerApp(
    prefs: UserPreferences,
    onPreferencesChange: (UserPreferences) -> Unit,
    appsLoader: () -> List<InstalledApp>,
    onLaunch: (InstalledApp) -> Unit
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var showPrivacy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) { runCatching(appsLoader) }
            result.onSuccess {
                apps = it
                scanError = null
            }.onFailure {
                apps = emptyList()
                scanError = it.localizedMessage ?: "Android could not read the launchable app list."
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, contentColor = Color.White) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(when (item) {
                                Tab.HOME -> Icons.Default.Apps
                                Tab.CLONES -> Icons.Default.Shield
                                Tab.SETTINGS -> Icons.Default.Settings
                            }, contentDescription = item.title)
                        },
                        label = { Text(item.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = Color(0xFF493AE4),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header(tab, onRefresh = { refresh() })
            when (tab) {
                Tab.HOME -> HomeContent(
                    apps = apps,
                    search = search,
                    onSearch = { search = it },
                    filter = filter,
                    onFilter = { filter = it },
                    loading = loading,
                    scanError = scanError,
                    onSelect = { selectedApp = it },
                    onOpenClones = { tab = Tab.CLONES },
                    onRefresh = { refresh() }
                )
                Tab.CLONES -> ClonesContent(onBrowse = { tab = Tab.HOME; filter = AppFilter.ALL })
                Tab.SETTINGS -> SettingsContent(
                    prefs = prefs,
                    onChange = onPreferencesChange,
                    onPrivacy = { showPrivacy = true }
                )
            }
        }
    }
    selectedApp?.let { app ->
        AppDetailsDialog(app = app, prefs = prefs, onDismiss = { selectedApp = null }, onLaunch = onLaunch)
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Privacy", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "App Cloner reads the labels, icons, package names, and versions of apps with a launcher activity so it can display your app list. This information stays on this device. No APKs, credentials, or app data are uploaded. This release does not create clone APKs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun Header(tab: Tab, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF5144E9)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Apps, null, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("App Cloner", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(tab.title, color = Color(0xFFAEB8D0), fontSize = 12.sp)
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Tune, "Refresh app list", tint = Color.White) }
        Box(Modifier.size(36.dp).clip(CircleShape).background(Lavender), contentAlignment = Alignment.Center) {
            Text("A", color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeContent(
    apps: List<InstalledApp>, search: String, onSearch: (String) -> Unit,
    filter: AppFilter, onFilter: (AppFilter) -> Unit, loading: Boolean, scanError: String?,
    onSelect: (InstalledApp) -> Unit, onOpenClones: () -> Unit, onRefresh: () -> Unit
) {
    val matchingApps = remember(apps, search, filter) {
        apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
            .let { if (filter == AppFilter.ALL) it else emptyList() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = Mint)
                        Spacer(Modifier.width(8.dp))
                        Text("On-device app list", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Only apps with a launcher icon are shown. No app data is accessed.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Mint))
                        Spacer(Modifier.width(8.dp))
                        Text("${apps.size} installed apps found", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("APK cloning unavailable", color = Lavender, fontSize = 11.sp)
                    }
                }
            }
        }
        item { SectionTitle("INSTALLED APPS", "${matchingApps.size} shown") }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = search, onValueChange = onSearch, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (search.isEmpty()) Text("Search installed apps…", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppFilter.entries.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { onFilter(item) },
                        label = { Text(item.title, maxLines = 1, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF5144E9), selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surface, labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null
                    )
                }
            }
        }
        if (scanError != null) {
            item {
                EmptyMessage(
                    title = "App scan failed",
                    body = scanError,
                    action = "Retry",
                    onAction = onRefresh
                )
            }
        } else if (filter == AppFilter.COMPATIBLE) {
            item {
                InfoCard(
                    title = "No apps verified as cloneable",
                    body = "This release does not transform or re-sign APKs, so it will not label arbitrary installed apps as cloneable.",
                    icon = Icons.Default.Info
                )
            }
        } else if (filter == AppFilter.CLONED) {
            item { EmptyClonesCard(onBrowse = onOpenClones) }
        } else if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Lavender) } }
        } else if (matchingApps.isEmpty()) {
            item {
                EmptyMessage(
                    title = if (search.isBlank()) "No launchable apps found" else "No search results",
                    body = if (search.isBlank()) "Try refreshing the app list." else "Try another app name or package identifier.",
                    action = if (search.isBlank()) "Refresh" else null,
                    onAction = onRefresh
                )
            }
        } else {
            items(matchingApps, key = { it.packageName }) { app ->
                AppRow(app = app, onClick = { onSelect(app) })
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(app.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${app.packageName}  ·  v${app.versionName}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text("DETAILS", color = Lavender, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppIcon(icon: Drawable, modifier: Modifier = Modifier) {
    val bitmap = remember(icon) { runCatching { icon.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    if (bitmap != null) Image(bitmap, null, modifier.clip(RoundedCornerShape(12.dp)))
    else Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Apps, null, tint = Lavender)
    }
}

@Composable
private fun AppDetailsDialog(app: InstalledApp, prefs: UserPreferences, onDismiss: () -> Unit, onLaunch: (InstalledApp) -> Unit) {
    var name by remember(app.packageName, prefs.defaultCloneName, prefs.automaticNumbering) {
        mutableStateOf("${app.label} ${prefs.defaultCloneName}${if (prefs.automaticNumbering) " 1" else ""}")
    }
    val nameError = CloneNameRules.validate(name)
    val compatibility = CloneCompatibilityChecker.assess(app.packageName)
    val unsupportedReason = (compatibility as? Compatibility.Unsupported)?.reason
        ?: "Compatibility has not been assessed."
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("App details", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.icon, Modifier.size(48.dp)); Spacer(Modifier.width(12.dp))
                    Column {
                        Text(app.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Version ${app.versionName}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                Text("${app.packageName}${if (app.isSystemApp) " · System app" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Proposed display name") },
                    isError = nameError != null, supportingText = { Text(nameError ?: "For reference only; no APK will be generated.") },
                    singleLine = true
                )
                Surface(color = Color(0xFF30212D), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFF929D), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$unsupportedReason No changes will be made to the original app.",
                            color = Color(0xFFFFCCD1), fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onLaunch(app); onDismiss() }) {
                Icon(Icons.Default.Launch, null); Spacer(Modifier.width(6.dp)); Text("Open original")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ClonesContent(onBrowse: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(74.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Apps, null, tint = Lavender, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("No clones yet", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This app currently cannot generate installable APK clones. We won't display demo entries or claim a clone was installed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onBrowse, colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink)) {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Browse installed apps")
                    }
                    Text("No APK was generated or installed.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        item {
            InfoCard(
                title = "Why no clone records?",
                body = "A different package identity usually requires modifying and re-signing an APK. Re-signed apps may break integrity checks, authentication, licensing, or services. This build does not attempt those operations.",
                icon = Icons.Default.Info
            )
        }
        item { SectionTitle("PRIVACY", "On-device only") }
        item {
            InfoCard(
                title = "Original applications stay untouched",
                body = "The app list is read through Android's launcher-app visibility query. App-private data, accounts, and credentials are never read.",
                icon = Icons.Default.Shield
            )
        }
    }
}

@Composable
private fun SettingsContent(prefs: UserPreferences, onChange: (UserPreferences) -> Unit, onPrivacy: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("GENERAL ENGINE", "3 options") }
        item {
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) {
                OutlinedTextField(
                    value = prefs.defaultCloneName,
                    onValueChange = { value -> onChange(prefs.copy(defaultCloneName = value.take(40))) },
                    label = { Text("Default clone name") }, supportingText = { Text("Used as a suggested name only") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
                SettingSwitch("Automatically number clones", "Increment suggested name suffix", prefs.automaticNumbering) {
                    onChange(prefs.copy(automaticNumbering = it))
                }
                SettingSwitch("Confirm before delete", "Ask before destructive actions", prefs.confirmBeforeDelete) {
                    onChange(prefs.copy(confirmBeforeDelete = it))
                }
            }
        }
        item { SectionTitle("APPEARANCE", "") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Text("App theme", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = prefs.theme == theme,
                            onClick = { onChange(prefs.copy(theme = theme)) },
                            label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5144E9), selectedLabelColor = Color.White,
                                containerColor = Ink, labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ), border = null
                        )
                    }
                }
            }
        }
        item { SectionTitle("ABOUT & SYSTEM", "Version 0.1.0") }
        item {
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)) {
                InfoRow("App Cloner", "Native Android · On-device app list", Icons.Default.Apps)
                HorizontalDivider(color = Color(0xFF29344A))
                InfoRow("Android compatibility", "Android 8.0 (API 26) and later", Icons.Default.Info)
                HorizontalDivider(color = Color(0xFF29344A))
                Row(Modifier.fillMaxWidth().clickable(onClick = onPrivacy).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = Lavender)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Privacy information", color = MaterialTheme.colorScheme.onSurface)
                        Text("What this app reads and why", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = Color(0xFF29344A))
                InfoRow("Clone engine", "APK transformation not available", Icons.Default.Warning)
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun InfoRow(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Lavender)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Lavender, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyClonesCard(onBrowse: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, null, tint = Lavender, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text("No clones to show", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text("There are no verified installed clones managed by this app.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        TextButton(onClick = onBrowse) { Text("See My Clones", color = Lavender) }
    }
}

@Composable
private fun EmptyMessage(title: String, body: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Lavender) }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFFD5D9E8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing.isNotBlank()) Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}
