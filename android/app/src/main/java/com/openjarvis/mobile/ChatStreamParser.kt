package com.openjarvis.mobile

import org.json.JSONObject

/** Parses OpenAI-compatible Server-Sent Events and ignores non-content frames. */
internal class ChatStreamParser {
    fun contentFromLine(line: String): String? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        val root = JSONObject(payload)
        val choices = root.optJSONArray("choices") ?: return null
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
        return delta.optString("content").takeIf { it.isNotEmpty() }
    }
}
