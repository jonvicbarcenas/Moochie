package com.cute.moochie.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.cute.moochie.R
import com.cute.moochie.adapter.NotificationsAdapter
import com.cute.moochie.data.NotificationData
import com.cute.moochie.data.NotificationManager
import com.cute.moochie.service.MoochieNotificationListenerService
import com.google.android.material.button.MaterialButton

class NotificationsActivity : AppCompatActivity() {
    
    private val TAG = "NotificationsActivity"
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyView: TextView
    private lateinit var enableServiceButton: Button
    private lateinit var clearAllButton: MaterialButton
    private lateinit var notificationManager: NotificationManager
    private lateinit var adapter: NotificationsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        
        Log.d(TAG, "onCreate: Initializing NotificationsActivity")
        
        // Setup toolbar with back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Notification History"
        
        // Initialize components
        recyclerView = findViewById(R.id.notifications_recycler_view)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        emptyView = findViewById(R.id.empty_view)
        enableServiceButton = findViewById(R.id.enable_service_button)
        clearAllButton = findViewById(R.id.clear_all_button)
        
        // Initialize notification manager
        notificationManager = NotificationManager(this)
        
        // Setup RecyclerView
        adapter = NotificationsAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Setup swipe refresh
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Swipe refresh triggered")
            loadNotifications()
        }
        
        // Setup enable service button
        enableServiceButton.setOnClickListener {
            Log.d(TAG, "Enable service button clicked")
            openNotificationListenerSettings()
        }
        
        // Setup clear all button
        clearAllButton.setOnClickListener {
            Log.d(TAG, "Clear all button clicked")
            clearAllNotifications()
        }
        
        // Check if notification listener is enabled
        checkNotificationListenerStatus()
        
        // Force restart notification service to ensure it's running
        restartNotificationService()
        
        // Load notifications
        loadNotifications()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Refreshing notification data")
        checkNotificationListenerStatus()
        loadNotifications()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun checkNotificationListenerStatus() {
        val isEnabled = notificationManager.isNotificationListenerEnabled(this)
        Log.d(TAG, "Notification listener enabled: $isEnabled")
        enableServiceButton.visibility = if (isEnabled) View.GONE else View.VISIBLE
    }
    
    private fun openNotificationListenerSettings() {
        try {
            Log.d(TAG, "Opening notification listener settings")
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification settings: ${e.message}", e)
            Toast.makeText(
                this,
                "Unable to open notification settings. Please enable it manually.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun loadNotifications() {
        Log.d(TAG, "Loading notifications")
        swipeRefreshLayout.isRefreshing = true
        
        notificationManager.getUserNotifications { notifications ->
            Log.d(TAG, "Received ${notifications.size} notifications from manager")
            runOnUiThread {
                updateNotificationsList(notifications)
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun updateNotificationsList(notifications: List<NotificationData>) {
        Log.d(TAG, "Updating notifications list with ${notifications.size} items")
        adapter.updateNotifications(notifications)
        
        if (notifications.isEmpty()) {
            Log.d(TAG, "No notifications to display, showing empty view")
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            Log.d(TAG, "Displaying notifications in RecyclerView")
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }
    
    private fun clearAllNotifications() {
        Log.d(TAG, "Clearing all notifications")
        swipeRefreshLayout.isRefreshing = true
        
        notificationManager.deleteAllUserNotifications { success ->
            Log.d(TAG, "Notifications cleared: $success")
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "All notifications cleared", Toast.LENGTH_SHORT).show()
                    updateNotificationsList(emptyList())
                } else {
                    Toast.makeText(this, "Failed to clear notifications", Toast.LENGTH_SHORT).show()
                }
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun restartNotificationService() {
        Log.d(TAG, "Attempting to restart notification service")
        try {
            // Toggle notification listeners to force a restart
            val flat = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            )
            
            if (flat != null && flat.contains(packageName)) {
                Log.d(TAG, "Service is enabled, sending broadcast to ensure it's running")
                val intent = Intent(MoochieNotificationListenerService.ACTION_NOTIFICATION_LISTENER_STARTED)
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting notification service: ${e.message}", e)
        }
    }
} 