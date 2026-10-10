package com.payhub.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Prefs.enabled(context)) return
        if (Prefs.url(context).isBlank()) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        // Long SMS arrive in several parts: join parts that share the same sender.
        val grouped = LinkedHashMap<String, StringBuilder>()
        var ts = System.currentTimeMillis()
        for (p in parts) {
            val from = p.originatingAddress ?: ""
            grouped.getOrPut(from) { StringBuilder() }.append(p.messageBody ?: "")
            ts = p.timestampMillis
        }

        val allowed = Prefs.senderList(context)
        for ((from, body) in grouped) {
            // Empty allowed list = forward everything.
            if (allowed.isNotEmpty() && allowed.none { it.equals(from.trim(), ignoreCase = true) }) continue
            LogStore.add(context, "SMS from=$from queued (${body.length} chars)")
            ForwardWorker.enqueue(context, from, body.toString(), ts)
        }
    }
}
