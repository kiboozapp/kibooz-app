package com.nthb.kibooz.ui.wali

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.TrashBreakdown
import com.nthb.kibooz.data.remote.TrashStats
import com.nthb.kibooz.data.remote.TrashStatsStudent
import com.nthb.kibooz.ml.TrashCategory
import com.nthb.kibooz.ui.common.TrashPieChartView
import com.nthb.kibooz.ui.components.KiboozTopBar
import com.nthb.kibooz.ui.components.KiboozWaliScaffold
import com.nthb.kibooz.ui.components.WaliTab
import com.nthb.kibooz.ui.components.outlinedCard
import com.nthb.kibooz.ui.theme.KiboozTheme

/** Latar kartu dampak lingkungan, hanya dipakai di layar ini. */
private val impactCard = Color(0xFFF3EDFF)

/** Pengganti teks "…" di layout XML selama statistik belum termuat. */
private const val PLACEHOLDER = "…"

/**
 * Statistik pilah sampah seorang anak untuk semester yang berjalan. Pilihan periode (hari, minggu, bulan) di layout XML
 * selalu tersembunyi karena API hanya punya angka semester berjalan, jadi tidak dibuat di sini.
 */
@Composable
internal fun StatistikSampahScreen(
    state: TrashStatsState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onTab: (WaliTab) -> Unit,
    onScan: () -> Unit
) {
    val stats = state.stats
    KiboozWaliScaffold(selected = WaliTab.WASTE, onTab = onTab, onScan = onScan) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.fillMaxSize()) {
                KiboozTopBar(title = stringResource(R.string.sampah_statistik_judul), onBack = onBack)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)
                ) {
                    CategoryCard(stats = stats, modifier = Modifier.padding(top = 16.dp))
                    // Kotak sisa scan disembunyikan sampai statistik termuat
                    if (stats != null) QuotaCard(quota = stats.scanQuota(), modifier = Modifier.padding(top = 14.dp))
                    ImpactCard(stats = stats, modifier = Modifier.padding(top = 14.dp))
                }
            }
            SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Kartu diagram lingkaran dan legenda. Persentase hanya di diagram, legenda cukup jumlahnya. */
@Composable
private fun CategoryCard(stats: TrashStats?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .outlinedCard(20.dp, 3.dp, Color.White, MaterialTheme.colorScheme.outlineVariant)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.sampah_perbandingan_judul),
            color = colorResource(R.color.kibooz_text_dark),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        TrashPie(
            stats = stats,
            modifier = Modifier.padding(top = 12.dp).size(180.dp).align(Alignment.CenterHorizontally)
        )
        LegendRow(
            color = R.color.waste_organic,
            count = stats?.scansOf(TrashCategory.ORGANIK),
            title = R.string.sampah_organik_judul,
            example = R.string.sampah_organik_contoh,
            modifier = Modifier.padding(top = 16.dp)
        )
        LegendRow(
            color = R.color.waste_anorganic,
            count = stats?.scansOf(TrashCategory.ANORGANIK),
            title = R.string.sampah_anorganik_judul,
            example = R.string.sampah_anorganik_contoh,
            modifier = Modifier.padding(top = 10.dp)
        )
        // Garis pemisah: B3 tidak diberi poin, jadi dipisah dari dua kategori di atas
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colorResource(R.color.kibooz_border))
        )
        LegendRow(
            color = R.color.waste_b3,
            count = stats?.scansOf(TrashCategory.B3),
            title = R.string.sampah_b3_judul,
            example = R.string.sampah_b3_contoh,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/**
 * Diagram lingkaran bawaan layar XML (`TrashPieChartView`). `update` berjalan di setiap recomposition, jadi jumlahnya
 * disimpan di `tag` dan `setCounts` hanya dipanggil saat angkanya berganti. Sebelum termuat, view menggambar lingkaran abu-abu.
 */
@Composable
private fun TrashPie(stats: TrashStats?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> TrashPieChartView(context) },
        update = { view ->
            if (stats != null) {
                val counts = Triple(
                    stats.scansOf(TrashCategory.ORGANIK),
                    stats.scansOf(TrashCategory.ANORGANIK),
                    stats.scansOf(TrashCategory.B3)
                )
                if (view.tag != counts) {
                    view.tag = counts
                    view.setCounts(counts.first, counts.second, counts.third)
                }
            }
        },
        modifier = modifier
    )
}

/** Satu baris legenda: kotak warna, jumlah, lalu judul kategori dan contohnya. [count] null berarti belum termuat. */
@Composable
private fun LegendRow(
    @ColorRes color: Int,
    count: Int?,
    @StringRes title: Int,
    @StringRes example: Int,
    modifier: Modifier = Modifier
) {
    val categoryColor = colorResource(color)
    Row(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(top = 5.dp).size(14.dp).background(categoryColor))
        Text(
            text = count?.let { stringResource(R.string.sampah_item, it) } ?: PLACEHOLDER,
            modifier = Modifier.padding(start = 10.dp).width(64.dp),
            color = colorResource(R.color.kibooz_text_dark),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(title), color = categoryColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = stringResource(example), color = colorResource(R.color.kibooz_text_secondary), fontSize = 12.sp)
        }
    }
}

/** Sisa scan hari ini, berupa ajakan selama kuota masih ada atau pesan poin dikurangi saat sudah habis. */
@Composable
private fun QuotaCard(quota: ScanQuota, modifier: Modifier = Modifier) {
    val purple = colorResource(R.color.kibooz_primary_purple)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .outlinedCard(16.dp, 1.dp, colorResource(R.color.kibooz_lavender_light), purple)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_scan_waste),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(purple)
        )
        Text(
            text = stringResource(quota.messageRes, quota.remaining, quota.dailyLimit),
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            color = purple,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Poin semester, total scan dan peringkat kelas anak. Tanpa peringkat (0), kalimat peringkatnya tidak ditulis. */
@Composable
private fun ImpactCard(stats: TrashStats?, modifier: Modifier = Modifier) {
    val purple = colorResource(R.color.kibooz_primary_purple)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .outlinedCard(16.dp, 1.dp, impactCard, MaterialTheme.colorScheme.outlineVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_leaf),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            colorFilter = ColorFilter.tint(purple)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = stats?.let { stringResource(R.string.sampah_banner_judul, it.seasonPoints) } ?: PLACEHOLDER,
                color = purple,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stats?.let { bannerBody(it) } ?: PLACEHOLDER,
                color = colorResource(R.color.kibooz_text_muted),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun bannerBody(stats: TrashStats): String =
    if (stats.classRank > 0) {
        stringResource(R.string.sampah_banner_isi, stats.student.fullName, stats.totalScans, stats.totalPoints, stats.classRank)
    } else {
        stringResource(R.string.sampah_banner_isi_tanpa_peringkat, stats.student.fullName, stats.totalScans, stats.totalPoints)
    }

private val previewStats = TrashStats(
    student = TrashStatsStudent(id = "1", fullName = "Budi Santoso"),
    totalPoints = 120,
    seasonPoints = 85,
    classRank = 3,
    totalScans = 20,
    breakdown = listOf(
        TrashBreakdown(TrashCategory.ORGANIK, scans = 11, points = 55),
        TrashBreakdown(TrashCategory.ANORGANIK, scans = 7, points = 30),
        TrashBreakdown(TrashCategory.B3, scans = 2, points = 0)
    ),
    todayScans = 2,
    dailyLimit = 5,
    remainingDailyScans = 3
)

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun StatistikSampahScreenPreview() {
    KiboozTheme {
        StatistikSampahScreen(
            state = TrashStatsState(loading = false, stats = previewStats),
            snackbarHost = SnackbarHostState(),
            onBack = {},
            onTab = {},
            onScan = {}
        )
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun StatistikSampahScreenLoadingPreview() {
    KiboozTheme {
        StatistikSampahScreen(state = TrashStatsState(), snackbarHost = SnackbarHostState(), onBack = {}, onTab = {}, onScan = {})
    }
}
