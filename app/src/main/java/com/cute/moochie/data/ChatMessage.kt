package com.cute.moochie.data

/**
 * Data class representing a chat message in the Firebase Realtime Database
 */
data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val messageText: String = "",
    val timestamp: Long = 0L,
    val code: String = "",    // The 4-digit code this message belongs to
    val isRead: Boolean = false
) {
    // Empty constructor needed for Firebase
    constructor() : this("", "", "", "", 0L, "", false)
} 