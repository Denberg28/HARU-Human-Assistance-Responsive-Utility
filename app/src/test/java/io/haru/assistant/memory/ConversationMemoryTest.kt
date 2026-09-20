package io.haru.assistant.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMemoryTest {

    @Test
    fun keepsOnlyLatestTenCompletedExchanges() {
        val memory = ConversationMemory()

        repeat(12) { index ->
            memory.append(
                user = "user-$index",
                assistant = "assistant-$index",
            )
        }

        val snapshot = memory.snapshot()
        assertEquals(10, snapshot.size)
        assertEquals("user-2", snapshot.first().user)
        assertEquals("assistant-11", snapshot.last().assistant)
    }

    @Test
    fun ignoresIncompleteExchange() {
        val memory = ConversationMemory()

        memory.append("hello", "")
        memory.append("", "reply")

        assertTrue(memory.snapshot().isEmpty())
    }

    @Test
    fun trimsOversizedFields() {
        val memory = ConversationMemory()

        memory.append(
            user = "u".repeat(ConversationMemoryPolicy.MAX_USER_CHARS + 50),
            assistant = "a".repeat(ConversationMemoryPolicy.MAX_ASSISTANT_CHARS + 50),
        )

        val exchange = memory.snapshot().single()
        assertEquals(
            ConversationMemoryPolicy.MAX_USER_CHARS,
            exchange.user.length,
        )
        assertEquals(
            ConversationMemoryPolicy.MAX_ASSISTANT_CHARS,
            exchange.assistant.length,
        )
    }

    @Test
    fun totalBudgetEvictsOldestExchanges() {
        val memory =
            ConversationMemory(
                maxExchanges = 10,
                maxTotalChars = 40,
            )

        memory.append("1111111111", "aaaaaaaaaa")
        memory.append("2222222222", "bbbbbbbbbb")
        memory.append("3333333333", "cccccccccc")

        val snapshot = memory.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals("2222222222", snapshot.first().user)
    }

    @Test
    fun evictedTurnsBecomeRollingRecap() {
        val memory = ConversationMemory()

        repeat(11) { index ->
            memory.append(
                user = "question $index",
                assistant = "answer $index",
            )
        }

        assertEquals(10, memory.snapshot().size)
        assertTrue(memory.summary().contains("question 0"))
        assertTrue(memory.summary().contains("answer 0"))
    }

    @Test
    fun rollingRecapIsBounded() {
        val memory =
            ConversationMemory(
                maxExchanges = 1,
                maxSummaryChars = 120,
            )

        repeat(8) { index ->
            memory.append(
                user = "question-$index-" + "u".repeat(80),
                assistant = "answer-$index-" + "a".repeat(80),
            )
        }

        assertTrue(memory.summary().length <= 120)
    }

    @Test
    fun antigravitySessionExpires() {
        val now = 1_000_000_000L
        val fresh =
            AntigravitySession(
                interactionId = "interaction",
                environmentId = "environment",
                updatedAtMs = now - 1_000L,
            )
        val stale =
            fresh.copy(
                updatedAtMs =
                    now -
                        ConversationMemoryPolicy.ANTIGRAVITY_SESSION_MAX_AGE_MS -
                        1L,
            )

        assertTrue(fresh.isFresh(now))
        assertTrue(!stale.isFresh(now))
    }

    @Test
    fun clearRemovesAllMemory() {
        val memory = ConversationMemory()
        memory.append("hello", "hi")

        memory.clear()

        assertTrue(memory.snapshot().isEmpty())
    }
}
