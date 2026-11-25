package com.openjarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStreamParserTest {
    private val parser = ChatStreamParser()

    @Test
    fun extractsDeltaContent() {
        assertEquals(
            "Hello",
            parser.contentFromLine("""data: {"choices":[{"delta":{"content":"Hello"}}]}"""),
        )
    }

    @Test
    fun ignoresDoneAndNonDataLines() {
        assertNull(parser.contentFromLine("event: message"))
        assertNull(parser.contentFromLine("data: [DONE]"))
    }

    @Test
    fun ignoresRoleOnlyFrames() {
        assertNull(
            parser.contentFromLine("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""),
        )
    }
}
