package com.idk.groqbubble

import android.util.Base64
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Minimal Groq (OpenAI-compatible) chat completions client.
 * It supports streaming replies, web search context, and model switching.
 */
class GroqApiClient(private val apiKey: String, private var selectedModel: String = TEXT_MODEL) {

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val TEXT_MODEL = "openai/gpt-oss-120b"
        const val ALT_TEXT_MODEL = "llama-3.3-70b-versatile"
        const val VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class ChatMessage(var role: String, var text: String, var imageBase64: String? = null)

    fun updateModel(model: String) {
        selectedModel = model
    }

    suspend fun streamChat(
        history: List<ChatMessage>,
        systemPrompt: String? = null,
        onToken: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val hasImage = history.any { it.imageBase64 != null }
            val model = if (hasImage) VISION_MODEL else selectedModel
            val messagesJson = buildMessagesJson(history, systemPrompt)

            val bodyJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesJson)
                put("temperature", 0.7)
                put("max_tokens", 700)
                put("stream", true)
            }

            val body = bodyJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body ?: throw Exception("Empty response from Groq")
                if (!response.isSuccessful) {
                    throw Exception("Groq API error ${response.code}: ${responseBody.string()}")
                }

                val builder = StringBuilder()
                responseBody.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty() || payload == "[DONE]") continue
                        val json = JSONObject(payload)
                        val choices = json.optJSONArray("choices") ?: continue
                        val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: continue
                        val chunk = delta.optString("content", "")
                        if (chunk.isNotEmpty()) {
                            builder.append(chunk)
                            onToken(chunk)
                        }
                    }
                }
                Result.success(builder.toString())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChat(history: List<ChatMessage>, systemPrompt: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            val builder = StringBuilder()
            streamChat(history, systemPrompt) { chunk -> builder.append(chunk) }.map { builder.toString() }
        }

    suspend fun searchWeb(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://r.jina.ai/http://html.duckduckgo.com/html/?q=$encoded")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val text = response.body?.string() ?: return@withContext null
                val lines = text.lines().filter { it.isNotBlank() }.take(8)
                if (lines.isEmpty()) return@withContext null
                lines.joinToString("\n")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildMessagesJson(history: List<ChatMessage>, systemPrompt: String?): JSONArray {
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
        return messagesJson
    }

    fun encodeImage(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)
}
