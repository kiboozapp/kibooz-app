package com.nthb.kibooz.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nthb.kibooz.R

private val sheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

/** Layar masuk: latar ungu dengan logo di atas dan lembar putih setinggi 78 persen yang bisa digulir. */
@Composable
internal fun AuthSheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            // Lembar tetap di atas bilah navigasi dan papan ketik
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
    ) {
        Box(modifier = Modifier.weight(0.22f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_kibooz_bear),
                contentDescription = stringResource(R.string.logo_kibooz),
                modifier = Modifier.size(width = 110.dp, height = 88.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(0.78f)
                .fillMaxWidth()
                .shadow(24.dp, sheetShape)
                .background(MaterialTheme.colorScheme.surface, sheetShape)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 24.dp),
            content = content
        )
    }
}
