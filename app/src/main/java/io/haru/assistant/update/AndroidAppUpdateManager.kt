package io.haru.assistant.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val remoteVersion: String,
    val apkUrl: String,
    val releaseUrl: String,
    val updateAvailable: Boolean,
)

class AndroidAppUpdateManager {
    suspend fun check(currentVersion: String): AppUpdateInfo = withContext(Dispatchers.IO) {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "HARU-Android/$currentVersion")

        try {
            val code = connection.responseCode
            require(code in 200..299) {
                if (code == 404) "No HARU release is published yet."
                else "Update check failed (HTTP $code)."
            }

            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                it.readText().take(1_000_000)
            }
            val json = JSONObject(raw)
            val remote = json.optString("tag_name").removePrefix("v").trim()
            require(remote.isNotBlank()) { "Release version is missing." }

            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val item = assets.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = item.optString("browser_download_url")
                        if (apkUrl.startsWith("https://")) break
                    }
                }
            }

            AppUpdateInfo(
                remoteVersion = remote,
                apkUrl = apkUrl,
                releaseUrl = json.optString("html_url"),
                updateAvailable = isNewer(remote, currentVersion),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = versionParts(remote)
        val c = versionParts(current)
        val length = maxOf(r.size, c.size)
        for (i in 0 until length) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun versionParts(value: String): List<Int> =
        value.split('.', '-', '+')
            .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/Denberg28/" +
                "HARU-Human-Assistance-Responsive-Utility/releases/latest"
    }
}
