package com.nthb.kibooz.ui.chat

import androidx.annotation.StringRes
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatConversation

/** Satu baris di layar daftar pesan. [key] membedakan baris untuk DiffUtil. */
sealed interface ChatListRow {
    val key: String
}

data class SectionRow(
    @StringRes val title: Int,
    val filterClasses: List<ChatClassOption> = emptyList(),
    val filterContacts: List<ChatContact> = emptyList(),
    val selectedClassId: String? = null
) : ChatListRow {
    override val key: String get() = "s:$title"
}

data class EmptyFilterRow(@StringRes val message: Int) : ChatListRow {
    override val key: String get() = "u:empty"
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

/** Menggabungkan kelas aktif dan kelas yang muncul di kontak agar setiap kontak terjangkau di filter dropdown. */
fun contactFilterClasses(starters: ChatStarters): List<ChatClassOption> {
    val merged = linkedMapOf<String, ChatClassOption>()
    starters.classes.forEach { merged[it.id] = it }
    starters.contacts.forEach { contact ->
        contact.classes.forEach { cls ->
            merged.putIfAbsent(cls.id, ChatClassOption(cls.id, cls.name))
        }
    }
    return merged.values.toList()
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
            val filterClasses = contactFilterClasses(starters)
            val selectedClassId = state.selectedContactClassId?.takeIf { id -> filterClasses.any { it.id == id } }
            val classOrder = filterClasses.mapIndexed { index, option -> option.id to index }.toMap()

            rows += SectionRow(
                title = R.string.chat_bagian_kontak,
                filterClasses = filterClasses,
                filterContacts = starters.contacts,
                selectedClassId = selectedClassId
            )

            val visible = if (selectedClassId == null) {
                if (filterClasses.size > 1) {
                    starters.contacts.sortedBy { contact ->
                        contact.classes.minOfOrNull { classOrder[it.id] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
                    }
                } else {
                    starters.contacts
                }
            } else {
                starters.contacts.filter { contact -> contact.classes.any { it.id == selectedClassId } }
            }

            if (visible.isEmpty()) {
                rows += EmptyFilterRow(R.string.chat_kontak_kelas_kosong)
            } else {
                visible.forEach { rows += ContactRow(it, state.opening == openingKey(it)) }
            }
        }
        rows
    }
}
