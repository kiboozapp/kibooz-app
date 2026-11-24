package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.Guardian
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.UpdateChildRequest
import com.nthb.kibooz.data.remote.UpdateWaliProfileRequest
import com.nthb.kibooz.ui.wali.ChildFixtures.failure
import com.nthb.kibooz.ui.wali.ChildFixtures.offline
import com.nthb.kibooz.ui.wali.ChildFixtures.ok
import com.nthb.kibooz.ui.wali.ChildFixtures.profile
import com.nthb.kibooz.ui.wali.ChildFixtures.repositoryOf
import com.nthb.kibooz.ui.wali.ChildFixtures.summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfilViewModelTest {

    private val api = FakeWaliProfileApi()

    private fun viewModel() = EditProfilViewModel(repositoryOf(api), "s1")

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api.child = { id -> ok(profile(id, name = "Amelia", nisn = "0012345678")) }
        api.update = { id, body -> ok(summary(id, name = body.fullName ?: "Amelia", nisn = body.nisn ?: "0012345678")) }
        api.profile = { body ->
            ok(
                Guardian(
                    body.fullName ?: "Citra",
                    "citra@example.org",
                    body.phoneNumber?.ifEmpty { null } ?: "0812 3456",
                    body.whatsappNumber?.ifEmpty { null },
                    body.address?.ifEmpty { null } ?: "Jl. Contoh 1"
                )
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `it loads the child with the data of the parent`() {
        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals("Amelia", state.profile?.fullName)
        assertEquals("Citra", state.profile?.guardian?.fullName)
        assertNull(state.loadError)
        assertFalse(state.gone)
    }

    @Test
    fun `a child that is gone ends the screen with the gone flag`() {
        api.child = { failure(404, "STUDENT_NOT_FOUND") }

        val state = viewModel().state.value

        assertTrue(state.gone)
        assertEquals(R.string.anak_error_tidak_ditemukan, state.loadError)
        assertNull(state.profile)
    }

    @Test
    fun `an offline load shows the offline card`() {
        api.child = { offline() }

        val state = viewModel().state.value

        assertEquals(OutageKind.OFFLINE, state.outage)
        assertEquals(R.string.error_network, state.loadError)
        assertFalse(state.gone)
    }

    // Anak

    @Test
    fun `a short name and a bad NISN are refused before the request`() {
        val viewModel = viewModel()

        viewModel.saveChild("A", "12ab")

        val state = viewModel.state.value
        assertEquals(R.string.form_nama_terlalu_pendek, state.fieldErrors[EditField.CHILD_NAME])
        assertEquals(R.string.form_nisn_tidak_valid, state.fieldErrors[EditField.NISN])
        assertTrue(api.updateRequests.isEmpty())
    }

    @Test
    fun `an empty NISN is refused for a child that has one`() {
        val viewModel = viewModel()

        viewModel.saveChild("Amelia Baru", "")

        assertEquals(R.string.form_nisn_tidak_valid, viewModel.state.value.fieldErrors[EditField.NISN])
        assertTrue(api.updateRequests.isEmpty())
    }

    @Test
    fun `nothing changed sends nothing`() {
        val viewModel = viewModel()

        viewModel.saveChild(" Amelia ", "0012345678")

        assertEquals(R.string.anak_tidak_ada_perubahan, viewModel.state.value.message)
        assertTrue(api.updateRequests.isEmpty())
        assertFalse(viewModel.state.value.changed)
    }

    @Test
    fun `only the changed name is sent`() {
        val viewModel = viewModel()

        viewModel.saveChild("Amelia Baru", "0012345678")

        assertEquals(listOf(UpdateChildRequest(fullName = "Amelia Baru")), api.updateRequests)
        val state = viewModel.state.value
        assertEquals("Amelia Baru", state.profile?.fullName)
        assertEquals(R.string.anak_data_tersimpan, state.message)
        assertTrue(state.changed)
        assertFalse(state.savingChild)
    }

    @Test
    fun `only the changed NISN is sent`() {
        val viewModel = viewModel()

        viewModel.saveChild("Amelia", " 0099887766 ")

        assertEquals(listOf(UpdateChildRequest(nisn = "0099887766")), api.updateRequests)
        assertEquals("0099887766", viewModel.state.value.profile?.nisn)
    }

    @Test
    fun `both fields go in one request`() {
        val viewModel = viewModel()

        viewModel.saveChild("Amelia Baru", "0099887766")

        assertEquals(listOf(UpdateChildRequest("Amelia Baru", "0099887766")), api.updateRequests)
    }

    @Test
    fun `a child that waits for a NISN can be renamed without one and can enter one`() {
        api.child = { id -> ok(profile(id, name = "Amelia", nisn = "", needsUpdate = true)) }
        api.update = { id, body -> ok(summary(id, body.fullName ?: "Amelia", body.nisn.orEmpty(), needsUpdate = body.nisn == null)) }
        val viewModel = viewModel()

        viewModel.saveChild("Amelia Baru", "")
        assertEquals(listOf(UpdateChildRequest(fullName = "Amelia Baru")), api.updateRequests)
        assertTrue(viewModel.state.value.profile!!.nisnNeedsUpdate)

        api.update = { id, body -> ok(summary(id, name = "Amelia Baru", nisn = body.nisn.orEmpty(), needsUpdate = false)) }
        viewModel.saveChild("Amelia Baru", "0055443322")

        assertEquals(UpdateChildRequest(nisn = "0055443322"), api.updateRequests.last())
        assertFalse(viewModel.state.value.profile!!.nisnNeedsUpdate)
        assertEquals("0055443322", viewModel.state.value.profile?.nisn)
    }

    @Test
    fun `an empty form with a waiting NISN still needs a valid number to send the NISN`() {
        api.child = { id -> ok(profile(id, name = "Amelia", nisn = "", needsUpdate = true)) }
        val viewModel = viewModel()

        viewModel.saveChild("Amelia", "123")

        assertEquals(R.string.form_nisn_tidak_valid, viewModel.state.value.fieldErrors[EditField.NISN])
    }

    @Test
    fun `changing a verified NISN asks first and a new number drops the verification`() {
        api.child = { id -> ok(profile(id, name = "Amelia", nisn = "0012345678", verified = true)) }
        api.update = { id, body -> ok(summary(id, name = "Amelia", nisn = body.nisn ?: "0012345678", verified = false)) }
        val viewModel = viewModel()

        assertTrue(viewModel.nisnWillLoseVerification("0099887766"))
        assertFalse(viewModel.nisnWillLoseVerification(" 0012345678 "))
        assertFalse(viewModel.nisnWillLoseVerification(""))

        viewModel.saveChild("Amelia", "0099887766")

        assertFalse(viewModel.state.value.profile!!.nisnVerified)
        assertFalse(viewModel.nisnWillLoseVerification("0011223344"))
    }

    @Test
    fun `an unverified NISN never asks`() {
        assertFalse(viewModel().nisnWillLoseVerification("0099887766"))
    }

    @Test
    fun `a NISN that a teacher verified for another child is shown on the field`() {
        api.update = { _, _ -> failure(409, "NISN_ALREADY_REGISTERED") }
        val viewModel = viewModel()

        viewModel.saveChild("Amelia", "0099887766")

        val state = viewModel.state.value
        assertEquals(R.string.anak_error_nisn_terverifikasi, state.fieldErrors[EditField.NISN])
        assertNull(state.message)
        assertEquals("0012345678", state.profile?.nisn)
        assertFalse(state.savingChild)
        assertFalse(state.changed)
    }

    @Test
    fun `a child that was deleted elsewhere sets the gone flag`() {
        api.update = { _, _ -> failure(404, "STUDENT_NOT_FOUND") }
        val viewModel = viewModel()

        viewModel.saveChild("Amelia Baru", "0012345678")

        assertTrue(viewModel.state.value.gone)
        assertEquals(R.string.anak_error_tidak_ditemukan, viewModel.state.value.message)
    }

    @Test
    fun `a server validation error marks the fields it names`() {
        api.update = { _, _ -> failure(400, "VALIDATION_ERROR", mapOf("nisn" to "bad", "fullName" to "bad")) }
        val viewModel = viewModel()

        viewModel.saveChild("Amelia Baru", "0099887766")

        val errors = viewModel.state.value.fieldErrors
        assertEquals(R.string.form_nama_terlalu_pendek, errors[EditField.CHILD_NAME])
        assertEquals(R.string.form_nisn_tidak_valid, errors[EditField.NISN])
    }

    @Test
    fun `a validation error with no known field is a general message`() {
        api.update = { _, _ -> failure(400, "VALIDATION_ERROR", mapOf("body" to "bad")) }
        val viewModel = viewModel()

        viewModel.saveChild("Amelia Baru", "0012345678")

        assertEquals(R.string.error_validation, viewModel.state.value.message)
        assertTrue(viewModel.state.value.fieldErrors.isEmpty())
    }

    @Test
    fun `other errors of the child save become a message and keep the data`() {
        val cases = listOf(
            failure<com.nthb.kibooz.data.remote.ChildSummary>(429, "RATE_LIMITED") to R.string.error_rate_limited,
            failure<com.nthb.kibooz.data.remote.ChildSummary>(500, "INTERNAL_ERROR") to R.string.error_server,
            failure<com.nthb.kibooz.data.remote.ChildSummary>(401, "AUTH_TOKEN_INVALID") to R.string.error_session_ended
        )
        cases.forEach { (response, message) ->
            api.update = { _, _ -> response }
            val viewModel = viewModel()

            viewModel.saveChild("Amelia Baru", "0012345678")

            assertEquals(message, viewModel.state.value.message)
            assertEquals("Amelia", viewModel.state.value.profile?.fullName)
            assertFalse(viewModel.state.value.changed)
        }
    }

    @Test
    fun `an offline child save says there is no connection`() {
        api.update = { _, _ -> offline() }
        val viewModel = viewModel()

        viewModel.saveChild("Amelia Baru", "0012345678")

        assertEquals(R.string.error_network, viewModel.state.value.message)
        assertFalse(viewModel.state.value.savingChild)
    }

    // Wali

    @Test
    fun `an empty parent name and a bad phone are refused before the request`() {
        val viewModel = viewModel()

        viewModel.saveParent("   ", "12", "abc", "x".repeat(1001))

        val errors = viewModel.state.value.fieldErrors
        assertEquals(R.string.anak_form_nama_wali_kosong, errors[EditField.PARENT_NAME])
        assertEquals(R.string.anak_form_telepon_tidak_valid, errors[EditField.PHONE])
        assertEquals(R.string.anak_form_telepon_tidak_valid, errors[EditField.WHATSAPP])
        assertEquals(R.string.form_alamat_terlalu_panjang, errors[EditField.ADDRESS])
        assertTrue(api.profileRequests.isEmpty())
    }

    @Test
    fun `phone numbers follow the pattern of the server`() {
        listOf("+62 812-3456-7890", "(021) 555 1234", "081234", "0812 3456").forEach {
            assertNull(it, EditProfilRules.phone(it))
        }
        assertNull(EditProfilRules.phone(""))
        assertNull(EditProfilRules.phone("   "))
        listOf("12345", "+62", "abc123456", "0812-", "1".repeat(31), "+(+62)812345").forEach {
            assertEquals(it, R.string.anak_form_telepon_tidak_valid, EditProfilRules.phone(it))
        }
    }

    @Test
    fun `nothing changed sends nothing for the parent`() {
        val viewModel = viewModel()

        viewModel.saveParent("Citra", "0812 3456", "", "Jl. Contoh 1")

        assertEquals(R.string.anak_tidak_ada_perubahan, viewModel.state.value.message)
        assertTrue(api.profileRequests.isEmpty())
    }

    @Test
    fun `only the changed parent fields are sent`() {
        val viewModel = viewModel()

        viewModel.saveParent(" Citra Baru ", "0812 3456", "+62 813 0000 1111", "Jl. Contoh 1")

        assertEquals(
            listOf(UpdateWaliProfileRequest(fullName = "Citra Baru", whatsappNumber = "+62 813 0000 1111")),
            api.profileRequests
        )
        val state = viewModel.state.value
        assertEquals("Citra Baru", state.profile?.guardian?.fullName)
        assertEquals(R.string.anak_data_wali_tersimpan, state.message)
        assertTrue(state.changed)
        assertFalse(state.savingParent)
    }

    @Test
    fun `an empty text clears the phone and the address`() {
        api.profile = { ok(Guardian("Citra", "citra@example.org", null, null, null)) }
        val viewModel = viewModel()

        viewModel.saveParent("Citra", "", "", "  ")

        assertEquals(UpdateWaliProfileRequest(phoneNumber = "", address = ""), api.profileRequests.single())
        assertNull(viewModel.state.value.profile?.guardian?.phoneNumber)
        assertNull(viewModel.state.value.profile?.guardian?.address)
    }

    @Test
    fun `a server validation error names the phone field`() {
        api.profile = { failure(400, "VALIDATION_ERROR", mapOf("phoneNumber" to "bad", "whatsappNumber" to "bad")) }
        val viewModel = viewModel()

        viewModel.saveParent("Citra", "0812 9999", "0813 8888", "Jl. Contoh 1")

        val errors = viewModel.state.value.fieldErrors
        assertEquals(R.string.anak_form_telepon_tidak_valid, errors[EditField.PHONE])
        assertEquals(R.string.anak_form_telepon_tidak_valid, errors[EditField.WHATSAPP])
    }

    @Test
    fun `a server name error on the parent form marks the parent name`() {
        api.profile = { failure(400, "VALIDATION_ERROR", mapOf("fullName" to "is required")) }
        val viewModel = viewModel()

        viewModel.saveParent("Citra Baru", "0812 3456", "", "Jl. Contoh 1")

        assertEquals(R.string.anak_form_nama_wali_kosong, viewModel.state.value.fieldErrors[EditField.PARENT_NAME])
        assertNull(viewModel.state.value.fieldErrors[EditField.CHILD_NAME])
    }

    @Test
    fun `a missing parent profile and other errors become a message`() {
        val cases = listOf(
            failure<Guardian>(404, "PROFILE_NOT_FOUND") to R.string.anak_error_profil_tidak_ada,
            failure<Guardian>(429, "RATE_LIMITED") to R.string.error_rate_limited,
            failure<Guardian>(403, "AUTH_FORBIDDEN") to R.string.error_forbidden
        )
        cases.forEach { (response, message) ->
            api.profile = { response }
            val viewModel = viewModel()

            viewModel.saveParent("Citra Baru", "0812 3456", "", "Jl. Contoh 1")

            assertEquals(message, viewModel.state.value.message)
            assertEquals("Citra", viewModel.state.value.profile?.guardian?.fullName)
        }
    }

    @Test
    fun `an offline parent save says there is no connection`() {
        api.profile = { offline() }
        val viewModel = viewModel()

        viewModel.saveParent("Citra Baru", "0812 3456", "", "Jl. Contoh 1")

        assertEquals(R.string.error_network, viewModel.state.value.message)
        assertFalse(viewModel.state.value.savingParent)
    }

    @Test
    fun `a saved child does not touch the parent data`() {
        val viewModel = viewModel()

        viewModel.saveChild("Amelia Baru", "0012345678")

        assertEquals(sampleGuardian(), viewModel.state.value.profile?.guardian)
        assertTrue(api.profileRequests.isEmpty())
    }

    @Test
    fun `the message is shown once`() {
        val viewModel = viewModel()
        viewModel.saveChild("Amelia", "0012345678")

        viewModel.messageShown()

        assertNull(viewModel.state.value.message)
    }

    private fun sampleGuardian() = ChildFixtures.sampleGuardian
}
