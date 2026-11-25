package com.nthb.kibooz.ui.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.nthb.kibooz.BuildConfig
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ReverseGeocodeResult(
    val address: String,
    val isCoordinateFallback: Boolean
)

/**
 * Mengubah koordinat <-> alamat untuk pemilih lokasi di peta.
 * Memprioritaskan [Geocoder] bawaan Android lalu fallback ke Nominatim OpenStreetMap HTTPS.
 */
class LocationGeocoder(context: Context) {

    private val appContext = context.applicationContext
    private val localeId = Locale("id", "ID")
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val userAgent = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME} (https://kibooz.syafahadyan.com)"

    suspend fun reverse(latitude: Double, longitude: Double): ReverseGeocodeResult {
        val fromAndroid = reverseWithAndroidGeocoder(latitude, longitude)
        if (!fromAndroid.isNullOrBlank()) {
            return ReverseGeocodeResult(fromAndroid, isCoordinateFallback = false)
        }

        val fromNominatim = reverseWithNominatim(latitude, longitude)
        if (!fromNominatim.isNullOrBlank()) {
            return ReverseGeocodeResult(fromNominatim, isCoordinateFallback = false)
        }

        return ReverseGeocodeResult(
            address = LocationAddressFormatter.formatCoordinates(latitude, longitude),
            isCoordinateFallback = true
        )
    }

    suspend fun search(query: String): List<LocationSearchCandidate> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()

        val fromNominatim = searchWithNominatim(clean)
        if (fromNominatim.isNotEmpty()) return fromNominatim

        return searchWithAndroidGeocoder(clean)
    }

    private suspend fun reverseWithAndroidGeocoder(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, localeId)
        val addresses = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(results: MutableList<Address>) {
                            if (cont.isActive) cont.resume(results)
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(emptyList())
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
                }
            }
        }.getOrNull().orEmpty()

        val first = addresses.firstOrNull() ?: return null
        return formatAndroidAddress(first)
    }

    private suspend fun searchWithAndroidGeocoder(query: String): List<LocationSearchCandidate> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(appContext, localeId)
        val addresses = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(results: MutableList<Address>) {
                            if (cont.isActive) cont.resume(results)
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(emptyList())
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 5).orEmpty()
                }
            }
        }.getOrNull().orEmpty()

        return addresses.mapNotNull { addr ->
            val formatted = formatAndroidAddress(addr) ?: return@mapNotNull null
            val title = addr.featureName?.takeIf { !it.equals(addr.subThoroughfare, ignoreCase = true) }
                ?: formatted.substringBefore(",").trim().ifEmpty { formatted }
            LocationSearchCandidate(
                title = title,
                address = formatted,
                latitude = addr.latitude,
                longitude = addr.longitude
            )
        }
    }

    private fun formatAndroidAddress(addr: Address): String? {
        val street = listOfNotNull(
            addr.thoroughfare?.trim()?.takeIf { it.isNotEmpty() },
            addr.subThoroughfare?.trim()?.takeIf { it.isNotEmpty() }?.let { "No. $it" }
        ).joinToString(" ").ifBlank { null }

        val poi = addr.featureName?.trim()?.takeIf {
            it.isNotEmpty() && it != addr.subThoroughfare && it != addr.thoroughfare && !it.contains("+")
        }

        val combined = LocationAddressFormatter.formatParts(
            poi,
            street,
            addr.subLocality,
            addr.locality,
            addr.subAdminArea,
            addr.adminArea,
            addr.postalCode
        )
        if (combined.isNotBlank()) return combined

        val lines = (0..addr.maxAddressLineIndex).mapNotNull { i -> addr.getAddressLine(i)?.trim() }
        return LocationAddressFormatter.clampAddress(lines.joinToString(", ")).takeIf { it.isNotBlank() }
    }

    private suspend fun reverseWithNominatim(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&addressdetails=1&accept-language=id",
            latitude,
            longitude
        )
        val body = fetchJson(url) ?: return@withContext null
        LocationAddressFormatter.parseNominatimReverse(body)
    }

    private suspend fun searchWithNominatim(query: String): List<LocationSearchCandidate> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$encoded&limit=5&addressdetails=1&countrycodes=id&accept-language=id"
        val body = fetchJson(url) ?: return@withContext emptyList()
        LocationAddressFormatter.parseNominatimSearch(body)
    }

    private fun fetchJson(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(request).execute().use { res ->
            if (!res.isSuccessful) null else res.body?.string()
        }
    }.getOrNull()
}
