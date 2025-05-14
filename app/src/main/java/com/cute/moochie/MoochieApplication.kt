package com.cute.moochie

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cute.moochie.util.ImageUpdateWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class MoochieApplication : Application() {
    
    companion object {
        private const val TAG = "MoochieApplication"
        private const val IMAGE_UPDATE_WORK = "image_update_work"
        private const val REMOTE_CONFIG_EXCLUDED_TITLES = "excluded_notification_titles"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Initializing Firebase and database")
        
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            
            // Configure Firebase Realtime Database
            val database = FirebaseDatabase.getInstance()
            
            // Enable disk persistence for Firebase Realtime Database
            database.setPersistenceEnabled(true)
            
            // Set database logging level for debugging
            FirebaseDatabase.getInstance().setLogLevel(com.google.firebase.database.Logger.Level.DEBUG)
            
            // Create an index on the userId field in the notifications node
            val notificationsRef = database.getReference("notifications")
            notificationsRef.keepSynced(true)
            
            // Initialize Firebase Remote Config with defaults
            setupRemoteConfig()
            
            Log.d(TAG, "Firebase and database initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
        }
        
        // Schedule periodic work to check for image updates
        scheduleImageUpdateWork()
    }
    
    private fun setupRemoteConfig() {
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            
            // Set default values
            val defaultExcludedTitles = JSONArray().apply {
                put("Chat heads active")
                put("Updating your shared location…")
                put("Choose input method")
                put("")
            }.toString()
            
            val defaults = HashMap<String, Any>()
            defaults[REMOTE_CONFIG_EXCLUDED_TITLES] = defaultExcludedTitles
            
            remoteConfig.setDefaultsAsync(defaults)
            
            // Configure settings
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 hour
                .build()
            
            remoteConfig.setConfigSettingsAsync(configSettings)
            
            // Fetch and activate
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Remote config fetched and activated successfully")
                } else {
                    Log.e(TAG, "Remote config fetch failed", task.exception)
                }
            }
            
            Log.d(TAG, "Remote Config setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Remote Config: ${e.message}", e)
        }
    }
    
    private fun scheduleImageUpdateWork() {
        // Check for image updates every 15 minutes
        val imageUpdateRequest = PeriodicWorkRequestBuilder<ImageUpdateWorker>(
            15, TimeUnit.MINUTES
        )
            .setInitialDelay(5, TimeUnit.MINUTES) // Initial delay
            .build()
        
        Log.d(TAG, "Scheduling periodic image update checks")
        
        // Enqueue the work
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            IMAGE_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE, // Replace existing work if any
            imageUpdateRequest
        )
    }
} 