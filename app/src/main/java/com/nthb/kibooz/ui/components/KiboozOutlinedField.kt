package com.nthb.kibooz.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nthb.kibooz.R

/**
 * Kolom satu baris bergaris tepi dengan label mengambang, pengganti `TextInputLayout` gaya OutlinedBox.
 * [errorText] menggantikan [helperText] di bawah kolom.
 */
@Composable
fun KiboozOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorText: String? = null,
    helperText: String? = null,
    contentType: ContentType? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    placeholder: String? = null,
    counter: String? = null,
    counterDescription: String? = null,
    textSize: TextUnit = TextUnit.Unspecified,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    val supporting = errorText ?: helperText
    // Warna bawaan TextInputLayout Material 3, agar layar Compose sama dengan layar XML
    val border = colorResource(R.color.kibooz_field_border)
    val labelColor = colorResource(R.color.kibooz_field_label)
    // Kolom terkunci tetap harus menampilkan galatnya, jadi warna galat juga dipakai saat nonaktif
    val errorColor = MaterialTheme.colorScheme.error
    val disabledBorder = if (errorText != null) errorColor else border
    val disabledLabel = if (errorText != null) errorColor else labelColor
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics {
            if (contentType != null) this.contentType = contentType
            if (errorText != null) error(errorText)
        },
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.let {
            if (textSize == TextUnit.Unspecified) it else it.copy(fontSize = textSize)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = if (supporting == null && counter == null) {
            null
        } else {
            {
                Row {
                    // Galat diumumkan pembaca layar begitu muncul, seperti TextInputLayout
                    Text(
                        text = supporting.orEmpty(),
                        modifier = Modifier.weight(1f).then(
                            if (errorText != null) {
                                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            } else {
                                Modifier
                            }
                        )
                    )
                    if (counter != null) {
                        Text(
                            text = counter,
                            modifier = Modifier.padding(start = 16.dp).semantics {
                                if (counterDescription != null) contentDescription = counterDescription
                            }
                        )
                    }
                }
            }
        },
        trailingIcon = trailingIcon,
        isError = errorText != null,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = border,
            unfocusedLabelColor = labelColor,
            focusedSupportingTextColor = labelColor,
            unfocusedSupportingTextColor = labelColor,
            focusedTrailingIconColor = labelColor,
            unfocusedTrailingIconColor = labelColor,
            errorTrailingIconColor = labelColor,
            // Kolom yang dikunci tetap terbaca, layar yang meredupkannya bila perlu
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = disabledBorder,
            disabledLabelColor = disabledLabel,
            disabledSupportingTextColor = disabledLabel
        )
    )
}

/** [KiboozOutlinedField] untuk kata sandi, dengan tombol untuk menampilkan atau menyembunyikan isinya. */
@Composable
fun KiboozOutlinedPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    helperText: String? = null,
    contentType: ContentType = ContentType.Password,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    // Sengaja tidak disimpan: kata sandi kembali tersembunyi setelah layar diputar
    var visible by remember { mutableStateOf(false) }
    KiboozOutlinedField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        errorText = errorText,
        helperText = helperText,
        contentType = contentType,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            autoCorrectEnabled = false
        ),
        keyboardActions = keyboardActions,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(R.drawable.ic_eye),
                    contentDescription = stringResource(
                        if (visible) R.string.sembunyikan_kata_sandi else R.string.tampilkan_kata_sandi
                    )
                )
            }
        }
    )
}
