package com.nthb.kibooz.ui.auth

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.ui.theme.KiboozTheme
import androidx.compose.ui.semantics.Role as SemanticsRole

private val cardShape = RoundedCornerShape(44.dp)

/** Layar pertama setelah pengenalan: pengguna memilih portal wali murid atau guru. */
@Composable
internal fun RoleSelectionScreen(onRoleSelected: (Role) -> Unit) {
    val background = gradient(R.color.kibooz_role_bg_top, R.color.kibooz_role_bg_center, R.color.kibooz_role_bg_bottom)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        // Bisa digulir agar kartu tetap terjangkau di layar pendek atau saat huruf diperbesar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = screenHeight)
                // Latar ikut digulir, agar teks putih di bawah tetap berada di bagian gradasi yang gelap
                .background(background)
                .systemBarsPadding()
                .padding(start = 20.dp, top = 36.dp, end = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.pilih_peran_judul),
                modifier = Modifier.semantics { heading() },
                color = colorResource(R.color.kibooz_role_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.pilih_peran_subjudul),
                modifier = Modifier.padding(top = 2.dp),
                color = colorResource(R.color.kibooz_role_subtitle),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            // Pembagian 45 : 55 sama dengan layout_constraintVertical_bias layar XML sebelumnya
            Spacer(modifier = Modifier.weight(0.45f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                RoleCard(
                    title = R.string.pilih_peran_wali,
                    description = R.string.pilih_peran_wali_deskripsi,
                    image = R.drawable.img_role_orang_tua,
                    cardBrush = gradient(
                        R.color.kibooz_role_wali_top,
                        R.color.kibooz_role_wali_center,
                        R.color.kibooz_role_wali_bottom
                    ),
                    onClick = { onRoleSelected(Role.WALI) },
                    modifier = Modifier.weight(1f)
                )
                RoleCard(
                    title = R.string.pilih_peran_guru,
                    description = R.string.pilih_peran_guru_deskripsi,
                    image = R.drawable.img_role_guru,
                    cardBrush = gradient(
                        R.color.kibooz_role_guru_top,
                        R.color.kibooz_role_guru_center,
                        R.color.kibooz_role_guru_bottom
                    ),
                    onClick = { onRoleSelected(Role.GURU) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.weight(0.55f))
        }
    }
}

/** Kartu bergambar dengan teks di bawahnya. Seluruh kolom bisa ditekan, efek tekannya mengikuti bentuk kartu. */
@Composable
private fun RoleCard(
    @StringRes title: Int,
    @StringRes description: Int,
    @DrawableRes image: Int,
    cardBrush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = SemanticsRole.Button,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .shadow(10.dp, cardShape)
                .clip(cardShape)
                .background(cardBrush)
                .border(1.5.dp, Color.White.copy(alpha = 0.3f), cardShape)
                .indication(interactionSource, ripple(color = Color.White))
                .padding(16.dp)
        ) {
            // Gambar hanya hiasan, kartu sudah dibaca dari judul dan deskripsinya
            Image(
                painter = painterResource(image),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Text(
            text = stringResource(title),
            modifier = Modifier.padding(top = 16.dp),
            color = colorResource(R.color.kibooz_white),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(description),
            modifier = Modifier.padding(top = 2.dp),
            color = colorResource(R.color.kibooz_role_description),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** Gradasi dari atas ke bawah, sama dengan `android:angle="270"`. */
@Composable
private fun gradient(@ColorRes top: Int, @ColorRes center: Int, @ColorRes bottom: Int): Brush =
    Brush.verticalGradient(listOf(colorResource(top), colorResource(center), colorResource(bottom)))

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun RoleSelectionPreview() {
    KiboozTheme { RoleSelectionScreen(onRoleSelected = {}) }
}
