package com.openjarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConversationStoreModelTest {
    @Test
    fun conversationTitleUsesFirstUserMessage() {
        val title = SavedConversation.titleFor(
            listOf(
                ChatMessage("user", "  Help me understand Arduino PWM  "),
                ChatMessage("assistant", "PWM controls average power."),
            ),
        )
        assertEquals("Help me understand Arduino PWM", title)
    }

    @Test
    fun longConversationTitleIsBounded() {
        val title = SavedConversation.titleFor(
            listOf(ChatMessage("user", "A".repeat(100))),
        )
        assertEquals(43, title.length)
        assertEquals("…", title.last().toString())
    }

    @Test
    fun newConversationsHaveDistinctIds() {
        assertNotEquals(SavedConversation.create().id, SavedConversation.create().id)
    }
}
