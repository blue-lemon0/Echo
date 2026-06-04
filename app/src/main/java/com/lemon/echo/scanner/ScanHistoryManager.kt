package com.lemon.echo.scanner

import android.content.Context
import android.content.SharedPreferences

data class ScanHistoryItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ScanHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("echo_prefs", Context.MODE_PRIVATE)

    var autoCopyEnabled: Boolean
        get() = prefs.getBoolean("auto_copy", false)
        set(value) = prefs.edit().putBoolean("auto_copy", value).apply()

    private val _history = mutableListOf<ScanHistoryItem>()
    val history: List<ScanHistoryItem> get() = _history.toList()

    fun addToHistory(result: ScanResult) {
        _history.add(0, ScanHistoryItem(text = result.rawText))
    }

    fun clearHistory() {
        _history.clear()
    }
}
