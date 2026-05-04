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
    private lateinit var bottomSheet: View
    private lateinit var placeName: TextView
    private lateinit var placeAddress: TextView
    private lateinit var placeDistance: TextView
    private lateinit var btnDirections: Button
    private lateinit var btnBookRide: Button

    private var currentLocation: Location? = null
    private var currentMarkers = mutableListOf<Marker>()
    private var selectedPlace: OSMPlace? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSM configuration - THIS IS CRITICAL
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
        // Set tile source - THIS MAKES MAP VISIBLE
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
                    searchNearbyPlaces("hospital")
                }
            }
        }

        // Force map refresh
        mapView.invalidate()
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btnHospitals).setOnClickListener { searchNearbyPlaces("hospital") }
        findViewById<Button>(R.id.btnPolice).setOnClickListener { searchNearbyPlaces("police") }
        findViewById<Button>(R.id.btnFuel).setOnClickListener { searchNearbyPlaces("fuel") }
        findViewById<Button>(R.id.btnMechanic).setOnClickListener { searchNearbyPlaces("mechanic") }
        findViewById<Button>(R.id.btnBloodBank).setOnClickListener { searchNearbyPlaces("blood_bank") }

        findViewById<FloatingActionButton>(R.id.myLocationFab).setOnClickListener {
            currentLocation?.let {
                mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                mapView.controller.setZoom(15.0)
            } ?: Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show()
        }

        btnDirections.setOnClickListener {
            selectedPlace?.let { startDirections(it) }
        }

        btnBookRide.setOnClickListener {
            selectedPlace?.let { bookRide(it) }
        }
    }

    private fun searchNearbyPlaces(category: String) {
        val location = currentLocation
        if (location == null) {
            Toast.makeText(this, "Waiting for location. Try again in a moment.", Toast.LENGTH_SHORT).show()
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

                    val categoryNames = mapOf(
                        "hospital" to "Hospitals",
                        "police" to "Police Stations",
                        "fuel" to "Fuel Stations",
                        "mechanic" to "Mechanics",
                        "blood_bank" to "Blood Banks"
                    )

                    if (places.isNotEmpty()) {
                        addMarkersToMap(places)
                        Toast.makeText(
                            this@NearbyActivity,
                            "Found ${places.size} ${categoryNames[category]}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@NearbyActivity,
                            "No ${categoryNames[category]} found nearby",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@NearbyActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
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
        mapView.controller.setCenter(GeoPoint(place.lat.toDouble(), place.lon.toDouble()))
        mapView.controller.setZoom(16.0)
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
        val destName = place.display_name.split(",").firstOrNull() ?: "Destination"

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