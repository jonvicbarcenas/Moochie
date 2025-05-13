package com.cute.moochie.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cute.moochie.R
import com.cute.moochie.data.AuthManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LoginActivity"
    }
    
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var authManager: AuthManager
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    
    private lateinit var signInButton: SignInButton
    private lateinit var customSignInButton: MaterialButton
    private lateinit var progressIndicator: CircularProgressIndicator
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        // Initialize UI components
        signInButton = findViewById(R.id.sign_in_button)
        customSignInButton = findViewById(R.id.custom_sign_in_button)
        progressIndicator = findViewById(R.id.progress_indicator)
        
        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
            
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        
        // Sign out from Google to prevent automatic sign-in with last account
        googleSignInClient.signOut().addOnCompleteListener {
            Log.d(TAG, "Google sign out complete")
        }
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // Initialize AuthManager
        authManager = AuthManager(this)
        
        // Set up Google SignInButton click listener
        signInButton.setOnClickListener {
            signIn()
        }
        
        // Set up custom sign in button click listener
        customSignInButton.setOnClickListener {
            signIn()
        }
        
        // Register for Google Sign-In result
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    // Google Sign In was successful, authenticate with Firebase
                    val account = task.getResult(ApiException::class.java)
                    firebaseAuthWithGoogle(account)
                } catch (e: ApiException) {
                    // Google Sign In failed, update UI appropriately
                    Log.w(TAG, "Google sign in failed", e)
                    showSignInFailed()
                }
            } else {
                Log.w(TAG, "Google sign in failed: result not OK")
                showSignInFailed()
            }
        }
        
        // Check if user is already signed in
        checkExistingSession()
    }
    
    private fun checkExistingSession() {
        // If user is already logged in, go directly to MainActivity
        if (authManager.isLoggedIn()) {
            startMainActivity()
        }
    }
    
    private fun signIn() {
        showLoading(true)
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }
    
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
        
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    if (user != null) {
                        // Save user session data
                        authManager.saveUserSession(user)
                        startMainActivity()
                    } else {
                        showSignInFailed()
                    }
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    showSignInFailed()
                }
                
                showLoading(false)
            }
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun showSignInFailed() {
        showLoading(false)
        Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
    }
    
    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            signInButton.visibility = View.INVISIBLE
            customSignInButton.visibility = View.INVISIBLE
            progressIndicator.visibility = View.VISIBLE
        } else {
            signInButton.visibility = View.VISIBLE
            customSignInButton.visibility = View.VISIBLE
            progressIndicator.visibility = View.INVISIBLE
        }
    }
}