package com.nthb.kibooz.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.nthb.kibooz.R

/**
 * Baloo 2 dengan tebal yang sama seperti TextView: huruf dasarnya dari berkas font XML, dan tebal dibuat dengan
 * `Typeface.create(dasar, Typeface.BOLD)`, sama seperti `android:textStyle="bold"`.
 */
private data class Baloo2Font(override val weight: FontWeight) :
    AndroidFont(FontLoadingStrategy.Blocking, Baloo2Loader, FontVariation.Settings()) {
    override val style: FontStyle = FontStyle.Normal
}

private object Baloo2Loader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        val base = ResourcesCompat.getFont(context, R.font.baloo2) ?: return null
        return if (font.weight >= FontWeight.SemiBold) Typeface.create(base, Typeface.BOLD) else base
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? = loadBlocking(context, font)
}

private val baloo2 = FontFamily(Baloo2Font(FontWeight.Normal), Baloo2Font(FontWeight.Bold))

// TextView memakai font padding, sedangkan Compose tidak secara bawaan
@Suppress("DEPRECATION")
private val baseStyle = TextStyle(fontFamily = baloo2, platformStyle = PlatformTextStyle(includeFontPadding = true))

// Tinggi baris tidak diatur, sama seperti TextView. bodyLarge dipakai teks tanpa gaya, jadi ukurannya 14sp
internal val kiboozTypography = Typography(
    displayLarge = baseStyle.copy(fontSize = 57.sp),
    displayMedium = baseStyle.copy(fontSize = 45.sp),
    displaySmall = baseStyle.copy(fontSize = 36.sp),
    headlineLarge = baseStyle.copy(fontSize = 32.sp),
    headlineMedium = baseStyle.copy(fontSize = 28.sp),
    headlineSmall = baseStyle.copy(fontSize = 24.sp),
    titleLarge = baseStyle.copy(fontSize = 22.sp),
    titleMedium = baseStyle.copy(fontSize = 16.sp),
    titleSmall = baseStyle.copy(fontSize = 14.sp),
    bodyLarge = baseStyle.copy(fontSize = 14.sp),
    bodyMedium = baseStyle.copy(fontSize = 14.sp),
    bodySmall = baseStyle.copy(fontSize = 12.sp),
    labelLarge = baseStyle.copy(fontSize = 14.sp),
    labelMedium = baseStyle.copy(fontSize = 12.sp),
    labelSmall = baseStyle.copy(fontSize = 11.sp)
)
