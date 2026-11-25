package com.openjarvis.mobile

data class ChatMessage(
    val role: String,
    val content: String,
) {
    init {
        require(role == "user" || role == "assistant") { "Unsupported chat role" }
        require(content.isNotBlank()) { "Message cannot be blank" }
    }
}
