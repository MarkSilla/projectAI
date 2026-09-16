package com.marksilla.auraagent

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class OnlineAiClient(
    context: Context
) {
    private val securePreferences = SecureAiPreferences(context)
    private val preferences =
        context.getSharedPreferences("aura_preferences", Context.MODE_PRIVATE)

    val isConfigured: Boolean
        get() = securePreferences.getApiKey().orEmpty().isNotBlank()

    @Volatile
    var lastError: String? = null
        private set

    fun complete(
        prompt: String,
        recentContext: List<String> = emptyList()
    ): String? {
        val apiKey = securePreferences.getApiKey().orEmpty()
        if (apiKey.isBlank()) {
            lastError = "No API key is configured"
            return null
        }

        lastError = null

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

        return try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode !in 200..299) {
                val errorBody =
                    connection.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                lastError =
                    "HTTP ${connection.responseCode}" +
                        if (errorBody.isBlank()) {
                            ""
                        } else {
                            ": ${errorBody.take(180)}"
                        }
                null
            } else {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            lastError = "Could not reach the AI endpoint"
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val KEY_API_KEY = "online_ai_api_key"
        const val KEY_ENDPOINT = "online_ai_endpoint"
        const val KEY_MODEL = "online_ai_model"
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}
