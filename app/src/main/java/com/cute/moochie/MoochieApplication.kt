package com.cute.moochie

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cute.moochie.util.ImageUpdateWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

class MoochieApplication : Application() {
    
    companion object {
        private const val TAG = "MoochieApplication"
        private const val IMAGE_UPDATE_WORK = "image_update_work"
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
            
            Log.d(TAG, "Firebase and database initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}", e)
        }
        
        // Schedule periodic work to check for image updates
        scheduleImageUpdateWork()
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