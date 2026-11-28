package com.nthb.kibooz.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.wrapContentWidth
import com.nthb.kibooz.data.remote.ChildProfile
import com.nthb.kibooz.data.remote.Guardian
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.AvatarImage
import com.nthb.kibooz.ui.components.ConnectionStatusCard
import com.nthb.kibooz.ui.components.KiboozOutlinedField
import com.nthb.kibooz.ui.components.KiboozTopBar
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.EditField
import com.nthb.kibooz.ui.wali.EditProfilState
import com.nthb.kibooz.util.AvatarLoader

private const val NISN_MAX = 30
private const val PHONE_MAX = 30

/** Karakter yang boleh ada di nomor telepon, sama dengan pola di server. Isian yang ditempel ikut disaring. */
private fun isPhoneChar(c: Char): Boolean = c.isDigit() || c in "+()- "

/** Gambar foto di satu kartu, yaitu foto anak atau foto Wali. */
internal data class EditPhoto(val url: String?, val placeholder: Int, val description: String)

/** Galat sebuah isian, atau null bila isinya sudah diubah sejak simpan terakhir. */
private fun EditProfilState.errorOf(field: EditField, edited: Set<EditField>): Int? =
    if (field in edited) null else fieldErrors[field]

/**
 * Formulir edit data anak dan data Wali, masing-masing dengan tombol simpan sendiri. Setiap kartu menyimpan isiannya
 * sendiri dan mengisinya sekali dari `state.profile`, supaya ketikan Wali tidak tertimpa saat data lain disimpan.
 * Galat per isian datang dari [state].
 */
@Composable
internal fun EditProfilSiswaScreen(
    state: EditProfilState,
    parentAvatarUrl: String?,
    avatarLoader: AvatarLoader,
    pickedAddress: String?,
    onAddressConsumed: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPickChildPhoto: () -> Unit,
    onPickParentPhoto: () -> Unit,
    onPickMap: (currentAddress: String) -> Unit,
    onSaveChild: (name: String, nisn: String) -> Unit,
    onSaveParent: (name: String, phone: String, whatsapp: String, address: String) -> Unit
) {
    // Foto di URL yang sama bisa berganti isinya, jadi muat ulang gambar setiap unggahan selesai
    var uploading by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.uploadingAvatar) {
        if (uploading && !state.uploadingAvatar) avatarVersion++
        uploading = state.uploadingAvatar
    }

    val profile = state.profile
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KiboozTopBar(title = stringResource(R.string.anak_edit_judul), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            if (profile == null) {
                LoadStatus(state = state, onRetry = onRetry)
            } else {
                ChildSection(
                    state = state,
                    profile = profile,
                    avatarLoader = avatarLoader,
                    avatarVersion = avatarVersion,
                    onPickPhoto = onPickChildPhoto,
                    onSave = onSaveChild
                )
                ParentSection(
                    state = state,
                    guardian = profile.guardian,
                    avatarUrl = parentAvatarUrl,
                    avatarLoader = avatarLoader,
                    avatarVersion = avatarVersion,
                    pickedAddress = pickedAddress,
                    onAddressConsumed = onAddressConsumed,
                    onPickPhoto = onPickParentPhoto,
                    onPickMap = onPickMap,
                    onSave = onSaveParent
                )
            }
        }
    }
}

/** Selama belum ada data, yaitu pemuat, kartu gangguan server, atau kartu galat muat. */
@Composable
private fun LoadStatus(state: EditProfilState, onRetry: () -> Unit) {
    val loadError = state.loadError
    val outage = state.outage
    when {
        loadError == null -> if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxWidth().wrapContentWidth().padding(vertical = 24.dp).size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        outage != null -> ConnectionStatusCard(kind = outage, onRetry = onRetry)
        // Anak yang sudah tidak ada tidak bisa dicoba lagi, jadi hanya pesannya yang tampil
        else -> LoadErrorCard(message = stringResource(loadError), onRetry = if (state.gone) null else onRetry)
    }
}

@Composable
private fun ChildSection(
    state: EditProfilState,
    profile: ChildProfile,
    avatarLoader: AvatarLoader,
    avatarVersion: Int,
    onPickPhoto: () -> Unit,
    onSave: (name: String, nisn: String) -> Unit
) {
    // Diisi sekali saat kartu pertama tampil, karena kartu ini hanya ada setelah profil termuat
    var name by rememberSaveable { mutableStateOf(profile.fullName) }
    var nisn by rememberSaveable { mutableStateOf(profile.nisn) }
    // Galat sebuah isian disembunyikan begitu isinya diubah, dan muncul lagi bila simpan berikutnya masih gagal
    var edited by remember { mutableStateOf(emptySet<EditField>()) }

    SectionCard(title = stringResource(R.string.anak_edit_bagian_anak)) {
        PhotoRow(
            photo = EditPhoto(profile.avatarUrl, R.drawable.img_avatar_siswa, stringResource(R.string.anak_avatar_siswa)),
            button = stringResource(R.string.avatar_ubah_foto_anak),
            enabled = !state.saving,
            avatarLoader = avatarLoader,
            avatarVersion = avatarVersion,
            onPick = onPickPhoto
        )
        if (profile.nisnNeedsUpdate) {
            Text(
                text = stringResource(R.string.anak_nisn_perlu_isi_pesan),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(colorResource(R.color.kibooz_nisn_prompt_surface))
                    .padding(12.dp),
                color = colorResource(R.color.kibooz_text_dark),
                fontSize = 13.sp
            )
        }
        KiboozOutlinedField(
            value = name,
            onValueChange = {
                name = it
                edited = edited + EditField.CHILD_NAME
            },
            label = stringResource(R.string.anak_hint_nama_anak),
            modifier = Modifier.padding(top = 12.dp),
            errorText = state.errorOf(EditField.CHILD_NAME, edited)?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            textSize = 14.sp
        )
        // Pesan galat NISN menggantikan bantuan selama ada, dan bantuan kembali setelah galat hilang
        KiboozOutlinedField(
            value = nisn,
            onValueChange = {
                nisn = it.filter(Char::isDigit).take(NISN_MAX)
                edited = edited + EditField.NISN
            },
            label = stringResource(R.string.anak_hint_nisn),
            modifier = Modifier.padding(top = 12.dp),
            errorText = state.errorOf(EditField.NISN, edited)?.let { stringResource(it) },
            helperText = if (profile.nisnVerified) stringResource(R.string.anak_hint_nisn_terverifikasi) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            textSize = 14.sp
        )
        SolidButton(
            text = stringResource(R.string.anak_simpan_anak),
            onClick = {
                edited = emptySet()
                onSave(name, nisn)
            },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
            fontSize = 15.sp,
            enabled = !state.saving
        )
    }
}

@Composable
private fun ParentSection(
    state: EditProfilState,
    guardian: Guardian,
    avatarUrl: String?,
    avatarLoader: AvatarLoader,
    avatarVersion: Int,
    pickedAddress: String?,
    onAddressConsumed: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickMap: (currentAddress: String) -> Unit,
    onSave: (name: String, phone: String, whatsapp: String, address: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(guardian.fullName) }
    var phone by rememberSaveable { mutableStateOf(guardian.phoneNumber.orEmpty()) }
    var whatsapp by rememberSaveable { mutableStateOf(guardian.whatsappNumber.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(guardian.address.orEmpty()) }
    var edited by remember { mutableStateOf(emptySet<EditField>()) }
    LaunchedEffect(pickedAddress) {
        if (pickedAddress != null) {
            address = pickedAddress
            edited = edited + EditField.ADDRESS
            onAddressConsumed()
        }
    }

    SectionCard(title = stringResource(R.string.anak_edit_bagian_wali), modifier = Modifier.padding(top = 16.dp)) {
        PhotoRow(
            photo = EditPhoto(avatarUrl, R.drawable.img_avatar_guru, stringResource(R.string.anak_avatar_wali)),
            button = stringResource(R.string.avatar_ubah_foto_wali),
            enabled = !state.saving,
            avatarLoader = avatarLoader,
            avatarVersion = avatarVersion,
            onPick = onPickPhoto
        )
        KiboozOutlinedField(
            value = name,
            onValueChange = {
                name = it
                edited = edited + EditField.PARENT_NAME
            },
            label = stringResource(R.string.anak_hint_nama_wali),
            modifier = Modifier.padding(top = 12.dp),
            errorText = state.errorOf(EditField.PARENT_NAME, edited)?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            textSize = 14.sp
        )
        // Email tidak bisa diubah, jadi hanya dibaca
        KiboozOutlinedField(
            value = guardian.email,
            onValueChange = {},
            label = stringResource(R.string.anak_hint_email),
            modifier = Modifier.padding(top = 12.dp),
            enabled = false,
            helperText = stringResource(R.string.anak_hint_email_bantuan),
            textSize = 14.sp
        )
        PhoneField(
            value = phone,
            onValueChange = {
                phone = it
                edited = edited + EditField.PHONE
            },
            label = stringResource(R.string.anak_hint_telepon),
            errorRes = state.errorOf(EditField.PHONE, edited)
        )
        PhoneField(
            value = whatsapp,
            onValueChange = {
                whatsapp = it
                edited = edited + EditField.WHATSAPP
            },
            label = stringResource(R.string.anak_hint_whatsapp),
            errorRes = state.errorOf(EditField.WHATSAPP, edited)
        )
        KiboozOutlinedField(
            value = address,
            onValueChange = {
                address = it
                edited = edited + EditField.ADDRESS
            },
            label = stringResource(R.string.anak_hint_alamat),
            modifier = Modifier.padding(top = 12.dp),
            errorText = state.errorOf(EditField.ADDRESS, edited)?.let { stringResource(it) },
            helperText = stringResource(R.string.anak_hint_kosongkan),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            textSize = 14.sp,
            singleLine = false,
            minLines = 3
        )
        MapButton(enabled = !state.saving, onClick = { onPickMap(address) })
        SolidButton(
            text = stringResource(R.string.anak_simpan_wali),
            onClick = {
                edited = emptySet()
                onSave(name, phone, whatsapp, address)
            },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
            fontSize = 15.sp,
            enabled = !state.saving
        )
    }
}

/** Isian nomor telepon yang menyaring karakter selain angka dan tanda nomor. */
@Composable
private fun PhoneField(value: String, onValueChange: (String) -> Unit, label: String, errorRes: Int?) {
    KiboozOutlinedField(
        value = value,
        onValueChange = { onValueChange(it.filter(::isPhoneChar).take(PHONE_MAX)) },
        label = label,
        modifier = Modifier.padding(top = 12.dp),
        errorText = errorRes?.let { stringResource(it) },
        helperText = stringResource(R.string.anak_hint_kosongkan),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        textSize = 14.sp
    )
}

/** Kartu putih dengan judul bagian dan isinya. */
@Composable
private fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Foto bulat 64dp dengan tombol ubah foto di sebelahnya. Foto dan tombol membuka pemilih foto yang sama. */
@Composable
private fun PhotoRow(
    photo: EditPhoto,
    button: String,
    enabled: Boolean,
    avatarLoader: AvatarLoader,
    avatarVersion: Int,
    onPick: () -> Unit
) {
    val purple = colorResource(R.color.kibooz_primary_purple)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .border(1.dp, colorResource(R.color.kibooz_border), CircleShape)
                .clickable(role = Role.Button, onClick = onPick)
                .semantics { contentDescription = photo.description }
        ) {
            AvatarImage(
                url = photo.url,
                placeholder = photo.placeholder,
                loader = avatarLoader,
                modifier = Modifier.fillMaxSize(),
                version = avatarVersion
            )
        }
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier.padding(start = 14.dp).heightIn(min = 44.dp),
            enabled = enabled,
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = purple, disabledContentColor = purple),
            border = BorderStroke(1.dp, purple)
        ) {
            Text(text = button, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MapButton(enabled: Boolean, onClick: () -> Unit) {
    val purple = colorResource(R.color.kibooz_primary_purple)
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colorResource(R.color.kibooz_lavender_light),
            contentColor = purple,
            disabledContainerColor = colorResource(R.color.kibooz_lavender_light),
            disabledContentColor = purple
        ),
        border = BorderStroke(1.dp, purple)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painter = painterResource(R.drawable.ic_location), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.lokasi_pilih_lewat_peta),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun EditProfilSiswaPreview() {
    KiboozTheme {
        EditProfilSiswaScreen(
            state = EditProfilState(loading = false, loadError = R.string.error_unknown),
            parentAvatarUrl = null,
            avatarLoader = AvatarLoader(okhttp3.OkHttpClient()),
            pickedAddress = null,
            onAddressConsumed = {},
            onBack = {},
            onRetry = {},
            onPickChildPhoto = {},
            onPickParentPhoto = {},
            onPickMap = {},
            onSaveChild = { _, _ -> },
            onSaveParent = { _, _, _, _ -> }
        )
    }
}
