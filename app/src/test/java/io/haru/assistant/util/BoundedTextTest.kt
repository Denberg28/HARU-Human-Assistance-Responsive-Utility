package io.haru.assistant.util

import java.io.IOException
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedTextTest {
    @Test fun acceptsEmptyAndExactBoundary() {
        assertEquals("", StringReader("").readBoundedText(4))
        assertEquals("HARU", StringReader("HARU").readBoundedText(4))
        assertEquals("Hi ₍^.^₎", StringReader("Hi ₍^.^₎").readBoundedText(20))
    }
    @Test(expected = IOException::class)
    fun rejectsOversizedResponseInsteadOfTruncatingJson() {
        StringReader("12345").readBoundedText(4)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidLimit() { StringReader("").readBoundedText(0) }
}
