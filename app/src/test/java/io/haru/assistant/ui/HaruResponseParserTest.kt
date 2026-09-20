package io.haru.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaruResponseParserTest {

    @Test
    fun parsesCommonAiMarkdownIntoStructuredBlocks() {
        val blocks =
            parseAssistantResponse(
                """
                ### Philippine History

                1. **Pre-Colonial Period**
                2. Spanish Period

                * Trade was active.
                * Local communities were diverse.

                > Short note

                ```
                val ready = true
                ```
                """.trimIndent()
            )

        assertEquals(
            HaruResponseBlock.Heading(
                text = "Philippine History",
                level = 3,
            ),
            blocks[0],
        )
        assertTrue(blocks.any { it is HaruResponseBlock.Numbered })
        assertTrue(blocks.any { it is HaruResponseBlock.Bullet })
        assertTrue(blocks.any { it is HaruResponseBlock.Quote })
        assertTrue(blocks.any { it is HaruResponseBlock.Code })
    }

    @Test
    fun joinsWrappedParagraphLinesWithoutRawControlCharacters() {
        val blocks =
            parseAssistantResponse(
                "This is a long\nwrapped paragraph.\u0000"
            )

        assertEquals(
            listOf(
                HaruResponseBlock.Paragraph(
                    "This is a long wrapped paragraph."
                )
            ),
            blocks,
        )
    }

    @Test
    fun malformedOrEmptyInputDoesNotCrash() {
        assertTrue(parseAssistantResponse("").isEmpty())
        assertEquals(
            listOf(HaruResponseBlock.Paragraph("**unfinished")),
            parseAssistantResponse("**unfinished"),
        )
    }
}
