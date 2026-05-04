package com.jgd.pothbondhu.myapplication.app

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                        "conditions" to "",
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
                        conditions = document.getString("conditions") ?: "",
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
        val userId = getCurrentUserId() ?: run {
            callback(false, "User not logged in")
            return
        }

        // First delete emergency contacts subcollection
        firestore.collection("users").document(userId)
            .collection("emergencyContacts")
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    firestore.collection("users").document(userId)
                        .collection("emergencyContacts").document(doc.id)
                        .delete()
                }

                // Then delete user document from Firestore
                firestore.collection("users").document(userId)
                    .delete()
                    .addOnSuccessListener {
                        // Finally delete from Authentication
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
            .addOnFailureListener { exception ->
                callback(false, "Failed to delete contacts: ${exception.message}")
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
     * Update user's allergies
     */
    fun updateAllergies(allergies: String, callback: (success: Boolean, message: String) -> Unit) {
        val userId = getCurrentUserId() ?: ""
        val updates = mapOf("allergies" to allergies)
        saveUserProfile(userId, updates, callback)
    }

    /**
     * Update user's medications
     */
    fun updateMedications(medications: String, callback: (success: Boolean, message: String) -> Unit) {
        val userId = getCurrentUserId() ?: ""
        val updates = mapOf("medications" to medications)
        saveUserProfile(userId, updates, callback)
    }

    /**
     * Update user's medical conditions
     */
    fun updateConditions(conditions: String, callback: (success: Boolean, message: String) -> Unit) {
        val userId = getCurrentUserId() ?: ""
        val updates = mapOf("conditions" to conditions)
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
        val userId = getCurrentUserId() ?: run {
            callback(false, "User not logged in")
            return
        }

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
     * Delete an emergency contact
     */
    fun deleteEmergencyContact(
        contactId: String,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        val userId = getCurrentUserId() ?: run {
            callback(false, "User not logged in")
            return
        }

        firestore.collection("users").document(userId)
            .collection("emergencyContacts").document(contactId)
            .delete()
            .addOnSuccessListener {
                callback(true, "Emergency contact deleted")
            }
            .addOnFailureListener { exception ->
                callback(false, "Failed to delete contact: ${exception.message}")
            }
    }

    /**
     * Get all emergency contacts
     */
    fun getEmergencyContacts(
        callback: (success: Boolean, contacts: List<EmergencyContact>) -> Unit
    ) {
        val userId = getCurrentUserId() ?: run {
            callback(false, emptyList())
            return
        }

        firestore.collection("users").document(userId)
            .collection("emergencyContacts")
            .get()
            .addOnSuccessListener { documents ->
                val contacts = mutableListOf<EmergencyContact>()
                for (document in documents) {
                    val contact = EmergencyContact(
                        contactId = document.getString("contactId") ?: document.id,
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

    // ========== SOS EMERGENCY FUNCTIONS ==========

    /**
     * Get user's medical profile for SOS
     */
    fun getMedicalProfileForSOS(callback: (bloodGroup: String, allergies: String, userName: String, userEmail: String) -> Unit) {
        val userId = getCurrentUserId() ?: run {
            callback("Not set", "None", "PothBondhu User", "")
            return
        }

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                val bloodGroup = document.getString("bloodGroup") ?: "Not set"
                val allergies = document.getString("allergies") ?: "None"
                val userName = document.getString("userName") ?: "PothBondhu User"
                val userEmail = document.getString("userEmail") ?: ""
                callback(bloodGroup, allergies, userName, userEmail)
            }
            .addOnFailureListener {
                callback("Not set", "None", "PothBondhu User", "")
            }
    }

    /**
     * Log emergency event to Firestore
     */
    fun logEmergency(
        emergencyLog: EmergencyLog,
        callback: (success: Boolean, documentId: String?, message: String) -> Unit
    ) {
        val userId = getCurrentUserId() ?: run {
            callback(false, null, "User not logged in")
            return
        }

        val emergencyData = hashMapOf(
            "userId" to userId,
            "userName" to emergencyLog.userName,
            "userEmail" to emergencyLog.userEmail,
            "bloodGroup" to emergencyLog.bloodGroup,
            "allergies" to emergencyLog.allergies,
            "timestamp" to emergencyLog.timestamp,
            "formattedTime" to formatTimestamp(emergencyLog.timestamp),
            "locationLat" to emergencyLog.locationLat,
            "locationLon" to emergencyLog.locationLon,
            "locationAddress" to emergencyLog.locationAddress,
            "isActive" to true,
            "contactsNotifiedCount" to emergencyLog.contactsNotified.size
        )

        firestore.collection("emergencies")
            .add(emergencyData)
            .addOnSuccessListener { documentReference ->
                val documentId = documentReference.id

                // Add contacts as subcollection
                for (contact in emergencyLog.contactsNotified) {
                    firestore.collection("emergencies").document(documentId)
                        .collection("contactsNotified")
                        .add(contact)
                }

                callback(true, documentId, "Emergency logged successfully")
                Log.d(TAG, "Emergency logged with ID: $documentId")
            }
            .addOnFailureListener { e ->
                callback(false, null, "Failed to log emergency: ${e.message}")
                Log.e(TAG, "Error logging emergency", e)
            }
    }

    /**
     * Get emergency history for current user
     */
    fun getEmergencyHistory(
        callback: (success: Boolean, emergencies: List<EmergencyLog>) -> Unit
    ) {
        val userId = getCurrentUserId() ?: run {
            callback(false, emptyList())
            return
        }

        firestore.collection("emergencies")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val emergencies = mutableListOf<EmergencyLog>()
                for (document in documents) {
                    val emergency = EmergencyLog(
                        documentId = document.id,
                        userId = document.getString("userId") ?: "",
                        userName = document.getString("userName") ?: "",
                        userEmail = document.getString("userEmail") ?: "",
                        bloodGroup = document.getString("bloodGroup") ?: "",
                        allergies = document.getString("allergies") ?: "",
                        timestamp = document.getLong("timestamp") ?: 0,
                        locationLat = document.getDouble("locationLat") ?: 0.0,
                        locationLon = document.getDouble("locationLon") ?: 0.0,
                        locationAddress = document.getString("locationAddress") ?: "",
                        isActive = document.getBoolean("isActive") ?: true
                    )
                    emergencies.add(emergency)
                }
                callback(true, emergencies)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting emergency history", e)
                callback(false, emptyList())
            }
    }

    /**
     * Get active emergency (if any)
     */
    fun getActiveEmergency(callback: (success: Boolean, emergency: EmergencyLog?) -> Unit) {
        val userId = getCurrentUserId() ?: run {
            callback(false, null)
            return
        }

        firestore.collection("emergencies")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isActive", true)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty()) {
                    callback(true, null)
                } else {
                    val document = documents.documents[0]
                    val emergency = EmergencyLog(
                        documentId = document.id,
                        userId = document.getString("userId") ?: "",
                        userName = document.getString("userName") ?: "",
                        userEmail = document.getString("userEmail") ?: "",
                        bloodGroup = document.getString("bloodGroup") ?: "",
                        allergies = document.getString("allergies") ?: "",
                        timestamp = document.getLong("timestamp") ?: 0,
                        locationLat = document.getDouble("locationLat") ?: 0.0,
                        locationLon = document.getDouble("locationLon") ?: 0.0,
                        locationAddress = document.getString("locationAddress") ?: "",
                        isActive = document.getBoolean("isActive") ?: true
                    )
                    callback(true, emergency)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting active emergency", e)
                callback(false, null)
            }
    }

    /**
     * Deactivate an emergency (when user cancels or resolves)
     */
    fun deactivateEmergency(emergencyId: String, callback: (success: Boolean, message: String) -> Unit) {
        firestore.collection("emergencies").document(emergencyId)
            .update("isActive", false)
            .addOnSuccessListener {
                callback(true, "Emergency deactivated")
            }
            .addOnFailureListener { e ->
                callback(false, "Failed to deactivate: ${e.message}")
            }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return format.format(date)
    }
}