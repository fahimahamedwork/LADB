package com.ladb.reborn.core

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Command history with JSON persistence, newest last. */
class HistoryRepository(app: Application) {

    companion object {
        private const val MAX_ENTRIES = 500
        private const val FILE = "command_history.json"
    }

    private val file = java.io.File(app.filesDir, FILE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items

    init {
        runCatching {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                _items.value = List(arr.length()) { i -> arr.getString(i) }
            }
        }
    }

    fun add(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        val updated = _items.value.filterNot { it == trimmed } + trimmed
        _items.value = updated.takeLast(MAX_ENTRIES)
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    private fun persist() {
        val snapshot = _items.value
        scope.launch {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach { arr.put(it) }
                file.writeText(arr.toString())
            }
        }
    }
}
