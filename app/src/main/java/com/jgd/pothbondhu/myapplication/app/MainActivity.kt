package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var osmHelper: OSMHelper
    private lateinit var authRepository: AuthRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val LOCATION_PERMISSION_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AuthRepository
        authRepository = AuthRepository()

        // Check if user is logged in
        if (!authRepository.isUserLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Initialize OSM configuration
        Configuration.getInstance().load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        // Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize views
        mapView = findViewById(R.id.mapView)
        val sosButton = findViewById<Button>(R.id.sosButton)
        val btnHospitals = findViewById<Button>(R.id.btnHospitals)
        val btnPolice = findViewById<Button>(R.id.btnPolice)
        val btnBloodBank = findViewById<Button>(R.id.btnBloodBank)
        val btnFuel = findViewById<Button>(R.id.btnFuel)
        val btnMechanic = findViewById<Button>(R.id.btnMechanic)
        val btnRide = findViewById<Button>(R.id.btnRide)
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // Initialize OSM helper
        osmHelper = OSMHelper(this, mapView)
        osmHelper.setupMap()

        // Get real location and center map
        getRealLocationAndCenterMap()

        // SOS Button - One tap emergency alert
        sosButton.setOnClickListener {
            sendSOS()
        }

        // Emergency service buttons - Search nearby places
        btnHospitals.setOnClickListener { searchNearbyService("hospital") }
        btnPolice.setOnClickListener { searchNearbyService("police") }
        btnBloodBank.setOnClickListener { searchNearbyService("blood_bank") }
        btnFuel.setOnClickListener { searchNearbyService("fuel") }
        btnMechanic.setOnClickListener { searchNearbyService("mechanic") }

        // Ride booking button (to be implemented)
        btnRide.setOnClickListener {
            Toast.makeText(this, "🚗 Ride booking coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Bottom navigation
        setupBottomNavigation(bottomNavigation)
    }

    private fun getRealLocationAndCenterMap() {
        // Check if location permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
            return
        }

        // Get the last known location
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                // Real location found - center map there
                val realLat = location.latitude
                val realLon = location.longitude

                // Center map on real location
                mapView.controller.setCenter(GeoPoint(realLat, realLon))
                mapView.controller.setZoom(15.0)

                // Enable blue dot overlay
                osmHelper.enableMyLocation()

                Toast.makeText(this, "📍 Map centered on your location", Toast.LENGTH_SHORT).show()
                android.util.Log.d("MainActivity", "Location: $realLat, $realLon")
            } else {
                // No location found - use default and request update
                Toast.makeText(this, "⚠️ Getting your location... Please wait", Toast.LENGTH_SHORT).show()
                osmHelper.enableMyLocation()

                // Try to get updated location
                requestLocationUpdate()
            }
        }.addOnFailureListener { exception ->
            Toast.makeText(this, "❌ Could not get location: ${exception.message}", Toast.LENGTH_SHORT).show()
            osmHelper.enableMyLocation()
        }
    }

    private fun requestLocationUpdate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    mapView.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                    mapView.controller.setZoom(15.0)
                    Toast.makeText(this, "📍 Location updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.SEND_SMS
                ),
                LOCATION_PERMISSION_REQUEST)
        } else {
            getRealLocationAndCenterMap()
        }
    }

    private fun searchNearbyService(category: String) {
        // Show loading toast
        Toast.makeText(this, "🔍 Searching for $category...", Toast.LENGTH_SHORT).show()

        // Get user's current location from map
        val lastKnownLocation = osmHelper.getLastKnownLocation()

        val latitude = lastKnownLocation?.latitude ?: 24.8949  // Default to Sylhet
        val longitude = lastKnownLocation?.longitude ?: 91.8687

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val places = osmHelper.searchNearby(latitude, longitude, category)

                withContext(Dispatchers.Main) {
                    osmHelper.clearMarkers()

                    if (places.isNotEmpty()) {
                        osmHelper.addMarkersForCategory(places, android.graphics.Color.RED)

                        // Also center map on first result
                        val firstPlace = places.first()
                        val placeLat = firstPlace.lat.toDouble()
                        val placeLon = firstPlace.lon.toDouble()
                        mapView.controller.setCenter(GeoPoint(placeLat, placeLon))

                        Toast.makeText(
                            this@MainActivity,
                            "📍 Found ${places.size} ${category}(s) near you",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "⚠️ No $category found nearby",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "❌ Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun sendSOS() {
        // Get current location
        val location = osmHelper.getLastKnownLocation()
        val locationText = if (location != null) {
            "📍 Location: ${location.latitude}, ${location.longitude}\n" +
                    "🗺️ Google Maps Link: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "📍 Location: Unable to get current location"
        }

        // Get user info
        val userEmail = authRepository.getUserEmail() ?: "PothBondhu User"

        // Create SOS message
        val sosMessage = """
            🚨 SOS EMERGENCY ALERT 🚨
            
            User: $userEmail
            Time: ${SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date())}
            
            $locationText
            
            Please help immediately!
            - Sent from PothBondhu Emergency App
        """.trimIndent()

        // Show confirmation
        Toast.makeText(this, "🚨 SOS SENT! Your location has been shared with emergency contacts.", Toast.LENGTH_LONG).show()

        // For debugging
        android.util.Log.d("MainActivity", "SOS Sent: $sosMessage")
    }

    private fun setupBottomNavigation(bottomNavigation: BottomNavigationView) {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Already on home
                    true
                }
                R.id.nav_contacts -> {
                    Toast.makeText(this, "📞 Manage emergency contacts coming soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_journey -> {
                    Toast.makeText(this, "🛡️ Safe journey timer coming soon!", Toast.LENGTH_SHORT).show()
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
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getRealLocationAndCenterMap()
                    Toast.makeText(this, "✅ Location permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "⚠️ Location permission denied. Some features may not work.", Toast.LENGTH_LONG).show()
                    // Use default location
                    osmHelper.enableMyLocation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}