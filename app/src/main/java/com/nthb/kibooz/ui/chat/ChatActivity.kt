package com.nthb.kibooz.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ChatConversation
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.databinding.ActivityChatBinding
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Satu percakapan, langsung atau ruang kelas. Riwayat dimuat dari yang terbaru dan yang lebih lama dimuat saat
 * digulir ke atas. Pesan baru diambil tiap 5 detik lewat [ChatPoller], hanya selagi layar terlihat. Mengetuk pesan
 * sendiri menawarkan hapus. Jika percakapan tertutup, kotak tulis diganti keterangan alasannya.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var ownUserId = ""
    private var isRoom = false
    private var peerDeleted = false

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory(
            appContainer.chatRepository,
            ChatInfo(
                conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty(),
                isRoom = isRoom,
                peerDeleted = peerDeleted,
                canSend = intent.getBooleanExtra(EXTRA_CAN_SEND, true),
                peerLastReadMessageId = intent.getLongExtra(EXTRA_PEER_READ, 0L)
            ),
            ownUserId
        )
    }

    private var deleteDialog: AlertDialog? = null

    // Baris yang terakhir dikirim ke adapter, supaya perubahan state yang tidak mengubah baris tidak memicu gulir
    private var lastRows: List<ChatRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        val userId = appContainer.authRepository.session.value?.user?.id
        if (conversationId.isNullOrBlank() || userId == null) {
            finish()
            return
        }
        ownUserId = userId
        isRoom = intent.getBooleanExtra(EXTRA_IS_ROOM, false)
        peerDeleted = intent.getBooleanExtra(EXTRA_PEER_DELETED, false)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsUtil.applySystemBarInsets(root = binding.root, headerView = binding.clTopBar, bottomBar = binding.clInputBar)

        bindHeader()
        setupList()
        setupComposer()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.state.map { it.outage }.distinctUntilChanged().collectLatest(::runOutageCountdown) }
                launch {
                    // Polling hanya jalan selagi layar terlihat, dan tanda baca hanya dikirim selagi terlihat
                    viewModel.setVisible(true)
                    try {
                        ChatPoller().run { viewModel.poll() }
                    } finally {
                        viewModel.setVisible(false)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        super.onDestroy()
    }

    private fun bindHeader() {
        binding.ivBack.setOnClickListener { finish() }

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.tvTitle.text = title
        binding.tvSubtitle.setText(
            when {
                isRoom -> R.string.chat_subjudul_ruang
                intent.getStringExtra(EXTRA_PEER_ROLE) == ROLE_GURU -> R.string.chat_peran_guru
                intent.getStringExtra(EXTRA_PEER_ROLE) == ROLE_WALI -> R.string.chat_peran_wali
                else -> R.string.chat_subjudul_kosong
            }
        )

        binding.ivRoomIcon.isVisible = isRoom
        binding.tvInitials.isVisible = !isRoom
        binding.ivAvatar.isVisible = !isRoom
        if (!isRoom) {
            binding.tvInitials.text = initialsOf(title)
            val avatarUrl = intent.getStringExtra(EXTRA_PEER_AVATAR).takeUnless { peerDeleted }
            appContainer.avatarLoader.load(binding.ivAvatar, avatarUrl, android.R.color.transparent)
        }

        binding.btnCobaLagi.setOnClickListener { viewModel.load() }
        binding.connectionStatus.onRetry = { viewModel.load() }
    }

    private fun setupList() {
        adapter = ChatAdapter(
            onDelete = ::confirmDelete,
            onRetry = viewModel::retry,
            onDiscard = viewModel::discardFailed,
            onOlder = { viewModel.loadOlder(retry = true) }
        )
        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
        // Gelembung baru muncul tanpa animasi geser supaya urutan dan gulir ke bawah tidak terasa melompat
        binding.rvMessages.itemAnimator = null

        binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                viewModel.setAtBottom(isAtBottom())
                loadOlderIfNearTop()
            }
        })
    }

    private fun setupComposer() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString().orEmpty()
            if (viewModel.send(text)) binding.etMessage.text?.clear()
        }
        binding.etMessage.doAfterTextChanged { updateSendButton(viewModel.state.value) }
    }

    private fun isAtBottom(): Boolean =
        adapter.itemCount == 0 || layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 1

    private fun loadOlderIfNearTop() {
        val first = layoutManager.findFirstVisibleItemPosition()
        if (first in 0..LOAD_OLDER_THRESHOLD) viewModel.loadOlder()
    }

    /**
     * Hitung mundur sampai percobaan otomatis berikutnya saat server tidak terjangkau di muat awal (FE-12).
     * Berhenti sendiri saat layar tidak terlihat, dan dimulai lagi dari awal saat layar tampil kembali.
     */
    private suspend fun runOutageCountdown(outage: ServiceOutage?) {
        if (outage == null) {
            binding.connectionStatus.hide()
            return
        }

        for (secondsLeft in outage.waitSeconds downTo 1) {
            binding.connectionStatus.show(outage.kind, secondsLeft.toInt())
            delay(1_000)
        }
        viewModel.retryAfterOutage()
    }

    private fun render(state: ChatState) {
        val hasContent = state.timeline.messages.isNotEmpty() || state.local != null
        val failed = (!state.loaded || state.gone) && !state.loading && state.loadError != null && state.outage == null

        binding.tvOffline.isVisible = state.offline
        binding.llMemuat.isVisible = state.loading && !state.loaded && state.outage == null
        binding.llGagal.isVisible = failed
        if (failed) {
            state.loadError?.let { binding.tvGagalPesan.setText(it) }
            binding.btnCobaLagi.isVisible = !state.gone
        }
        binding.llKosong.isVisible = state.loaded && !state.gone && !hasContent
        binding.tvKosongIsi.setText(if (state.canSend) R.string.chat_kosong_pesan_bisa else R.string.chat_kosong_pesan_tutup)
        binding.rvMessages.isVisible = state.loaded && !state.gone && hasContent

        renderComposer(state)

        val rows = buildChatRows(state, ownUserId, isRoom)
        if (rows != lastRows) {
            lastRows = rows
            submitRows(rows)
        }

        state.restoreDraft?.let { draft ->
            if (binding.etMessage.text.isNullOrEmpty()) binding.etMessage.setText(draft)
            viewModel.draftRestored()
        }

        state.notice?.let {
            Snackbar.make(binding.root, it.res, Snackbar.LENGTH_LONG).setAnchorView(binding.clInputBar).show()
            viewModel.noticeShown()
        }
    }

    /** Kotak tulis hanya aktif saat percakapan terbuka. Selain itu diganti keterangan alasannya. */
    private fun renderComposer(state: ChatState) {
        binding.llComposer.isVisible = state.canSend
        binding.tvClosed.isVisible = !state.canSend
        if (!state.canSend) binding.tvClosed.setText(closedReasonRes(isRoom, peerDeleted))
        updateSendButton(state)
    }

    // Kotak tulis tetap bisa diketik selagi pesan terkirim. Hanya tombol kirim yang menunggu
    private fun updateSendButton(state: ChatState) {
        val enabled = state.canCompose && !binding.etMessage.text.isNullOrBlank()
        binding.btnSend.isEnabled = enabled
        binding.btnSend.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    private fun submitRows(rows: List<ChatRow>) {
        val old = adapter.currentList
        val wasAtBottom = old.isEmpty() || isAtBottom()
        val firstPosition = layoutManager.findFirstVisibleItemPosition()
        val anchorKey = old.getOrNull(firstPosition)?.key
        val anchorTop = layoutManager.findViewByPosition(firstPosition)?.top ?: 0
        val addedLocal = rows.lastOrNull() is LocalRow && old.lastOrNull() !is LocalRow

        adapter.submitList(rows) {
            if (rows.isNotEmpty()) {
                if (wasAtBottom || addedLocal) {
                    binding.rvMessages.scrollToPosition(rows.lastIndex)
                } else if (anchorKey != null) {
                    // Pesan lama yang dimuat di atas tidak boleh menggeser pesan yang sedang dibaca
                    val index = rows.indexOfFirst { it.key == anchorKey }
                    if (index >= 0) layoutManager.scrollToPositionWithOffset(index, anchorTop)
                }
            }
            binding.rvMessages.post {
                viewModel.setAtBottom(isAtBottom())
                loadOlderIfNearTop()
            }
        }
    }

    private fun confirmDelete(messageId: Long) {
        deleteDialog?.dismiss()
        deleteDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chat_hapus_judul)
            .setMessage(R.string.chat_hapus_isi)
            .setPositiveButton(R.string.chat_hapus_ya) { _, _ -> viewModel.delete(messageId) }
            .setNegativeButton(R.string.batal, null)
            .show()
    }

    companion object {
        private const val EXTRA_CONVERSATION_ID = "CONVERSATION_ID"
        private const val EXTRA_IS_ROOM = "IS_ROOM"
        private const val EXTRA_TITLE = "TITLE"
        private const val EXTRA_PEER_ROLE = "PEER_ROLE"
        private const val EXTRA_PEER_AVATAR = "PEER_AVATAR"
        private const val EXTRA_PEER_DELETED = "PEER_DELETED"
        private const val EXTRA_CAN_SEND = "CAN_SEND"
        private const val EXTRA_PEER_READ = "PEER_READ"

        private const val ROLE_GURU = "GURU"
        private const val ROLE_WALI = "WALI"

        /** Pesan teratas yang terlihat sampai indeks ini memicu muat pesan yang lebih lama. */
        private const val LOAD_OLDER_THRESHOLD = 3

        private const val DISABLED_ALPHA = 0.4f

        /** Membuka [conversation], dari daftar percakapan atau dari balasan buka percakapan. */
        fun intent(context: Context, conversation: ChatConversation): Intent {
            val deletedLabel = context.getString(R.string.chat_akun_dihapus)
            val title = if (conversation.isRoom) {
                context.getString(R.string.chat_ruang_judul, conversation.classRoom?.name.orEmpty())
            } else {
                conversation.peer?.displayName(deletedLabel).orEmpty()
            }

            return Intent(context, ChatActivity::class.java)
                .putExtra(EXTRA_CONVERSATION_ID, conversation.id)
                .putExtra(EXTRA_IS_ROOM, conversation.isRoom)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PEER_ROLE, conversation.peer?.role)
                .putExtra(EXTRA_PEER_AVATAR, conversation.peer?.avatarUrl)
                .putExtra(EXTRA_PEER_DELETED, conversation.peer?.deleted == true)
                .putExtra(EXTRA_CAN_SEND, conversation.canSend)
                .putExtra(EXTRA_PEER_READ, conversation.peerLastReadMessageId)
        }
    }
}
