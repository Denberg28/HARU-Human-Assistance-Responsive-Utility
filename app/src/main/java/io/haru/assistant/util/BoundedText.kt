package io.haru.assistant.util

import java.io.IOException
import java.io.Reader

/** Enforce the limit during I/O; truncating readText() afterwards still allocates the entire response. */
fun Reader.readBoundedText(maxChars: Int): String {
    require(maxChars > 0)
    val buffer = CharArray(minOf(8192, maxChars))
    val text = StringBuilder(minOf(8192, maxChars))
    while (true) {
        val count = read(buffer)
        if (count < 0) return text.toString()
        if (count > maxChars - text.length) throw IOException("Response exceeded HARU's size limit.")
        text.append(buffer, 0, count)
    }
}
