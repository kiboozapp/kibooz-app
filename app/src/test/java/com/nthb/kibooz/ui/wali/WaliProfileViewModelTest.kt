package com.nthb.kibooz.ui.wali

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.OutageKind
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
class WaliProfileViewModelTest {

    private val api = FakeWaliProfileApi()

    private fun viewModel(studentId: String? = null, name: String? = null) =
        WaliProfileViewModel(repositoryOf(api), studentId) { name }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        api.children = { ok(ChildList(listOf(summary("s1"), summary("s2")))) }
        api.child = { id -> ok(profile(id)) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `it loads the list and the oldest child when no child is named`() {
        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals(listOf("children", "child:s1"), api.calls)
        assertEquals("s1", state.profile?.id)
        assertEquals(2, state.children.size)
        assertEquals("Citra", state.parentName)
        assertNull(state.loadError)
        assertFalse(state.noChild)
    }

    @Test
    fun `a named child is loaded and a child that is gone falls back to the oldest`() {
        assertEquals("s2", viewModel("s2").state.value.profile?.id)

        api.calls.clear()
        assertEquals("s1", viewModel("deleted").state.value.profile?.id)
        assertEquals(listOf("children", "child:s1"), api.calls)
    }

    @Test
    fun `choosing another child loads that child and a reload keeps the choice`() {
        val viewModel = viewModel()

        viewModel.selectChild("s2")
        assertEquals("s2", viewModel.state.value.profile?.id)

        viewModel.load()
        assertEquals("s2", viewModel.state.value.profile?.id)
        assertEquals(listOf("children", "child:s1", "children", "child:s2", "children", "child:s2"), api.calls)
    }

    @Test
    fun `choosing the shown child again does not call the server`() {
        val viewModel = viewModel()

        viewModel.selectChild("s1")

        assertEquals(listOf("children", "child:s1"), api.calls)
    }

    @Test
    fun `a parent without children gets the empty state and the name of the session`() {
        api.children = { ok(ChildList(emptyList())) }

        val state = viewModel(name = "Citra Lestari").state.value

        assertTrue(state.noChild)
        assertNull(state.profile)
        assertNull(state.loadError)
        assertEquals("Citra Lestari", state.parentName)
        assertEquals(listOf("children"), api.calls)
    }

    @Test
    fun `adding a child is offered below 10 children and picking needs more than one`() {
        assertTrue(viewModel().state.value.canAddChild)
        assertTrue(viewModel().state.value.canPickChild)

        api.children = { ok(ChildList((1..10).map { summary("s$it") })) }
        assertFalse(viewModel().state.value.canAddChild)

        api.children = { ok(ChildList(listOf(summary("s1")))) }
        assertFalse(viewModel().state.value.canPickChild)
    }

    @Test
    fun `an offline list is an error with the offline card and no data`() {
        api.children = { offline() }

        val state = viewModel().state.value

        assertFalse(state.loading)
        assertEquals(R.string.error_network, state.loadError)
        assertEquals(OutageKind.OFFLINE, state.outage)
        assertNull(state.profile)
    }

    @Test
    fun `a failing child keeps the list and shows the error`() {
        api.child = { failure(500, "INTERNAL_ERROR") }

        val state = viewModel().state.value

        assertEquals(R.string.error_server, state.loadError)
        assertNull(state.outage)
        assertEquals(2, state.children.size)
        assertNull(state.profile)
    }

    @Test
    fun `the old profile stays when a reload of the same child goes offline`() {
        val viewModel = viewModel()
        api.children = { offline() }

        viewModel.load()

        assertEquals("s1", viewModel.state.value.profile?.id)
        assertEquals(R.string.error_network, viewModel.state.value.loadError)
    }

    @Test
    fun `the profile of another child is not shown under a new choice that failed`() {
        val viewModel = viewModel()
        api.child = { failure(404, "STUDENT_NOT_FOUND") }

        viewModel.selectChild("s2")

        assertNull(viewModel.state.value.profile)
        assertEquals(R.string.anak_error_tidak_ditemukan, viewModel.state.value.loadError)
    }

    @Test
    fun `deleting a child reloads the list and picks the oldest one that is left`() {
        val viewModel = viewModel()
        var left = listOf(summary("s1"), summary("s2"))
        api.children = { ok(ChildList(left)) }
        api.delete = { id ->
            left = left.filter { it.id != id }
            ok(com.nthb.kibooz.data.remote.EmptyData())
        }

        viewModel.deleteSelectedChild()

        val state = viewModel.state.value
        assertEquals(R.string.anak_dihapus, state.message)
        assertFalse(state.deleting)
        assertEquals("s2", state.profile?.id)
        assertEquals(1, state.children.size)
        assertEquals("delete:s1", api.calls.first { it.startsWith("delete") })
    }

    @Test
    fun `deleting the last child shows the empty state`() {
        val viewModel = viewModel()
        var left = listOf(summary("s1"))
        api.children = { ok(ChildList(left)) }
        api.delete = {
            left = emptyList()
            ok(com.nthb.kibooz.data.remote.EmptyData())
        }
        viewModel.load()

        viewModel.deleteSelectedChild()

        assertTrue(viewModel.state.value.noChild)
        assertNull(viewModel.state.value.profile)
        assertEquals(R.string.anak_dihapus, viewModel.state.value.message)
    }

    @Test
    fun `deleting a child that is already gone reloads and says so`() {
        val viewModel = viewModel()
        api.delete = { failure(404, "STUDENT_NOT_FOUND") }
        api.children = { ok(ChildList(listOf(summary("s2")))) }

        viewModel.deleteSelectedChild()

        assertEquals(R.string.anak_error_tidak_ditemukan, viewModel.state.value.message)
        assertEquals("s2", viewModel.state.value.profile?.id)
    }

    @Test
    fun `a failed delete keeps the child and shows the error`() {
        val viewModel = viewModel()
        api.delete = { failure(429, "RATE_LIMITED") }

        viewModel.deleteSelectedChild()

        val state = viewModel.state.value
        assertEquals(R.string.error_rate_limited, state.message)
        assertEquals("s1", state.profile?.id)
        assertFalse(state.deleting)
        assertEquals(1, api.calls.count { it == "children" })
    }

    @Test
    fun `an offline delete says there is no connection`() {
        val viewModel = viewModel()
        api.delete = { offline() }

        viewModel.deleteSelectedChild()

        assertEquals(R.string.error_network, viewModel.state.value.message)
        assertEquals("s1", viewModel.state.value.profile?.id)
    }

    @Test
    fun `the message is shown once`() {
        val viewModel = viewModel()
        api.delete = { failure(429, "RATE_LIMITED") }
        viewModel.deleteSelectedChild()

        viewModel.messageShown()

        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `nothing is deleted before a child is loaded`() {
        api.children = { offline() }
        val viewModel = viewModel()

        viewModel.deleteSelectedChild()

        assertTrue(api.calls.none { it.startsWith("delete") })
    }
}
