package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatContactList
import com.nthb.kibooz.data.remote.ChatConversationList
import com.nthb.kibooz.data.remote.ChildList
import com.nthb.kibooz.data.remote.ChildSummary
import com.nthb.kibooz.data.remote.ClassList
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.data.repository.ChatRepository
import com.nthb.kibooz.data.repository.ClassRepository
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.ui.chat.ChatFixtures.contact
import com.nthb.kibooz.ui.chat.ChatFixtures.direct
import com.nthb.kibooz.ui.chat.ChatFixtures.failure
import com.nthb.kibooz.ui.chat.ChatFixtures.ok
import com.nthb.kibooz.ui.chat.ChatFixtures.room
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    private val chatApi = FakeChatApi()
    private val classesApi = FakeClassesApi()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val repository = ChatRepository(chatApi, moshi)
    private val classes = ClassRepository(classesApi, moshi)

    private fun viewModel(role: Role? = Role.GURU) = ChatListViewModel(repository, classes, role, jitter = { 0L })

    private fun summary(id: String, name: String, archivedAt: String? = null) =
        ClassSummary(id, name, "Class A", "SD Kibooz", "2026/2027", "CODE$id", true, 10, "2026-07-01T00:00:00Z", archivedAt)

    private fun child(id: String, classId: String, className: String, archivedAt: String? = null) =
        ChildSummary(id, "Anak $id", "", null, classId, className, classArchivedAt = archivedAt)

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        chatApi.conversationsReply = { page ->
            ok(ChatConversationList(page, 50, 2, listOf(direct("a"), room("r"))))
        }
        chatApi.contactsReply = { page ->
            ok(ChatContactList(page, 50, 2, listOf(contact("u1", children = listOf("Rani")), contact("u2"))))
        }
        classesApi.classesReply = {
            ok(ClassList(1, 50, 3, listOf(summary("c1", "Bunga"), summary("c2", "Mawar"), summary("c3", "Melati", "2026-09-01T00:00:00Z"))))
        }
        classesApi.childrenReply = {
            ok(
                ChildList(
                    listOf(
                        child("k1", "c1", "Bunga"),
                        child("k2", "c1", "Bunga"),
                        child("k3", "c2", "Mawar", archivedAt = "2026-09-01T00:00:00Z")
                    )
                )
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Percakapan

    @Test
    fun `it loads the conversations on creation and does not load the new tab yet`() {
        val state = viewModel().state.value

        assertEquals(ChatListTab.CONVERSATIONS, state.tab)
        assertFalse(state.conversations.loading)
        assertEquals(listOf("a", "r"), state.conversations.data?.map { it.id })
        assertNull(state.starters)
        assertTrue(chatApi.contactPages.isEmpty())
    }

    @Test
    fun `an account with no conversation loads as empty`() {
        chatApi.conversationsReply = { page -> ok(ChatConversationList(page, 50, 0, emptyList())) }

        val state = viewModel().state.value

        assertEquals(emptyList<Any>(), state.conversations.data)
        assertNull(state.conversations.error)
    }

    @Test
    fun `offline shows the error and a countdown, and a retry that works clears both`() {
        chatApi.conversationsReply = { throw IOException("putus") }
        val viewModel = viewModel()

        var section = viewModel.state.value.conversations
        assertNull(section.data)
        assertFalse(section.loading)
        assertEquals(R.string.error_network, section.error)
        assertEquals(OutageKind.OFFLINE, section.outage?.kind)
        assertEquals(30L, section.outage?.waitSeconds)

        viewModel.retryAfterOutage()
        assertEquals(60L, viewModel.state.value.conversations.outage?.waitSeconds)

        chatApi.conversationsReply = { page -> ok(ChatConversationList(page, 50, 1, listOf(direct("a")))) }
        viewModel.refresh()
        section = viewModel.state.value.conversations
        assertEquals(listOf("a"), section.data?.map { it.id })
        assertNull(section.error)
        assertNull(section.outage)
    }

    @Test
    fun `a server error shows its message and no countdown`() {
        chatApi.conversationsReply = { failure(500, "INTERNAL_ERROR") }

        val section = viewModel().state.value.conversations

        assertEquals(R.string.error_server, section.error)
        assertNull(section.outage)
    }

    @Test
    fun `a refresh that fails keeps the list that was already on screen`() {
        val viewModel = viewModel()
        chatApi.conversationsReply = { throw IOException("putus") }

        viewModel.refresh()

        val section = viewModel.state.value.conversations
        assertEquals(listOf("a", "r"), section.data?.map { it.id })
        assertEquals(R.string.error_network, section.error)
        assertFalse(section.loading)
    }

    @Test
    fun `refreshing quietly replaces the list when it works and leaves it alone when it fails`() {
        val viewModel = viewModel()
        chatApi.conversationsReply = { page -> ok(ChatConversationList(page, 50, 1, listOf(direct("z", unread = 3)))) }

        viewModel.refreshQuietly()
        assertEquals(listOf("z"), viewModel.state.value.conversations.data?.map { it.id })
        assertEquals(3, viewModel.state.value.conversations.data?.single()?.unreadCount)

        chatApi.conversationsReply = { throw IOException("putus") }
        viewModel.refreshQuietly()
        assertEquals(listOf("z"), viewModel.state.value.conversations.data?.map { it.id })
        assertNull(viewModel.state.value.conversations.error)
    }

    @Test
    fun `refreshing quietly does nothing before there is a list to refresh`() {
        chatApi.conversationsReply = { throw IOException("putus") }
        val viewModel = viewModel()
        val calls = chatApi.conversationPages.size

        viewModel.refreshQuietly()

        assertEquals(calls, chatApi.conversationPages.size)
    }

    @Test
    fun `a tick reloads the list, and a rate limit stops further requests until Retry-After passed`() = runBlocking {
        var clock = 0L
        val viewModel = ChatListViewModel(repository, classes, Role.GURU, jitter = { 0L }, now = { clock })
        chatApi.conversationsReply = { _ -> failure(429, "RATE_LIMITED", retryAfter = "10") }

        assertFalse(viewModel.refreshOnTick())
        val calls = chatApi.conversationPages.size

        clock = 9_999L
        assertFalse(viewModel.refreshOnTick())
        viewModel.refreshQuietly()
        assertEquals(calls, chatApi.conversationPages.size)

        clock = 10_000L
        chatApi.conversationsReply = { page -> ok(ChatConversationList(page, 50, 1, listOf(direct("z")))) }
        assertTrue(viewModel.refreshOnTick())
        assertEquals(listOf("z"), viewModel.state.value.conversations.data?.map { it.id })
    }

    // Mulai baru

    @Test
    fun `the new tab loads the contacts and the active classes of a teacher when it is opened`() {
        val viewModel = viewModel(Role.GURU)

        viewModel.selectTab(ChatListTab.NEW)

        val starters = viewModel.state.value.starters
        assertFalse(starters!!.loading)
        assertEquals(listOf("u1", "u2"), starters.data?.contacts?.map { it.id })
        assertEquals(listOf(ChatClassOption("c1", "Bunga"), ChatClassOption("c2", "Mawar")), starters.data?.classes)
        assertEquals(ChatListTab.NEW, viewModel.state.value.tab)
    }

    @Test
    fun `a parent gets the classes of the children once each, and not an archived class`() {
        val viewModel = viewModel(Role.WALI)

        viewModel.selectTab(ChatListTab.NEW)

        assertEquals(listOf(ChatClassOption("c1", "Bunga")), viewModel.state.value.starters?.data?.classes)
    }

    @Test
    fun `an account with no known role gets contacts and no class rooms`() {
        val viewModel = viewModel(role = null)

        viewModel.selectTab(ChatListTab.NEW)

        assertTrue(viewModel.state.value.starters?.data?.classes?.isEmpty() == true)
        assertEquals(2, viewModel.state.value.starters?.data?.contacts?.size)
    }

    @Test
    fun `opening the new tab again does not load it again`() {
        val viewModel = viewModel()

        viewModel.selectTab(ChatListTab.NEW)
        viewModel.selectTab(ChatListTab.CONVERSATIONS)
        viewModel.selectTab(ChatListTab.NEW)

        assertEquals(listOf(1), chatApi.contactPages)
    }

    @Test
    fun `the refresh button reloads the tab that is open`() {
        val viewModel = viewModel()
        viewModel.selectTab(ChatListTab.NEW)
        val conversationCalls = chatApi.conversationPages.size

        viewModel.refresh()

        assertEquals(listOf(1, 1), chatApi.contactPages)
        assertEquals(conversationCalls, chatApi.conversationPages.size)
    }

    @Test
    fun `contacts that fail to load show the error on the new tab only`() {
        chatApi.contactsReply = { failure(500, "INTERNAL_ERROR") }
        val viewModel = viewModel()

        viewModel.selectTab(ChatListTab.NEW)

        val starters = viewModel.state.value.starters
        assertNull(starters?.data)
        assertEquals(R.string.error_server, starters?.error)
        assertNull(viewModel.state.value.conversations.error)
    }

    @Test
    fun `classes that fail to load fail the new tab as a whole, with a countdown when offline`() {
        classesApi.classesReply = { throw IOException("putus") }
        val viewModel = viewModel(Role.GURU)

        viewModel.selectTab(ChatListTab.NEW)

        val starters = viewModel.state.value.starters
        assertNull(starters?.data)
        assertEquals(R.string.error_network, starters?.error)
        assertEquals(OutageKind.OFFLINE, starters?.outage?.kind)
    }

    // Membuka percakapan

    @Test
    fun `a contact is opened by its user id and the conversation is handed to the screen once`() {
        chatApi.startReply = { ok(direct("cv5")) }
        val viewModel = viewModel()
        viewModel.selectTab(ChatListTab.NEW)

        viewModel.openContact(contact("u1"))

        assertEquals(1, chatApi.started.size)
        assertEquals("u1", chatApi.started[0].userId)
        assertNull(chatApi.started[0].classId)
        assertEquals("cv5", viewModel.state.value.opened?.id)
        assertNull(viewModel.state.value.opening)

        viewModel.openedHandled()
        assertNull(viewModel.state.value.opened)
    }

    @Test
    fun `a class room is opened by its class id`() {
        chatApi.startReply = { ok(room("rm")) }
        val viewModel = viewModel()

        viewModel.openClass(ChatClassOption("c1", "Bunga"))

        assertEquals("c1", chatApi.started.single().classId)
        assertNull(chatApi.started.single().userId)
        assertTrue(viewModel.state.value.opened?.isRoom == true)
    }

    @Test
    fun `a second tap while one is opening is ignored`() {
        val gate = CompletableDeferred<Unit>()
        chatApi.startGate = gate
        chatApi.startReply = { ok(direct("cv5")) }
        val viewModel = viewModel()

        viewModel.openContact(contact("u1"))
        assertEquals(openingKey(contact("u1")), viewModel.state.value.opening)

        viewModel.openContact(contact("u2"))
        viewModel.openClass(ChatClassOption("c1", "Bunga"))
        assertEquals(1, chatApi.started.size)

        gate.complete(Unit)
        assertNull(viewModel.state.value.opening)
        assertEquals("cv5", viewModel.state.value.opened?.id)
    }

    @Test
    fun `a contact that is no longer allowed shows why and reloads the new tab`() {
        chatApi.startReply = { failure(403, "CHAT_CONTACT_NOT_ALLOWED") }
        val viewModel = viewModel()
        viewModel.selectTab(ChatListTab.NEW)

        viewModel.openContact(contact("u1"))

        val state = viewModel.state.value
        assertEquals(R.string.error_chat_contact_not_allowed, state.notice)
        assertNull(state.opened)
        assertNull(state.opening)
        assertEquals(listOf(1, 1), chatApi.contactPages)

        viewModel.noticeShown()
        assertNull(viewModel.state.value.notice)
    }

    @Test
    fun `offline while opening shows the network message and lets the person tap again`() {
        chatApi.startReply = { throw IOException("putus") }
        val viewModel = viewModel()

        viewModel.openContact(contact("u1"))

        assertEquals(R.string.error_network, viewModel.state.value.notice)
        assertNull(viewModel.state.value.opening)

        chatApi.startReply = { ok(direct("cv5")) }
        viewModel.openContact(contact("u1"))
        assertNotNull(viewModel.state.value.opened)
    }

    @Test
    fun `a conversation from the list is opened without calling the server`() {
        val viewModel = viewModel()
        val conversation = viewModel.state.value.conversations.data!!.first()

        viewModel.openConversation(conversation)

        assertEquals(conversation, viewModel.state.value.opened)
        assertTrue(chatApi.started.isEmpty())
    }
}
