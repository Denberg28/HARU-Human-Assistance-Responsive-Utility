package io.haru.assistant.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val remoteVersion: String,
    val apkUrl: String,
    val releaseUrl: String,
    val updateAvailable: Boolean,
)

class AndroidAppUpdateManager {
    suspend fun check(currentVersion: String): AppUpdateInfo =
        withContext(Dispatchers.IO) {
            val connection = URL(
                RELEASES_API + "&refresh=" + System.currentTimeMillis()
            ).openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty("User-Agent", "HARU-Android/$currentVersion")

            try {
                val code = connection.responseCode
                require(code in 200..299) {
                    "Update check failed (HTTP $code)."
                }

                val releases = JSONArray(
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                        it.readText().take(2_000_000)
                    }
                )

                var bestVersion = currentVersion
                var bestApk = ""
                var bestReleaseUrl = ""

                for (i in 0 until releases.length()) {
                    val release = releases.optJSONObject(i) ?: continue
                    if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue

                    val version =
                        release.optString("tag_name")
                            .removePrefix("v")
                            .trim()

                    if (!VERSION_PATTERN.matches(version)) continue

                    val apk = findApk(
                        release.optJSONArray("assets") ?: JSONArray(),
                        version,
                    )
                    if (apk.isBlank()) continue

                    if (bestApk.isBlank() || isNewer(version, bestVersion)) {
                        bestVersion = version
                        bestApk = apk
                        bestReleaseUrl = release.optString("html_url")
                    }
                }

                require(bestApk.isNotBlank()) {
                    "No HARU standalone APK release was found."
                }

                AppUpdateInfo(
                    remoteVersion = bestVersion,
                    apkUrl = bestApk,
                    releaseUrl = bestReleaseUrl,
                    updateAvailable = isNewer(bestVersion, currentVersion),
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun findApk(
        assets: JSONArray,
        version: String,
    ): String {
        val expectedName =
            "HARU-v" + version + "-Standalone-arm64.apk"

        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            val name = item.optString("name")
            val url = item.optString("browser_download_url")
            if (name != expectedName) continue

            val parsed = runCatching { URL(url) }.getOrNull() ?: continue
            if (
                parsed.protocol.equals("https", ignoreCase = true) &&
                parsed.host.equals("github.com", ignoreCase = true)
            ) {
                return url
            }
        }
        return ""
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val r = versionParts(remote)
        val c = versionParts(current)
        val n = maxOf(r.size, c.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun versionParts(value: String): List<Int> =
        value.substringBefore('+').substringBefore('-').split('.').map {
            it.filter(Char::isDigit).toIntOrNull() ?: 0
        }

    companion object {
        private val VERSION_PATTERN =
            Regex("^\\d+\\.\\d+\\.\\d+$")

        private const val RELEASES_API =
            "https://api.github.com/repos/Denberg28/" +
                "HARU-Human-Assistance-Responsive-Utility/releases?per_page=20"
    }
}
