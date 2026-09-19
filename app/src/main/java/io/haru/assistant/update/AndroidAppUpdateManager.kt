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
            val url =
                RELEASES_API + "&refresh=" + System.currentTimeMillis()

            val connection =
                URL(url).openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.useCaches = false
            connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json",
            )
            connection.setRequestProperty(
                "X-GitHub-Api-Version",
                "2022-11-28",
            )
            connection.setRequestProperty(
                "Cache-Control",
                "no-cache, no-store, max-age=0",
            )
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty(
                "User-Agent",
                "HARU-Android/$currentVersion",
            )

            try {
                val code = connection.responseCode
                require(code in 200..299) {
                    "Update check failed (HTTP $code)."
                }

                val releases = JSONArray(
                    connection.inputStream
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText().take(2_000_000) }
                )

                var bestVersion = currentVersion
                var bestApk = ""
                var bestReleaseUrl = ""

                for (i in 0 until releases.length()) {
                    val release =
                        releases.optJSONObject(i) ?: continue

                    if (
                        release.optBoolean("draft") ||
                        release.optBoolean("prerelease")
                    ) {
                        continue
                    }

                    val version = release
                        .optString("tag_name")
                        .removePrefix("v")
                        .trim()

                    if (version.isBlank()) continue

                    val apk = findApk(
                        release.optJSONArray("assets") ?: JSONArray()
                    )
                    if (apk.isBlank()) continue

                    if (
                        bestApk.isBlank() ||
                        isNewer(version, bestVersion)
                    ) {
                        bestVersion = version
                        bestApk = apk
                        bestReleaseUrl =
                            release.optString("html_url")
                    }
                }

                require(bestApk.isNotBlank()) {
                    "No installable HARU APK release was found."
                }

                AppUpdateInfo(
                    remoteVersion = bestVersion,
                    apkUrl = bestApk,
                    releaseUrl = bestReleaseUrl,
                    updateAvailable =
                        isNewer(bestVersion, currentVersion),
                )
            } finally {
                connection.disconnect()
            }
        }

    private fun findApk(assets: JSONArray): String {
        for (i in 0 until assets.length()) {
            val item = assets.optJSONObject(i) ?: continue
            val name = item.optString("name")
            val url = item.optString("browser_download_url")

            if (
                name.endsWith(".apk", ignoreCase = true) &&
                url.startsWith("https://")
            ) {
                return url
            }
        }

        return ""
    }

    private fun isNewer(
        remote: String,
        current: String,
    ): Boolean {
        val remoteParts = versionParts(remote)
        val currentParts = versionParts(current)
        val length =
            maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until length) {
            val rv = remoteParts.getOrElse(i) { 0 }
            val cv = currentParts.getOrElse(i) { 0 }

            if (rv != cv) {
                return rv > cv
            }
        }

        return false
    }

    private fun versionParts(value: String): List<Int> =
        value
            .substringBefore('+')
            .substringBefore('-')
            .split('.')
            .map { part ->
                part.filter(Char::isDigit)
                    .toIntOrNull()
                    ?: 0
            }

    companion object {
        private const val RELEASES_API =
            "https://api.github.com/repos/Denberg28/" +
                "HARU-Human-Assistance-Responsive-Utility/" +
                "releases?per_page=20"
    }
}
