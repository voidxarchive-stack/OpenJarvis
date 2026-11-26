package com.openjarvis.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Encodes plain text or a single screen image as an OpenAI-compatible message. */
internal fun chatMessageToJson(message: ChatMessage): JSONObject {
    val payload = JSONObject().put("role", message.role)
    val image = message.imageBase64
    if (image.isNullOrBlank()) {
        payload.put("content", message.content)
    } else {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", message.content))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$image"),
                    ),
            )
        payload.put("content", content)
    }
    return payload
}

class OpenJarvisApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun checkConnection(baseUrl: String, apiKey: String): List<String> {
        val url = validateServerUrl(baseUrl)
        val request = Request.Builder()
            .url("$url/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Server returned HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val models = JSONObject(body).optJSONArray("data")
                ?: error("Server response did not contain a model list")
            return (0 until models.length()).mapNotNull { index ->
                models.optJSONObject(index)?.optString("id")?.takeIf(String::isNotBlank)
            }
        }
    }

    suspend fun streamMessage(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        onDelta: suspend (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        require(model.isNotBlank()) { "Choose a model before sending a message" }
        require(messages.isNotEmpty()) { "Message history cannot be empty" }
        val payloadMessages = JSONArray()
        messages.forEach { message -> payloadMessages.put(chatMessageToJson(message)) }
        val payload = JSONObject()
            .put("model", model)
            .put("messages", payloadMessages)
            .put("stream", true)
            .toString()
        val request = Request.Builder()
            .url("${validateServerUrl(baseUrl)}/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody(JSON))
            .build()

        val result = StringBuilder()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(500)
                error("Server returned HTTP ${response.code}${if (detail.isBlank()) "" else ": $detail"}")
            }
            val source = response.body?.source() ?: error("Server returned an empty response")
            val parser = ChatStreamParser()
            while (true) {
                val line = source.readUtf8Line() ?: break
                val delta = parser.contentFromLine(line) ?: continue
                result.append(delta)
                withContext(Dispatchers.Main.immediate) { onDelta(delta) }
            }
        }
        result.toString().ifBlank { "The server returned an empty response." }
    }

    internal fun validateServerUrl(input: String): String {
        val normalized = input.trim().trimEnd('/')
        val uri = java.net.URI(normalized)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Use an HTTPS server URL. Unencrypted HTTP connections are disabled."
        }
        require(!uri.host.isNullOrBlank()) { "Enter a valid server URL" }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Server URL must not contain credentials, query parameters, or fragments"
        }
        return normalized
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
