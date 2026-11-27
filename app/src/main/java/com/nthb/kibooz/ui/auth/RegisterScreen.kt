package com.nthb.kibooz.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.data.session.Role
import com.nthb.kibooz.ui.components.ConnectionStatusCard
import com.nthb.kibooz.ui.components.KiboozOutlinedField
import com.nthb.kibooz.ui.components.KiboozOutlinedPasswordField
import com.nthb.kibooz.ui.components.KiboozPrimaryButton
import com.nthb.kibooz.ui.theme.KiboozTheme

/** Akun Google yang sedang menyelesaikan pendaftaran. Email dikunci dan kata sandi tidak diminta. */
internal data class GooglePrefill(val idToken: String, val email: String, val fullName: String)

/** Formulir pendaftaran Guru atau Wali Murid. Isian yang tidak milik [role] tidak ditampilkan. */
@Composable
internal fun RegisterScreen(
    role: Role,
    state: RegisterUiState,
    google: GooglePrefill?,
    onRegister: (RegisterForm) -> Unit,
    onLogin: () -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf(google?.fullName.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(google?.email.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var nip by rememberSaveable { mutableStateOf("") }
    var schoolName by rememberSaveable { mutableStateOf("") }
    var className by rememberSaveable { mutableStateOf("") }
    var gradeLevel by rememberSaveable { mutableStateOf("") }
    var academicYear by rememberSaveable { mutableStateOf("") }
    var classCode by rememberSaveable { mutableStateOf("") }
    var childName by rememberSaveable { mutableStateOf("") }
    var nisn by rememberSaveable { mutableStateOf("") }
    val isGuru = role == Role.GURU

    val submit = {
        onRegister(
            RegisterForm(
                role = role,
                fullName = fullName,
                email = email,
                password = password,
                phone = phone,
                nip = nip,
                schoolName = schoolName,
                className = className,
                gradeLevel = gradeLevel,
                academicYear = academicYear,
                classCode = classCode,
                childName = childName,
                nisn = nisn,
                googleIdToken = google?.idToken
            )
        )
    }

    AuthSheet(sheetFraction = 0.84f, logoSize = DpSize(90.dp, 72.dp), contentTopPadding = 28.dp) {
        Text(
            text = stringResource(if (isGuru) R.string.daftar_judul_guru else R.string.daftar_judul_wali),
            modifier = Modifier.semantics { heading() },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = when {
                google != null -> stringResource(R.string.daftar_subjudul_google, google.email)
                isGuru -> stringResource(R.string.daftar_subjudul_guru)
                else -> stringResource(R.string.daftar_subjudul_wali)
            },
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )

        FormField(
            value = fullName,
            onValueChange = { fullName = it },
            label = R.string.hint_nama_lengkap,
            error = state.fieldErrors[RegisterField.FULL_NAME],
            capitalization = KeyboardCapitalization.Words,
            contentType = ContentType.PersonFullName
        )
        FormField(
            value = email,
            onValueChange = { email = it },
            label = R.string.hint_email_daftar,
            error = state.fieldErrors[RegisterField.EMAIL],
            keyboardType = KeyboardType.Email,
            contentType = ContentType.EmailAddress,
            enabled = google == null
        )
        if (google == null) {
            KiboozOutlinedPasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.hint_password_daftar),
                modifier = Modifier.padding(top = 12.dp),
                errorText = state.fieldErrors[RegisterField.PASSWORD]?.let { stringResource(it) },
                contentType = ContentType.NewPassword
            )
        }
        FormField(
            value = phone,
            onValueChange = { phone = it },
            label = R.string.hint_telepon_opsional,
            error = state.fieldErrors[RegisterField.PHONE],
            keyboardType = KeyboardType.Phone,
            contentType = ContentType.PhoneNumber
        )

        if (isGuru) {
            FormField(value = nip, onValueChange = { nip = it }, label = R.string.hint_nip_opsional)
            FormField(
                value = schoolName,
                onValueChange = { schoolName = it },
                label = R.string.hint_sekolah_opsional,
                capitalization = KeyboardCapitalization.Words
            )
            FormField(
                value = className,
                onValueChange = { className = it },
                label = R.string.hint_nama_kelas,
                error = state.fieldErrors[RegisterField.CLASS_NAME],
                capitalization = KeyboardCapitalization.Words
            )
            FormField(value = gradeLevel, onValueChange = { gradeLevel = it }, label = R.string.hint_tingkat_opsional)
            FormField(
                value = academicYear,
                onValueChange = { academicYear = it },
                label = R.string.hint_tahun_ajaran_opsional,
                imeAction = ImeAction.Done
            )
        } else {
            FormField(
                value = classCode,
                onValueChange = { classCode = it },
                label = R.string.hint_kode_kelas,
                error = state.fieldErrors[RegisterField.CLASS_CODE],
                capitalization = KeyboardCapitalization.Characters
            )
            FormField(
                value = childName,
                onValueChange = { childName = it },
                label = R.string.hint_nama_anak,
                error = state.fieldErrors[RegisterField.CHILD_NAME],
                capitalization = KeyboardCapitalization.Words
            )
            FormField(
                value = nisn,
                onValueChange = { nisn = it },
                label = R.string.hint_nisn,
                error = state.fieldErrors[RegisterField.NISN],
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            )
        }

        state.outage?.let {
            ConnectionStatusCard(kind = it, onRetry = submit, modifier = Modifier.padding(top = 24.dp))
        }
        KiboozPrimaryButton(
            text = stringResource(R.string.btn_daftar),
            onClick = submit,
            modifier = Modifier.padding(top = 24.dp),
            enabled = !state.loading
        )
        Text(
            text = stringResource(R.string.sudah_punya_akun),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
                .minimumInteractiveComponentSize()
                .clickable(role = SemanticsRole.Button, onClick = onLogin)
                .padding(8.dp),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Satu isian teks pendaftaran dengan jarak antar isian 12dp. Isian terkunci diredupkan. */
@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    contentType: ContentType? = null,
    enabled: Boolean = true
) {
    KiboozOutlinedField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(label),
        // Isian terkunci diredupkan, kecuali saat ada galat yang harus terbaca
        modifier = Modifier.padding(top = 12.dp).alpha(if (enabled || error != null) 1f else 0.7f),
        enabled = enabled,
        errorText = error?.let { stringResource(it) },
        contentType = contentType,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = imeAction,
            // Isian lama juga tanpa koreksi otomatis, agar nama, email dan kode tidak diubah keyboard
            autoCorrectEnabled = false
        )
    )
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun RegisterWaliPreview() {
    KiboozTheme {
        RegisterScreen(role = Role.WALI, state = RegisterUiState(), google = null, onRegister = {}, onLogin = {})
    }
}
