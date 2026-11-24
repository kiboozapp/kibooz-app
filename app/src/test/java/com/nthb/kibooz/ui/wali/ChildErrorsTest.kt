package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiResult
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ChildErrorsTest {

    private fun failure(code: String?, status: Int = 409) = ApiResult.Failure(status, code, "x")

    @Test
    fun `every code of the child routes has a message that names the next step`() {
        val expected = mapOf(
            "STUDENT_NOT_FOUND" to R.string.anak_error_tidak_ditemukan,
            "NISN_ALREADY_REGISTERED" to R.string.anak_error_nisn_terverifikasi,
            "CLASS_CODE_NOT_FOUND" to R.string.anak_error_kode_tidak_ditemukan,
            "CLASS_JOINING_CLOSED" to R.string.anak_error_kelas_tutup,
            "CLASS_FULL" to R.string.anak_error_kelas_penuh,
            "CLASS_NO_ACTIVE_TEACHER" to R.string.anak_error_kelas_tanpa_guru,
            "CHILD_LIMIT_REACHED" to R.string.anak_error_batas,
            "PROFILE_NOT_FOUND" to R.string.anak_error_profil_tidak_ada
        )

        expected.forEach { (code, message) -> assertEquals(code, message, failure(code).childErrorRes()) }
    }

    @Test
    fun `the shared codes keep the shared messages`() {
        assertEquals(R.string.error_rate_limited, failure("RATE_LIMITED", 429).childErrorRes())
        assertEquals(R.string.error_request_in_progress, failure("IDEMPOTENCY_IN_PROGRESS").childErrorRes())
        assertEquals(R.string.error_key_reused, failure("IDEMPOTENCY_KEY_REUSED", 422).childErrorRes())
        assertEquals(R.string.error_validation, failure("VALIDATION_ERROR", 400).childErrorRes())
        assertEquals(R.string.error_session_ended, failure("AUTH_TOKEN_INVALID", 401).childErrorRes())
        assertEquals(R.string.error_forbidden, failure("AUTH_FORBIDDEN", 403).childErrorRes())
    }

    @Test
    fun `an unknown code falls back to the status and a proxy answer to its status`() {
        assertEquals(R.string.error_not_found, failure("SOMETHING_NEW", 404).childErrorRes())
        assertEquals(R.string.error_unavailable, failure(null, 503).childErrorRes())
        assertEquals(R.string.error_unknown, failure("SOMETHING_NEW", 418).childErrorRes())
    }

    @Test
    fun `no answer is the network message`() {
        assertEquals(R.string.error_network, ApiResult.NetworkError(IOException("offline")).childErrorRes())
    }
}
