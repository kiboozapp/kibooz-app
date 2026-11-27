package com.nthb.kibooz.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nthb.kibooz.R

/** Tab pada bilah bawah Wali. Tombol scan sampah di tengah bukan tab. */
enum class WaliTab { HOME, WASTE, RANKING, PROFILE }

/** Kerangka layar portal Wali, yaitu bilah bawah putih bersudut bulat dengan tombol scan sampah di tengah. Sama dengan bilah di layar XML. */
@Composable
fun KiboozWaliScaffold(
    selected: WaliTab,
    onTab: (WaliTab) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    PortalScaffold(
        onScan = onScan,
        scanIcon = R.drawable.ic_scan_waste,
        scanLabel = R.string.scan_sampah_ai,
        barShape = RoundedCornerShape(24.dp),
        modifier = modifier,
        tabs = {
            WaliBarTab(WaliTab.HOME, selected, R.drawable.ic_home, R.string.tab_wali_beranda, onTab)
            WaliBarTab(WaliTab.WASTE, selected, R.drawable.ic_stats, R.string.tab_wali_sampah, onTab)
            Spacer(Modifier.weight(1f))
            WaliBarTab(WaliTab.RANKING, selected, R.drawable.ic_trophy, R.string.tab_wali_peringkat, onTab)
            WaliBarTab(WaliTab.PROFILE, selected, R.drawable.ic_profile, R.string.tab_wali_profil, onTab, 26.dp)
        },
        content = content
    )
}

@Composable
private fun RowScope.WaliBarTab(
    tab: WaliTab,
    selected: WaliTab,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onTab: (WaliTab) -> Unit,
    iconSize: Dp = 24.dp
) {
    BarTab(active = tab == selected, icon = icon, label = label, onClick = { onTab(tab) }, iconSize = iconSize)
}
