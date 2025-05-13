package com.cute.moochie.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cute.moochie.R
import com.cute.moochie.data.UserPreferences
import com.cute.moochie.network.ApiClient
import com.cute.moochie.widget.ImageWidgetProvider
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ImageUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    
    companion object {
        private const val TAG = "ImageUpdateWorker"
        const val ACTION_IMAGE_UPDATED = "com.cute.moochie.ACTION_IMAGE_UPDATED"
        const val EXTRA_IMAGE_URL = "extra_image_url"
    }
    
    override fun doWork(): Result {
        Log.d(TAG, "Starting image update check")
        
        val userPreferences = UserPreferences(context)
        
        // Check if user has a saved code
        if (!userPreferences.isCodeSaved()) {
            Log.d(TAG, "No saved code, skipping update check")
            return Result.success()
        }
        
        val userCode = userPreferences.getUserCode() ?: ""
        val apiClient = ApiClient("http://54.255.202.96:3000")
        
        return runBlocking {
            try {
                val result = checkForImageUpdate(apiClient, userCode, userPreferences)
                
                if (result) {
                    Log.d(TAG, "Image updated successfully")
                    Result.success()
                } else {
                    Log.d(TAG, "No new image updates found")
                    Result.success() // Still return success even if no updates
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for image updates", e)
                Result.retry() // Retry on error
            }
        }
    }
    
    private suspend fun checkForImageUpdate(
        apiClient: ApiClient,
        code: String,
        userPreferences: UserPreferences
    ): Boolean = suspendCoroutine { continuation ->
        apiClient.fetchImageByCode(code, object : ApiClient.FetchImageCallback {
            override fun onSuccess(imageUrl: String, code: String, timestamp: String) {
                // Check if timestamp has changed
                if (userPreferences.hasNewTimestamp(timestamp)) {
                    Log.d(TAG, "New image detected with timestamp: $timestamp")
                    
                    // Save the new timestamp
                    userPreferences.saveImageTimestamp(timestamp)
                    
                    // Update widget
                    updateWidgets(imageUrl)
                    
                    continuation.resume(true)
                } else {
                    Log.d(TAG, "No new image updates")
                    continuation.resume(false)
                }
            }
            
            override fun onFailure(errorMessage: String) {
                Log.e(TAG, "Failed to fetch image: $errorMessage")
                continuation.resume(false)
            }
        })
    }
    
    private fun updateWidgets(imageUrl: String) {
        // Send broadcast to update widgets
        val intent = Intent(context, ImageWidgetProvider::class.java).apply {
            action = ImageWidgetProvider.ACTION_UPDATE_IMAGE
            putExtra(ImageWidgetProvider.EXTRA_IMAGE_URI, imageUrl)
            putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) // Update all widgets
        }
        context.sendBroadcast(intent)
        
        // Send a broadcast to notify the app about the image update
        val appUpdateIntent = Intent(ACTION_IMAGE_UPDATED).apply {
            putExtra(EXTRA_IMAGE_URL, imageUrl)
        }
        context.sendBroadcast(appUpdateIntent)
        
        // Also update all instances of the widget
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, ImageWidgetProvider::class.java)
        )
        
        if (appWidgetIds.isNotEmpty()) {
            // Force update all widgets
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_image)
            // Call onUpdate on the provider
            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                component = ComponentName(context, ImageWidgetProvider::class.java)
            }
            context.sendBroadcast(updateIntent)
        }
    }
} 