package com.anjaz.app.sync

import com.anjaz.app.BrandConfig
import com.anjaz.app.cloud.CloudState
import com.google.firebase.auth.FirebaseAuth
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class ApiClient @Inject constructor(private val cloud: CloudState) : Notifier, com.anjaz.app.media.MediaTransport {
    val configured: Boolean get() = cloud.available && BrandConfig.WORKER_URL.isNotBlank()

    suspend fun post(path: String, body: String): Pair<Int, String>? {
        if (!configured) return null
        return withContext(Dispatchers.IO) {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return@withContext null
            val conn = (URL(BrandConfig.WORKER_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 15_000; doOutput = true
                setRequestProperty("authorization", "Bearer $token"); setRequestProperty("content-type", "application/json")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                code to text
            } finally { conn.disconnect() }
        }
    }

    override suspend fun notify(kind: String, id: String) {
        post("/v1/notify", """{"kind":"$kind","id":"$id"}""")
    }

    private suspend fun token(): String = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: error("غير مسجل")

    private fun open(path: String, method: String, token: String) =
        (URL(BrandConfig.WORKER_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("authorization", "Bearer $token")
        }

    override suspend fun upload(roomId: String, mediaId: String, file: java.io.File, mime: String) {
        check(configured) { "الخادم غير مهيأ" }
        val t = token()
        withContext(Dispatchers.IO) {
            val conn = open("/v1/media", "PUT", t).apply {
                doOutput = true; setFixedLengthStreamingMode(file.length())
                setRequestProperty("content-type", mime); setRequestProperty("x-scope", "room:$roomId"); setRequestProperty("x-idempotency-key", mediaId)
            }
            try {
                file.inputStream().use { i -> conn.outputStream.use { i.copyTo(it) } }
                val code = conn.responseCode
                if (code !in 200..299) throw java.io.IOException("HTTP $code")
            } finally { conn.disconnect() }
        }
    }

    override suspend fun download(mediaId: String, dest: java.io.File): com.anjaz.app.media.Download {
        check(configured) { "الخادم غير مهيأ" }
        val t = token()
        return withContext(Dispatchers.IO) {
            val conn = open("/v1/media/$mediaId", "GET", t)
            try {
                when (val code = conn.responseCode) {
                    200 -> { conn.inputStream.use { i -> dest.outputStream().use { i.copyTo(it) } }; com.anjaz.app.media.Download.Done }
                    404 -> com.anjaz.app.media.Download.Gone
                    else -> throw java.io.IOException("HTTP $code")
                }
            } finally { conn.disconnect() }
        }
    }

    override suspend fun ack(mediaId: String) {
        post("/v1/media/$mediaId/ack", "{}")
    }
}
