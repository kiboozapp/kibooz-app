package com.nthb.kibooz.ui.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatConversation
import com.nthb.kibooz.databinding.ItemChatConversationBinding
import com.nthb.kibooz.databinding.ItemChatEmptyFilterBinding
import com.nthb.kibooz.databinding.ItemChatSectionBinding
import com.nthb.kibooz.databinding.ItemChatStarterBinding
import com.nthb.kibooz.util.AvatarLoader
import com.nthb.kibooz.util.ClassNames

/** Daftar percakapan, ruang kelas dan kontak. Satu adapter untuk dua tab karena barisnya berbeda jenis. */
class ChatListAdapter(
    private val avatarLoader: AvatarLoader,
    private val ownUserId: String,
    private val onConversation: (ChatConversation) -> Unit,
    private val onContact: (ChatContact) -> Unit,
    private val onClass: (ChatClassOption) -> Unit,
    private val onSelectContactClass: (String?) -> Unit = {}
) : ListAdapter<ChatListRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SectionRow -> TYPE_SECTION
        is EmptyFilterRow -> TYPE_EMPTY_FILTER
        is ConversationRow -> TYPE_CONVERSATION
        is ContactRow, is ClassRow -> TYPE_STARTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION -> SectionHolder(ItemChatSectionBinding.inflate(inflater, parent, false))
            TYPE_EMPTY_FILTER -> EmptyFilterHolder(ItemChatEmptyFilterBinding.inflate(inflater, parent, false))
            TYPE_CONVERSATION -> ConversationHolder(ItemChatConversationBinding.inflate(inflater, parent, false))
            else -> StarterHolder(ItemChatStarterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is SectionRow -> (holder as SectionHolder).bind(row)
            is EmptyFilterRow -> (holder as EmptyFilterHolder).bind(row)
            is ConversationRow -> (holder as ConversationHolder).bind(row.conversation)
            is ContactRow -> (holder as StarterHolder).bindContact(row)
            is ClassRow -> (holder as StarterHolder).bindClass(row)
        }
    }

    private class EmptyFilterHolder(private val binding: ItemChatEmptyFilterBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: EmptyFilterRow) {
            binding.tvEmptyFilter.setText(row.message)
        }
    }

    private inner class SectionHolder(private val binding: ItemChatSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: SectionRow) {
            val context = binding.root.context
            binding.tvSection.setText(row.title)

            val showFilter = row.filterClasses.size > 1
            binding.cardClassFilter.isVisible = showFilter
            if (!showFilter) {
                binding.cardClassFilter.setOnClickListener(null)
                return
            }

            val selectedName = row.filterClasses.firstOrNull { it.id == row.selectedClassId }?.name
                ?: context.getString(R.string.chat_semua_kelas)
            binding.tvClassFilter.text = "$selectedName ▾"
            binding.cardClassFilter.setOnClickListener {
                val menu = PopupMenu(context, binding.cardClassFilter)
                val allLabel = context.getString(R.string.chat_semua_kelas)
                menu.menu.add(0, 0, 0, context.getString(R.string.chat_filter_opsi_jumlah, allLabel, row.filterContacts.size))
                row.filterClasses.forEachIndexed { index, option ->
                    val count = row.filterContacts.count { contact -> contact.classes.any { it.id == option.id } }
                    menu.menu.add(0, index + 1, index + 1, context.getString(R.string.chat_filter_opsi_jumlah, option.name, count))
                }
                menu.setOnMenuItemClickListener { item ->
                    val chosenId = if (item.itemId == 0) null else row.filterClasses[item.itemId - 1].id
                    onSelectContactClass(chosenId)
                    true
                }
                menu.show()
            }
        }
    }

    private inner class ConversationHolder(private val binding: ItemChatConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: ChatConversation) {
            val context = binding.root.context
            val deletedLabel = context.getString(R.string.chat_akun_dihapus)
            val peer = conversation.peer

            val name = if (conversation.isRoom) {
                ClassNames.chatRoomTitle(context, conversation.classRoom?.name)
            } else {
                peer?.displayName(deletedLabel).orEmpty()
            }
            binding.tvName.text = name

            binding.ivRoomIcon.isVisible = conversation.isRoom
            binding.tvInitials.isVisible = !conversation.isRoom
            binding.ivAvatar.isVisible = !conversation.isRoom
            if (!conversation.isRoom) {
                binding.tvInitials.text = initialsOf(name)
                avatarLoader.load(binding.ivAvatar, peer?.avatarUrl.takeUnless { peer?.deleted == true }, R.drawable.bg_chat_empty)
            }

            val last = conversation.lastMessage
            binding.tvPreview.text = last?.let { previewText(context, conversation, it) }.orEmpty()
            binding.tvTime.text = last?.let { listTimeLabel(it.createdAt) }.orEmpty()

            val unread = conversation.unreadCount
            binding.tvUnread.isVisible = unread > 0
            if (unread > 0) {
                binding.tvUnread.text = if (unread > MAX_BADGE) "$MAX_BADGE+" else unread.toString()
                binding.tvUnread.contentDescription = context.getString(R.string.chat_belum_dibaca_cd, unread)
            }
            binding.tvClosed.isVisible = !conversation.canSend

            binding.cardConversation.setOnClickListener { onConversation(conversation) }
        }

        private fun previewText(context: Context, conversation: ChatConversation, message: com.nthb.kibooz.data.remote.ChatMessage): String {
            val base = when (message.previewKind()) {
                PreviewKind.TEXT -> message.body.trim().replace(Regex("\\s+"), " ")
                PreviewKind.DELETED -> context.getString(R.string.chat_pratinjau_dihapus)
                PreviewKind.PHOTO -> context.getString(R.string.chat_pratinjau_foto)
                PreviewKind.VIDEO -> context.getString(R.string.chat_pratinjau_video)
                PreviewKind.ATTACHMENT -> context.getString(R.string.chat_pratinjau_lampiran)
                PreviewKind.EMPTY -> ""
            }
            return when {
                message.deleted -> base
                message.senderId == ownUserId -> context.getString(R.string.chat_pratinjau_anda, base)
                conversation.isRoom -> context.getString(
                    R.string.chat_pratinjau_nama,
                    message.sender.displayName(context.getString(R.string.chat_akun_dihapus)),
                    base
                )
                else -> base
            }
        }
    }

    private inner class StarterHolder(private val binding: ItemChatStarterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindContact(row: ContactRow) {
            val context = binding.root.context
            val contact = row.contact
            binding.tvTitle.text = contact.title()
            val subtitle = contact.subtitle(
                guruLabel = context.getString(R.string.chat_peran_guru),
                waliLabel = context.getString(R.string.chat_peran_wali)
            )
            binding.tvSubtitle.text = subtitle
            binding.tvSubtitle.isVisible = subtitle.isNotEmpty()

            binding.ivRoomIcon.isVisible = false
            binding.tvInitials.isVisible = true
            binding.ivAvatar.isVisible = true
            binding.tvInitials.text = initialsOf(contact.fullName)
            avatarLoader.load(binding.ivAvatar, contact.avatarUrl, R.drawable.bg_chat_empty)

            showOpening(row.opening)
            binding.cardStarter.setOnClickListener { onContact(contact) }
        }

        fun bindClass(row: ClassRow) {
            val context = binding.root.context
            binding.tvTitle.text = ClassNames.chatRoomTitle(context, row.option.name)
            binding.tvSubtitle.setText(R.string.chat_ruang_isi)
            binding.tvSubtitle.isVisible = true

            binding.ivRoomIcon.isVisible = true
            binding.tvInitials.isVisible = false
            binding.ivAvatar.isVisible = false

            showOpening(row.opening)
            binding.cardStarter.setOnClickListener { onClass(row.option) }
        }

        private fun showOpening(opening: Boolean) {
            binding.progressOpening.isVisible = opening
            binding.ivChevron.isVisible = !opening
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatListRow>() {
        override fun areItemsTheSame(oldItem: ChatListRow, newItem: ChatListRow): Boolean = oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: ChatListRow, newItem: ChatListRow): Boolean = oldItem == newItem
    }

    private companion object {
        const val TYPE_SECTION = 0
        const val TYPE_CONVERSATION = 1
        const val TYPE_STARTER = 2
        const val TYPE_EMPTY_FILTER = 3
        const val MAX_BADGE = 99
    }
}
