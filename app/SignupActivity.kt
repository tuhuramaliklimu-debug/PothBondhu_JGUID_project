// SignupActivity.kt - Registration screen UI and logic
package com.jgd.pothbondhu.myapplication.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.View


class SignupActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var signupButton: Button
    private lateinit var loginLink: TextView
    private lateinit var loadingIndicator: View

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Initialize UI elements
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        phoneInput = findViewById(R.id.phoneInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        signupButton = findViewById(R.id.signupButton)
        loginLink = findViewById(R.id.loginLink)
        loadingIndicator = findViewById(R.id.loadingIndicator)

        // Signup button click listener
        signupButton.setOnClickListener {
            performSignup()
        }

        // Login link click listener
        loginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    /**
     * Perform signup validation and Firebase registration
     */
    private fun performSignup() {
        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        // Client-side validation
        if (!validateInputs(name, email, phone, password, confirmPassword)) {
            return
        }

        // Show loading state
        showLoading(true)

        // Call Firebase registration
        authRepository.registerUser(email, password, name, phone) { success, message, userId ->
            showLoading(false)

            if (success) {
                Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                navigateToLogin()
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Validate signup inputs
     */
    private fun validateInputs(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        return when {
            name.isBlank() -> {
                nameInput.error = "Name is required"
                false
            }
            name.length < 3 -> {
                nameInput.error = "Name must be at least 3 characters"
                false
            }
            email.isBlank() -> {
                emailInput.error = "Email is required"
                false
            }
            !email.contains("@") || !email.contains(".") -> {
                emailInput.error = "Invalid email format"
                false
            }
            phone.isBlank() -> {
                phoneInput.error = "Phone number is required"
                false
            }
            !phone.matches(Regex("^[0-9]{10,11}$")) -> {
                phoneInput.error = "Invalid phone number (10-11 digits required)"
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
            password != confirmPassword -> {
                confirmPasswordInput.error = "Passwords do not match"
                false
            }
            else -> true
        }
    }

    /**
     * Navigate to login screen
     */
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Show/hide loading indicator
     */
    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        signupButton.isEnabled = !isLoading
    }
}