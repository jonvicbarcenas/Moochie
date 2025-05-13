package com.cute.moochie.data

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Manages authentication state and user session data
 */
class AuthManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("MoochieAuthPrefs", Context.MODE_PRIVATE)
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    companion object {
        private const val PREF_USER_ID = "user_id"
        private const val PREF_USER_EMAIL = "user_email"
        private const val PREF_USER_NAME = "user_name"
        private const val PREF_USER_PHOTO_URL = "user_photo_url"
        private const val PREF_IS_LOGGED_IN = "is_logged_in"
    }
    
    /**
     * Saves the user session data to SharedPreferences
     */
    fun saveUserSession(user: FirebaseUser) {
        sharedPreferences.edit().apply {
            putString(PREF_USER_ID, user.uid)
            putString(PREF_USER_EMAIL, user.email)
            putString(PREF_USER_NAME, user.displayName)
            putString(PREF_USER_PHOTO_URL, user.photoUrl?.toString())
            putBoolean(PREF_IS_LOGGED_IN, true)
        }.apply()
    }
    
    /**
     * Clears the user session data
     */
    fun clearUserSession() {
        sharedPreferences.edit().apply {
            remove(PREF_USER_ID)
            remove(PREF_USER_EMAIL)
            remove(PREF_USER_NAME)
            remove(PREF_USER_PHOTO_URL)
            putBoolean(PREF_IS_LOGGED_IN, false)
        }.apply()
    }
    
    /**
     * Checks if the user is logged in
     */
    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(PREF_IS_LOGGED_IN, false)
    }
    
    /**
     * Gets the current Firebase user
     */
    fun getCurrentUser(): FirebaseUser? {
        return firebaseAuth.currentUser
    }
    
    /**
     * Gets the current user ID
     */
    fun getUserId(): String? {
        return sharedPreferences.getString(PREF_USER_ID, null)
    }
    
    /**
     * Gets the current user email
     */
    fun getUserEmail(): String? {
        return sharedPreferences.getString(PREF_USER_EMAIL, null)
    }
    
    /**
     * Gets the current user name
     */
    fun getUserName(): String? {
        return sharedPreferences.getString(PREF_USER_NAME, null)
    }
    
    /**
     * Gets the current user photo URL
     */
    fun getUserPhotoUrl(): String? {
        return sharedPreferences.getString(PREF_USER_PHOTO_URL, null)
    }
    
    /**
     * Sign out the user
     */
    fun signOut() {
        // Sign out from Firebase
        firebaseAuth.signOut()
        
        // Sign out from Google
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        googleSignInClient.signOut()
        
        // Clear local session data
        clearUserSession()
    }
} 