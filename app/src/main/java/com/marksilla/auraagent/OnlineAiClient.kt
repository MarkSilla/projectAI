package com.marksilla.auraagent

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class OnlineAiClient(
    context: Context
) {
    private val preferences =
        context.getSharedPreferences("aura_preferences", Context.MODE_PRIVATE)

    val isConfigured: Boolean
        get() = preferences.getString(KEY_API_KEY, null).orEmpty().isNotBlank()

    fun complete(
        prompt: String,
        recentContext: List<String> = emptyList()
    ): String? {
        val apiKey = preferences.getString(KEY_API_KEY, null).orEmpty()
        if (apiKey.isBlank()) {
            return null
        }

        val endpoint =
            preferences.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT)
                .orEmpty()
                .ifBlank { DEFAULT_ENDPOINT }
        val model =
            preferences.getString(KEY_MODEL, DEFAULT_MODEL)
                .orEmpty()
                .ifBlank { DEFAULT_MODEL }
        val contextText = recentContext.takeLast(6).joinToString("\n")
        val body =
            JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    org.json.JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put(
                                    "content",
                                    "You are AURA, a concise Android assistant. " +
                                        "Answer clearly and do not claim actions you did not perform."
                                )
                        )
                        .put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "content",
                                    "Recent context:\n$contextText\n\nUser request:\n$prompt"
                                )
                        )
                )
                .toString()

        val connection =
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
            }

        return runCatching {
            connection.use { request ->
                request.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }

                if (request.responseCode !in 200..299) {
                    return@runCatching null
                }

                val response = request.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    companion object {
        const val KEY_API_KEY = "online_ai_api_key"
        const val KEY_ENDPOINT = "online_ai_endpoint"
        const val KEY_MODEL = "online_ai_model"
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
