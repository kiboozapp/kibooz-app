package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.AddChildRequest
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.NewStudent
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.ui.wali.ChildFixtures.failure
import com.nthb.kibooz.ui.wali.ChildFixtures.offline
import com.nthb.kibooz.ui.wali.ChildFixtures.ok
import com.nthb.kibooz.ui.wali.ChildFixtures.repositoryOf
import com.nthb.kibooz.ui.wali.ChildFixtures.summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TambahAnakViewModelTest {

    private val api = FakeWaliProfileApi()

    private fun viewModel() = TambahAnakViewModel(repositoryOf(api))

    private fun TambahAnakViewModel.submitValid() = submit("k7m2qx4r", "Amelia Zahra", "0012345678")

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api.add = { ok(summary("new", name = it.student.fullName, nisn = it.student.nisn)) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a valid form registers the child with the upper case code`() {
        val viewModel = viewModel()

        viewModel.submitValid()

        assertEquals(listOf(AddChildRequest("K7M2QX4R", NewStudent("0012345678", "Amelia Zahra"))), api.addRequests)
        val state = viewModel.state.value
        assertEquals("new", state.added?.id)
        assertFalse(state.loading)
        assertTrue(state.fieldErrors.isEmpty())
    }

    @Test
    fun `a bad code, a short name and a bad NISN are refused before the request`() {
        val viewModel = viewModel()

        viewModel.submit("ab-1", "A", "12ab")

        val errors = viewModel.state.value.fieldErrors
        assertEquals(R.string.form_kode_kelas_tidak_valid, errors[AddChildField.CLASS_CODE])
        assertEquals(R.string.form_nama_terlalu_pendek, errors[AddChildField.CHILD_NAME])
        assertEquals(R.string.form_nisn_tidak_valid, errors[AddChildField.NISN])
        assertTrue(api.addRequests.isEmpty())
    }

    @Test
    fun `a code that is too long is refused`() {
        val viewModel = viewModel()

        viewModel.submit("ABCDEFGHIJKLM", "Amelia", "0012345678")

        assertEquals(R.string.form_kode_kelas_tidak_valid, viewModel.state.value.fieldErrors[AddChildField.CLASS_CODE])
        assertTrue(api.addRequests.isEmpty())
    }

    @Test
    fun `the class errors are shown on the code field`() {
        listOf(
            Triple("CLASS_CODE_NOT_FOUND", 404, R.string.anak_error_kode_tidak_ditemukan),
            Triple("CLASS_JOINING_CLOSED", 409, R.string.anak_error_kelas_tutup),
            Triple("CLASS_FULL", 409, R.string.anak_error_kelas_penuh),
            Triple("CLASS_NO_ACTIVE_TEACHER", 409, R.string.anak_error_kelas_tanpa_guru)
        ).forEach { (code, status, message) ->
            api.add = { failure(status, code) }
            val viewModel = viewModel()

            viewModel.submitValid()

            val state = viewModel.state.value
            assertEquals(code, message, state.fieldErrors[AddChildField.CLASS_CODE])
            assertNull(code, state.error)
            assertNull(code, state.added)
        }
    }

    @Test
    fun `a NISN that a teacher verified for another child is shown on the NISN field`() {
        api.add = { failure(409, "NISN_ALREADY_REGISTERED") }
        val viewModel = viewModel()

        viewModel.submitValid()

        assertEquals(R.string.anak_error_nisn_terverifikasi, viewModel.state.value.fieldErrors[AddChildField.NISN])
    }

    @Test
    fun `the child limit is a general message and not a field error`() {
        api.add = { failure(409, "CHILD_LIMIT_REACHED") }
        val viewModel = viewModel()

        viewModel.submitValid()

        val state = viewModel.state.value
        assertEquals(R.string.anak_error_batas, state.error)
        assertTrue(state.fieldErrors.isEmpty())
        assertNull(state.outage)
    }

    @Test
    fun `a request that is still running and a rate limit are general messages`() {
        api.add = { failure(409, "IDEMPOTENCY_IN_PROGRESS") }
        val viewModel = viewModel()
        viewModel.submitValid()
        assertEquals(R.string.error_request_in_progress, viewModel.state.value.error)

        api.add = { failure(429, "RATE_LIMITED") }
        viewModel.submitValid()
        assertEquals(R.string.error_rate_limited, viewModel.state.value.error)
    }

    @Test
    fun `a validation error marks the fields that the server names`() {
        api.add = {
            failure(400, "VALIDATION_ERROR", mapOf("classCode" to "bad", "student.nisn" to "bad", "student.fullName" to "bad"))
        }
        val viewModel = viewModel()

        viewModel.submitValid()

        val errors = viewModel.state.value.fieldErrors
        assertEquals(R.string.form_kode_kelas_tidak_valid, errors[AddChildField.CLASS_CODE])
        assertEquals(R.string.form_nisn_tidak_valid, errors[AddChildField.NISN])
        assertEquals(R.string.form_nama_terlalu_pendek, errors[AddChildField.CHILD_NAME])
    }

    @Test
    fun `a validation error with no known field is a general message`() {
        api.add = { failure(400, "VALIDATION_ERROR") }
        val viewModel = viewModel()

        viewModel.submitValid()

        assertEquals(R.string.error_validation, viewModel.state.value.error)
    }

    @Test
    fun `going offline shows the offline card and the same key is used on retry`() {
        api.add = { offline() }
        val viewModel = viewModel()

        viewModel.submitValid()

        val state = viewModel.state.value
        assertEquals(OutageKind.OFFLINE, state.outage)
        assertEquals(R.string.error_network, state.error)

        api.add = { ok(summary("new")) }
        viewModel.submitValid()

        assertEquals(2, api.addKeys.size)
        assertEquals(api.addKeys[0], api.addKeys[1])
        assertEquals("new", viewModel.state.value.added?.id)
    }

    @Test
    fun `a gateway error without a JSON body shows the unavailable card`() {
        api.add = { retrofit2.Response.error(503, "".toResponseBody("text/html".toMediaType())) }
        val viewModel = viewModel()

        viewModel.submitValid()

        assertEquals(OutageKind.UNAVAILABLE, viewModel.state.value.outage)
        assertEquals(R.string.error_unavailable, viewModel.state.value.error)
    }

    @Test
    fun `a changed form gets a new key and the same form keeps its key`() {
        api.add = { failure(409, "CLASS_FULL") }
        val viewModel = viewModel()

        viewModel.submitValid()
        viewModel.submitValid()
        viewModel.submit("K7M2QX4R", "Amelia Zahra", "0012345679")

        assertEquals(api.addKeys[0], api.addKeys[1])
        assertNotEquals(api.addKeys[1], api.addKeys[2])
    }

    @Test
    fun `a spacing change in the same form still counts as the same action`() {
        api.add = { failure(409, "CLASS_FULL") }
        val viewModel = viewModel()

        viewModel.submit("K7M2QX4R", "Amelia Zahra", "0012345678")
        viewModel.submit(" k7m2qx4r ", " Amelia Zahra ", " 0012345678 ")

        assertEquals(api.addKeys[0], api.addKeys[1])
    }

    @Test
    fun `a key that the server says was used for another request is replaced`() {
        api.add = { failure(422, "IDEMPOTENCY_KEY_REUSED") }
        val viewModel = viewModel()

        viewModel.submitValid()
        assertEquals(R.string.error_key_reused, viewModel.state.value.error)

        api.add = { ok(summary("new")) }
        viewModel.submitValid()

        assertNotEquals(api.addKeys[0], api.addKeys[1])
        assertEquals("new", viewModel.state.value.added?.id)
    }

    @Test
    fun `after a success the next child gets a new key`() {
        val viewModel = viewModel()

        viewModel.submitValid()
        viewModel.submitValid()

        assertNotEquals(api.addKeys[0], api.addKeys[1])
    }

    @Test
    fun `the general error is shown once`() {
        api.add = { failure(409, "CHILD_LIMIT_REACHED") }
        val viewModel = viewModel()
        viewModel.submitValid()

        viewModel.errorShown()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `an added child keeps the data of the server`() {
        api.add = { ok(ChildSummary("n1", "Amelia Zahra", "0012345678", null, "c2", "Mawar", true, false)) }
        val viewModel = viewModel()

        viewModel.submitValid()

        assertEquals("Mawar", viewModel.state.value.added?.className)
        assertTrue(viewModel.state.value.added!!.nisnVerified)
    }
}
