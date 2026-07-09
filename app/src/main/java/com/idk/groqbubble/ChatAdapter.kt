package com.idk.groqbubble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin

class ChatAdapter(private val messages: MutableList<GroqApiClient.ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private var markwon: Markwon? = null

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.messageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)

        // Initialize Markwon once with LaTeX support
        if (markwon == null) {
            markwon = Markwon.builder(parent.context)
                .usePlugin(JLatexMathPlugin.create(parent.context.resources.displayMetrics.scaledDensity))
                .build()
        }

        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        val prefix = if (msg.role == "user") "You" else "Assistant"

        // Format as markdown: prefix, then the message content
        val content = if (msg.role == "user") {
            "**$prefix:** ${msg.text}"
        } else {
            msg.text
        }

        // Render markdown/latex using Markwon
        markwon?.setMarkdown(holder.text, content)
    }

    override fun getItemCount() = messages.size
}
