// LoginActivity.kt - Login screen UI and logic
package com.jgd.pothbondhu.myapplication.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.View

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var signupLink: TextView
    private lateinit var forgotPasswordLink: TextView
    private lateinit var loadingIndicator: View

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize UI elements
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        signupLink = findViewById(R.id.signupLink)
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        // Check if user is already logged in
        if (authRepository.isUserLoggedIn()) {
            navigateToHome()
        }

        // Login button click listener
        loginButton.setOnClickListener {
            performLogin()
        }

        // Signup link click listener
        signupLink.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Forgot password link click listener
        forgotPasswordLink.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isBlank()) {
                Toast.makeText(this, "Enter your email to reset password", Toast.LENGTH_SHORT).show()
            } else {
                resetPassword(email)
            }
        }
    }

    /**
     * Perform login validation and Firebase authentication
     */
    private fun performLogin()
    {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (!validateInputs(email, password)) {
            return
        }

        showLoading(true)

        authRepository.loginUser(email, password) { success, message, userId ->
            showLoading(false)

            if (success) {
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                navigateToHome()
            } else {
                // Show specific error message
                val friendlyMessage = when {
                    message.contains("user-not-found", ignoreCase = true) ->
                        "No account found with this email. Please sign up first."
                    message.contains("wrong-password", ignoreCase = true) ->
                        "Incorrect password. Please try again."
                    message.contains("invalid-email", ignoreCase = true) ->
                        "Invalid email format."
                    message.contains("network", ignoreCase = true) ->
                        "Network error. Check your internet connection."
                    message.contains("too-many-requests", ignoreCase = true) ->
                        "Too many failed attempts. Try again later."
                    message.contains("internal-error", ignoreCase = true) ->
                        "Firebase authentication not properly configured. Please enable Email/Password sign-in in Firebase Console."
                    else -> message
                }
                Toast.makeText(this, friendlyMessage, Toast.LENGTH_LONG).show()

                // Log the full error for debugging
                Log.e("LoginActivity", "Login failed: $message")
            }
        }
    }


    /**
     * Validate login inputs
     */
    private fun validateInputs(email: String, password: String): Boolean {
        return when {
            email.isBlank() -> {
                emailInput.error = "Email is required"
                false
            }
            !email.contains("@") || !email.contains(".") -> {
                emailInput.error = "Invalid email format"
                false
            }
            password.isBlank() -> {
                passwordInput.error = "Password is required"
                false
            }
            password.length < 6 -> {
                passwordInput.error = "Password must be at least 6 characters"
                false
            }
            else -> true
        }
    }

    /**
     * Reset password for user
     */
    private fun resetPassword(email: String) {
        showLoading(true)
        authRepository.resetPassword(email) { success, message ->
            showLoading(false)
            if (success) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Navigate to home screen
     */
    private fun navigateToHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Show/hide loading indicator
     */
    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
    }
}