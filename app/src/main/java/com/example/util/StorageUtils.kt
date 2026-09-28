package com.example.util

import android.os.Environment
import android.os.StatFs

data class DeviceStorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long
)

object StorageUtils {
    fun getDeviceStorageInfo(): DeviceStorageInfo {
        return try {
            val path = Environment.getDataDirectory().path
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = (total - free).coerceAtLeast(0L)
            DeviceStorageInfo(total, free, used)
        } catch (e: Exception) {
            DeviceStorageInfo(0L, 0L, 0L)
        }
    }
}
