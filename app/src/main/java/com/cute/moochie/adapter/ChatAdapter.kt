package com.cute.moochie.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.cute.moochie.R
import com.cute.moochie.data.ChatManager
import com.cute.moochie.data.ChatMessage

class ChatAdapter(
    private var messages: List<ChatMessage>,
    private val currentUserId: String,
    private val chatManager: ChatManager
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {
    
    private val TAG = "ChatAdapter"
    
    init {
        Log.d(TAG, "Adapter initialized with ${messages.size} messages")
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        Log.d(TAG, "Creating new message view holder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        Log.d(TAG, "Binding message at position $position from ${message.senderName}")
        holder.bind(message, currentUserId == message.senderId)
    }
    
    override fun getItemCount(): Int = messages.size
    
    fun updateMessages(newMessages: List<ChatMessage>) {
        Log.d(TAG, "Updating adapter with ${newMessages.size} messages")
        this.messages = newMessages
        notifyDataSetChanged()
    }
    
    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sentMessageCard: CardView = itemView.findViewById(R.id.sent_message_card)
        private val sentMessageText: TextView = itemView.findViewById(R.id.sent_message_text)
        private val sentMessageTime: TextView = itemView.findViewById(R.id.sent_message_time)
        
        private val receivedMessageCard: CardView = itemView.findViewById(R.id.received_message_card)
        private val senderName: TextView = itemView.findViewById(R.id.sender_name)
        private val receivedMessageText: TextView = itemView.findViewById(R.id.received_message_text)
        private val receivedMessageTime: TextView = itemView.findViewById(R.id.received_message_time)
        
        fun bind(message: ChatMessage, isSentByCurrentUser: Boolean) {
            if (isSentByCurrentUser) {
                // Show the sent message layout
                sentMessageCard.visibility = View.VISIBLE
                receivedMessageCard.visibility = View.GONE
                
                sentMessageText.text = message.messageText
                sentMessageTime.text = chatManager.formatTimestamp(message.timestamp)
            } else {
                // Show the received message layout
                sentMessageCard.visibility = View.GONE
                receivedMessageCard.visibility = View.VISIBLE
                
                senderName.text = message.senderName
                receivedMessageText.text = message.messageText
                receivedMessageTime.text = chatManager.formatTimestamp(message.timestamp)
            }
        }
    }
} 