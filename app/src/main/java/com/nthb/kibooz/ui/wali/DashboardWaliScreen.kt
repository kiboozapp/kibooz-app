package com.nthb.kibooz.ui.wali

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.WaliDashboard
import com.nthb.kibooz.ui.common.displayName
import com.nthb.kibooz.ui.common.emoji
import com.nthb.kibooz.ui.components.AvatarImage
import com.nthb.kibooz.ui.components.KiboozWaliScaffold
import com.nthb.kibooz.ui.components.PortalHeaderActions
import com.nthb.kibooz.ui.components.PortalRetryButton
import com.nthb.kibooz.ui.components.WaliTab
import com.nthb.kibooz.ui.components.outlinedCard
import com.nthb.kibooz.ui.components.pullUp
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.AvatarLoader
import com.nthb.kibooz.util.TimeFormat
import kotlin.math.roundToInt

private val headerShape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
private val headerText = Color(0xFFE0DBF5)
private val bannerRed = Color(0xFFD84315)
private val organicGreen = Color(0xFF2E7D32)
private val inorganicRed = Color(0xFFC62828)
private val lavenderCard = Color(0xFFF5EEFD)
private val lavenderStroke = Color(0xFFDDD0F9)

/** Semua aksi layar dasbor Wali, dikumpulkan supaya layar tidak membuka layar lain sendiri. */
internal data class DashboardWaliActions(
    val onRetry: () -> Unit,
    val onHistory: () -> Unit,
    val onNotifications: () -> Unit,
    val onStudentHeader: () -> Unit,
    val onLeaderboard: () -> Unit,
    val onTrashStats: () -> Unit,
    val onGuidance: () -> Unit,
    val onClassRoom: () -> Unit,
    val onChat: () -> Unit,
    val onTab: (WaliTab) -> Unit,
    val onScan: () -> Unit
)

/**
 * Dasbor Wali, yaitu kepala ungu dengan anak yang dipilih, tiga angka ringkas, banner peringkat, mood hari ini,
 * pintu masuk ruang kelas dan chat, ringkasan pilah sampah, panduan emosi dan bilah bawah portal Wali.
 */
@Composable
internal fun DashboardWaliScreen(
    state: WaliDashboardState,
    date: String,
    avatarLoader: AvatarLoader,
    snackbarHost: SnackbarHostState,
    actions: DashboardWaliActions
) {
    val data = state.dashboard
    KiboozWaliScaffold(selected = WaliTab.HOME, onTab = actions.onTab, onScan = actions.onScan) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Header(state = state, date = date, avatarLoader = avatarLoader, actions = actions)
                MetricRow(data = data, actions = actions, modifier = Modifier.pullUp(32.dp))
                // Muat pertama yang gagal. Tombol ini tetap ada sampai ada data yang berhasil dimuat
                if (data == null && !state.loading && !state.noChild) PortalRetryButton(onClick = actions.onRetry)
                Body(state = state, data = data, actions = actions)
            }
            SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Header(state: WaliDashboardState, date: String, avatarLoader: AvatarLoader, actions: DashboardWaliActions) {
    val student = state.dashboard?.student
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
                text = stringResource(R.string.dashboard_wali_judul),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            PortalHeaderActions(
                startIcon = R.drawable.ic_history,
                startDescription = stringResource(R.string.dashboard_wali_riwayat),
                onStart = actions.onHistory,
                endIcon = R.drawable.ic_notification,
                endDescription = stringResource(R.string.dashboard_wali_notifikasi),
                onEnd = actions.onNotifications,
                iconGap = 12.dp
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clickable(role = Role.Button, onClick = actions.onStudentHeader),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            ) {
                AvatarImage(
                    url = student?.avatarUrl,
                    placeholder = R.drawable.img_avatar_siswa,
                    loader = avatarLoader,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dashboard_wali_sapaan),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        color = headerText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = date, color = headerText, fontSize = 12.sp, maxLines = 1)
                }
                Text(
                    text = when {
                        student == null -> "…"
                        state.children.size > 1 -> "${student.fullName} ▾"
                        else -> student.fullName
                    },
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (student == null) "…" else "${student.className}\nNISN: ${student.nisn}",
                    color = headerText,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MetricRow(data: WaliDashboard?, actions: DashboardWaliActions, modifier: Modifier = Modifier) {
    val points = data?.pointsSummary
    val rank = points?.let { if (it.classRank > 0) "#${it.classRank}" else "-" } ?: "…"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        MetricCard(modifier = Modifier.weight(1f), onClick = actions.onLeaderboard) {
            MetricValue(rank, colorResource(R.color.kibooz_yellow_accent), 22)
            MetricLabel(stringResource(R.string.dashboard_wali_ranking_kelas))
        }
        MetricCard(modifier = Modifier.weight(1f), onClick = actions.onTrashStats) {
            MetricValue(points?.sortedCount?.toString() ?: "…", colorResource(R.color.kibooz_primary_purple), 22)
            MetricLabel(stringResource(R.string.dashboard_wali_pilah_sampah))
        }
        MetricCard(modifier = Modifier.weight(1f), onClick = null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.ic_flame), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = points?.totalPoints?.toString() ?: "…",
                    modifier = Modifier.padding(start = 3.dp),
                    color = Color(0xFFF67E02),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            MetricLabel(stringResource(R.string.dashboard_wali_total_poin))
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .height(94.dp)
            .outlinedCard(16.dp, 4.dp, Color.White, MaterialTheme.colorScheme.outlineVariant)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) { content() }
}

@Composable
private fun MetricValue(text: String, color: Color, size: Int) {
    Text(text = text, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun MetricLabel(text: String) {
    Text(text = text, color = colorResource(R.color.kibooz_text_muted), fontSize = 11.sp)
}

@Composable
private fun Body(state: WaliDashboardState, data: WaliDashboard?, actions: DashboardWaliActions) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 48.dp)) {
        RankingBanner(data = data, onClick = actions.onLeaderboard)
        SectionTitle(R.string.dashboard_wali_mood_judul)
        MoodCard(data = data, outline = outline, onClick = actions.onGuidance)
        SectionTitle(R.string.dashboard_wali_kelas_judul)
        EntryCard(
            icon = R.drawable.img_mapel_seni,
            iconSize = 52.dp,
            title = stringResource(R.string.dashboard_wali_kelas_kartu_judul),
            body = stringResource(R.string.dashboard_wali_kelas_kartu_isi),
            bodyColor = colorResource(R.color.kibooz_text_muted),
            onClick = actions.onClassRoom,
            modifier = Modifier.padding(top = 10.dp)
        )
        ChatCard(onClick = actions.onChat)
        SectionTitle(R.string.dashboard_wali_sampah_judul)
        TrashCard(data = data, state = state, outline = outline, onClick = actions.onTrashStats)
        val guidance = data?.recommendedGuidance
        if (guidance != null) {
            SectionTitle(R.string.dashboard_wali_panduan_judul)
            GuidanceCard(category = guidance.category, title = guidance.title, outline = outline, onClick = actions.onGuidance)
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        text = stringResource(text),
        modifier = Modifier.padding(top = 20.dp),
        color = colorResource(R.color.kibooz_text_dark),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun RankingBanner(data: WaliDashboard?, onClick: () -> Unit) {
    val points = data?.pointsSummary
    val rank = points?.let { if (it.classRank > 0) "#${it.classRank}" else "-" }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .outlinedCard(20.dp, 2.dp, Color(0xFFFFF8E7), Color(0xFFFFE0B2))
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.img_banner_ranking),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            contentScale = ContentScale.Fit
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = if (rank == null) "…" else stringResource(R.string.wali_banner_peringkat, rank),
                color = bannerRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (points == null) "…" else stringResource(R.string.wali_banner_poin_semester, points.seasonPoints),
                color = colorResource(R.color.kibooz_text_muted),
                fontSize = 12.sp
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(bannerRed)
        )
    }
}

@Composable
private fun MoodCard(data: WaliDashboard?, outline: Color, onClick: () -> Unit) {
    val mood = data?.todayMood
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .outlinedCard(20.dp, 3.dp, Color.White, outline)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = mood?.let { TimeFormat.localTime(it.recordedAt) }
                    ?.let { stringResource(R.string.wali_mood_hari_ini_jam, it) }
                    ?: stringResource(R.string.wali_mood_hari_ini),
                color = colorResource(R.color.kibooz_text_muted),
                fontSize = 12.sp
            )
            Text(
                text = when {
                    data == null -> "…"
                    mood != null -> "${mood.moodType.emoji()} ${mood.moodType.displayName()}"
                    else -> stringResource(R.string.mood_belum_dicatat)
                },
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(colorResource(R.color.kibooz_lavender), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                color = colorResource(R.color.kibooz_mood_bingung),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .background(colorResource(R.color.kibooz_lavender), RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_wali_catatan_guru),
                color = colorResource(R.color.kibooz_primary_purple),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (data == null) {
                    "…"
                } else {
                    mood?.teacherNotes?.takeIf { it.isNotBlank() } ?: stringResource(R.string.wali_belum_ada_catatan)
                },
                modifier = Modifier.padding(top = 2.dp),
                color = colorResource(R.color.kibooz_text_dark),
                fontSize = 12.sp
            )
        }
        PillButton(
            text = stringResource(R.string.dashboard_wali_lihat_panduan),
            fill = colorResource(R.color.kibooz_primary_purple),
            textColor = Color.White,
            onClick = onClick
        )
    }
}

/** Tombol lonjong setinggi 44dp di dalam kartu, pengganti `MaterialButton` dengan `cornerRadius` 22dp. */
@Composable
private fun PillButton(text: String, fill: Color, textColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Kartu lavender dengan ikon di kiri dan panah di kanan, dipakai untuk ruang kelas. */
@Composable
private fun EntryCard(
    icon: Int,
    iconSize: Dp,
    title: String,
    body: String,
    bodyColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .outlinedCard(20.dp, 2.dp, lavenderCard, lavenderStroke)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = minHeight)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize), contentScale = ContentScale.Fit)
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(text = title, color = colorResource(R.color.kibooz_primary_purple), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text = body, color = bodyColor, fontSize = 12.sp)
        }
        Image(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(colorResource(R.color.kibooz_primary_purple))
        )
    }
}

@Composable
private fun ChatCard(onClick: () -> Unit) {
    EntryCard(
        icon = R.drawable.ic_chat,
        iconSize = 32.dp,
        title = stringResource(R.string.chat_masuk_judul_wali),
        body = stringResource(R.string.chat_masuk_isi_wali),
        bodyColor = colorResource(R.color.chat_entry_subtitle),
        onClick = onClick,
        modifier = Modifier.padding(top = 12.dp),
        minHeight = 72.dp
    )
}

@Composable
private fun TrashCard(data: WaliDashboard?, state: WaliDashboardState, outline: Color, onClick: () -> Unit) {
    val points = data?.pointsSummary
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .outlinedCard(20.dp, 3.dp, Color.White, outline)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.img_trash_cans),
                contentDescription = null,
                modifier = Modifier.size(width = 90.dp, height = 72.dp),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                TrashRow(R.string.sampah_kategori_organik, points?.let { countWithShare(it.organicCount, it.sortedCount) }, organicGreen)
                TrashRow(
                    R.string.sampah_kategori_anorganik,
                    points?.let { countWithShare(it.anorganicCount, it.sortedCount) },
                    inorganicRed,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        val quota = state.scanQuota
        if (quota != null) {
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .background(colorResource(R.color.kibooz_lavender_light), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_scan_waste),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(colorResource(R.color.kibooz_primary_purple))
                )
                Text(
                    text = stringResource(quota.messageRes, quota.remaining, quota.dailyLimit),
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    color = colorResource(R.color.kibooz_primary_purple),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        PillButton(
            text = stringResource(R.string.dashboard_wali_lihat_statistik),
            fill = Color(0xFFF2EDFD),
            textColor = colorResource(R.color.kibooz_primary_purple),
            onClick = onClick
        )
    }
}

@Composable
private fun TrashRow(label: Int, value: String?, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color))
        Text(
            text = stringResource(label),
            modifier = Modifier.weight(1f).padding(start = 6.dp),
            color = colorResource(R.color.kibooz_text_dark),
            fontSize = 13.sp
        )
        Text(text = value ?: "…", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun countWithShare(count: Int, total: Int): String =
    if (total == 0) "$count" else "$count (${(count * 100f / total).roundToInt()}%)"

@Composable
private fun GuidanceCard(category: String, title: String, outline: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 8.dp)
            .fillMaxWidth()
            .outlinedCard(20.dp, 3.dp, Color.White, outline)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.img_penanganan_aman),
            contentDescription = null,
            modifier = Modifier.size(70.dp),
            contentScale = ContentScale.Fit
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = category,
                modifier = Modifier
                    .background(colorResource(R.color.kibooz_lavender), RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = colorResource(R.color.kibooz_mood_bingung),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                modifier = Modifier.padding(top = 4.dp),
                color = colorResource(R.color.kibooz_text_dark),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.dashboard_wali_panduan_isi),
                color = colorResource(R.color.kibooz_text_muted),
                fontSize = 12.sp
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(colorResource(R.color.kibooz_primary_purple))
        )
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun DashboardWaliScreenPreview() {
    KiboozTheme {
        DashboardWaliScreen(
            state = WaliDashboardState(loading = true),
            date = "Sab, 28 Nov 2026",
            avatarLoader = AvatarLoader(okhttp3.OkHttpClient()),
            snackbarHost = SnackbarHostState(),
            actions = DashboardWaliActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        )
    }
}
