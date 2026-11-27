package com.nthb.kibooz.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import com.nthb.kibooz.R
import com.nthb.kibooz.util.InsetsUtil

/**
 * Tema layar Compose. Warnanya dibaca dari `colors.xml`, sehingga layar Compose dan XML memakai nilai yang sama.
 * Hanya ada tema terang, sama seperti `Theme.KiboozApp`.
 *
 * Pakai di Activity dengan `setContent { KiboozTheme { ... } }`.
 */
@Composable
fun KiboozTheme(content: @Composable () -> Unit) {
    // Ikon bilah navigasi harus gelap. Atribut tema baru berlaku mulai Android 8.1, jadi diatur juga lewat kode
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect { InsetsUtil.ensureNavigationBarColor(view) }
    }
    val white = colorResource(R.color.kibooz_white)
    val textPrimary = colorResource(R.color.kibooz_text_primary)
    val colors = lightColorScheme(
        primary = colorResource(R.color.kibooz_primary_purple),
        onPrimary = white,
        secondary = colorResource(R.color.kibooz_yellow_accent),
        onSecondary = white,
        background = colorResource(R.color.kibooz_background),
        onBackground = textPrimary,
        surface = white,
        onSurface = textPrimary,
        onSurfaceVariant = colorResource(R.color.kibooz_text_secondary),
        outline = colorResource(R.color.kibooz_border),
        error = colorResource(R.color.kibooz_red_danger),
        onError = white
    )
    MaterialTheme(colorScheme = colors, typography = kiboozTypography) {
        // Teks tanpa warna memakai warna teks utama, bukan hitam bawaan Compose
        CompositionLocalProvider(LocalContentColor provides textPrimary, content = content)
    }
}
