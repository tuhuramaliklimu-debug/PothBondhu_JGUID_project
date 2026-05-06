package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class JourneyTimerActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var authRepository: AuthRepository

    // UI Elements
    private lateinit var destinationInput: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var startJourneyBtn: Button
    private lateinit var activeTimerLayout: LinearLayout
    private lateinit var timerText: TextView
    private lateinit var destinationText: TextView
    private lateinit var expectedTimeText: TextView
    private lateinit var checkInBtn: Button
    private lateinit var extendTimerBtn: Button
    private lateinit var cancelJourneyBtn: Button
    private lateinit var statusText: TextView

    // Timer variables
    private var countDownTimer: CountDownTimer? = null
    private var checkInTimer: Timer? = null
    private var currentDestination: String = ""
    private var expectedTimeMillis: Long = 0
    private var isJourneyActive = false
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    companion object {
        private const val PREFS_NAME = "journey_timer_prefs"
        private const val KEY_JOURNEY_ACTIVE = "journey_active"
        private const val KEY_DESTINATION = "destination"
        private const val KEY_EXPECTED_TIME = "expected_time"
        private const val KEY_START_TIME = "start_time"
        private const val NOTIFICATION_CHANNEL_ID = "journey_timer_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journey_timer)

        authRepository = AuthRepository()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupViews()
        setupToolbar()
        checkForActiveJourney()
        requestLocationPermission()
    }

    private fun setupViews() {
        destinationInput = findViewById(R.id.destinationInput)
        timePicker = findViewById(R.id.timePicker)
        startJourneyBtn = findViewById(R.id.startJourneyBtn)
        activeTimerLayout = findViewById(R.id.activeTimerLayout)
        timerText = findViewById(R.id.timerText)
        destinationText = findViewById(R.id.destinationText)
        expectedTimeText = findViewById(R.id.expectedTimeText)
        checkInBtn = findViewById(R.id.checkInBtn)
        extendTimerBtn = findViewById(R.id.extendTimerBtn)
        cancelJourneyBtn = findViewById(R.id.cancelJourneyBtn)
        statusText = findViewById(R.id.statusText)

        // Set time picker to current time + 30 minutes by default
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 30)
        if (Build.VERSION.SDK_INT >= 23) {
            timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
            timePicker.minute = calendar.get(Calendar.MINUTE)
        }

        startJourneyBtn.setOnClickListener { startJourney() }
        checkInBtn.setOnClickListener { checkIn() }
        extendTimerBtn.setOnClickListener { extendTimer() }
        cancelJourneyBtn.setOnClickListener { cancelJourney() }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Safe Journey Timer"
    }

    private fun checkForActiveJourney() {
        isJourneyActive = sharedPreferences.getBoolean(KEY_JOURNEY_ACTIVE, false)
        if (isJourneyActive) {
            currentDestination = sharedPreferences.getString(KEY_DESTINATION, "") ?: ""
            expectedTimeMillis = sharedPreferences.getLong(KEY_EXPECTED_TIME, 0)

            destinationText.text = "📍 Destination: $currentDestination"
            val expectedTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(expectedTimeMillis))
            expectedTimeText.text = "⏰ Expected by: $expectedTimeStr"

            activeTimerLayout.visibility = android.view.View.VISIBLE
            findViewById<LinearLayout>(R.id.setupLayout).visibility = android.view.View.GONE

            startTimerDisplay()
            scheduleCheckInAlert()
        }
    }

    private fun startJourney() {
        currentDestination = destinationInput.text.toString().trim()
        if (currentDestination.isEmpty()) {
            Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show()
            return
        }

        // Get selected time
        val calendar = Calendar.getInstance()
        if (Build.VERSION.SDK_INT >= 23) {
            calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
            calendar.set(Calendar.MINUTE, timePicker.minute)
        } else {
            calendar.set(Calendar.HOUR_OF_DAY, timePicker.currentHour)
            calendar.set(Calendar.MINUTE, timePicker.currentMinute)
        }
        expectedTimeMillis = calendar.timeInMillis

        // If selected time is in the past, add a day
        if (expectedTimeMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            expectedTimeMillis = calendar.timeInMillis
        }

        // Save journey state
        sharedPreferences.edit()
            .putBoolean(KEY_JOURNEY_ACTIVE, true)
            .putString(KEY_DESTINATION, currentDestination)
            .putLong(KEY_EXPECTED_TIME, expectedTimeMillis)
            .putLong(KEY_START_TIME, System.currentTimeMillis())
            .apply()

        isJourneyActive = true

        // Update UI
        destinationText.text = "📍 Destination: $currentDestination"
        val expectedTimeStr = SimpleDateFormat("hh:mm a, MMM dd", Locale.getDefault()).format(Date(expectedTimeMillis))
        expectedTimeText.text = "⏰ Expected by: $expectedTimeStr"

        findViewById<LinearLayout>(R.id.setupLayout).visibility = android.view.View.GONE
        activeTimerLayout.visibility = android.view.View.VISIBLE

        startTimerDisplay()
        scheduleCheckInAlert()

        // Send journey started notification
        sendJourneyStartedNotification()

        Toast.makeText(this, "Journey started! Stay safe. We'll remind you before arrival time.", Toast.LENGTH_LONG).show()
    }

    private fun startTimerDisplay() {
        val startTime = sharedPreferences.getLong(KEY_START_TIME, System.currentTimeMillis())

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                val hours = elapsedSeconds / 3600
                val minutes = (elapsedSeconds % 3600) / 60
                val seconds = elapsedSeconds % 60
                timerText.text = String.format("⏱️ %02d:%02d:%02d", hours, minutes, seconds)

                // Update status text
                updateStatusMessage()
            }
            override fun onFinish() {}
        }.start()
    }

    private fun updateStatusMessage() {
        val timeRemaining = expectedTimeMillis - System.currentTimeMillis()
        when {
            timeRemaining > 30 * 60 * 1000 -> statusText.text = "🚗 On your way. Doing great!"
            timeRemaining > 10 * 60 * 1000 -> statusText.text = "⏰ Almost there! Don't forget to check in."
            timeRemaining > 5 * 60 * 1000 -> statusText.text = "⚠️ Arriving soon! Please check in when you reach."
            timeRemaining > 0 -> statusText.text = "🔔 Almost at your destination! Ready to check in?"
            else -> statusText.text = "⚠️ You've missed your check-in time!"
        }
    }

    private fun scheduleCheckInAlert() {
        val checkInTime = expectedTimeMillis - (5 * 60 * 1000) // 5 minutes before expected time

        if (checkInTime > System.currentTimeMillis()) {
            val delay = checkInTime - System.currentTimeMillis()
            Handler(Looper.getMainLooper()).postDelayed({
                showCheckInReminder()
            }, delay)
        }
    }

    private fun showCheckInReminder() {
        AlertDialog.Builder(this)
            .setTitle("🚨 Journey Check-in Reminder")
            .setMessage("You're almost at your destination: $currentDestination\n\nDon't forget to check in to let your emergency contacts know you're safe!")
            .setPositiveButton("Check In Now") { _, _ ->
                checkIn()
            }
            .setNegativeButton("Remind Later") { _, _ ->
                // Schedule another reminder in 5 minutes
                Handler(Looper.getMainLooper()).postDelayed({
                    showCheckInReminder()
                }, 5 * 60 * 1000)
            }
            .setCancelable(false)
            .show()

        sendCheckInReminderNotification()
    }

    private fun checkIn() {
        // Get current location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                }
                completeCheckIn()
            }
        } else {
            completeCheckIn()
        }
    }

    private fun completeCheckIn() {
        AlertDialog.Builder(this)
            .setTitle("✅ Safe Arrival")
            .setMessage("You have successfully checked in at your destination!\n\nYour emergency contacts will be notified that you've arrived safely.")
            .setPositiveButton("OK") { _, _ ->
                notifyEmergencyContacts()
                finishJourney()
            }
            .show()
    }

    private fun notifyEmergencyContacts() {
        authRepository.getEmergencyContacts { success, contacts ->
            if (success && contacts.isNotEmpty()) {
                val message = """
                    ✅ SAFE ARRIVAL ALERT ✅
                    
                    I have safely arrived at my destination!
                    
                    Destination: $currentDestination
                    Time: ${SimpleDateFormat("hh:mm a, dd/MM/yyyy", Locale.getDefault()).format(Date())}
                    
                    - Sent from PothBondhu Emergency App
                """.trimIndent()

                for (contact in contacts) {
                    try {
                        val smsManager = SmsManager.getDefault()
                        smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
                    } catch (e: Exception) {
                        // Handle error silently
                    }
                }
                Toast.makeText(this, "Arrival notification sent to ${contacts.size} contact(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun extendTimer() {
        AlertDialog.Builder(this)
            .setTitle("Extend Journey Timer")
            .setMessage("How much more time do you need?")
            .setPositiveButton("+15 min") { _, _ -> extendBy(15 * 60 * 1000) }
            .setNeutralButton("+30 min") { _, _ -> extendBy(30 * 60 * 1000) }
            .setNegativeButton("+1 hour") { _, _ -> extendBy(60 * 60 * 1000) }
            .show()
    }

    private fun extendBy(extraMillis: Long) {
        expectedTimeMillis += extraMillis
        sharedPreferences.edit().putLong(KEY_EXPECTED_TIME, expectedTimeMillis).apply()

        val newExpectedTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(expectedTimeMillis))
        expectedTimeText.text = "⏰ Expected by: $newExpectedTimeStr"

        Toast.makeText(this, "Timer extended! New expected time: $newExpectedTimeStr", Toast.LENGTH_LONG).show()

        // Reschedule check-in reminder
        scheduleCheckInAlert()
    }

    private fun cancelJourney() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Journey")
            .setMessage("Are you sure you want to cancel this journey? Your emergency contacts won't be notified.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                finishJourney()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun finishJourney() {
        countDownTimer?.cancel()
        isJourneyActive = false

        sharedPreferences.edit().clear().apply()

        finish()
    }

    private fun sendJourneyStartedNotification() {
        createNotificationChannel()

        val intent = Intent(this, JourneyTimerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Journey in Progress")
            .setContentText("You're traveling to: $currentDestination. Remember to check in!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun sendCheckInReminderNotification() {
        val intent = Intent(this, JourneyTimerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("⚠️ Time to Check In!")
            .setContentText("You're almost at $currentDestination. Please check in to confirm your safe arrival.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Journey Timer",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for your safe journey timer"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                101)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}