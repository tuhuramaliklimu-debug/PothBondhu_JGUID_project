package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.DecimalFormat

class NearbyActivity : AppCompatActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private val DEFAULT_CENTER = GeoPoint(23.8103, 90.4125) // Dhaka

        // If no GPS fix arrives in this time, search around the map center instead
        private const val FIRST_SEARCH_FALLBACK_MS = 4000L
    }

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
    private val currentMarkers = mutableListOf<Marker>()
    private var selectedPlace: OSMPlace? = null
    private var selectedCategory: String = "hospital"
    private var firstSearchStarted = false

    // Scope tied to this screen; cancelled in onDestroy so no work leaks
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val osmHelper by lazy { OSMHelper(this, mapView) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // MUST be before setContentView: MapView reads this config during XML inflation
        initOsmDroid()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nearby)

        // Category passed from MainActivity (e.g. the Blood Bank card)
        selectedCategory = intent.getStringExtra("selected_category") ?: "hospital"

        setupViews()
        setupMap()
        setupClickListeners()
        checkLocationPermission()

        // If GPS never gives a fix (emulator without location, permission denied),
        // still run a search so the screen is not empty.
        mapView.postDelayed({
            if (!isFinishing && !firstSearchStarted) {
                firstSearchStarted = true
                searchNearbyPlaces(selectedCategory)
            }
        }, FIRST_SEARCH_FALLBACK_MS)
    }

    private fun initOsmDroid() {
        val config = Configuration.getInstance()

        // 1. Load configuration from preferences FIRST
        config.load(
            applicationContext,
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        // 2. Set User-Agent (OSM servers block empty/default UAs)
        config.userAgentValue = packageName

        // 3. Set explicit internal cache paths to avoid permission issues
        val osmDir = File(applicationContext.cacheDir, "osmdroid")
        val tilesDir = File(osmDir, "tiles")
        if (!tilesDir.exists()) tilesDir.mkdirs()

        config.osmdroidBasePath = osmDir
        config.osmdroidTileCache = tilesDir
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
        mapView.setTilesScaledToDpi(true) // Better rendering on high-res screens
        mapView.controller.setZoom(13.0)
        mapView.controller.setCenter(DEFAULT_CENTER)
        mapView.invalidate()
    }

    private fun checkLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startLocationTracking()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                startLocationTracking()
            } else {
                Toast.makeText(
                    this,
                    "Location permission denied. Showing Dhaka. Tap a category to search.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startLocationTracking() {
        myLocationOverlay = MyLocationNewOverlay(mapView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        mapView.overlays.add(myLocationOverlay)
        mapView.invalidate()

        myLocationOverlay.runOnFirstFix {
            val fix = myLocationOverlay.lastFix ?: return@runOnFirstFix
            currentLocation = fix
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                myLocationOverlay.disableFollowLocation()
                mapView.controller.animateTo(GeoPoint(fix.latitude, fix.longitude))
                mapView.controller.setZoom(14.0)
                firstSearchStarted = true
                searchNearbyPlaces(selectedCategory)
            }
        }
    }

    private fun lastOverlayFix(): Location? =
        if (::myLocationOverlay.isInitialized) myLocationOverlay.lastFix else null

    private fun setupClickListeners() {
        mapOf(
            R.id.btnHospitals to "hospital",
            R.id.btnPolice to "police",
            R.id.btnFuel to "fuel",
            R.id.btnMechanic to "mechanic",
            R.id.btnBloodBank to "blood_bank"
        ).forEach { (id, category) ->
            findViewById<Button>(id).setOnClickListener {
                selectedCategory = category
                firstSearchStarted = true
                searchNearbyPlaces(category)
            }
        }

        findViewById<FloatingActionButton>(R.id.myLocationFab).setOnClickListener {
            val loc = currentLocation ?: lastOverlayFix()
            if (loc != null) {
                currentLocation = loc
                mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                mapView.controller.setZoom(15.0)
            } else {
                Toast.makeText(
                    this,
                    "Waiting for GPS...\nIn emulator: Extended Controls → Location → Set coordinates",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        btnDirections.setOnClickListener { selectedPlace?.let { startDirections(it) } }
        btnBookRide.setOnClickListener { selectedPlace?.let { bookRide(it) } }
        btnCall.setOnClickListener { selectedPlace?.let { showContactOptions(it) } }
    }

    private fun searchNearbyPlaces(category: String) {
        val location = currentLocation
            ?: lastOverlayFix()
            ?: Location("default").apply {
                latitude = mapView.mapCenter.latitude
                longitude = mapView.mapCenter.longitude
            }
        currentLocation = location

        progressBar.visibility = View.VISIBLE
        bottomSheet.visibility = View.GONE

        scope.launch {
            try {
                val places = osmHelper.searchNearby(location.latitude, location.longitude, category)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    clearMarkers()
                    val label = mapOf(
                        "hospital" to "Hospitals",
                        "police" to "Police Stations",
                        "fuel" to "Fuel Stations",
                        "mechanic" to "Mechanics",
                        "blood_bank" to "Blood Banks"
                    )[category] ?: category

                    if (places.isNotEmpty()) {
                        addMarkersToMap(places, category)
                        Toast.makeText(
                            this@NearbyActivity,
                            "✅ Found ${places.size} $label. Tap a pin for details.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@NearbyActivity,
                            "⚠️ No $label found within 5 km.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@NearbyActivity,
                        "❌ ${e.message ?: "Search failed"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun addMarkersToMap(places: List<OSMPlace>, category: String) {
        val emoji = mapOf(
            "hospital" to "🏥", "police" to "🚓",
            "fuel" to "⛽", "mechanic" to "🔧", "blood_bank" to "🩸"
        )
        for (place in places) {
            val marker = Marker(mapView)
            marker.position = GeoPoint(place.lat.toDouble(), place.lon.toDouble())
            marker.title = "${emoji[category] ?: "📍"} ${getShortName(place.display_name)}"
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.setOnMarkerClickListener { _, _ -> showPlaceDetails(place, category); true }
            mapView.overlays.add(marker)
            currentMarkers.add(marker)
        }
        mapView.invalidate()
    }

    private fun getShortName(name: String) = name.split(",").firstOrNull()?.trim() ?: name

    private fun showPlaceDetails(place: OSMPlace, category: String) {
        selectedPlace = place
        selectedCategory = category
        placeName.text = getShortName(place.display_name)
        placeAddress.text = place.display_name

        currentLocation?.let { loc ->
            val dest = Location("p").apply {
                latitude = place.lat.toDouble()
                longitude = place.lon.toDouble()
            }
            val d = loc.distanceTo(dest)
            placeDistance.text = if (d < 1000) "📍 ${d.toInt()} m away"
            else "📍 ${DecimalFormat("#.#").format(d / 1000)} km away"
        }

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
                placeEmergency.text = "🏥 Ambulance Available"
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
        mapView.controller.animateTo(GeoPoint(place.lat.toDouble(), place.lon.toDouble()))
        mapView.controller.setZoom(16.0)
    }

    private fun showContactOptions(place: OSMPlace) {
        if (selectedCategory == "police" || selectedCategory == "hospital") {
            val label = if (selectedCategory == "police") "Police" else "Hospital"
            android.app.AlertDialog.Builder(this)
                .setTitle("Contact $label")
                .setMessage("Emergency Number: 999")
                .setPositiveButton("Call 999") { _, _ ->
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:999")))
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            selectedPlace?.let { startDirections(it) }
        }
    }

    private fun startDirections(place: OSMPlace) {
        val lat = currentLocation?.latitude ?: mapView.mapCenter.latitude
        val lon = currentLocation?.longitude ?: mapView.mapCenter.longitude
        val uri = Uri.parse("https://www.google.com/maps/dir/$lat,$lon/${place.lat},${place.lon}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun bookRide(place: OSMPlace) {
        val name = getShortName(place.display_name)
        val uri = Uri.parse(
            "uber://?action=setPickup&pickup=my_location" +
                    "&dropoff[latitude]=${place.lat}" +
                    "&dropoff[longitude]=${place.lon}" +
                    "&dropoff[nickname]=${Uri.encode(name)}"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else Toast.makeText(this, "Please install Uber", Toast.LENGTH_SHORT).show()
    }

    private fun clearMarkers() {
        currentMarkers.forEach { mapView.overlays.remove(it) }
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

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mapView.onDetach()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}