package com.jgd.pothbondhu.myapplication.app

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.DecimalFormat

class NearbyActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var progressBar: ProgressBar
    private lateinit var bottomSheet: CardView
    private lateinit var placeName: TextView
    private lateinit var placeAddress: TextView
    private lateinit var placeDistance: TextView
    private lateinit var placePhone: TextView
    private lateinit var placeEmergency: TextView
    private lateinit var btnDirections: Button
    private lateinit var btnBookRide: Button
    private lateinit var btnCall: Button

    private var currentLocation: Location? = null
    private var currentMarkers = mutableListOf<Marker>()
    private var selectedPlace: OSMPlace? = null
    private var selectedCategory: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSM configuration
        Configuration.getInstance().load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().setUserAgentValue("PothBondhu/1.0")

        setContentView(R.layout.activity_nearby)

        setupViews()
        setupMap()
        setupClickListeners()
    }

    private fun setupViews() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Find Help Nearby"

        mapView = findViewById(R.id.mapView)
        progressBar = findViewById(R.id.progressBar)
        bottomSheet = findViewById(R.id.bottomSheet)
        placeName = findViewById(R.id.placeName)
        placeAddress = findViewById(R.id.placeAddress)
        placeDistance = findViewById(R.id.placeDistance)
        placePhone = findViewById(R.id.placePhone)
        placeEmergency = findViewById(R.id.placeEmergency)
        btnDirections = findViewById(R.id.btnDirections)
        btnBookRide = findViewById(R.id.btnBookRide)
        btnCall = findViewById(R.id.btnCall)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(true)
        mapView.controller.setZoom(15.0)

        // Center on Dhaka as default
        mapView.controller.setCenter(GeoPoint(23.8103, 90.4125))

        // My location overlay
        myLocationOverlay = MyLocationNewOverlay(mapView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        mapView.overlays.add(myLocationOverlay)

        // Get location when available
        myLocationOverlay.runOnFirstFix {
            currentLocation = myLocationOverlay.lastFix
            runOnUiThread {
                currentLocation?.let {
                    mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                    // Show location info
                    Toast.makeText(
                        this@NearbyActivity,
                        "📍 Location found: ${it.latitude}, ${it.longitude}",
                        Toast.LENGTH_LONG
                    ).show()
                    // Auto-search for hospitals by default
                    searchNearbyPlaces("hospital")
                }
            }
        }

        mapView.invalidate()
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btnHospitals).setOnClickListener {
            selectedCategory = "hospital"
            searchNearbyPlaces("hospital")
        }
        findViewById<Button>(R.id.btnPolice).setOnClickListener {
            selectedCategory = "police"
            searchNearbyPlaces("police")
        }
        findViewById<Button>(R.id.btnFuel).setOnClickListener {
            selectedCategory = "fuel"
            searchNearbyPlaces("fuel")
        }
        findViewById<Button>(R.id.btnMechanic).setOnClickListener {
            selectedCategory = "mechanic"
            searchNearbyPlaces("mechanic")
        }
        findViewById<Button>(R.id.btnBloodBank).setOnClickListener {
            selectedCategory = "blood_bank"
            searchNearbyPlaces("blood_bank")
        }

        findViewById<FloatingActionButton>(R.id.myLocationFab).setOnClickListener {
            currentLocation?.let {
                mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                mapView.controller.setZoom(15.0)
                Toast.makeText(this, "📍 Centered on your location", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "Waiting for location... Set mock location in emulator", Toast.LENGTH_LONG).show()
        }

        btnDirections.setOnClickListener {
            selectedPlace?.let { startDirections(it) }
        }

        btnBookRide.setOnClickListener {
            selectedPlace?.let { bookRide(it) }
        }

        btnCall.setOnClickListener {
            selectedPlace?.let {
                showContactOptions(it)
            }
        }
    }

    private fun searchNearbyPlaces(category: String) {
        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, "📍 No location found! Please set mock location in emulator:\n\n1. Click ⋯ on emulator\n2. Go to Location tab\n3. Set coordinates (e.g., 23.8103, 90.4125 for Dhaka)\n4. Click 'Set Location'", Toast.LENGTH_LONG).show()
            return
        }

        // Show what location we're searching from
        Toast.makeText(
            this,
            "🔍 Searching for $category near:\nLat: ${location.latitude}\nLon: ${location.longitude}",
            Toast.LENGTH_LONG
        ).show()

        progressBar.visibility = View.VISIBLE
        bottomSheet.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val osmHelper = OSMHelper(this@NearbyActivity, mapView)
                val places = osmHelper.searchNearby(location.latitude, location.longitude, category)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    clearMarkers()

                    val categoryNames = mapOf(
                        "hospital" to "Hospitals",
                        "police" to "Police Stations",
                        "fuel" to "Fuel Stations",
                        "mechanic" to "Mechanics",
                        "blood_bank" to "Blood Banks"
                    )

                    if (places.isNotEmpty()) {
                        addMarkersToMap(places, category)
                        Toast.makeText(
                            this@NearbyActivity,
                            "✅ Found ${places.size} ${categoryNames[category]} near you! Tap on markers to see details.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@NearbyActivity,
                            "⚠️ No ${categoryNames[category]} found nearby.\n\nTry:\n• Zooming out the map\n• Using a different location\n• Checking internet connection",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@NearbyActivity,
                        "❌ Error: ${e.message}\n\nCheck your internet connection.",
                        Toast.LENGTH_LONG
                    ).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun addMarkersToMap(places: List<OSMPlace>, category: String) {
        for (place in places) {
            val marker = Marker(mapView)
            marker.position = GeoPoint(place.lat.toDouble(), place.lon.toDouble())

            // Set custom title based on category
            marker.title = when (category) {
                "police" -> "🚓 ${getShortName(place.display_name)}"
                "hospital" -> "🏥 ${getShortName(place.display_name)}"
                "fuel" -> "⛽ ${getShortName(place.display_name)}"
                "blood_bank" -> "🩸 ${getShortName(place.display_name)}"
                else -> getShortName(place.display_name)
            }

            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.setOnMarkerClickListener { _, _ ->
                showPlaceDetails(place, category)
                true
            }
            mapView.overlays.add(marker)
            currentMarkers.add(marker)
        }
        mapView.invalidate()
    }

    private fun getShortName(fullName: String): String {
        return fullName.split(",").firstOrNull()?.trim() ?: fullName
    }

    private fun showPlaceDetails(place: OSMPlace, category: String) {
        selectedPlace = place
        selectedCategory = category

        // Set place name
        placeName.text = getShortName(place.display_name)
        placeAddress.text = place.display_name

        // Calculate and show distance
        currentLocation?.let { loc ->
            val placeLoc = Location("place").apply {
                latitude = place.lat.toDouble()
                longitude = place.lon.toDouble()
            }
            val distance = loc.distanceTo(placeLoc)
            val formattedDistance = if (distance < 1000) {
                "${distance.toInt()} meters away"
            } else {
                "${DecimalFormat("#.#").format(distance / 1000)} km away"
            }
            placeDistance.text = "📍 $formattedDistance"
        }

        // Set category-specific information
        when (category) {
            "police" -> {
                placePhone.text = "📞 Emergency: 999"
                placeEmergency.text = "🚓 24/7 Police Service"
                btnCall.text = "📞 Call Police"
                placePhone.visibility = View.VISIBLE
                placeEmergency.visibility = View.VISIBLE
                btnCall.visibility = View.VISIBLE
            }
            "hospital" -> {
                placePhone.text = "📞 Emergency: 999"
                placeEmergency.text = "🏥 Ambulance Service Available"
                btnCall.text = "📞 Call Hospital"
                placePhone.visibility = View.VISIBLE
                placeEmergency.visibility = View.VISIBLE
                btnCall.visibility = View.VISIBLE
            }
            "fuel" -> {
                placePhone.text = "⛽ Fuel Available"
                placeEmergency.text = "🕒 24/7 Service"
                btnCall.text = "📍 Navigate"
                placePhone.visibility = View.VISIBLE
                placeEmergency.visibility = View.VISIBLE
                btnCall.visibility = View.VISIBLE
            }
            "blood_bank" -> {
                placePhone.text = "🩸 Blood Bank"
                placeEmergency.text = "🏥 Emergency Blood Available"
                btnCall.text = "📞 Call Blood Bank"
                placePhone.visibility = View.VISIBLE
                placeEmergency.visibility = View.VISIBLE
                btnCall.visibility = View.VISIBLE
            }
            else -> {
                placePhone.visibility = View.GONE
                placeEmergency.visibility = View.GONE
                btnCall.visibility = View.GONE
            }
        }

        bottomSheet.visibility = View.VISIBLE
        mapView.controller.setCenter(GeoPoint(place.lat.toDouble(), place.lon.toDouble()))
        mapView.controller.setZoom(16.0)
    }

    private fun showContactOptions(place: OSMPlace) {
        when (selectedCategory) {
            "police" -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Contact Police")
                    .setMessage("Emergency Number: 999\n\nWhat would you like to do?")
                    .setPositiveButton("Call 999") { _, _ ->
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:999"))
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            "hospital" -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Contact Hospital")
                    .setMessage("Emergency Number: 999\n\nWould you like to call emergency services?")
                    .setPositiveButton("Call 999") { _, _ ->
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:999"))
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                Toast.makeText(this, "Navigate to this location for more info", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDirections(place: OSMPlace) {
        val startLat = currentLocation?.latitude ?: return
        val startLon = currentLocation?.longitude ?: return
        val destLat = place.lat.toDouble()
        val destLon = place.lon.toDouble()

        val uri = Uri.parse("https://www.google.com/maps/dir/$startLat,$startLon/$destLat,$destLon")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun bookRide(place: OSMPlace) {
        val destLat = place.lat.toDouble()
        val destLon = place.lon.toDouble()
        val destName = getShortName(place.display_name)

        val uberUri = Uri.parse("uber://?action=setPickup&pickup=my_location&dropoff[latitude]=$destLat&dropoff[longitude]=$destLon&dropoff[nickname]=${Uri.encode(destName)}")
        val uberIntent = Intent(Intent.ACTION_VIEW, uberUri)

        if (uberIntent.resolveActivity(packageManager) != null) {
            startActivity(uberIntent)
        } else {
            Toast.makeText(this, "Please install Uber", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearMarkers() {
        for (marker in currentMarkers) {
            mapView.overlays.remove(marker)
        }
        currentMarkers.clear()
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}