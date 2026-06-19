package com.lemon.echo.scanner

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class HistoryLabel { SCAN, CHAIN }

data class ScanHistoryItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val label: HistoryLabel = HistoryLabel.SCAN
)

class ScanHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("echo_history", Context.MODE_PRIVATE)

    private val historyKey = "history"

    private val _history = mutableListOf<ScanHistoryItem>()
    private val _historyFlow = MutableStateFlow<List<ScanHistoryItem>>(emptyList())
    val historyFlow: StateFlow<List<ScanHistoryItem>> = _historyFlow.asStateFlow()

    init {
        loadHistory()
    }

    fun addToHistory(result: ScanResult) {
        addToHistory(result.rawText, HistoryLabel.SCAN)
    }

    fun addToHistory(text: String, label: HistoryLabel = HistoryLabel.SCAN) {
        _history.removeAll { it.text == text }
        _history.add(0, ScanHistoryItem(text = text, label = label))
        saveHistory()
        _historyFlow.value = _history.toList()
    }

    fun clearHistory() {
        _history.clear()
        saveHistory()
        _historyFlow.value = emptyList()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        _history.forEach { item ->
            arr.put(JSONObject().apply {
                put("text", item.text)
                put("timestamp", item.timestamp)
                put("label", item.label.name)
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
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        label = try { HistoryLabel.valueOf(obj.optString("label", "SCAN")) }
                        catch (_: Exception) { HistoryLabel.SCAN }
                    )
                )
            }
        } catch (_: Exception) {
        }
    }
}
