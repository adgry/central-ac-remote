package com.hvacpanel.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.hvacpanel.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A release newer than the one running. */
data class Update(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

sealed interface CheckResult {
    data class Available(val update: Update) : CheckResult
    data object UpToDate : CheckResult
    /** The repository has no releases yet — not an error, just nothing to get. */
    data object NoReleases : CheckResult
    data class Failed(val message: String) : CheckResult
}

/**
 * Looks for a newer build on GitHub Releases, fetches the APK, and hands it to
 * the system installer.
 *
 * Two things this deliberately does not do: it never installs anything without
 * a tap, and it never downgrades — a release whose tag parses lower than the
 * running version is ignored, so re-tagging an old commit cannot push a rollback
 * onto the phone.
 *
 * The update only installs over the running app if both are signed with the same
 * key. See README: build releases with the project keystore, not a debug key.
 */
class UpdateChecker(private val context: Context) {

    val currentVersion: String = BuildConfig.VERSION_NAME
    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val repo: String = BuildConfig.UPDATE_REPO

    private val downloadDir get() = File(context.cacheDir, "update").apply { mkdirs() }

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext CheckResult.Failed("没有配置更新源")
        val body = try {
            fetch("https://api.github.com/repos/$repo/releases/latest")
        } catch (e: NotFound) {
            return@withContext CheckResult.NoReleases
        } catch (e: Exception) {
            return@withContext CheckResult.Failed(e.message ?: "查不到更新")
        }
        try {
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            if (tag.isBlank()) return@withContext CheckResult.NoReleases
            if (json.optBoolean("draft")) return@withContext CheckResult.NoReleases
            if (!isNewer(tag, currentVersion)) return@withContext CheckResult.UpToDate

            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        size = a.optLong("size")
                        break
                    }
                }
            }
            if (apkUrl.isBlank()) {
                return@withContext CheckResult.Failed("$tag 这个版本没有附带 APK")
            }
            CheckResult.Available(
                Update(
                    versionName = tag.removePrefix("v"),
                    notes = json.optString("body").trim(),
                    apkUrl = apkUrl,
                    sizeBytes = size,
                    pageUrl = json.optString("html_url"),
                ),
            )
        } catch (e: Exception) {
            CheckResult.Failed("看不懂更新信息：${e.message}")
        }
    }

    /** Downloads the APK into the cache, reporting 0..1 progress. */
    suspend fun download(update: Update, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            downloadDir.listFiles()?.forEach { it.delete() }
            val target = File(downloadDir, "hvacpanel-${update.versionName}.apk")
            val conn = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "hvacpanel")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IllegalStateException("下载失败：HTTP $code")
                val total = if (update.sizeBytes > 0) update.sizeBytes else conn.contentLengthLong
                var done = 0L
                conn.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (target.length() <= 0) throw IllegalStateException("下载到的文件是空的")
                onProgress(1f)
                target
            } finally {
                conn.disconnect()
            }
        }

    /** Android 8+ requires a per-app grant before it will let us install. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    // ---------------------------------------------------------------- plumbing

    private class NotFound : Exception()

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "hvacpanel")
        }
        try {
            val code = conn.responseCode
            if (code == 404) throw NotFound()
            if (code == 403) throw IllegalStateException("GitHub 限流了，过一会儿再试")
            if (code !in 200..299) throw IllegalStateException("GitHub 返回 $code")
            return conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: NotFound) {
            throw e
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            throw IllegalStateException("连不上 GitHub：${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    /** Numeric-segment comparison, so 1.10.0 beats 1.9.0 and v-prefixes are fine. */
    internal fun isNewer(remoteTag: String, localVersion: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .mapNotNull { seg -> seg.takeWhile(Char::isDigit).toIntOrNull() }
        val remote = parts(remoteTag)
        val local = parts(localVersion)
        if (remote.isEmpty()) return false
        for (i in 0 until maxOf(remote.size, local.size)) {
            val a = remote.getOrElse(i) { 0 }
            val b = local.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
