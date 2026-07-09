package com.idk.groqbubble

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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

    private fun buildStyledMessage(prefix: String, rawText: String): CharSequence {
        val cleaned = rawText.trim().ifEmpty { "..." }
        val displayed = "$prefix: $cleaned"
        return formatText(displayed)
    }

    private fun formatText(input: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder(input)
        val textColor = holderColor()

        val fencedCode = Regex("```([\\s\\S]*?)```")
        fencedCode.findAll(input).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            builder.setSpan(BackgroundColorSpan(textColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(LeadingMarginSpan.Standard(24), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val boldRegex = Regex("(\\*\\*[^*]+\\*\\*)")
        boldRegex.findAll(input).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val italicRegex = Regex("(\\*[^*]+\\*)")
        italicRegex.findAll(input).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val inlineCodeRegex = Regex("(`[^`]+`)")
        inlineCodeRegex.findAll(input).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            builder.setSpan(BackgroundColorSpan(textColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val latexRegex = Regex("(\\$[^$]+\\$)")
        latexRegex.findAll(input).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return builder
    }

    private fun holderColor(): Int {
        return ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
    }
}
