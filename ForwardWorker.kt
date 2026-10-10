package com.payhub.smsforwarder

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Sends one SMS to the PayHub webhook. WorkManager keeps the job across reboots and
 * retries with backoff when there is no internet or the server is down.
 */
class ForwardWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val from = inputData.getString("from") ?: ""
        val message = inputData.getString("message") ?: ""
        val ts = inputData.getLong("ts", System.currentTimeMillis())
        val url = Prefs.url(applicationContext)

        if (url.isBlank() || !url.startsWith("https://")) {
            LogStore.add(applicationContext, "SKIP: webhook URL missing or not https")
            return Result.failure()
        }

        val body = JSONObject()
            .put("from", from)
            .put("message", message)
            .put("timestamp", ts)
            .toString()

        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val resp = try {
                (if (code < 400) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }?.take(160) ?: ""
            } catch (e: Exception) {
                ""
            }
            conn.disconnect()

            when {
                code in 200..299 -> {
                    LogStore.add(applicationContext, "OK $code  from=$from  $resp")
                    Result.success()
                }
                code == 404 || code == 401 || code == 403 -> {
                    // Wrong/rotated secret — retrying will not help.
                    LogStore.add(applicationContext, "FAILED $code (check webhook URL / secret)  $resp")
                    Result.failure()
                }
                else -> retryOrGiveUp("HTTP $code $resp")
            }
        } catch (e: IOException) {
            retryOrGiveUp("network error: ${e.message}")
        }
    }

    private fun retryOrGiveUp(reason: String): Result {
        return if (runAttemptCount >= MAX_ATTEMPTS) {
            LogStore.add(applicationContext, "GIVE UP after $runAttemptCount tries: $reason")
            Result.failure()
        } else {
            LogStore.add(applicationContext, "RETRY #${runAttemptCount + 1}: $reason")
            Result.retry()
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 40

        fun enqueue(ctx: Context, from: String, message: String, ts: Long) {
            val data = workDataOf(
                "from" to from.take(64),
                "message" to message.take(2000),
                "ts" to ts
            )
            val req = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueue(req)
        }
    }
}
