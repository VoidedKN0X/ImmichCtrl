package com.vknxworks.immichctrl.data.log

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** In-app log of what happened (connections, backups, errors), viewable and shareable in Settings > Logs. */
object AppLog {
    enum class Level { INFO, WARN, ERROR }

    data class Entry(val time: LocalDateTime, val level: Level, val tag: String, val message: String) {
        override fun toString() = "${time.format(fmt)} ${level.name.first()} [$tag] $message"
    }

    private const val MAX = 800
    private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    private val buffer = ArrayDeque<Entry>()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    private fun add(level: Level, tag: String, message: String) {
        buffer.addLast(Entry(LocalDateTime.now(), level, tag, message))
        while (buffer.size > MAX) buffer.removeFirst()
        _entries.value = buffer.toList()
    }

    fun i(tag: String, message: String) {
        Log.i("ImmichCtrl/$tag", message)
        add(Level.INFO, tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w("ImmichCtrl/$tag", message)
        add(Level.WARN, tag, message)
    }

    fun e(tag: String, message: String, t: Throwable? = null) {
        Log.e("ImmichCtrl/$tag", message, t)
        add(Level.ERROR, tag, if (t != null) "$message: ${t.message ?: t::class.simpleName}" else message)
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    fun text(): String = entries.value.joinToString("\n")
}
