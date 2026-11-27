package com.nthb.kibooz.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nthb.kibooz.R

/**
 * Kerangka bersama portal Guru dan Wali, yaitu isi layar di atas bilah bawah setinggi 72dp dengan tombol scan yang
 * menonjol di tengah. [tabs] mengisi baris bilah dan harus menyisakan ruang tengah untuk tombol scan. Tombol digambar
 * di atas isi, jadi isi yang bisa digulir perlu jarak bawah sendiri.
 */
@Composable
internal fun PortalScaffold(
    onScan: () -> Unit,
    @DrawableRes scanIcon: Int,
    @StringRes scanLabel: Int,
    barShape: Shape,
    modifier: Modifier = Modifier,
    tabs: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, barShape)
                    .background(MaterialTheme.colorScheme.surface, barShape)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = tabs
            )
        }
        ScanFab(
            onClick = onScan,
            icon = scanIcon,
            label = scanLabel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 36.dp)
        )
    }
}

/** Satu tab bilah bawah. Tab aktif berwarna ungu dengan titik kuning, tanpa efek sentuh seperti bilah di layar XML. */
@Composable
internal fun RowScope.BarTab(
    active: Boolean,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    iconSize: Dp = 24.dp
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = active },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
            modifier = Modifier.size(iconSize),
            tint = if (active) MaterialTheme.colorScheme.primary else colorResource(R.color.kibooz_text_muted)
        )
        if (active) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(6.dp)
                    .background(colorResource(R.color.kibooz_yellow_accent), CircleShape)
            )
        }
    }
}

@Composable
private fun ScanFab(onClick: () -> Unit, @DrawableRes icon: Int, @StringRes label: Int, modifier: Modifier = Modifier) {
    val gradient = Brush.linearGradient(
        colors = listOf(colorResource(R.color.kibooz_fab_start), colorResource(R.color.kibooz_fab_end)),
        start = Offset.Infinite,
        end = Offset.Zero
    )
    Box(
        modifier = modifier
            .size(58.dp)
            .shadow(16.dp, CircleShape)
            .clip(CircleShape)
            .background(gradient)
            .border(3.dp, Color.White, CircleShape)
            .clickable(
                interactionSource = null,
                indication = ripple(color = Color.White),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(icon), contentDescription = stringResource(label))
    }
}
