package com.nthb.kibooz.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.nthb.kibooz.R

/** Tab pada bilah bawah Guru. Tombol scan di tengah bukan tab. */
enum class GuruTab { HOME, STATS, CLASS, PROFILE }

/** Kerangka layar portal Guru, yaitu bilah bawah putih rata dengan tombol scan mood di tengah. Sama dengan bilah di layar XML. */
@Composable
fun KiboozGuruScaffold(
    selected: GuruTab,
    onTab: (GuruTab) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    PortalScaffold(
        onScan = onScan,
        scanIcon = R.drawable.ic_scan_face,
        scanLabel = R.string.scan_face_mood_ai,
        barShape = RectangleShape,
        modifier = modifier,
        tabs = {
            GuruBarTab(GuruTab.HOME, selected, R.drawable.ic_home, R.string.tab_guru_beranda, onTab)
            GuruBarTab(GuruTab.STATS, selected, R.drawable.ic_stats, R.string.tab_guru_statistik, onTab)
            Spacer(Modifier.weight(1f))
            GuruBarTab(GuruTab.CLASS, selected, R.drawable.ic_class, R.string.tab_guru_kelas, onTab)
            GuruBarTab(GuruTab.PROFILE, selected, R.drawable.ic_profile, R.string.tab_guru_profil, onTab)
        },
        content = content
    )
}

@Composable
private fun RowScope.GuruBarTab(
    tab: GuruTab,
    selected: GuruTab,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onTab: (GuruTab) -> Unit
) {
    BarTab(active = tab == selected, icon = icon, label = label, onClick = { onTab(tab) })
}
