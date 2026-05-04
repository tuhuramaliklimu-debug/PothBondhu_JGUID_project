package com.jgd.pothbondhu.myapplication.app

/**
 * Data class for Emergency Contact
 */
data class EmergencyContact(
    val contactId: String = "",
    val name: String = "",
    val phoneNumber: String = ""
)

/**
 * Data class for Emergency Log
 */
data class EmergencyLog(
    val documentId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val bloodGroup: String = "",
    val allergies: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val locationLat: Double = 0.0,
    val locationLon: Double = 0.0,
    val locationAddress: String = "",
    val contactsNotified: List<EmergencyContact> = emptyList(),
    val isActive: Boolean = true
)
