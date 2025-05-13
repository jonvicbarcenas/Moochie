package com.cute.moochie.util

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import com.cute.moochie.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

object ImageLoader {
    
    // Maximum dimensions for widget images to avoid memory issues
    private const val MAX_WIDTH = 600
    private const val MAX_HEIGHT = 600
    
    fun loadImageForWidget(
        context: Context,
        imageUri: String,
        views: RemoteViews,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Use a coroutine to load the image in the background
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Download the image but with sampling to reduce memory usage
                val url = URL(imageUri)
                
                // First decode with inJustDecodeBounds=true to check dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(url.openConnection().getInputStream(), null, options)
                
                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT)
                
                // Decode bitmap with inSampleSize set
                options.inJustDecodeBounds = false
                
                // Reconnect to the URL since the stream was consumed
                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream(), null, options)
                
                // Resize the bitmap if needed
                val resizedBitmap = if (bitmap != null && (bitmap.width > MAX_WIDTH || bitmap.height > MAX_HEIGHT)) {
                    resizeBitmap(bitmap, MAX_WIDTH, MAX_HEIGHT)
                } else {
                    bitmap
                }
                
                withContext(Dispatchers.Main) {
                    // Update widget with downloaded and resized image
                    if (resizedBitmap != null) {
                        views.setImageViewBitmap(R.id.widget_image, resizedBitmap)
                        try {
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                            Log.d("ImageLoader", "Widget updated successfully with resized image")
                        } catch (e: Exception) {
                            Log.e("ImageLoader", "Error updating widget with bitmap: ${e.message}")
                            // Fallback to a smaller default image
                            views.setImageViewResource(R.id.widget_image, android.R.drawable.ic_menu_gallery)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    } else {
                        Log.e("ImageLoader", "Failed to decode bitmap")
                        views.setImageViewResource(R.id.widget_image, android.R.drawable.ic_dialog_alert)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageLoader", "Error loading image: ${e.message}")
                withContext(Dispatchers.Main) {
                    // Show error image
                    views.setImageViewResource(R.id.widget_image, android.R.drawable.ic_dialog_alert)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
    
    // Calculate the optimal sample size to load a downsized version of the image
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        Log.d("ImageLoader", "Original size: ${width}x${height}, Sample size: $inSampleSize")
        return inSampleSize
    }
    
    // Resize a bitmap to fit within the specified dimensions
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val ratio: Float = width.toFloat() / height.toFloat()
        
        val newWidth: Int
        val newHeight: Int
        
        if (width > height) {
            // Landscape
            newWidth = maxWidth
            newHeight = (newWidth / ratio).toInt()
        } else {
            // Portrait or square
            newHeight = maxHeight
            newWidth = (newHeight * ratio).toInt()
        }
        
        Log.d("ImageLoader", "Resizing from ${width}x${height} to ${newWidth}x${newHeight}")
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
} 