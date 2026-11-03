package com.jgd.pothbondhu.myapplication.app

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.math.cos

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

    companion object {
        private const val TAG = "OSMHelper"
        private const val USER_AGENT = "PothBondhu/1.0 (Emergency App Bangladesh)"

        // Search radius in meters
        private const val SEARCH_RADIUS_METERS = 5000

        // Max number of places returned to the UI
        private const val MAX_RESULTS = 25

        // Hard limit for one whole request (connect + TLS + send + read).
        // Short, so a blocked server fails fast and we move on to the next one.
        private const val CALL_TIMEOUT_SEC = 12L

        /** Overpass servers, tried in order. */
        private val OVERPASS_MIRRORS = listOf(
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass-api.de/api/interpreter"
        )

        /**
         * Last-resort fallback if every Overpass server is unreachable.
         * Same organisation (OSMF) that serves the map tiles.
         */
        private const val NOMINATIM_HOST = "nominatim.openstreetmap.org"
    }

    /** OkHttp client that prefers IPv4 and gives up quickly. */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val all = Dns.SYSTEM.lookup(hostname)
                    val v4 = all.filterIsInstance<Inet4Address>()
                    return if (v4.isNotEmpty()) v4 else all
                }
            })
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()
    }

    init {
        val config = Configuration.getInstance()

        // Only load if it hasn't been initialized with a custom User-Agent
        if (config.userAgentValue.isNullOrEmpty() || config.userAgentValue == "osmdroid") {
            config.load(context, PreferenceManager.getDefaultSharedPreferences(context))
            config.userAgentValue = context.packageName

            // Set internal cache paths to avoid permission issues
            val osmDir = File(context.cacheDir, "osmdroid")
            val tilesDir = File(osmDir, "tiles")
            if (!tilesDir.exists()) tilesDir.mkdirs()
            config.osmdroidBasePath = osmDir
            config.osmdroidTileCache = tilesDir
        }
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
        return if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.lastFix ?: lastKnownLocation
        } else {
            lastKnownLocation
        }
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
     * Searches for nearby places.
     *
     * 1. Tries each Overpass mirror (best data, exact tags).
     * 2. If all of them fail, falls back to Nominatim.
     * 3. If that fails too, throws an IOException so the UI shows a real error.
     *
     * Results are sorted by distance, closest first.
     */
    suspend fun searchNearby(latitude: Double, longitude: Double, category: String): List<OSMPlace> =
        withContext(Dispatchers.IO) {
            val overpassQuery = buildOverpassQuery(latitude, longitude, category)
            var lastError: Exception? = null

            // ---- Step 1: Overpass mirrors ----
            for (mirror in OVERPASS_MIRRORS) {
                try {
                    Log.d(TAG, "Trying Overpass mirror: $mirror")
                    val body = postOverpassQuery(mirror, overpassQuery)
                    val places = parseOverpassResponse(body, category)
                    Log.d(TAG, "Overpass $mirror returned ${places.size} places")
                    return@withContext places
                        .sortedBy { distanceMeters(latitude, longitude, it) }
                        .take(MAX_RESULTS)
                } catch (e: Exception) {
                    Log.w(TAG, "Overpass failed: $mirror -> ${e.javaClass.simpleName}: ${e.message}")
                    lastError = e
                }
            }

            // ---- Step 2: Nominatim fallback ----
            try {
                Log.d(TAG, "Trying Nominatim fallback")
                val places = searchViaNominatim(latitude, longitude, category)
                Log.d(TAG, "Nominatim returned ${places.size} places")
                return@withContext places
                    .sortedBy { distanceMeters(latitude, longitude, it) }
                    .take(MAX_RESULTS)
            } catch (e: Exception) {
                Log.w(TAG, "Nominatim failed -> ${e.javaClass.simpleName}: ${e.message}")
                lastError = e
            }

            throw IOException(
                "Could not reach the map servers. Your network may be blocking them. " +
                        "Try a VPN or a different Wi-Fi.",
                lastError
            )
        }

    private fun distanceMeters(fromLat: Double, fromLon: Double, place: OSMPlace): Float {
        val out = FloatArray(1)
        Location.distanceBetween(fromLat, fromLon, place.lat.toDouble(), place.lon.toDouble(), out)
        return out[0]
    }

    // ------------------------------------------------------------------
    // Overpass
    // ------------------------------------------------------------------

    private fun buildOverpassQuery(lat: Double, lon: Double, category: String): String {
        val tags = getCategoryTags(category)
        val radius = SEARCH_RADIUS_METERS

        val tagFilters = tags.joinToString("\n") { (key, value) ->
            "node[\"$key\"=\"$value\"](around:$radius,$lat,$lon);\n" +
                    "way[\"$key\"=\"$value\"](around:$radius,$lat,$lon);"
        }

        return "[out:json][timeout:25];\n(\n$tagFilters\n);\nout center 60;"
    }

    /** Maps app categories to OSM tag key-value pairs. */
    private fun getCategoryTags(category: String): List<Pair<String, String>> {
        return when (category) {
            "hospital" -> listOf(
                "amenity" to "hospital",
                "amenity" to "clinic",
                "amenity" to "doctors"
            )
            "police" -> listOf("amenity" to "police")
            "fuel" -> listOf("amenity" to "fuel")
            "mechanic" -> listOf(
                "shop" to "car_repair",
                "shop" to "motorcycle_repair"
            )
            "blood_bank" -> listOf(
                "amenity" to "blood_bank",
                "amenity" to "blood_donation",
                "healthcare" to "blood_bank",
                "healthcare" to "clinic",
                "amenity" to "hospital"
            )
            else -> listOf("amenity" to category)
        }
    }

    private fun postOverpassQuery(urlString: String, query: String): String {
        val body = FormBody.Builder().add("data", query).build()
        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from $urlString")
            return resp.body?.string() ?: throw IOException("Empty response from $urlString")
        }
    }

    private fun parseOverpassResponse(responseBody: String, category: String): List<OSMPlace> {
        val results = mutableListOf<OSMPlace>()
        val json = JSONObject(responseBody)
        val elements = json.getJSONArray("elements")
        val seenIds = HashSet<Long>()

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
            if (!seenIds.add(id)) continue // skip duplicates

            val tags = element.optJSONObject("tags")

            val name = tags?.optString("name")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("operator")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("brand")?.takeIf { it.isNotBlank() }
                ?: "Unnamed ${category.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }}"

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

            val placeType = tags?.optString("amenity")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("shop")?.takeIf { it.isNotBlank() }
                ?: category

            results.add(
                OSMPlace(
                    place_id = id,
                    lat = lat.toString(),
                    lon = lon.toString(),
                    display_name = displayName,
                    type = placeType
                )
            )
        }

        return results
    }

    // ------------------------------------------------------------------
    // Nominatim fallback
    // ------------------------------------------------------------------

    /** Search words sent to Nominatim for each app category. */
    private fun getNominatimTerms(category: String): List<String> {
        return when (category) {
            "hospital" -> listOf("hospital", "clinic")
            "police" -> listOf("police")
            "fuel" -> listOf("fuel station")
            "mechanic" -> listOf("car repair", "garage")
            "blood_bank" -> listOf("blood bank", "hospital")
            else -> listOf(category.replace('_', ' '))
        }
    }

    /**
     * Searches inside a box around the point (about SEARCH_RADIUS_METERS in each direction).
     * Nominatim's policy allows 1 request per second, so requests are spaced out.
     */
    private fun searchViaNominatim(lat: Double, lon: Double, category: String): List<OSMPlace> {
        val dLat = SEARCH_RADIUS_METERS / 111_320.0
        val dLon = dLat / cos(Math.toRadians(lat))
        // viewbox = left,top,right,bottom  (lon_min, lat_max, lon_max, lat_min)
        val viewbox = "${lon - dLon},${lat + dLat},${lon + dLon},${lat - dLat}"

        val results = mutableListOf<OSMPlace>()
        val seenIds = HashSet<Long>()
        var anySuccess = false
        var lastError: Exception? = null

        for ((index, term) in getNominatimTerms(category).withIndex()) {
            if (index > 0) Thread.sleep(1100)

            try {
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host(NOMINATIM_HOST)
                    .addPathSegment("search")
                    .addQueryParameter("q", term)
                    .addQueryParameter("format", "jsonv2")
                    .addQueryParameter("viewbox", viewbox)
                    .addQueryParameter("bounded", "1")
                    .addQueryParameter("limit", "20")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from Nominatim")
                    val text = resp.body?.string() ?: throw IOException("Empty response from Nominatim")
                    val array = JSONArray(text)
                    anySuccess = true

                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val id = item.optLong("place_id")
                        if (!seenIds.add(id)) continue

                        results.add(
                            OSMPlace(
                                place_id = id,
                                lat = item.getString("lat"),
                                lon = item.getString("lon"),
                                display_name = item.optString("display_name").ifBlank {
                                    item.optString("name", "Unnamed")
                                },
                                type = item.optString("type", category)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Nominatim term '$term' failed: ${e.javaClass.simpleName}: ${e.message}")
                lastError = e
            }
        }

        if (!anySuccess) throw lastError ?: IOException("Nominatim unreachable")
        return results
    }
}