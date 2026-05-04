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
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.DecimalFormat

class NearbyActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var osmHelper: OSMHelper
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var progressBar: ProgressBar
    private lateinit var bottomSheet: View
    private lateinit var placeName: TextView
    private lateinit var placeAddress: TextView
    private lateinit var placeDistance: TextView
    private lateinit var btnDirections: Button
    private lateinit var btnBookRide: Button

    private var currentLocation: Location? = null
    private var currentMarkers = mutableListOf<Marker>()
    private var selectedPlace: OSMPlace? = null
    private var currentRoute: Polyline? = null

    private val categories = mapOf(
        "hospital" to "🏥 Hospitals",
        "police" to "🚓 Police Stations",
        "fuel" to "⛽ Fuel Stations",
        "mechanic" to "🔧 Mechanics",
        "blood_bank" to "🩸 Blood Banks"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nearby)

        // Initialize OSM
        Configuration.getInstance().load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        setupViews()
        setupMap()
        setupClickListeners()
        getCurrentLocationAndSearch()
    }

    private fun setupViews() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mapView = findViewById(R.id.mapView)
        progressBar = findViewById(R.id.progressBar)
        bottomSheet = findViewById(R.id.bottomSheet)
        placeName = findViewById(R.id.placeName)
        placeAddress = findViewById(R.id.placeAddress)
        placeDistance = findViewById(R.id.placeDistance)
        btnDirections = findViewById(R.id.btnDirections)
        btnBookRide = findViewById(R.id.btnBookRide)
    }

    private fun setupMap() {
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(true)
        mapView.controller.setZoom(14.0)

        // Setup my location overlay
        myLocationOverlay = MyLocationNewOverlay(mapView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        mapView.overlays.add(myLocationOverlay)

        // Get current location
        currentLocation = myLocationOverlay.lastFix
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btnHospitals).setOnClickListener {
            searchNearbyPlaces("hospital")
        }
        findViewById<Button>(R.id.btnPolice).setOnClickListener {
            searchNearbyPlaces("police")
        }
        findViewById<Button>(R.id.btnFuel).setOnClickListener {
            searchNearbyPlaces("fuel")
        }
        findViewById<Button>(R.id.btnMechanic).setOnClickListener {
            searchNearbyPlaces("mechanic")
        }
        findViewById<Button>(R.id.btnBloodBank).setOnClickListener {
            searchNearbyPlaces("blood_bank")
        }

        findViewById<FloatingActionButton>(R.id.myLocationFab).setOnClickListener {
            centerOnMyLocation()
        }

        btnDirections.setOnClickListener {
            selectedPlace?.let { place ->
                showDirections(place)
            }
        }

        btnBookRide.setOnClickListener {
            selectedPlace?.let { place ->
                bookRide(place)
            }
        }
    }

    private fun getCurrentLocationAndSearch() {
        if (currentLocation != null) {
            centerOnMyLocation()
            searchNearbyPlaces("hospital") // Default search
        } else {
            Toast.makeText(this, "Waiting for location...", Toast.LENGTH_SHORT).show()
            // Try again after a delay
            mapView.postDelayed({
                currentLocation = myLocationOverlay.lastFix
                if (currentLocation != null) {
                    centerOnMyLocation()
                    searchNearbyPlaces("hospital")
                } else {
                    Toast.makeText(this, "Could not get location. Please try again.", Toast.LENGTH_LONG).show()
                }
            }, 3000)
        }
    }

    private fun centerOnMyLocation() {
        currentLocation = myLocationOverlay.lastFix
        currentLocation?.let {
            mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
            mapView.controller.setZoom(15.0)
        }
    }

    private fun searchNearbyPlaces(category: String) {
        val location = currentLocation ?: run {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        bottomSheet.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val osmHelper = OSMHelper(this@NearbyActivity, mapView)
                val places = osmHelper.searchNearby(location.latitude, location.longitude, category)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    clearMarkers()

                    if (places.isNotEmpty()) {
                        addMarkersToMap(places)
                        Toast.makeText(
                            this@NearbyActivity,
                            "Found ${places.size} ${categories[category]}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@NearbyActivity,
                            "No ${categories[category]} found nearby",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@NearbyActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addMarkersToMap(places: List<OSMPlace>) {
        for (place in places) {
            val marker = Marker(mapView)
            marker.position = GeoPoint(place.lat.toDouble(), place.lon.toDouble())
            marker.title = place.display_name
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.setOnMarkerClickListener { _, _ ->
                showPlaceDetails(place)
                true
            }
            mapView.overlays.add(marker)
            currentMarkers.add(marker)
        }
        mapView.invalidate()
    }

    private fun showPlaceDetails(place: OSMPlace) {
        selectedPlace = place
        placeName.text = place.display_name.split(",").firstOrNull() ?: place.display_name
        placeAddress.text = place.display_name

        // Calculate distance if we have current location
        currentLocation?.let { loc ->
            val placeLoc = android.location.Location("place").apply {
                latitude = place.lat.toDouble()
                longitude = place.lon.toDouble()
            }
            val distance = loc.distanceTo(placeLoc)
            val formattedDistance = if (distance < 1000) {
                "${distance.toInt()} m"
            } else {
                "${DecimalFormat("#.#").format(distance / 1000)} km"
            }
            placeDistance.text = "📍 $formattedDistance away"
        }

        bottomSheet.visibility = View.VISIBLE

        // Center map on selected place
        mapView.controller.setCenter(GeoPoint(place.lat.toDouble(), place.lon.toDouble()))
        mapView.controller.setZoom(16.0)
    }

    private fun showDirections(place: OSMPlace) {
        val startLat = currentLocation?.latitude ?: run {
            Toast.makeText(this, "Current location not available", Toast.LENGTH_SHORT).show()
            return
        }
        val startLon = currentLocation?.longitude ?: return
        val destLat = place.lat.toDouble()
        val destLon = place.lon.toDouble()

        // Open Google Maps for directions
        val uri = Uri.parse("https://www.google.com/maps/dir/$startLat,$startLon/$destLat,$destLon")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    private fun bookRide(place: OSMPlace) {
        val destLat = place.lat.toDouble()
        val destLon = place.lon.toDouble()
        val destName = place.display_name.split(",").firstOrNull() ?: "Destination"

        // Try to open Uber first, then Pathao
        val uberUri = Uri.parse("uber://?action=setPickup&pickup=my_location&dropoff[latitude]=$destLat&dropoff[longitude]=$destLon&dropoff[nickname]=${Uri.encode(destName)}")
        val uberIntent = Intent(Intent.ACTION_VIEW, uberUri)

        val pathaoUri = Uri.parse("pathao://ride?drop_lat=$destLat&drop_lng=$destLon&drop_name=${Uri.encode(destName)}")
        val pathaoIntent = Intent(Intent.ACTION_VIEW, pathaoUri)

        if (uberIntent.resolveActivity(packageManager) != null) {
            startActivity(uberIntent)
        } else if (pathaoIntent.resolveActivity(packageManager) != null) {
            startActivity(pathaoIntent)
        } else {
            Toast.makeText(this, "Please install Uber or Pathao to book a ride", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearMarkers() {
        for (marker in currentMarkers) {
            mapView.overlays.remove(marker)
        }
        currentMarkers.clear()
        currentRoute?.let {
            mapView.overlays.remove(it)
            currentRoute = null
        }
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