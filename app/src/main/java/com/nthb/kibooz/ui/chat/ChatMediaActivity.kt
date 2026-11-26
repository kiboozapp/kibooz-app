package com.nthb.kibooz.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.nthb.kibooz.R
import com.nthb.kibooz.appContainer
import com.nthb.kibooz.data.remote.CHAT_MEDIA_VIDEO
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.databinding.ActivityChatMediaBinding
import com.nthb.kibooz.util.InsetsUtil

/**
 * Satu foto atau video lampiran chat dalam layar penuh. Video diputar [android.widget.VideoView] di dalam
 * aplikasi, supaya alamat bertanda tangan tidak dibagikan ke aplikasi lain.
 */
class ChatMediaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatMediaBinding

    /** Posisi putar video, dipertahankan saat layar diputar atau ditinggal sebentar. */
    private var positionMillis = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)
        val key = intent.getStringExtra(EXTRA_KEY)
        if (url == null || key == null) {
            finish()
            return
        }

        binding = ActivityChatMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsUtil.applySystemBarInsets(root = binding.root, headerView = binding.llTopBar)
        binding.ivBack.setOnClickListener { finish() }
        positionMillis = savedInstanceState?.getInt(STATE_POSITION) ?: 0

        if (intent.getStringExtra(EXTRA_KIND) == CHAT_MEDIA_VIDEO) showVideo(url) else showPhoto(key, url)
    }

    override fun onPause() {
        if (::binding.isInitialized) {
            binding.videoView.currentPosition.takeIf { it > 0 }?.let { positionMillis = it }
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_POSITION, positionMillis)
    }

    private fun showPhoto(key: String, url: String) {
        binding.tvTitle.setText(R.string.chat_pratinjau_foto)
        binding.ivPhoto.isVisible = true
        appContainer.chatMediaLoader.showPhoto(binding.ivPhoto, key, url, full = true) { loaded ->
            binding.progress.isVisible = false
            // Gambar kecil yang sudah tersimpan tetap tampil walaupun yang besar gagal
            if (!loaded && binding.ivPhoto.drawable == null) showError(R.string.chat_foto_gagal_dibuka)
        }
    }

    private fun showVideo(url: String) {
        binding.tvTitle.setText(R.string.chat_pratinjau_video)
        binding.videoView.isVisible = true

        val controls = MediaController(this)
        controls.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(controls)
        binding.videoView.setOnPreparedListener {
            binding.progress.isVisible = false
            binding.videoView.seekTo(positionMillis)
            binding.videoView.start()
            controls.show()
        }
        binding.videoView.setOnErrorListener { _, _, _ ->
            showError(R.string.chat_video_gagal)
            true
        }
        binding.videoView.setVideoURI(Uri.parse(url))
    }

    private fun showError(@StringRes message: Int) {
        binding.progress.isVisible = false
        binding.tvError.setText(message)
        binding.tvError.isVisible = true
    }

    companion object {
        private const val EXTRA_KIND = "KIND"
        private const val EXTRA_URL = "URL"
        private const val EXTRA_KEY = "CACHE_KEY"
        private const val STATE_POSITION = "POSITION"

        /** Membuka lampiran [message], yang harus punya lampiran. */
        fun intent(context: Context, message: ChatMessage): Intent =
            Intent(context, ChatMediaActivity::class.java)
                .putExtra(EXTRA_KIND, message.media?.kind)
                .putExtra(EXTRA_URL, message.media?.url)
                .putExtra(EXTRA_KEY, message.mediaCacheKey())
    }
}
