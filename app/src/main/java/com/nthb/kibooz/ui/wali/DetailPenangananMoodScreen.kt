package com.nthb.kibooz.ui.wali

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.KiboozTopBar
import com.nthb.kibooz.ui.components.lineSpacedStyle
import com.nthb.kibooz.ui.theme.KiboozTheme

private data class GuidanceStep(@StringRes val title: Int, @StringRes val body: Int)

private val steps = listOf(
    GuidanceStep(R.string.panduan_langkah_1_judul, R.string.panduan_langkah_1_isi),
    GuidanceStep(R.string.panduan_langkah_2_judul, R.string.panduan_langkah_2_isi),
    GuidanceStep(R.string.panduan_langkah_3_judul, R.string.panduan_langkah_3_isi),
    GuidanceStep(R.string.panduan_langkah_4_judul, R.string.panduan_langkah_4_isi)
)

// Jarak tambahan antarbaris teks isi, yang juga dibawa baris terakhir dan dikurangkan dari jarak di bawahnya
private const val BODY_EXTRA_DP = 2f

/**
 * Panduan penanganan emosi yang dipilih server. [categoryText] dan [title] datang dari dasbor, dan [state] memberi
 * tombol laporan keadaan kirim, menunggu batas laju, atau sudah terkirim.
 */
@Composable
internal fun DetailPenangananMoodScreen(
    categoryText: String,
    title: String,
    state: GuidanceApplyState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KiboozTopBar(
            title = stringResource(R.string.panduan_judul),
            onBack = onBack,
            actions = {
                IconButton(onClick = onShare) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = stringResource(R.string.panduan_bagikan),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp)
        ) {
            HeaderCard(categoryText = categoryText, title = title)
            Text(
                text = stringResource(R.string.panduan_langkah_judul),
                modifier = Modifier.padding(top = 20.dp),
                color = colorResource(R.color.kibooz_text_dark),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            steps.forEachIndexed { index, step -> StepCard(number = index + 1, step = step) }
            Spacer(Modifier.height(8.dp))
        }
        ApplyBar(state = state, onApply = onApply)
    }
}

/** Kartu putih dengan garis tepi tipis dan bayangan, pengganti `MaterialCardView` di layar XML. */
@Composable
private fun Modifier.cardSurface(radius: Dp, elevation: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(elevation, shape)
        .background(Color.White, shape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
}

@Composable
private fun HeaderCard(categoryText: String, title: String) {
    val imageShape = RoundedCornerShape(14.dp)
    Column(modifier = Modifier.fillMaxWidth().cardSurface(radius = 20.dp, elevation = 3.dp).padding(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(imageShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, imageShape)
        ) {
            Image(
                painter = painterResource(R.drawable.img_penanganan_aman_banner),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Text(
            text = categoryText,
            modifier = Modifier
                .padding(top = 12.dp)
                .background(colorResource(R.color.kibooz_lavender), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            color = colorResource(R.color.kibooz_mood_bingung),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 6.dp),
            color = colorResource(R.color.kibooz_text_dark),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        // Baris terakhir ikut membawa jarak tambahan, jadi jarak bawah kartu dikurangi sebesar itu
        Text(
            text = stringResource(R.string.panduan_tujuan),
            modifier = Modifier.padding(top = 4.dp, bottom = (16 - BODY_EXTRA_DP).dp),
            color = colorResource(R.color.kibooz_text_muted),
            style = lineSpacedStyle(fontSize = 13f, extraDp = BODY_EXTRA_DP)
        )
    }
}

@Composable
private fun StepCard(number: Int, step: GuidanceStep) {
    Row(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .cardSurface(radius = 16.dp, elevation = 2.dp)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = (16 - BODY_EXTRA_DP).dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(colorResource(R.color.kibooz_primary_purple), colorResource(R.color.kibooz_purple_gradient_end))
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number.toString(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = stringResource(step.title),
                color = colorResource(R.color.kibooz_text_dark),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(step.body),
                modifier = Modifier.padding(top = 4.dp),
                color = colorResource(R.color.kibooz_text_muted),
                style = lineSpacedStyle(fontSize = 13f, extraDp = BODY_EXTRA_DP)
            )
        }
    }
}

/** Bilah bawah dengan satu tombol. Selama mengirim atau setelah terkirim tombolnya redup dan tidak bisa dipakai. */
@Composable
private fun ApplyBar(state: GuidanceApplyState, onApply: () -> Unit) {
    val enabled = !state.sending && !state.applied
    val label = when {
        state.applied -> stringResource(R.string.panduan_sudah_dilaporkan)
        state.retryInSeconds != null -> stringResource(R.string.batas_laju_menunggu, state.retryInSeconds)
        else -> stringResource(R.string.panduan_terapkan_label)
    }
    val shape = RoundedCornerShape(25.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Bilah berhenti di atas tombol navigasi sistem
            .windowInsetsPadding(WindowInsets.navigationBars)
            .shadow(8.dp)
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(shape)
                .background(colorResource(R.color.kibooz_primary_purple))
                .clickable(
                    interactionSource = null,
                    indication = ripple(color = Color.White),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onApply
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun DetailPenangananMoodScreenPreview() {
    KiboozTheme {
        DetailPenangananMoodScreen(
            categoryText = "Kategori emosi: Sedih",
            title = "Temani Anak Saat Merasa Sedih",
            state = GuidanceApplyState(),
            onBack = {},
            onShare = {},
            onApply = {}
        )
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun DetailPenangananMoodScreenAppliedPreview() {
    KiboozTheme {
        DetailPenangananMoodScreen(
            categoryText = "Kategori emosi: Sedih",
            title = "Temani Anak Saat Merasa Sedih",
            state = GuidanceApplyState(applied = true),
            onBack = {},
            onShare = {},
            onApply = {}
        )
    }
}
