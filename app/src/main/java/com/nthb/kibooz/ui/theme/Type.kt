package com.nthb.kibooz.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.nthb.kibooz.R

/** Baloo 2 dimuat dari berkas font XML yang sama dengan layar XML, agar tebal dan tingginya tampil sama. */
@Composable
internal fun rememberKiboozTypography(): Typography {
    val context = LocalContext.current
    return remember(context) {
        val family = ResourcesCompat.getFont(context, R.font.baloo2)?.let { FontFamily(Typeface(it)) } ?: FontFamily.Default
        kiboozTypography(family)
    }
}

private fun kiboozTypography(family: FontFamily): Typography {
    // TextView memakai font padding, sedangkan Compose tidak secara bawaan
    @Suppress("DEPRECATION")
    val base = TextStyle(fontFamily = family, platformStyle = PlatformTextStyle(includeFontPadding = true))

    // Tinggi baris tidak diatur, sama seperti TextView. bodyLarge dipakai teks tanpa gaya, jadi ukurannya 14sp
    return Typography(
        displayLarge = base.copy(fontSize = 57.sp),
        displayMedium = base.copy(fontSize = 45.sp),
        displaySmall = base.copy(fontSize = 36.sp),
        headlineLarge = base.copy(fontSize = 32.sp),
        headlineMedium = base.copy(fontSize = 28.sp),
        headlineSmall = base.copy(fontSize = 24.sp),
        titleLarge = base.copy(fontSize = 22.sp),
        titleMedium = base.copy(fontSize = 16.sp),
        titleSmall = base.copy(fontSize = 14.sp),
        bodyLarge = base.copy(fontSize = 14.sp),
        bodyMedium = base.copy(fontSize = 14.sp),
        bodySmall = base.copy(fontSize = 12.sp),
        labelLarge = base.copy(fontSize = 14.sp),
        labelMedium = base.copy(fontSize = 12.sp),
        labelSmall = base.copy(fontSize = 11.sp)
    )
}
