package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.data.remote.AddTeacherRequest
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ClassMemberApi
import com.nthb.kibooz.data.remote.ClassTeacher
import com.nthb.kibooz.data.remote.ClassTeacherList
import com.nthb.kibooz.data.remote.EmptyData
import com.nthb.kibooz.data.remote.TransferOwnershipRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** Server palsu untuk rute anggota kelas. [teachers] adalah daftar di sisi "server" yang boleh diubah tes. */
internal class FakeClassMemberApi(var teachers: List<ClassTeacher> = emptyList()) : ClassMemberApi {
    var listCalls = 0
    val addKeys = mutableListOf<String>()
    val addedEmails = mutableListOf<String>()
    val removedTeachers = mutableListOf<String>()
    val transferred = mutableListOf<String>()
    val removedStudents = mutableListOf<Pair<String, String>>()

    /** Jika diisi, permintaan yang mengubah data menunggu sampai gerbangnya dibuka. */
    var gate: CompletableDeferred<Unit>? = null

    var listReply: (() -> Response<ApiEnvelope<ClassTeacherList>>)? = null
    var addReply: () -> Response<ApiEnvelope<ClassTeacher>> = { error("not set") }
    var removeReply: () -> Response<ApiEnvelope<EmptyData>> = { ok(EmptyData()) }
    var transferReply: () -> Response<ApiEnvelope<ClassTeacher>> = { error("not set") }
    var removeStudentReply: () -> Response<ApiEnvelope<EmptyData>> = { ok(EmptyData()) }

    override suspend fun listTeachers(classId: String): Response<ApiEnvelope<ClassTeacherList>> {
        listCalls++
        return listReply?.invoke() ?: ok(ClassTeacherList(teachers))
    }

    override suspend fun addTeacher(
        classId: String,
        idempotencyKey: String,
        body: AddTeacherRequest
    ): Response<ApiEnvelope<ClassTeacher>> {
        addKeys += idempotencyKey
        addedEmails += body.email
        gate?.await()
        return addReply()
    }

    override suspend fun removeTeacher(classId: String, teacherId: String): Response<ApiEnvelope<EmptyData>> {
        removedTeachers += teacherId
        gate?.await()
        return removeReply()
    }

    override suspend fun transferOwnership(
        classId: String,
        body: TransferOwnershipRequest
    ): Response<ApiEnvelope<ClassTeacher>> {
        transferred += body.teacherId
        gate?.await()
        return transferReply()
    }

    override suspend fun removeStudent(classId: String, studentId: String): Response<ApiEnvelope<EmptyData>> {
        removedStudents += classId to studentId
        gate?.await()
        return removeStudentReply()
    }
}

/** Jeda palsu: setiap jeda satu detik menunggu sampai tes memanggil [advance], jadi tidak ada tidur sungguhan. */
internal class TickPause {
    private val ticks = Channel<Unit>(Channel.UNLIMITED)

    val pause: suspend (Long) -> Unit = { ticks.receive() }

    fun advance(seconds: Int = 1) {
        repeat(seconds) { ticks.trySend(Unit) }
    }
}

internal fun <T> ok(data: T): Response<ApiEnvelope<T>> = Response.success(ApiEnvelope(true, null, data))

internal fun <T> apiFailure(status: Int, errorCode: String, retryAfter: Long? = null): Response<ApiEnvelope<T>> {
    val body = """{"success":false,"message":"x","errorCode":"$errorCode"}"""
        .toResponseBody("application/json".toMediaType())
    val raw = okhttp3.Response.Builder()
        .request(okhttp3.Request.Builder().url("http://localhost/").build())
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(status)
        .message("error")
    if (retryAfter != null) raw.header("Retry-After", retryAfter.toString())
    return Response.error(body, raw.build())
}

/** Balasan dari proxy: bukan amplop JSON, jadi tanpa `errorCode`. */
internal fun <T> proxyFailure(status: Int): Response<ApiEnvelope<T>> =
    Response.error(status, "<html>proxy</html>".toResponseBody("text/html".toMediaType()))
