package com.nthb.kibooz.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.ConnectionStatusCard
import com.nthb.kibooz.ui.components.KiboozDangerButton
import com.nthb.kibooz.ui.components.KiboozTextField
import com.nthb.kibooz.ui.components.KiboozTopBar
import com.nthb.kibooz.ui.theme.KiboozTheme

private const val PASSWORD_MAX_LENGTH = 72
private const val EMAIL_MAX_LENGTH = 255
private val warningShape = RoundedCornerShape(16.dp)

/** Formulir hapus akun. Isian guru pengganti hanya tampil untuk Guru. Konfirmasinya dialog milik activity. */
@Composable
internal fun HapusAkunScreen(
    isTeacher: Boolean,
    state: DeleteAccountState,
    successor: String,
    onSuccessorChange: (String) -> Unit,
    onBack: () -> Unit,
    onDelete: (password: String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    val submit = { onDelete(password) }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KiboozTopBar(title = stringResource(R.string.hapus_akun_judul), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 40.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorResource(R.color.kibooz_warning_surface), warningShape)
                    .border(1.dp, colorResource(R.color.kibooz_warning_border), warningShape)
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.hapus_akun_peringatan_judul),
                    modifier = Modifier.semantics { heading() },
                    color = colorResource(R.color.kibooz_warning_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        if (isTeacher) R.string.hapus_akun_peringatan_guru else R.string.hapus_akun_peringatan_wali
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            FieldLabel(R.string.hapus_akun_label_sandi, Modifier.padding(top = 24.dp))
            KiboozTextField(
                value = password,
                onValueChange = { password = limited(password, it, PASSWORD_MAX_LENGTH) },
                placeholder = stringResource(R.string.hapus_akun_hint_sandi),
                leadingIcon = R.drawable.ic_lock,
                modifier = Modifier.padding(top = 6.dp),
                isPassword = true,
                keyboardOptions = KeyboardOptions(imeAction = if (isTeacher) ImeAction.Next else ImeAction.Done),
                placeholderColor = secondary,
                accessibilityLabel = stringResource(R.string.hapus_akun_label_sandi)
            )
            state.passwordError?.let { FieldError(it) }

            if (isTeacher) {
                val successorLabel = if (state.successorRequired) {
                    R.string.hapus_akun_label_pengganti_wajib
                } else {
                    R.string.hapus_akun_label_pengganti_opsional
                }
                FieldLabel(successorLabel, Modifier.padding(top = 20.dp))
                KiboozTextField(
                    value = successor,
                    onValueChange = { onSuccessorChange(limited(successor, it, EMAIL_MAX_LENGTH)) },
                    placeholder = stringResource(R.string.hapus_akun_hint_pengganti),
                    leadingIcon = null,
                    modifier = Modifier.padding(top = 6.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    contentType = ContentType.EmailAddress,
                    placeholderColor = secondary,
                    accessibilityLabel = stringResource(successorLabel)
                )
                state.successorError?.let { FieldError(it) }
                Text(
                    text = stringResource(R.string.hapus_akun_penjelasan_pengganti),
                    modifier = Modifier.padding(top = 6.dp),
                    color = secondary,
                    fontSize = 13.sp
                )
            }

            state.outage?.let {
                // Coba lagi memakai isian yang tampil sekarang dan meminta konfirmasi lagi, karena isiannya
                // bisa sudah diubah sejak gagal
                ConnectionStatusCard(kind = it, onRetry = submit, modifier = Modifier.padding(top = 20.dp))
            }
            KiboozDangerButton(
                text = when {
                    state.retryInSeconds != null ->
                        stringResource(R.string.hapus_akun_tombol_menunggu, state.retryInSeconds)
                    state.loading -> stringResource(R.string.hapus_akun_tombol_proses)
                    else -> stringResource(R.string.hapus_akun_tombol)
                },
                onClick = submit,
                modifier = Modifier.padding(top = 28.dp),
                enabled = !state.loading
            )
        }
    }
}

/** Batas panjang seperti `android:maxLength`. Sisipan di tengah ditolak, agar akhir isian tidak terpotong diam-diam. */
private fun limited(current: String, typed: String, max: Int): String = when {
    typed.length <= max -> typed
    // Pasangan surrogate, misalnya emoji, tidak dipotong di tengah
    typed.startsWith(current) -> typed.take(if (typed[max - 1].isHighSurrogate()) max - 1 else max)
    else -> current
}

@Composable
private fun FieldLabel(@StringRes label: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(label),
        modifier = modifier,
        color = colorResource(R.color.kibooz_text_dark),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun FieldError(@StringRes error: Int) {
    Text(
        text = stringResource(error),
        modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
        fontSize = 13.sp
    )
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun HapusAkunPreview() {
    KiboozTheme {
        HapusAkunScreen(
            isTeacher = true,
            state = DeleteAccountState(),
            successor = "",
            onSuccessorChange = {},
            onBack = {},
            onDelete = {}
        )
    }
}
