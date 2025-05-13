package com.cute.moochie.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.*
import java.util.*

/**
 * Manages notification data operations with Firebase Realtime Database
 */
class NotificationManager(private val context: Context) {
    
    private val TAG = "NotificationManager"
    private val database = FirebaseDatabase.getInstance()
    private val notificationsRef = database.getReference("notifications")
    private val authManager = AuthManager(context)
    
    init {
        // Initialize database with keepSynced to improve offline capabilities
        notificationsRef.keepSynced(true)
    }
    
    /**
     * Gets all notifications for all users
     */
    fun getUserNotifications(callback: (List<NotificationData>) -> Unit) {
        val userId = authManager.getUserId()
        if (userId == null) {
            Log.e(TAG, "No user ID found, cannot fetch notifications")
            callback(emptyList())
            return
        }
        
        Log.d(TAG, "Fetching all notifications from database")
        
        // Changed to fetch all notifications without filtering by userId
        notificationsRef.orderByChild("postTime")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Notification data received. Count: ${snapshot.childrenCount}")
                    
                    val notifications = mutableListOf<NotificationData>()
                    for (notificationSnapshot in snapshot.children) {
                        try {
                            val notification = notificationSnapshot.getValue(NotificationData::class.java)
                            if (notification != null) {
                                Log.d(TAG, "Found notification: ${notification.title} from ${notification.appName}, userId: ${notification.userId}")
                                notifications.add(notification)
                            } else {
                                Log.e(TAG, "Failed to parse notification data from: ${notificationSnapshot.key}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing notification: ${e.message}", e)
                        }
                    }
                    
                    // Sort by post time (newest first)
                    notifications.sortByDescending { it.postTime }
                    
                    Log.d(TAG, "Returning ${notifications.size} notifications to UI")
                    callback(notifications)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error getting notifications: ${error.message}", error.toException())
                    callback(emptyList())
                }
            })
    }
    
    /**
     * Deletes all notifications for the current user
     */
    fun deleteAllUserNotifications(callback: (Boolean) -> Unit) {
        val userId = authManager.getUserId()
        if (userId == null) {
            Log.e(TAG, "No user ID found, cannot delete notifications")
            callback(false)
            return
        }
        
        Log.d(TAG, "Deleting all notifications for user: $userId")
        
        notificationsRef.orderByChild("userId").equalTo(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val updates = HashMap<String, Any?>()
                    
                    Log.d(TAG, "Found ${snapshot.childrenCount} notifications to delete")
                    
                    for (notificationSnapshot in snapshot.children) {
                        updates[notificationSnapshot.key!!] = null
                    }
                    
                    if (updates.isEmpty()) {
                        Log.d(TAG, "No notifications to delete")
                        callback(true)
                        return
                    }
                    
                    notificationsRef.updateChildren(updates)
                        .addOnSuccessListener {
                            Log.d(TAG, "Successfully deleted ${updates.size} notifications")
                            callback(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error deleting notifications: ${e.message}", e)
                            callback(false)
                        }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error getting notifications to delete: ${error.message}", error.toException())
                    callback(false)
                }
            })
    }
    
    /**
     * Check if notification listener permission is granted
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        
        val isEnabled = if (flat != null && flat.isNotEmpty()) {
            val names = flat.split(":")
            names.any { name ->
                val componentName = android.content.ComponentName.unflattenFromString(name)
                componentName != null && componentName.packageName == packageName
            }
        } else {
            false
        }
        
        Log.d(TAG, "Notification listener enabled: $isEnabled")
        return isEnabled
    }
} 