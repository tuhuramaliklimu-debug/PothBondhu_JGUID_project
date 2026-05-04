package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale

class SOSService(private val context: Context) {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val TAG = "SOSService"

    /**
     * Send SOS alerts to all emergency contacts
     */
    fun sendSOS(
        authRepository: AuthRepository,
        contacts: List<EmergencyContact>,
        onComplete: (success: Boolean, message: String, emergencyId: String?) -> Unit
    ) {
        // Get current location
        getCurrentLocation { location ->
            if (location == null) {
                onComplete(false, "Could not get current location", null)
                return@getCurrentLocation
            }

            // Get address from coordinates
            getAddressFromLocation(location) { address ->

                // Get user's medical profile
                authRepository.getMedicalProfileForSOS { bloodGroup, allergies, userName, userEmail ->

                    // Send SMS to all contacts
                    sendSmsToContacts(contacts, location, address, userName)

                    // Create emergency log
                    val emergencyLog = EmergencyLog(
                        userId = authRepository.getCurrentUserId() ?: "",
                        userName = userName,
                        userEmail = userEmail,
                        bloodGroup = bloodGroup,
                        allergies = allergies,
                        timestamp = System.currentTimeMillis(),
                        locationLat = location.latitude,
                        locationLon = location.longitude,
                        locationAddress = address,
                        contactsNotified = contacts,
                        isActive = true
                    )

                    // Log to Firestore
                    authRepository.logEmergency(emergencyLog) { success, docId, message ->
                        if (success) {
                            showNotification("SOS Sent", "Emergency alert sent to ${contacts.size} contacts")
                            onComplete(true, "SOS sent successfully to ${contacts.size} contacts", docId)
                        } else {
                            onComplete(false, message, null)
                        }
                    }
                }
            }
        }
    }

    private fun getCurrentLocation(callback: (Location?) -> Unit) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                callback(location)
            }
            .addOnFailureListener {
                callback(null)
            }
    }

    private fun getAddressFromLocation(location: Location, callback: (String) -> Unit) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val addressString = "${address.getAddressLine(0)}, ${address.locality}, ${address.countryName}"
                callback(addressString)
            } else {
                callback("${location.latitude}, ${location.longitude}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder error", e)
            callback("${location.latitude}, ${location.longitude}")
        }
    }

    private fun sendSmsToContacts(
        contacts: List<EmergencyContact>,
        location: Location,
        address: String,
        userName: String
    ) {
        val mapsLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"

        val message = """
            🚨 SOS EMERGENCY ALERT 🚨
            
            $userName needs immediate help!
            
            Location: $address
            Maps Link: $mapsLink
            Time: ${System.currentTimeMillis()}
            
            Please respond immediately.
            - Sent from PothBondhu Emergency App
        """.trimIndent()

        for (contact in contacts) {
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
                Log.d(TAG, "SMS sent to ${contact.name} (${contact.phoneNumber})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to ${contact.name}", e)
            }
        }
    }

    private fun showNotification(title: String, content: String) {
        val channelId = "sos_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}