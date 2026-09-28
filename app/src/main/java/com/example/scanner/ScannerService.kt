package com.example.scanner

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.BuildConfig
import com.example.model.Category
import com.example.model.DuplicateGroup
import com.example.model.FileItem
import com.example.util.ScanMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

class ScannerService(private val context: Context) {

    companion object {
        private const val TAG = "ScannerService"
        private const val STREAM_BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer
        private const val PARTIAL_PREFIX_SIZE = 16 * 1024 // 16 KB prefix for fast pre-filter

        val PHOTO_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif",
            "raw", "dng", "svg", "ico", "tiff", "tif", "psd"
        )

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "wav", "m4a", "flac", "aac", "ogg", "opus", "amr",
            "wma", "mid", "midi", "m4r", "aiff", "alac", "3ga"
        )

        val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "ts",
            "wmv", "m4v", "mpeg", "mpg", "vob", "ogv", "divx"
        )

        val OTHERS_EXTENSIONS = setOf(
            "txt", "rar", "zip", "7z", "tar", "gz", "bz2", "xz", "tgz",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
            "csv", "rtf", "apk", "xapk", "apks", "iso", "bin", "dat", "log",
            "json", "xml", "html", "htm", "md", "epub", "mobi", "sql", "sqlite",
            "db", "bak", "ini", "cfg", "properties", "yaml", "yml"
        )
    }

    fun scanDevice(
        mode: ScanMode = ScanMode.QUICK,
        enabledCategories: Set<Category> = Category.values().toSet()
    ): Flow<ScanProgress> = flow {
        val scanStartTime = System.currentTimeMillis()
        if (BuildConfig.DEBUG) Log.d(TAG, "=== SCAN STARTED (Mode: $mode, Categories: $enabledCategories) ===")

        val allFiles = ArrayList<FileItem>()
        var totalDiscovered = 0
        var lastEmitTime = 0L

        fun shouldEmitThrottle(): Boolean {
            val now = System.currentTimeMillis()
            if (now - lastEmitTime > 120) {
                lastEmitTime = now
                return true
            }
            return false
        }

        if (mode == ScanMode.QUICK) {
            // ==========================================
            // QUICK SCAN PIPELINE: Fast, media-centric
            // ==========================================

            // 1. Photos (Quick - MediaStore)
            if (Category.PHOTOS in enabledCategories) {
                emit(ScanProgress(0.04f, "⚡ Quick Scan: Scanning Photos...", Category.PHOTOS, totalDiscovered))
                val photos = queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Category.PHOTOS)
                allFiles.addAll(photos)
                totalDiscovered += photos.size
            }
            yield()

            // 2. Audio (Quick - MediaStore)
            if (Category.AUDIO in enabledCategories) {
                emit(ScanProgress(0.12f, "⚡ Quick Scan: Scanning Audio...", Category.AUDIO, totalDiscovered))
                val audio = queryMediaStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Category.AUDIO)
                allFiles.addAll(audio)
                totalDiscovered += audio.size
            }
            yield()

            // 3. Videos (Quick - MediaStore Video + File collection)
            if (Category.VIDEOS in enabledCategories) {
                emit(ScanProgress(0.20f, "⚡ Quick Scan: Scanning Videos...", Category.VIDEOS, totalDiscovered))
                val videos = queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Category.VIDEOS)
                allFiles.addAll(videos)
                totalDiscovered += videos.size
            }
            yield()

            // 4. Others (Quick - Downloads collection + shallow Downloads directory)
            if (Category.FILES in enabledCategories) {
                emit(ScanProgress(0.28f, "⚡ Quick Scan: Scanning Downloads & Documents...", Category.FILES, totalDiscovered))
                val othersList = ArrayList<FileItem>()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlItems = queryMediaStore(MediaStore.Downloads.EXTERNAL_CONTENT_URI, Category.FILES)
                    othersList.addAll(dlItems.filter { item ->
                        val ext = item.name.substringAfterLast('.', "").lowercase()
                        ext in OTHERS_EXTENSIONS
                    })
                }

                val nonMediaSelection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} NOT IN (" +
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, " +
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}, " +
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

                val nonMediaFiles = queryMediaStore(
                    MediaStore.Files.getContentUri("external"),
                    Category.FILES,
                    selection = nonMediaSelection
                )
                othersList.addAll(nonMediaFiles.filter { item ->
                    val ext = item.name.substringAfterLast('.', "").lowercase()
                    ext in OTHERS_EXTENSIONS
                })

                // Fast shallow check of Download and Documents directories (maxDepth = 2)
                val standardFolders = listOfNotNull(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                )
                for (folder in standardFolders) {
                    othersList.addAll(
                        scanDirectoryFiles(
                            directory = folder,
                            category = Category.FILES,
                            allowedExtensions = OTHERS_EXTENSIONS,
                            maxDepth = 2,
                            currentDepth = 0
                        )
                    )
                }

                allFiles.addAll(othersList)
                totalDiscovered += othersList.size
            }
            yield()

        } else {
            // ==============================================================
            // DEEP SCAN PIPELINE: Full storage crawl across all directories
            // ==============================================================
            emit(ScanProgress(0.03f, "🔍 Deep Scan: Initializing full storage crawl...", null, totalDiscovered))

            // Step 1: Query MediaStore databases as base index
            if (Category.PHOTOS in enabledCategories) {
                emit(ScanProgress(0.06f, "🔍 Deep Scan: Querying Photo database...", Category.PHOTOS, totalDiscovered))
                val photos = queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Category.PHOTOS)
                allFiles.addAll(photos)
                totalDiscovered += photos.size
            }
            yield()

            if (Category.AUDIO in enabledCategories) {
                emit(ScanProgress(0.10f, "🔍 Deep Scan: Querying Audio database...", Category.AUDIO, totalDiscovered))
                val audio = queryMediaStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Category.AUDIO)
                allFiles.addAll(audio)
                totalDiscovered += audio.size
            }
            yield()

            if (Category.VIDEOS in enabledCategories) {
                emit(ScanProgress(0.14f, "🔍 Deep Scan: Querying Video database...", Category.VIDEOS, totalDiscovered))
                val videos = queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Category.VIDEOS)
                allFiles.addAll(videos)
                totalDiscovered += videos.size
            }
            yield()

            if (Category.FILES in enabledCategories) {
                emit(ScanProgress(0.18f, "🔍 Deep Scan: Querying File & Download database...", Category.FILES, totalDiscovered))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val dlItems = queryMediaStore(MediaStore.Downloads.EXTERNAL_CONTENT_URI, Category.FILES)
                    allFiles.addAll(dlItems)
                    totalDiscovered += dlItems.size
                }

                val nonMediaSelection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} NOT IN (" +
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, " +
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}, " +
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

                val nonMediaFiles = queryMediaStore(
                    MediaStore.Files.getContentUri("external"),
                    Category.FILES,
                    selection = nonMediaSelection
                )
                allFiles.addAll(nonMediaFiles)
                totalDiscovered += nonMediaFiles.size
            }
            yield()

            // Step 2: Thorough recursive filesystem crawl across ALL storage volumes (Internal & SD Card)
            val storageRoots = getAllStorageRoots()
            if (BuildConfig.DEBUG) Log.d(TAG, "Deep Scan storage roots: ${storageRoots.map { it.absolutePath }}")

            for ((rootIndex, rootDir) in storageRoots.withIndex()) {
                val baseProgress = 0.20f + (0.15f * (rootIndex.toFloat() / storageRoots.size))
                emit(ScanProgress(baseProgress, "🔍 Deep Crawling: ${rootDir.name}...", null, totalDiscovered))

                val deepFiles = deepCrawlDirectory(
                    directory = rootDir,
                    enabledCategories = enabledCategories,
                    maxDepth = 25,
                    currentDepth = 0,
                    onDirectoryVisit = { visitingDir, foundSoFar ->
                        if (shouldEmitThrottle()) {
                            val shortPath = visitingDir.absolutePath.replace(rootDir.parent ?: "", "")
                            emit(
                                ScanProgress(
                                    progress = (baseProgress + 0.05f).coerceAtMost(0.36f),
                                    message = "Deep Crawling: $shortPath",
                                    currentCategory = null,
                                    filesScannedCount = totalDiscovered + foundSoFar
                                )
                            )
                        }
                    }
                )
                allFiles.addAll(deepFiles)
                totalDiscovered += deepFiles.size
                yield()
            }
        }

        yield()

        // STEP 1: Deduplicate discovered files by canonical physical path or URI
        // MediaStore references are preserved; duplicate references to the same physical file on disk are merged!
        val seenPhysicalKeys = HashSet<String>()
        val uniqueDiscoveredFiles = ArrayList<FileItem>()

        for (item in allFiles) {
            val physicalPath = item.path?.trim()?.ifEmpty { null }
            val uniqueKey = if (physicalPath != null) {
                try {
                    File(physicalPath).canonicalPath
                } catch (_: Exception) {
                    physicalPath
                }
            } else {
                item.uri.toString()
            }

            if (seenPhysicalKeys.add(uniqueKey)) {
                uniqueDiscoveredFiles.add(item)
            }
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Mode: $mode, Total raw: ${allFiles.size}, Unique distinct physical files: ${uniqueDiscoveredFiles.size}")

        // STEP 2: Exclude 0-byte files, group candidates strictly by size
        val validFiles = uniqueDiscoveredFiles.filter { it.size > 0 }
        val sizeGroups = validFiles.groupBy { it.size }.filter { it.value.size > 1 }
        val sizeCandidateCount = sizeGroups.values.sumOf { it.size }

        if (BuildConfig.DEBUG) Log.d(TAG, "Valid files: ${validFiles.size}, Size candidates: $sizeCandidateCount in ${sizeGroups.size} buckets")

        if (sizeGroups.isEmpty()) {
            emit(
                ScanProgress(
                    progress = 1.0f,
                    message = if (mode == ScanMode.DEEP) "Deep Scan complete • No duplicates found" else "Quick Scan complete • No duplicates found",
                    currentCategory = null,
                    filesScannedCount = uniqueDiscoveredFiles.size,
                    duplicatesDiscoveredCount = 0,
                    recoverableBytesDiscovered = 0L,
                    results = emptyList()
                )
            )
            return@flow
        }

        // STEP 3: Multi-stage duplicate detection (Partial hash pre-filtering in Quick Scan, full byte SHA-256 verification)
        val duplicateGroups = mutableListOf<DuplicateGroup>()
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        val hashCache = HashMap<String, String>()

        var itemsAnalyzed = 0
        var hashesCalculated = 0
        var runningDuplicatesCount = 0
        var runningRecoverableBytes = 0L

        for ((size, items) in sizeGroups) {
            yield()

            // In Quick scan: if more than 2 items share identical size, compute prefix hash (16KB) to eliminate mismatches fast
            // In Deep scan: candidates are all grouped for full byte verification
            val candidateBuckets: List<List<FileItem>> = if (mode == ScanMode.QUICK && items.size > 2 && size > PARTIAL_PREFIX_SIZE) {
                val prefixGroups = mutableMapOf<String, MutableList<FileItem>>()
                for (item in items) {
                    yield()
                    val prefixHash = calculatePrefixHash(context.contentResolver, item.uri, item.path, PARTIAL_PREFIX_SIZE, buffer)
                    if (prefixHash != null) {
                        prefixGroups.getOrPut(prefixHash) { mutableListOf() }.add(item)
                    }
                }
                prefixGroups.values.filter { it.size > 1 }
            } else {
                listOf(items)
            }

            for (candidateList in candidateBuckets) {
                val hashGroups = mutableMapOf<String, MutableList<FileItem>>()

                for (item in candidateList) {
                    yield()

                    itemsAnalyzed++
                    val progressRatio = (itemsAnalyzed.toFloat() / sizeCandidateCount).coerceIn(0f, 1f)
                    val scanProgressPercent = 0.38f + (0.60f * progressRatio)

                    val statusMsg = if (mode == ScanMode.DEEP) {
                        "Deep SHA-256 byte analysis ($itemsAnalyzed / $sizeCandidateCount)"
                    } else {
                        "Analyzing duplicates ($itemsAnalyzed / $sizeCandidateCount)"
                    }

                    emit(
                        ScanProgress(
                            progress = scanProgressPercent,
                            message = statusMsg,
                            currentCategory = item.category,
                            filesScannedCount = uniqueDiscoveredFiles.size,
                            duplicatesDiscoveredCount = runningDuplicatesCount,
                            recoverableBytesDiscovered = runningRecoverableBytes
                        )
                    )

                    val cacheKey = item.path ?: item.uri.toString()
                    val hash = hashCache[cacheKey] ?: calculateFullHash(
                        context.contentResolver,
                        item.uri,
                        item.path,
                        buffer
                    )?.also {
                        hashCache[cacheKey] = it
                        hashesCalculated++
                    }

                    if (hash != null) {
                        item.hash = hash
                        hashGroups.getOrPut(hash) { mutableListOf() }.add(item)
                    } else {
                        if (BuildConfig.DEBUG) Log.w(TAG, "SCAN ERROR: Unable to hash file: ${item.name}, uri: ${item.uri}, path: ${item.path}")
                    }
                }

                // STEP 4: Build exact duplicate groups from content hash
                for ((hash, hashItems) in hashGroups) {
                    // Filter out any duplicate references to the same physical file on disk
                    val distinctFiles = hashItems.distinctBy { item ->
                        item.path?.trim()?.ifEmpty { null }?.let { p ->
                            try { File(p).canonicalPath } catch (_: Exception) { p }
                        } ?: item.uri.toString()
                    }

                    // Only if there are 2 or more TRULY DISTINCT physical files with identical content!
                    if (distinctFiles.size > 1) {
                        val sortedItems = distinctFiles.toMutableList()

                        // Keep-one-original rule: Sort so first item is the original kept safe
                        sortedItems.sortWith(
                            compareBy<FileItem> { it.name.contains(Regex("\\([0-9]+\\)|_copy|copy|-copy", RegexOption.IGNORE_CASE)) }
                                .thenBy { it.name.length }
                                .thenBy { it.dateModified }
                        )

                        val group = DuplicateGroup(
                            hash = hash,
                            category = sortedItems.first().category,
                            items = sortedItems,
                            sizePerItem = size
                        )
                        duplicateGroups.add(group)

                        val dupsInGroup = sortedItems.size - 1
                        runningDuplicatesCount += dupsInGroup
                        runningRecoverableBytes += (size * dupsInGroup)
                    }
                }
            }
        }

        val totalScanTime = System.currentTimeMillis() - scanStartTime
        val totalDuplicateFiles = duplicateGroups.sumOf { it.items.size }

        if (BuildConfig.DEBUG) Log.d(TAG, """
            === PERFORMANCE METRICS ===
            Mode: $mode
            Total scan time: ${totalScanTime}ms
            Number of files discovered: ${uniqueDiscoveredFiles.size}
            Number of size candidates: $sizeCandidateCount
            Number of full hashes calculated: $hashesCalculated
            Number of duplicate groups: ${duplicateGroups.size}
            Total duplicate files: $totalDuplicateFiles
            Recoverable size: $runningRecoverableBytes bytes
            ===========================
        """.trimIndent())

        hashCache.clear()

        val completionMsg = if (mode == ScanMode.DEEP) {
            "Deep Scan complete • $runningDuplicatesCount duplicates found"
        } else {
            "Quick Scan complete • $runningDuplicatesCount duplicates found"
        }

        emit(
            ScanProgress(
                progress = 1.0f,
                message = completionMsg,
                currentCategory = null,
                filesScannedCount = uniqueDiscoveredFiles.size,
                duplicatesDiscoveredCount = runningDuplicatesCount,
                recoverableBytesDiscovered = runningRecoverableBytes,
                results = duplicateGroups
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun queryMediaStore(
        collection: Uri,
        category: Category,
        selection: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): List<FileItem> {
        val items = ArrayList<FileItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                var count = 0
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idCol)
                        var name = cursor.getString(nameCol) ?: ""
                        var size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol)
                        val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                        val path = if (dataCol >= 0) cursor.getString(dataCol) else null

                        // If size from MediaStore is 0 or unpopulated, query file directly
                        if (size <= 0L && !path.isNullOrBlank()) {
                            try {
                                val file = File(path)
                                if (file.exists() && file.isFile) {
                                    size = file.length()
                                }
                            } catch (_: Exception) {}
                        }

                        // Fallback name if unknown or empty
                        if (name.isBlank() || name.equals("Unknown", ignoreCase = true)) {
                            if (!path.isNullOrBlank()) {
                                name = File(path).name
                            } else {
                                name = "file_$id"
                            }
                        }

                        val uri = Uri.withAppendedPath(collection, id.toString())
                        items.add(FileItem(id, uri, name, size, date, category, mime, null, path))
                        count++
                        if (count % 200 == 0) {
                            onProgress?.invoke(count)
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Cursor row parse exception: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "SCAN ERROR: Failed querying MediaStore collection $collection: ${e.message}", e)
        }
        return items
    }

    /**
     * Discovers all physical storage roots (Internal storage /storage/emulated/0 and any SD Cards / mounted drives).
     */
    private fun getAllStorageRoots(): List<File> {
        val roots = LinkedHashSet<File>()

        // 1. Primary external storage (/storage/emulated/0)
        try {
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && primary.exists() && primary.isDirectory) {
                roots.add(primary)
            }
        } catch (_: Exception) {}

        // 2. Storage directories from getExternalFilesDirs (SD Cards / external mounts)
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null) {
                    var current: File? = dir
                    while (current != null && current.parentFile != null && current.parentFile?.absolutePath != "/storage") {
                        current = current.parentFile
                    }
                    if (current != null && current.exists() && current.isDirectory && current.canRead()) {
                        roots.add(current)
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. /storage direct entries
        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val mounts = storageDir.listFiles()
                if (mounts != null) {
                    for (mount in mounts) {
                        val name = mount.name
                        if (mount.isDirectory && mount.canRead() &&
                            !name.equals("self", ignoreCase = true) &&
                            !name.equals("knox-emulated", ignoreCase = true)
                        ) {
                            if (name.equals("emulated", ignoreCase = true)) {
                                val emulated0 = File(mount, "0")
                                if (emulated0.exists() && emulated0.isDirectory) {
                                    roots.add(emulated0)
                                }
                            } else {
                                roots.add(mount)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return if (roots.isEmpty()) listOfNotNull(Environment.getExternalStorageDirectory()) else roots.toList()
    }

    /**
     * Exhaustive deep crawl of storage directory trees up to [maxDepth] levels.
     * Catalogs all Photos, Audio, Videos, Archives, Documents (.txt, .rar, .zip, etc.),
     * and any user files while skipping restricted OS system folders.
     */
    private suspend fun deepCrawlDirectory(
        directory: File?,
        enabledCategories: Set<Category>,
        maxDepth: Int = 25,
        currentDepth: Int = 0,
        onDirectoryVisit: (suspend (File, Int) -> Unit)? = null
    ): List<FileItem> {
        if (directory == null || !directory.exists() || !directory.isDirectory || !directory.canRead()) {
            return emptyList()
        }
        if (currentDepth > maxDepth) return emptyList()

        val results = ArrayList<FileItem>()
        onDirectoryVisit?.invoke(directory, results.size)
        yield()

        try {
            val files = directory.listFiles() ?: return emptyList()
            for (file in files) {
                try {
                    val name = file.name
                    if (file.isDirectory) {
                        val isRestricted = name.equals("Android", ignoreCase = true) ||
                                name.equals(".thumbnails", ignoreCase = true) ||
                                name.startsWith(".trash", ignoreCase = true)
                        if (!isRestricted) {
                            results.addAll(
                                deepCrawlDirectory(
                                    directory = file,
                                    enabledCategories = enabledCategories,
                                    maxDepth = maxDepth,
                                    currentDepth = currentDepth + 1,
                                    onDirectoryVisit = onDirectoryVisit
                                )
                            )
                        }
                    } else if (file.isFile && file.length() > 0) {
                        val ext = file.extension.lowercase()
                        val cat = when {
                            ext in PHOTO_EXTENSIONS -> Category.PHOTOS
                            ext in AUDIO_EXTENSIONS -> Category.AUDIO
                            ext in VIDEO_EXTENSIONS -> Category.VIDEOS
                            ext in OTHERS_EXTENSIONS -> Category.FILES
                            else -> Category.FILES // In Deep Scan, any non-media user file is categorized under Others
                        }

                        if (cat in enabledCategories) {
                            val uri = Uri.fromFile(file)
                            val item = FileItem(
                                id = file.absolutePath.hashCode().toLong(),
                                uri = uri,
                                name = file.name,
                                size = file.length(),
                                dateModified = file.lastModified() / 1000L,
                                category = cat,
                                mimeType = when (cat) {
                                    Category.PHOTOS -> "image/*"
                                    Category.AUDIO -> "audio/*"
                                    Category.VIDEOS -> "video/*"
                                    Category.FILES -> "application/*"
                                },
                                hash = null,
                                path = file.absolutePath
                            )
                            results.add(item)
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Directory crawl warning on ${directory.absolutePath}: ${e.message}")
        }
        return results
    }

    /**
     * Directly scans a filesystem directory for files matching allowed extensions.
     */
    private fun scanDirectoryFiles(
        directory: File?,
        category: Category,
        allowedExtensions: Set<String>,
        maxDepth: Int = 2,
        currentDepth: Int = 0
    ): List<FileItem> {
        if (directory == null || !directory.exists() || !directory.isDirectory || !directory.canRead()) {
            return emptyList()
        }
        if (currentDepth > maxDepth) return emptyList()

        val results = ArrayList<FileItem>()
        try {
            val files = directory.listFiles() ?: return emptyList()
            for (file in files) {
                try {
                    if (file.isDirectory) {
                        val name = file.name
                        if (!name.startsWith(".") && !name.equals("Android", ignoreCase = true)) {
                            results.addAll(scanDirectoryFiles(file, category, allowedExtensions, maxDepth, currentDepth + 1))
                        }
                    } else if (file.isFile && file.length() > 0) {
                        val ext = file.extension.lowercase()
                        if (ext in allowedExtensions) {
                            val uri = Uri.fromFile(file)
                            val item = FileItem(
                                id = file.absolutePath.hashCode().toLong(),
                                uri = uri,
                                name = file.name,
                                size = file.length(),
                                dateModified = file.lastModified() / 1000L,
                                category = category,
                                mimeType = when (category) {
                                    Category.VIDEOS -> "video/*"
                                    Category.FILES -> "application/*"
                                    Category.PHOTOS -> "image/*"
                                    Category.AUDIO -> "audio/*"
                                },
                                hash = null,
                                path = file.absolutePath
                            )
                            results.add(item)
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Directory traversal warning on ${directory.absolutePath}: ${e.message}")
        }
        return results
    }

    /**
     * Robust stream opener that handles all Android storage models:
     * direct filesystem FileInputStream, ContentResolver openInputStream,
     * ContentResolver openFileDescriptor (scoped storage), and file:// URI schemes.
     */
    private fun openStream(contentResolver: ContentResolver, uri: Uri, path: String?): InputStream? {
        // Method 1: Direct File access if path exists (Fastest, works on any physical file on disk)
        if (!path.isNullOrBlank()) {
            try {
                val file = File(path)
                if (file.exists() && file.isFile) {
                    return FileInputStream(file)
                }
            } catch (_: Exception) {}
        }

        // Method 2: Try ContentResolver openInputStream for content:// URIs
        try {
            val stream = contentResolver.openInputStream(uri)
            if (stream != null) return stream
        } catch (_: Exception) {}

        // Method 3: Try ContentResolver openFileDescriptor (bypasses Scoped Storage restrictions)
        try {
            val pfd = contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                return FileInputStream(pfd.fileDescriptor)
            }
        } catch (_: Exception) {}

        // Method 4: If URI has file:// scheme
        try {
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val uriPath = uri.path
                if (!uriPath.isNullOrBlank()) {
                    val file = File(uriPath)
                    if (file.exists() && file.isFile) {
                        return FileInputStream(file)
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Calculates a prefix SHA-256 hash using at most [maxBytes] to quickly distinguish files with identical size.
     */
    private fun calculatePrefixHash(
        contentResolver: ContentResolver,
        uri: Uri,
        path: String?,
        maxBytes: Int,
        buffer: ByteArray
    ): String? {
        return try {
            openStream(contentResolver, uri, path)?.use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                var totalRead = 0
                while (totalRead < maxBytes) {
                    val toRead = minOf(buffer.size, maxBytes - totalRead)
                    val bytesRead = stream.read(buffer, 0, toRead)
                    if (bytesRead <= 0) break
                    digest.update(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "HASH ERROR: Prefix hash failed for $uri ($path): ${e.message}")
            null
        }
    }

    /**
     * Calculates a full SHA-256 cryptographic content hash using streaming I/O.
     * Never loads full file into RAM, safely handles large videos and streams.
     */
    private fun calculateFullHash(
        contentResolver: ContentResolver,
        uri: Uri,
        path: String?,
        buffer: ByteArray
    ): String? {
        return try {
            openStream(contentResolver, uri, path)?.use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "HASH ERROR: Full hash failed for $uri ($path): ${e.message}")
            null
        }
    }
}

data class ScanProgress(
    val progress: Float,
    val message: String,
    val currentCategory: Category? = null,
    val filesScannedCount: Int = 0,
    val duplicatesDiscoveredCount: Int = 0,
    val recoverableBytesDiscovered: Long = 0L,
    val results: List<DuplicateGroup>? = null
)
