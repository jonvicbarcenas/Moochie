package com.cute.moochie.util

import android.net.Uri
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cute.moochie.R
import com.cute.moochie.data.UserPreferences
import com.cute.moochie.network.ApiClient
import java.io.File

class ImageOperationsHelper(
    private val activity: AppCompatActivity,
    private val apiClient: ApiClient,
    private val userPreferences: UserPreferences,
    private val previewImageView: ImageView,
    private val updateWidgetCallback: (String) -> Unit,
    private val showToastCallback: (message: String, duration: Int) -> Unit
) {
    var lastUpdatedImageUrl: String? = null
    var isShowingTemporaryPreview: Boolean = false

    fun clearImageCaches() {
        // Clear memory cache on UI thread
        Glide.get(activity).clearMemory()

        // Clear disk cache on background thread
        Thread {
            try {
                Glide.get(activity).clearDiskCache()
            } catch (e: Exception) {
                Log.e("ImageOpsHelper", "Error clearing disk cache: ${e.message}")
            }
        }.start()

        // Reset the last updated URL to force refresh
        lastUpdatedImageUrl = null
    }

    fun updatePreviewWithUri(uri: Uri) {
        showToastCallback("Image selected", Toast.LENGTH_SHORT)
        isShowingTemporaryPreview = true
        Glide.with(activity)
            .load(uri)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(previewImageView)
    }

    fun updatePreviewWithUrl(imageUrl: String, userCodeForValidation: String?) {
        // Check if this is a different URL from the last one we updated with
        if (imageUrl == lastUpdatedImageUrl) {
            Log.d("ImageOpsHelper", "Image already displayed, skipping update")
            return
        }

        // Verify that the URL corresponds to the current user code
        if (userCodeForValidation != null && userCodeForValidation.isNotEmpty() &&
            !imageUrl.contains("/$userCodeForValidation") && !imageUrl.contains("code=$userCodeForValidation")) {
            Log.d("ImageOpsHelper", "Image URL doesn't match current code, skipping update")
            return
        }

        lastUpdatedImageUrl = imageUrl

        // Only show toast and update UI if activity is not finishing
        if (!activity.isFinishing) {
            // Add timestamp to force cache refresh
            val timestampedUrl = addTimestampToUrl(imageUrl)

            // Load the image using Glide
            Glide.with(activity)
                .load(timestampedUrl)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(previewImageView)

            Log.d("ImageOpsHelper", "Updated app preview image: $timestampedUrl")
            showToastCallback("Image updated", Toast.LENGTH_SHORT)
        } else {
            Log.d("ImageOpsHelper", "Activity finishing, skipped UI update")
        }
    }

    private fun addTimestampToUrl(url: String): String {
        return if (!url.contains("t=")) {
            val currentTime = System.currentTimeMillis()
            if (url.contains("?")) {
                "$url&t=$currentTime"
            } else {
                "$url?t=$currentTime"
            }
        } else {
            url
        }
    }

    fun uploadImage(imageUri: Uri, userCode: String, tempFile: File) {
        showToastCallback("Uploading...", Toast.LENGTH_SHORT)
        lastUpdatedImageUrl = null // Reset before upload
        isShowingTemporaryPreview = false // Reset after selecting a new image for upload

        apiClient.uploadImage(tempFile, userCode, object : ApiClient.UploadCallback {
            override fun onSuccess(imageUrl: String, code: String) {
                activity.runOnUiThread {
                    updateWidgetCallback(imageUrl)
                    showToastCallback("Upload successful!", Toast.LENGTH_SHORT)
                }
            }

            override fun onFailure(errorMessage: String) {
                activity.runOnUiThread {
                    showToastCallback(errorMessage, Toast.LENGTH_SHORT)
                }
            }
        })
    }

    fun fetchAndDisplayImage(userCode: String) {
        Log.d("ImageOpsHelper", "Fetching image for code: $userCode")

        // Show loading indicator or placeholder
        previewImageView.setImageResource(R.drawable.ic_image_placeholder)

        // Always reset lastUpdatedImageUrl to force refresh
        lastUpdatedImageUrl = null
        // Reset temporary preview flag since we're fetching the saved image
        isShowingTemporaryPreview = false

        apiClient.fetchImageByCode(userCode, object : ApiClient.FetchImageCallback {
            override fun onSuccess(imageUrl: String, code: String, timestamp: String) {
                activity.runOnUiThread {
                    Log.d("ImageOpsHelper", "Successfully fetched image URL: $imageUrl")
                    Log.d("ImageOpsHelper", "Image timestamp: $timestamp")

                    // Save the new timestamp
                    if (timestamp.isNotEmpty()) {
                        userPreferences.saveImageTimestamp(timestamp)
                    }

                    // Always add a cache-busting parameter with current time to guarantee freshness
                    val finalImageUrl = addTimestampToUrl(imageUrl)

                    // Load the image using Glide with strict no-cache settings
                    Glide.with(activity)
                        .load(finalImageUrl)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(previewImageView)

                    // Update the widget with the fresh URL
                    updateWidgetCallback(finalImageUrl)
                }
            }

            override fun onFailure(errorMessage: String) {
                activity.runOnUiThread {
                    Log.e("ImageOpsHelper", "Failed to fetch image: $errorMessage")
                    showToastCallback(errorMessage, Toast.LENGTH_SHORT)
                }
            }
        })
    }
} 