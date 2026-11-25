package com.nthb.kibooz.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.databinding.ItemChatDateBinding
import com.nthb.kibooz.databinding.ItemChatLocalBinding
import com.nthb.kibooz.databinding.ItemChatMessageOtherBinding
import com.nthb.kibooz.databinding.ItemChatMessageOwnBinding
import com.nthb.kibooz.databinding.ItemChatOlderBinding
import com.nthb.kibooz.util.formatWaitSeconds

/**
 * Daftar pesan satu percakapan. Pesan sendiri di kanan, pesan lawan bicara di kiri dengan nama pengirim, dan
 * penanda tanggal di antaranya. Mengetuk pesan sendiri meminta menghapusnya, dan pesan yang belum terkirim
 * punya aksi kirim ulang dan buang.
 */
class ChatAdapter(
    private val onDelete: (messageId: Long) -> Unit,
    private val onRetry: () -> Unit,
    private val onDiscard: () -> Unit,
    private val onOlder: () -> Unit
) : ListAdapter<ChatRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (val row = getItem(position)) {
        is OlderRow -> TYPE_OLDER
        is DateRow -> TYPE_DATE
        is MessageRow -> if (row.own) TYPE_OWN else TYPE_OTHER
        is LocalRow -> TYPE_LOCAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_OLDER -> OlderHolder(ItemChatOlderBinding.inflate(inflater, parent, false))
            TYPE_DATE -> DateHolder(ItemChatDateBinding.inflate(inflater, parent, false))
            TYPE_OWN -> OwnHolder(ItemChatMessageOwnBinding.inflate(inflater, parent, false))
            TYPE_OTHER -> OtherHolder(ItemChatMessageOtherBinding.inflate(inflater, parent, false))
            else -> LocalHolder(ItemChatLocalBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is OlderRow -> (holder as OlderHolder).bind(row)
            is DateRow -> (holder as DateHolder).bind(row)
            is MessageRow -> if (row.own) (holder as OwnHolder).bind(row) else (holder as OtherHolder).bind(row)
            is LocalRow -> (holder as LocalHolder).bind(row)
        }
    }

    private inner class OlderHolder(private val binding: ItemChatOlderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: OlderRow) {
            binding.progressOlder.isVisible = row.loading
            binding.tvOlder.setText(if (row.loading) R.string.chat_riwayat_memuat else R.string.chat_riwayat_gagal)

            binding.root.isClickable = !row.loading
            binding.root.isFocusable = !row.loading
            binding.root.setOnClickListener(if (row.loading) null else View.OnClickListener { onOlder() })
        }
    }

    private class DateHolder(private val binding: ItemChatDateBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: DateRow) {
            binding.tvDate.text = row.label
        }
    }

    private class OtherHolder(private val binding: ItemChatMessageOtherBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: MessageRow) {
            val context = binding.root.context
            binding.tvSender.text = row.message.sender.displayName(context.getString(R.string.chat_akun_dihapus))
            bindBody(binding.tvBody, row.message, own = false)
            binding.tvTime.text = row.time
        }
    }

    private inner class OwnHolder(private val binding: ItemChatMessageOwnBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: MessageRow) {
            val context = binding.root.context
            bindBody(binding.tvBody, row.message, own = true)

            binding.tvMeta.text = when {
                row.deleting -> context.getString(R.string.chat_menghapus)
                row.readByPeer -> context.getString(R.string.chat_meta_dibaca, row.time)
                else -> row.time
            }

            // Hanya pesan sendiri yang belum dihapus yang bisa diketuk, dan TalkBack menyebut aksinya "Hapus pesan"
            ViewCompat.setAccessibilityDelegate(binding.llBubble, null)
            binding.llBubble.isClickable = row.deletable
            binding.llBubble.isFocusable = row.deletable
            if (row.deletable) {
                binding.llBubble.setOnClickListener { onDelete(row.message.id) }
                ViewCompat.replaceAccessibilityAction(
                    binding.llBubble,
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    context.getString(R.string.chat_hapus_aksi),
                    null
                )
            } else {
                binding.llBubble.setOnClickListener(null)
                binding.llBubble.isClickable = false
            }
        }
    }

    private inner class LocalHolder(private val binding: ItemChatLocalBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: LocalRow) {
            val context = binding.root.context
            binding.tvBody.text = row.local.pending.body

            when (val status = row.local.status) {
                is LocalStatus.Sending -> {
                    binding.tvStatus.text = if (status.waitSeconds > 0) {
                        context.getString(R.string.chat_mengirim_menunggu, formatWaitSeconds(status.waitSeconds))
                    } else {
                        context.getString(R.string.chat_mengirim)
                    }
                    binding.tvError.isVisible = false
                    binding.llActions.isVisible = false
                }
                is LocalStatus.Failed -> {
                    binding.tvStatus.setText(R.string.chat_belum_terkirim)
                    binding.tvError.setText(status.error)
                    binding.tvError.isVisible = true
                    binding.llActions.isVisible = true
                }
            }

            binding.btnRetry.setOnClickListener { onRetry() }
            binding.btnDiscard.setOnClickListener { onDiscard() }
        }
    }

    private companion object {
        const val TYPE_OLDER = 0
        const val TYPE_DATE = 1
        const val TYPE_OWN = 2
        const val TYPE_OTHER = 3
        const val TYPE_LOCAL = 4

        /**
         * Isi pesan, keterangan pesan yang dihapus, atau keterangan lampiran yang belum bisa dibuka. Keterangan
         * memakai warna yang lebih pudar, dan tetap terbaca karena rasio kontrasnya di atas 4,5:1.
         */
        fun bindBody(view: TextView, message: ChatMessage, own: Boolean) {
            val context = view.context
            val normal = if (own) R.color.kibooz_white else R.color.kibooz_text_primary
            val faded = if (own) R.color.chat_own_meta_text else R.color.kibooz_text_secondary

            when (message.previewKind()) {
                PreviewKind.TEXT -> {
                    view.text = message.body
                    view.setTextColor(ContextCompat.getColor(context, normal))
                }
                PreviewKind.DELETED -> {
                    view.setText(R.string.chat_pesan_dihapus)
                    view.setTextColor(ContextCompat.getColor(context, faded))
                }
                PreviewKind.PHOTO -> showNote(view, context.getString(R.string.chat_lampiran_foto), faded)
                PreviewKind.VIDEO -> showNote(view, context.getString(R.string.chat_lampiran_video), faded)
                PreviewKind.ATTACHMENT -> showNote(view, context.getString(R.string.chat_lampiran_lain), faded)
                PreviewKind.EMPTY -> view.text = ""
            }
        }

        private fun showNote(view: TextView, text: String, @ColorRes color: Int) {
            view.text = text
            view.setTextColor(ContextCompat.getColor(view.context, color))
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatRow>() {
        override fun areItemsTheSame(oldItem: ChatRow, newItem: ChatRow): Boolean = oldItem.key == newItem.key

        override fun areContentsTheSame(oldItem: ChatRow, newItem: ChatRow): Boolean = oldItem == newItem
    }
}
