package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.util.Log
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.osmdroid.views.MapView

class MainActivity : AppCompatActivity() {

    private lateinit var authRepository: AuthRepository
    private lateinit var sosService: SOSService
    private lateinit var osmHelper: OSMHelper
    private lateinit var mapView: MapView
    private lateinit var crashDetectionServiceIntent: Intent

    private val LOCATION_PERMISSION_REQUEST = 1
    private val SMS_PERMISSION_REQUEST = 2

    private var sosCountDownTimer: CountDownTimer? = null
    private var isSosActive = false

    private val crashReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "CRASH_DETECTED" -> {
                    val message = intent.getStringExtra("message") ?: "Crash detected!"
                    showCrashAlertDialog(message)
                }
                "AUTO_SOS" -> {
                    triggerAutoSOS()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authRepository = AuthRepository()
        sosService = SOSService(this)
        crashDetectionServiceIntent = Intent(this, CrashDetectionService::class.java)

        if (!authRepository.isUserLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        requestSmsPermission()

        mapView = findViewById(R.id.mapView)
        osmHelper = OSMHelper(this, mapView)
        osmHelper.setupMap()
        osmHelper.enableMyLocation()

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

        loadEmergencyContacts()

        val isCrashDetectionOn = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("crash_detection_enabled", true)
        crashStatus.text = if (isCrashDetectionOn) "● On" else "○ Off"
        crashStatus.setTextColor(if (isCrashDetectionOn)
            android.graphics.Color.parseColor("#4CAF50")
        else android.graphics.Color.parseColor("#F44336"))

        if (isCrashDetectionOn) {
            startCrashDetectionService()
        }

        sosFab.setOnClickListener {
            showSOSConfirmationDialog()
        }

        addContactButton.setOnClickListener {
            showProfileFragment()
            Toast.makeText(this, "Go to Profile tab to add emergency contacts", Toast.LENGTH_SHORT).show()
        }

        cardSos.setOnClickListener {
            showSOSConfirmationDialog()
        }

        cardCrash.setOnClickListener {
            showProfileFragment()
            Toast.makeText(this, "Go to Profile → Safety Settings to adjust crash detection", Toast.LENGTH_LONG).show()
        }

        cardFindHelp.setOnClickListener {
            startActivity(Intent(this, NearbyActivity::class.java))
        }

        cardJourney.setOnClickListener {
            startActivity(Intent(this, JourneyTimerActivity::class.java))
        }

        cardBloodBank.setOnClickListener {
            val intent = Intent(this, NearbyActivity::class.java)
            intent.putExtra("selected_category", "blood_bank")
            startActivity(intent)
        }

        cardRide.setOnClickListener {
            startActivity(Intent(this, RideBookingActivity::class.java))
        }

        setupBottomNavigation(bottomNavigation)
        showHomeContent()
        registerCrashReceiver()
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
                    userLocation.text = user.location.ifEmpty { "Dhaka, Bangladesh" }

                    Log.d("MainActivity", "User loaded: ${user.userName}, Location: ${user.location}")
                } else {
                    Log.e("MainActivity", "Failed to load user profile")
                    userName.text = authRepository.getUserEmail()?.split("@")?.first() ?: "PothBondhu User"
                    userLocation.text = "Dhaka, Bangladesh"
                }
            }
        } else {
            Log.e("MainActivity", "User ID is null")
            userName.text = "PothBondhu User"
            userLocation.text = "Dhaka, Bangladesh"
        }
    }

    private fun registerCrashReceiver() {
        val filter = IntentFilter().apply {
            addAction("CRASH_DETECTED")
            addAction("AUTO_SOS")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(crashReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(crashReceiver, filter)
        }
    }

    private fun startCrashDetectionService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(crashDetectionServiceIntent)
        } else {
            startService(crashDetectionServiceIntent)
        }
        Log.d("MainActivity", "Crash detection service started")
    }

    private fun showCrashAlertDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ CRASH DETECTED")
            .setMessage("$message\n\nSOS will be sent in 5 seconds unless you cancel.")
            .setPositiveButton("Cancel SOS") { _, _ ->
                val cancelIntent = Intent("CRASH_CANCELLED")
                sendBroadcast(cancelIntent)
                Toast.makeText(this, "SOS cancelled", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Send SOS Now") { _, _ ->
                sendSOS()
            }
            .setCancelable(false)
            .show()
    }

    private fun triggerAutoSOS() {
        Log.d("MainActivity", "Auto SOS triggered by crash detection")
        sendSOS()
    }

    private fun showSOSConfirmationDialog() {
        authRepository.getEmergencyContacts { success, contacts ->
            if (!success || contacts.isEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("No Emergency Contacts")
                    .setMessage("You haven't added any emergency contacts. Please add contacts in Profile first.")
                    .setPositiveButton("Add Contacts") { _, _ ->
                        showProfileFragment()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@getEmergencyContacts
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("🚨 SOS EMERGENCY 🚨")
                .setMessage("This will send your live location to ${contacts.size} emergency contact(s).\n\nDo you want to proceed?")
                .setPositiveButton("SEND SOS") { _, _ ->
                    startSOSCountdown()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun startSOSCountdown() {
        if (isSosActive) {
            Toast.makeText(this, "SOS already active! Wait for timer to finish.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_sos_countdown, null)
        val timerText = dialogView.findViewById<TextView>(R.id.timerText)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val sosMessage = dialogView.findViewById<TextView>(R.id.sosMessage)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.show()

        var timeLeft = 5
        timerText.text = "Sending SOS in $timeLeft seconds..."

        sosCountDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = (millisUntilFinished / 1000).toInt()
                timerText.text = "Sending SOS in $timeLeft seconds..."

                when (timeLeft) {
                    5 -> sosMessage.text = "Get ready..."
                    3 -> sosMessage.text = "Stay calm. Help is coming."
                    1 -> sosMessage.text = "Sending SOS now..."
                }
            }

            override fun onFinish() {
                isSosActive = true
                dialog.dismiss()
                executeSOS()
            }
        }.start()

        cancelButton.setOnClickListener {
            sosCountDownTimer?.cancel()
            dialog.dismiss()
            Toast.makeText(this, "SOS cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeSOS() {
        authRepository.getEmergencyContacts { success, contacts ->
            if (!success || contacts.isEmpty()) {
                isSosActive = false
                Toast.makeText(this, "No emergency contacts found", Toast.LENGTH_SHORT).show()
                return@getEmergencyContacts
            }

            val progressDialog = AlertDialog.Builder(this)
                .setTitle("Sending SOS...")
                .setMessage("Sending alerts to ${contacts.size} contact(s)...\nPlease wait.")
                .setCancelable(false)
                .create()
            progressDialog.show()

            sosService.sendSOS(authRepository, contacts) { sendSuccess, message, emergencyId ->
                progressDialog.dismiss()
                isSosActive = false

                if (sendSuccess) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("✅ SOS Sent Successfully")
                        .setMessage("Emergency alerts have been sent to ${contacts.size} contact(s).\n\nThey have received your live location with Google Maps link.")
                        .setPositiveButton("OK") { _, _ -> }
                        .setNeutralButton("Share Again") { _, _ ->
                            startSOSCountdown()
                        }
                        .show()
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("❌ SOS Failed")
                        .setMessage("Failed to send SOS: $message\n\nPlease check your connection and try again.")
                        .setPositiveButton("Try Again") { _, _ ->
                            startSOSCountdown()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
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
                findViewById<TextView>(R.id.contactCount).text = contacts.size.toString()
                contactsContainer.removeAllViews()

                for (contact in contacts) {
                    val contactView = createContactView(contact)
                    contactsContainer.addView(contactView)

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

        val avatar = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40)
            text = contact.name.take(2).uppercase()
            textSize = 14f
            setTextColor(resources.getColor(R.color.beige_light))
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.contact_avatar)
        }

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
            text = contact.name
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
            }
        }

        layout.addView(callBtn)
        return layout
    }

    private fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.SEND_SMS),
                SMS_PERMISSION_REQUEST)
        }
    }

    // ========== STEP 10: UPDATED BOTTOM NAVIGATION WITH ALERTS HISTORY ==========
    private fun setupBottomNavigation(bottomNavigation: BottomNavigationView) {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showHomeContent()
                    true
                }
                R.id.nav_nearby -> {
                    startActivity(Intent(this, NearbyActivity::class.java))
                    true
                }
                R.id.nav_alerts -> {
                    // ========== UPDATED: Open Alert History Activity ==========
                    startActivity(Intent(this, AlertHistoryActivity::class.java))
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            SMS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "SMS permission denied. SOS alerts may not work properly.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (currentFragment == null || currentFragment !is ProfileFragment) {
            loadEmergencyContacts()
            authRepository.getEmergencyContacts { success, contacts ->
                if (success) {
                    findViewById<TextView>(R.id.contactCount).text = contacts.size.toString()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sosCountDownTimer?.cancel()
        try {
            unregisterReceiver(crashReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}