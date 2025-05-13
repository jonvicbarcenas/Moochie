package com.cute.moochie.ui

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cute.moochie.R
import com.cute.moochie.camera.CameraHelper
import com.cute.moochie.data.AuthManager
import com.cute.moochie.data.UserPreferences
import com.cute.moochie.network.ApiClient
import com.cute.moochie.util.FileHelper
import com.cute.moochie.util.ImageUpdateWorker
import com.cute.moochie.util.ImageOperationsHelper
import com.cute.moochie.util.NotificationPermissionHelper
import com.cute.moochie.widget.ImageWidgetProvider
import com.cute.moochie.widget.WidgetPreferences
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.io.IOException
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.cute.moochie.service.ChatNotificationService

class MainActivity : AppCompatActivity() {
    
    private val TAG = "MainActivity"
    private var currentPhotoPath: String = ""
    private var imageUri: Uri? = null
    private lateinit var userPreferences: UserPreferences
    private lateinit var cameraHelper: CameraHelper
    private lateinit var apiClient: ApiClient
    private lateinit var codeEditText: TextInputEditText
    private lateinit var codeInputLayout: TextInputLayout
    private lateinit var previewImageView: ImageView
    private lateinit var imageOpsHelper: ImageOperationsHelper
    private lateinit var authManager: AuthManager
    private lateinit var notificationPermissionHelper: NotificationPermissionHelper
    
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
            // Restart the service to ensure it's running
            notificationPermissionHelper.restartNotificationListenerService()
        }
    }
    
    // BroadcastReceiver for image updates
    private val imageUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ImageUpdateWorker.ACTION_IMAGE_UPDATED) {
                val imageUrl = intent.getStringExtra(ImageUpdateWorker.EXTRA_IMAGE_URL)
                Log.d(TAG, "Received image update broadcast: $imageUrl")
                
                if (imageUrl != null) {
                    // Update the preview image in the app
                    imageOpsHelper.updatePreviewWithUrl(imageUrl, userPreferences.getUserCode())
                }
            }
        }
    }
    
    // Camera permission launcher
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            dispatchTakePictureIntent()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Gallery launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                imageUri = uri
                updatePreviewImage(uri)
            }
        }
    }
    
    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val file = File(currentPhotoPath)
            imageUri = cameraHelper.getUriForFile(file)
            updatePreviewImage(imageUri!!)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.d(TAG, "onCreate: Starting MainActivity")
        
        // Initialize user preferences
        userPreferences = UserPreferences(this)
        
        // Initialize authentication manager
        authManager = AuthManager(this)
        
        // Initialize notification permission helper
        notificationPermissionHelper = NotificationPermissionHelper(this)
        
        // Check if user is authenticated
        if (!authManager.isLoggedIn()) {
            redirectToLogin()
            return
        }
        
        // Check notification permission
        checkNotificationPermission()
        
        // Start chat notification service if user is logged in
        startChatNotificationService()
        
        // Initialize components
        initializeComponents()
        
        // Clear image caches on startup via helper
        imageOpsHelper.clearImageCaches()
        
        // Set up button listeners
        setupButtonListeners()
        
        // Load saved code
        loadSavedCode()
        
        // Fetch and display the saved image if a code exists
        if (userPreferences.isCodeSaved()) {
            fetchAndDisplayImage()
        }
        
        // Register the broadcast receiver
        registerImageUpdateReceiver()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        
        // Hide notification history option unless email is one of the allowed emails
        val notificationItem = menu.findItem(R.id.action_notifications)
        val userEmail = authManager.getUserEmail()
        val allowedEmails = listOf("humbamanok1@gmail.com", "excchantpro@gmail.com")
        
        notificationItem?.isVisible = allowedEmails.contains(userEmail)
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sign_out -> {
                signOut()
                true
            }
            R.id.action_notifications -> {
                openNotificationsActivity()
                true
            }
            R.id.action_chat -> {
                openChatActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun openNotificationsActivity() {
        val intent = Intent(this, NotificationsActivity::class.java)
        startActivity(intent)
    }
    
    private fun openChatActivity() {
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
    }
    
    private fun signOut() {
        authManager.signOut()
        redirectToLogin()
    }
    
    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister the broadcast receiver
        try {
            unregisterReceiver(imageUpdateReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
    }
    
    private fun registerImageUpdateReceiver() {
        try {
            val filter = IntentFilter(ImageUpdateWorker.ACTION_IMAGE_UPDATED)
            registerReceiver(imageUpdateReceiver, filter)
            Log.d("MainActivity", "Registered image update receiver")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error registering receiver: ${e.message}")
        }
    }
    
    private fun initializeComponents() {
        // Initialize UI components
        codeEditText = findViewById(R.id.codeEditText)
        codeInputLayout = findViewById(R.id.codeInputLayout)
        previewImageView = findViewById(R.id.previewImageView)
        
        // Set up image click listener to refresh
        previewImageView.setOnClickListener {
            if (userPreferences.isCodeSaved()) {
                fetchAndDisplayImage()
                scheduleImmediateWidgetUpdate()
            }
        }
        
        // Set up refresh button
        findViewById<FloatingActionButton>(R.id.refreshButton).setOnClickListener {
            if (userPreferences.isCodeSaved()) {

                Glide.get(this).clearMemory()
                Thread {
                    try {
                        Glide.get(this).clearDiskCache()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error clearing disk cache: ${e.message}")
                    }
                }.start()
                
                // Force fetch with cache bypassing
                fetchAndDisplayImage()
                
                // Also update widget
                scheduleImmediateWidgetUpdate()
            }
        }
        
        // Initialize helper classes
        cameraHelper = CameraHelper(this)
        apiClient = ApiClient("http://54.255.202.96:3000")

        // Initialize ImageOperationsHelper
        imageOpsHelper = ImageOperationsHelper(
            activity = this,
            apiClient = apiClient,
            userPreferences = userPreferences,
            previewImageView = previewImageView,
            updateWidgetCallback = ::updateWidget,
            showToastCallback = { message, duration -> Toast.makeText(this, message, duration).show() }
        )
    }
    
    private fun setupButtonListeners() {
        findViewById<Button>(R.id.captureButton).setOnClickListener {
            checkCameraPermission()
        }
        
        findViewById<Button>(R.id.galleryButton).setOnClickListener {
            openGallery()
        }
        
        findViewById<Button>(R.id.uploadButton).setOnClickListener {
            uploadImage()
        }
        
        findViewById<Button>(R.id.editCodeButton).setOnClickListener {
            toggleCodeEditing()
        }
    }
    
    private fun loadSavedCode() {
        if (userPreferences.isCodeSaved()) {
            // Code exists, display it and disable editing
            codeEditText.setText(userPreferences.getUserCode())
            codeEditText.isEnabled = false
            codeInputLayout.helperText = "Code saved"
            findViewById<Button>(R.id.editCodeButton).text = "Edit Code"
        } else {
            // No code saved yet, allow editing
            codeEditText.isEnabled = true
            codeInputLayout.helperText = "Enter a 4-digit code"
            findViewById<Button>(R.id.editCodeButton).text = "Save Code"
        }
    }
    
    private fun toggleCodeEditing() {
        if (codeEditText.isEnabled) {
            // Save the code
            val userCode = codeEditText.text.toString().trim()
            if (userCode.length != 4 || !userCode.all { it.isDigit() }) {
                Toast.makeText(this, "Please enter a valid 4-digit code", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Check if code is different from previous one
            val oldCode = userPreferences.getUserCode()
            val isNewCode = oldCode != userCode
            
            // Save to preferences
            userPreferences.saveUserCode(userCode)
            
            // Disable editing
            codeEditText.isEnabled = false
            codeInputLayout.helperText = "Code saved"
            findViewById<Button>(R.id.editCodeButton).text = "Edit Code"
            
            Toast.makeText(this, "Code saved", Toast.LENGTH_SHORT).show()
            
            // Reset the lastUpdatedImageUrl if code changed to force refresh
            if (isNewCode) {
                imageOpsHelper.lastUpdatedImageUrl = null
            }
            
            // Fetch and display the image with the new code
            fetchAndDisplayImage()
        } else {
            // Enable editing
            codeEditText.isEnabled = true
            codeInputLayout.helperText = "Enter a 4-digit code"
            findViewById<Button>(R.id.editCodeButton).text = "Save Code"
        }
    }
    
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            dispatchTakePictureIntent()
        }
    }
    
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }
    
    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            // Create the file where the photo should go
            var photoFile: File? = null
            try {
                photoFile = cameraHelper.createImageFile()
                currentPhotoPath = cameraHelper.getCurrentPhotoPath()
            } catch (ex: IOException) {
                Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
            }
            
            photoFile?.let {
                val photoURI = cameraHelper.getUriForFile(it)
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                cameraLauncher.launch(takePictureIntent)
            }
        } else {
            Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updatePreviewImage(uri: Uri) {
        // Show a toast to confirm selection
        imageOpsHelper.updatePreviewWithUri(uri)
    }
    
    private fun uploadImage() {
        // Get the user code
        val userCode = if (codeEditText.isEnabled) {
            codeEditText.text.toString().trim()
        } else {
            userPreferences.getUserCode() ?: ""
        }
        
        // Validate the code - must be 4 digits
        if (userCode.length != 4 || !userCode.all { it.isDigit() }) {
            Toast.makeText(this, "Please enter a valid 4-digit code", Toast.LENGTH_SHORT).show()
            return
        }
        
        imageUri?.let { uri ->
            // Show loading or progress
            imageOpsHelper.uploadImage(uri, userCode, FileHelper.createTempFileFromUri(contentResolver, uri, cacheDir))
        } ?: run {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateWidget(imageUrl: String) {
        val intent = Intent(this, ImageWidgetProvider::class.java).apply {
            action = ImageWidgetProvider.ACTION_UPDATE_IMAGE
            putExtra(ImageWidgetProvider.EXTRA_IMAGE_URI, imageUrl)
            putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) // Update all widgets
        }
        sendBroadcast(intent)
        
        // Also update all instances of the widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this, ImageWidgetProvider::class.java)
        )
        
        if (appWidgetIds.isNotEmpty()) {
            // Force update all widgets
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_image)
            // Call onUpdate on the provider
            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                component = ComponentName(this@MainActivity, ImageWidgetProvider::class.java)
            }
            sendBroadcast(updateIntent)
        }
    }
    
    private fun fetchAndDisplayImage() {
        val userCode = userPreferences.getUserCode()
        if (userCode.isNullOrEmpty() || userCode.length != 4 || !userCode.all { it.isDigit() }) {
            Log.d("MainActivity", "Invalid code, cannot fetch image")
            return
        }
        
        imageOpsHelper.fetchAndDisplayImage(userCode)
    }
    
    private fun scheduleImmediateWidgetUpdate() {
        val workManager = WorkManager.getInstance(applicationContext)

        val periodicUpdateRequest =
            PeriodicWorkRequestBuilder<ImageUpdateWorker>(15, TimeUnit.MINUTES)
                .build()

        workManager.enqueueUniquePeriodicWork(
            "ImageUpdateWorkerPeriodic",
            ExistingPeriodicWorkPolicy.KEEP, // Keep the existing work if it's already scheduled
            periodicUpdateRequest
        )
        
        Log.d("MainActivity", "Scheduled periodic widget update check (every 15 minutes)")
    }
    
    override fun onPause() {
        super.onPause()
        
        // If the user has a code saved, trigger an update check when app goes to background
        if (userPreferences.isCodeSaved()) {
            scheduleImmediateWidgetUpdate()
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check notification permission if not already granted
        if (!notificationPermissionHelper.isNotificationListenerEnabled()) {
            Log.d(TAG, "onResume: Notification permission not granted, showing dialog")
            checkNotificationPermission()
        }
        
        // Only check for widget image if we're not showing a temporary preview
        // and the user has a code saved
        if (!imageOpsHelper.isShowingTemporaryPreview && userPreferences.isCodeSaved()) {
            checkWidgetImageForPreview()
        }
    }
    
    private fun checkWidgetImageForPreview() {
        val savedImageUri = WidgetPreferences.getSavedImageUri(this)
        val userCode = userPreferences.getUserCode()
        
        if (savedImageUri != null && userCode != null) {
            // Strip any timestamp parameters to check the base URI
            val baseUri = savedImageUri.split("?")[0].split("&")[0]
            
            // Only update if the saved image URI matches the current code
            if (baseUri.contains("/$userCode") || baseUri.contains("code=$userCode") ||
                savedImageUri.contains("/$userCode") || savedImageUri.contains("code=$userCode")) {
                Log.d("MainActivity", "Found matching widget image, updating preview: $savedImageUri")
                imageOpsHelper.updatePreviewWithUrl(savedImageUri, userCode)
            } else {
                Log.d("MainActivity", "Widget image doesn't match current code, fetching current image instead")
                fetchAndDisplayImage()
            }
        }
    }
    
    /**
     * Check if notification listener permission is granted
     * If not, show dialog requiring the user to enable it
     */
    private fun checkNotificationPermission() {
        if (!notificationPermissionHelper.isNotificationListenerEnabled()) {
            Log.d(TAG, "Notification permission not granted, opening settings directly")
            
            // Try to directly open the settings with the activity result launcher
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                notificationSettingsLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening notification settings directly", e)
                // Fall back to the helper method which will show a dialog
                notificationPermissionHelper.showRequiredPermissionDialog(this)
            }
        } else {
            Log.d(TAG, "Notification permission already granted")
            // Ensure service is running
            notificationPermissionHelper.restartNotificationListenerService()
        }
    }
    
    private fun startChatNotificationService() {
        // Check if the user is logged in and has a code
        if (authManager.isLoggedIn() && userPreferences.getUserCode().isNotEmpty()) {
            Log.d(TAG, "Starting chat notification service")
            ChatNotificationService.startService(this)
        }
    }
} 