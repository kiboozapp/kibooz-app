package com.nthb.kibooz.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.AvatarImage
import com.nthb.kibooz.ui.components.GuruTab
import com.nthb.kibooz.ui.components.KiboozGuruScaffold
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.AvatarLoader

/** Yang tampil di profil Guru. [subtitle] adalah tanggal bergabung, atau nama sekolah bila tanggalnya tidak ada. */
internal data class GuruProfileHeader(
    val name: String,
    val subtitle: String,
    val avatarUrl: String?,
    val avatarVersion: Int = 0
)

/** Layar profil Guru, yaitu kepala ungu dengan foto, tombol detail, kartu menu dan bilah bawah portal Guru. */
@Composable
internal fun ProfilGuruScreen(
    header: GuruProfileHeader,
    avatarLoader: AvatarLoader,
    onAvatar: () -> Unit,
    onDetail: () -> Unit,
    menu: List<ProfileMenuItem>,
    onTab: (GuruTab) -> Unit,
    onScan: () -> Unit
) {
    KiboozGuruScaffold(selected = GuruTab.PROFILE, onTab = onTab, onScan = onScan) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.kibooz_profile_page))
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(colorResource(R.color.kibooz_topbar_start), colorResource(R.color.kibooz_topbar_end))
                            ),
                            RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
                        )
                )
                Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.profil_guru_judul),
                        modifier = Modifier.semantics { heading() },
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier
                            .size(96.dp)
                            .clickable(
                                onClickLabel = stringResource(R.string.profil_ganti_foto),
                                role = Role.Button,
                                onClick = onAvatar
                            ),
                        shape = CircleShape,
                        shadowElevation = 8.dp,
                        border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface)
                    ) {
                        AvatarImage(
                            url = header.avatarUrl,
                            placeholder = R.drawable.img_avatar_guru,
                            loader = avatarLoader,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = if (header.name.isBlank()) {
                                stringResource(R.string.profil_foto_guru_umum)
                            } else {
                                stringResource(R.string.profil_foto_guru, header.name)
                            },
                            version = header.avatarVersion
                        )
                    }
                    Text(
                        text = header.name,
                        modifier = Modifier.padding(top = 12.dp),
                        color = colorResource(R.color.kibooz_profile_name),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = header.subtitle,
                        modifier = Modifier.padding(top = 2.dp),
                        color = colorResource(R.color.kibooz_profile_muted),
                        fontSize = 13.sp
                    )
                    DetailButton(onClick = onDetail, modifier = Modifier.padding(horizontal = 24.dp).padding(top = 14.dp))
                }
            }
            ProfileMenuCard(items = menu, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
        }
    }
}

/** Tombol ungu penuh dengan ikon, sama dengan tombol "Lihat Detail" di layar XML. */
@Composable
private fun DetailButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_badge_id),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.profil_guru_lihat_detail),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun ProfilGuruPreview() {
    KiboozTheme {
        ProfilGuruScreen(
            header = GuruProfileHeader("Guru Contoh", "Bergabung sejak 1 Januari 2026", null),
            avatarLoader = AvatarLoader(okhttp3.OkHttpClient()),
            onAvatar = {},
            onDetail = {},
            menu = listOf(ProfileMenuItem(R.drawable.ic_key, "Ubah Kata sandi", {})),
            onTab = {},
            onScan = {}
        )
    }
}
