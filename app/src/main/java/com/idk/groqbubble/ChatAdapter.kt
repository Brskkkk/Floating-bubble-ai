package com.idk.groqbubble

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: MutableList<GroqApiClient.ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.messageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        val prefix = if (msg.role == "user") "You" else "AI"
        holder.text.text = buildStyledMessage(prefix, msg.text)
        holder.text.setTypeface(null, if (msg.role == "assistant") Typeface.NORMAL else Typeface.BOLD)
    }

    override fun getItemCount() = messages.size

    private fun buildStyledMessage(prefix: String, rawText: String): String {
        val cleaned = rawText.trim().ifEmpty { "..." }
        return if (prefix == "You") {
            "$prefix: $cleaned"
        } else {
            "$prefix: $cleaned"
        }
    }
}
