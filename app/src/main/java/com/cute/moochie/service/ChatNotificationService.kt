package com.cute.moochie.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cute.moochie.R
import com.cute.moochie.data.AuthManager
import com.cute.moochie.data.ChatManager
import com.cute.moochie.data.ChatMessage
import com.cute.moochie.data.UserPreferences
import com.cute.moochie.ui.ChatActivity
import com.google.firebase.database.ValueEventListener

class ChatNotificationService : Service() {
    
    private val TAG = "ChatNotificationService"
    private lateinit var chatManager: ChatManager
    private lateinit var authManager: AuthManager
    private lateinit var userPreferences: UserPreferences
    private var userCode: String = ""
    private lateinit var messageListener: ValueEventListener
    private var lastMessageCount = 0
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize managers
        chatManager = ChatManager(this)
        authManager = AuthManager(this)
        userPreferences = UserPreferences(this)
        
        // Get the user code
        userCode = userPreferences.getUserCode()
        if (userCode.isEmpty()) {
            Log.e(TAG, "No user code set, stopping service")
            stopSelf()
            return
        }
        
        // Create notification channel (required for Android O+)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        // Start as foreground service with a minimal notification
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        
        // Start listening for chat messages
        startChatListener()

        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        // Remove listener when service is destroyed
        if (::messageListener.isInitialized) {
            chatManager.removeMessageListener(userCode, messageListener)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun startChatListener() {
        Log.d(TAG, "Starting chat listener for code: $userCode")
        
        messageListener = chatManager.getChatMessages(userCode) { messages ->
            Log.d(TAG, "Received ${messages.size} messages")
            
            // Check if there are new messages
            if (messages.size > lastMessageCount && lastMessageCount > 0) {
                // Get the new messages
                val newMessages = messages.subList(lastMessageCount, messages.size)
                
                // Show notification for new messages
                for (message in newMessages) {
                    val currentUserId = authManager.getCurrentUser()?.uid ?: ""
                    // Only show notification for messages from others
                    if (message.senderId != currentUserId) {
                        showNewMessageNotification(message)
                    }
                }
            }
            
            // Update the message count
            lastMessageCount = messages.size
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the high importance channel for actual chat notifications
            val chatChannel = NotificationChannel(
                CHAT_NOTIFICATION_CHANNEL,
                "Chat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
            }
            
            // Create a separate minimal importance channel for the foreground service
            val serviceChannel = NotificationChannel(
                SERVICE_NOTIFICATION_CHANNEL,
                "Background Services",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent notifications for background services"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                importance = NotificationManager.IMPORTANCE_MIN
            }
            
            // Register the channels with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(chatChannel)
            notificationManager.createNotificationChannel(serviceChannel)
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
        
        val notificationId = Math.abs(message.messageId.hashCode())
        if (notificationId != FOREGROUND_NOTIFICATION_ID) {
            notificationManager.notify(notificationId, notificationBuilder.build())
        }
    }
    
    private fun createForegroundNotification(): Notification {
        // Create a minimal notification that won't crash
        return NotificationCompat.Builder(this, SERVICE_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher) // Must use a valid icon resource
            .setContentTitle("") // Empty title
            .setContentText("") // Empty text
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setSound(null)
            .setVibrate(null)
            .setShowWhen(false) // Don't show timestamp
            .build()
    }
    
    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 8888
        private const val CHAT_NOTIFICATION_CHANNEL = "moochie_chat_channel"
        private const val SERVICE_NOTIFICATION_CHANNEL = "moochie_service_channel"
        
        fun startService(context: Context) {
            val intent = Intent(context, ChatNotificationService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, ChatNotificationService::class.java)
            context.stopService(intent)
        }
    }
}