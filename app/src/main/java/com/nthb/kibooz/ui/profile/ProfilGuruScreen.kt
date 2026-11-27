package com.nthb.kibooz.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
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
            ProfileHeader(
                title = stringResource(R.string.profil_guru_judul),
                photoDescription = if (header.name.isBlank()) {
                    stringResource(R.string.profil_foto_guru_umum)
                } else {
                    stringResource(R.string.profil_foto_guru, header.name)
                },
                avatarUrl = header.avatarUrl,
                avatarVersion = header.avatarVersion,
                avatarLoader = avatarLoader,
                onAvatar = onAvatar
            ) {
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
                ProfileDetailButton(
                    label = stringResource(R.string.profil_guru_lihat_detail),
                    onClick = onDetail,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 14.dp)
                )
            }
            ProfileMenuCard(items = menu, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
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
