package com.cute.moochie.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages chat operations with Firebase Realtime Database
 */
class ChatManager(
    private val context: Context,
    private val authManager: AuthManager = AuthManager(context)
) {
    private val TAG = "ChatManager"
    private val database = FirebaseDatabase.getInstance()
    private val messagesRef = database.getReference("chats")
    
    /**
     * Sends a message to a specific chat room identified by the 4-digit code
     */
    fun sendMessage(code: String, messageText: String, callback: (Boolean) -> Unit) {
        val currentUser = authManager.getCurrentUser() ?: run {
            Log.e(TAG, "Failed to send message: User not logged in")
            callback(false)
            return
        }
        
        val chatRoomRef = messagesRef.child(code)
        val newMessageRef = chatRoomRef.push()
        val messageId = newMessageRef.key ?: UUID.randomUUID().toString()
        
        val timestamp = System.currentTimeMillis()
        
        val message = ChatMessage(
            messageId = messageId,
            senderId = currentUser.uid,
            senderName = currentUser.displayName ?: "Anonymous",
            messageText = messageText,
            timestamp = timestamp,
            code = code,
            isRead = false
        )
        
        newMessageRef.setValue(message)
            .addOnSuccessListener {
                Log.d(TAG, "Message sent successfully")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error sending message: ${e.message}", e)
                callback(false)
            }
    }
    
    /**
     * Listens for new messages in a specific chat room
     */
    fun getChatMessages(code: String, callback: (List<ChatMessage>) -> Unit): ValueEventListener {
        val chatRoomRef = messagesRef.child(code)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = mutableListOf<ChatMessage>()
                
                for (messageSnapshot in snapshot.children) {
                    try {
                        val message = messageSnapshot.getValue(ChatMessage::class.java)
                        message?.let { messages.add(it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message: ${e.message}")
                    }
                }
                
                // Sort messages by timestamp
                messages.sortBy { it.timestamp }
                
                callback(messages)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error getting messages: ${error.message}", error.toException())
                callback(emptyList())
            }
        }
        
        chatRoomRef.addValueEventListener(listener)
        return listener
    }
    
    /**
     * Formats timestamp to readable time
     */
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Formats timestamp to readable date and time
     */
    fun formatFullTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Removes a listener for chat messages
     */
    fun removeMessageListener(code: String, listener: ValueEventListener) {
        val chatRoomRef = messagesRef.child(code)
        chatRoomRef.removeEventListener(listener)
    }
} 