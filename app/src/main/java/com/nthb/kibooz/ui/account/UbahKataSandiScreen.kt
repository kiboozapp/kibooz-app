package com.nthb.kibooz.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nthb.kibooz.R
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
            PasswordOutlinedField(
                value = current,
                onValueChange = { current = it },
                label = R.string.sandi_hint_sekarang,
                errorRes = state.fieldErrors[PasswordField.CURRENT],
                contentType = ContentType.Password,
                modifier = Modifier.padding(top = 20.dp)
            )
            PasswordOutlinedField(
                value = new,
                onValueChange = { new = it },
                label = R.string.sandi_hint_baru,
                errorRes = state.fieldErrors[PasswordField.NEW],
                contentType = ContentType.NewPassword,
                helperRes = R.string.sandi_bantuan_baru,
                modifier = Modifier.padding(top = 12.dp)
            )
            PasswordOutlinedField(
                value = confirm,
                onValueChange = { confirm = it },
                label = R.string.sandi_hint_konfirmasi,
                errorRes = state.fieldErrors[PasswordField.CONFIRM],
                contentType = ContentType.NewPassword,
                imeAction = ImeAction.Done,
                modifier = Modifier.padding(top = 12.dp)
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

/** Kolom kata sandi bergaris tepi dengan label mengambang, pengganti `TextInputLayout` dengan `password_toggle`. */
@Composable
private fun PasswordOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes errorRes: Int?,
    contentType: ContentType,
    modifier: Modifier = Modifier,
    @StringRes helperRes: Int? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    // Sengaja tidak disimpan: kata sandi kembali tersembunyi setelah layar diputar
    var visible by remember { mutableStateOf(false) }
    val errorText = errorRes?.let { stringResource(it) }
    val supporting = errorText ?: helperRes?.let { stringResource(it) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics {
            this.contentType = contentType
            if (errorText != null) error(errorText)
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(stringResource(label)) },
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(R.drawable.ic_eye),
                    contentDescription = stringResource(
                        if (visible) R.string.sembunyikan_kata_sandi else R.string.tampilkan_kata_sandi
                    ),
                    tint = colorResource(R.color.kibooz_text_secondary)
                )
            }
        },
        isError = errorText != null,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            autoCorrectEnabled = false
        ),
        singleLine = true
    )
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun UbahKataSandiPreview() {
    KiboozTheme { UbahKataSandiScreen(state = ChangePasswordState(), onBack = {}, onSave = { _, _, _ -> }) }
}
