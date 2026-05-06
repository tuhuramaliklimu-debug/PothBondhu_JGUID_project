package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class RideBookingActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var resultsContainer: LinearLayout
    private lateinit var currentLocationTextView: TextView

    private var currentLatitude: Double = 23.8103
    private var currentLongitude: Double = 90.4125
    private val LOCATION_PERMISSION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ride_booking)

        setupViews()
        setupLocation()
        setupClickListeners()
    }

    private fun setupViews() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Book Emergency Ride"

        searchInput = findViewById(R.id.searchInput)
        searchButton = findViewById(R.id.searchButton)
        progressBar = findViewById(R.id.progressBar)
        resultsContainer = findViewById(R.id.resultsContainer)
        currentLocationTextView = findViewById(R.id.currentLocationText)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun setupLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST)
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                currentLocationTextView.text = "📍 Current Location: ${String.format("%.4f", currentLatitude)}, ${String.format("%.4f", currentLongitude)}"
            }
        }
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val destination = searchInput.text.toString().trim()
            if (destination.isNotEmpty()) {
                searchNearbyHospitalsAndPolice(destination)
            } else {
                Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchNearbyHospitalsAndPolice(destination: String) {
        progressBar.visibility = android.view.View.VISIBLE
        resultsContainer.removeAllViews()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Search for hospitals
                val hospitals = searchOsmPlaces(destination, "hospital")
                // Search for police stations
                val policeStations = searchOsmPlaces(destination, "police")

                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE

                    if (hospitals.isEmpty() && policeStations.isEmpty()) {
                        Toast.makeText(this@RideBookingActivity, "No emergency services found near $destination", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    // Add Hospitals section
                    if (hospitals.isNotEmpty()) {
                        addSectionHeader("🏥 Hospitals")
                        for (place in hospitals) {
                            addPlaceCard(place, "hospital")
                        }
                    }

                    // Add Police Stations section
                    if (policeStations.isNotEmpty()) {
                        addSectionHeader("🚓 Police Stations")
                        for (place in policeStations) {
                            addPlaceCard(place, "police")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@RideBookingActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun searchOsmPlaces(destination: String, category: String): List<OSMPlace> {
        val overpassQuery = buildOverpassQueryForDestination(currentLatitude, currentLongitude, category)
        val url = "https://overpass-api.de/api/interpreter"

        return try {
            val responseBody = postOverpassQuery(url, overpassQuery)
            parseOverpassResponse(responseBody, category)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildOverpassQueryForDestination(lat: Double, lon: Double, category: String): String {
        val tagFilter = when (category) {
            "hospital" -> """
                node["amenity"="hospital"](around:10000,$lat,$lon);
                node["amenity"="clinic"](around:10000,$lat,$lon);
                way["amenity"="hospital"](around:10000,$lat,$lon);
            """.trimIndent()
            "police" -> """
                node["amenity"="police"](around:10000,$lat,$lon);
                way["amenity"="police"](around:10000,$lat,$lon);
            """.trimIndent()
            else -> ""
        }

        return """
            [out:json][timeout:25];
            (
            $tagFilter
            );
            out center 10;
        """.trimIndent()
    }

    private fun postOverpassQuery(urlString: String, query: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpsURLConnection

        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "PothBondhu/1.0 (Emergency App Bangladesh)")
            connectTimeout = 15000
            readTimeout = 25000
            doOutput = true
        }

        val postData = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        connection.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseOverpassResponse(responseBody: String, category: String): List<OSMPlace> {
        val results = mutableListOf<OSMPlace>()
        val json = JSONObject(responseBody)
        val elements = json.optJSONArray("elements") ?: return emptyList()

        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            val type = element.getString("type")

            val lat: Double
            val lon: Double

            when (type) {
                "node" -> {
                    lat = element.getDouble("lat")
                    lon = element.getDouble("lon")
                }
                "way" -> {
                    val center = element.optJSONObject("center") ?: continue
                    lat = center.getDouble("lat")
                    lon = center.getDouble("lon")
                }
                else -> continue
            }

            val id = element.getLong("id")
            val tags = element.optJSONObject("tags")
            val name = tags?.optString("name") ?: "${category.replaceFirstChar { it.uppercase() }} at this location"

            results.add(
                OSMPlace(
                    place_id = id,
                    lat = lat.toString(),
                    lon = lon.toString(),
                    display_name = name,
                    type = category
                )
            )
        }

        return results.take(5)
    }

    private fun addSectionHeader(title: String) {
        val header = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(resources.getColor(R.color.navy_dark))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(16, 24, 16, 8)
        }
        resultsContainer.addView(header)
    }

    private fun addPlaceCard(place: OSMPlace, category: String) {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 12f
            cardElevation = 4f
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Get clean name without duplication
        val cleanName = getCleanPlaceName(place)

        val nameText = TextView(this).apply {
            text = when (category) {
                "hospital" -> "🏥 $cleanName"
                "police" -> "🚓 $cleanName"
                else -> cleanName
            }
            textSize = 16f
            setTextColor(resources.getColor(R.color.navy_dark))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val addressText = TextView(this).apply {
            text = "📍 ${place.display_name}"
            textSize = 12f
            setTextColor(resources.getColor(R.color.navy_light))
            setPadding(0, 4, 0, 0)
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val rideButton = Button(this).apply {
            text = "Book Ride"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 0, 8, 0)
            }
            setBackgroundColor(resources.getColor(R.color.navy_primary))
            setTextColor(resources.getColor(R.color.beige_light))
            setOnClickListener {
                showRideOptionsDialog(place)
            }
        }

        val directionsButton = Button(this).apply {
            text = "Directions"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setBackgroundColor(resources.getColor(R.color.navy_primary))
            setTextColor(resources.getColor(R.color.beige_light))
            setOnClickListener {
                openDirections(place)
            }
        }

        buttonLayout.addView(rideButton)
        buttonLayout.addView(directionsButton)
        layout.addView(nameText)
        layout.addView(addressText)
        layout.addView(buttonLayout)
        card.addView(layout)
        resultsContainer.addView(card)
    }

    private fun getCleanPlaceName(place: OSMPlace): String {
        val fullName = place.display_name
        // Try to get a clean name from the display_name
        val name = fullName.split(",").firstOrNull()?.trim()
        return if (name.isNullOrEmpty()) "Destination" else name
    }

    // ========== UPDATED: RIDE OPTIONS DIALOG WITH TOAST CONFIRMATION ==========
    private fun showRideOptionsDialog(place: OSMPlace) {
        val destLat = place.lat.toDouble()
        val destLon = place.lon.toDouble()
        val destName = getCleanPlaceName(place)

        val options = arrayOf("🚗 Uber", "🛺 Pathao", "Cancel")

        AlertDialog.Builder(this)
            .setTitle("Choose Ride Service")
            .setMessage("Book a ride to:\n$destName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        Toast.makeText(this, "Opening Uber...", Toast.LENGTH_SHORT).show()
                        bookUber(destLat, destLon, destName)
                    }
                    1 -> {
                        Toast.makeText(this, "Opening Pathao...", Toast.LENGTH_SHORT).show()
                        bookPathao(destLat, destLon, destName)
                    }
                    // 2 is Cancel - do nothing
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== UPDATED: UBER WITH BETTER ERROR HANDLING ==========
    private fun bookUber(lat: Double, lon: Double, name: String) {
        try {
            val uberUri = Uri.parse("uber://?action=setPickup&pickup=my_location&dropoff[latitude]=$lat&dropoff[longitude]=$lon&dropoff[nickname]=${Uri.encode(name)}")
            val intent = Intent(Intent.ACTION_VIEW, uberUri)

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Toast.makeText(this, "Opening Uber app...", Toast.LENGTH_SHORT).show()
            } else {
                // Fallback to Uber Mobile Web
                val webUri = Uri.parse("https://m.uber.com/ul/?action=setPickup&pickup=my_location&dropoff[latitude]=$lat&dropoff[longitude]=$lon")
                startActivity(Intent(Intent.ACTION_VIEW, webUri))
                Toast.makeText(this, "Uber app not installed. Opening in browser.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening Uber: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== UPDATED: PATHAO WITH BETTER ERROR HANDLING ==========
    private fun bookPathao(lat: Double, lon: Double, name: String) {
        try {
            val pathaoUri = Uri.parse("pathao://ride?destination_lat=$lat&destination_lng=$lon&destination_name=${Uri.encode(name)}")
            val intent = Intent(Intent.ACTION_VIEW, pathaoUri)

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Toast.makeText(this, "Opening Pathao app...", Toast.LENGTH_SHORT).show()
            } else {
                // Show install prompt
                AlertDialog.Builder(this)
                    .setTitle("Pathao Not Installed")
                    .setMessage("Pathao app is not installed on your device. Would you like to install it?")
                    .setPositiveButton("Install") { _, _ ->
                        val playStoreUri = Uri.parse("market://details?id=com.pathao.user")
                        val playStoreIntent = Intent(Intent.ACTION_VIEW, playStoreUri)
                        startActivity(playStoreIntent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening Pathao: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDirections(place: OSMPlace) {
        val destLat = place.lat.toDouble()
        val destLon = place.lon.toDouble()
        val uri = Uri.parse("https://www.google.com/maps/dir/$currentLatitude,$currentLongitude/$destLat,$destLon")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
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
                    getCurrentLocation()
                } else {
                    Toast.makeText(this, "Location permission needed for accurate ride booking", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}