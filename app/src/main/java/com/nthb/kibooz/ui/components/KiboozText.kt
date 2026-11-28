package com.nthb.kibooz.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// Tinggi baris alami Baloo 2 adalah 1,602 kali ukuran huruf. Layar XML menambah jarak antarbaris lewat lineSpacingExtra
private const val BALOO_LINE_HEIGHT = 1.602f

// Jarak tambahan diletakkan di bawah tiap baris, seperti TextView
private val extraBelowLine = LineHeightStyle(alignment = LineHeightStyle.Alignment.Top, trim = LineHeightStyle.Trim.None)

/**
 * Gaya dengan tinggi baris alami Baloo 2 ditambah [extraDp], yang tidak ikut membesar saat huruf diperbesar.
 * Seperti `lineSpacingExtra` di TextView, jarak tambahan hanya terasa di antara baris. Baris terakhir ikut membawanya,
 * jadi kurangi [extraDp] dari jarak di bawah teks itu.
 */
@Composable
fun lineSpacedStyle(fontSize: Float, extraDp: Float): TextStyle {
    // 1dp tambahan sama dengan 1/fontScale sp, sehingga jarak tambahan tidak ikut membesar saat huruf diperbesar
    val lineHeight = (fontSize * BALOO_LINE_HEIGHT + extraDp / LocalDensity.current.fontScale).sp
    return MaterialTheme.typography.bodyLarge.copy(
        fontSize = fontSize.sp,
        lineHeight = lineHeight,
        lineHeightStyle = extraBelowLine
    )
}
