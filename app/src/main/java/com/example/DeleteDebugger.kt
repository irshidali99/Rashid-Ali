package com.example

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.example.BuildConfig

/**
 * Debug-only deletion diagnostics. All logging is stripped from release builds
 * so user file names/URIs never end up in production logcat (Play policy).
 */
object DeleteDebugger {
    fun logDeleteAttempt(context: Context, uri: Uri, step: String, details: String = "") {
        if (!BuildConfig.DEBUG) return
        Log.e("DELETE_DEBUG", "[$step] URI: $uri | $details")
    }

    fun logBeforeDelete(
        context: Context,
        filename: String,
        uri: Uri,
        path: String?,
        mimeType: String,
        size: Long,
        storageType: String,
        deleteAuthStatus: String
    ) {
        if (!BuildConfig.DEBUG) return
        val isManageExternal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        val readImagesPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        Log.d("DELETE_DEBUG", """
            === BEFORE DELETION ===
            filename: $filename
            uri: $uri
            path if available: ${path ?: "N/A"}
            mimeType: $mimeType
            size: $size bytes
            storage type: $storageType
            Android SDK version: ${Build.VERSION.SDK_INT}
            MANAGE_EXTERNAL_STORAGE status: $isManageExternal
            read permission status: $readImagesPerm
            delete authorization status: $deleteAuthStatus
            =======================
        """.trimIndent())
    }

    fun logDuringDelete(
        uri: Uri,
        deleteMethod: String,
        deleteRequestCreated: Boolean,
        systemAuthLaunched: Boolean,
        authResult: String,
        deleteResult: String,
        exception: Throwable? = null
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d("DELETE_DEBUG", """
            === DURING DELETION ===
            uri: $uri
            delete method: $deleteMethod
            delete request created: $deleteRequestCreated
            system authorization launched: $systemAuthLaunched
            authorization result: $authResult
            delete result: $deleteResult
            exception if any: ${exception?.message ?: "None"}
            ======================
        """.trimIndent())
    }

    fun logAfterDelete(
        filename: String,
        uri: Uri,
        existsAfterDeletion: Boolean,
        finalResult: String
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d("DELETE_DEBUG", """
            === AFTER DELETION ===
            filename: $filename
            uri: $uri
            exists/queryable after deletion: $existsAfterDeletion
            final result: $finalResult
            ======================
        """.trimIndent())
    }
}

