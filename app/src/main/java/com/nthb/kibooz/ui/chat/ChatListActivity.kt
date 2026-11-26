package com.nthb.kibooz.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.ServiceOutage
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.databinding.ActivityChatListBinding
import com.nthb.kibooz.util.InsetsUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Daftar pesan untuk Guru dan Wali. Tab pertama berisi percakapan yang sudah punya pesan, yang terbaru dulu, dengan
 * jumlah belum dibaca. Tab "Mulai baru" berisi ruang kelas dan kontak untuk memulai percakapan. Daftar dimuat ulang
 * saat layar tampil lagi, saat socket memberi tahu ada yang berubah, dan lewat tombol muat ulang di atas.
 */
class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private lateinit var adapter: ChatListAdapter

    private val session get() = appContainer.authRepository.session.value
    private val role: Role? get() = session?.user?.role

    private val viewModel: ChatListViewModel by viewModels {
        ChatListViewModel.Factory(appContainer.chatRepository, appContainer.classRepository, role)
    }

    // Galat muat ulang yang sudah dijelaskan, supaya pesannya tidak muncul lagi di setiap perubahan state
    private var announcedError: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsUtil.applySystemBarInsets(root = binding.root, headerView = binding.clTopBar)

        adapter = ChatListAdapter(
            avatarLoader = appContainer.avatarLoader,
            ownUserId = session?.user?.id.orEmpty(),
            onConversation = viewModel::openConversation,
            onContact = viewModel::openContact,
            onClass = viewModel::openClass
        )
        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = adapter

        setupListeners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.state.map { activeOutage(it) }.distinctUntilChanged().collectLatest(::runOutageCountdown) }
                launch {
                    // onResume sudah memuat ulang saat layar tampil, jadi tidak ada tick awal
                    ChatTicker(appContainer.chatSocket, pollMillis = CHAT_LIST_POLL_INTERVAL_MILLIS, catchUpOnStart = false).run(
                        tickFor = { it.tickForList(session?.user?.id.orEmpty()) },
                        onTick = { viewModel.refreshOnTick() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari percakapan bisa membawa pesan baru dan jumlah belum dibaca yang berubah
        viewModel.refreshQuietly()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }
        binding.ivRefresh.setOnClickListener { viewModel.refresh() }
        binding.tabPercakapan.setOnClickListener { viewModel.selectTab(ChatListTab.CONVERSATIONS) }
        binding.tabBaru.setOnClickListener { viewModel.selectTab(ChatListTab.NEW) }
        binding.btnCobaLagi.setOnClickListener { viewModel.refresh() }
        binding.connectionStatus.onRetry = { viewModel.refresh() }
        binding.btnMulai.setOnClickListener { viewModel.selectTab(ChatListTab.NEW) }
    }

    private fun activeOutage(state: ChatListState): ServiceOutage? = when (state.tab) {
        ChatListTab.CONVERSATIONS -> state.conversations.outage
        ChatListTab.NEW -> state.starters?.outage
    }

    /**
     * Hitung mundur sampai percobaan otomatis berikutnya saat server tidak terjangkau (FE-12). Berhenti sendiri
     * saat layar tidak terlihat, dan dimulai lagi dari awal saat layar tampil kembali.
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

    private fun render(state: ChatListState) {
        val onConversations = state.tab == ChatListTab.CONVERSATIONS
        styleTab(binding.tabPercakapan, selected = onConversations)
        styleTab(binding.tabBaru, selected = !onConversations)

        val loading: Boolean
        val hasData: Boolean
        val empty: Boolean
        val error: Int?
        val outage: ServiceOutage?
        if (onConversations) {
            val section = state.conversations
            loading = section.loading
            hasData = section.data != null
            empty = section.data?.isEmpty() == true
            error = section.error
            outage = section.outage
        } else {
            val section = state.starters
            val starters = section?.data
            loading = section?.loading ?: true
            hasData = starters != null
            empty = starters != null && starters.contacts.isEmpty() && starters.classes.isEmpty()
            error = section?.error
            outage = section?.outage
        }

        binding.progressTop.isVisible = (loading && hasData) || state.opening != null
        binding.llMemuat.isVisible = !hasData && loading && outage == null
        val failed = !hasData && !loading && error != null && outage == null
        binding.llGagal.isVisible = failed
        if (failed && error != null) binding.tvGagalPesan.setText(error)

        binding.llKosong.isVisible = hasData && empty
        if (hasData && empty) renderEmpty(onConversations)
        binding.rvList.isVisible = hasData && !empty
        adapter.submitList(buildChatListRows(state))

        // Muat ulang yang gagal saat daftar lama masih tampil dijelaskan lewat pesan singkat, bukan layar penuh
        if (error == null) {
            announcedError = null
        } else if (hasData && outage == null && error != announcedError) {
            announcedError = error
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).setAction(R.string.coba_lagi) { viewModel.refresh() }.show()
        }

        state.notice?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            viewModel.noticeShown()
        }

        state.opened?.let {
            viewModel.openedHandled()
            startActivity(ChatActivity.intent(this, it))
        }
    }

    private fun renderEmpty(onConversations: Boolean) {
        if (onConversations) {
            binding.tvKosongJudul.setText(R.string.chat_kosong_judul)
            binding.tvKosongIsi.setText(R.string.chat_kosong_isi)
            binding.btnMulai.isVisible = true
        } else {
            binding.tvKosongJudul.setText(R.string.chat_baru_kosong_judul)
            binding.tvKosongIsi.setText(
                if (role == Role.GURU) R.string.chat_baru_kosong_guru else R.string.chat_baru_kosong_wali
            )
            binding.btnMulai.isVisible = false
        }
    }

    private fun styleTab(tab: TextView, selected: Boolean) {
        tab.isSelected = selected
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_chat_tab_active)
            tab.setTextColor(ContextCompat.getColor(this, R.color.kibooz_white))
        } else {
            tab.setBackgroundResource(R.drawable.bg_chat_tab_idle)
            tab.setTextColor(ContextCompat.getColor(this, R.color.chat_tab_idle_text))
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ChatListActivity::class.java)
    }
}
