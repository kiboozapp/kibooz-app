package com.nthb.kibooz.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.KiboozOutlinedPasswordField
import com.nthb.kibooz.ui.components.KiboozPrimaryButton
import com.nthb.kibooz.ui.components.KiboozTopBar
import com.nthb.kibooz.ui.theme.KiboozTheme

/** Formulir ganti kata sandi. Galat per isian datang dari [state]. */
@Composable
internal fun UbahKataSandiScreen(
    state: ChangePasswordState,
    onBack: () -> Unit,
    onSave: (current: String, new: String, confirm: String) -> Unit
) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KiboozTopBar(title = stringResource(R.string.sandi_judul), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 40.dp)
        ) {
            Text(
                text = stringResource(R.string.sandi_info),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            KiboozOutlinedPasswordField(
                value = current,
                onValueChange = { current = it },
                label = stringResource(R.string.sandi_hint_sekarang),
                modifier = Modifier.padding(top = 20.dp),
                errorText = state.fieldErrors[PasswordField.CURRENT]?.let { stringResource(it) }
            )
            KiboozOutlinedPasswordField(
                value = new,
                onValueChange = { new = it },
                label = stringResource(R.string.sandi_hint_baru),
                modifier = Modifier.padding(top = 12.dp),
                errorText = state.fieldErrors[PasswordField.NEW]?.let { stringResource(it) },
                helperText = stringResource(R.string.sandi_bantuan_baru),
                contentType = ContentType.NewPassword
            )
            KiboozOutlinedPasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = stringResource(R.string.sandi_hint_konfirmasi),
                modifier = Modifier.padding(top = 12.dp),
                errorText = state.fieldErrors[PasswordField.CONFIRM]?.let { stringResource(it) },
                contentType = ContentType.NewPassword,
                imeAction = ImeAction.Done
            )
            KiboozPrimaryButton(
                text = stringResource(R.string.sandi_simpan),
                onClick = { onSave(current, new, confirm) },
                modifier = Modifier.padding(top = 24.dp),
                enabled = !state.loading
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun UbahKataSandiPreview() {
    KiboozTheme { UbahKataSandiScreen(state = ChangePasswordState(), onBack = {}, onSave = { _, _, _ -> }) }
}
