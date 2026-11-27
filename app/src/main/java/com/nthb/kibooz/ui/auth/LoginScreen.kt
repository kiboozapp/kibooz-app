package com.nthb.kibooz.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.ConnectionStatusCard
import com.nthb.kibooz.ui.components.KiboozOutlinedButton
import com.nthb.kibooz.ui.components.KiboozPrimaryButton
import com.nthb.kibooz.ui.components.KiboozTextField
import com.nthb.kibooz.ui.theme.KiboozTheme

/** Formulir masuk. [onDemoLogin] hanya diisi pada build debug. */
@Composable
internal fun LoginScreen(
    state: LoginUiState,
    onLogin: (email: String, password: String) -> Unit,
    onGoogleLogin: () -> Unit,
    onRegister: () -> Unit,
    onDemoLogin: (() -> Unit)? = null
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val enabled = !state.loading
    val submit = { onLogin(email, password) }
    val muted = colorResource(R.color.kibooz_text_muted)

    AuthSheet {
        Text(
            text = stringResource(R.string.login_title),
            modifier = Modifier.semantics { heading() },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        KiboozTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.hint_email),
            leadingIcon = R.drawable.ic_email,
            modifier = Modifier.padding(top = 28.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            contentType = ContentType.EmailAddress,
            errorText = state.emailError?.let { stringResource(it) }
        )
        KiboozTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = stringResource(R.string.hint_password),
            leadingIcon = R.drawable.ic_lock,
            modifier = Modifier.padding(top = 16.dp),
            isPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            errorText = state.passwordError?.let { stringResource(it) }
        )
        // Belum ada alur lupa kata sandi, jadi teks ini belum bisa ditekan, sama seperti sebelumnya
        Text(
            text = stringResource(R.string.forgot_password),
            modifier = Modifier.align(Alignment.End).padding(top = 12.dp),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        state.outage?.let {
            ConnectionStatusCard(kind = it, onRetry = submit, modifier = Modifier.padding(top = 16.dp))
        }
        KiboozPrimaryButton(
            text = stringResource(R.string.btn_login),
            onClick = submit,
            modifier = Modifier.padding(top = 24.dp),
            enabled = enabled
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Divider(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.or_login_with),
                modifier = Modifier.padding(horizontal = 12.dp),
                color = muted,
                fontSize = 13.sp
            )
            Divider(Modifier.weight(1f))
        }
        KiboozOutlinedButton(
            text = stringResource(R.string.login_with_google),
            onClick = onGoogleLogin,
            modifier = Modifier.padding(top = 18.dp).alpha(if (enabled) 1f else 0.6f),
            icon = R.drawable.ic_google,
            enabled = enabled,
            onLongClick = onDemoLogin
        )
        Text(
            text = stringResource(R.string.belum_punya_akun),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 18.dp)
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = onRegister)
                .padding(8.dp),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        if (onDemoLogin != null) {
            Text(
                text = stringResource(R.string.login_google_demo_debug),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
                    .minimumInteractiveComponentSize()
                    .clickable(enabled = enabled, role = Role.Button, onClick = onDemoLogin)
                    .padding(6.dp),
                color = muted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Divider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(1.dp).background(MaterialTheme.colorScheme.outline))
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun LoginPreview() {
    KiboozTheme { LoginScreen(state = LoginUiState(), onLogin = { _, _ -> }, onGoogleLogin = {}, onRegister = {}) }
}
