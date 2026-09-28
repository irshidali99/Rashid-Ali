package com.rashid.appcloner.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.rashid.appcloner.domain.InstalledApp

class InstalledAppRepository(private val context: Context) {
    @Suppress("DEPRECATION")
    fun loadLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(launcher, 0)
        }
        return resolved.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .mapNotNull { info ->
                runCatching {
                    val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                        pm.getPackageInfo(info.packageName, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        pm.getPackageInfo(info.packageName, 0)
                    }
                    InstalledApp(
                        label = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName,
                        versionName = packageInfo.versionName ?: "Unknown version",
                        icon = pm.getApplicationIcon(info),
                        isSystemApp = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
