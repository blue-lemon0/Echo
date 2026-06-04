package com.lemon.echo.scanner

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ScanHistoryItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ScanHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("echo_history", Context.MODE_PRIVATE)

    private val historyKey = "history"

    init {
        loadHistory()
    }

    private val _history = mutableListOf<ScanHistoryItem>()
    val history: List<ScanHistoryItem> get() = _history.toList()

    fun addToHistory(result: ScanResult) {
        // Dedup: don't add if same text already exists
        _history.removeAll { it.text == result.rawText }
        _history.add(0, ScanHistoryItem(text = result.rawText))
        saveHistory()
    }

    fun clearHistory() {
        _history.clear()
        saveHistory()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        _history.forEach { item ->
            arr.put(JSONObject().apply {
                put("text", item.text)
                put("timestamp", item.timestamp)
            })
        }
        prefs.edit().putString(historyKey, arr.toString()).apply()
    }

    private fun loadHistory() {
        try {
            val json = prefs.getString(historyKey, null) ?: return
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                _history.add(
                    ScanHistoryItem(
                        text = obj.getString("text"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (_: Exception) {
        }
    }
}
