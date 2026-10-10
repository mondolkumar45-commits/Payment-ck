package com.payhub.smsforwarder

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the last few log lines so you can see what happened without adb. */
object LogStore {
    private const val MAX_LINES = 60
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)

    @Synchronized
    fun add(c: Context, line: String) {
        val sp = c.getSharedPreferences("forwarder_log", Context.MODE_PRIVATE)
        val old = sp.getString("log", "") ?: ""
        val lines = if (old.isEmpty()) mutableListOf() else old.split("\n").toMutableList()
        lines.add("${fmt.format(Date())}  $line")
        while (lines.size > MAX_LINES) lines.removeAt(0)
        sp.edit().putString("log", lines.joinToString("\n")).apply()
    }

    @Synchronized
    fun read(c: Context): String =
        c.getSharedPreferences("forwarder_log", Context.MODE_PRIVATE).getString("log", "") ?: ""

    @Synchronized
    fun clear(c: Context) {
        c.getSharedPreferences("forwarder_log", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
