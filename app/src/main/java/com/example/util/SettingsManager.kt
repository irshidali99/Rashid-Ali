package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ScanMode {
    QUICK,
    DEEP
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("duplicate_remover_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_SCAN_PHOTOS = "scan_photos"
        private const val KEY_SCAN_AUDIO = "scan_audio"
        private const val KEY_SCAN_VIDEOS = "scan_videos"
        private const val KEY_SCAN_FILES = "scan_files"
        private const val KEY_SCAN_MODE = "scan_mode"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
    }

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _scanPhotos = MutableStateFlow(prefs.getBoolean(KEY_SCAN_PHOTOS, true))
    val scanPhotos: StateFlow<Boolean> = _scanPhotos.asStateFlow()

    private val _scanAudio = MutableStateFlow(prefs.getBoolean(KEY_SCAN_AUDIO, true))
    val scanAudio: StateFlow<Boolean> = _scanAudio.asStateFlow()

    private val _scanVideos = MutableStateFlow(prefs.getBoolean(KEY_SCAN_VIDEOS, true))
    val scanVideos: StateFlow<Boolean> = _scanVideos.asStateFlow()

    private val _scanFiles = MutableStateFlow(prefs.getBoolean(KEY_SCAN_FILES, true))
    val scanFiles: StateFlow<Boolean> = _scanFiles.asStateFlow()

    private val _scanMode = MutableStateFlow(loadScanMode())
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        return when (prefs.getString(KEY_THEME, "SYSTEM")) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    private fun loadScanMode(): ScanMode {
        return when (prefs.getString(KEY_SCAN_MODE, "QUICK")) {
            "DEEP" -> ScanMode.DEEP
            else -> ScanMode.QUICK
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setScanCategory(category: Category, enabled: Boolean) {
        when (category) {
            Category.PHOTOS -> {
                _scanPhotos.value = enabled
                prefs.edit().putBoolean(KEY_SCAN_PHOTOS, enabled).apply()
            }
            Category.AUDIO -> {
                _scanAudio.value = enabled
                prefs.edit().putBoolean(KEY_SCAN_AUDIO, enabled).apply()
            }
            Category.VIDEOS -> {
                _scanVideos.value = enabled
                prefs.edit().putBoolean(KEY_SCAN_VIDEOS, enabled).apply()
            }
            Category.FILES -> {
                _scanFiles.value = enabled
                prefs.edit().putBoolean(KEY_SCAN_FILES, enabled).apply()
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
        prefs.edit().putString(KEY_SCAN_MODE, mode.name).apply()
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        _hasCompletedOnboarding.value = completed
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
    }

    fun getEnabledCategories(): Set<Category> {
        val set = mutableSetOf<Category>()
        if (_scanPhotos.value) set.add(Category.PHOTOS)
        if (_scanAudio.value) set.add(Category.AUDIO)
        if (_scanVideos.value) set.add(Category.VIDEOS)
        if (_scanFiles.value) set.add(Category.FILES)
        // Ensure at least one category remains enabled
        return if (set.isEmpty()) Category.values().toSet() else set
    }
}
