package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var authRepository: AuthRepository
    private val LOCATION_PERMISSION_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authRepository = AuthRepository()

        // Check if user is logged in
        if (!authRepository.isUserLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Initialize views
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

        // Load user data
        loadUserData(userName, userLocation, bloodGroup, allergies, contactCount)

        // Load emergency contacts dynamically
        loadEmergencyContacts()

        // Set Crash Detection status
        val isCrashDetectionOn = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("crash_detection", true)
        crashStatus.text = if (isCrashDetectionOn) "● On" else "○ Off"
        crashStatus.setTextColor(if (isCrashDetectionOn)
            android.graphics.Color.parseColor("#4CAF50")
        else android.graphics.Color.parseColor("#F44336"))

        // SOS Button
        sosFab.setOnClickListener {
            sendSOS()
        }

        // Add Contact Button
        addContactButton.setOnClickListener {
            // Navigate to Profile to add contacts
            showProfileFragment()
            Toast.makeText(this, "Go to Profile tab to add emergency contacts", Toast.LENGTH_SHORT).show()
        }

        // Feature Card Click Listeners
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

        // Setup bottom navigation
        setupBottomNavigation(bottomNavigation)

        // By default, show home content
        showHomeContent()
    }

    private fun showHomeContent() {
        val mainContent = findViewById<LinearLayout>(R.id.mainContent)
        val fragmentContainer = findViewById<androidx.fragment.app.FragmentContainerView>(R.id.fragmentContainer)

        mainContent.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE
    }

    private fun showProfileFragment() {
        val mainContent = findViewById<LinearLayout>(R.id.mainContent)
        val fragmentContainer = findViewById<androidx.fragment.app.FragmentContainerView>(R.id.fragmentContainer)

        mainContent.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ProfileFragment())
            .commit()
    }

    private fun loadEmergencyContacts() {
        val contactsContainer = findViewById<LinearLayout>(R.id.contactsContainer)

        authRepository.getEmergencyContacts { success, contacts ->
            if (success && contacts.isNotEmpty()) {
                // Update contact count
                findViewById<TextView>(R.id.contactCount).text = contacts.size.toString()

                // Clear existing views
                contactsContainer.removeAllViews()

                // Add each contact dynamically
                for (contact in contacts) {
                    val contactView = createContactView(contact)
                    contactsContainer.addView(contactView)

                    // Add divider except for last
                    if (contacts.indexOf(contact) < contacts.size - 1) {
                        val divider = View(this)
                        divider.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        )
                        divider.setBackgroundColor(resources.getColor(R.color.beige_dark))
                        divider.setPadding(0, 8, 0, 8)
                        contactsContainer.addView(divider)
                    }
                }
            } else {
                // Show "No contacts" message
                findViewById<TextView>(R.id.contactCount).text = "0"
                contactsContainer.removeAllViews()

                val noContactsText = TextView(this).apply {
                    text = "No emergency contacts added yet.\nTap '+ Add Contact' to add."
                    textSize = 12f
                    setTextColor(resources.getColor(R.color.navy_light))
                    gravity = android.view.Gravity.CENTER
                    setPadding(16, 24, 16, 24)
                }
                contactsContainer.addView(noContactsText)
            }
        }
    }

    private fun createContactView(contact: EmergencyContact): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 12, 8, 12)
        }

        // Avatar with initials
        val avatar = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40)
            text = contact.name.take(2).uppercase()
            textSize = 14f
            setTextColor(resources.getColor(R.color.beige_light))
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.contact_avatar)
        }

        // Text layout (name and phone)
        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(12, 0, 0, 0)
        }

        val nameText = TextView(this).apply {
            text = "${contact.name} (${getRelationshipString(contact.name)})"
            textSize = 14f
            setTextColor(resources.getColor(R.color.navy_dark))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val phoneText = TextView(this).apply {
            text = "📞 ${contact.phoneNumber}"
            textSize = 12f
            setTextColor(resources.getColor(R.color.navy_light))
        }

        textLayout.addView(nameText)
        textLayout.addView(phoneText)
        layout.addView(avatar)
        layout.addView(textLayout)

        // Call button
        val callBtn = Button(this).apply {
            text = "Call"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.navy_primary))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.beige_light))
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Calling ${contact.name}...", Toast.LENGTH_SHORT).show()
                // You can add actual phone call intent here
            }
        }

        layout.addView(callBtn)
        return layout
    }

    private fun getRelationshipString(name: String): String {
        return when {
            name.contains("Fatima", ignoreCase = true) -> "Mom"
            name.contains("Karim", ignoreCase = true) -> "Dad"
            name.contains("Roni", ignoreCase = true) -> "Sister"
            else -> "Contact"
        }
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
        userLocation.text = "Dhaka, Bangladesh"
    }

    private fun sendSOS() {
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
                    showHomeContent()
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
                    showProfileFragment()
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh contacts when returning to home screen
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (currentFragment == null || currentFragment !is ProfileFragment) {
            loadEmergencyContacts()

            // Refresh contact count
            authRepository.getEmergencyContacts { success, contacts ->
                if (success) {
                    findViewById<TextView>(R.id.contactCount).text = contacts.size.toString()
                }
            }
        }
    }
}