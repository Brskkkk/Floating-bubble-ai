package com.idk.groqbubble

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BubbleService : Service() {

    companion object {
        var instance: BubbleService? = null
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var app: GroqBubbleApp
    private lateinit var chatAdapter: ChatAdapter
    private var recyclerView: RecyclerView? = null

    private val maxHistoryMessages = 8

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = application as GroqBubbleApp
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        instance = this
        startForegroundWithNotification()
        addBubble()
    }

    private fun startForegroundWithNotification() {
        val channelId = "groq_bubble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Groq Bubble", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Groq Bubble active")
            .setSmallIcon(R.drawable.ic_bubble)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1, notification)
    }

    private fun addBubble() {
        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.bubble_view, null)

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        windowManager.addView(bubbleView, bubbleParams)
        makeBubbleDraggable()

        bubbleView?.setOnClickListener {
            if (panelView == null) showPanel() else hidePanel()
        }
    }

    private fun makeBubbleDraggable() {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bubbleView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams!!.x
                    initialY = bubbleParams!!.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) moved = true
                    bubbleParams!!.x = initialX + dx
                    bubbleParams!!.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun showPanel() {
        val inflater = LayoutInflater.from(this)
        panelView = inflater.inflate(R.layout.chat_panel, null)

        val panelParams = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.65).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        recyclerView = panelView!!.findViewById<RecyclerView>(R.id.chatRecyclerView)
        chatAdapter = ChatAdapter(app.conversation)
        recyclerView!!.layoutManager = LinearLayoutManager(this)
        recyclerView!!.adapter = chatAdapter
        if (app.conversation.isNotEmpty()) {
            recyclerView!!.scrollToPosition(app.conversation.size - 1)
        }

        val input = panelView!!.findViewById<EditText>(R.id.messageInput)
        val sendBtn = panelView!!.findViewById<ImageButton>(R.id.sendButton)
        val attachBtn = panelView!!.findViewById<ImageButton>(R.id.attachButton)
        val closeBtn = panelView!!.findViewById<ImageButton>(R.id.closeButton)
        val modelSpinner = panelView!!.findViewById<Spinner>(R.id.modelSpinner)
        val conversationSpinner = panelView!!.findViewById<Spinner>(R.id.conversationSpinner)
        val newConversationButton = panelView!!.findViewById<Button>(R.id.newConversationButton)
        val saveConversationButton = panelView!!.findViewById<Button>(R.id.saveConversationButton)

        val options = listOf(GroqApiClient.TEXT_MODEL, GroqApiClient.ALT_TEXT_MODEL, GroqApiClient.VISION_MODEL)
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = modelAdapter
        modelSpinner.setSelection(options.indexOf(app.selectedModel).coerceAtLeast(0))
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                app.updateSelectedModel(options[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        refreshConversationSpinner(conversationSpinner)
        conversationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedConversation = app.conversations.getOrNull(position) ?: return
                app.switchConversation(selectedConversation.id)
                chatAdapter.notifyDataSetChanged()
                recyclerView?.scrollToPosition(app.conversation.size - 1)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        newConversationButton.setOnClickListener {
            app.createConversation("New chat")
            refreshConversationSpinner(conversationSpinner)
            Toast.makeText(this, "Started a new conversation", Toast.LENGTH_SHORT).show()
            chatAdapter.notifyDataSetChanged()
        }

        saveConversationButton.setOnClickListener {
            app.saveCurrentConversation()
            refreshConversationSpinner(conversationSpinner)
            Toast.makeText(this, "Conversation saved", Toast.LENGTH_SHORT).show()
        }

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                input.setText("")
                sendMessage(text, null)
            }
        }

        attachBtn.setOnClickListener {
            val pickIntent = Intent(this, MainActivity::class.java).apply {
                action = "PICK_IMAGE_FOR_BUBBLE"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(pickIntent)
        }

        closeBtn.setOnClickListener { hidePanel() }

        windowManager.addView(panelView, panelParams)
    }

    private fun hidePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        recyclerView = null
    }

    private fun sendMessage(text: String, imageBase64: String?) {
        val userMessage = GroqApiClient.ChatMessage("user", text, imageBase64)
        app.conversation.add(userMessage)
        app.saveCurrentConversation()
        chatAdapter.notifyItemInserted(app.conversation.size - 1)
        recyclerView?.scrollToPosition(app.conversation.size - 1)

        val assistantIndex = app.conversation.size
        val assistantMessage = GroqApiClient.ChatMessage("assistant", "")
        app.conversation.add(assistantMessage)
        app.saveCurrentConversation()
        chatAdapter.notifyItemInserted(assistantIndex)
        recyclerView?.scrollToPosition(assistantIndex)

        scope.launch {
            val historyToSend = app.conversation.dropLast(1).takeLast(maxHistoryMessages)
            val webContext = maybeSearchWeb(text)
            val prompt = buildString {
                append("You are a concise, helpful assistant inside a floating chat bubble on Android. Use Markdown and LaTeX when helpful, keep replies short, and avoid repeating the question.")
                if (webContext != null) {
                    append("\n\nWeb context:\n$webContext")
                }
            }

            val result = app.groqClient.streamChat(
                historyToSend,
                systemPrompt = prompt
            ) { chunk ->
                scope.launch {
                    assistantMessage.text += chunk
                    app.persistConversation()
                    chatAdapter.notifyItemChanged(assistantIndex)
                    recyclerView?.scrollToPosition(assistantIndex)
                }
            }

            result.onSuccess {
                app.persistConversation()
            }.onFailure { err ->
                assistantMessage.text = "Error: ${err.message}"
                app.persistConversation()
                chatAdapter.notifyItemChanged(assistantIndex)
                recyclerView?.scrollToPosition(assistantIndex)
            }
        }
    }

    private fun refreshConversationSpinner(spinner: Spinner) {
        val titles = app.conversations.map { it.title }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, titles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val activeIndex = app.conversations.indexOfFirst { it.id == app.activeConversationId }
        if (activeIndex >= 0) spinner.setSelection(activeIndex)
    }

    private suspend fun maybeSearchWeb(text: String): String? {
        val lower = text.lowercase()
        val shouldSearch = lower.contains("latest") || lower.contains("news") || lower.contains("today") || lower.contains("current") || lower.contains("weather") || lower.contains("price") || lower.contains("search") || lower.contains("who is") || lower.contains("what happened")
        if (!shouldSearch) return null
        return app.groqClient.searchWeb(text)
    }

    fun receiveImageMessage(caption: String, base64: String) {
        sendMessage(caption, base64)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        bubbleView?.let { windowManager.removeView(it) }
        panelView?.let { windowManager.removeView(it) }
    }
}
