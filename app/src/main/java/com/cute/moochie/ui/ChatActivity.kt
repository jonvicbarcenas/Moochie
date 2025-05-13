package com.cute.moochie.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cute.moochie.R
import com.cute.moochie.adapter.ChatAdapter
import com.cute.moochie.data.AuthManager
import com.cute.moochie.data.ChatManager
import com.cute.moochie.data.ChatMessage
import com.cute.moochie.data.UserPreferences
import com.cute.moochie.service.ChatNotificationService
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.ValueEventListener

class ChatActivity : AppCompatActivity() {
    
    private val TAG = "ChatActivity"
    private lateinit var recyclerView: RecyclerView
    private lateinit var codeTextView: TextView
    private lateinit var emptyView: TextView
    private lateinit var messageEditText: TextInputEditText
    private lateinit var sendButton: MaterialButton
    
    private lateinit var chatManager: ChatManager
    private lateinit var authManager: AuthManager
    private lateinit var userPreferences: UserPreferences
    private lateinit var adapter: ChatAdapter
    
    private var userCode: String = ""
    private lateinit var messageListener: ValueEventListener
    private var lastMessageCount = 0
    private var isActivityVisible = false
    
    companion object {
        // Match the channel ID in ChatNotificationService
        private const val CHAT_NOTIFICATION_CHANNEL = "moochie_chat_channel"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        Log.d(TAG, "onCreate: Initializing ChatActivity")
        
        // Setup toolbar with back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Chat"
        
        // Initialize components
        recyclerView = findViewById(R.id.messages_recycler_view)
        codeTextView = findViewById(R.id.code_text_view)
        emptyView = findViewById(R.id.empty_view)
        messageEditText = findViewById(R.id.message_edit_text)
        sendButton = findViewById(R.id.send_button)
        
        // Initialize managers
        chatManager = ChatManager(this)
        authManager = AuthManager(this)
        userPreferences = UserPreferences(this)
        
        // Get the user code
        userCode = userPreferences.getUserCode()
        if (userCode.isEmpty()) {
            Toast.makeText(this, "Please set your 4-digit code first", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Setup code title
        codeTextView.text = "Chat Room - Code: $userCode"
        
        // Setup current user ID
        val currentUserId = authManager.getCurrentUser()?.uid ?: ""
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "You must be logged in to use chat", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Setup RecyclerView
        adapter = ChatAdapter(emptyList(), currentUserId, chatManager)
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Messages appear from bottom
        }
        recyclerView.adapter = adapter
        
        // Setup send button
        sendButton.setOnClickListener {
            sendMessage()
        }
        
        // Create notification channel for Android O and above
        createNotificationChannel()
        
        // Load messages
        loadMessages()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Refreshing chat data")
        isActivityVisible = true
        
        // Stop the background notification service since activity is visible
        ChatNotificationService.stopService(this)
        
        // Clear any existing chat notifications
        clearChatNotifications()
    }
    
    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        
        // Remove listener when activity is paused
        if (::messageListener.isInitialized) {
            chatManager.removeMessageListener(userCode, messageListener)
        }
        
        // Start the background notification service if we're logged in
        if (authManager.getCurrentUser() != null) {
            ChatNotificationService.startService(this)
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun loadMessages() {
        Log.d(TAG, "Loading messages for code: $userCode")
        
        messageListener = chatManager.getChatMessages(userCode) { messages ->
            Log.d(TAG, "Received ${messages.size} messages")
            
            // Check if there are new messages
            if (messages.size > lastMessageCount && lastMessageCount > 0) {
                // Get the new messages
                val newMessages = messages.subList(lastMessageCount, messages.size)
                
                // Show notification for new messages only when activity is in foreground
                for (message in newMessages) {
                    val currentUserId = authManager.getCurrentUser()?.uid ?: ""
                    // Only show notification for messages from others and when app is in foreground
                    if (message.senderId != currentUserId && isActivityVisible) {
                        showNewMessageNotification(message)
                    }
                }
            }
            
            // Update the message count
            lastMessageCount = messages.size
            
            runOnUiThread {
                updateMessagesList(messages)
            }
        }
    }
    
    private fun updateMessagesList(messages: List<ChatMessage>) {
        Log.d(TAG, "Updating messages list with ${messages.size} items")
        adapter.updateMessages(messages)
        
        if (messages.isEmpty()) {
            Log.d(TAG, "No messages to display, showing empty view")
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            Log.d(TAG, "Displaying messages in RecyclerView")
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            // Scroll to the bottom
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }
    
    private fun sendMessage() {
        val messageText = messageEditText.text.toString().trim()
        
        if (messageText.isEmpty()) {
            return
        }
        
        sendButton.isEnabled = false
        
        chatManager.sendMessage(userCode, messageText) { success ->
            runOnUiThread {
                if (success) {
                    messageEditText.text?.clear()
                } else {
                    Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
                sendButton.isEnabled = true
            }
        }
    }
    
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Chat Notifications"
            val descriptionText = "Notifications for new chat messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHAT_NOTIFICATION_CHANNEL, name, importance).apply {
                description = descriptionText
            }
            
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showNewMessageNotification(message: ChatMessage) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, pendingIntentFlags
        )
        
        val messageText = "${message.senderName}: ${message.messageText}"
        val messageTime = chatManager.formatTimestamp(message.timestamp)
        
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, CHAT_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New Chat Message")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setSubText(messageTime)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Use a random int as notification ID to allow multiple notifications
        val notificationId = message.messageId.hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
    
    /**
     * Clears all chat notifications when entering the chat room
     */
    private fun clearChatNotifications() {
        Log.d(TAG, "Clearing all chat notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }
}