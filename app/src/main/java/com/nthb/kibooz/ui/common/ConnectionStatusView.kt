package com.nthb.kibooz.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.databinding.ViewConnectionStatusBinding
import com.nthb.kibooz.util.formatWaitSeconds

/**
 * Kartu gangguan server (FE-12): "tidak ada koneksi" saat offline, dan "layanan tidak tersedia" dengan tautan ke
 * halaman status saat server atau proxy yang bermasalah. Menampilkan hitung mundur sampai percobaan berikutnya
 * dan tombol coba lagi. Tersembunyi sampai [show] dipanggil.
 */
class ConnectionStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = ViewConnectionStatusBinding.inflate(LayoutInflater.from(context), this)

    /** Dipanggil saat pengguna menekan coba lagi. */
    var onRetry: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_connection_status)
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)

        binding.btnOutageRetry.setOnClickListener { onRetry?.invoke() }
        binding.tvOutageStatusLink.setOnClickListener { openStatusPage() }
    }

    fun show(kind: OutageKind, secondsLeft: Int) {
        val wait = formatWaitSeconds(secondsLeft)
        when (kind) {
            OutageKind.OFFLINE -> {
                binding.tvOutageTitle.setText(R.string.outage_offline_title)
                binding.tvOutageMessage.text = context.getString(R.string.outage_offline_message, wait)
            }
            OutageKind.UNAVAILABLE -> {
                binding.tvOutageTitle.setText(R.string.outage_unavailable_title)
                binding.tvOutageMessage.text = context.getString(R.string.outage_unavailable_message, wait)
            }
        }
        // Halaman status hanya berguna jika masalahnya ada di server, bukan di ponsel
        binding.tvOutageStatusLink.isVisible = kind == OutageKind.UNAVAILABLE
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }

    private fun openStatusPage() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(STATUS_PAGE_URL)))
        } catch (e: ActivityNotFoundException) {
            // Tidak ada peramban di perangkat, tombol coba lagi tetap bisa dipakai
        }
    }

    companion object {
        const val STATUS_PAGE_URL = "https://status.syafahadyan.com/"
    }
}
