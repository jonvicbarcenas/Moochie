package com.cute.moochie.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.content.ComponentName
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cute.moochie.R
import com.cute.moochie.data.UserPreferences
import com.cute.moochie.ui.MainActivity
import com.cute.moochie.util.ImageLoader
import com.cute.moochie.util.ImageUpdateWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

class ImageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        
        // First widget added, schedule immediate update
        Log.d("ImageWidgetProvider", "First widget added, requesting image update")
        scheduleImmediateWidgetUpdate(context)
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        
        // Last widget removed, clean up if needed
        Log.d("ImageWidgetProvider", "All widgets removed")
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_IMAGE) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)
            
            if (imageUri != null) {
                // Save the URI to SharedPreferences
                WidgetPreferences.saveImageUri(context, imageUri)
                
                // Notify the app about the image update
                val appUpdateIntent = Intent(ImageUpdateWorker.ACTION_IMAGE_UPDATED).apply {
                    putExtra(ImageUpdateWorker.EXTRA_IMAGE_URL, imageUri)
                }
                context.sendBroadcast(appUpdateIntent)
                
                // Update all widgets or a specific one
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    // Update specific widget
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val views = RemoteViews(context.packageName, R.layout.image_widget)
                    views.setImageViewUri(R.id.widget_image, Uri.parse(imageUri))
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } else {
                    // Update all widgets
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, ImageWidgetProvider::class.java)
                    )
                    for (widgetId in appWidgetIds) {
                        updateWidgetWithImage(context, appWidgetManager, widgetId, imageUri)
                    }
                }
            }
        }
    }
    
    private fun scheduleImmediateWidgetUpdate(context: Context) {
        // Check if user has a saved code first
        val userPreferences = UserPreferences(context)
        
        if (userPreferences.isCodeSaved()) {
            val workManager = WorkManager.getInstance(context)
            val periodicUpdateRequest =
                PeriodicWorkRequestBuilder<ImageUpdateWorker>(15, TimeUnit.MINUTES)
                    .build()
            
            workManager.enqueueUniquePeriodicWork(
                "ImageUpdateWorkerPeriodic", // Use the same unique name
                ExistingPeriodicWorkPolicy.KEEP,
                periodicUpdateRequest
            )
            Log.d("ImageWidgetProvider", "Scheduled periodic widget update check (every 15 minutes)")
        } else {
            Log.d("ImageWidgetProvider", "No saved code, skipping update check")
        }
    }

    companion object {
        const val ACTION_UPDATE_IMAGE = "com.cute.moochie.ACTION_UPDATE_IMAGE"
        const val EXTRA_IMAGE_URI = "extra_image_uri"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.image_widget)
            
            // Get the saved image URI
            val imageUri = WidgetPreferences.getSavedImageUri(context)
            
            if (imageUri != null) {
                // We have a saved image, try to update with it
                updateWidgetWithImage(context, appWidgetManager, appWidgetId, imageUri)
            } else {
                // Set an onClick intent to MainActivity
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
        
        private fun updateWidgetWithImage(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            imageUri: String
        ) {
            val views = RemoteViews(context.packageName, R.layout.image_widget)
            
            // If it's a web URL, try to download and cache the image
            if (imageUri.startsWith("http")) {
                // Set a loading placeholder temporarily
                views.setImageViewResource(R.id.widget_image, android.R.drawable.ic_menu_gallery)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                
                // Use the ImageLoader utility to load the image
                ImageLoader.loadImageForWidget(context, imageUri, views, appWidgetManager, appWidgetId)
            } else {
                try {
                    views.setImageViewUri(R.id.widget_image, Uri.parse(imageUri))
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    Log.e("ImageWidgetProvider", "Error setting local image: ${e.message}")
                    views.setImageViewResource(R.id.widget_image, android.R.drawable.ic_dialog_alert)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
} 