package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.AccountApi
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.AvatarData
import com.nthb.kibooz.data.remote.DeleteAccountRequest
import com.nthb.kibooz.data.remote.EmptyData
import com.nthb.kibooz.data.remote.GuruProfile
import com.nthb.kibooz.data.remote.GuruProfileApi
import com.nthb.kibooz.data.remote.GuruProfileDetail
import com.nthb.kibooz.data.remote.UpdateGuruProfileRequest
import com.nthb.kibooz.data.repository.AccountRepository
import com.nthb.kibooz.data.repository.GuruProfileRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class GuruProfileViewModelTest {

    private class FakeGuruProfileApi(var current: GuruProfileDetail) : GuruProfileApi {
        val updates = mutableListOf<UpdateGuruProfileRequest>()

        override suspend fun profile(): Response<ApiEnvelope<GuruProfile>> = error("unused")

        override suspend fun profileDetail(): Response<ApiEnvelope<GuruProfileDetail>> =
            Response.success(ApiEnvelope(true, null, current))

        override suspend fun updateProfile(body: UpdateGuruProfileRequest): Response<ApiEnvelope<GuruProfileDetail>> {
            updates += body
            current = current.copy(
                fullName = body.fullName ?: current.fullName,
                phoneNumber = body.phoneNumber?.ifEmpty { null } ?: if (body.phoneNumber == "") null else current.phoneNumber,
                address = body.address?.ifEmpty { null } ?: if (body.address == "") null else current.address
            )
            return Response.success(ApiEnvelope(true, "ok", current))
        }
    }

    private class FakeAccountApi : AccountApi {
        var uploadCalls = 0
        override suspend fun deleteAccount(body: DeleteAccountRequest): Response<ApiEnvelope<EmptyData>> = error("unused")
        override suspend fun uploadAvatar(
            file: MultipartBody.Part,
            studentId: RequestBody?
        ): Response<ApiEnvelope<AvatarData>> {
            uploadCalls++
            return Response.success(ApiEnvelope(true, null, AvatarData("https://cdn.example/guru-new.jpg")))
        }
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val initialDetail = GuruProfileDetail(
        id = "g1",
        fullName = "Ibu Siti Aminah",
        nip = "198705152010012001",
        schoolName = "TK Harapan Bangsa",
        avatarUrl = null,
        email = "guru.kibooz1@gmail.com",
        phoneNumber = "081234567890",
        address = "Jl. Melati No. 5",
        joinedAt = "2026-08-01T07:00:00Z"
    )

    private lateinit var guruApi: FakeGuruProfileApi
    private lateinit var accountApi: FakeAccountApi
    private var synced: Pair<String?, String?>? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        guruApi = FakeGuruProfileApi(initialDetail)
        accountApi = FakeAccountApi()
        synced = null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = GuruProfileViewModel(
        guruProfileRepository = GuruProfileRepository(guruApi, moshi),
        accountRepository = AccountRepository(accountApi, moshi),
        onSessionSync = { fullName, avatarUrl -> synced = fullName to avatarUrl }
    )

    @Test
    fun `init loads profile detail`() {
        val model = vm()
        assertFalse(model.state.value.loading)
        assertEquals("Ibu Siti Aminah", model.state.value.profile?.fullName)
        assertEquals("198705152010012001", model.state.value.profile?.nip)
    }

    @Test
    fun `invalid fields set fieldErrors without calling API`() {
        val model = vm()
        model.saveProfile("   ", "12", "Jl. Melati")
        val errors = model.state.value.fieldErrors
        assertEquals(R.string.anak_form_nama_wali_kosong, errors[GuruEditField.FULL_NAME])
        assertEquals(R.string.anak_form_telepon_tidak_valid, errors[GuruEditField.PHONE])
        assertTrue(guruApi.updates.isEmpty())
    }

    @Test
    fun `unchanged values report anak_tidak_ada_perubahan without request`() {
        val model = vm()
        model.saveProfile("Ibu Siti Aminah", "081234567890", "Jl. Melati No. 5")
        assertEquals(R.string.anak_tidak_ada_perubahan, model.state.value.message)
        assertTrue(guruApi.updates.isEmpty())
        assertFalse(model.state.value.changed)
    }

    @Test
    fun `saveProfile sends only modified fields and syncs session`() {
        val model = vm()
        model.saveProfile("Ibu Siti M.Pd.", "081234567890", "Jl. Melati No. 12")

        assertEquals(1, guruApi.updates.size)
        val sent = guruApi.updates.single()
        assertEquals("Ibu Siti M.Pd.", sent.fullName)
        assertNull(sent.phoneNumber)
        assertEquals("Jl. Melati No. 12", sent.address)

        assertTrue(model.state.value.changed)
        assertEquals(R.string.guru_profil_simpan_sukses, model.state.value.message)
        assertEquals("Ibu Siti M.Pd." to null, synced)
    }

    @Test
    fun `uploadAvatar updates avatarUrl and syncs session`() {
        val model = vm()
        val temp = File.createTempFile("guru-avatar-", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            deleteOnExit()
        }

        model.uploadAvatar(temp)

        assertEquals(1, accountApi.uploadCalls)
        assertEquals("https://cdn.example/guru-new.jpg", model.state.value.profile?.avatarUrl)
        assertEquals(null to "https://cdn.example/guru-new.jpg", synced)
        assertEquals(R.string.avatar_sukses, model.state.value.message)
        assertTrue(model.state.value.changed)
    }
}
