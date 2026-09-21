package io.haru.assistant.update

import java.net.URI

internal object TrustedReleaseUrl {
    const val REPOSITORY_PATH = "/Denberg28/HARU-Human-Assistance-Responsive-Utility"

    fun apk(version: String, name: String, url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host.equals("github.com", ignoreCase = true) &&
            uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath == "$REPOSITORY_PATH/releases/download/v$version/$name"
    }.getOrDefault(false)
}
