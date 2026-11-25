package com.openjarvis.mobile

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenJarvisApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun checkConnection(baseUrl: String, apiKey: String): List<String> {
        val url = validatedBaseUrl(baseUrl)
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

    fun sendMessage(baseUrl: String, apiKey: String, model: String, message: String): String {
        require(model.isNotBlank()) { "Choose a model before sending a message" }
        require(message.isNotBlank()) { "Message cannot be blank" }
        val payload = JSONObject()
            .put("model", model)
            .put("messages", org.json.JSONArray().put(
                JSONObject().put("role", "user").put("content", message),
            ))
            .put("stream", false)
            .toString()
        val request = Request.Builder()
            .url("${validatedBaseUrl(baseUrl)}/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Server returned HTTP ${response.code}")
            val choices = JSONObject(body).optJSONArray("choices")
                ?: error("Server response did not contain chat choices")
            val content = choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            return content.ifBlank { "The server returned an empty response." }
        }
    }

    private fun validatedBaseUrl(input: String): String {
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
