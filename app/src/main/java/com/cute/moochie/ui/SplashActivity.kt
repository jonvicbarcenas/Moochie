package com.cute.moochie.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cute.moochie.R
import com.cute.moochie.util.VersionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DELAY = 1500L
        private const val VERSION_CHECK_TIMEOUT = 5000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        checkForUpdate()
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            try {
                val result = withTimeoutOrNull(VERSION_CHECK_TIMEOUT) {
                    VersionManager.checkForUpdate()
                }

                if (result == null) {
                    Log.d(TAG, "Version check timed out")
                    proceedToNextScreen()
                    return@launch
                }

                val (updateNeeded, latestVersion, downloadUrl) = result
                
                if (updateNeeded && latestVersion.isNotEmpty() && downloadUrl.isNotEmpty()) {
                    VersionManager.showUpdateDialog(
                        context = this@SplashActivity,
                        latestVersion = latestVersion,
                        downloadUrl = downloadUrl
                    )
                } else {
                    proceedToNextScreen()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during version check", e)
                proceedToNextScreen()
            }
        }
    }

    private fun proceedToNextScreen() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, PermissionCheckActivity::class.java)
            startActivity(intent)
            finish()
        }, SPLASH_DELAY)
    }
} 