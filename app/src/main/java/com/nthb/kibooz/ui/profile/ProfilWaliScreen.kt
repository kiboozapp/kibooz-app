package com.nthb.kibooz.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.ConnectionStatusCard
import com.nthb.kibooz.ui.components.KiboozWaliScaffold
import com.nthb.kibooz.ui.components.WaliTab
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.WaliProfileState
import com.nthb.kibooz.util.AvatarLoader

/**
 * Layar profil Wali, yaitu kepala ungu dengan foto, nama Wali, anak yang dipilih, tombol ke profil anak atau form tambah
 * anak, kartu menu dan bilah bawah portal Wali. Kartu gangguan atau galat muat tampil di bawah kepala selama belum ada
 * data sama sekali.
 */
@Composable
internal fun ProfilWaliScreen(
    state: WaliProfileState,
    avatarUrl: String?,
    avatarVersion: Int,
    avatarLoader: AvatarLoader,
    onAvatar: () -> Unit,
    onDetail: (WaliProfileState) -> Unit,
    onRetry: () -> Unit,
    menu: List<ProfileMenuItem>,
    onTab: (WaliTab) -> Unit,
    onScan: () -> Unit
) {
    val profile = state.profile
    val hasContent = profile != null || state.noChild
    val name = state.parentName
    val loadError = state.loadError
    val blocking = loadError != null && !hasContent

    KiboozWaliScaffold(selected = WaliTab.PROFILE, onTab = onTab, onScan = onScan) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.kibooz_profile_page))
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            ProfileHeader(
                title = stringResource(R.string.profil_wali_judul),
                photoDescription = stringResource(R.string.anak_avatar_wali),
                avatarUrl = avatarUrl,
                avatarVersion = avatarVersion,
                avatarLoader = avatarLoader,
                onAvatar = onAvatar
            ) {
                if (name != null) {
                    Text(
                        text = name,
                        modifier = Modifier.padding(top = 12.dp).padding(horizontal = 24.dp),
                        color = colorResource(R.color.kibooz_profile_name),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                if (hasContent) {
                    Text(
                        text = if (profile != null) {
                            stringResource(R.string.anak_wali_subjudul, profile.fullName, profile.className)
                        } else {
                            stringResource(R.string.anak_wali_subjudul_tanpa_anak)
                        },
                        modifier = Modifier.padding(top = 2.dp).padding(horizontal = 24.dp),
                        color = colorResource(R.color.kibooz_profile_subtitle),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    ProfileDetailButton(
                        label = stringResource(if (state.noChild) R.string.anak_tambah_anak else R.string.anak_lihat_detail),
                        onClick = { onDetail(state) },
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 14.dp)
                    )
                }
            }
            if (state.loading && !hasContent && loadError == null) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 18.dp).size(48.dp).align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (blocking) {
                val outage = state.outage
                if (outage != null) {
                    ConnectionStatusCard(
                        kind = outage,
                        onRetry = onRetry,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 18.dp)
                    )
                } else if (loadError != null) {
                    LoadErrorCard(
                        message = stringResource(loadError),
                        onRetry = onRetry,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 18.dp)
                    )
                }
            }
            ProfileMenuCard(items = menu, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 16.dp))
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun ProfilWaliPreview() {
    KiboozTheme {
        ProfilWaliScreen(
            state = WaliProfileState(loading = false, sessionName = "Wali Contoh", noChild = true),
            avatarUrl = null,
            avatarVersion = 0,
            avatarLoader = AvatarLoader(okhttp3.OkHttpClient()),
            onAvatar = {},
            onDetail = {},
            onRetry = {},
            menu = listOf(ProfileMenuItem(R.drawable.ic_key, "Ubah Kata sandi", {})),
            onTab = {},
            onScan = {}
        )
    }
}
