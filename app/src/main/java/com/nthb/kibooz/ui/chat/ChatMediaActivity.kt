package com.nthb.kibooz.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.nthb.kibooz.BuildConfig
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

    private val bindingDelegate = lazy { ActivityChatMediaBinding.inflate(layoutInflater) }
    private val binding by bindingDelegate

    /** Posisi putar video, dipertahankan saat layar diputar atau ditinggal sebentar. */
    private var positionMillis = 0

    /** False setelah orang menjeda atau videonya selesai, supaya tidak berbunyi sendiri saat layar kembali. */
    private var autoPlay = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)
        val key = intent.getStringExtra(EXTRA_KEY)
        if (url == null || key == null) {
            finish()
            return
        }

        setContentView(binding.root)
        InsetsUtil.applySystemBarInsets(root = binding.root, headerView = binding.llTopBar)
        binding.ivBack.setOnClickListener { finish() }
        positionMillis = savedInstanceState?.getInt(STATE_POSITION) ?: 0
        autoPlay = savedInstanceState?.getBoolean(STATE_AUTO_PLAY, true) ?: true

        if (intent.getStringExtra(EXTRA_KIND) == CHAT_MEDIA_VIDEO) showVideo(url) else showPhoto(key, url)
    }

    override fun onPause() {
        if (bindingDelegate.isInitialized()) {
            // Durasi negatif berarti video belum siap, jadi keadaan yang tersimpan tidak ditimpa
            val duration = binding.videoView.duration
            if (duration > 0) {
                autoPlay = binding.videoView.isPlaying
                val position = binding.videoView.currentPosition
                positionMillis = if (position < duration - END_MARGIN_MILLIS) position else 0
            }
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_POSITION, positionMillis)
        outState.putBoolean(STATE_AUTO_PLAY, autoPlay)
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
        // Alamat tanpa TLS ditolak selagi API memakai TLS, sama seperti pada unduhan foto
        if (url.startsWith("http://", ignoreCase = true) && BuildConfig.API_BASE_URL.startsWith("https://")) {
            showError(R.string.chat_video_gagal)
            return
        }
        binding.videoView.isVisible = true

        val controls = MediaController(this)
        controls.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(controls)
        binding.videoView.setOnPreparedListener {
            binding.progress.isVisible = false
            binding.videoView.seekTo(positionMillis)
            if (autoPlay) binding.videoView.start()
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
        private const val STATE_AUTO_PLAY = "AUTO_PLAY"
        private const val END_MARGIN_MILLIS = 500

        /** Membuka lampiran [message], yang harus punya lampiran. */
        fun intent(context: Context, message: ChatMessage): Intent =
            Intent(context, ChatMediaActivity::class.java)
                .putExtra(EXTRA_KIND, message.media?.kind)
                .putExtra(EXTRA_URL, message.media?.url)
                .putExtra(EXTRA_KEY, message.mediaCacheKey())
    }
}
