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
class ApiClient @Inject constructor(private val cloud: CloudState) : Notifier {
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
}
