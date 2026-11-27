package com.nthb.kibooz.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.GuruProfileDetail
import com.nthb.kibooz.ui.components.AvatarImage
import com.nthb.kibooz.ui.components.KiboozTopBar
import com.nthb.kibooz.ui.guru.GuruEditField
import com.nthb.kibooz.ui.guru.GuruProfileState
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.AvatarLoader

/**
 * Formulir sunting profil Guru. Nama, telepon, dan alamat disimpan di layar ini dan terisi sekali dari
 * `state.profile`. NIP dan email hanya dibaca. Galat per isian datang dari [state].
 */
@Composable
internal fun EditProfilGuruScreen(
    state: GuruProfileState,
    avatarLoader: AvatarLoader,
    pickedAddress: String?,
    onAddressConsumed: () -> Unit,
    onBack: () -> Unit,
    onPickAvatar: () -> Unit,
    onPickMap: (currentAddress: String) -> Unit,
    onSave: (name: String, phone: String, address: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var filled by rememberSaveable { mutableStateOf(false) }
    // Galat sebuah isian disembunyikan begitu isinya diubah, dan muncul lagi bila simpan berikutnya masih gagal
    var edited by remember { mutableStateOf(emptySet<GuruEditField>()) }
    // Foto di URL yang sama bisa berganti isinya, jadi muat ulang gambar setiap unggahan selesai
    var uploading by remember { mutableStateOf(false) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.uploadingAvatar) {
        if (uploading && !state.uploadingAvatar) avatarVersion++
        uploading = state.uploadingAvatar
    }

    val profile = state.profile
    LaunchedEffect(profile != null) {
        if (profile != null && !filled) {
            filled = true
            name = profile.fullName
            phone = profile.phoneNumber.orEmpty()
            address = profile.address.orEmpty()
        }
    }
    LaunchedEffect(pickedAddress) {
        if (pickedAddress != null) {
            address = pickedAddress
            edited = edited + GuruEditField.ADDRESS
            onAddressConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KiboozTopBar(title = stringResource(R.string.edit_profil_guru_judul), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 40.dp)
        ) {
            AvatarPicker(profile = profile, avatarLoader = avatarLoader, version = avatarVersion, onPick = onPickAvatar)
            FormField(
                label = stringResource(R.string.edit_profil_guru_nama),
                value = name,
                onValueChange = {
                    name = it
                    edited = edited + GuruEditField.FULL_NAME
                },
                modifier = Modifier.padding(top = 20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                errorText = state.fieldErrors.visible(GuruEditField.FULL_NAME, edited)?.let { stringResource(it) }
            )
            FormField(
                label = stringResource(R.string.edit_profil_guru_nip),
                value = profile?.nip?.takeIf { it.isNotBlank() } ?: "-",
                onValueChange = {},
                modifier = Modifier.padding(top = 14.dp),
                locked = true
            )
            FormField(
                label = stringResource(R.string.edit_profil_guru_email),
                value = profile?.email ?: "-",
                onValueChange = {},
                modifier = Modifier.padding(top = 14.dp),
                locked = true
            )
            Text(
                text = stringResource(R.string.guru_profil_nip_email_tetap),
                modifier = Modifier.padding(top = 4.dp),
                color = colorResource(R.color.kibooz_text_muted),
                fontSize = 12.sp
            )
            FormField(
                label = stringResource(R.string.edit_profil_guru_telepon),
                value = phone,
                onValueChange = {
                    phone = it
                    edited = edited + GuruEditField.PHONE
                },
                modifier = Modifier.padding(top = 14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                errorText = state.fieldErrors.visible(GuruEditField.PHONE, edited)?.let { stringResource(it) }
            )
            FormField(
                label = stringResource(R.string.edit_profil_guru_alamat),
                value = address,
                onValueChange = {
                    address = it
                    edited = edited + GuruEditField.ADDRESS
                },
                modifier = Modifier.padding(top = 14.dp),
                minHeight = 80.dp,
                singleLine = false,
                placeholder = stringResource(R.string.edit_profil_guru_alamat_hint),
                errorText = state.fieldErrors.visible(GuruEditField.ADDRESS, edited)?.let { stringResource(it) },
                maxLines = 3
            )
            MapButton(enabled = !state.busy, onClick = { onPickMap(address) })
            Button(
                onClick = {
                    edited = emptySet()
                    onSave(name, phone, address)
                },
                modifier = Modifier.padding(top = 28.dp).fillMaxWidth().heightIn(min = 52.dp),
                enabled = !state.busy && profile != null,
                shape = RoundedCornerShape(26.dp),
                // Seperti tombol di layar XML, tampilannya sama saat nonaktif
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.kibooz_primary_purple),
                    contentColor = Color.White,
                    disabledContainerColor = colorResource(R.color.kibooz_primary_purple),
                    disabledContentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.edit_profil_guru_simpan),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Foto bulat 96dp dengan lencana kamera, dan teks petunjuk di bawahnya. Keduanya membuka pemilih foto. */
@Composable
private fun AvatarPicker(profile: GuruProfileDetail?, avatarLoader: AvatarLoader, version: Int, onPick: () -> Unit) {
    val purple = colorResource(R.color.kibooz_primary_purple)
    val badge = Brush.horizontalGradient(listOf(purple, colorResource(R.color.kibooz_purple_gradient_end)))
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val gantiFoto = stringResource(R.string.edit_profil_guru_ganti_foto)
        Box(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onPick)
                .semantics(mergeDescendants = true) { contentDescription = gantiFoto }
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
            ) {
                AvatarImage(
                    url = profile?.avatarUrl,
                    placeholder = R.drawable.img_avatar_guru,
                    loader = avatarLoader,
                    modifier = Modifier.fillMaxSize(),
                    version = version
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_camera),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .shadow(4.dp, CircleShape)
                    .background(badge, CircleShape)
                    .padding(7.dp)
                    .size(18.dp),
                tint = Color.White
            )
        }
        Text(
            text = stringResource(R.string.edit_profil_guru_ketuk_foto),
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(role = Role.Button, onClick = onPick)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                // Foto di atasnya sudah menjadi tombol yang sama, jadi teks ini hanya petunjuk bagi mata
                .clearAndSetSemantics {},
            color = colorResource(R.color.kibooz_primary_purple),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
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

/**
 * Isian dengan label di atasnya, sama dengan `bg_input_field`. Isian [locked] hanya dibaca dan berwarna redup.
 * [errorText] tampil sebagai teks di bawah isian, jadi galat tidak hanya terlihat dari warna garis tepi.
 */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    minHeight: Dp = 48.dp,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String? = null,
    errorText: String? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val dark = colorResource(R.color.kibooz_text_dark)
    val muted = colorResource(R.color.kibooz_text_muted)
    val borderColor = if (errorText != null) MaterialTheme.colorScheme.error else colorResource(R.color.kibooz_border)
    // Isi yang diganti dari luar, seperti alamat dari peta, menaruh kursor di akhir. Ketikan sendiri menjaga kursornya
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, color = dark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .semantics { if (errorText != null) error(errorText) },
            enabled = !locked,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (locked) muted else dark, fontSize = 14.sp),
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(colorResource(R.color.kibooz_primary_purple)),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .heightIn(min = minHeight)
                        .background(Color.White, shape)
                        .border(1.5.dp, borderColor, shape)
                        .padding(horizontal = 16.dp, vertical = if (singleLine) 0.dp else 10.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, color = muted, fontSize = 14.sp)
                    }
                    innerTextField()
                }
            }
        )
        if (errorText != null) {
            Text(
                text = errorText,
                // Galat diumumkan pembaca layar begitu muncul, seperti pada KiboozOutlinedField
                modifier = Modifier.padding(start = 16.dp, top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }
    }
}

/** Galat sebuah isian, atau null bila isinya sudah diubah sejak simpan terakhir. */
private fun Map<GuruEditField, Int>.visible(field: GuruEditField, edited: Set<GuruEditField>): Int? =
    if (field in edited) null else this[field]

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun EditProfilGuruPreview() {
    KiboozTheme {
        EditProfilGuruScreen(
            state = GuruProfileState(
                loading = false,
                profile = GuruProfileDetail(
                    id = "1",
                    fullName = "Ibu Siti Rahayu, S.Pd.",
                    nip = "198001012005012001",
                    schoolName = "TK Kibooz",
                    avatarUrl = null,
                    email = "siti@example.org",
                    phoneNumber = "+62 812-3456-7890",
                    address = "Jl. Melati No. 14, Lowokwaru, Kota Malang",
                    joinedAt = "2025-01-01T00:00:00Z"
                )
            ),
            avatarLoader = AvatarLoader(okhttp3.OkHttpClient()),
            pickedAddress = null,
            onAddressConsumed = {},
            onBack = {},
            onPickAvatar = {},
            onPickMap = {},
            onSave = { _, _, _ -> }
        )
    }
}
