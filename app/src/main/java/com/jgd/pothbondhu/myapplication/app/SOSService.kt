package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        Log.d(TAG, "========== SOS SERVICE STARTED ==========")
        Log.d(TAG, "Contacts to notify: ${contacts.size}")

        // Get current location
        getCurrentLocation { location ->
            if (location == null) {
                Log.e(TAG, "❌ Failed to get current location")
                onComplete(false, "Could not get current location. Please ensure GPS is enabled and you have set a mock location on emulator.", null)
                return@getCurrentLocation
            }

            Log.d(TAG, "✅ Location obtained: ${location.latitude}, ${location.longitude}")

            // Get address from coordinates
            getAddressFromLocation(location) { address ->
                Log.d(TAG, "✅ Address obtained: $address")

                // Get user's medical profile
                authRepository.getMedicalProfileForSOS { bloodGroup, allergies, userName, userEmail ->
                    Log.d(TAG, "✅ Medical profile: Blood=$bloodGroup, Allergies=$allergies, Name=$userName")

                    // Send SMS to all contacts
                    sendSmsToContacts(contacts, location, address, userName)

                    // Show notification
                    showNotification("SOS Sent", "Emergency alert sent to ${contacts.size} contacts")

                    // Complete successfully
                    onComplete(true, "SOS sent successfully to ${contacts.size} contacts", null)
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
            Log.e(TAG, "Location permission not granted")
            callback(null)
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "Location found via FusedLocationProvider: ${location.latitude}, ${location.longitude}")
                    callback(location)
                } else {
                    Log.e(TAG, "Location is null from FusedLocationProvider - waiting for GPS fix")
                    // Try to get a fresh location
                    requestFreshLocation(callback)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location: ${e.message}")
                callback(null)
            }
    }

    private fun requestFreshLocation(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }

        // Try to get a fresh location update
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                Log.d(TAG, "Fresh location obtained: ${location.latitude}, ${location.longitude}")
                callback(location)
            } else {
                Log.e(TAG, "Still no location available. Please set mock location on emulator.")
                callback(null)
            }
        }
    }

    private fun getAddressFromLocation(location: Location, callback: (String) -> Unit) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val addressString = buildString {
                    address.getAddressLine(0)?.let { append(it) }
                    if (!address.locality.isNullOrEmpty()) append(", ${address.locality}")
                    if (!address.countryName.isNullOrEmpty()) append(", ${address.countryName}")
                }
                callback(addressString.ifEmpty { "${location.latitude}, ${location.longitude}" })
            } else {
                callback("${location.latitude}, ${location.longitude}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoder error: ${e.message}")
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
                Log.d(TAG, "✅ SMS sent to ${contact.name} (${contact.phoneNumber})")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to send SMS to ${contact.name}: ${e.message}")
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

        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}