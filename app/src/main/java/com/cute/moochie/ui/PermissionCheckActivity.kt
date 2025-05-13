package com.cute.moochie.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cute.moochie.R
import com.cute.moochie.data.AuthManager
import com.cute.moochie.util.NotificationPermissionHelper

/**
 * Initial activity to check notification permission before proceeding
 */
class PermissionCheckActivity : AppCompatActivity() {
    
    private val TAG = "PermissionCheck"
    private lateinit var notificationPermissionHelper: NotificationPermissionHelper
    private lateinit var authManager: AuthManager
    
    // ActivityResultLauncher for notification settings
    private val notificationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { 
        // Check if permission was granted after returning from settings
        if (!notificationPermissionHelper.isNotificationListenerEnabled()) {
            Log.d(TAG, "Notification permission still not granted after settings")
            // Show dialog again if permission wasn't granted
            checkNotificationPermission()
        } else {
            Log.d(TAG, "Notification permission granted after settings")
            // Permission granted, proceed to appropriate activity
            proceedToNextActivity()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_check)
        
        Log.d(TAG, "onCreate: Starting permission check")
        
        // Initialize helpers
        notificationPermissionHelper = NotificationPermissionHelper(this)
        authManager = AuthManager(this)
        
        // Check notification permission
        checkNotificationPermission()
    }
    
    /**
     * Check notification permission and show dialog if needed
     */
    private fun checkNotificationPermission() {
        if (!notificationPermissionHelper.isNotificationListenerEnabled()) {
            Log.d(TAG, "Notification permission not granted, showing required dialog")
            
            // Direct way to open notification listener settings
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                notificationSettingsLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening notification settings", e)
                // Show dialog with manual instructions
                notificationPermissionHelper.showRequiredPermissionDialog(this)
            }
        } else {
            Log.d(TAG, "Notification permission already granted")
            // Permission already granted, proceed to appropriate activity
            proceedToNextActivity()
        }
    }
    
    /**
     * Proceed to the appropriate activity based on authentication status
     */
    private fun proceedToNextActivity() {
        if (authManager.isLoggedIn()) {
            // User is logged in, go to MainActivity
            Log.d(TAG, "User is logged in, proceeding to MainActivity")
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        } else {
            // User is not logged in, go to LoginActivity
            Log.d(TAG, "User is not logged in, proceeding to LoginActivity")
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
        
        // Close this activity
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        
        // If we return to this activity, check permission again
        if (notificationPermissionHelper.isNotificationListenerEnabled()) {
            // Permission granted while we were away, proceed
            proceedToNextActivity()
        }
    }
} 