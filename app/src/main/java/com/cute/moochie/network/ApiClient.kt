package com.cute.moochie.network

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private val serverUrl: String) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    interface UploadCallback {
        fun onSuccess(imageUrl: String, code: String)
        fun onFailure(errorMessage: String)
    }
    
    interface FetchImageCallback {
        fun onSuccess(imageUrl: String, code: String, timestamp: String)
        fun onFailure(errorMessage: String)
    }
    
    fun uploadImage(imageFile: File, code: String, callback: UploadCallback) {
        // Verify the file exists and has content
        if (!imageFile.exists() || imageFile.length() == 0L) {
            Log.e("ApiClient", "File doesn't exist or is empty: ${imageFile.absolutePath}")
            callback.onFailure("Error: Could not prepare image for upload")
            return
        }
        
        // Check if file is too large (10MB)
        if (imageFile.length() > 10 * 1024 * 1024) {
            callback.onFailure("Image is too large. Please select a smaller image.")
            return
        }
        
        Log.d("ApiClient", "Prepared file for upload: ${imageFile.absolutePath}, size: ${imageFile.length()} bytes")
        Log.d("ApiClient", "Using provided code: $code")
        
        // Create multipart request to match server expectations
        val mediaType = "image/jpeg".toMediaTypeOrNull()
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", 
                "image.jpg",
                imageFile.asRequestBody(mediaType)
            )
            .addFormDataPart("code", code)
            .build()
        
        val request = Request.Builder()
            .url("$serverUrl/api/upload")
            .post(requestBody)
            .build()
        
        Log.d("ApiClient", "Sending request to: ${request.url}")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiClient", "Network failure: ${e.message}", e)
                val errorMessage = if (e is java.net.SocketTimeoutException) {
                    "Server timeout. Please check your internet connection or try again later."
                } else {
                    "Upload failed: ${e.message}"
                }
                callback.onFailure(errorMessage)
            }
            
            override fun onResponse(call: Call, response: Response) {
                val statusCode = response.code
                Log.d("ApiClient", "Server response: $statusCode")
                
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        Log.d("ApiClient", "Response body: $responseBody")
                        
                        val jsonObject = JSONObject(responseBody)
                        val returnedCode = jsonObject.getString("code")
                        // Get the image URL from response or build it ourselves
                        val imageUrl = if (jsonObject.has("imageUrl")) {
                            serverUrl + jsonObject.getString("imageUrl")
                        } else {
                            "$serverUrl/api/images/$returnedCode"
                        }
                        
                        Log.d("ApiClient", "Image URL: $imageUrl")
                        callback.onSuccess(imageUrl, returnedCode)
                    } catch (e: Exception) {
                        Log.e("ApiClient", "Error parsing response: ${e.message}", e)
                        callback.onFailure("Error parsing response: ${e.message}")
                    }
                } else {
                    val errorBody = response.body?.string() ?: response.message
                    Log.e("ApiClient", "Error response: $errorBody")
                    callback.onFailure("Upload failed: $errorBody")
                }
            }
        })
    }
    
    fun fetchImageByCode(code: String, callback: FetchImageCallback) {
        Log.d("ApiClient", "Fetching image with code: $code")
        
        // Add current timestamp to prevent caching
        val timestamp = System.currentTimeMillis()
        
        val request = Request.Builder()
            .url("$serverUrl/api/images/$code?t=$timestamp") // Add timestamp to URL
            .header("Cache-Control", "no-cache, no-store, must-revalidate")
            .header("Pragma", "no-cache")
            .header("X-Requested-With", "$timestamp") // Another cache-busting header
            .get()
            .build()
        
        Log.d("ApiClient", "Sending request to: ${request.url}")
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiClient", "Network failure: ${e.message}", e)
                val errorMessage = if (e is java.net.SocketTimeoutException) {
                    "Server timeout. Please check your internet connection or try again later."
                } else {
                    "Failed to fetch image: ${e.message}"
                }
                callback.onFailure(errorMessage)
            }
            
            override fun onResponse(call: Call, response: Response) {
                val statusCode = response.code
                Log.d("ApiClient", "Server response: $statusCode")
                
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string()
                        Log.d("ApiClient", "Response body: $responseBody")
                        
                        val jsonObject = JSONObject(responseBody)
                        val returnedCode = jsonObject.getString("code")
                        // Get the image URL from response
                        val imageUrl = if (jsonObject.has("imageUrl")) {
                            serverUrl + jsonObject.getString("imageUrl")
                        } else {
                            "$serverUrl/api/images/$returnedCode"
                        }
                        
                        // Get timestamp if available
                        val timestamp = if (jsonObject.has("timestamp")) {
                            jsonObject.getString("timestamp")
                        } else {
                            ""
                        }
                        
                        Log.d("ApiClient", "Image URL: $imageUrl, Timestamp: $timestamp")
                        callback.onSuccess(imageUrl, returnedCode, timestamp)
                    } catch (e: Exception) {
                        Log.e("ApiClient", "Error parsing response: ${e.message}", e)
                        callback.onFailure("Error parsing response: ${e.message}")
                    }
                } else {
                    val errorBody = response.body?.string() ?: response.message
                    Log.e("ApiClient", "Error response: $errorBody")
                    callback.onFailure("Failed to fetch image: $errorBody")
                }
            }
        })
    }
} 