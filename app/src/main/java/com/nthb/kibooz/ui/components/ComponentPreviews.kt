package com.nthb.kibooz.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.ui.theme.KiboozTheme

/** Pratinjau komponen bersama di Android Studio. Teksnya contoh. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ComponentsPreview() {
    KiboozTheme {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            KiboozTextField(value = "", onValueChange = {}, placeholder = "Email", leadingIcon = R.drawable.ic_email)
            KiboozTextField(
                value = "contoh",
                onValueChange = {},
                placeholder = "Kata sandi",
                leadingIcon = R.drawable.ic_lock,
                isPassword = true
            )
            ConnectionStatusCard(kind = OutageKind.UNAVAILABLE, onRetry = {})
            KiboozPrimaryButton(text = "Tombol utama", onClick = {})
            KiboozOutlinedButton(text = "Tombol kedua", onClick = {}, icon = R.drawable.ic_google)
        }
    }
}
