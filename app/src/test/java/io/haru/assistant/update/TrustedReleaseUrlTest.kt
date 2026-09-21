package io.haru.assistant.update

import org.junit.Assert.*
import org.junit.Test

class TrustedReleaseUrlTest {
    private val name = "HARU-v0.7.1-Standalone-arm64.apk"
    private val path = "${TrustedReleaseUrl.REPOSITORY_PATH}/releases/download/v0.7.1/$name"

    @Test fun acceptsOnlyExactHaruReleaseAsset() {
        assertTrue(TrustedReleaseUrl.apk("0.7.1", name, "https://github.com$path"))
        listOf("http://github.com$path", "https://github.com.evil.test$path",
            "https://github.com/other/repository/releases/download/v0.7.1/$name",
            "https://user@github.com$path", "https://github.com:8443$path",
            "https://github.com$path?redirect=1", "https://github.com$path#fragment",
            "https://github.com" + path.replace("v0.7.1", "v0.7.0"),
            "not a URL").forEach { assertFalse(it, TrustedReleaseUrl.apk("0.7.1", name, it)) }
    }
}
