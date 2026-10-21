package com.nthb.kibooz.data.remote

import okhttp3.ConnectionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectionSpecsTest {

    @Test
    fun `an https address allows only modern TLS`() {
        val specs = connectionSpecsFor("https://kibooz-api.syafahadyan.com/api/v1/")

        assertEquals(listOf(ConnectionSpec.RESTRICTED_TLS), specs)
        assertFalse(ConnectionSpec.CLEARTEXT in specs)
    }

    @Test
    fun `an http address also allows cleartext for the local server`() {
        val specs = connectionSpecsFor("http://10.0.2.2:8080/api/v1/")

        assertEquals(listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.CLEARTEXT), specs)
    }
}
