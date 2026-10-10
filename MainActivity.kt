package com.payhub.smsforwarder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var urlInput: EditText
    private lateinit var senderInput: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, pad / 2, 0, 0) }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        status = TextView(this).apply { textSize = 15f }
        root.addView(status)

        root.addView(label("Webhook URL (from PayHub dashboard, must be https)"))
        urlInput = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(false)
            setText(Prefs.url(this@MainActivity))
        }
        root.addView(urlInput)

        root.addView(label("Allowed senders, comma separated (bKash, NAGAD, Rocket/16216, Upay). Empty = forward ALL SMS"))
        senderInput = EditText(this).apply { setText(Prefs.sender(this@MainActivity)) }
        root.addView(senderInput)

        enabledSwitch = Switch(this).apply {
            text = "Forwarding enabled"
            isChecked = Prefs.enabled(this@MainActivity)
        }
        root.addView(enabledSwitch)

        root.addView(button("Save") { save() })
        root.addView(button("1) Allow SMS permission") { askSmsPermission() })
        root.addView(button("2) Disable battery optimization") { askBattery() })
        root.addView(button("Send connection test") { sendTest() })
        root.addView(button("Refresh log") { refresh() })
        root.addView(button("Clear log") { LogStore.clear(this); refresh() })

        root.addView(label("Log"))
        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }
        root.addView(logView)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun save() {
        val url = urlInput.text.toString().trim()
        if (url.isNotEmpty() && !url.startsWith("https://")) {
            toast("URL must start with https://")
            return
        }
        Prefs.save(this, url, senderInput.text.toString(), enabledSwitch.isChecked)
        toast("Saved")
        refresh()
    }

    private fun hasSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun askSmsPermission() {
        if (hasSmsPermission()) {
            toast("SMS permission already granted")
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (!hasSmsPermission()) {
            toast("Not granted. On Android 13+: App info > ⋮ > Allow restricted settings, then try again")
        }
        refresh()
    }

    private fun ignoringBattery(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun askBattery() {
        if (ignoringBattery()) {
            toast("Already unrestricted")
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun sendTest() {
        save()
        if (Prefs.url(this).isBlank()) {
            toast("Set the webhook URL first")
            return
        }
        // Server ignores this (not a bKash receive SMS) but replies 200 if URL/secret are valid.
        LogStore.add(this, "Test queued")
        ForwardWorker.enqueue(this, Prefs.senderList(this).firstOrNull() ?: "test", "PayHub connection test", System.currentTimeMillis())
        toast("Test queued — refresh log in a few seconds")
    }

    private fun refresh() {
        val ok = "✅"
        val no = "❌"
        status.text = buildString {
            append("SMS permission: ${if (hasSmsPermission()) ok else no}\n")
            append("Battery unrestricted: ${if (ignoringBattery()) ok else no}\n")
            append("Webhook set: ${if (Prefs.url(this@MainActivity).isNotBlank()) ok else no}\n")
            append("Forwarding: ${if (Prefs.enabled(this@MainActivity)) "ON" else "OFF"}")
        }
        logView.text = LogStore.read(this).ifEmpty { "(empty)" }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
