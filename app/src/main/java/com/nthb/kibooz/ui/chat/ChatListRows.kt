package com.nthb.kibooz.ui.chat

import androidx.annotation.StringRes
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatConversation

/** Satu baris di layar daftar pesan. [key] membedakan baris untuk DiffUtil. */
sealed interface ChatListRow {
    val key: String
}

data class SectionRow(@StringRes val title: Int) : ChatListRow {
    override val key: String get() = "s:$title"
}

data class ConversationRow(val conversation: ChatConversation) : ChatListRow {
    override val key: String get() = "v:${conversation.id}"
}

data class ContactRow(val contact: ChatContact, val opening: Boolean) : ChatListRow {
    override val key: String get() = "u:${contact.id}"
}

data class ClassRow(val option: ChatClassOption, val opening: Boolean) : ChatListRow {
    override val key: String get() = "c:${option.id}"
}

/** Menyusun baris layar dari state: percakapan di tab pertama, ruang kelas lalu kontak di tab "Mulai baru". Fungsi murni. */
fun buildChatListRows(state: ChatListState): List<ChatListRow> = when (state.tab) {
    ChatListTab.CONVERSATIONS -> state.conversations.data.orEmpty().map { ConversationRow(it) }
    ChatListTab.NEW -> {
        val starters = state.starters?.data
        val rows = mutableListOf<ChatListRow>()

        if (starters != null && starters.classes.isNotEmpty()) {
            rows += SectionRow(R.string.chat_bagian_ruang_kelas)
            starters.classes.forEach { rows += ClassRow(it, state.opening == openingKey(it)) }
        }
        if (starters != null && starters.contacts.isNotEmpty()) {
            rows += SectionRow(R.string.chat_bagian_kontak)
            starters.contacts.forEach { rows += ContactRow(it, state.opening == openingKey(it)) }
        }
        rows
    }
}
