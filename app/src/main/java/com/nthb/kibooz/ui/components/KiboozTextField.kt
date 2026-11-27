package com.nthb.kibooz.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R

private val fieldShape = RoundedCornerShape(16.dp)

/**
 * Kolom isian satu baris dengan ikon, sama dengan `bg_input_field`. [placeholder] tampil selama kolom kosong.
 * Kolom kata sandi mendapat tombol untuk menampilkan atau menyembunyikan isinya. [errorText] tampil di bawah kolom.
 */
@Composable
fun KiboozTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    @DrawableRes leadingIcon: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    contentType: ContentType? = if (isPassword) ContentType.Password else null,
    errorText: String? = null
) {
    // Sengaja tidak disimpan: kata sandi kembali tersembunyi setelah layar diputar atau dipulihkan
    var passwordVisible by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val borderColor = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (contentType != null) this.contentType = contentType
                    if (errorText != null) error(errorText)
                },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            // Kata sandi selalu memakai keyboard kata sandi, agar tidak disarankan atau dipelajari keyboard
            keyboardOptions =
                if (isPassword) {
                    keyboardOptions.copy(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                } else {
                    keyboardOptions
                },
            keyboardActions = keyboardActions,
            visualTransformation =
                if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush = SolidColor(primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .heightIn(min = 54.dp)
                        .background(MaterialTheme.colorScheme.surface, fieldShape)
                        .border(1.5.dp, borderColor, fieldShape)
                        .padding(start = 16.dp, end = if (isPassword) 4.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(leadingIcon), contentDescription = null, modifier = Modifier.size(22.dp), tint = primary)
                    Box(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        // Tetap disusun saat kolom terisi, karena teks ini juga nama kolom bagi pembaca layar
                        Text(
                            text = placeholder,
                            color = if (value.isEmpty()) colorResource(R.color.kibooz_text_muted) else Color.Transparent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        innerTextField()
                    }
                    if (isPassword) {
                        // Area sentuh 48dp, ikonnya tetap 22dp seperti layar lama
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = enabled) {
                            Icon(
                                painter = painterResource(R.drawable.ic_eye),
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.sembunyikan_kata_sandi else R.string.tampilkan_kata_sandi
                                ),
                                modifier = Modifier.size(22.dp),
                                tint = primary
                            )
                        }
                    }
                }
            }
        )
        // Ditampilkan sebagai teks, agar galat tidak hanya terlihat dari warna garis tepi
        if (errorText != null) {
            Text(
                text = errorText,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }
    }
}
