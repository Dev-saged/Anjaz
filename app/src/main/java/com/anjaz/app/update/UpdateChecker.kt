package com.anjaz.app.update

import com.anjaz.app.BrandConfig
import com.anjaz.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class AppRelease(
    val version: String,
    val build: Int,
    val code: Int?,
    val apkUrl: String,
    val notes: String,
    val size: Long = 0,
)

/** يقرأ آخر Release على GitHub بثلاث طرق مستقلة كي لا يخفي حجبٌ واحد النسخة الجديدة. */
@Singleton
class UpdateChecker @Inject constructor() {

    sealed interface Result {
        data class Available(val release: AppRelease) : Result
        data class UpToDate(val version: String) : Result
        data class Failed(val reason: String) : Result
    }

    private class HttpFail(val status: Int) : IOException("HTTP $status")

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val layers = listOf<Pair<String, () -> AppRelease>>("A" to ::viaAsset, "B" to ::viaRedirect, "C" to ::viaApi)
        for ((name, layer) in layers) {
            val release = try {
                layer()
            } catch (e: Exception) {
                errors += "$name: ${(e as? HttpFail)?.message ?: e.javaClass.simpleName}"
                continue
            }
            return@withContext if (isUpdate(release, BuildConfig.VERSION_NAME, BuildConfig.BUILD_NUMBER, BuildConfig.VERSION_CODE)) {
                Result.Available(release.copy(notes = cleanNotes(release.notes)))
            } else {
                Result.UpToDate(release.version)
            }
        }
        Result.Failed(errors.joinToString("\n"))
    }

    private fun open(url: String, follow: Boolean = true) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        instanceFollowRedirects = follow
        setRequestProperty("User-Agent", "Anjaz-App")
    }

    private fun get(url: String, accept: String? = null): String {
        val conn = open(url).apply { accept?.let { setRequestProperty("Accept", it) } }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw HttpFail(conn.responseCode)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun viaAsset(): AppRelease = parseVersionJson(get("$PAGE/download/version.json"), REPO)

    private fun viaRedirect(): AppRelease {
        val conn = open(PAGE, follow = false)
        val tag = try {
            if (conn.responseCode !in 300..399) throw HttpFail(conn.responseCode)
            val location = conn.getHeaderField("Location") ?: throw IOException("no location")
            URL(URL(PAGE), location).path.substringAfterLast("/tag/", "")
        } finally {
            conn.disconnect()
        }
        val (version, build) = parseTag(tag) ?: throw IOException("bad tag")
        val notes = runCatching { get("$RAW/docs/releases/v$version.md") }.getOrDefault("")
        return AppRelease(version, build, null, apkUrl(tag), notes)
    }

    private fun viaApi(): AppRelease {
        val json = Json.parseToJsonElement(get(API, "application/vnd.github+json")).jsonObject
        val tag = json["tag_name"]?.jsonPrimitive?.contentOrNull ?: throw IOException("no tag")
        val (version, build) = parseTag(tag) ?: throw IOException("bad tag")
        val apk = json["assets"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            .firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull.orEmpty().endsWith(".apk") }
        return AppRelease(
            version, build, null,
            apk?.get("browser_download_url")?.jsonPrimitive?.contentOrNull ?: apkUrl(tag),
            json["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            apk?.get("size")?.jsonPrimitive?.longOrNull ?: 0,
        )
    }

    companion object {
        private val REPO = "https://github.com/${BrandConfig.GITHUB_REPO}"
        private val RAW = "https://raw.githubusercontent.com/${BrandConfig.GITHUB_REPO}/main"
        private val API = "https://api.github.com/repos/${BrandConfig.GITHUB_REPO}/releases/latest"
        val PAGE = "$REPO/releases/latest"
        private const val MAX_NOTES = 1_200

        private fun apkUrl(tag: String) = "$REPO/releases/download/$tag/Anjaz-$tag.apk"

        fun parts(version: String): List<Int> =
            version.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit) }
                .takeIf { p -> p.isNotEmpty() && p.all { it.isNotEmpty() } }
                ?.map { it.toInt() }
                .orEmpty()

        /** "v0.3.0-b12" ← (0.3.0, 12). */
        fun parseTag(tag: String): Pair<String, Int>? {
            val body = tag.removePrefix("v")
            val version = body.substringBefore("-b")
            if (parts(version).isEmpty()) return null
            return version to (body.substringAfter("-b", "").toIntOrNull() ?: 0)
        }

        fun parseVersionJson(text: String, repo: String): AppRelease {
            val j = Json.parseToJsonElement(text).jsonObject
            val version = j["version"]?.jsonPrimitive?.contentOrNull ?: throw IOException("no version")
            if (parts(version).isEmpty()) throw IOException("bad version")
            val build = j["build"]?.jsonPrimitive?.intOrNull ?: 0
            val tag = j["tag"]?.jsonPrimitive?.contentOrNull ?: "v$version-b$build"
            val apk = j["apk"]?.jsonPrimitive?.contentOrNull ?: "Anjaz-$tag.apk"
            return AppRelease(
                version, build, j["code"]?.jsonPrimitive?.intOrNull,
                "$repo/releases/download/$tag/$apk",
                j["notes"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                j["size"]?.jsonPrimitive?.longOrNull ?: 0,
            )
        }

        fun isNewer(candidate: String, installed: String): Boolean {
            val a = parts(candidate)
            val b = parts(installed)
            if (a.isEmpty()) return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** كود الإصدار إن توفر، وإلا رقم النسخة ثم رقم البناء. */
        fun isUpdate(r: AppRelease, installedVersion: String, installedBuild: Int, installedCode: Int): Boolean =
            if (r.code != null) r.code > installedCode
            else isNewer(r.version, installedVersion) || (!isNewer(installedVersion, r.version) && r.build > installedBuild)

        fun cleanNotes(markdown: String): String = markdown.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(">") }
            .joinToString("\n") { line ->
                val text = line.replace("**", "").replace("`", "")
                if (text.startsWith("- ") || text.startsWith("* ")) "• " + text.drop(2) else text
            }
            .let { if (it.length > MAX_NOTES) it.take(MAX_NOTES).substringBeforeLast('\n') + "\n…" else it }
    }
}
