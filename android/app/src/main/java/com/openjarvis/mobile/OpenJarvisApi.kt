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
    val images = (listOfNotNull(message.imageBase64) + message.imageBase64List)
        .filter(String::isNotBlank)
    if (images.isEmpty()) {
        payload.put("content", message.content)
    } else {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", message.content))
        images.forEach { image ->
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image")),
            )
        }
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
        val requestBuilder = Request.Builder()
            .url("$url/v1/models")
            .get()
        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(300)
                val message = when (response.code) {
                    401, 403 -> if (apiKey.isBlank()) {
                        "This server requires an API key. Enter the server API key in Settings."
                    } else {
                        "API key rejected (HTTP ${response.code}). Check the server API key."
                    }
                    404 -> "Server is reachable, but /v1/models was not found. Check the server URL and API path."
                    else -> "Server returned HTTP ${response.code}${if (detail.isBlank()) "" else ": $detail"}"
                }
                error(message)
            }
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
        val requestBuilder = Request.Builder()
            .url("${validateServerUrl(baseUrl)}/v1/chat/completions")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody(JSON))
        if (apiKey.isNotBlank()) requestBuilder.header("Authorization", "Bearer $apiKey")
        val request = requestBuilder.build()

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
        val uri = try {
            java.net.URI(normalized)
        } catch (_: Exception) {
            error("Server URL is malformed. Enter a complete URL such as https://server.example or http://127.0.0.1:8080.")
        }
        val host = uri.host?.lowercase().orEmpty()
        require(host.isNotBlank()) {
            "Enter a valid server URL including its host name."
        }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Server URL must not contain credentials, query parameters, or fragments."
        }
        val isLoopback = host == "127.0.0.1" || host == "localhost" || host == "::1"
        val isHttps = uri.scheme.equals("https", ignoreCase = true)
        val isLocalHttp = uri.scheme.equals("http", ignoreCase = true) && isLoopback
        require(isHttps || isLocalHttp) {
            "Remote servers must use HTTPS. Plain HTTP is allowed only for this device's loopback address (127.0.0.1 or localhost)."
        }
        return normalized
    }

    internal fun isLocalLoopbackHttp(input: String): Boolean = try {
        val uri = java.net.URI(validateServerUrl(input))
        uri.scheme.equals("http", ignoreCase = true) &&
            (uri.host.equals("127.0.0.1", ignoreCase = true) ||
                uri.host.equals("localhost", ignoreCase = true) ||
                uri.host.equals("::1", ignoreCase = true))
    } catch (_: Exception) {
        false
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
