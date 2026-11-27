package com.nthb.kibooz.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.ConnectionStatusCard
import com.nthb.kibooz.ui.components.KiboozOutlinedField
import com.nthb.kibooz.ui.components.KiboozOutlinedPasswordField
import com.nthb.kibooz.ui.components.KiboozPrimaryButton
import com.nthb.kibooz.ui.components.KiboozTopBar
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.formatWaitSeconds

private const val DISABLED_ALPHA = 0.5f
private val boxShape = RoundedCornerShape(14.dp)

/** Isian kode verifikasi dan kata sandi akun, dengan tombol verifikasi dan kirim ulang. */
@Composable
internal fun VerifikasiEmailScreen(
    state: VerifyEmailState,
    onBack: () -> Unit,
    onCodeEdited: () -> Unit,
    onPasswordEdited: () -> Unit,
    onVerify: (code: String, password: String) -> Unit,
    onResend: (password: String) -> Unit,
    onRetry: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    // Sengaja tidak disimpan: kata sandi tidak masuk state tersimpan
    var password by remember { mutableStateOf("") }
    val hasPassword = password.isNotEmpty()
    val canVerify = code.length == VerifyEmailViewModel.CODE_LENGTH && hasPassword && !state.busy
    val canResend = hasPassword && !state.busy && state.resendSecondsLeft == 0
    val submit = { onVerify(code, password) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KiboozTopBar(title = stringResource(R.string.verifikasi_judul), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 40.dp)
        ) {
            Text(
                text = stringResource(state.notice),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorResource(R.color.kibooz_lavender), boxShape)
                    .padding(14.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            Text(
                text = stringResource(R.string.verifikasi_penjelasan, state.email),
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            KiboozOutlinedField(
                value = code,
                onValueChange = { typed ->
                    val allDigits = typed.filter { it in '0'..'9' }
                    val digits = allDigits.take(VerifyEmailViewModel.CODE_LENGTH)
                    // Kode yang sudah lengkap menolak sisipan, seperti android:maxLength
                    val overflow = code.length == VerifyEmailViewModel.CODE_LENGTH && allDigits.length > code.length
                    if (digits != code && !overflow) {
                        code = digits
                        onCodeEdited()
                    }
                },
                label = stringResource(R.string.verifikasi_label_kode),
                modifier = Modifier.padding(top = 20.dp),
                errorText = state.codeError?.let { stringResource(it) },
                placeholder = stringResource(R.string.verifikasi_hint_kode),
                counter = "${code.length}/${VerifyEmailViewModel.CODE_LENGTH}",
                counterDescription = stringResource(
                    R.string.verifikasi_kode_terisi,
                    code.length,
                    VerifyEmailViewModel.CODE_LENGTH
                ),
                textSize = 20.sp,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
            KiboozOutlinedPasswordField(
                value = password,
                onValueChange = {
                    if (it != password) {
                        password = it
                        onPasswordEdited()
                    }
                },
                label = stringResource(R.string.verifikasi_label_sandi),
                modifier = Modifier.padding(top = 12.dp),
                errorText = state.passwordError?.let { stringResource(it) },
                helperText = stringResource(R.string.verifikasi_bantuan_sandi),
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { submit() })
            )
            state.outage?.let {
                ConnectionStatusCard(kind = it, onRetry = onRetry, modifier = Modifier.padding(top = 16.dp))
            }
            KiboozPrimaryButton(
                text = stringResource(
                    if (state.loading) R.string.verifikasi_tombol_proses else R.string.verifikasi_tombol
                ),
                onClick = submit,
                modifier = Modifier.padding(top = 24.dp).alpha(if (canVerify) 1f else DISABLED_ALPHA),
                enabled = canVerify
            )
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .alpha(if (canResend) 1f else DISABLED_ALPHA)
                    .clip(boxShape)
                    .background(colorResource(R.color.kibooz_lavender))
                    .clickable(enabled = canResend, role = Role.Button) { onResend(password) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        state.resending -> stringResource(R.string.verifikasi_kirim_ulang_proses)
                        state.resendSecondsLeft > 0 -> stringResource(
                            R.string.verifikasi_kirim_ulang_tunggu,
                            formatWaitSeconds(state.resendSecondsLeft)
                        )
                        else -> stringResource(R.string.verifikasi_kirim_ulang)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun VerifikasiEmailPreview() {
    KiboozTheme {
        VerifikasiEmailScreen(
            state = VerifyEmailState(email = "wali@example.org", notice = R.string.verifikasi_judul),
            onBack = {},
            onCodeEdited = {},
            onPasswordEdited = {},
            onVerify = { _, _ -> },
            onResend = {},
            onRetry = {}
        )
    }
}
