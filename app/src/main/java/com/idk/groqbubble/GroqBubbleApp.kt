package com.idk.groqbubble

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject

class GroqBubbleApp : Application() {

    val conversation = mutableListOf<GroqApiClient.ChatMessage>()

    lateinit var groqClient: GroqApiClient
        private set

    var selectedModel: String = GroqApiClient.TEXT_MODEL
        private set

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
        val key = prefs.getString("groq_api_key", "") ?: ""
        selectedModel = prefs.getString("selected_model", GroqApiClient.TEXT_MODEL) ?: GroqApiClient.TEXT_MODEL
        groqClient = GroqApiClient(key, selectedModel)
        loadConversation(prefs)
    }

    fun updateApiKey(newKey: String) {
        getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
            .edit().putString("groq_api_key", newKey).apply()
        groqClient = GroqApiClient(newKey, selectedModel)
        persistConversation()
    }

    fun updateSelectedModel(model: String) {
        selectedModel = model
        getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
            .edit().putString("selected_model", model).apply()
        groqClient.updateModel(model)
        persistConversation()
    }

    fun persistConversation() {
        val prefs = getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
        val json = JSONArray()
        conversation.forEach { message ->
            val obj = JSONObject().apply {
                put("role", message.role)
                put("text", message.text)
                if (message.imageBase64 != null) {
                    put("imageBase64", message.imageBase64)
                }
            }
            json.put(obj)
        }
        prefs.edit().putString("conversation", json.toString()).apply()
    }

    private fun loadConversation(prefs: android.content.SharedPreferences) {
        val raw = prefs.getString("conversation", null) ?: return
        try {
            conversation.clear()
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val image = obj.optString("imageBase64", "").takeIf { it.isNotEmpty() }
                conversation.add(
                    GroqApiClient.ChatMessage(
                        role = obj.optString("role", "assistant"),
                        text = obj.optString("text", ""),
                        imageBase64 = image
                    )
                )
            }
        } catch (_: Exception) {
            conversation.clear()
        }
    }
}
