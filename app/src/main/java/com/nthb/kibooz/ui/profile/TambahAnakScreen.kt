package com.nthb.kibooz.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.ConnectionStatusCard
import com.nthb.kibooz.ui.components.KiboozOutlinedField
import com.nthb.kibooz.ui.components.KiboozTopBar
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.ui.wali.AddChildField
import com.nthb.kibooz.ui.wali.TambahAnakState

private const val CLASS_CODE_MAX = 12
private const val NISN_MAX = 30

/**
 * Formulir menambah anak dengan kode kelas dari guru. Isian disimpan di layar ini dan galat per isian datang dari
 * [state]. Kartu gangguan server menawarkan coba lagi dengan isian yang sama.
 */
@Composable
internal fun TambahAnakScreen(
    state: TambahAnakState,
    onBack: () -> Unit,
    onSubmit: (classCode: String, childName: String, nisn: String) -> Unit
) {
    var classCode by rememberSaveable { mutableStateOf("") }
    var childName by rememberSaveable { mutableStateOf("") }
    var nisn by rememberSaveable { mutableStateOf("") }
    // Galat sebuah isian disembunyikan begitu isinya diubah, dan muncul lagi bila kirim berikutnya masih gagal
    var edited by remember { mutableStateOf(emptySet<AddChildField>()) }

    fun errorOf(field: AddChildField): Int? = if (field in edited) null else state.fieldErrors[field]
    fun submit() {
        edited = emptySet()
        onSubmit(classCode, childName, nisn)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KiboozTopBar(title = stringResource(R.string.anak_tambah_judul), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 40.dp)
        ) {
            Text(
                text = stringResource(R.string.anak_tambah_pengantar),
                color = colorResource(R.color.kibooz_text_secondary),
                fontSize = 14.sp
            )
            KiboozOutlinedField(
                value = classCode,
                onValueChange = {
                    classCode = it.take(CLASS_CODE_MAX)
                    edited = edited + AddChildField.CLASS_CODE
                },
                label = stringResource(R.string.anak_hint_kode_kelas),
                modifier = Modifier.padding(top = 16.dp),
                errorText = errorOf(AddChildField.CLASS_CODE)?.let { stringResource(it) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                ),
                textSize = 14.sp
            )
            KiboozOutlinedField(
                value = childName,
                onValueChange = {
                    childName = it
                    edited = edited + AddChildField.CHILD_NAME
                },
                label = stringResource(R.string.anak_hint_nama_anak),
                modifier = Modifier.padding(top = 12.dp),
                errorText = errorOf(AddChildField.CHILD_NAME)?.let { stringResource(it) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                textSize = 14.sp
            )
            KiboozOutlinedField(
                value = nisn,
                onValueChange = {
                    nisn = it.filter { c -> c in '0'..'9' }.take(NISN_MAX)
                    edited = edited + AddChildField.NISN
                },
                label = stringResource(R.string.anak_hint_nisn),
                modifier = Modifier.padding(top = 12.dp),
                errorText = errorOf(AddChildField.NISN)?.let { stringResource(it) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                textSize = 14.sp
            )
            // Saat server tidak terjangkau, kartu gangguan menjelaskan dan menawarkan coba lagi
            state.outage?.let { ConnectionStatusCard(kind = it, onRetry = ::submit, modifier = Modifier.padding(top = 16.dp)) }
            SolidButton(
                text = stringResource(R.string.anak_tambah_kirim),
                onClick = ::submit,
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(26.dp),
                fontSize = 16.sp,
                enabled = !state.loading
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun TambahAnakPreview() {
    KiboozTheme { TambahAnakScreen(state = TambahAnakState(), onBack = {}, onSubmit = { _, _, _ -> }) }
}
