package com.nthb.kibooz.data.remote

import com.nthb.kibooz.R
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorMessagesTest {

    private fun failure(status: Int, errorCode: String? = null) =
        ApiResult.Failure(status = status, errorCode = errorCode, message = null)

    @Test
    fun `a known error code picks its own message whatever the status is`() {
        assertEquals(R.string.error_invalid_credentials, failure(401, "AUTH_INVALID_CREDENTIALS").messageRes())
        assertEquals(R.string.error_class_full, failure(409, "CLASS_FULL").messageRes())
        assertEquals(R.string.error_trash_daily_limit, failure(429, "TRASH_DAILY_LIMIT_REACHED").messageRes())
        assertEquals(R.string.error_nisn_taken, failure(409, "NISN_ALREADY_REGISTERED").messageRes())
    }

    @Test
    fun `the email verification codes have their own messages`() {
        assertEquals(R.string.verifikasi_error_belum_terverifikasi, failure(403, "EMAIL_NOT_VERIFIED").messageRes())
        assertEquals(R.string.verifikasi_error_kode_salah, failure(400, "VERIFICATION_CODE_INVALID").messageRes())
        assertEquals(R.string.verifikasi_error_terlalu_cepat, failure(429, "VERIFICATION_RESEND_TOO_SOON").messageRes())
        assertEquals(R.string.verifikasi_sudah_terverifikasi, failure(409, "EMAIL_ALREADY_VERIFIED").messageRes())
        assertEquals(R.string.verifikasi_error_kirim_gagal, failure(503, "EMAIL_SEND_FAILED").messageRes())
    }

    @Test
    fun `every token error ends the session with the same message`() {
        listOf("AUTH_TOKEN_MISSING", "AUTH_TOKEN_INVALID", "AUTH_REFRESH_INVALID").forEach {
            assertEquals(it, R.string.error_session_ended, failure(401, it).messageRes())
        }
    }

    @Test
    fun `an unknown code falls back to the status`() {
        assertEquals(R.string.error_rate_limited, failure(429, "SOMETHING_NEW").messageRes())
        assertEquals(R.string.error_unknown, failure(409, "SOMETHING_NEW").messageRes())
    }

    @Test
    fun `a reply without an envelope falls back to the status`() {
        assertEquals(R.string.error_rate_limited, failure(429).messageRes())
        assertEquals(R.string.error_unavailable, failure(502).messageRes())
        assertEquals(R.string.error_unavailable, failure(503).messageRes())
        assertEquals(R.string.error_unavailable, failure(504).messageRes())
        assertEquals(R.string.error_file_too_large, failure(413).messageRes())
        assertEquals(R.string.error_forbidden, failure(403).messageRes())
        assertEquals(R.string.error_not_found, failure(404).messageRes())
    }

    @Test
    fun `a missing connection has its own message and a success has none`() {
        assertEquals(R.string.error_network, ApiResult.NetworkError(IOException("offline")).errorMessageRes())
        assertNull(ApiResult.Success("ok").errorMessageRes())
    }

    @Test
    fun `a missing class means the class is gone for the person`() {
        assertEquals(R.string.error_class_gone, failure(404, "CLASS_NOT_FOUND").messageRes())
    }

    @Test
    fun `an archived class and a class that is not archived have their own messages`() {
        assertEquals(R.string.error_class_archived, failure(409, "CLASS_ARCHIVED").messageRes())
        assertEquals(R.string.error_class_not_archived, failure(409, "CLASS_NOT_ARCHIVED").messageRes())
    }
}
