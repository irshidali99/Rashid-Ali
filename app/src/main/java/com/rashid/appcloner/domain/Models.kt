package com.rashid.appcloner.domain

import android.graphics.drawable.Drawable

/** Information obtained from PackageManager; the icon remains device-local. */
data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val icon: Drawable,
    val isSystemApp: Boolean
)

enum class AppTheme { LIGHT, DARK, SYSTEM }

data class UserPreferences(
    val defaultCloneName: String = "Clone",
    val automaticNumbering: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val theme: AppTheme = AppTheme.DARK
)
