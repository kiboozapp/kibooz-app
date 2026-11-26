package com.nthb.kibooz.ui.guru

import com.nthb.kibooz.R
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeluarkanSiswaViewModelTest {

    private val api = FakeClassMemberApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val clock = TickPause()
    private var signOuts = 0

    private fun viewModel() = KeluarkanSiswaViewModel(
        ClassMemberRepository(api, moshi),
        signOut = { signOuts++ },
        pause = clock.pause
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `removing a child drops it, asks for a reload and names it`() {
        val viewModel = viewModel()

        viewModel.remove("c1", "s1", "Adit")

        val state = viewModel.state.value
        assertEquals(listOf("c1" to "s1"), api.removedStudents)
        assertEquals("s1", state.removedStudentId)
        assertTrue(state.reload)
        assertEquals(KelolaNotice(R.string.peserta_hasil_dikeluarkan, listOf("Adit")), state.notice)
        assertNull(state.busyStudentId)
        assertNull(state.end)

        viewModel.rosterHandled()
        viewModel.noticeShown()
        assertNull(viewModel.state.value.removedStudentId)
        assertFalse(viewModel.state.value.reload)
        assertNull(viewModel.state.value.notice)
    }

    @Test
    fun `a double tap sends one request`() {
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        api.gate = gate

        viewModel.remove("c1", "s1", "Adit")
        viewModel.remove("c1", "s1", "Adit")
        viewModel.remove("c1", "s2", "Budi")

        assertEquals(1, api.removedStudents.size)
        assertEquals("s1", viewModel.state.value.busyStudentId)

        gate.complete(Unit)
        assertNull(viewModel.state.value.busyStudentId)
    }

    @Test
    fun `a blank class id sends nothing`() {
        viewModel().remove("", "s1", "Adit")

        assertTrue(api.removedStudents.isEmpty())
    }

    @Test
    fun `STUDENT_NOT_FOUND drops the stale row and reloads`() {
        api.removeStudentReply = { apiFailure(404, "STUDENT_NOT_FOUND") }
        val viewModel = viewModel()

        viewModel.remove("c1", "s1", "Adit")

        val state = viewModel.state.value
        assertEquals("s1", state.removedStudentId)
        assertTrue(state.reload)
        assertEquals(KelolaNotice(R.string.peserta_sudah_tidak_ada), state.notice)
    }

    @Test
    fun `CLASS_ARCHIVED stops further removals`() {
        api.removeStudentReply = { apiFailure(409, "CLASS_ARCHIVED") }
        val viewModel = viewModel()

        viewModel.remove("c1", "s1", "Adit")
        viewModel.remove("c1", "s2", "Budi")

        val state = viewModel.state.value
        assertTrue(state.archived)
        assertNull(state.removedStudentId)
        assertEquals(KelolaNotice(R.string.error_class_archived), state.notice)
        assertEquals(1, api.removedStudents.size)
    }

    @Test
    fun `CLASS_NOT_FOUND closes the class screens`() {
        api.removeStudentReply = { apiFailure(404, "CLASS_NOT_FOUND") }
        val viewModel = viewModel()

        viewModel.remove("c1", "s1", "Adit")

        assertEquals(ClassScreenEnd.LOST, viewModel.state.value.end)
        assertFalse(viewModel.state.value.reload)
        assertEquals(0, signOuts)
    }

    @Test
    fun `PROFILE_NOT_FOUND signs the account out`() {
        api.removeStudentReply = { apiFailure(404, "PROFILE_NOT_FOUND") }
        val viewModel = viewModel()

        viewModel.remove("c1", "s1", "Adit")

        assertEquals(ClassScreenEnd.ACCOUNT_GONE, viewModel.state.value.end)
        assertEquals(1, signOuts)
    }

    @Test
    fun `other errors keep the child, show a message and reload`() {
        val viewModel = viewModel()

        api.removeStudentReply = { apiFailure(403, "AUTH_FORBIDDEN") }
        viewModel.remove("c1", "s1", "Adit")
        assertEquals(KelolaNotice(R.string.error_forbidden), viewModel.state.value.notice)

        api.removeStudentReply = { apiFailure(400, "VALIDATION_ERROR") }
        viewModel.remove("c1", "s1", "Adit")
        assertEquals(KelolaNotice(R.string.error_validation), viewModel.state.value.notice)

        api.removeStudentReply = { apiFailure(500, "INTERNAL_ERROR") }
        viewModel.remove("c1", "s1", "Adit")
        assertEquals(KelolaNotice(R.string.error_server), viewModel.state.value.notice)

        for (status in 502..504) {
            api.removeStudentReply = { proxyFailure(status) }
            viewModel.remove("c1", "s1", "Adit")
            assertEquals(KelolaNotice(R.string.error_unavailable), viewModel.state.value.notice)
        }

        api.removeStudentReply = { throw IOException("offline") }
        viewModel.remove("c1", "s1", "Adit")
        assertEquals(KelolaNotice(R.string.error_network), viewModel.state.value.notice)

        assertNull(viewModel.state.value.removedStudentId)
        assertTrue(viewModel.state.value.reload)
        assertFalse(viewModel.state.value.locked)
        assertEquals(7, api.removedStudents.size)
    }

    @Test
    fun `a rate limit locks the buttons until Retry-After passes`() {
        api.removeStudentReply = { apiFailure(429, "RATE_LIMITED", retryAfter = 3) }
        val viewModel = viewModel()

        viewModel.remove("c1", "s1", "Adit")

        val state = viewModel.state.value
        assertEquals(KelolaNotice(R.string.pengajar_batas_percobaan, listOf("3 detik")), state.notice)
        assertEquals(3, state.cooldownSeconds)
        assertFalse(state.reload)
        viewModel.remove("c1", "s1", "Adit")
        assertEquals(1, api.removedStudents.size)

        clock.advance(3)
        assertFalse(viewModel.state.value.locked)
    }
}
