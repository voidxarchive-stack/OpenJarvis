package com.openjarvis.mobile

data class ChatMessage(
    val role: String,
    val content: String,
    /** Base64 JPEG for one explicitly shared screen frame. Never persisted to chat history. */
    val imageBase64: String? = null,
) {
    init {
        require(role == "user" || role == "assistant" || role == "system") {
            "Unsupported chat role"
        }
        require(content.isNotBlank()) { "Message cannot be blank" }
    }
}
