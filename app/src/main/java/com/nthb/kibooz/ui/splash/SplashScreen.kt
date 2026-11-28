package com.nthb.kibooz.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.theme.KiboozTheme

// Ungu muda yang sama dengan teks tagline di layar XML sebelumnya
private val taglineColor = Color(0xFFE5DEFF)
private val logoSize = DpSize(125.dp, 100.dp)

/** Layar pembuka: maskot, nama aplikasi dan tagline di tengah latar ungu, selama sesi dibaca. */
@Composable
internal fun SplashScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_kibooz_bear),
            contentDescription = stringResource(R.string.logo_kibooz),
            modifier = Modifier.size(logoSize)
        )
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.app_tagline),
            modifier = Modifier.padding(top = 4.dp),
            color = taglineColor,
            fontSize = 18.sp
        )
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun SplashScreenPreview() {
    KiboozTheme { SplashScreen() }
}
