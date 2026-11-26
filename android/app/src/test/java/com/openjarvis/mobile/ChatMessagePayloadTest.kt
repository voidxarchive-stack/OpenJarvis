package com.openjarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessagePayloadTest {
    @Test
    fun plainTextMessageKeepsOpenAiCompatibleStringContent() {
        val payload = chatMessageToJson(ChatMessage("user", "Hello Jarvis"))
        assertEquals("user", payload.getString("role"))
        assertEquals("Hello Jarvis", payload.getString("content"))
    }

    @Test
    fun screenFrameUsesOpenAiCompatibleMultimodalContent() {
        val payload = chatMessageToJson(ChatMessage("user", "What is on this screen?", "YWJj"))
        val content = payload.getJSONArray("content")
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("What is on this screen?", content.getJSONObject(0).getString("text"))
        val image = content.getJSONObject(1)
        assertEquals("image_url", image.getString("type"))
        assertTrue(
            image.getJSONObject("image_url").getString("url")
                .startsWith("data:image/jpeg;base64,YWJj"),
        )
    }
}
