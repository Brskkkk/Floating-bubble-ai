package com.idk.groqbubble

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Groq (OpenAI-compatible) chat completions client.
 * Docs: https://console.groq.com/docs/api-reference
 *
 * Text model: openai/gpt-oss-120b  (or llama-3.3-70b-versatile)
 * Vision model: meta-llama/llama-4-scout-17b-16e-instruct (check current Groq model list,
 * vision-capable models rotate — verify at console.groq.com/docs/models)
 */
class GroqApiClient(private val apiKey: String) {

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val TEXT_MODEL = "openai/gpt-oss-120b"
        const val VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class ChatMessage(val role: String, val text: String, val imageBase64: String? = null)

    /**
     * Sends a full conversation history and returns the assistant's reply text.
     * If any message contains an image, the vision model is used automatically.
     */
    suspend fun sendChat(history: List<ChatMessage>, systemPrompt: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val hasImage = history.any { it.imageBase64 != null }
                val model = if (hasImage) VISION_MODEL else TEXT_MODEL

                val messagesJson = JSONArray()

                if (systemPrompt != null) {
                    messagesJson.put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }

                for (msg in history) {
                    val messageObj = JSONObject()
                    messageObj.put("role", msg.role)

                    if (msg.imageBase64 != null) {
                        // Vision content is an array of content blocks
                        val contentArray = JSONArray()
                        contentArray.put(JSONObject().apply {
                            put("type", "text")
                            put("text", msg.text)
                        })
                        contentArray.put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,${msg.imageBase64}")
                            })
                        })
                        messageObj.put("content", contentArray)
                    } else {
                        messageObj.put("content", msg.text)
                    }
                    messagesJson.put(messageObj)
                }

                val bodyJson = JSONObject().apply {
                    put("model", model)
                    put("messages", messagesJson)
                    put("temperature", 0.7)
                    put("max_tokens", 1024)
                }

                val body = bodyJson.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(BASE_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            Exception("Groq API error ${response.code}: $responseBody")
                        )
                    }
                    val json = JSONObject(responseBody)
                    val reply = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    Result.success(reply)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Helper: encode raw image bytes to base64 for sending to the vision model. */
    fun encodeImage(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)
}
