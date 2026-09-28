package com.example

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Category
import com.example.model.DuplicateGroup
import com.example.model.FileItem
import com.example.scanner.ScannerService
import com.example.util.ScanHistoryItem
import com.example.util.ScanHistoryManager
import com.example.util.ScanMode
import com.example.util.SettingsManager
import com.example.util.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(
        val progress: Float,
        val message: String,
        val category: Category? = null,
        val filesScanned: Int = 0,
        val duplicatesFound: Int = 0,
        val recoverableBytes: Long = 0L,
        val scanMode: ScanMode = ScanMode.QUICK
    ) : ScanState()
    data class Complete(
        val duplicateGroups: List<DuplicateGroup>,
        val totalFilesScanned: Int = 0
    ) : ScanState()
}

sealed class DeletionState {
    object Idle : DeletionState()
    data class InProgress(val current: Int, val total: Int) : DeletionState()
    data class Complete(val deletedCount: Int, val recoveredBytes: Long) : DeletionState()
    data class Error(val message: String, val deletedCount: Int, val failedCount: Int) : DeletionState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val scannerService = ScannerService(application)
    val settingsManager = SettingsManager(application)
    val scanHistoryManager = ScanHistoryManager(application)

    private var scanJob: Job? = null
    
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _lastScanTime = MutableStateFlow<Long?>(null)
    val lastScanTime: StateFlow<Long?> = _lastScanTime.asStateFlow()

    private val _deletionState = MutableStateFlow<DeletionState>(DeletionState.Idle)
    val deletionState: StateFlow<DeletionState> = _deletionState.asStateFlow()

    private val _scanHistory = MutableStateFlow<List<ScanHistoryItem>>(scanHistoryManager.getHistory())
    val scanHistory: StateFlow<List<ScanHistoryItem>> = _scanHistory.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settingsManager.themeMode
    val scanMode: StateFlow<ScanMode> = settingsManager.scanMode
    val hasCompletedOnboarding: StateFlow<Boolean> = settingsManager.hasCompletedOnboarding

    fun setDeletionState(state: DeletionState) {
        _deletionState.value = state
    }
    
    // Set of URIs currently selected for deletion
    private val _selectedForDeletion = MutableStateFlow<Set<Uri>>(emptySet())
    val selectedForDeletion: StateFlow<Set<Uri>> = _selectedForDeletion.asStateFlow()
    
    var pendingDeleteUris: List<Uri> = emptyList()

    fun getFileItem(uri: Uri): FileItem? {
        val state = _scanState.value as? ScanState.Complete ?: return null
        for (group in state.duplicateGroups) {
            val found = group.items.find { it.uri == uri }
            if (found != null) return found
        }
        return null
    }

    fun scanDevice(requestedMode: ScanMode? = null) {
        // Cancel any previous ongoing scan job before starting a new one
        scanJob?.cancel()
        val actualMode = requestedMode ?: settingsManager.scanMode.value
        val enabledCategories = settingsManager.getEnabledCategories()

        scanJob = viewModelScope.launch {
            scannerService.scanDevice(mode = actualMode, enabledCategories = enabledCategories).collect { progress ->
                if (progress.results != null) {
                    val now = System.currentTimeMillis()
                    _lastScanTime.value = now
                    _scanState.value = ScanState.Complete(progress.results, progress.filesScannedCount)
                    
                    // Automatically mark duplicates, keeping the FIRST item in each group safe
                    val autoMarked = mutableSetOf<Uri>()
                    for (group in progress.results) {
                        for (i in 1 until group.items.size) {
                            autoMarked.add(group.items[i].uri)
                        }
                    }
                    _selectedForDeletion.value = autoMarked

                    // Record in scan history
                    val dupsCount = progress.results.sumOf { it.items.size - 1 }
                    val recBytes = progress.results.sumOf { it.sizePerItem * (it.items.size - 1) }
                    scanHistoryManager.recordScan(
                        filesScanned = progress.filesScannedCount,
                        duplicatesFound = dupsCount,
                        recoverableBytes = recBytes,
                        scanMode = if (actualMode == ScanMode.QUICK) "Quick Scan" else "Deep Scan"
                    )
                    _scanHistory.value = scanHistoryManager.getHistory()
                } else {
                    _scanState.value = ScanState.Scanning(
                        progress = progress.progress,
                        message = progress.message,
                        category = progress.currentCategory,
                        filesScanned = progress.filesScannedCount,
                        duplicatesFound = progress.duplicatesDiscoveredCount,
                        recoverableBytes = progress.recoverableBytesDiscovered,
                        scanMode = actualMode
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanState.Idle
    }

    fun toggleSelection(uri: Uri, groupItems: List<Uri>) {
        val currentSelected = _selectedForDeletion.value.toMutableSet()
        if (currentSelected.contains(uri)) {
            currentSelected.remove(uri)
        } else {
            // Check if adding this would mean ALL items in the group are selected
            val othersSelected = groupItems.count { it != uri && currentSelected.contains(it) }
            if (othersSelected == groupItems.size - 1) {
                // We are about to select the last remaining item. 
                // Unselect one other to keep it safe (e.g., the first one that is currently selected)
                val oneToUnselect = groupItems.firstOrNull { it != uri && currentSelected.contains(it) }
                if (oneToUnselect != null) {
                    currentSelected.remove(oneToUnselect)
                }
            }
            currentSelected.add(uri)
        }
        _selectedForDeletion.value = currentSelected
    }

    fun clearSelections() {
        _selectedForDeletion.value = emptySet()
    }

    fun clearHistory() {
        scanHistoryManager.clearHistory()
        _scanHistory.value = emptyList()
    }
    
    fun removeDeletedItems(deletedUris: List<Uri>) {
        val currentState = _scanState.value
        if (currentState is ScanState.Complete) {
            val deletedSet = deletedUris.toHashSet()
            val updatedGroups = currentState.duplicateGroups.mapNotNull { group ->
                val remainingItems = group.items.filter { it.uri !in deletedSet }
                if (remainingItems.size > 1) {
                    group.copy(items = remainingItems)
                } else {
                    null // If only 1 or 0 items left, it's no longer a duplicate group
                }
            }
            _scanState.value = ScanState.Complete(updatedGroups, currentState.totalFilesScanned)
            
            // Clean up selections
            _selectedForDeletion.value = _selectedForDeletion.value.filter { it !in deletedSet }.toSet()
        }
    }
}
