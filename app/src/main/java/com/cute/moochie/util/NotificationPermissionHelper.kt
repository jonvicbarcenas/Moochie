package com.cute.moochie.util

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.cute.moochie.service.MoochieNotificationListenerService

/**
 * Helper class to check and request notification listener permissions
 */
class NotificationPermissionHelper(private val context: Context) {
    
    private val TAG = "NotificationPermission"
    
    /**
     * Check if notification listener permission is granted
     */
    fun isNotificationListenerEnabled(): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        
        val isEnabled = if (flat != null && flat.isNotEmpty()) {
            val names = flat.split(":")
            names.any { name ->
                val componentName = ComponentName.unflattenFromString(name)
                componentName != null && componentName.packageName == packageName
            }
        } else {
            false
        }
        
        Log.d(TAG, "Notification listener enabled: $isEnabled")
        return isEnabled
    }
    
    /**
     * Show a dialog requiring the user to enable notification access
     * If user declines, the app will exit
     */
    fun showRequiredPermissionDialog(activity: Activity) {
        if (isNotificationListenerEnabled()) {
            // Permission already granted
            return
        }
        
        // Try to directly open the notification settings
        try {
            Log.d(TAG, "Directly opening notification listener settings")
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            activity.startActivity(intent)
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error directly opening notification settings, falling back to dialog", e)
        }
        
        // If direct opening fails, show a dialog with instructions
        AlertDialog.Builder(context)
            .setTitle("Notification Access Required")
            .setMessage("This app needs notification access to monitor notifications. Please enable it in the next screen.")
            .setCancelable(false)
            .setPositiveButton("Enable") { _, _ ->
                try {
                    // Open notification listener settings
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening notification settings", e)
                    showManualInstructionsDialog(activity)
                }
            }
            .setNegativeButton("Exit App") { _, _ ->
                // Exit the app if user declines
                activity.finish()
            }
            .show()
    }
    
    /**
     * Show instructions if automatic opening fails
     */
    private fun showManualInstructionsDialog(activity: Activity) {
        AlertDialog.Builder(context)
            .setTitle("Manual Setup Required")
            .setMessage("Please go to Settings > Apps & notifications > Special app access > Notification access, and enable access for this app.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                // Exit the app since we can't proceed without permission
                activity.finish()
            }
            .show()
    }
    
    /**
     * Restart the notification listener service
     */
    fun restartNotificationListenerService() {
        Log.d(TAG, "Attempting to restart notification service")
        
        try {
            if (isNotificationListenerEnabled()) {
                // Toggle notification listener to force a restart
                val componentName = ComponentName(context, MoochieNotificationListenerService::class.java)
                
                // Request rebind
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                
                Log.d(TAG, "Notification service component toggled for restart")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting notification service", e)
        }
    }
} 