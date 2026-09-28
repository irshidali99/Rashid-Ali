package com.example.model

import android.net.Uri

enum class Category {
    PHOTOS, AUDIO, VIDEOS, FILES;

    val displayName: String
        get() = when (this) {
            PHOTOS -> "Photos"
            AUDIO -> "Audio"
            VIDEOS -> "Videos"
            FILES -> "Others"
        }
}

data class FileItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val category: Category,
    val mimeType: String,
    var hash: String? = null,
    val path: String? = null
)

data class DuplicateGroup(
    val hash: String,
    val category: Category,
    val items: List<FileItem>,
    val sizePerItem: Long
)

