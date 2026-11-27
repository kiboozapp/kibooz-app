package com.nthb.kibooz.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ChildProfile
import com.nthb.kibooz.ui.components.AvatarImage
import com.nthb.kibooz.ui.components.ConnectionStatusCard
import com.nthb.kibooz.ui.components.KiboozWaliScaffold
import com.nthb.kibooz.ui.components.WaliTab
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.WaliProfileState
import com.nthb.kibooz.util.AvatarLoader

/**
 * Layar profil seorang anak beserta data Wali, di atas bilah bawah portal Wali. Kepala ungu memuat foto, nama anak
 * (bisa diketuk untuk memilih anak bila ada lebih dari satu) dan tombol edit. Kartu gangguan atau galat muat tampil
 * selama belum ada data sama sekali.
 */
@Composable
internal fun ProfilSiswaScreen(
    state: WaliProfileState,
    avatarLoader: AvatarLoader,
    avatarVersion: Int,
    onPickChild: () -> Unit,
    onEdit: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onTab: (WaliTab) -> Unit,
    onScan: () -> Unit
) {
    val profile = state.profile
    val loadError = state.loadError
    val blocking = loadError != null && profile == null

    KiboozWaliScaffold(selected = WaliTab.PROFILE, onTab = onTab, onScan = onScan) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            StudentHeader(
                state = state,
                avatarLoader = avatarLoader,
                avatarVersion = avatarVersion,
                onPickChild = onPickChild,
                onEdit = onEdit
            )
            Column(modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp)) {
                if (state.loading && profile == null && !state.noChild) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(vertical = 24.dp).size(48.dp).align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (blocking) {
                    val outage = state.outage
                    if (outage != null) {
                        ConnectionStatusCard(kind = outage, onRetry = onRetry)
                    } else if (loadError != null) {
                        LoadErrorCard(message = stringResource(loadError), onRetry = onRetry)
                    }
                }
                if (state.noChild) EmptyChildren(onAddChild = onAddChild)
                if (profile != null) {
                    ProfileContent(
                        state = state,
                        profile = profile,
                        onEdit = onEdit,
                        onAddChild = onAddChild,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

/** Pita ungu bersudut bulat dengan judul, foto 84dp, nama, kelas dan tombol edit. Nama dan kelas muncul setelah data termuat. */
@Composable
private fun StudentHeader(
    state: WaliProfileState,
    avatarLoader: AvatarLoader,
    avatarVersion: Int,
    onPickChild: () -> Unit,
    onEdit: () -> Unit
) {
    val profile = state.profile
    val gradient = Brush.verticalGradient(
        listOf(colorResource(R.color.kibooz_topbar_start), colorResource(R.color.kibooz_topbar_end))
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .statusBarsPadding()
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.anak_judul_profil_siswa),
            modifier = Modifier.align(Alignment.Start).semantics { heading() },
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        val photoDescription = stringResource(R.string.anak_avatar_siswa)
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .size(84.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .border(3.dp, Color.White, CircleShape)
                .semantics { contentDescription = photoDescription }
        ) {
            AvatarImage(
                url = profile?.avatarUrl,
                placeholder = R.drawable.img_avatar_siswa,
                loader = avatarLoader,
                modifier = Modifier.fillMaxSize(),
                version = avatarVersion
            )
        }
        if (profile != null) {
            val pickDescription = stringResource(R.string.anak_pilih_anak_desc, profile.fullName)
            // Dengan lebih dari satu anak, ketukan pada nama membuka pilihan anak
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 48.dp)
                    .then(
                        if (state.canPickChild) {
                            Modifier
                                .clickable(role = Role.Button, onClick = onPickChild)
                                .semantics { contentDescription = pickDescription }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.canPickChild) "${profile.fullName} ▾" else profile.fullName,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = stringResource(R.string.anak_kelas_ringkas, displayClassName(state, profile), profile.schoolName),
                color = colorResource(R.color.kibooz_student_class),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Surface(
                onClick = onEdit,
                modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp).semantics { role = Role.Button },
                shape = RoundedCornerShape(24.dp),
                color = colorResource(R.color.kibooz_student_edit_button)
            ) {
                Box(modifier = Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.anak_edit_profil),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Nama kelas, dengan penanda bila kelasnya sudah diarsipkan. */
@Composable
private fun displayClassName(state: WaliProfileState, profile: ChildProfile): String {
    val archived = state.children.firstOrNull { it.id == profile.id }?.classArchivedAt != null
    return if (archived) "${profile.className} ${stringResource(R.string.anak_kelas_diarsipkan)}" else profile.className
}

@Composable
private fun EmptyChildren(onAddChild: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.anak_kosong_judul),
            color = colorResource(R.color.kibooz_text_dark),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.anak_kosong_pesan),
            modifier = Modifier.padding(top = 4.dp),
            color = colorResource(R.color.kibooz_text_secondary),
            fontSize = 14.sp
        )
        SolidButton(
            text = stringResource(R.string.anak_tambah_anak),
            onClick = onAddChild,
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
            fontSize = 15.sp
        )
    }
}

@Composable
private fun ProfileContent(
    state: WaliProfileState,
    profile: ChildProfile,
    onEdit: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit
) {
    val purple = colorResource(R.color.kibooz_primary_purple)
    val empty = stringResource(R.string.anak_belum_diisi)
    val nisnStatus = when {
        profile.nisnNeedsUpdate || profile.nisn.isEmpty() -> R.string.anak_nisn_kosong
        profile.nisnVerified -> R.string.anak_nisn_terverifikasi
        else -> R.string.anak_nisn_belum_terverifikasi
    }
    val rank = if (profile.classRank > 0) "#${profile.classRank}" else stringResource(R.string.anak_belum_ada_peringkat)
    val className = displayClassName(state, profile)
    val childRows = listOf(
        R.string.anak_label_nisn to profile.nisn.ifEmpty { empty },
        R.string.anak_label_status_nisn to stringResource(nisnStatus),
        R.string.anak_label_kelas to stringResource(R.string.anak_kelas_ringkas, className, profile.gradeLevel),
        R.string.anak_label_sekolah to profile.schoolName,
        R.string.anak_label_tahun_ajaran to profile.academicYear,
        R.string.anak_label_peringkat to rank,
        R.string.anak_label_poin to profile.seasonPoints.toString()
    )
    val guardian = profile.guardian
    val guardianRows = listOf(
        R.string.anak_label_nama_wali to guardian.fullName,
        R.string.anak_label_email to guardian.email,
        R.string.anak_label_telepon to (guardian.phoneNumber?.takeIf { it.isNotBlank() } ?: empty),
        R.string.anak_label_whatsapp to (guardian.whatsappNumber?.takeIf { it.isNotBlank() } ?: empty),
        R.string.anak_label_alamat to (guardian.address?.takeIf { it.isNotBlank() } ?: empty)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        if (profile.nisnNeedsUpdate) {
            // NISN kosong karena guru memverifikasi nomor yang sama untuk anak lain
            val shape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
                    .background(colorResource(R.color.kibooz_nisn_prompt_surface), shape)
                    .border(1.dp, colorResource(R.color.kibooz_yellow_accent), shape)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.anak_nisn_perlu_isi_judul),
                    color = colorResource(R.color.kibooz_text_dark),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.anak_nisn_perlu_isi_pesan),
                    modifier = Modifier.padding(top = 2.dp),
                    color = colorResource(R.color.kibooz_text_dark),
                    fontSize = 13.sp
                )
                SolidButton(
                    text = stringResource(R.string.anak_nisn_isi_sekarang),
                    onClick = onEdit,
                    modifier = Modifier.padding(top = 10.dp).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(24.dp),
                    fontSize = 14.sp
                )
            }
        }
        InfoCard(title = stringResource(R.string.anak_bagian_siswa), rows = childRows)
        InfoCard(
            title = stringResource(R.string.anak_bagian_wali),
            rows = guardianRows,
            modifier = Modifier.padding(top = 16.dp)
        )
        // Tambah anak disembunyikan di batas 10 anak
        if (state.canAddChild) {
            OutlinedButton(
                onClick = onAddChild,
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = purple),
                border = BorderStroke(1.dp, purple)
            ) {
                Text(
                    text = stringResource(R.string.anak_tambah_anak),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = stringResource(R.string.anak_batas_tercapai),
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
                color = colorResource(R.color.kibooz_text_secondary),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        val deleteColor = colorResource(R.color.kibooz_delete_child_text)
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 52.dp),
            enabled = !state.deleting && !state.loading,
            shape = RoundedCornerShape(26.dp),
            // Seperti tombol di layar XML, warnanya sama saat nonaktif
            colors = ButtonDefaults.outlinedButtonColors(contentColor = deleteColor, disabledContentColor = deleteColor),
            border = BorderStroke(1.dp, colorResource(R.color.kibooz_delete_child_border))
        ) {
            Text(
                text = stringResource(R.string.anak_hapus),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Kartu putih dengan judul dan baris label di atas nilai. Setiap baris dibaca sebagai satu kesatuan. */
@Composable
private fun InfoCard(title: String, rows: List<Pair<Int, String>>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = colorResource(R.color.kibooz_text_dark),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            rows.forEach { (label, value) ->
                Column(modifier = Modifier.padding(top = 10.dp).fillMaxWidth().semantics(mergeDescendants = true) {}) {
                    Text(
                        text = stringResource(label),
                        color = colorResource(R.color.kibooz_text_secondary),
                        fontSize = 12.sp
                    )
                    Text(
                        text = value,
                        color = colorResource(R.color.kibooz_text_dark),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun ProfilSiswaEmptyPreview() {
    KiboozTheme {
        ProfilSiswaScreen(
            state = WaliProfileState(loading = false, noChild = true),
            avatarLoader = AvatarLoader(okhttp3.OkHttpClient()),
            avatarVersion = 0,
            onPickChild = {},
            onEdit = {},
            onAddChild = {},
            onDelete = {},
            onRetry = {},
            onTab = {},
            onScan = {}
        )
    }
}
