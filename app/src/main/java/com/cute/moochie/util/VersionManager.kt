package com.cute.moochie.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cute.moochie.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handles app version checking and update prompting
 */
class VersionManager {
    companion object {
        private const val TAG = "VersionManager"
        private const val VERSION_CHECK_URL = "https://jonvicbarcenas.github.io/Moochie/version.json"
        
        /**
         * Checks if an update is available
         * @return Triple containing (isUpdateAvailable, latestVersion, downloadUrl)
         */
        suspend fun checkForUpdate(): Triple<Boolean, String, String> = withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                    
                val request = Request.Builder()
                    .url(VERSION_CHECK_URL)
                    .build()
                
                Log.d(TAG, "Checking for updates at: $VERSION_CHECK_URL")
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "Version response: $responseBody")
                    
                    val jsonObject = JSONObject(responseBody ?: "{}")
                    val latestVersion = jsonObject.getString("version").removePrefix("v")
                    val downloadUrl = jsonObject.getString("url")
                    
                    val currentVersion = BuildConfig.VERSION_NAME
                    Log.d(TAG, "Current version: $currentVersion, Latest version: $latestVersion")
                    
                    val updateNeeded = isUpdateNeeded(currentVersion, latestVersion)
                    return@withContext Triple(updateNeeded, latestVersion, downloadUrl)
                } else {
                    Log.e(TAG, "Failed to check for updates: ${response.code}")
                    return@withContext Triple(false, "", "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                return@withContext Triple(false, "", "")
            }
        }
        
        /**
         * Compares version strings to determine if an update is needed
         */
        private fun isUpdateNeeded(currentVersion: String, latestVersion: String): Boolean {
            try {
                val current = currentVersion.split(".").map { it.toInt() }
                val latest = latestVersion.split(".").map { it.toInt() }
                
                for (i in 0 until minOf(current.size, latest.size)) {
                    if (latest[i] > current[i]) return true
                    if (latest[i] < current[i]) return false
                }
                
                return latest.size > current.size
            } catch (e: Exception) {
                Log.e(TAG, "Error comparing versions", e)
                return false
            }
        }
        
        /**
         * Shows an update dialog to the user
         */
        fun showUpdateDialog(context: Context, latestVersion: String, downloadUrl: String) {
            AlertDialog.Builder(context)
                .setTitle("Update Available")
                .setMessage("A new version (v$latestVersion) of the app is available. Update now to access the latest features.")
                .setPositiveButton("Update") { _, _ ->
                    try {
                        // Try to open with Chrome explicitly first
                        val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                        chromeIntent.setPackage("com.android.chrome")
                        
                        if (chromeIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(chromeIntent)
                        } else {
                            // Fall back to default browser if Chrome is not available
                            val defaultIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                            context.startActivity(defaultIntent)
                        }
                        
                        // Exit the app after directing to download
                        if (context is AppCompatActivity) {
                            context.finishAffinity()
                        } else {
                            System.exit(0)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening browser", e)
                        // Fall back if any error occurs
                        val defaultIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                        context.startActivity(defaultIntent)
                        
                        // Exit the app
                        if (context is AppCompatActivity) {
                            context.finishAffinity()
                        } else {
                            System.exit(0)
                        }
                    }
                }
                .setCancelable(false)
                .show()
        }
    }
} 