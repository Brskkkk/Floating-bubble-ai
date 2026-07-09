package com.idk.groqbubble

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class GroqBubbleApp : Application() {

    data class ChatConversation(
        val id: String = UUID.randomUUID().toString(),
        var title: String = "New chat",
        val messages: MutableList<GroqApiClient.ChatMessage> = mutableListOf()
    )

    val conversation = mutableListOf<GroqApiClient.ChatMessage>()
    val conversations = mutableListOf<ChatConversation>()
    var activeConversationId: String? = null
        private set

    lateinit var groqClient: GroqApiClient
        private set

    var selectedModel: String = GroqApiClient.TEXT_MODEL
        private set

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
        val key = prefs.getString("groq_api_key", "") ?: ""
        selectedModel = prefs.getString("selected_model", GroqApiClient.TEXT_MODEL)
            ?: GroqApiClient.TEXT_MODEL
        groqClient = GroqApiClient(key, selectedModel)
        reloadConversationsFromStorage()
    }

    fun updateApiKey(newKey: String) {
        val prefs = getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
        prefs.edit().putString("groq_api_key", newKey).apply()
        groqClient = GroqApiClient(newKey, selectedModel)
    }

    fun updateSelectedModel(model: String) {
        selectedModel = model
        groqClient.updateModel(model)
        val prefs = getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
        prefs.edit().putString("selected_model", model).apply()
    }

    fun createConversation(title: String = "New chat"): ChatConversation {
        val chatConversation = ChatConversation(title = title.ifBlank { "New chat" })
        conversations.add(chatConversation)
        activeConversationId = chatConversation.id
        conversation.clear()
        persistConversations()
        return chatConversation
    }

    fun switchConversation(id: String) {
        val target = conversations.firstOrNull { it.id == id } ?: return
        activeConversationId = target.id
        conversation.clear()
        conversation.addAll(target.messages)
        persistConversations()
    }

    fun saveCurrentConversation() {
        val active = ensureActiveConversation()
        active.messages.clear()
        active.messages.addAll(conversation)
        if (active.title.isBlank() || active.title == "New chat") {
            active.title = deriveConversationTitle(conversation)
        }
        persistConversations()
    }

    fun deleteConversation(id: String) {
        conversations.removeAll { it.id == id }
        if (activeConversationId == id) {
            activeConversationId = conversations.firstOrNull()?.id
            conversation.clear()
            if (activeConversationId != null) {
                conversations.firstOrNull { it.id == activeConversationId }?.let {
                    conversation.addAll(it.messages)
                }
            }
        }
        persistConversations()
    }

    fun reloadConversationsFromStorage() {
        val prefs = getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
        loadConversations(prefs)
    }

    private fun ensureActiveConversation(): ChatConversation {
        val current =
            activeConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
        return if (current != null) {
            current
        } else {
            createConversation("New chat")
        }
    }

    private fun deriveConversationTitle(messages: List<GroqApiClient.ChatMessage>): String {
        val firstUser = messages.firstOrNull { it.role == "user" }?.text?.trim().orEmpty()
        return if (firstUser.isBlank()) "New chat" else firstUser.take(24)
            .replace(Regex("\\s+"), " ")
    }

    private fun persistConversations() {
        val prefs = getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
        val active = ensureActiveConversation()
        active.messages.clear()
        active.messages.addAll(conversation)

        val conversationsJson = JSONArray()
        conversations.forEach { chatConversation ->
            val obj = JSONObject().apply {
                put("id", chatConversation.id)
                put("title", chatConversation.title)
                val messagesJson = JSONArray()
                chatConversation.messages.forEach { message ->
                    val messageObj = JSONObject().apply {
                        put("role", message.role)
                        put("text", message.text)
                        if (message.imageBase64 != null) {
                            put("imageBase64", message.imageBase64)
                        }
                    }
                    messagesJson.put(messageObj)
                }
                put("messages", messagesJson)
            }
            conversationsJson.put(obj)
        }

        prefs.edit()
            .putString("conversations", conversationsJson.toString())
            .putString("active_conversation_id", activeConversationId ?: active.id)
            .apply()
    }

    private fun loadConversations(prefs: android.content.SharedPreferences) {
        val raw = prefs.getString("conversations", null)
        if (raw != null) {
            try {
                conversations.clear()
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val chatConversation = ChatConversation(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", "New chat")
                    )
                    val messagesArray = obj.optJSONArray("messages") ?: JSONArray()
                    for (j in 0 until messagesArray.length()) {
                        val messageObj = messagesArray.getJSONObject(j)
                        val image =
                            messageObj.optString("imageBase64", "").takeIf { it.isNotEmpty() }
                        chatConversation.messages.add(
                            GroqApiClient.ChatMessage(
                                role = messageObj.optString("role", "assistant"),
                                text = messageObj.optString("text", ""),
                                imageBase64 = image
                            )
                        )
                    }
                    conversations.add(chatConversation)
                }
                activeConversationId = prefs.getString("active_conversation_id", null)
                if (activeConversationId == null || conversations.none { it.id == activeConversationId }) {
                    activeConversationId = conversations.firstOrNull()?.id
                }
                conversation.clear()
                if (activeConversationId != null) {
                    conversations.firstOrNull { it.id == activeConversationId }?.let { active ->
                        conversation.addAll(active.messages)
                    }
                }
                return
            } catch (_: Exception) {
                conversations.clear()
            }
        }
        createConversation("New chat")
    }
}
