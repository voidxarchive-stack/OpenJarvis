package com.openjarvis.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores a bounded conversation locally in app-private storage; it is not uploaded. */
internal class ChatHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): List<ChatMessage> {
        val raw = preferences.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val role = item.optString("role")
                val content = item.optString("content")
                if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                    ChatMessage(role, content)
                } else null
            }.takeLast(MAX_MESSAGES)
        } catch (_: Exception) {
            clear()
            emptyList()
        }
    }

    fun save(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(MAX_MESSAGES).forEach { message ->
            array.put(JSONObject().put("role", message.role).put("content", message.content))
        }
        preferences.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_MESSAGES).apply()
    }

    private companion object {
        const val PREFERENCES = "openjarvis_chat_history"
        const val KEY_MESSAGES = "messages"
        const val MAX_MESSAGES = 40
    }
}
