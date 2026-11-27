package com.openjarvis.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class SavedConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
) {
    companion object {
        fun create(): SavedConversation {
            val now = System.currentTimeMillis()
            return SavedConversation(UUID.randomUUID().toString(), "New chat", now, now)
        }

        fun titleFor(messages: List<ChatMessage>): String {
            val firstUserMessage = messages.firstOrNull { it.role == "user" }?.content
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                .orEmpty()
            if (firstUserMessage.isBlank()) return "New chat"
            return firstUserMessage.take(42).let {
                if (firstUserMessage.length > 42) "$it…" else it
            }
        }
    }
}

/** Conversation history is stored separately from PersonalMemoryStore. The first read migrates the old single chat without deleting it. */
internal class ConversationStore(
    context: Context,
    private val legacyHistoryStore: ChatHistoryStore,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun loadAll(): List<SavedConversation> {
        val raw = preferences.getString(KEY_CONVERSATIONS, null)
        if (raw == null) {
            val legacyMessages = legacyHistoryStore.load()
            val migrated = if (legacyMessages.isNotEmpty()) {
                val now = System.currentTimeMillis()
                listOf(
                    SavedConversation(
                        id = UUID.randomUUID().toString(),
                        title = SavedConversation.titleFor(legacyMessages),
                        createdAt = now,
                        updatedAt = now,
                        messages = legacyMessages,
                    ),
                )
            } else emptyList()
            saveAll(migrated)
            return migrated
        }
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull conversation@{ index ->
                val item = array.optJSONObject(index) ?: return@conversation null
                val id = item.optString("id").takeIf(String::isNotBlank) ?: return@conversation null
                val messagesArray = item.optJSONArray("messages") ?: JSONArray()
                val messages = (0 until messagesArray.length()).mapNotNull message@{ messageIndex ->
                    val message = messagesArray.optJSONObject(messageIndex) ?: return@message null
                    val role = message.optString("role")
                    val content = message.optString("content")
                    if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                        ChatMessage(role, content)
                    } else null
                }.takeLast(MAX_MESSAGES_PER_CONVERSATION)
                SavedConversation(
                    id = id,
                    title = item.optString("title", "New chat").ifBlank { "New chat" },
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                    pinned = item.optBoolean("pinned", false),
                    messages = messages,
                )
            }.sortedWith(compareByDescending<SavedConversation> { it.pinned }.thenByDescending { it.updatedAt })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAll(conversations: List<SavedConversation>) {
        val array = JSONArray()
        conversations
            .sortedWith(compareByDescending<SavedConversation> { it.pinned }.thenByDescending { it.updatedAt })
            .take(MAX_CONVERSATIONS)
            .forEach { conversation ->
                val messages = JSONArray()
                conversation.messages.takeLast(MAX_MESSAGES_PER_CONVERSATION).forEach { message ->
                    // Attachments and screen frames are intentionally not persisted.
                    messages.put(JSONObject().put("role", message.role).put("content", message.content))
                }
                array.put(
                    JSONObject()
                        .put("id", conversation.id)
                        .put("title", conversation.title)
                        .put("createdAt", conversation.createdAt)
                        .put("updatedAt", conversation.updatedAt)
                        .put("pinned", conversation.pinned)
                        .put("messages", messages),
                )
            }
        preferences.edit().putString(KEY_CONVERSATIONS, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES = "openjarvis_conversations_v2"
        const val KEY_CONVERSATIONS = "conversations"
        const val MAX_CONVERSATIONS = 100
        const val MAX_MESSAGES_PER_CONVERSATION = 100
    }
}
