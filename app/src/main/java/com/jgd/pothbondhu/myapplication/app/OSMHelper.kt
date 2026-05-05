package com.jgd.pothbondhu.myapplication.app

import android.content.Context
import android.location.Location
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

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

    // Search radius in meters
    private val SEARCH_RADIUS_METERS = 5000

    init {
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
        val locationOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
        mapView.overlays.clear()
        locationOverlay?.let { mapView.overlays.add(it) }
        mapView.invalidate()
    }

    /**
     * Searches for nearby places using the Overpass API (OSM's actual POI query engine).
     *
     * Why Overpass and not Nominatim?
     * - Nominatim is a geocoder: it converts text addresses to coordinates.
     * - Overpass is a spatial query engine: it finds OSM nodes/ways within a radius.
     * - "hospital near 23.8,90.4" is not a valid Nominatim query — it parses it as
     *   a place name search and returns nothing or wrong results.
     */
    suspend fun searchNearby(latitude: Double, longitude: Double, category: String): List<OSMPlace> {
        val overpassQuery = buildOverpassQuery(latitude, longitude, category)
        val url = "https://overpass-api.de/api/interpreter"

        return try {
            val responseBody = postOverpassQuery(url, overpassQuery)
            parseOverpassResponse(responseBody, category)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Builds an Overpass QL query for the given category.
     *
     * The query pattern:
     *   [out:json][timeout:25];
     *   (
     *     node["amenity"="hospital"](around:5000,23.8103,90.4125);
     *     way["amenity"="hospital"](around:5000,23.8103,90.4125);
     *   );
     *   out center 20;
     *
     * - `around:RADIUS,LAT,LON` — finds all OSM elements within RADIUS meters of the point
     * - `out center` — for ways (polygons like hospital buildings), returns the center point
     * - We query both `node` and `way` because some places are mapped as points, others as building outlines
     */
    private fun buildOverpassQuery(lat: Double, lon: Double, category: String): String {
        val tags = getCategoryTags(category)
        val radius = SEARCH_RADIUS_METERS

        val tagFilters = tags.joinToString("\n") { (key, value) ->
            """
            node["$key"="$value"](around:$radius,$lat,$lon);
            way["$key"="$value"](around:$radius,$lat,$lon);
            """.trimIndent()
        }

        return """
            [out:json][timeout:25];
            (
            $tagFilters
            );
            out center 20;
        """.trimIndent()
    }

    /**
     * Maps app categories to OSM tag key-value pairs.
     * These are the official OSM tags used by mappers worldwide.
     * Reference: https://wiki.openstreetmap.org/wiki/Map_features
     */
    private fun getCategoryTags(category: String): List<Pair<String, String>> {
        return when (category) {
            "hospital" -> listOf(
                "amenity" to "hospital",
                "amenity" to "clinic",
                "amenity" to "doctors"
            )
            "police" -> listOf(
                "amenity" to "police"
            )
            "fuel" -> listOf(
                "amenity" to "fuel"
            )
            "mechanic" -> listOf(
                "shop" to "car_repair",
                "shop" to "motorcycle_repair"
            )
            "blood_bank" -> listOf(
                "amenity" to "blood_bank",
                "amenity" to "blood_donation",
                "healthcare" to "blood_bank",     // A more modern healthcare-specific tag
                "healthcare" to "clinic",         // Some donation centers are tagged as clinics
                "amenity" to "hospital"
            )
            else -> listOf("amenity" to category)
        }
    }

    /**
     * Makes the HTTP POST request to Overpass API.
     * Overpass requires POST with the query in the request body prefixed by "data=".
     * We also set a User-Agent — required by Nominatim, good practice for Overpass.
     */
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

    /**
     * Parses the Overpass JSON response into OSMPlace objects.
     *
     * Overpass response structure:
     * {
     *   "elements": [
     *     { "type": "node", "id": 123, "lat": 23.8, "lon": 90.4, "tags": { "name": "..." } },
     *     { "type": "way",  "id": 456, "center": { "lat": 23.9, "lon": 90.5 }, "tags": { ... } }
     *   ]
     * }
     *
     * For ways, we use the "center" field (computed by Overpass) instead of lat/lon directly.
     */
    private fun parseOverpassResponse(responseBody: String, category: String): List<OSMPlace> {
        val results = mutableListOf<OSMPlace>()
        val json = JSONObject(responseBody)
        val elements = json.getJSONArray("elements")

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
                    // Ways have a "center" object when queried with "out center"
                    val center = element.optJSONObject("center") ?: continue
                    lat = center.getDouble("lat")
                    lon = center.getDouble("lon")
                }
                else -> continue
            }

            val id = element.getLong("id")
            val tags = element.optJSONObject("tags")

            // Build a display name from OSM tags, falling back gracefully
            val name = tags?.optString("name")
                ?: tags?.optString("operator")
                ?: tags?.optString("brand")
                ?: "Unnamed ${category.replaceFirstChar { it.uppercaseChar() }}"

            // Build a readable address from available address tags
            val addressParts = mutableListOf<String>()
            tags?.let {
                it.optString("addr:housenumber").takeIf { s -> s.isNotEmpty() }?.let { v -> addressParts.add(v) }
                it.optString("addr:street").takeIf { s -> s.isNotEmpty() }?.let { v -> addressParts.add(v) }
                it.optString("addr:suburb").takeIf { s -> s.isNotEmpty() }?.let { v -> addressParts.add(v) }
                it.optString("addr:city").takeIf { s -> s.isNotEmpty() }?.let { v -> addressParts.add(v) }
            }
            val displayName = if (addressParts.isNotEmpty()) {
                "$name, ${addressParts.joinToString(", ")}"
            } else {
                name
            }

            results.add(
                OSMPlace(
                    place_id = id,
                    lat = lat.toString(),
                    lon = lon.toString(),
                    display_name = displayName,
                    type = tags?.optString("amenity") ?: tags?.optString("shop") ?: category
                )
            )
        }

        return results
    }
}