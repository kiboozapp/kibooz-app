package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.data.remote.AddChildRequest
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ChildProfile
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.EmptyData
import com.nthb.kibooz.data.remote.Guardian
import com.nthb.kibooz.data.remote.UpdateChildRequest
import com.nthb.kibooz.data.remote.UpdateWaliProfileRequest
import com.nthb.kibooz.data.remote.WaliProfileApi
import com.nthb.kibooz.data.repository.WaliProfileRepository
import com.nthb.kibooz.ui.wali.ChildFixtures.ok
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** Server palsu untuk rute anak dan profil Wali. Setiap rute punya lambda yang bisa diganti per tes. */
class FakeWaliProfileApi : WaliProfileApi {

    var children: () -> Response<ApiEnvelope<ChildList>> = { ok(ChildList(emptyList())) }
    var child: (String) -> Response<ApiEnvelope<ChildProfile>> = { error("not set") }
    var add: (AddChildRequest) -> Response<ApiEnvelope<ChildSummary>> = { error("not set") }
    var update: (String, UpdateChildRequest) -> Response<ApiEnvelope<ChildSummary>> = { _, _ -> error("not set") }
    var delete: (String) -> Response<ApiEnvelope<EmptyData>> = { ok(EmptyData()) }
    var profile: (UpdateWaliProfileRequest) -> Response<ApiEnvelope<Guardian>> = { error("not set") }

    /** Urutan panggilan, misalnya `children`, `child:s1`, `delete:s1`. */
    val calls = mutableListOf<String>()
    val addKeys = mutableListOf<String>()
    val addRequests = mutableListOf<AddChildRequest>()
    val updateRequests = mutableListOf<UpdateChildRequest>()
    val profileRequests = mutableListOf<UpdateWaliProfileRequest>()

    override suspend fun listChildren(): Response<ApiEnvelope<ChildList>> {
        calls += "children"
        return children()
    }

    override suspend fun child(studentId: String): Response<ApiEnvelope<ChildProfile>> {
        calls += "child:$studentId"
        return child.invoke(studentId)
    }

    override suspend fun addChild(idempotencyKey: String, body: AddChildRequest): Response<ApiEnvelope<ChildSummary>> {
        calls += "add"
        addKeys += idempotencyKey
        addRequests += body
        return add(body)
    }

    override suspend fun updateChild(studentId: String, body: UpdateChildRequest): Response<ApiEnvelope<ChildSummary>> {
        calls += "update:$studentId"
        updateRequests += body
        return update(studentId, body)
    }

    override suspend fun deleteChild(studentId: String): Response<ApiEnvelope<EmptyData>> {
        calls += "delete:$studentId"
        return delete.invoke(studentId)
    }

    override suspend fun updateProfile(body: UpdateWaliProfileRequest): Response<ApiEnvelope<Guardian>> {
        calls += "profile"
        profileRequests += body
        return profile(body)
    }
}

/** Hasil bangun data dan balasan untuk tes anak dan profil Wali. Dikelompokkan supaya tidak bentrok dengan tes lain di package ini. */
object ChildFixtures {
    val testMoshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    fun repositoryOf(api: WaliProfileApi) = WaliProfileRepository(api, testMoshi)

    fun <T> ok(data: T): Response<ApiEnvelope<T>> = Response.success(ApiEnvelope(true, null, data))

    /** Balasan gagal dengan amplop JSON server. [details] berisi pasangan field dan pesan untuk `VALIDATION_ERROR`. */
    fun <T> failure(status: Int, errorCode: String, details: Map<String, String> = emptyMap()): Response<ApiEnvelope<T>> {
        val detailJson = if (details.isEmpty()) "" else ""","details":{${details.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }}}"""
        return Response.error(
            status,
            """{"success":false,"message":"x","errorCode":"$errorCode"$detailJson}""".toResponseBody("application/json".toMediaType())
        )
    }

    fun <T> offline(): Response<ApiEnvelope<T>> = throw IOException("offline")

    fun summary(
        id: String,
        name: String = "Anak $id",
        nisn: String = "00$id",
        verified: Boolean = false,
        needsUpdate: Boolean = false
    ) = ChildSummary(id, name, nisn, null, "c1", "Bunga", verified, needsUpdate)

    val sampleGuardian = Guardian("Citra", "citra@example.org", "0812 3456", null, "Jl. Contoh 1")

    fun profile(
        id: String,
        name: String = "Anak $id",
        nisn: String = "00$id",
        verified: Boolean = false,
        needsUpdate: Boolean = false,
        guardian: Guardian = sampleGuardian
    ) = ChildProfile(id, name, nisn, verified, needsUpdate, null, "c1", "Bunga", "TK A", "TK Pertiwi", "2026/2027", 100, 20, 2, guardian)
}
