package com.rashid.appcloner.data

import android.content.Context
import com.rashid.appcloner.domain.AppTheme
import com.rashid.appcloner.domain.UserPreferences

class PreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_cloner_preferences", Context.MODE_PRIVATE)

    fun read() = UserPreferences(
        defaultCloneName = prefs.getString(KEY_NAME, "Clone") ?: "Clone",
        automaticNumbering = prefs.getBoolean(KEY_NUMBERING, true),
        confirmBeforeDelete = prefs.getBoolean(KEY_CONFIRM_DELETE, true),
        theme = runCatching { AppTheme.valueOf(prefs.getString(KEY_THEME, "DARK") ?: "DARK") }
            .getOrDefault(AppTheme.DARK)
    )

    fun update(value: UserPreferences) {
        prefs.edit()
            .putString(KEY_NAME, value.defaultCloneName)
            .putBoolean(KEY_NUMBERING, value.automaticNumbering)
            .putBoolean(KEY_CONFIRM_DELETE, value.confirmBeforeDelete)
            .putString(KEY_THEME, value.theme.name)
            .apply()
    }

    private companion object {
        const val KEY_NAME = "default_name"
        const val KEY_NUMBERING = "automatic_numbering"
        const val KEY_CONFIRM_DELETE = "confirm_delete"
        const val KEY_THEME = "theme"
    }
}
