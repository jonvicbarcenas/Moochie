package com.cute.moochie.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cute.moochie.R
import com.cute.moochie.data.NotificationData

class NotificationsAdapter(
    private var notifications: List<NotificationData>
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {
    
    private val TAG = "NotificationsAdapter"
    
    init {
        Log.d(TAG, "Adapter initialized with ${notifications.size} notifications")
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        Log.d(TAG, "Creating new view holder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        Log.d(TAG, "Binding notification at position $position: ${notification.title}")
        holder.bind(notification)
    }
    
    override fun getItemCount(): Int = notifications.size
    
    fun updateNotifications(newNotifications: List<NotificationData>) {
        Log.d(TAG, "Updating adapter with ${newNotifications.size} notifications")
        this.notifications = newNotifications
        notifyDataSetChanged()
    }
    
    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appNameTextView: TextView = itemView.findViewById(R.id.app_name_text_view)
        private val titleTextView: TextView = itemView.findViewById(R.id.title_text_view)
        private val contentTextView: TextView = itemView.findViewById(R.id.content_text_view)
        private val timeTextView: TextView = itemView.findViewById(R.id.time_text_view)
        private val senderInfoTextView: TextView = itemView.findViewById(R.id.sender_info_text_view)
        
        fun bind(notification: NotificationData) {
            // Include info about which account this notification is from
            appNameTextView.text = notification.appName
            titleTextView.text = "From: ${notification.title}"
            contentTextView.text = notification.content
            timeTextView.text = notification.formattedTime

            // Display the sender info, which now includes the Gmail account
            if (notification.senderInfo.isNotEmpty()) {
                senderInfoTextView.text = notification.senderInfo
                senderInfoTextView.visibility = View.VISIBLE
            } else {
                senderInfoTextView.visibility = View.GONE
            }
        }
    }
} 