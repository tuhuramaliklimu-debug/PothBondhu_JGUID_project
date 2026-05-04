package com.jgd.pothbondhu.myapplication.app

import android.content.Context
import android.location.Location
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// API Interface for Nominatim (OSM search)
interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 20
    ): List<OSMPlace>
}

// Data class for API response
data class OSMPlace(
    val place_id: Long,
    val lat: String,
    val lon: String,
    val display_name: String,
    val type: String
)

class OSMHelper(private val context: Context, private val mapView: MapView) {

    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private var lastKnownLocation: Location? = null

    init {
        // Initialize OSM configuration
        Configuration.getInstance().load(
            context,
            PreferenceManager.getDefaultSharedPreferences(context)
        )
    }

    fun setupMap(centerLat: Double = 23.8103, centerLon: Double = 90.4125) {
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(true)
        mapView.controller.setZoom(14.0)
        mapView.controller.setCenter(GeoPoint(centerLat, centerLon))
    }

    fun enableMyLocation() {
        myLocationOverlay = MyLocationNewOverlay(mapView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        mapView.overlays.add(myLocationOverlay)

        // Get last known location
        lastKnownLocation = myLocationOverlay.lastFix
    }

    fun getLastKnownLocation(): Location? {
        return myLocationOverlay.lastFix ?: lastKnownLocation
    }

    fun addMarker(lat: Double, lon: Double, title: String, snippet: String? = null) {
        val marker = Marker(mapView)
        marker.position = GeoPoint(lat, lon)
        marker.title = title
        marker.snippet = snippet
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(marker)
    }

    fun addMarkersForCategory(places: List<OSMPlace>, categoryColor: Int) {
        for (place in places) {
            val marker = Marker(mapView)
            marker.position = GeoPoint(place.lat.toDouble(), place.lon.toDouble())
            marker.title = place.display_name
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    fun clearMarkers() {
        // Keep only myLocation overlay
        val myLocationOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
        mapView.overlays.clear()
        myLocationOverlay?.let { mapView.overlays.add(it) }
        mapView.invalidate()
    }

    suspend fun searchNearby(latitude: Double, longitude: Double, category: String): List<OSMPlace> {
        val api = Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApi::class.java)

        val searchQuery = when (category) {
            "hospital" -> "hospital near $latitude,$longitude"
            "police" -> "police station near $latitude,$longitude"
            "blood_bank" -> "blood bank near $latitude,$longitude"
            "fuel" -> "petrol pump near $latitude,$longitude"
            "mechanic" -> "car repair shop near $latitude,$longitude"
            else -> "$category near $latitude,$longitude"
        }

        return try {
            api.search(searchQuery)
        } catch (e: Exception) {
            emptyList()
        }
    }
}