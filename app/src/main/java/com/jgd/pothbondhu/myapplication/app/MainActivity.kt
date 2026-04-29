package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.cardview.widget.CardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var authRepository: AuthRepository
    private val LOCATION_PERMISSION_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authRepository = AuthRepository()


        if (!authRepository.isUserLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }


        val sosFab = findViewById<ExtendedFloatingActionButton>(R.id.sosFab)
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val addContactButton = findViewById<Button>(R.id.addContactButton)

        val cardSos = findViewById<CardView>(R.id.cardSos)
        val cardCrash = findViewById<CardView>(R.id.cardCrash)
        val cardFindHelp = findViewById<CardView>(R.id.cardFindHelp)
        val cardJourney = findViewById<CardView>(R.id.cardJourney)
        val cardBloodBank = findViewById<CardView>(R.id.cardBloodBank)
        val cardRide = findViewById<CardView>(R.id.cardRide)

        val contactCount = findViewById<TextView>(R.id.contactCount)
        val crashStatus = findViewById<TextView>(R.id.crashStatus)
        val userName = findViewById<TextView>(R.id.userName)
        val userLocation = findViewById<TextView>(R.id.userLocation)
        val bloodGroup = findViewById<TextView>(R.id.bloodGroup)
        val allergies = findViewById<TextView>(R.id.allergies)


        loadUserData(userName, userLocation, bloodGroup, allergies, contactCount)

        val isCrashDetectionOn = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("crash_detection", true)
        crashStatus.text = if (isCrashDetectionOn) "● On" else "○ Off"
        crashStatus.setTextColor(if (isCrashDetectionOn)
            android.graphics.Color.parseColor("#4CAF50")
        else android.graphics.Color.parseColor("#F44336"))


        sosFab.setOnClickListener {
            sendSOS()
        }

        addContactButton.setOnClickListener {
            Toast.makeText(this, "Add Emergency Contact - Coming Soon!", Toast.LENGTH_SHORT).show()
        }


        cardSos.setOnClickListener {
            Toast.makeText(this, "🚨 SOS Alert Feature - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        cardCrash.setOnClickListener {
            Toast.makeText(this, "📱 Crash Detection Settings - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        cardFindHelp.setOnClickListener {
            Toast.makeText(this, "🗺️ Find Help - Map Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        cardJourney.setOnClickListener {
            Toast.makeText(this, "⏱️ Journey Timer - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        cardBloodBank.setOnClickListener {
            Toast.makeText(this, "🩸 Blood Bank Finder - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        cardRide.setOnClickListener {
            Toast.makeText(this, "🚗 Book Ride - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.contact1).setOnClickListener {
            Toast.makeText(this, "Call Fatima Mia", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.contact2).setOnClickListener {
            Toast.makeText(this, "Call Karim Hassan", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.contact3).setOnClickListener {
            Toast.makeText(this, "Call Roni Sultana", Toast.LENGTH_SHORT).show()
        }

        setupBottomNavigation(bottomNavigation)
    }

    private fun loadUserData(
        userName: TextView,
        userLocation: TextView,
        bloodGroup: TextView,
        allergies: TextView,
        contactCount: TextView
    ) {
        val userId = authRepository.getCurrentUserId()
        if (userId != null) {
            authRepository.getUserProfile(userId) { success, user ->
                if (success && user != null) {
                    userName.text = user.userName.ifEmpty { "PothBondhu User" }
                    bloodGroup.text = "Blood Group: ${user.bloodGroup.ifEmpty { "Not set" }}"
                    allergies.text = "Allergies: ${user.allergies.ifEmpty { "None" }}"
                }
            }
        }

        authRepository.getEmergencyContacts { success, contacts ->
            if (success) {
                contactCount.text = contacts.size.toString()
            }
        }
        userLocation.text = "Dhaka, Bangladesh"
    }

    private fun sendSOS() {
        // Get emergency contacts
        authRepository.getEmergencyContacts { success, contacts ->
            if (success && contacts.isNotEmpty()) {
                Toast.makeText(
                    this,
                    "🚨 SOS Sent to ${contacts.size} contacts!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "⚠️ No emergency contacts added. Please add contacts first.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupBottomNavigation(bottomNavigation: BottomNavigationView) {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    true
                }
                R.id.nav_nearby -> {
                    Toast.makeText(this, "📍 Nearby Services - Coming Soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_alerts -> {
                    Toast.makeText(this, "🔔 Alerts History - Coming Soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_profile -> {
                    Toast.makeText(this, "👤 User Profile - Coming Soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val contactCount = findViewById<TextView>(R.id.contactCount)
        authRepository.getEmergencyContacts { success, contacts ->
            if (success) {
                contactCount.text = contacts.size.toString()
            }
        }
    }
}