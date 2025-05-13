package com.cute.moochie.widget

import android.content.Context
import android.content.SharedPreferences

object WidgetPreferences {
    private const val PREFS_NAME = "com.cute.moochie.ImageWidgetProvider"
    private const val PREF_IMAGE_URI_KEY = "image_uri"
    
    fun saveImageUri(context: Context, imageUri: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_IMAGE_URI_KEY, imageUri).apply()
    }
    
    fun getSavedImageUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_IMAGE_URI_KEY, null)
    }
} 