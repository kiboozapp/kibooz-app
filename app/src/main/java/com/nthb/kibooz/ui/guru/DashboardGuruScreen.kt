package com.nthb.kibooz.ui.guru

import androidx.annotation.StringRes
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.data.remote.GuruDashboard
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ui.common.DonutMoodChartView
import com.nthb.kibooz.ui.common.MoodChartMath
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.ui.common.emoji
import com.nthb.kibooz.ui.components.AvatarImage
import com.nthb.kibooz.ui.components.ConnectionStatusCard
import com.nthb.kibooz.ui.components.GuruTab
import com.nthb.kibooz.ui.components.KiboozGuruScaffold
import com.nthb.kibooz.ui.components.PortalHeaderActions
import com.nthb.kibooz.ui.components.PortalRetryButton
import com.nthb.kibooz.ui.components.outlinedCard
import com.nthb.kibooz.ui.components.plainCard
import com.nthb.kibooz.ui.components.pullUp
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.AvatarLoader
import kotlin.math.roundToInt

private val headerShape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
private val lavenderText = Color(0xFFE5DEFF)
private const val PULSE_MIN_ALPHA = 0.35f
private const val PULSE_MILLIS = 800

/** Yang dibutuhkan layar dari luar selain [GuruDashboardState], supaya layar tidak membaca sesi atau jam sendiri. */
internal data class GuruDashboardHeaderInfo(
    @StringRes val greeting: Int,
    val date: String,
    val avatarUrl: String?
)

/**
 * Dasbor Guru, yaitu kepala ungu dengan foto dan sapaan, ringkasan kelas, donat mood hari ini, pintu masuk chat,
 * banner ranking dan bilah bawah portal Guru. Angka dan donat berkedip selama data belum tiba. [dashboard] adalah data
 * terakhir yang diterima, jadi angka kelas sebelumnya tetap tampil selagi kelas lain dimuat.
 */
@Composable
internal fun DashboardGuruScreen(
    state: GuruDashboardState,
    dashboard: GuruDashboard?,
    info: GuruDashboardHeaderInfo,
    avatarLoader: AvatarLoader,
    outageSecondsLeft: Int?,
    snackbarHost: SnackbarHostState,
    onRetry: () -> Unit,
    onPickClass: () -> Unit,
    onHistory: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit,
    onMoodStats: () -> Unit,
    onScan: () -> Unit,
    onChat: () -> Unit,
    onRanking: () -> Unit,
    onTab: (GuruTab) -> Unit
) {
    // Selama kelas lain dimuat, angka kelas sebelumnya tetap tampil dan berkedip, seperti layar XML
    val data = dashboard
    val pulse = pulseAlpha(active = state.loading && state.dashboard == null)

    KiboozGuruScaffold(selected = GuruTab.HOME, onTab = onTab, onScan = onScan) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Header(
                    data = data,
                    info = info,
                    avatarLoader = avatarLoader,
                    onHistory = onHistory,
                    onNotifications = onNotifications,
                    onProfile = onProfile
                )
                StatsCard(data = data, pulse = pulse, modifier = Modifier.padding(horizontal = 20.dp).pullUp(28.dp))
                ClassSwitcher(state = state, onClick = onPickClass)
                if (state.outage != null && outageSecondsLeft != null) {
                    ConnectionStatusCard(
                        kind = state.outage.kind,
                        onRetry = onRetry,
                        secondsLeft = outageSecondsLeft,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp)
                    )
                }
                // Muat pertama yang gagal tanpa gangguan server. Tombol ini tetap ada sampai ada data yang berhasil dimuat
                if (data == null && !state.loading && state.outage == null) PortalRetryButton(onClick = onRetry)
                MoodCard(data = data, pulse = pulse, onClick = onMoodStats, onScan = onScan)
                ChatEntryCard(onClick = onChat)
                RankingBanner(onClick = onRanking)
            }
            SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Kedipan pelan pengganti data yang belum tiba, dari terang penuh ke 35 persen selama 0,8 detik. */
@Composable
private fun pulseAlpha(active: Boolean): () -> Float {
    if (!active) return { 1f }
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    // Dibaca di fase gambar oleh pemakainya, supaya kedipan tidak menyusun ulang seluruh layar
    return { alpha.value }
}

@Composable
private fun Header(
    data: GuruDashboard?,
    info: GuruDashboardHeaderInfo,
    avatarLoader: AvatarLoader,
    onHistory: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit
) {
    val gradient = Brush.verticalGradient(
        listOf(colorResource(R.color.kibooz_topbar_start), colorResource(R.color.kibooz_topbar_end))
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, headerShape)
            .statusBarsPadding()
            // Baris judul setinggi 48dp untuk area sentuh ikon, jadi 8dp dikurangi dari jarak atas 20dp
            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 48.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_kibooz_bear),
                contentDescription = stringResource(R.string.logo_kibooz),
                modifier = Modifier.size(width = 38.dp, height = 32.dp)
            )
            Text(
                text = stringResource(R.string.dashboard_guru_judul),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            PortalHeaderActions(
                startIcon = R.drawable.ic_notification,
                startDescription = stringResource(R.string.dashboard_guru_notifikasi),
                onStart = onNotifications,
                endIcon = R.drawable.ic_history,
                endDescription = stringResource(R.string.dashboard_guru_riwayat),
                onEnd = onHistory,
                iconGap = 16.dp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val name = data?.teacher?.fullName
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onProfile)
            ) {
                AvatarImage(
                    url = data?.teacher?.avatarUrl ?: info.avatarUrl,
                    placeholder = R.drawable.img_avatar_guru,
                    loader = avatarLoader,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = if (name.isNullOrBlank()) {
                        stringResource(R.string.profil_foto_guru_umum)
                    } else {
                        stringResource(R.string.profil_foto_guru, name)
                    }
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(info.greeting),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        color = lavenderText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = info.date, color = lavenderText, fontSize = 12.sp, maxLines = 1)
                }
                Text(
                    text = name ?: "…",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatsCard(data: GuruDashboard?, pulse: () -> Float, modifier: Modifier = Modifier) {
    val overview = data?.classOverview
    val dividerColor = colorResource(R.color.kibooz_border)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .plainCard(radius = 20.dp, elevation = 6.dp)
            .padding(vertical = 14.dp)
            .height(IntrinsicSize.Min)
    ) {
        Stat(
            label = stringResource(R.string.dashboard_guru_jumlah_siswa),
            value = overview?.let { "🎯 ${it.totalStudents}" } ?: "…",
            pulse = pulse,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))
        Stat(
            label = stringResource(R.string.dashboard_guru_mood_dominan),
            value = if (overview == null) {
                "…"
            } else {
                overview.dominantMood?.let { "${it.emoji()} ${it.displayName()}" } ?: stringResource(R.string.mood_belum_dicatat)
            },
            pulse = pulse,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(dividerColor))
        Stat(
            label = overview?.let { stringResource(R.string.guru_hadir_label, it.presentStudents, it.totalStudents) }
                ?: stringResource(R.string.dashboard_guru_hadir_hari_ini),
            value = if (overview == null) {
                "…"
            } else {
                "✅ " + (
                    DashboardMath.attendancePercent(overview.presentStudents, overview.totalStudents)
                        ?.let { stringResource(R.string.guru_hadir_persen, it) }
                        ?: stringResource(R.string.guru_tanpa_persen)
                    )
            },
            pulse = pulse,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Stat(label: String, value: String, pulse: () -> Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text = label, color = colorResource(R.color.kibooz_text_secondary), fontSize = 12.sp)
        Text(
            text = value,
            modifier = Modifier.padding(top = 2.dp).graphicsLayer { alpha = pulse() },
            color = colorResource(R.color.kibooz_text_primary),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Kelas yang sedang dipilih. Hanya bisa diketuk, dan menampilkan pil "Ganti kelas", bila Guru punya lebih dari satu kelas. */
@Composable
private fun ClassSwitcher(state: GuruDashboardState, onClick: () -> Unit) {
    val activeClass = resolveSelectedClass(state.classes, state.selectedClassId ?: state.dashboard?.classOverview?.classId)
    val fallbackName = state.dashboard?.classOverview?.className
    val label = when {
        activeClass != null -> stringResource(R.string.guru_kelas_ringkas, activeClass.name, activeClass.totalStudents)
        !fallbackName.isNullOrBlank() -> fallbackName
        else -> return
    }
    val canSwitch = state.classes.size > 1
    Row(
        modifier = Modifier
            .padding(start = 20.dp, top = 14.dp, end = 20.dp)
            .fillMaxWidth()
            .outlinedCard(16.dp, 2.dp, Color.White, Color(0xFFE8E4F8))
            .clip(RoundedCornerShape(16.dp))
            .then(if (canSwitch) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_class),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colorResource(R.color.kibooz_primary_purple))
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(start = 10.dp, end = 8.dp),
            color = colorResource(R.color.kibooz_text_primary),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (canSwitch) {
            Text(
                text = stringResource(R.string.guru_ganti_kelas_pill),
                modifier = Modifier
                    .background(colorResource(R.color.kibooz_lavender), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = colorResource(R.color.kibooz_primary_purple),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MoodCard(data: GuruDashboard?, pulse: () -> Float, onClick: () -> Unit, onScan: () -> Unit) {
    val today = data?.dailyMoodDistribution
    val percentages = today?.let(MoodChartMath::percentages)
    Column(
        modifier = Modifier
            .padding(start = 20.dp, top = 12.dp, end = 20.dp)
            .fillMaxWidth()
            .plainCard(radius = 24.dp, elevation = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.dashboard_guru_mood_kelas),
                modifier = Modifier.weight(1f),
                color = colorResource(R.color.kibooz_text_primary),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorResource(R.color.kibooz_lavender))
                    .clickable(role = Role.Button, onClick = onScan)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painter = painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.dashboard_guru_scan_ulang),
                    modifier = Modifier.padding(start = 4.dp),
                    color = colorResource(R.color.kibooz_primary_purple),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            DonutChart(distribution = today, modifier = Modifier.padding(start = 8.dp).size(130.dp).graphicsLayer { alpha = pulse() })
            Column(modifier = Modifier.padding(start = 24.dp)) {
                Legend(R.string.legend_senang, R.color.mood_happy, percentages, Emotion.SENANG, first = true)
                Legend(R.string.legend_bingung, R.color.mood_bingung, percentages, Emotion.BINGUNG)
                Legend(R.string.legend_sedih, R.color.mood_sedih, percentages, Emotion.SEDIH)
                Legend(R.string.legend_marah, R.color.mood_marah, percentages, Emotion.MARAH)
            }
        }
        val overview = data?.classOverview
        Text(
            text = when {
                overview == null || today == null -> ""
                today.total == 0 -> stringResource(R.string.guru_kelas_kosong_mood, overview.className)
                else -> stringResource(R.string.guru_kelas_info, overview.className, overview.joinCode)
            },
            modifier = Modifier.align(Alignment.End).padding(top = 12.dp),
            color = colorResource(R.color.kibooz_text_muted),
            fontSize = 11.sp
        )
    }
}

/** Donat bawaan layar XML (`DonutMoodChartView`), digambar ulang dengan animasi hanya saat sebarannya berganti. */
@Composable
private fun DonutChart(distribution: DailyMoodDistribution?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> DonutMoodChartView(context) },
        update = { view ->
            if (distribution != null && view.tag != distribution) {
                view.tag = distribution
                view.setDistribution(distribution)
            }
        },
        modifier = modifier
    )
}

@Composable
private fun Legend(
    @StringRes label: Int,
    dotColor: Int,
    percentages: Map<Emotion, Float>?,
    emotion: Emotion,
    first: Boolean = false
) {
    Row(modifier = Modifier.padding(top = if (first) 0.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).background(colorResource(dotColor), CircleShape))
        Text(
            text = percentages?.let { "${stringResource(label)} (${it.getValue(emotion).roundToInt()}%)" } ?: stringResource(label),
            modifier = Modifier.padding(start = 8.dp),
            color = colorResource(R.color.kibooz_text_primary),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ChatEntryCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 20.dp, top = 16.dp, end = 20.dp)
            .fillMaxWidth()
            .outlinedCard(20.dp, 2.dp, Color(0xFFF5EEFD), Color(0xFFDDD0F9))
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = painterResource(R.drawable.ic_chat), contentDescription = null, modifier = Modifier.size(32.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = stringResource(R.string.chat_masuk_judul_guru),
                color = colorResource(R.color.kibooz_primary_purple),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.chat_masuk_isi_guru),
                color = colorResource(R.color.chat_entry_subtitle),
                fontSize = 12.sp
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colorResource(R.color.kibooz_primary_purple))
        )
    }
}

@Composable
private fun RankingBanner(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp)
            .fillMaxWidth()
            .height(130.dp)
            .shadow(4.dp, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Image(
            painter = painterResource(R.drawable.img_banner_ranking),
            contentDescription = stringResource(R.string.dashboard_guru_banner_ranking),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun DashboardGuruScreenPreview() {
    KiboozTheme {
        DashboardGuruScreen(
            state = GuruDashboardState(loading = true),
            dashboard = null,
            info = GuruDashboardHeaderInfo(greeting = R.string.sapaan_pagi, date = "Sab, 28 Nov 2026", avatarUrl = null),
            avatarLoader = AvatarLoader(okhttp3.OkHttpClient()),
            outageSecondsLeft = null,
            snackbarHost = SnackbarHostState(),
            onRetry = {},
            onPickClass = {},
            onHistory = {},
            onNotifications = {},
            onProfile = {},
            onMoodStats = {},
            onScan = {},
            onChat = {},
            onRanking = {},
            onTab = {}
        )
    }
}
