package com.nthb.kibooz.ui.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatContact
import com.nthb.kibooz.data.remote.ChatConversation
import com.nthb.kibooz.databinding.ItemChatConversationBinding
import com.nthb.kibooz.databinding.ItemChatSectionBinding
import com.nthb.kibooz.databinding.ItemChatStarterBinding
import com.nthb.kibooz.util.AvatarLoader

/** Daftar percakapan, ruang kelas dan kontak. Satu adapter untuk dua tab karena barisnya berbeda jenis. */
class ChatListAdapter(
    private val avatarLoader: AvatarLoader,
    private val ownUserId: String,
    private val onConversation: (ChatConversation) -> Unit,
    private val onContact: (ChatContact) -> Unit,
    private val onClass: (ChatClassOption) -> Unit
) : ListAdapter<ChatListRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SectionRow -> TYPE_SECTION
        is ConversationRow -> TYPE_CONVERSATION
        is ContactRow, is ClassRow -> TYPE_STARTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION -> SectionHolder(ItemChatSectionBinding.inflate(inflater, parent, false))
            TYPE_CONVERSATION -> ConversationHolder(ItemChatConversationBinding.inflate(inflater, parent, false))
            else -> StarterHolder(ItemChatStarterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is SectionRow -> (holder as SectionHolder).bind(row)
            is ConversationRow -> (holder as ConversationHolder).bind(row.conversation)
            is ContactRow -> (holder as StarterHolder).bindContact(row)
            is ClassRow -> (holder as StarterHolder).bindClass(row)
        }
    }

    private class SectionHolder(private val binding: ItemChatSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: SectionRow) {
            binding.tvSection.setText(row.title)
        }
    }

    private inner class ConversationHolder(private val binding: ItemChatConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: ChatConversation) {
            val context = binding.root.context
            val deletedLabel = context.getString(R.string.chat_akun_dihapus)
            val peer = conversation.peer

            val name = if (conversation.isRoom) {
                context.getString(R.string.chat_ruang_judul, conversation.classRoom?.name.orEmpty())
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
            val contact = row.contact
            binding.tvTitle.text = contact.title()
            binding.tvSubtitle.text = contact.classNames()
            binding.tvSubtitle.isVisible = binding.tvSubtitle.text.isNotEmpty()

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
            binding.tvTitle.text = context.getString(R.string.chat_ruang_judul, row.option.name)
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
        const val MAX_BADGE = 99
    }
}
