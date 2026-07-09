package com.idk.groqbubble

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handlePickedImage(it, "I attached an image, can you analyze it?") }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            proceedToOverlayCheck()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val app = application as GroqBubbleApp
        val prefs = getSharedPreferences("groq_bubble_prefs", MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("groq_api_key", ""))

        findViewById<Button>(R.id.saveKeyButton).setOnClickListener {
            val key = apiKeyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            app.updateApiKey(key)
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.startBubbleButton).setOnClickListener {
            if (prefs.getString("groq_api_key", "").isNullOrEmpty()) {
                Toast.makeText(this, "Please save API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requestOverlayPermissionThenStart()
        }

        findViewById<Button>(R.id.loadConversationsButton).setOnClickListener {
            app.reloadConversationsFromStorage()
            Toast.makeText(this, "Loaded saved conversations", Toast.LENGTH_SHORT).show()
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            "PICK_IMAGE_FOR_BUBBLE" -> imagePicker.launch("image/*")
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null && intent.type?.startsWith("image/") == true) {
                    handlePickedImage(uri, "I shared this image, can you analyze it?")
                    requestOverlayPermissionThenStart()
                }
            }
        }
    }

    private fun handlePickedImage(uri: Uri, caption: String) {
        val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return
        val app = application as GroqBubbleApp
        val base64 = app.groqClient.encodeImage(bytes)
        BubbleService.instance?.receiveImageMessage(caption, base64)
            ?: run {
                app.conversation.add(
                    GroqApiClient.ChatMessage("user", caption, base64)
                )
                app.saveCurrentConversation()
            }
        Toast.makeText(this, "Image sent to bubble!", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlayPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        proceedToOverlayCheck()
    }

    private fun proceedToOverlayCheck() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(
                this,
                "Grant overlay permission, then tap Start Bubble again",
                Toast.LENGTH_LONG
            ).show()
        } else {
            startService(Intent(this, BubbleService::class.java))
            Toast.makeText(this, "Bubble started! Minimize this app.", Toast.LENGTH_SHORT).show()
        }
    }
}
