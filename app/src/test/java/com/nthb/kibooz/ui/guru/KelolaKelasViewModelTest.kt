package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ApiEnvelope
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ClassApi
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassStudentList
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.ClassTeacherList
import com.nthb.kibooz.data.remote.CreateClassRequest
import com.nthb.kibooz.data.remote.EmptyData
import com.nthb.kibooz.data.remote.GuruClassApi
import com.nthb.kibooz.data.remote.JoinCodeData
import com.nthb.kibooz.data.remote.JoiningData
import com.nthb.kibooz.data.remote.NisnVerificationData
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.remote.SetJoiningRequest
import com.nthb.kibooz.data.repository.ClassManagementRepository
import com.nthb.kibooz.data.repository.ClassRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class KelolaKelasViewModelTest {

    /** Kelas di sisi "server". Tes mengubahnya untuk meniru perubahan dari perangkat atau guru lain. */
    private class FakeClassApi(var classes: List<ClassSummary> = emptyList()) : ClassApi {
        var listCalls = 0
        var listReply: (() -> Response<ApiEnvelope<ClassList>>)? = null

        override suspend fun listClasses(page: Int, limit: Int): Response<ApiEnvelope<ClassList>> {
            listCalls++
            return listReply?.invoke() ?: Response.success(ApiEnvelope(true, null, ClassList(page, limit, classes.size, classes)))
        }

        override suspend fun listStudents(classId: String, page: Int, limit: Int): Response<ApiEnvelope<ClassStudentList>> =
            error("not used")

        override suspend fun listChildren(): Response<ApiEnvelope<ChildList>> = error("not used")
    }

    private class FakeGuruClassApi : GuruClassApi {
        val createKeys = mutableListOf<String>()
        val createRequests = mutableListOf<CreateClassRequest>()
        val rotated = mutableListOf<String>()
        val joining = mutableListOf<Pair<String, Boolean>>()
        val archived = mutableListOf<String>()
        val restored = mutableListOf<String>()

        var createReply: () -> Response<ApiEnvelope<ClassSummary>> = { error("not set") }
        var rotateReply: () -> Response<ApiEnvelope<JoinCodeData>> = { error("not set") }
        var joiningReply: (Boolean) -> Response<ApiEnvelope<JoiningData>> = { error("not set") }
        var archiveReply: () -> Response<ApiEnvelope<ClassSummary>> = { error("not set") }
        var restoreReply: () -> Response<ApiEnvelope<ClassSummary>> = { error("not set") }

        override suspend fun createClass(idempotencyKey: String, body: CreateClassRequest): Response<ApiEnvelope<ClassSummary>> {
            createKeys += idempotencyKey
            createRequests += body
            return createReply()
        }

        override suspend fun rotateJoinCode(classId: String): Response<ApiEnvelope<JoinCodeData>> {
            rotated += classId
            return rotateReply()
        }

        override suspend fun setJoining(classId: String, body: SetJoiningRequest): Response<ApiEnvelope<JoiningData>> {
            joining += classId to body.joinOpen
            return joiningReply(body.joinOpen)
        }

        override suspend fun archiveClass(classId: String): Response<ApiEnvelope<ClassSummary>> {
            archived += classId
            return archiveReply()
        }

        override suspend fun restoreClass(classId: String): Response<ApiEnvelope<ClassSummary>> {
            restored += classId
            return restoreReply()
        }

        override suspend fun verifyStudentNisn(classId: String, studentId: String): Response<ApiEnvelope<NisnVerificationData>> =
            error("not used")

        override suspend fun unverifyStudentNisn(classId: String, studentId: String): Response<ApiEnvelope<NisnVerificationData>> =
            error("not used")

        override suspend fun removeStudent(classId: String, studentId: String): Response<ApiEnvelope<EmptyData>> =
            error("not used")

        override suspend fun listClassTeachers(classId: String): Response<ApiEnvelope<ClassTeacherList>> =
            error("not used")
    }

    private val classApi = FakeClassApi()
    private val guruApi = FakeGuruClassApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val repository = ClassManagementRepository(ClassRepository(classApi, moshi), guruApi, moshi)

    private fun summary(
        id: String,
        name: String = "Kelas $id",
        joinCode: String = "CODE$id",
        open: Boolean = true,
        students: Int = 10,
        archivedAt: String? = null,
        grade: String = "Class A"
    ) = ClassSummary(id, name, grade, "SD Kibooz", "2026/2027", joinCode, open, students, "2026-07-01T00:00:00Z", archivedAt)

    private fun <T> ok(data: T): Response<ApiEnvelope<T>> = Response.success(ApiEnvelope(true, null, data))

    private fun <T> failure(status: Int, errorCode: String): Response<ApiEnvelope<T>> = Response.error(
        status,
        """{"success":false,"message":"x","errorCode":"$errorCode"}""".toResponseBody("application/json".toMediaType())
    )

    private fun viewModel() = KelolaKelasViewModel(repository)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        classApi.classes = listOf(summary("1"), summary("2", open = false), summary("3", archivedAt = "2026-09-01T00:00:00Z"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Daftar

    @Test
    fun `it loads the classes on creation and splits active from archived`() {
        val state = viewModel().state.value

        assertFalse(state.loading)
        assertNull(state.loadError)
        assertEquals(listOf("1", "2"), state.active.map { it.id })
        assertEquals(listOf("3"), state.archived.map { it.id })
        assertEquals(2, state.activeTotal)
        assertEquals(1, state.archivedTotal)
        assertFalse(state.empty)
    }

    @Test
    fun `a teacher without classes gets the empty state`() {
        classApi.classes = emptyList()

        val state = viewModel().state.value

        assertTrue(state.empty)
        assertFalse(state.noMatch)
        assertEquals(0, state.activeTotal)
    }

    @Test
    fun `a failed first load has no data, a mapped error and no add class until the retry works`() {
        classApi.listReply = { failure(403, "AUTH_FORBIDDEN") }
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.classes)
        assertEquals(R.string.error_forbidden, viewModel.state.value.loadError)
        assertFalse(viewModel.state.value.empty)

        viewModel.createClass("Bunga", "", "")
        assertTrue(guruApi.createKeys.isEmpty())

        classApi.listReply = null
        viewModel.load()

        assertEquals(3, viewModel.state.value.classes?.size)
        assertNull(viewModel.state.value.loadError)
    }

    @Test
    fun `being offline on the first load starts an outage countdown`() {
        classApi.listReply = { throw IOException("offline") }

        val state = viewModel().state.value

        assertEquals(R.string.error_network, state.loadError)
        assertEquals(OutageKind.OFFLINE, state.outage?.kind)
        assertEquals(30L, state.outage?.waitSeconds)
    }

    @Test
    fun `the old list stays when a reload fails`() {
        val viewModel = viewModel()
        classApi.listReply = { failure(500, "INTERNAL_ERROR") }

        viewModel.load()

        assertEquals(3, viewModel.state.value.classes?.size)
        assertEquals(R.string.error_server, viewModel.state.value.loadError)
    }

    @Test
    fun `the search matches name, grade and join code and ignores case`() {
        classApi.classes = listOf(
            summary("1", name = "Bunga Mawar", joinCode = "AAAA1111"),
            summary("2", name = "Melati", joinCode = "BBBB2222", grade = "Class B"),
            summary("3", name = "Dahlia", joinCode = "CCCC3333", archivedAt = "2026-09-01T00:00:00Z")
        )
        val viewModel = viewModel()

        viewModel.setQuery("  mawar ")
        assertEquals(listOf("1"), viewModel.state.value.active.map { it.id })

        viewModel.setQuery("bbbb")
        assertEquals(listOf("2"), viewModel.state.value.active.map { it.id })

        viewModel.setQuery("class b")
        assertEquals(listOf("2"), viewModel.state.value.active.map { it.id })

        viewModel.setQuery("dahlia")
        assertTrue(viewModel.state.value.active.isEmpty())
        assertEquals(listOf("3"), viewModel.state.value.archived.map { it.id })

        viewModel.setQuery("tidak ada")
        assertTrue(viewModel.state.value.noMatch)
        assertEquals(3, viewModel.state.value.classes?.size)

        viewModel.setQuery("")
        assertFalse(viewModel.state.value.noMatch)
        assertEquals(2, viewModel.state.value.active.size)
    }

    // Buat kelas

    @Test
    fun `creating a class sends the trimmed name with an idempotency key, adds it and refreshes`() {
        val created = summary("9", name = "Anggrek")
        guruApi.createReply = { ok(created) }
        val viewModel = viewModel()
        val listCallsBefore = classApi.listCalls
        classApi.classes = classApi.classes + created

        viewModel.createClass("  Anggrek ", "", "  ")

        assertEquals(listOf(CreateClassRequest("Anggrek")), guruApi.createRequests)
        assertEquals(1, guruApi.createKeys.size)
        val state = viewModel.state.value
        assertFalse(state.creating)
        assertTrue(state.createDone)
        assertNull(state.createError)
        assertTrue(state.classes!!.any { it.id == "9" })
        assertEquals(KelolaNotice(R.string.kelola_hasil_dibuat, listOf("Anggrek")), state.notice)
        assertEquals(listCallsBefore + 1, classApi.listCalls)
    }

    @Test
    fun `the grade level and academic year are sent when they are filled`() {
        guruApi.createReply = { ok(summary("9")) }
        val viewModel = viewModel()

        viewModel.createClass("Anggrek", " Class B ", "2027/2028")

        assertEquals(listOf(CreateClassRequest("Anggrek", "Class B", "2027/2028")), guruApi.createRequests)
    }

    @Test
    fun `a blank or too long field is refused without calling the server`() {
        val viewModel = viewModel()

        viewModel.createClass("   ", "", "")
        assertEquals(R.string.kelola_nama_wajib, viewModel.state.value.createError)

        viewModel.createClass("x".repeat(51), "", "")
        assertEquals(R.string.kelola_nama_panjang, viewModel.state.value.createError)

        viewModel.createClass("Bunga", "x".repeat(21), "")
        assertEquals(R.string.kelola_tingkat_panjang, viewModel.state.value.createError)

        viewModel.createClass("Bunga", "", "x".repeat(21))
        assertEquals(R.string.kelola_tahun_panjang, viewModel.state.value.createError)

        assertTrue(guruApi.createKeys.isEmpty())
        assertFalse(viewModel.state.value.createDone)
    }

    @Test
    fun `a name of exactly 50 characters is accepted`() {
        guruApi.createReply = { ok(summary("9")) }

        viewModel().createClass("x".repeat(50), "", "")

        assertEquals(1, guruApi.createKeys.size)
    }

    @Test
    fun `CLASS_LIMIT_REACHED on create tells the teacher to archive a class`() {
        guruApi.createReply = { failure(409, "CLASS_LIMIT_REACHED") }
        val viewModel = viewModel()

        viewModel.createClass("Bunga", "", "")

        val state = viewModel.state.value
        assertEquals(R.string.kelola_batas_buat, state.createError)
        assertFalse(state.createDone)
        assertFalse(state.creating)
        assertNull(state.notice)
    }

    @Test
    fun `a validation error from the server is shown in the dialog`() {
        guruApi.createReply = { failure(400, "VALIDATION_ERROR") }
        val viewModel = viewModel()

        viewModel.createClass("Bunga", "", "")

        assertEquals(R.string.error_validation, viewModel.state.value.createError)
    }

    @Test
    fun `other create errors use the shared messages`() {
        val viewModel = viewModel()

        guruApi.createReply = { failure(409, "IDEMPOTENCY_IN_PROGRESS") }
        viewModel.createClass("Bunga", "", "")
        assertEquals(R.string.error_request_in_progress, viewModel.state.value.createError)

        guruApi.createReply = { failure(429, "RATE_LIMITED") }
        viewModel.createClass("Bunga", "", "")
        assertEquals(R.string.error_rate_limited, viewModel.state.value.createError)

        guruApi.createReply = { throw IOException("offline") }
        viewModel.createClass("Bunga", "", "")
        assertEquals(R.string.error_network, viewModel.state.value.createError)
    }

    @Test
    fun `retrying the same class after a failure reuses the key and changed fields get a new key`() {
        guruApi.createReply = { throw IOException("offline") }
        val viewModel = viewModel()

        viewModel.createClass("Bunga", "", "")
        viewModel.createClass("Bunga", "", "")
        viewModel.createClass("Bunga Baru", "", "")

        assertEquals(guruApi.createKeys[0], guruApi.createKeys[1])
        assertNotEquals(guruApi.createKeys[0], guruApi.createKeys[2])
    }

    @Test
    fun `a class created after a success gets a fresh key`() {
        guruApi.createReply = { ok(summary("9")) }
        val viewModel = viewModel()

        viewModel.createClass("Bunga", "", "")
        viewModel.createClass("Bunga", "", "")

        assertNotEquals(guruApi.createKeys[0], guruApi.createKeys[1])
    }

    @Test
    fun `after IDEMPOTENCY_KEY_REUSED the next try uses a new key`() {
        guruApi.createReply = { failure(422, "IDEMPOTENCY_KEY_REUSED") }
        val viewModel = viewModel()

        viewModel.createClass("Bunga", "", "")
        viewModel.createClass("Bunga", "", "")

        assertEquals(R.string.error_key_reused, viewModel.state.value.createError)
        assertNotEquals(guruApi.createKeys[0], guruApi.createKeys[1])
    }

    @Test
    fun `the dialog state can be cleared and the created flag consumed`() {
        guruApi.createReply = { failure(409, "CLASS_LIMIT_REACHED") }
        val viewModel = viewModel()
        viewModel.createClass("Bunga", "", "")

        viewModel.clearCreateError()
        assertNull(viewModel.state.value.createError)

        guruApi.createReply = { ok(summary("9")) }
        viewModel.createClass("Bunga", "", "")
        assertTrue(viewModel.state.value.createDone)

        viewModel.createHandled()
        assertFalse(viewModel.state.value.createDone)

        viewModel.noticeShown()
        assertNull(viewModel.state.value.notice)
    }

    // Kode baru

    @Test
    fun `a new join code replaces the code in the list and the list is refreshed`() {
        guruApi.rotateReply = { ok(JoinCodeData("NEWCODE1")) }
        val viewModel = viewModel()
        val listCallsBefore = classApi.listCalls
        classApi.classes = classApi.classes.map { if (it.id == "1") it.copy(joinCode = "NEWCODE1", totalStudents = 14) else it }

        viewModel.rotateJoinCode("1")

        val state = viewModel.state.value
        assertEquals(listOf("1"), guruApi.rotated)
        assertNull(state.busyClassId)
        assertEquals(KelolaNotice(R.string.kelola_hasil_kode_baru, listOf("Kelas 1", "NEWCODE1")), state.notice)
        assertEquals("NEWCODE1", state.classes!!.first { it.id == "1" }.joinCode)
        assertEquals(14, state.classes!!.first { it.id == "1" }.totalStudents)
        assertEquals(listCallsBefore + 1, classApi.listCalls)
    }

    @Test
    fun `a new join code on a class that was archived meanwhile shows CLASS_ARCHIVED and refreshes`() {
        guruApi.rotateReply = { failure(409, "CLASS_ARCHIVED") }
        val viewModel = viewModel()
        classApi.classes = classApi.classes.map { if (it.id == "1") it.copy(archivedAt = "2026-10-01T00:00:00Z") else it }

        viewModel.rotateJoinCode("1")

        val state = viewModel.state.value
        assertEquals(KelolaNotice(R.string.error_class_archived), state.notice)
        assertNull(state.busyClassId)
        assertTrue(state.archived.any { it.id == "1" })
    }

    // Pendaftaran

    @Test
    fun `closing and opening joining use the value the server returns`() {
        guruApi.joiningReply = { open -> ok(JoiningData(open)) }
        val viewModel = viewModel()

        classApi.classes = classApi.classes.map { if (it.id == "1") it.copy(joinOpen = false) else it }
        viewModel.setJoining("1", open = false)

        assertEquals(listOf("1" to false), guruApi.joining)
        assertFalse(viewModel.state.value.classes!!.first { it.id == "1" }.joinOpen)
        assertEquals(KelolaNotice(R.string.kelola_hasil_ditutup, listOf("Kelas 1")), viewModel.state.value.notice)

        classApi.classes = classApi.classes.map { if (it.id == "1") it.copy(joinOpen = true) else it }
        viewModel.setJoining("1", open = true)

        assertEquals("1" to true, guruApi.joining.last())
        assertTrue(viewModel.state.value.classes!!.first { it.id == "1" }.joinOpen)
        assertEquals(KelolaNotice(R.string.kelola_hasil_dibuka, listOf("Kelas 1")), viewModel.state.value.notice)
    }

    @Test
    fun `joining on an archived class shows CLASS_ARCHIVED and a missing class shows CLASS_NOT_FOUND and is dropped`() {
        val viewModel = viewModel()

        guruApi.joiningReply = { failure(409, "CLASS_ARCHIVED") }
        viewModel.setJoining("1", open = false)
        assertEquals(KelolaNotice(R.string.error_class_archived), viewModel.state.value.notice)

        classApi.classes = classApi.classes.filterNot { it.id == "2" }
        guruApi.joiningReply = { failure(404, "CLASS_NOT_FOUND") }
        viewModel.setJoining("2", open = true)

        assertEquals(KelolaNotice(R.string.error_class_gone), viewModel.state.value.notice)
        assertFalse(viewModel.state.value.classes!!.any { it.id == "2" })
    }

    // Arsip

    @Test
    fun `archiving moves the class to the archived list`() {
        val archived = summary("1", archivedAt = "2026-10-01T00:00:00Z")
        guruApi.archiveReply = { ok(archived) }
        val viewModel = viewModel()
        val listCallsBefore = classApi.listCalls
        classApi.classes = classApi.classes.map { if (it.id == "1") archived else it }

        viewModel.archive("1")

        val state = viewModel.state.value
        assertEquals(listOf("1"), guruApi.archived)
        assertEquals(listOf("2"), state.active.map { it.id })
        assertEquals(listOf("1", "3"), state.archived.map { it.id })
        assertEquals(KelolaNotice(R.string.kelola_hasil_diarsipkan, listOf("Kelas 1")), state.notice)
        assertEquals(listCallsBefore + 1, classApi.listCalls)
    }

    @Test
    fun `a teacher who is not the owner gets the owner message and the list is left alone`() {
        guruApi.archiveReply = { failure(403, "CLASS_OWNER_REQUIRED") }
        val viewModel = viewModel()
        val listCallsBefore = classApi.listCalls

        viewModel.archive("1")

        val state = viewModel.state.value
        assertEquals(KelolaNotice(R.string.error_owner_required), state.notice)
        assertEquals(listOf("1", "2"), state.active.map { it.id })
        assertNull(state.busyClassId)
        assertEquals(listCallsBefore, classApi.listCalls)
    }

    @Test
    fun `archiving a class that is gone shows CLASS_NOT_FOUND and drops it from the list`() {
        guruApi.archiveReply = { failure(404, "CLASS_NOT_FOUND") }
        val viewModel = viewModel()
        classApi.classes = classApi.classes.filterNot { it.id == "1" }

        viewModel.archive("1")

        assertEquals(KelolaNotice(R.string.error_class_gone), viewModel.state.value.notice)
        assertEquals(listOf("2"), viewModel.state.value.active.map { it.id })
    }

    @Test
    fun `archiving a class that was archived already shows CLASS_ARCHIVED and refreshes`() {
        guruApi.archiveReply = { failure(409, "CLASS_ARCHIVED") }
        val viewModel = viewModel()
        classApi.classes = classApi.classes.map { if (it.id == "1") it.copy(archivedAt = "2026-10-01T00:00:00Z") else it }

        viewModel.archive("1")

        assertEquals(KelolaNotice(R.string.error_class_archived), viewModel.state.value.notice)
        assertTrue(viewModel.state.value.archived.any { it.id == "1" })
    }

    // Pulihkan

    @Test
    fun `restoring makes the class active again and tells the new code`() {
        val restored = summary("3", joinCode = "FRESH123")
        guruApi.restoreReply = { ok(restored) }
        val viewModel = viewModel()
        val listCallsBefore = classApi.listCalls
        classApi.classes = classApi.classes.map { if (it.id == "3") restored else it }

        viewModel.restore("3")

        val state = viewModel.state.value
        assertEquals(listOf("3"), guruApi.restored)
        assertEquals(listOf("1", "2", "3"), state.active.map { it.id })
        assertTrue(state.archived.isEmpty())
        assertEquals(KelolaNotice(R.string.kelola_hasil_dipulihkan, listOf("Kelas 3", "FRESH123")), state.notice)
        assertEquals(listCallsBefore + 1, classApi.listCalls)
    }

    @Test
    fun `restoring at the class limit explains that a teacher of the class has 20 active classes`() {
        guruApi.restoreReply = { failure(409, "CLASS_LIMIT_REACHED") }
        val viewModel = viewModel()
        val listCallsBefore = classApi.listCalls

        viewModel.restore("3")

        assertEquals(KelolaNotice(R.string.kelola_batas_pulihkan), viewModel.state.value.notice)
        assertEquals(listOf("3"), viewModel.state.value.archived.map { it.id })
        assertEquals(listCallsBefore, classApi.listCalls)
    }

    @Test
    fun `restoring a class that is not archived shows CLASS_NOT_ARCHIVED and refreshes`() {
        guruApi.restoreReply = { failure(409, "CLASS_NOT_ARCHIVED") }
        val viewModel = viewModel()
        classApi.classes = classApi.classes.map { if (it.id == "3") it.copy(archivedAt = null) else it }

        viewModel.restore("3")

        assertEquals(KelolaNotice(R.string.error_class_not_archived), viewModel.state.value.notice)
        assertTrue(viewModel.state.value.active.any { it.id == "3" })
    }

    @Test
    fun `restoring a class that is gone shows CLASS_NOT_FOUND`() {
        guruApi.restoreReply = { failure(404, "CLASS_NOT_FOUND") }
        val viewModel = viewModel()
        classApi.classes = classApi.classes.filterNot { it.id == "3" }

        viewModel.restore("3")

        assertEquals(KelolaNotice(R.string.error_class_gone), viewModel.state.value.notice)
        assertTrue(viewModel.state.value.archived.isEmpty())
    }

    // Semua aksi

    @Test
    fun `an action that cannot reach the server shows the network message and keeps the list`() {
        guruApi.rotateReply = { throw IOException("offline") }
        guruApi.joiningReply = { throw IOException("offline") }
        guruApi.archiveReply = { throw IOException("offline") }
        guruApi.restoreReply = { throw IOException("offline") }
        val viewModel = viewModel()
        val before = viewModel.state.value.classes

        viewModel.rotateJoinCode("1")
        assertEquals(KelolaNotice(R.string.error_network), viewModel.state.value.notice)
        viewModel.noticeShown()
        viewModel.setJoining("1", false)
        assertEquals(KelolaNotice(R.string.error_network), viewModel.state.value.notice)
        viewModel.noticeShown()
        viewModel.archive("1")
        assertEquals(KelolaNotice(R.string.error_network), viewModel.state.value.notice)
        viewModel.noticeShown()
        viewModel.restore("3")
        assertEquals(KelolaNotice(R.string.error_network), viewModel.state.value.notice)

        assertEquals(before, viewModel.state.value.classes)
        assertNull(viewModel.state.value.busyClassId)
    }

    @Test
    fun `a failed refresh after an action keeps the update that the answer gave`() {
        guruApi.rotateReply = { ok(JoinCodeData("NEWCODE1")) }
        val viewModel = viewModel()
        classApi.listReply = { throw IOException("offline") }

        viewModel.rotateJoinCode("1")

        val state = viewModel.state.value
        assertEquals("NEWCODE1", state.classes!!.first { it.id == "1" }.joinCode)
        assertNull(state.loadError)
        assertNotNull(state.notice)
    }

    @Test
    fun `an action for an unknown class does nothing`() {
        val viewModel = viewModel()

        viewModel.rotateJoinCode("zzz")
        viewModel.setJoining("zzz", true)
        viewModel.archive("zzz")
        viewModel.restore("zzz")

        assertTrue(guruApi.rotated.isEmpty() && guruApi.joining.isEmpty() && guruApi.archived.isEmpty() && guruApi.restored.isEmpty())
        assertNull(viewModel.state.value.notice)
    }

    @Test
    fun `an unexpected error code falls back to the status message`() {
        guruApi.archiveReply = { failure(500, "SOMETHING_NEW") }
        val viewModel = viewModel()

        viewModel.archive("1")

        assertEquals(KelolaNotice(R.string.error_unavailable), viewModel.state.value.notice)
    }
}
