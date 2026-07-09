package com.idk.groqbubble

import android.app.Application

class GroqBubbleApp : Application() {

    // Shared in-memory conversation so the bubble and any activity see the same state.
    // For persistence across process death, swap this for a Room DB or DataStore.
    val conversation = mutableListOf<GroqApiClient.ChatMessage>()

    lateinit var groqClient: GroqApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
        val key = prefs.getString("groq_api_key", "") ?: ""
        groqClient = GroqApiClient(key)
    }

    fun updateApiKey(newKey: String) {
        getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
            .edit().putString("groq_api_key", newKey).apply()
        groqClient = GroqApiClient(newKey)
    }
}
