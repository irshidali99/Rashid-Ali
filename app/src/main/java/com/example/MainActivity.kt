package com.example

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.Category
import com.example.model.FileItem
import com.example.util.ScanMode
import com.example.ui.DuplicateListScreen
import com.example.ui.HomeScreen
import com.example.ui.OnboardingDialog
import com.example.ui.ScanHistoryDialog
import com.example.ui.ScanningScreen
import com.example.ui.SettingsDialog
import com.example.ui.StoragePermissionDialog
import com.example.ui.formatSize
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Pre-cached file info for deletion verification and detailed debug logging
    private val pendingFileItems = mutableMapOf<Uri, FileItem>()
    private val pendingPaths = mutableMapOf<Uri, String?>()

    // FIX: Batched system-delete queue. MediaStore.createDeleteRequest is limited
    // (~100 URIs per request due to Binder limits), so large selections are split
    // into sequential batches instead of silently dropping everything past 100.
    private val mediaBatchQueue: ArrayDeque<List<Uri>> = ArrayDeque()
    private val batchDeletedUris = mutableListOf<Uri>()
    private var batchFailedCount = 0
    private var batchDirectDeletedCount = 0
    private var batchDirectFailedCount = 0
    private var batchTotalMediaCount = 0
    private var batchDirectRecoveredBytes = 0L

    private fun clearBatchState() {
        mediaBatchQueue.clear()
        batchDeletedUris.clear()
        batchFailedCount = 0
        batchDirectDeletedCount = 0
        batchDirectFailedCount = 0
        batchTotalMediaCount = 0
        batchDirectRecoveredBytes = 0L
        viewModel.pendingDeleteUris = emptyList()
        pendingPaths.clear()
        pendingFileItems.clear()
    }

    // Pending URIs for Android 10 RecoverableSecurityException flow
    private var pendingRecoverableUri: Uri? = null

    // Launcher for Android 11+ MediaStore.createDeleteRequest.
    // Handles one batch at a time and automatically continues with the next
    // queued batch until every selected media file has been processed.
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pendingUris = viewModel.pendingDeleteUris.toList()
            lifecycleScope.launch(Dispatchers.IO) {
                val trulyDeleted = mutableListOf<Uri>()
                var failureCount = 0

                for (uri in pendingUris) {
                    val path = pendingPaths[uri]
                    try {
                        contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        // Ignore
                    }

                    if (path != null) {
                        try {
                            val file = File(path)
                            if (file.exists()) {
                                file.delete()
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }

                    val stillExists = uriExistsWithStoredPath(this@MainActivity, uri, path)
                    if (!stillExists) {
                        trulyDeleted.add(uri)
                    } else {
                        failureCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    if (trulyDeleted.isNotEmpty()) {
                        viewModel.removeDeletedItems(trulyDeleted)
                    }
                    batchDeletedUris.addAll(trulyDeleted)
                    batchFailedCount += failureCount

                    // Report overall progress across all batches
                    val doneCount = batchDirectDeletedCount + batchDirectFailedCount +
                            batchDeletedUris.size + batchFailedCount
                    val totalCount = batchDirectDeletedCount + batchDirectFailedCount + batchTotalMediaCount
                    viewModel.setDeletionState(DeletionState.InProgress(doneCount, totalCount))

                    if (mediaBatchQueue.isNotEmpty()) {
                        launchNextMediaBatch()
                    } else {
                        finalizeBatchedDeletion()
                    }
                }
            }
        } else {
            // User cancelled: count the current + remaining queued batches as not deleted
            val remainingCount = viewModel.pendingDeleteUris.size + mediaBatchQueue.sumOf { it.size }
            batchFailedCount += remainingCount
            mediaBatchQueue.clear()
            viewModel.pendingDeleteUris = emptyList()
            if (batchDeletedUris.isEmpty() && batchDirectDeletedCount == 0) {
                Toast.makeText(this, "Deletion cancelled by user", Toast.LENGTH_SHORT).show()
                viewModel.setDeletionState(DeletionState.Idle)
                clearBatchState()
            } else {
                finalizeBatchedDeletion()
            }
        }
    }

    /** Launches the system delete confirmation for the next queued batch of media URIs. */
    private fun launchNextMediaBatch() {
        val nextBatch = mediaBatchQueue.removeFirstOrNull()
        if (nextBatch.isNullOrEmpty()) {
            finalizeBatchedDeletion()
            return
        }
        viewModel.pendingDeleteUris = nextBatch
        try {
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, nextBatch)
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            deleteLauncher.launch(request)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.e("DELETE_DEBUG", "MediaStore.createDeleteRequest error: ${e.message}", e)
            }
            // Fallback: delete this batch manually in background, then continue with the queue
            lifecycleScope.launch(Dispatchers.IO) {
                val manualDeleted = mutableListOf<Uri>()
                var manualFailed = 0
                for (uri in nextBatch) {
                    val path = pendingPaths[uri]
                    val s = attemptManualDeletion(this@MainActivity, uri, path)
                    if (s) manualDeleted.add(uri) else manualFailed++
                }
                withContext(Dispatchers.Main) {
                    if (manualDeleted.isNotEmpty()) {
                        viewModel.removeDeletedItems(manualDeleted)
                    }
                    batchDeletedUris.addAll(manualDeleted)
                    batchFailedCount += manualFailed
                    launchNextMediaBatch()
                }
            }
        }
    }

    /** Shows the final toast + dialog after every batch (media + non-media) has been processed. */
    private fun finalizeBatchedDeletion() {
        val totalDeleted = batchDirectDeletedCount + batchDeletedUris.size
        val totalFailed = batchDirectFailedCount + batchFailedCount
        val recoveredBytes = batchDeletedUris.mapNotNull { pendingFileItems[it]?.size }.sum() +
                batchDirectRecoveredBytes

        if (totalDeleted > 0) {
            Toast.makeText(this, "Successfully deleted $totalDeleted file(s)", Toast.LENGTH_SHORT).show()
        }
        if (totalFailed > 0) {
            Toast.makeText(this, "Failed to delete $totalFailed file(s)", Toast.LENGTH_SHORT).show()
        }
        if (totalFailed == 0 && totalDeleted > 0) {
            viewModel.setDeletionState(DeletionState.Complete(totalDeleted, recoveredBytes))
        } else if (totalFailed > 0 && totalDeleted > 0) {
            viewModel.setDeletionState(DeletionState.Error("Some files could not be deleted.", totalDeleted, totalFailed))
        } else if (totalDeleted == 0 && totalFailed > 0) {
            viewModel.setDeletionState(DeletionState.Error("Files could not be deleted.", 0, totalFailed))
        } else {
            viewModel.setDeletionState(DeletionState.Idle)
        }
        batchDirectRecoveredBytes = 0L
        clearBatchState()
    }

    // Launcher for Android 10 RecoverableSecurityException
    private val recoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uri = pendingRecoverableUri
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val fileItem = pendingFileItems[uri]
            val path = pendingPaths[uri]
            val success = attemptManualDeletion(this, uri, path)
            DeleteDebugger.logDuringDelete(
                uri = uri,
                deleteMethod = "RecoverableSecurityException Retry",
                deleteRequestCreated = true,
                systemAuthLaunched = true,
                authResult = "RESULT_OK",
                deleteResult = if (success) "SUCCESS" else "FAILED"
            )
            DeleteDebugger.logAfterDelete(
                filename = fileItem?.name ?: "Unknown",
                uri = uri,
                existsAfterDeletion = !success,
                finalResult = if (success) "DELETED" else "FAILED_STILL_EXISTS"
            )
            if (success) {
                viewModel.removeDeletedItems(listOf(uri))
                Toast.makeText(this, "Successfully deleted file", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Deletion cancelled", Toast.LENGTH_SHORT).show()
        }
        pendingRecoverableUri = null
    }

    // Launcher for MANAGE_EXTERNAL_STORAGE settings
    private var onManageStorageResult: (() -> Unit)? = null
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onManageStorageResult?.invoke()
        onManageStorageResult = null
    }

    private fun checkAllStoragePermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return true
            }
            val hasImages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        // Android 14+: user may grant access to selected photos/videos only
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                                checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == android.content.pm.PackageManager.PERMISSION_GRANTED)
            } else {
                checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            return hasImages
        } else {
            val hasRead = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasWrite = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            return hasRead || hasWrite
        }
    }

    private fun requestManageStoragePermission(onReturned: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            onManageStorageResult = onReturned
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(fallbackIntent)
            }
        } else {
            onReturned()
        }
    }

    private fun getPhysicalPath(context: Context, uri: Uri): String? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path
        }
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx != -1) {
                        return cursor.getString(dataIdx)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun uriExistsWithStoredPath(context: Context, uri: Uri, storedPath: String?): Boolean {
        val pathToCheck = storedPath ?: getPhysicalPath(context, uri)
        if (pathToCheck != null) {
            val file = File(pathToCheck)
            if (file.exists()) {
                DeleteDebugger.logDeleteAttempt(context, uri, "uriExists_StoredPath", "Physical file STILL EXISTS at $pathToCheck")
                return true
            } else {
                DeleteDebugger.logDeleteAttempt(context, uri, "uriExists_StoredPath", "Physical file does not exist")
            }
        } else {
            DeleteDebugger.logDeleteAttempt(context, uri, "uriExists_StoredPath", "Path is null, falling back to openFileDescriptor")
        }

        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                DeleteDebugger.logDeleteAttempt(context, uri, "uriExists_Fallback", "openFileDescriptor success, file exists")
                true
            } ?: run {
                DeleteDebugger.logDeleteAttempt(context, uri, "uriExists_Fallback", "openFileDescriptor null, file doesn't exist")
                false
            }
        } catch (e: java.io.FileNotFoundException) {
            DeleteDebugger.logDeleteAttempt(context, uri, "uriExists_Fallback", "FileNotFoundException, file doesn't exist")
            false
        } catch (e: Exception) {
            DeleteDebugger.logDeleteAttempt(context, uri, "uriExists_Fallback", "Exception: $e, assuming it still exists")
            true
        }
    }

    private fun isMediaStoreUri(uri: Uri): Boolean {
        val auth = uri.authority ?: return false
        return auth.equals(MediaStore.AUTHORITY, ignoreCase = true) || auth.startsWith("media")
    }

    private fun attemptManualDeletion(context: Context, uri: Uri, storedPath: String?): Boolean {
        val path = storedPath ?: getPhysicalPath(context, uri)

        // 1. Try to delete the physical file first if path is available
        if (path != null) {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // 2. Try to delete via ContentResolver / DocumentsContract
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            // Note: Never launch activity result launchers inside batch loops as concurrent launches crash Android
        }

        // 3. Verify it's actually gone using physical check
        val stillExists = uriExistsWithStoredPath(context, uri, path)
        return !stillExists
    }

    private fun executeDeletionPipeline(uris: List<Uri>) {
        if (viewModel.deletionState.value is DeletionState.InProgress) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w("DELETE_DEBUG", "Deletion already in progress. Ignoring duplicate request.")
            }
            return
        }
        if (uris.isEmpty()) return

        // 1. Check storage permissions first
        if (!checkAllStoragePermissions()) {
            Toast.makeText(this, "Storage permission is required for deletion", Toast.LENGTH_LONG).show()
            return
        }

        viewModel.setDeletionState(DeletionState.InProgress(0, uris.size))

        lifecycleScope.launch(Dispatchers.IO) {
            val fileItemsMap = mutableMapOf<Uri, FileItem>()
            val pathsMap = mutableMapOf<Uri, String?>()

            for (uri in uris) {
                val item = viewModel.getFileItem(uri)
                if (item != null) fileItemsMap[uri] = item
                pathsMap[uri] = item?.path ?: getPhysicalPath(this@MainActivity, uri)
            }

            val hasAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

            // Mode A: MANAGE_EXTERNAL_STORAGE (Android 11+) or Android 9 & below.
            // Direct physical and ContentResolver deletion without MediaStore prompt limits or binder crashes.
            if (hasAllFilesAccess || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val trulyDeleted = mutableListOf<Uri>()
                var failureCount = 0

                for ((index, uri) in uris.withIndex()) {
                    val path = pathsMap[uri]
                    val success = attemptManualDeletion(this@MainActivity, uri, path)
                    if (success) {
                        trulyDeleted.add(uri)
                    } else {
                        failureCount++
                    }

                    if (index % 5 == 0 || index == uris.size - 1) {
                        withContext(Dispatchers.Main) {
                            viewModel.setDeletionState(DeletionState.InProgress(index + 1, uris.size))
                        }
                    }
                }

                val recoveredBytes = trulyDeleted.mapNotNull { fileItemsMap[it]?.size }.sum()

                withContext(Dispatchers.Main) {
                    if (trulyDeleted.isNotEmpty()) {
                        viewModel.removeDeletedItems(trulyDeleted)
                        Toast.makeText(this@MainActivity, "Successfully deleted ${trulyDeleted.size} file(s)", Toast.LENGTH_SHORT).show()
                    }
                    if (failureCount == 0 && trulyDeleted.isNotEmpty()) {
                        viewModel.setDeletionState(DeletionState.Complete(trulyDeleted.size, recoveredBytes))
                    } else if (failureCount > 0 && trulyDeleted.isNotEmpty()) {
                        viewModel.setDeletionState(DeletionState.Error("Some files could not be deleted.", trulyDeleted.size, failureCount))
                    } else if (trulyDeleted.isEmpty() && failureCount > 0) {
                        viewModel.setDeletionState(DeletionState.Error("Files could not be deleted.", 0, failureCount))
                    } else {
                        viewModel.setDeletionState(DeletionState.Idle)
                    }
                }
                return@launch
            }

            // Mode B: Scoped storage on Android 11+ without MANAGE_EXTERNAL_STORAGE
            // Separate genuine MediaStore media (Photos, Audio, Videos) from Files/Documents
            val mediaStoreUris = mutableListOf<Uri>()
            val nonMediaStoreUris = mutableListOf<Uri>()

            for (uri in uris) {
                val item = fileItemsMap[uri]
                val isMedia = item?.category == Category.PHOTOS || item?.category == Category.AUDIO || item?.category == Category.VIDEOS
                if (isMedia && isMediaStoreUri(uri)) {
                    mediaStoreUris.add(uri)
                } else {
                    nonMediaStoreUris.add(uri)
                }
            }

            // Delete non-media files directly first
            val directDeleted = mutableListOf<Uri>()
            var directFailed = 0
            for ((index, uri) in nonMediaStoreUris.withIndex()) {
                val path = pathsMap[uri]
                val success = attemptManualDeletion(this@MainActivity, uri, path)
                if (success) directDeleted.add(uri) else directFailed++

                if (index % 5 == 0 || index == nonMediaStoreUris.size - 1) {
                    withContext(Dispatchers.Main) {
                        viewModel.setDeletionState(DeletionState.InProgress(index + 1, uris.size))
                    }
                }
            }

            if (directDeleted.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    viewModel.removeDeletedItems(directDeleted)
                }
            }

            // For genuine media files, request batch deletion safely in sequential
            // batches of at most 100 (Binder transaction limit). Every batch is
            // confirmed by the system one after another — nothing is skipped.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaStoreUris.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    pendingFileItems.clear()
                    pendingFileItems.putAll(fileItemsMap)
                    pendingPaths.clear()
                    pendingPaths.putAll(pathsMap)
                    // Reset accumulators for this deletion operation
                    batchDeletedUris.clear()
                    batchFailedCount = 0
                    batchDirectDeletedCount = directDeleted.size
                    batchDirectFailedCount = directFailed
                    batchDirectRecoveredBytes = directDeleted.mapNotNull { fileItemsMap[it]?.size }.sum()
                    batchTotalMediaCount = mediaStoreUris.size
                    mediaBatchQueue.clear()
                    mediaBatchQueue.addAll(mediaStoreUris.chunked(100))
                    launchNextMediaBatch()
                }
            } else {
                // Pre-Android 11 or no media items left
                val mediaDeleted = mutableListOf<Uri>()
                var mediaFailed = 0
                for ((index, uri) in mediaStoreUris.withIndex()) {
                    val path = pathsMap[uri]
                    val success = attemptManualDeletion(this@MainActivity, uri, path)
                    if (success) mediaDeleted.add(uri) else mediaFailed++

                    withContext(Dispatchers.Main) {
                        viewModel.setDeletionState(DeletionState.InProgress(nonMediaStoreUris.size + index + 1, uris.size))
                    }
                }

                val allDeleted = directDeleted + mediaDeleted
                val allFailed = directFailed + mediaFailed
                val recBytes = allDeleted.mapNotNull { fileItemsMap[it]?.size }.sum()

                withContext(Dispatchers.Main) {
                    if (mediaDeleted.isNotEmpty()) {
                        viewModel.removeDeletedItems(mediaDeleted)
                    }
                    if (allFailed == 0 && allDeleted.isNotEmpty()) {
                        viewModel.setDeletionState(DeletionState.Complete(allDeleted.size, recBytes))
                    } else if (allFailed > 0 && allDeleted.isNotEmpty()) {
                        viewModel.setDeletionState(DeletionState.Error("Some files could not be deleted.", allDeleted.size, allFailed))
                    } else if (allDeleted.isEmpty() && allFailed > 0) {
                        viewModel.setDeletionState(DeletionState.Error("Files could not be deleted.", 0, allFailed))
                    } else {
                        viewModel.setDeletionState(DeletionState.Idle)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val scanMode by viewModel.scanMode.collectAsState()
            val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()
            val scanHistory by viewModel.scanHistory.collectAsState()

            var showSettings by remember { mutableStateOf(false) }
            var showHistory by remember { mutableStateOf(false) }

            MyApplicationTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val scanState by viewModel.scanState.collectAsState()
                val selectedForDeletion by viewModel.selectedForDeletion.collectAsState()
                val deletionState by viewModel.deletionState.collectAsState()
                val lastScanTime by viewModel.lastScanTime.collectAsState()

                var showExplanationDialog by remember { mutableStateOf(false) }
                var isManageStorageGranted by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Environment.isExternalStorageManager()
                        } else {
                            true
                        }
                    )
                }

                val runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(
                        android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.READ_MEDIA_AUDIO
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    listOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                } else {
                    listOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }

                val runtimePermissionState = rememberMultiplePermissionsState(runtimePermissions)

                // FIX (Android 14+): the system photo dialog offers "Select photos and videos",
                // which grants READ_MEDIA_VISUAL_USER_SELECTED instead of the full READ_MEDIA_*
                // permissions. Treat that partial grant as usable media access so the app
                // doesn't keep nagging for permissions the user already answered.
                var permissionCheckTick by remember { mutableIntStateOf(0) }
                val hasPartialMediaAccess = remember(
                    permissionCheckTick,
                    runtimePermissionState.allPermissionsGranted
                ) {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                            checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                val mediaPermissionsGranted =
                    runtimePermissionState.allPermissionsGranted || hasPartialMediaAccess

                val allAccessReady = isManageStorageGranted && mediaPermissionsGranted

                fun triggerFullPermissionFlow() {
                    if (!mediaPermissionsGranted) {
                        runtimePermissionState.launchMultiplePermissionRequest()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        showExplanationDialog = true
                    }
                }

                // Initial Startup Check: Only scan when ALL access is ready and onboarding is done
                LaunchedEffect(Unit) {
                    if (allAccessReady && hasCompletedOnboarding) {
                        if (viewModel.scanState.value is ScanState.Idle) {
                            viewModel.scanDevice(scanMode)
                        }
                    } else if (hasCompletedOnboarding) {
                        triggerFullPermissionFlow()
                    }
                }

                // Auto start scan when permissions are granted and onboarding is complete
                LaunchedEffect(allAccessReady, hasCompletedOnboarding) {
                    if (allAccessReady && hasCompletedOnboarding && viewModel.scanState.value is ScanState.Idle) {
                        viewModel.scanDevice(scanMode)
                    }
                }

                // Check again whenever Activity resumes (e.g., coming back from Settings)
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissionCheckTick++
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val currentManagerStatus = Environment.isExternalStorageManager()
                                isManageStorageGranted = currentManagerStatus
                                if (currentManagerStatus && !mediaPermissionsGranted) {
                                    runtimePermissionState.launchMultiplePermissionRequest()
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Onboarding Dialog (Requirement 14)
                if (!hasCompletedOnboarding) {
                    OnboardingDialog(
                        onFinished = {
                            viewModel.settingsManager.setOnboardingCompleted(true)
                            if (allAccessReady) {
                                if (viewModel.scanState.value is ScanState.Idle) {
                                    viewModel.scanDevice(scanMode)
                                }
                            } else {
                                triggerFullPermissionFlow()
                            }
                        }
                    )
                }

                // Settings Dialog (Requirement 12)
                if (showSettings) {
                    SettingsDialog(
                        settingsManager = viewModel.settingsManager,
                        onDismiss = { showSettings = false },
                        onOpenHistory = {
                            showSettings = false
                            showHistory = true
                        }
                    )
                }

                // Scan History Dialog (Requirement 6)
                if (showHistory) {
                    ScanHistoryDialog(
                        history = scanHistory,
                        onDismiss = { showHistory = false },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }

                // Explanation dialog before opening MANAGE_ALL_FILES_ACCESS_PERMISSION settings
                if (showExplanationDialog) {
                    StoragePermissionDialog(
                        onDismiss = { showExplanationDialog = false },
                        onConfirm = {
                            showExplanationDialog = false
                            requestManageStoragePermission {
                                permissionCheckTick++
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val granted = Environment.isExternalStorageManager()
                                    isManageStorageGranted = granted
                                    if (granted && !mediaPermissionsGranted) {
                                        runtimePermissionState.launchMultiplePermissionRequest()
                                    }
                                }
                            }
                        }
                    )
                }

                // Deletion In-Progress Dialog
                if (deletionState is DeletionState.InProgress) {
                    val state = deletionState as DeletionState.InProgress
                    AlertDialog(
                        onDismissRequest = {},
                        icon = {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        },
                        title = {
                            Text(
                                text = "Deleting duplicates...",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        text = {
                            Text(
                                text = "${state.current} of ${state.total} files",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {},
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // Deletion Complete Dialog
                if (deletionState is DeletionState.Complete) {
                    val state = deletionState as DeletionState.Complete
                    AlertDialog(
                        onDismissRequest = { viewModel.setDeletionState(DeletionState.Idle) },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(KeepBadgeBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = KeepBadgeText,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Cleanup Complete",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        text = {
                            Text(
                                text = "${state.deletedCount} duplicate file(s) removed\n${formatSize(state.recoveredBytes)} recovered",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.setDeletionState(DeletionState.Idle) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("DONE", fontWeight = FontWeight.Bold)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                // Deletion Error Dialog
                if (deletionState is DeletionState.Error) {
                    val state = deletionState as DeletionState.Error
                    AlertDialog(
                        onDismissRequest = { viewModel.setDeletionState(DeletionState.Idle) },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DeleteBadgeBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = DeleteBadgeText,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "Cleanup Notice",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        text = {
                            Text(
                                text = "${state.message}\n\n${state.deletedCount} files removed, ${state.failedCount} files could not be removed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.setDeletionState(DeletionState.Idle) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            when (val state = scanState) {
                                is ScanState.Idle -> {
                                    HomeScreen(
                                        totalRecoverableSize = 0,
                                        totalDuplicateCount = 0,
                                        selectedDuplicateCount = 0,
                                        selectedRecoverableSize = 0,
                                        photosSize = 0, photosCount = 0,
                                        audioSize = 0, audioCount = 0,
                                        videosSize = 0, videosCount = 0,
                                        filesSize = 0, filesCount = 0,
                                        lastScanTime = lastScanTime,
                                        currentScanMode = scanMode,
                                        isManageStorageGranted = isManageStorageGranted,
                                        onRequestManageStorage = { triggerFullPermissionFlow() },
                                        onScanModeChanged = { viewModel.settingsManager.setScanMode(it) },
                                        onScanClick = {
                                            if (allAccessReady) {
                                                viewModel.scanDevice(scanMode)
                                            } else {
                                                triggerFullPermissionFlow()
                                                if (mediaPermissionsGranted) {
                                                    viewModel.scanDevice(scanMode)
                                                }
                                            }
                                        },
                                        onCleanNowClick = {},
                                        onCategoryClick = {},
                                        onOpenHistory = { showHistory = true },
                                        onOpenSettings = { showSettings = true }
                                    )
                                }
                                is ScanState.Scanning -> {
                                    ScanningScreen(
                                        state = state,
                                        onCancelScan = { viewModel.cancelScan() }
                                    )
                                }
                                is ScanState.Complete -> {
                                    val groups = state.duplicateGroups
                                    val photos = groups.filter { it.category == Category.PHOTOS }
                                    val audio = groups.filter { it.category == Category.AUDIO }
                                    val videos = groups.filter { it.category == Category.VIDEOS }
                                    val files = groups.filter { it.category == Category.FILES }

                                    val calculateSize: (List<com.example.model.DuplicateGroup>) -> Long = { list ->
                                        list.sumOf { it.sizePerItem * (it.items.size - 1) }
                                    }
                                    val calculateCount: (List<com.example.model.DuplicateGroup>) -> Int = { list ->
                                        list.sumOf { it.items.size - 1 }
                                    }

                                    val totalSize = calculateSize(groups)
                                    val totalCount = calculateCount(groups)

                                    val selectedDuplicates = groups.flatMap { it.items }.filter { it.uri in selectedForDeletion }
                                    val selectedCount = selectedDuplicates.size
                                    val selectedSize = selectedDuplicates.sumOf { it.size }

                                    HomeScreen(
                                        totalRecoverableSize = totalSize,
                                        totalDuplicateCount = totalCount,
                                        selectedDuplicateCount = selectedCount,
                                        selectedRecoverableSize = selectedSize,
                                        photosSize = calculateSize(photos), photosCount = calculateCount(photos),
                                        audioSize = calculateSize(audio), audioCount = calculateCount(audio),
                                        videosSize = calculateSize(videos), videosCount = calculateCount(videos),
                                        filesSize = calculateSize(files), filesCount = calculateCount(files),
                                        lastScanTime = lastScanTime,
                                        currentScanMode = scanMode,
                                        isManageStorageGranted = isManageStorageGranted,
                                        onRequestManageStorage = { triggerFullPermissionFlow() },
                                        onScanModeChanged = { viewModel.settingsManager.setScanMode(it) },
                                        onScanClick = {
                                            if (allAccessReady) {
                                                viewModel.scanDevice(scanMode)
                                            } else {
                                                triggerFullPermissionFlow()
                                                if (mediaPermissionsGranted) {
                                                    viewModel.scanDevice(scanMode)
                                                }
                                            }
                                        },
                                        onCleanNowClick = {
                                            navController.navigate("category/ALL")
                                        },
                                        onCategoryClick = { cat ->
                                            navController.navigate("category/${cat.name}")
                                        },
                                        onOpenHistory = { showHistory = true },
                                        onOpenSettings = { showSettings = true }
                                    )
                                }
                            }
                        }

                        composable("category/{categoryName}") { backStackEntry ->
                            val categoryName = backStackEntry.arguments?.getString("categoryName") ?: "ALL"
                            val initialCategory = if (categoryName.equals("ALL", ignoreCase = true)) {
                                null
                            } else {
                                try {
                                    Category.valueOf(categoryName)
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            val state = scanState as? ScanState.Complete
                            val groups = state?.duplicateGroups ?: emptyList()

                            DuplicateListScreen(
                                initialCategory = initialCategory,
                                groups = groups,
                                selectedUris = selectedForDeletion,
                                isDeleting = deletionState is DeletionState.InProgress,
                                onToggleSelection = { uri, groupUris -> viewModel.toggleSelection(uri, groupUris) },
                                onBack = { navController.popBackStack() },
                                onDeleteSelected = { uris ->
                                    executeDeletionPipeline(uris)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
