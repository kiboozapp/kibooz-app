package com.nthb.kibooz.ui.location

import com.nthb.kibooz.util.FormRules
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale

data class LocationSearchCandidate(
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

@JsonClass(generateAdapter = true)
internal data class NominatimAddressDto(
    val road: String? = null,
    @Json(name = "house_number") val houseNumber: String? = null,
    val neighbourhood: String? = null,
    val suburb: String? = null,
    val village: String? = null,
    @Json(name = "city_district") val cityDistrict: String? = null,
    val city: String? = null,
    val town: String? = null,
    val county: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val amenity: String? = null,
    val school: String? = null,
    val building: String? = null
)

@JsonClass(generateAdapter = true)
internal data class NominatimPlaceDto(
    val lat: String? = null,
    val lon: String? = null,
    val name: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    val address: NominatimAddressDto? = null
)

/** Helper murni untuk memformat dan mem-parse hasil geocoding menjadi alamat ringkas yang siap disimpan. */
object LocationAddressFormatter {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val placeAdapter = moshi.adapter(NominatimPlaceDto::class.java)
    private val searchListType = Types.newParameterizedType(List::class.java, NominatimPlaceDto::class.java)
    private val searchListAdapter = moshi.adapter<List<NominatimPlaceDto>>(searchListType)

    private val coordRegex = Regex("""(?:Koordinat:\s*)?(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    /** Menggabungkan komponen alamat, membuang duplikasi umum (misal kecamatan/kota berulang), dan membatasi ke [FormRules.ADDRESS_MAX]. */
    fun formatParts(vararg rawParts: String?): String {
        val unique = mutableListOf<String>()
        for (raw in rawParts) {
            val cleaned = raw?.trim().orEmpty()
            if (cleaned.isEmpty()) continue
            val normalized = normalizeToken(cleaned)
            if (normalized.isEmpty()) continue

            val duplicateIndex = unique.indexOfFirst { existing ->
                val normExisting = normalizeToken(existing)
                normExisting == normalized
            }
            if (duplicateIndex >= 0) {
                // Pertahankan varian yang lebih deskriptif (misal "Kota Malang" daripada "Malang")
                if (cleaned.length > unique[duplicateIndex].length) {
                    unique[duplicateIndex] = cleaned
                }
            } else {
                unique.add(cleaned)
            }
        }
        return clampAddress(unique.joinToString(", "))
    }

    private fun normalizeToken(value: String): String {
        return value.lowercase(Locale.ROOT)
            .removePrefix("kecamatan ")
            .removePrefix("kec. ")
            .removePrefix("kelurahan ")
            .removePrefix("kel. ")
            .removePrefix("desa ")
            .removePrefix("kota ")
            .removePrefix("kabupaten ")
            .removePrefix("kab. ")
            .trim()
    }

    fun formatCoordinates(latitude: Double, longitude: Double): String {
        return String.format(Locale.US, "Koordinat: %.5f, %.5f", latitude, longitude)
    }

    fun parseCoordinates(input: String): Pair<Double, Double>? {
        val match = coordRegex.matchEntire(input.trim()) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    fun clampAddress(address: String): String {
        val trimmed = address.trim()
        return if (trimmed.length <= FormRules.ADDRESS_MAX) trimmed else trimmed.take(FormRules.ADDRESS_MAX).trimEnd()
    }

    fun parseNominatimReverse(json: String): String? {
        val dto = runCatching { placeAdapter.fromJson(json) }.getOrNull() ?: return null
        return formatDtoAddress(dto)?.takeIf { it.isNotBlank() }
    }

    fun parseNominatimSearch(json: String): List<LocationSearchCandidate> {
        val list = runCatching { searchListAdapter.fromJson(json) }.getOrNull().orEmpty()
        return list.mapNotNull { dto ->
            val lat = dto.lat?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = dto.lon?.toDoubleOrNull() ?: return@mapNotNull null
            val formatted = formatDtoAddress(dto)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = dto.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: formatted.substringBefore(",").trim().ifEmpty { formatted }
            LocationSearchCandidate(
                title = title,
                address = formatted,
                latitude = lat,
                longitude = lon
            )
        }
    }

    private fun formatDtoAddress(dto: NominatimPlaceDto): String? {
        val a = dto.address
        if (a != null) {
            val poi = a.school ?: a.amenity ?: a.building ?: dto.name
            val street = listOfNotNull(
                a.road?.trim()?.takeIf { it.isNotEmpty() },
                a.houseNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { "No. $it" }
            ).joinToString(" ").ifBlank { null }

            val joined = formatParts(
                poi,
                street,
                a.neighbourhood,
                a.suburb ?: a.village,
                a.cityDistrict,
                a.city ?: a.town ?: a.county,
                a.state,
                a.postcode
            )
            if (joined.isNotBlank()) return joined
        }
        return dto.displayName?.let(::clampAddress)?.takeIf { it.isNotBlank() }
    }
}
