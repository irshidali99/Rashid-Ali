package com.example.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ScanHistoryItem(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val filesScanned: Int,
    val duplicatesFound: Int,
    val recoverableBytes: Long,
    val scanMode: String = "Deep Scan"
)

class ScanHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("duplicate_remover_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "scan_history_records"
        private const val MAX_HISTORY_ITEMS = 25
    }

    fun getHistory(): List<ScanHistoryItem> {
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val items = mutableListOf<ScanHistoryItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    ScanHistoryItem(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        filesScanned = obj.optInt("filesScanned", 0),
                        duplicatesFound = obj.optInt("duplicatesFound", 0),
                        recoverableBytes = obj.optLong("recoverableBytes", 0L),
                        scanMode = obj.optString("scanMode", "Deep Scan")
                    )
                )
            }
        } catch (e: Exception) {
            // Safe fallback on parsing error
        }
        return items
    }

    fun recordScan(filesScanned: Int, duplicatesFound: Int, recoverableBytes: Long, scanMode: String) {
        val current = getHistory().toMutableList()
        val newItem = ScanHistoryItem(
            filesScanned = filesScanned,
            duplicatesFound = duplicatesFound,
            recoverableBytes = recoverableBytes,
            scanMode = scanMode
        )
        current.add(0, newItem)
        if (current.size > MAX_HISTORY_ITEMS) {
            current.removeAt(current.lastIndex)
        }

        try {
            val jsonArray = JSONArray()
            for (item in current) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("filesScanned", item.filesScanned)
                    put("duplicatesFound", item.duplicatesFound)
                    put("recoverableBytes", item.recoverableBytes)
                    put("scanMode", item.scanMode)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            // Ignore write errors
        }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
