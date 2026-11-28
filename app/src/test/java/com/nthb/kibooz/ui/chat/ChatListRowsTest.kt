package com.nthb.kibooz.ui.chat

import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatContactClass
import com.nthb.kibooz.ui.chat.ChatFixtures.contact
import com.nthb.kibooz.ui.chat.ChatFixtures.direct
import com.nthb.kibooz.ui.chat.ChatFixtures.room
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListRowsTest {

    private val bunga = ChatClassOption(CLASS_1, "Bunga")
    private val mawar = ChatClassOption(CLASS_2, "Mawar")

    @Test
    fun `the first tab lists the conversations in the order the server gave`() {
        val state = ChatListState(
            conversations = ChatSection(loading = false, data = listOf(direct("b"), room("r"), direct("a")))
        )

        assertEquals(listOf("v:b", "v:r", "v:a"), buildChatListRows(state).map { it.key })
    }

    @Test
    fun `nothing loaded gives no rows`() {
        assertTrue(buildChatListRows(ChatListState()).isEmpty())
        assertTrue(buildChatListRows(ChatListState(tab = ChatListTab.NEW)).isEmpty())
    }

    @Test
    fun `the new tab lists the class rooms first and then the contacts, each under its own heading`() {
        val state = ChatListState(
            tab = ChatListTab.NEW,
            starters = ChatSection(
                loading = false,
                data = ChatStarters(listOf(contact(USER_1), contact(USER_2)), listOf(bunga, mawar))
            )
        )

        val rows = buildChatListRows(state)

        assertEquals(
            listOf(
                "s:${R.string.chat_bagian_ruang_kelas}",
                KEY_CLASS_1,
                "c:$CLASS_2",
                "s:${R.string.chat_bagian_kontak}",
                KEY_USER_1,
                KEY_USER_2
            ),
            rows.map { it.key }
        )
    }

    @Test
    fun `a heading is left out when its group is empty`() {
        val onlyContacts = ChatListState(
            tab = ChatListTab.NEW,
            starters = ChatSection(loading = false, data = ChatStarters(listOf(contact(USER_1)), emptyList()))
        )
        val onlyRooms = ChatListState(
            tab = ChatListTab.NEW,
            starters = ChatSection(loading = false, data = ChatStarters(emptyList(), listOf(bunga)))
        )

        assertEquals(listOf("s:${R.string.chat_bagian_kontak}", KEY_USER_1), buildChatListRows(onlyContacts).map { it.key })
        assertEquals(listOf("s:${R.string.chat_bagian_ruang_kelas}", KEY_CLASS_1), buildChatListRows(onlyRooms).map { it.key })
    }

    @Test
    fun `only the contact or class that is being opened shows the opening state`() {
        val state = ChatListState(
            tab = ChatListTab.NEW,
            starters = ChatSection(loading = false, data = ChatStarters(listOf(contact(USER_1), contact(USER_2)), listOf(bunga))),
            opening = openingKey(contact(USER_2))
        )

        val rows = buildChatListRows(state)

        assertFalse((rows.first { it.key == KEY_USER_1 } as ContactRow).opening)
        assertTrue((rows.first { it.key == KEY_USER_2 } as ContactRow).opening)
        assertFalse((rows.first { it.key == KEY_CLASS_1 } as ClassRow).opening)
    }

    @Test
    fun `a contact and a class with the same id do not share an opening key`() {
        assertFalse(openingKey(contact("x")) == openingKey(ChatClassOption("x", "Bunga")))
    }

    @Test
    fun `the conversation tab ignores what the new tab holds`() {
        val state = ChatListState(
            tab = ChatListTab.CONVERSATIONS,
            conversations = ChatSection(loading = false, data = listOf(direct("a"))),
            starters = ChatSection(loading = false, data = ChatStarters(listOf(contact(USER_1)), listOf(bunga)))
        )

        assertEquals(listOf("v:a"), buildChatListRows(state).map { it.key })
    }

    @Test
    fun `multi-class contacts group by class by default and filter cleanly when one class is picked`() {
        val waliMatahari = contact(USER_1, name = "Bapak Hendra", children = listOf("Adit"))
            .copy(classes = listOf(ChatContactClass(CLASS_1, NAME_MATAHARI)))
        val waliSakura = contact(USER_2, name = "Wali Kelas 2A", children = listOf("Calista"))
            .copy(classes = listOf(ChatContactClass(CLASS_2, "Kelas Sakura")))
        val waliMatahari2 = contact("u3", name = "Wali Kelas 1A", children = listOf("Aisyah"))
            .copy(classes = listOf(ChatContactClass(CLASS_1, NAME_MATAHARI)))
        val starters = ChatStarters(
            contacts = listOf(waliMatahari, waliSakura, waliMatahari2),
            classes = listOf(
                ChatClassOption(CLASS_1, NAME_MATAHARI),
                ChatClassOption(CLASS_2, "Kelas Sakura"),
                ChatClassOption("c3", "Kelas Kosong")
            )
        )

        val allState = ChatListState(tab = ChatListTab.NEW, starters = ChatSection(loading = false, data = starters))
        val allRows = buildChatListRows(allState)
        assertEquals(listOf(KEY_USER_1, "u:u3", KEY_USER_2), allRows.filterIsInstance<ContactRow>().map { it.key })

        val filtered = buildChatListRows(allState.copy(selectedContactClassId = CLASS_2))
        assertEquals(listOf(KEY_USER_2), filtered.filterIsInstance<ContactRow>().map { it.key })

        val emptyClass = buildChatListRows(allState.copy(selectedContactClassId = "c3"))
        assertTrue(emptyClass.any { it is EmptyFilterRow })
        assertTrue(emptyClass.any { it is SectionRow && it.title == R.string.chat_bagian_kontak })

        val singleUnselected = SectionRow(
            title = R.string.chat_bagian_kontak,
            filterClasses = listOf(ChatClassOption(CLASS_1, NAME_MATAHARI)),
            selectedClassId = null
        )
        val singleSelected = singleUnselected.copy(selectedClassId = CLASS_1)
        assertFalse(singleUnselected.shouldShowFilter())
        assertTrue(singleSelected.shouldShowFilter())
    }

    private companion object {
        const val CLASS_1 = "c1"
        const val KEY_CLASS_1 = "c:$CLASS_1"
        const val CLASS_2 = "c2"
        const val USER_1 = "u1"
        const val USER_2 = "u2"
        const val KEY_USER_1 = "u:u1"
        const val KEY_USER_2 = "u:u2"
        const val NAME_MATAHARI = "Kelas Matahari"
    }
}
