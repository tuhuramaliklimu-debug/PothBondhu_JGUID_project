// AuthRepository.kt - Handles all Firebase authentication logic
package com.jgd.pothbondhu.myapplication.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthRepository {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Register a new user with email and password
     * Returns: Result<String> - Success with userId or failure with error message
     */
    fun registerUser(
        email: String,
        password: String,
        userName: String,
        userPhone: String,
        callback: (success: Boolean, message: String, userId: String?) -> Unit
    ) {
        // Validate inputs
        if (email.isBlank() || password.isBlank() || userName.isBlank()) {
            callback(false, "All fields are required", null)
            return
        }

        if (password.length < 6) {
            callback(false, "Password must be at least 6 characters", null)
            return
        }

        // Create user with Firebase Authentication
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val userId = firebaseAuth.currentUser?.uid ?: ""

                    // Create user profile in Firestore
                    val userProfile = hashMapOf(
                        "userId" to userId,
                        "userName" to userName,
                        "userEmail" to email,
                        "userPhone" to userPhone,
                        "bloodGroup" to "",
                        "createdAt" to System.currentTimeMillis()
                    )

                    firestore.collection("users").document(userId)
                        .set(userProfile)
                        .addOnSuccessListener {
                            callback(true, "Registration successful", userId)
                        }
                        .addOnFailureListener { exception ->
                            callback(false, "Failed to save user profile: ${exception.message}", null)
                        }
                } else {
                    val errorMessage = authTask.exception?.message ?: "Registration failed"
                    callback(false, errorMessage, null)
                }
            }
    }

    /**
     * Login user with email and password
     * Returns: Result<String> - Success with userId or failure with error message
     */
    fun loginUser(email: String, password: String, callback: (success: Boolean, message: String, userId: String?) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            callback(false, "Email and password are required", null)
            return
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val userId = firebaseAuth.currentUser?.uid ?: ""
                    callback(true, "Login successful", userId)
                } else {
                    val exception = authTask.exception
                    // Log the actual error
                    android.util.Log.e("AuthRepository", "Login error: ${exception?.message}")
                    exception?.printStackTrace()

                    val errorMessage = exception?.message ?: "Login failed"
                    callback(false, errorMessage, null)
                }
            }
    }

    /**
     * Send password reset email
     */
    fun resetPassword(
        email: String,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        if (email.isBlank()) {
            callback(false, "Email is required")
            return
        }

        firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { resetTask ->
                if (resetTask.isSuccessful) {
                    callback(true, "Password reset email sent to $email")
                } else {
                    val errorMessage = resetTask.exception?.message ?: "Failed to send reset email"
                    callback(false, errorMessage)
                }
            }
    }

    /**
     * Logout current user
     */
    fun logoutUser() {
        firebaseAuth.signOut()
    }

    /**
     * Get current logged-in user ID
     */
    fun getCurrentUserId(): String? {
        return firebaseAuth.currentUser?.uid
    }

    /**
     * Check if user is logged in
     */
    fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    /**
     * Fetch user profile from Firestore
     */
    fun getUserProfile(
        userId: String,
        callback: (success: Boolean, user: User?) -> Unit
    ) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = User(
                        userId = document.getString("userId") ?: "",
                        userName = document.getString("userName") ?: "",
                        userEmail = document.getString("userEmail") ?: "",
                        userPhone = document.getString("userPhone") ?: "",
                        bloodGroup = document.getString("bloodGroup") ?: ""
                    )
                    callback(true, user)
                } else {
                    callback(false, null)
                }
            }
            .addOnFailureListener {
                callback(false, null)
            }
    }
}