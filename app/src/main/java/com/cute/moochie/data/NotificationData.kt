package com.cute.moochie.data

/**
 * Data class representing a notification captured from the device
 */
data class NotificationData(
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val content: String = "",
    val postTime: Long = 0L,
    val formattedTime: String = "",
    val userId: String = "",
    val senderInfo: String = "", // Added to store sender email/name for Gmail and other messaging apps
    // Using a UUID for each notification entry
    val id: String = java.util.UUID.randomUUID().toString()
) {
    // Empty constructor needed for Firebase
    constructor() : this("", "", "", "", 0L, "", "", "")
} 