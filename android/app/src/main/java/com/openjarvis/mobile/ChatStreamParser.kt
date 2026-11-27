package com.openjarvis.mobile

import org.json.JSONObject

/** Parses OpenAI-compatible Server-Sent Events and ignores non-content frames. */
internal class ChatStreamParser {
    fun contentFromLine(line: String): String? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null
        val root = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return null
        }
        val choices = root.optJSONArray("choices") ?: return null
        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
        val rawContent = delta.opt("content") ?: return null
        if (rawContent === JSONObject.NULL) return null
        return (rawContent as? String)?.takeIf { it.isNotEmpty() }
    }
}
