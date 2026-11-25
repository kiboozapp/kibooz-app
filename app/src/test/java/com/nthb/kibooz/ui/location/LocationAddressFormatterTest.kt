package com.nthb.kibooz.ui.location

import com.nthb.kibooz.util.FormRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationAddressFormatterTest {

    @Test
    fun formatPartsDeduplicatesRepeatedDistrictAndKeepsRicherCity() {
        val formatted = LocationAddressFormatter.formatParts(
            "SDN Percobaan",
            "Jl. Veteran",
            "Ketawanggede",
            "Lowokwaru",
            "Kec. Lowokwaru",
            "Malang",
            "Kota Malang",
            "Jawa Timur",
            "65145"
        )

        assertEquals(
            "SDN Percobaan, Jl. Veteran, Ketawanggede, Kec. Lowokwaru, Kota Malang, Jawa Timur, 65145",
            formatted
        )
    }

    @Test
    fun clampAddressRespectsMaxLimit() {
        val longText = "Jl. Pendidikan ".repeat(100)
        val clamped = LocationAddressFormatter.clampAddress(longText)
        assertEquals(FormRules.ADDRESS_MAX, clamped.length)
    }

    @Test
    fun parseCoordinatesAcceptsFormattedAndRawCoordinates() {
        val parsedFormatted = LocationAddressFormatter.parseCoordinates("Koordinat: -7.95248, 112.61372")
        assertEquals(-7.95248 to 112.61372, parsedFormatted)

        val parsedRaw = LocationAddressFormatter.parseCoordinates("-6.200000, 106.816666")
        assertEquals(-6.2 to 106.816666, parsedRaw)

        assertNull(LocationAddressFormatter.parseCoordinates("Jl. Veteran No. 1, Malang"))
        assertNull(LocationAddressFormatter.parseCoordinates("120.0, 112.0"))
    }

    @Test
    fun parseNominatimSearchExtractsOrderedCandidates() {
        val json = """
            [
              {
                "lat": "-7.95248",
                "lon": "112.61372",
                "name": "Universitas Brawijaya",
                "address": {
                  "amenity": "Universitas Brawijaya",
                  "road": "Jalan Veteran",
                  "suburb": "Ketawanggede",
                  "city_district": "Lowokwaru",
                  "city": "Kota Malang",
                  "state": "Jawa Timur",
                  "postcode": "65145"
                }
              }
            ]
        """.trimIndent()

        val candidates = LocationAddressFormatter.parseNominatimSearch(json)
        assertEquals(1, candidates.size)
        val first = candidates.first()
        assertEquals("Universitas Brawijaya", first.title)
        assertEquals(
            "Universitas Brawijaya, Jalan Veteran, Ketawanggede, Lowokwaru, Kota Malang, Jawa Timur, 65145",
            first.address
        )
        assertEquals(-7.95248, first.latitude, 0.00001)
        assertEquals(112.61372, first.longitude, 0.00001)
    }

    @Test
    fun parseNominatimReverseFormatsStreetAndNumber() {
        val json = """
            {
              "lat": "-7.96662",
              "lon": "112.63263",
              "address": {
                "road": "Jalan Melati",
                "house_number": "14",
                "village": "Jatimulyo",
                "city": "Kota Malang",
                "state": "Jawa Timur"
              }
            }
        """.trimIndent()

        val reversed = LocationAddressFormatter.parseNominatimReverse(json)
        assertEquals("Jalan Melati No. 14, Jatimulyo, Kota Malang, Jawa Timur", reversed)
    }
}
