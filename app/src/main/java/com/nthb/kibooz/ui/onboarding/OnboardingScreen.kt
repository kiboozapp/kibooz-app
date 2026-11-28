package com.nthb.kibooz.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.lineSpacedStyle
import com.nthb.kibooz.ui.theme.KiboozTheme
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    @DrawableRes val image: Int,
    @StringRes val title: Int,
    @StringRes val description: Int
)

private val slides = listOf(
    OnboardingSlide(R.drawable.img_onboarding_1, R.string.onboarding_title_1, R.string.onboarding_desc_1),
    OnboardingSlide(R.drawable.img_onboarding_2, R.string.onboarding_title_2, R.string.onboarding_desc_2),
    OnboardingSlide(R.drawable.img_onboarding_3, R.string.onboarding_title_3, R.string.onboarding_desc_3)
)

// Ungu muda yang sama dengan deskripsi slide di layar XML sebelumnya
private val descriptionColor = Color(0xFFE5DEFF)
private val inactiveDotColor = Color(0x66FFFFFF)

/** Pengenalan tiga slide sebelum pengguna memilih peran. [onFinished] dipanggil saat Lanjut ditekan di slide terakhir. */
@Composable
internal fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    // Seperti currentItem di ViewPager2, halaman tujuan berlaku begitu animasi dimulai, jadi ketukan cepat berturut-turut tetap maju
    val page = pagerState.targetPage

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index -> SlidePage(slides[index]) }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Bilah tetap di atas tombol navigasi sistem dan papan ketik
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ukuran titik tetap per urutan, hanya warnanya yang mengikuti slide, seperti layar XML sebelumnya
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(size = 10, active = page == 0)
                Spacer(Modifier.width(6.dp))
                Dot(size = 8, active = page == 1)
                Spacer(Modifier.width(6.dp))
                Dot(size = 8, active = page == 2)
            }
            Spacer(Modifier.weight(1f))
            if (page > 0) {
                // Area sentuh 48dp dengan ikon 24dp di tempat yang sama seperti tombol 44dp dan jarak 12dp sebelumnya
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.onboarding_sebelumnya),
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                }
            }
            NextButton(
                onClick = {
                    if (page < slides.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                    } else {
                        onFinished()
                    }
                }
            )
        }
    }
}

@Composable
private fun Dot(size: Int, active: Boolean) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(if (active) MaterialTheme.colorScheme.secondary else inactiveDotColor, CircleShape)
    )
}

/** Tombol bulat putih 52dp bergaris tepi dengan panah ungu dan elevasi 4dp, seperti di layar XML sebelumnya. */
@Composable
private fun NextButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color(0x1A000000)),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = stringResource(R.string.onboarding_lanjut),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SlidePage(slide: OnboardingSlide) {
    val gradient = Brush.verticalGradient(
        0f to Color(0x005D40B3),
        0.5f to Color(0xD95D40B3),
        1f to MaterialTheme.colorScheme.primary
    )
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
        Image(
            painter = painterResource(slide.image),
            contentDescription = stringResource(R.string.onboarding_ilustrasi),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Gradasi dimulai di 45% tinggi layar dan menyatu dengan ungu di bagian bawah
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.55f).background(gradient))
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // TextView tidak memberi jarak tambahan setelah baris terakhir, jadi 4dp itu dikurangkan dari 100dp
                .padding(start = 24.dp, end = 24.dp, bottom = 96.dp)
        ) {
            Text(
                text = stringResource(slide.title),
                color = MaterialTheme.colorScheme.onPrimary,
                style = lineSpacedStyle(fontSize = 24f, extraDp = 2f),
                fontWeight = FontWeight.Bold
            )
            // 8dp dikurangi 2dp jarak tambahan yang sudah ada di bawah baris terakhir judul
            Text(
                text = stringResource(slide.description),
                modifier = Modifier.padding(top = 6.dp),
                color = descriptionColor,
                style = lineSpacedStyle(fontSize = 15f, extraDp = 4f)
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun OnboardingScreenPreview() {
    KiboozTheme { OnboardingScreen(onFinished = {}) }
}
