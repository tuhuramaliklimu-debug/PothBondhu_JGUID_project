package com.jgd.pothbondhu.myapplication.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.DecimalFormat
import javax.net.ssl.HttpsURLConnection

class HotelFinderActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var sortSpinner: Spinner
    private lateinit var filterButton: Button
    private lateinit var noResultsText: TextView

    private var currentLatitude: Double = 23.8103
    private var currentLongitude: Double = 90.4125
    private var hotelsList = mutableListOf<Hotel>()
    private lateinit var hotelAdapter: HotelAdapter

    private val LOCATION_PERMISSION_REQUEST = 100
    private val SEARCH_RADIUS_METERS = 5000

    companion object {
        private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotel_finder)

        setupViews()
        setupToolbar()
        requestLocationPermission()
        setupSortSpinner()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.hotelRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        sortSpinner = findViewById(R.id.sortSpinner)
        filterButton = findViewById(R.id.filterButton)
        noResultsText = findViewById(R.id.noResultsText)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        hotelAdapter = HotelAdapter { hotel ->
            showHotelDetails(hotel)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = hotelAdapter
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Find Hotels Nearby"
    }

    private fun setupSortSpinner() {
        val sortOptions = arrayOf("⭐ Sort by Rating (Highest First)", "💰 Sort by Price (Lowest First)", "📏 Sort by Distance (Nearest First)")
        val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner.adapter = arrayAdapter

        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (hotelsList.isNotEmpty()) {
                    sortHotels(position)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        filterButton.setOnClickListener {
            searchHotels()
        }
    }

    private fun requestLocationPermission() {
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
            searchHotels()
            return
        }

        progressBar.visibility = View.VISIBLE

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLatitude = location.latitude
                currentLongitude = location.longitude
                searchHotels()
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Could not get location. Using default location.", Toast.LENGTH_SHORT).show()
                searchHotels()
            }
        }.addOnFailureListener {
            progressBar.visibility = View.GONE
            searchHotels()
        }
    }

    private fun searchHotels() {
        progressBar.visibility = View.VISIBLE
        noResultsText.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hotels = searchNearbyHotels()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    hotelsList.clear()
                    hotelsList.addAll(hotels)

                    if (hotelsList.isNotEmpty()) {
                        sortHotels(sortSpinner.selectedItemPosition)
                        hotelAdapter.updateHotels(hotelsList)
                        recyclerView.visibility = View.VISIBLE
                        noResultsText.visibility = View.GONE
                    } else {
                        recyclerView.visibility = View.GONE
                        noResultsText.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@HotelFinderActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun searchNearbyHotels(): List<Hotel> {
        val overpassQuery = buildHotelQuery()
        val response = postOverpassQuery(OVERPASS_URL, overpassQuery)
        return parseOverpassResponse(response)
    }

    private fun buildHotelQuery(): String {
        return """
            [out:json][timeout:25];
            (
              node["tourism"="hotel"](around:$SEARCH_RADIUS_METERS,$currentLatitude,$currentLongitude);
              node["tourism"="guest_house"](around:$SEARCH_RADIUS_METERS,$currentLatitude,$currentLongitude);
              way["tourism"="hotel"](around:$SEARCH_RADIUS_METERS,$currentLatitude,$currentLongitude);
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()
    }

    private fun postOverpassQuery(urlString: String, query: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpsURLConnection

        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "PothBondhu/1.0 (Hotel Finder)")
            connectTimeout = 15000
            readTimeout = 25000
            doOutput = true
        }

        val postData = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        connection.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }

        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseOverpassResponse(responseBody: String): List<Hotel> {
        val hotels = mutableListOf<Hotel>()
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
                    val center = element.optJSONObject("center")
                    if (center == null) continue
                    lat = center.getDouble("lat")
                    lon = center.getDouble("lon")
                }
                else -> continue
            }

            val tags = element.optJSONObject("tags")
            val name = tags?.optString("name") ?: continue

            // Calculate distance
            val distance = calculateDistance(currentLatitude, currentLongitude, lat, lon)

            // Extract rating from OSM tags (if available)
            var rating = 0.0
            tags?.let {
                rating = it.optString("rating").toDoubleOrNull() ?: 0.0
                if (rating == 0.0) {
                    rating = it.optString("stars").toDoubleOrNull() ?: 0.0
                }
            }

            // Extract price info
            var pricePerNight = 0.0
            tags?.let {
                val priceStr = it.optString("price")
                if (priceStr.isNotEmpty()) {
                    pricePerNight = priceStr.replace("[^\\d.]".toRegex(), "").toDoubleOrNull() ?: 0.0
                }
            }

            // Extract contact info
            val phone = tags?.optString("phone") ?: ""

            // Extract amenities
            val amenities = mutableListOf<String>()
            tags?.let {
                if (it.has("wifi") && it.getString("wifi") != "no") amenities.add("WiFi")
                if (it.has("air_conditioning") && it.getString("air_conditioning") != "no") amenities.add("AC")
                if (it.has("breakfast") && it.getString("breakfast") != "no") amenities.add("Breakfast")
                if (it.has("parking") && it.getString("parking") != "no") amenities.add("Parking")
                if (it.has("restaurant")) amenities.add("Restaurant")
            }

            // Generate random review count for demo (in production, fetch from API)
            val reviewCount = (50..500).random()

            hotels.add(
                Hotel(
                    id = element.getLong("id").toString(),
                    name = name,
                    address = tags?.optString("addr:street") ?: "Near your location",
                    latitude = lat,
                    longitude = lon,
                    rating = rating,
                    pricePerNight = pricePerNight,
                    phone = phone,
                    amenities = amenities,
                    distance = distance,
                    reviewCount = reviewCount
                )
            )
        }

        return hotels
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // Earth's radius in meters
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δφ = Math.toRadians(lat2 - lat1)
        val Δλ = Math.toRadians(lon2 - lon1)

        val a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) *
                Math.sin(Δλ / 2) * Math.sin(Δλ / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c
    }

    private fun sortHotels(position: Int) {
        when (position) {
            0 -> hotelsList.sortByDescending { it.rating }  // Rating high to low
            1 -> hotelsList.sortBy { it.pricePerNight }    // Price low to high
            2 -> hotelsList.sortBy { it.distance }         // Distance nearest first
        }
        hotelAdapter.updateHotels(hotelsList)
    }

    private fun showHotelDetails(hotel: Hotel) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_hotel_details, null)

        val nameText = dialogView.findViewById<TextView>(R.id.hotelName)
        val ratingText = dialogView.findViewById<TextView>(R.id.hotelRating)
        val distanceText = dialogView.findViewById<TextView>(R.id.hotelDistance)
        val priceText = dialogView.findViewById<TextView>(R.id.hotelPrice)
        val addressText = dialogView.findViewById<TextView>(R.id.hotelAddress)
        val phoneText = dialogView.findViewById<TextView>(R.id.hotelPhone)
        val amenitiesContainer = dialogView.findViewById<LinearLayout>(R.id.amenitiesContainer)
        val bookBtn = dialogView.findViewById<Button>(R.id.bookNowBtn)
        val directionsBtn = dialogView.findViewById<Button>(R.id.directionsBtn)
        val callBtn = dialogView.findViewById<Button>(R.id.callHotelBtn)

        nameText.text = hotel.name

        val formattedRating = String.format("%.1f", hotel.rating)
        ratingText.text = if (hotel.rating > 0) "⭐ $formattedRating ★ (${hotel.reviewCount} reviews)" else "⭐ New Hotel"

        val formattedDistance = if (hotel.distance < 1000) {
            "${hotel.distance.toInt()} meters away"
        } else {
            "${DecimalFormat("#.#").format(hotel.distance / 1000)} km away"
        }
        distanceText.text = "📍 $formattedDistance"

        if (hotel.pricePerNight > 0) {
            priceText.text = "💰 BDT ${String.format("%.0f", hotel.pricePerNight)}/night"
        } else {
            priceText.text = "💰 Price not specified"
        }

        addressText.text = "🏠 ${hotel.address}"

        if (hotel.phone.isNotEmpty()) {
            phoneText.text = "📞 ${hotel.phone}"
            phoneText.visibility = View.VISIBLE
            callBtn.visibility = View.VISIBLE
        } else {
            phoneText.visibility = View.GONE
            callBtn.visibility = View.GONE
        }

        // Add amenities
        amenitiesContainer.removeAllViews()
        for (amenity in hotel.amenities) {
            val amenityChip = TextView(this).apply {
                text = " ✓ $amenity"
                textSize = 12f
                setTextColor(resources.getColor(R.color.navy_primary))
                setPadding(8, 4, 8, 4)
                setBackgroundResource(R.drawable.medical_badge)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 8)
                }
            }
            amenitiesContainer.addView(amenityChip)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        bookBtn.setOnClickListener {
            dialog.dismiss()
            bookRideToHotel(hotel)
        }

        directionsBtn.setOnClickListener {
            val uri = Uri.parse("https://www.google.com/maps/dir/$currentLatitude,$currentLongitude/${hotel.latitude},${hotel.longitude}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        callBtn.setOnClickListener {
            if (hotel.phone.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${hotel.phone}"))
                startActivity(intent)
            }
        }

        dialog.show()
    }

    private fun bookRideToHotel(hotel: Hotel) {
        val options = arrayOf("🚗 Uber", "🛺 Pathao", "Cancel")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Book Ride to Hotel")
            .setMessage("Choose a ride service to reach:\n${hotel.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val uberUri = Uri.parse("uber://?action=setPickup&pickup=my_location&dropoff[latitude]=${hotel.latitude}&dropoff[longitude]=${hotel.longitude}&dropoff[nickname]=${Uri.encode(hotel.name)}")
                        startActivity(Intent(Intent.ACTION_VIEW, uberUri))
                    }
                    1 -> {
                        val pathaoUri = Uri.parse("pathao://ride?destination_lat=${hotel.latitude}&destination_lng=${hotel.longitude}&destination_name=${Uri.encode(hotel.name)}")
                        startActivity(Intent(Intent.ACTION_VIEW, pathaoUri))
                    }
                }
            }
            .show()
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
                    searchHotels()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
