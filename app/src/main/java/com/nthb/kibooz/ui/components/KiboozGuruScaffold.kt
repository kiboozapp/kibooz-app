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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nthb.kibooz.R

/** Tab pada bilah bawah Guru. Tombol scan di tengah bukan tab. */
enum class GuruTab { HOME, STATS, CLASS, PROFILE }

/**
 * Kerangka layar portal Guru, yaitu isi layar di atas bilah bawah putih setinggi 72dp dengan tombol scan mood yang
 * menonjol di tengah. Sama dengan bilah di layar XML. Tombol scan digambar di atas isi, jadi isi yang bisa digulir
 * perlu jarak bawah sendiri.
 */
@Composable
fun KiboozGuruScaffold(
    selected: GuruTab,
    onTab: (GuruTab) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarTab(GuruTab.HOME, selected, R.drawable.ic_home, R.string.tab_guru_beranda, onTab)
                BarTab(GuruTab.STATS, selected, R.drawable.ic_stats, R.string.tab_guru_statistik, onTab)
                Spacer(Modifier.weight(1f))
                BarTab(GuruTab.CLASS, selected, R.drawable.ic_class, R.string.tab_guru_kelas, onTab)
                BarTab(GuruTab.PROFILE, selected, R.drawable.ic_profile, R.string.tab_guru_profil, onTab)
            }
        }
        ScanFab(
            onClick = onScan,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 36.dp)
        )
    }
}

@Composable
private fun RowScope.BarTab(
    tab: GuruTab,
    selected: GuruTab,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onTab: (GuruTab) -> Unit
) {
    val active = tab == selected
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            // Tanpa efek sentuh, seperti bilah di layar XML
            .clickable(interactionSource = null, indication = null, role = Role.Tab) { onTab(tab) }
            .semantics { this.selected = active },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
            modifier = Modifier.size(24.dp),
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
private fun ScanFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
        Image(painter = painterResource(R.drawable.ic_scan_face), contentDescription = stringResource(R.string.scan_face_mood_ai))
    }
}
