package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ClassTeacher
import com.nthb.kibooz.data.remote.EmptyData
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.repository.ClassMemberRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PengajarKelasViewModelTest {

    private fun teacher(id: String, self: Boolean = false, owner: Boolean = false) = ClassTeacher(
        id = id,
        userId = "u-$id",
        isSelf = self,
        fullName = "Guru $id",
        nip = null,
        avatarUrl = null,
        isOwner = owner,
        joinedAt = "2026-07-01T00:00:00Z"
    )

    private val me = teacher("me", self = true, owner = true)
    private val budi = teacher("budi")
    private val citra = teacher("citra")

    private val api = FakeClassMemberApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val repository = ClassMemberRepository(api, moshi)
    private val clock = TickPause()
    private var signOuts = 0

    private fun viewModel(classId: String = "c1", archived: Boolean = false) =
        PengajarKelasViewModel(repository, classId, archived, signOut = { signOuts++ }, pause = clock.pause)

    /** View model dengan dialog tambah guru yang sudah diisi. */
    private fun adding(email: String = "dewi@example.org"): PengajarKelasViewModel {
        val viewModel = viewModel()
        viewModel.openAddDialog()
        viewModel.setAddEmail(email)
        return viewModel
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api.teachers = listOf(me, budi, citra)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Daftar dan tombol yang boleh tampil

    @Test
    fun `it loads the teachers and the owner gets the owner actions`() {
        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals(listOf("me", "budi", "citra"), state.teachers?.map { it.id })
        assertEquals(listOf(TeacherAction.LEAVE), state.actionsFor(me))
        assertEquals(listOf(TeacherAction.MAKE_OWNER, TeacherAction.REMOVE), state.actionsFor(budi))
        assertEquals(budi, state.successor)
        assertTrue(state.canAdd)
    }

    @Test
    fun `a teacher who is not the owner can only leave and add`() {
        val self = teacher("me", self = true)
        val owner = teacher("budi", owner = true)
        api.teachers = listOf(owner, self)

        val state = viewModel().state.value

        assertEquals(listOf(TeacherAction.LEAVE), state.actionsFor(self))
        assertTrue(state.actionsFor(owner).isEmpty())
        assertNull(state.successor)
        assertTrue(state.canAdd)
    }

    @Test
    fun `the only teacher cannot leave`() {
        api.teachers = listOf(me)
        val viewModel = viewModel()

        assertTrue(viewModel.state.value.actionsFor(me).isEmpty())
        viewModel.remove("me")

        assertTrue(api.removedTeachers.isEmpty())
    }

    @Test
    fun `an archived class is read only`() {
        val viewModel = viewModel(archived = true)

        val state = viewModel.state.value
        assertFalse(state.canAdd)
        assertTrue(state.actionsFor(me).isEmpty())
        assertTrue(state.actionsFor(budi).isEmpty())

        viewModel.openAddDialog()
        viewModel.remove("budi")
        viewModel.makeOwner("budi")

        assertNull(viewModel.state.value.addEmail)
        assertTrue(api.removedTeachers.isEmpty())
        assertTrue(api.transferred.isEmpty())
    }

    @Test
    fun `a class that is gone closes the screen`() {
        api.listReply = { apiFailure(404, "CLASS_NOT_FOUND") }

        val state = viewModel().state.value

        assertEquals(ClassScreenEnd.LOST, state.end)
        assertFalse(state.loading)
    }

    @Test
    fun `a blank class id closes the screen without a request`() {
        val state = viewModel(classId = "").state.value

        assertEquals(ClassScreenEnd.LOST, state.end)
        assertEquals(0, api.listCalls)
    }

    @Test
    fun `a missing profile signs the account out`() {
        api.listReply = { apiFailure(404, "PROFILE_NOT_FOUND") }

        val state = viewModel().state.value

        assertEquals(ClassScreenEnd.ACCOUNT_GONE, state.end)
        assertEquals(1, signOuts)
    }

    @Test
    fun `a failed first load shows the error and the retry works`() {
        api.listReply = { apiFailure(500, "INTERNAL_ERROR") }
        val viewModel = viewModel()

        assertNull(viewModel.state.value.teachers)
        assertEquals(R.string.error_server, viewModel.state.value.loadError)
        assertNull(viewModel.state.value.outage)

        api.listReply = null
        viewModel.load()

        assertEquals(3, viewModel.state.value.teachers?.size)
        assertNull(viewModel.state.value.loadError)
    }

    @Test
    fun `offline and proxy answers start an outage countdown`() {
        api.listReply = { throw IOException("offline") }
        assertEquals(OutageKind.OFFLINE, viewModel().state.value.outage?.kind)

        for (status in 502..504) {
            api.listReply = { proxyFailure(status) }
            assertEquals(OutageKind.UNAVAILABLE, viewModel().state.value.outage?.kind)
        }
    }

    @Test
    fun `a rate limited load locks the retry until Retry-After passes`() {
        api.listReply = { apiFailure(429, "RATE_LIMITED", retryAfter = 2) }
        val viewModel = viewModel()

        assertEquals(R.string.error_rate_limited, viewModel.state.value.loadError)
        assertEquals(2, viewModel.state.value.cooldownSeconds)

        api.listReply = null
        viewModel.load()
        assertEquals(1, api.listCalls)

        clock.advance(2)
        assertNull(viewModel.state.value.cooldownSeconds)
        viewModel.load()
        assertEquals(2, api.listCalls)
        assertEquals(3, viewModel.state.value.teachers?.size)
    }

    // Keluarkan guru dan keluar dari kelas

    @Test
    fun `removing a teacher drops the row, says so and refreshes`() {
        val viewModel = viewModel()
        api.teachers = listOf(me, citra)

        viewModel.remove("budi")

        val state = viewModel.state.value
        assertEquals(listOf("budi"), api.removedTeachers)
        assertEquals(listOf("me", "citra"), state.teachers?.map { it.id })
        assertEquals(KelolaNotice(R.string.pengajar_hasil_dikeluarkan, listOf("Guru budi")), state.notice)
        assertNull(state.busyTeacherId)
        assertNull(state.end)
        assertEquals(2, api.listCalls)

        viewModel.noticeShown()
        assertNull(viewModel.state.value.notice)
    }

    @Test
    fun `leaving closes the screen without another list request`() {
        val viewModel = viewModel()

        viewModel.remove("me")

        assertEquals(listOf("me"), api.removedTeachers)
        assertEquals(ClassScreenEnd.LEFT, viewModel.state.value.end)
        assertEquals(1, api.listCalls)
    }

    @Test
    fun `a double tap sends one request`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        api.gate = gate

        viewModel.remove("budi")
        viewModel.remove("budi")
        viewModel.remove("citra")
        viewModel.makeOwner("citra")
        viewModel.openAddDialog()
        viewModel.load()

        assertEquals(listOf("budi"), api.removedTeachers)
        assertTrue(api.transferred.isEmpty())
        assertNull(viewModel.state.value.addEmail)
        assertEquals("budi", viewModel.state.value.busyTeacherId)
        assertEquals(1, api.listCalls)

        gate.complete(Unit)
        assertNull(viewModel.state.value.busyTeacherId)
    }

    @Test
    fun `CLASS_OWNER_REQUIRED shows the message and refreshes the buttons`() {
        val viewModel = viewModel()
        api.removeReply = { apiFailure(403, "CLASS_OWNER_REQUIRED") }
        val demoted = me.copy(isOwner = false)
        api.teachers = listOf(demoted, budi.copy(isOwner = true), citra)

        viewModel.remove("citra")

        val state = viewModel.state.value
        assertEquals(KelolaNotice(R.string.error_owner_required), state.notice)
        assertTrue(state.actionsFor(citra).isEmpty())
        assertNull(state.end)
    }

    @Test
    fun `TEACHER_NOT_IN_CLASS refreshes the list`() {
        val viewModel = viewModel()
        api.removeReply = { apiFailure(404, "TEACHER_NOT_IN_CLASS") }
        api.teachers = listOf(me, citra)

        viewModel.remove("budi")

        assertEquals(KelolaNotice(R.string.error_teacher_not_in_class), viewModel.state.value.notice)
        assertEquals(listOf("me", "citra"), viewModel.state.value.teachers?.map { it.id })
    }

    @Test
    fun `LAST_TEACHER keeps the screen open and refreshes`() {
        val viewModel = viewModel()
        api.removeReply = { apiFailure(409, "LAST_TEACHER") }
        api.teachers = listOf(me)

        viewModel.remove("me")

        val state = viewModel.state.value
        assertEquals(KelolaNotice(R.string.error_last_teacher), state.notice)
        assertNull(state.end)
        assertTrue(state.actionsFor(me).isEmpty())
    }

    @Test
    fun `CLASS_ARCHIVED turns the screen read only`() {
        val viewModel = viewModel()
        api.removeReply = { apiFailure(409, "CLASS_ARCHIVED") }

        viewModel.remove("budi")

        val state = viewModel.state.value
        assertTrue(state.archived)
        assertFalse(state.canAdd)
        assertEquals(KelolaNotice(R.string.error_class_archived), state.notice)
    }

    @Test
    fun `CLASS_NOT_FOUND on an action closes the screen`() {
        val viewModel = viewModel()
        api.removeReply = { apiFailure(404, "CLASS_NOT_FOUND") }

        viewModel.remove("budi")

        assertEquals(ClassScreenEnd.LOST, viewModel.state.value.end)
    }

    @Test
    fun `PROFILE_NOT_FOUND on an action signs the account out`() {
        val viewModel = viewModel()
        api.removeReply = { apiFailure(404, "PROFILE_NOT_FOUND") }

        viewModel.remove("budi")

        assertEquals(ClassScreenEnd.ACCOUNT_GONE, viewModel.state.value.end)
        assertEquals(1, signOuts)
    }

    @Test
    fun `AUTH_FORBIDDEN after the teacher was removed ends in a lost class`() {
        val viewModel = viewModel()
        api.removeReply = { apiFailure(403, "AUTH_FORBIDDEN") }
        api.listReply = { apiFailure(404, "CLASS_NOT_FOUND") }

        viewModel.remove("budi")

        assertEquals(ClassScreenEnd.LOST, viewModel.state.value.end)
    }

    @Test
    fun `a lost answer on leaving is settled by the refresh`() {
        val viewModel = viewModel()
        api.removeReply = { throw IOException("offline") }
        api.listReply = { apiFailure(404, "CLASS_NOT_FOUND") }

        viewModel.remove("me")

        assertEquals(ClassScreenEnd.LOST, viewModel.state.value.end)
    }

    @Test
    fun `network and server errors keep the list and show a message`() {
        val viewModel = viewModel()

        api.removeReply = { throw IOException("offline") }
        viewModel.remove("budi")
        assertEquals(KelolaNotice(R.string.error_network), viewModel.state.value.notice)

        api.removeReply = { proxyFailure(502) }
        viewModel.remove("budi")
        assertEquals(KelolaNotice(R.string.error_unavailable), viewModel.state.value.notice)

        api.removeReply = { apiFailure(500, "INTERNAL_ERROR") }
        viewModel.remove("budi")
        assertEquals(KelolaNotice(R.string.error_server), viewModel.state.value.notice)

        assertEquals(3, viewModel.state.value.teachers?.size)
        assertEquals(3, api.removedTeachers.size)
        assertFalse(viewModel.state.value.locked)
    }

    @Test
    fun `a rate limited action locks the buttons and does not refresh`() {
        val viewModel = viewModel()
        api.removeReply = { apiFailure(429, "RATE_LIMITED", retryAfter = 90) }

        viewModel.remove("budi")

        val state = viewModel.state.value
        assertEquals(KelolaNotice(R.string.pengajar_batas_percobaan, listOf("1 menit 30 detik")), state.notice)
        assertEquals(90, state.cooldownSeconds)
        assertTrue(state.locked)
        assertEquals(1, api.listCalls)

        viewModel.remove("budi")
        assertEquals(1, api.removedTeachers.size)

        clock.advance(90)
        assertFalse(viewModel.state.value.locked)
    }

    // Pindah pemilik

    @Test
    fun `handing the class over moves the owner mark and removes the owner actions`() {
        val viewModel = viewModel()
        api.transferReply = { ok(budi.copy(isOwner = true)) }
        api.teachers = listOf(me.copy(isOwner = false), budi.copy(isOwner = true), citra)

        viewModel.makeOwner("budi")

        val state = viewModel.state.value
        assertEquals(listOf("budi"), api.transferred)
        assertEquals(listOf("budi"), state.teachers?.filter { it.isOwner }?.map { it.id })
        assertTrue(state.actionsFor(citra).isEmpty())
        assertEquals(KelolaNotice(R.string.pengajar_hasil_pemilik, listOf("Guru budi")), state.notice)
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `handing the class over reports every error code`() {
        val cases = listOf(
            Triple(403, "CLASS_OWNER_REQUIRED", R.string.error_owner_required),
            Triple(404, "TEACHER_NOT_IN_CLASS", R.string.error_teacher_not_in_class),
            Triple(409, "CLASS_ARCHIVED", R.string.error_class_archived),
            Triple(403, "AUTH_FORBIDDEN", R.string.error_forbidden)
        )

        for ((status, code, message) in cases) {
            val viewModel = viewModel()
            api.transferReply = { apiFailure(status, code) }

            viewModel.makeOwner("budi")

            assertEquals(code, KelolaNotice(message), viewModel.state.value.notice)
            assertNull(viewModel.state.value.busyTeacherId)
        }

        val lost = viewModel()
        api.transferReply = { apiFailure(404, "CLASS_NOT_FOUND") }
        lost.makeOwner("budi")
        assertEquals(ClassScreenEnd.LOST, lost.state.value.end)
    }

    // Tambah guru

    @Test
    fun `adding a teacher sends the trimmed email, closes the dialog and refreshes`() {
        val dewi = teacher("dewi")
        api.addReply = { ok(dewi) }
        val viewModel = adding("  dewi@example.org ")
        api.teachers = api.teachers + dewi

        viewModel.submitAdd()

        val state = viewModel.state.value
        assertEquals(listOf("dewi@example.org"), api.addedEmails)
        assertNull(state.addEmail)
        assertFalse(state.adding)
        assertEquals(listOf("me", "budi", "citra", "dewi"), state.teachers?.map { it.id })
        assertEquals(KelolaNotice(R.string.pengajar_hasil_ditambahkan, listOf("Guru dewi")), state.notice)
        assertEquals(2, api.listCalls)
    }

    @Test
    fun `an invalid email is refused without a request`() {
        val viewModel = adding("")

        viewModel.submitAdd()
        assertEquals(R.string.form_email_kosong, viewModel.state.value.addError)

        viewModel.setAddEmail("bukan email")
        assertNull(viewModel.state.value.addError)
        viewModel.submitAdd()

        assertEquals(R.string.form_email_tidak_valid, viewModel.state.value.addError)
        assertTrue(api.addKeys.isEmpty())
    }

    @Test
    fun `a double tap adds once and the dialog cannot close while sending`() {
        api.addReply = { ok(teacher("dewi")) }
        val viewModel = adding()
        val gate = CompletableDeferred<Unit>()
        api.gate = gate

        viewModel.submitAdd()
        viewModel.submitAdd()
        viewModel.closeAddDialog()
        viewModel.setAddEmail("lain@example.org")
        viewModel.remove("budi")

        assertEquals(1, api.addKeys.size)
        assertEquals("dewi@example.org", viewModel.state.value.addEmail)
        assertTrue(viewModel.state.value.adding)
        assertTrue(api.removedTeachers.isEmpty())

        gate.complete(Unit)
        assertFalse(viewModel.state.value.adding)
        assertNull(viewModel.state.value.addEmail)
    }

    @Test
    fun `add errors stay in the dialog`() {
        val cases = listOf(
            Triple(404, "TEACHER_NOT_FOUND", R.string.pengajar_tidak_ditemukan),
            Triple(409, "TEACHER_ALREADY_IN_CLASS", R.string.error_teacher_already),
            Triple(409, "CLASS_LIMIT_REACHED", R.string.pengajar_batas_kelas),
            Triple(409, "IDEMPOTENCY_IN_PROGRESS", R.string.error_request_in_progress),
            Triple(422, "IDEMPOTENCY_KEY_REUSED", R.string.error_key_reused),
            Triple(400, "VALIDATION_ERROR", R.string.error_validation),
            Triple(500, "INTERNAL_ERROR", R.string.error_server)
        )
        val viewModel = adding()

        for ((status, code, message) in cases) {
            api.addReply = { apiFailure(status, code) }

            viewModel.submitAdd()

            assertEquals(code, message, viewModel.state.value.addError)
            assertEquals("dewi@example.org", viewModel.state.value.addEmail)
            assertFalse(viewModel.state.value.adding)
        }
    }

    @Test
    fun `the same email keeps its key and a new email or a reused key gets a new one`() {
        val viewModel = adding()
        api.addReply = { throw IOException("offline") }
        viewModel.submitAdd()
        assertEquals(R.string.error_network, viewModel.state.value.addError)
        viewModel.submitAdd()

        viewModel.setAddEmail("lain@example.org")
        api.addReply = { apiFailure(422, "IDEMPOTENCY_KEY_REUSED") }
        viewModel.submitAdd()
        viewModel.submitAdd()

        assertEquals(api.addKeys[0], api.addKeys[1])
        assertNotEquals(api.addKeys[1], api.addKeys[2])
        assertNotEquals(api.addKeys[2], api.addKeys[3])
    }

    @Test
    fun `removing a teacher drops the pending key`() {
        val viewModel = adding()
        api.addReply = { throw IOException("offline") }
        viewModel.submitAdd()
        viewModel.closeAddDialog()
        viewModel.remove("budi")

        viewModel.openAddDialog()
        viewModel.setAddEmail("dewi@example.org")
        viewModel.submitAdd()

        assertNotEquals(api.addKeys[0], api.addKeys[1])
    }

    @Test
    fun `a rate limited add is sent again with the same key after Retry-After`() {
        api.addReply = { apiFailure(429, "RATE_LIMITED", retryAfter = 2) }
        val viewModel = adding()

        viewModel.submitAdd()
        assertEquals(2, viewModel.state.value.addWaitSeconds)
        assertTrue(viewModel.state.value.adding)

        api.addReply = { ok(teacher("dewi")) }
        clock.advance(2)

        assertEquals(2, api.addKeys.size)
        assertEquals(api.addKeys[0], api.addKeys[1])
        assertNull(viewModel.state.value.addEmail)
        assertNull(viewModel.state.value.addWaitSeconds)
    }

    @Test
    fun `a long rate limit is not waited for and locks the actions`() {
        api.addReply = { apiFailure(429, "RATE_LIMITED", retryAfter = 600) }
        val viewModel = adding()

        viewModel.submitAdd()

        val state = viewModel.state.value
        assertEquals(R.string.error_rate_limited, state.addError)
        assertEquals(600, state.cooldownSeconds)
        assertFalse(state.adding)
        viewModel.submitAdd()
        assertEquals(1, api.addKeys.size)
    }

    @Test
    fun `the wait for a rate limit can be cancelled and nothing is sent again`() {
        api.addReply = { apiFailure(429, "RATE_LIMITED", retryAfter = 5) }
        val viewModel = adding()
        viewModel.submitAdd()

        viewModel.closeAddDialog()

        assertNull(viewModel.state.value.addEmail)
        assertFalse(viewModel.state.value.adding)
        assertEquals(5, viewModel.state.value.cooldownSeconds)

        clock.advance(10)
        assertEquals(1, api.addKeys.size)
        assertNull(viewModel.state.value.cooldownSeconds)
    }

    @Test
    fun `an add on a class that was archived or lost closes the dialog`() {
        api.addReply = { apiFailure(409, "CLASS_ARCHIVED") }
        val archived = adding()
        archived.submitAdd()
        assertNull(archived.state.value.addEmail)
        assertTrue(archived.state.value.archived)
        assertEquals(KelolaNotice(R.string.error_class_archived), archived.state.value.notice)

        api.addReply = { apiFailure(404, "CLASS_NOT_FOUND") }
        val lost = adding()
        lost.submitAdd()
        assertNull(lost.state.value.addEmail)
        assertEquals(ClassScreenEnd.LOST, lost.state.value.end)
        assertFalse(lost.state.value.adding)
    }

    @Test
    fun `AUTH_FORBIDDEN on add closes the dialog and shows the message`() {
        api.addReply = { apiFailure(403, "AUTH_FORBIDDEN") }
        val viewModel = adding()

        viewModel.submitAdd()

        assertNull(viewModel.state.value.addEmail)
        assertNotNull(viewModel.state.value.notice)
        assertNull(viewModel.state.value.end)
    }

    @Test
    fun `a remove that answers with an empty envelope succeeds`() {
        api.removeReply = { ok(EmptyData()) }
        val viewModel = viewModel()

        viewModel.remove("citra")

        assertEquals(listOf("citra"), api.removedTeachers)
        assertNull(viewModel.state.value.end)
    }
}
