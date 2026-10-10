package com.payhub.smsforwarder

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("forwarder", Context.MODE_PRIVATE)

    fun url(c: Context): String = sp(c).getString("url", "") ?: ""
    const val DEFAULT_SENDERS = "bKash,NAGAD,Rocket,16216,Upay"

    /** Comma-separated list of allowed SMS senders. Empty = forward everything. */
    fun sender(c: Context): String = sp(c).getString("sender", DEFAULT_SENDERS) ?: DEFAULT_SENDERS

    fun senderList(c: Context): List<String> =
        sender(c).split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)

    fun save(c: Context, url: String, sender: String, enabled: Boolean) {
        sp(c).edit()
            .putString("url", url.trim())
            .putString("sender", sender.trim())
            .putBoolean("enabled", enabled)
            .apply()
    }
}
