package com.nthb.kibooz.ui.components

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.nthb.kibooz.util.AvatarLoader

/**
 * Foto profil bulat yang diambil lewat [AvatarLoader], yaitu pemuat yang sama dengan layar XML, jadi cache dan
 * pengosongannya (`evict`) tetap berlaku. [contentDescription] kosong menandai foto sebagai hiasan. Naikkan [version]
 * saat berkas di URL yang sama berganti, agar gambar dimuat ulang.
 */
@Composable
fun AvatarImage(
    url: String?,
    @DrawableRes placeholder: Int,
    loader: AvatarLoader,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    version: Int = 0
) {
    val described = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    key(version) {
        AndroidView(
            factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
            update = { view -> loader.load(view, url, placeholder) },
            modifier = described.clip(CircleShape)
        )
    }
}
