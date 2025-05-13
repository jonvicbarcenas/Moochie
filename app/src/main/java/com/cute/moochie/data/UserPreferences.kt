package com.cute.moochie.data

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("MoochiePrefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_USER_CODE = "user_code"
        private const val PREF_IMAGE_TIMESTAMP = "image_timestamp"
        private const val PREF_FCM_TOKEN = "fcm_token"
    }
    
    fun saveUserCode(code: String) {
        sharedPreferences.edit().putString(PREF_USER_CODE, code).apply()
    }
    
    fun getUserCode(): String {
        return sharedPreferences.getString(PREF_USER_CODE, "") ?: ""
    }
    
    fun isCodeSaved(): Boolean {
        return !getUserCode().isNullOrEmpty()
    }
    
    fun saveImageTimestamp(timestamp: String) {
        sharedPreferences.edit().putString(PREF_IMAGE_TIMESTAMP, timestamp).apply()
    }
    
    fun getImageTimestamp(): String {
        return sharedPreferences.getString(PREF_IMAGE_TIMESTAMP, "") ?: ""
    }
    
    fun hasNewTimestamp(newTimestamp: String): Boolean {
        if (newTimestamp.isEmpty()) return false
        
        val currentTimestamp = getImageTimestamp()
        return currentTimestamp != newTimestamp
    }
    
    fun saveFcmToken(token: String) {
        sharedPreferences.edit().putString(PREF_FCM_TOKEN, token).apply()
    }
    
    fun getFcmToken(): String {
        return sharedPreferences.getString(PREF_FCM_TOKEN, "") ?: ""
    }
} 