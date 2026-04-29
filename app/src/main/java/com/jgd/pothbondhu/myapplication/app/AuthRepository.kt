package com.jgd.pothbondhu.myapplication.app

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class AuthRepository {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "AuthRepository"

    /**
     * Register a new user with email and password
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
                        "allergies" to "",
                        "medications" to "",
                        "location" to "Sylhet, Bangladesh",
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
                    Log.e(TAG, "Registration error: $errorMessage")
                    callback(false, errorMessage, null)
                }
            }
    }

    /**
     * Login user with email and password
     */
    fun loginUser(
        email: String,
        password: String,
        callback: (success: Boolean, message: String, userId: String?) -> Unit
    ) {
        // Validate inputs
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
                    val errorMessage = authTask.exception?.message ?: "Login failed"
                    Log.e(TAG, "Login error: $errorMessage")
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
                    Log.e(TAG, "Reset password error: $errorMessage")
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
     * Get current user as FirebaseUser
     */
    fun getCurrentUser(): FirebaseUser? {
        return firebaseAuth.currentUser
    }

    /**
     * Get current user's email
     */
    fun getUserEmail(): String? {
        return firebaseAuth.currentUser?.email
    }

    /**
     * Get current user's display name
     */
    fun getUserName(): String? {
        return firebaseAuth.currentUser?.displayName
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
                        bloodGroup = document.getString("bloodGroup") ?: "",
                        allergies = document.getString("allergies") ?: "",
                        medications = document.getString("medications") ?: "",
                        location = document.getString("location") ?: "Sylhet, Bangladesh"
                    )
                    callback(true, user)
                } else {
                    callback(false, null)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Get user profile error: ${exception.message}")
                callback(false, null)
            }
    }

    /**
     * Save or update user profile in Firestore
     */
    fun saveUserProfile(
        userId: String,
        userData: Map<String, Any>,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        firestore.collection("users").document(userId)
            .update(userData)
            .addOnSuccessListener {
                callback(true, "Profile updated successfully")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Save profile error: ${exception.message}")
                callback(false, "Failed to update profile: ${exception.message}")
            }
    }

    /**
     * Delete user account and all associated data
     */
    fun deleteUser(callback: (success: Boolean, message: String) -> Unit) {
        val userId = getCurrentUserId() ?: ""

        // Delete from Firestore first
        firestore.collection("users").document(userId)
            .delete()
            .addOnSuccessListener {
                // Then delete from Authentication
                firebaseAuth.currentUser?.delete()
                    ?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            callback(true, "Account deleted successfully")
                        } else {
                            callback(false, task.exception?.message ?: "Failed to delete account")
                        }
                    }
            }
            .addOnFailureListener { exception ->
                callback(false, "Failed to delete user data: ${exception.message}")
            }
    }

    /**
     * Update user's blood group
     */
    fun updateBloodGroup(bloodGroup: String, callback: (success: Boolean, message: String) -> Unit) {
        val userId = getCurrentUserId() ?: ""
        val updates = mapOf("bloodGroup" to bloodGroup)
        saveUserProfile(userId, updates, callback)
    }

    /**
     * Add an emergency contact
     */
    fun addEmergencyContact(
        name: String,
        phoneNumber: String,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        val userId = getCurrentUserId() ?: ""
        val contactId = firestore.collection("users").document(userId)
            .collection("emergencyContacts").document().id

        val contact = hashMapOf(
            "contactId" to contactId,
            "name" to name,
            "phoneNumber" to phoneNumber,
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("users").document(userId)
            .collection("emergencyContacts").document(contactId)
            .set(contact)
            .addOnSuccessListener {
                callback(true, "Emergency contact added")
            }
            .addOnFailureListener { exception ->
                callback(false, "Failed to add contact: ${exception.message}")
            }
    }

    /**
     * Get all emergency contacts
     */
    fun getEmergencyContacts(
        callback: (success: Boolean, contacts: List<EmergencyContact>) -> Unit
    ) {
        val userId = getCurrentUserId() ?: ""

        firestore.collection("users").document(userId)
            .collection("emergencyContacts")
            .get()
            .addOnSuccessListener { documents ->
                val contacts = mutableListOf<EmergencyContact>()
                for (document in documents) {
                    val contact = EmergencyContact(
                        contactId = document.getString("contactId") ?: "",
                        name = document.getString("name") ?: "",
                        phoneNumber = document.getString("phoneNumber") ?: ""
                    )
                    contacts.add(contact)
                }
                callback(true, contacts)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Get contacts error: ${exception.message}")
                callback(false, emptyList())
            }
    }
}

/**
 * Data class for Emergency Contact
 */
data class EmergencyContact(
    val contactId: String = "",
    val name: String = "",
    val phoneNumber: String = ""
)