package com.nthb.kibooz.ui.chat

import android.widget.ImageView
import androidx.core.view.isVisible
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.CHAT_MEDIA_PHOTO
import com.nthb.kibooz.data.remote.CHAT_MEDIA_VIDEO
import com.nthb.kibooz.data.remote.ChatAttachment
import com.nthb.kibooz.data.remote.ChatMessage
import com.nthb.kibooz.databinding.ViewChatMediaBinding

/** Kunci penyimpanan lampiran sebuah pesan. Id pesan hanya unik di dalam percakapannya. */
fun ChatMessage.mediaCacheKey(): String = "$conversationId/$id"

/**
 * Mengisi kotak lampiran pesan yang sudah ada di server, atau menyembunyikannya untuk pesan tanpa lampiran.
 * Foto tampil sebagai gambar kecil, video sebagai kotak dengan tombol putar. Mengetuk membuka lampirannya, dan
 * [onLongPress] terisi untuk pesan sendiri yang bisa dihapus.
 */
fun bindMessageMedia(
    view: ViewChatMediaBinding,
    message: ChatMessage,
    loader: ChatMediaLoader,
    onOpen: () -> Unit,
    onFailed: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val media = message.shownMedia()
    view.root.isVisible = media != null
    if (media == null) {
        loader.clear(view.ivMedia)
        return
    }

    val context = view.root.context
    view.root.clipToOutline = true
    view.root.setOnClickListener { onOpen() }
    if (onLongPress != null) {
        view.root.setOnLongClickListener {
            onLongPress()
            true
        }
    } else {
        view.root.setOnLongClickListener(null)
        view.root.isLongClickable = false
    }

    if (media.kind == CHAT_MEDIA_VIDEO) {
        val size = fileSizeLabel(media.sizeBytes)
        loader.clear(view.ivMedia)
        view.ivPlay.isVisible = true
        view.tvMediaLabel.isVisible = true
        view.tvMediaLabel.text = context.getString(R.string.chat_lampiran_label_video, size)
        view.root.contentDescription = context.getString(R.string.chat_media_putar_cd, size)
    } else {
        view.ivPlay.isVisible = false
        view.tvMediaLabel.isVisible = true
        view.tvMediaLabel.setText(R.string.chat_media_memuat)
        view.root.contentDescription = context.getString(R.string.chat_media_buka_foto_cd)
        loader.showPhoto(view.ivMedia, message.mediaCacheKey(), media.url) { loaded ->
            view.tvMediaLabel.isVisible = !loaded
            if (!loaded) {
                view.tvMediaLabel.setText(R.string.chat_media_gagal)
                view.root.contentDescription = context.getString(R.string.chat_media_gagal)
                onFailed()
            }
        }
    }
}

/** Mengisi kotak lampiran pesan yang belum terkirim. Tidak bisa dibuka. */
fun bindLocalMedia(view: ViewChatMediaBinding, attachment: ChatAttachment?, loader: ChatMediaLoader) {
    view.root.isVisible = attachment != null
    view.root.isClickable = false
    view.root.isFocusable = false
    if (attachment == null) {
        loader.clear(view.ivMedia)
        return
    }

    val context = view.root.context
    val file = attachment.tempFile
    val photo = attachment.kind == CHAT_MEDIA_PHOTO
    val label = context.getString(
        if (photo) R.string.chat_lampiran_label_foto else R.string.chat_lampiran_label_video,
        fileSizeLabel(attachment.sizeBytes)
    )

    view.root.clipToOutline = true
    view.root.contentDescription = label
    view.ivPlay.isVisible = !photo
    view.tvMediaLabel.isVisible = !photo
    view.tvMediaLabel.text = label
    if (photo && file != null) loader.showFile(view.ivMedia, file) else loader.clear(view.ivMedia)
}

/** Gambar kecil lampiran yang dipilih, di atas kotak tulis. */
fun bindAttachmentThumbnail(view: ImageView, attachment: ChatAttachment?, loader: ChatMediaLoader) {
    val file = attachment?.tempFile
    if (attachment?.kind == CHAT_MEDIA_PHOTO && file != null) {
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        loader.showFile(view, file)
    } else {
        loader.clear(view)
        view.scaleType = ImageView.ScaleType.CENTER
        if (attachment?.kind == CHAT_MEDIA_VIDEO) view.setImageResource(R.drawable.ic_play)
    }
}
