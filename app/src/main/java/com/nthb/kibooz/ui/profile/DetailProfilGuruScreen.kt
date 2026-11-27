package com.nthb.kibooz.ui.profile

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ClassSummary
import com.nthb.kibooz.data.remote.DailyMoodDistribution
import com.nthb.kibooz.ml.Emotion
import com.nthb.kibooz.ui.common.DonutMoodChartView
import com.nthb.kibooz.ui.common.MoodChartMath
import com.nthb.kibooz.ui.components.AvatarImage
import com.nthb.kibooz.ui.guru.resolveSelectedClass
import com.nthb.kibooz.ui.theme.KiboozTheme
import com.nthb.kibooz.util.AvatarLoader
import kotlin.math.roundToInt

/** Data diri Guru di layar detail. Teks kosong berarti belum dimuat. */
internal data class GuruDetail(
    val name: String = "",
    val joinLine: String = "",
    val email: String = "",
    val nip: String = "",
    val phone: String = "",
    val address: String = "",
    val avatarUrl: String? = null,
    val avatarVersion: Int = 0
)

/** Statistik kinerja kelas yang dipilih. [note] terisi jika belum ada mood tercatat. */
internal data class GuruPerformance(
    val classes: List<ClassSummary> = emptyList(),
    val selectedClassId: String? = null,
    val fallbackClassName: String? = null,
    val note: String? = null,
    val distribution: DailyMoodDistribution = DailyMoodDistribution(senang = 0, bingung = 0, sedih = 0, marah = 0)
)

/** Layar detail profil Guru, yaitu kartu data diri, statistik mood kelas dengan grafik donat dan tombol ubah data. */
@Composable
internal fun DetailProfilGuruScreen(
    detail: GuruDetail,
    performance: GuruPerformance,
    avatarLoader: AvatarLoader,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSwitchClass: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.kibooz_profile_page))
            .navigationBarsPadding()
    ) {
        DetailTopBar(onBack = onBack, onEdit = onEdit)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp)
        ) {
            DetailIdentity(detail, avatarLoader)
            InfoCard(R.drawable.ic_badge_id, R.string.detail_profil_nip, detail.nip, Modifier.padding(top = 20.dp))
            InfoCard(R.drawable.ic_email, R.string.detail_profil_email, detail.email, Modifier.padding(top = 10.dp))
            InfoCard(R.drawable.ic_phone, R.string.detail_profil_telepon, detail.phone, Modifier.padding(top = 10.dp))
            AddressCard(detail.address, Modifier.padding(top = 10.dp))
            PerformanceHeader(performance, onSwitchClass)
            performance.note?.let { note ->
                Text(
                    text = note,
                    modifier = Modifier.padding(top = 4.dp),
                    color = colorResource(R.color.kibooz_profile_label),
                    fontSize = 12.sp
                )
            }
            MoodChartCard(performance.distribution)
            EditButton(onEdit)
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit, onEdit: () -> Unit) {
    val gradient = Brush.horizontalGradient(
        listOf(colorResource(R.color.kibooz_topbar_start), colorResource(R.color.kibooz_topbar_end))
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
            .statusBarsPadding()
            .height(64.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.kembali),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            text = stringResource(R.string.detail_profil_judul),
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_camera),
                contentDescription = stringResource(R.string.detail_profil_edit),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun DetailIdentity(detail: GuruDetail, avatarLoader: AvatarLoader) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val photoDescription = stringResource(R.string.detail_profil_avatar)
        Surface(
            modifier = Modifier.size(96.dp).semantics { contentDescription = photoDescription },
            shape = CircleShape,
            shadowElevation = 6.dp,
            border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface)
        ) {
            AvatarImage(
                url = detail.avatarUrl,
                placeholder = R.drawable.img_avatar_guru,
                loader = avatarLoader,
                modifier = Modifier.fillMaxSize(),
                version = detail.avatarVersion
            )
        }
        Text(
            text = detail.name,
            modifier = Modifier.padding(top = 10.dp),
            color = colorResource(R.color.kibooz_profile_name),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = detail.joinLine,
            modifier = Modifier.padding(top = 2.dp),
            color = colorResource(R.color.kibooz_profile_muted),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun InfoCard(@DrawableRes icon: Int, @StringRes label: Int, value: String, modifier: Modifier = Modifier) {
    DetailCard(modifier = modifier, corner = 16.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoIcon(icon)
            InfoLabel(label)
            Text(
                text = value,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                color = colorResource(R.color.kibooz_profile_menu_text),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun AddressCard(address: String, modifier: Modifier = Modifier) {
    DetailCard(modifier = modifier, corner = 16.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoIcon(R.drawable.ic_location)
                InfoLabel(R.string.detail_profil_alamat)
            }
            Text(
                text = address,
                modifier = Modifier.padding(start = 38.dp, top = 6.dp),
                color = colorResource(R.color.kibooz_profile_menu_text),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoIcon(@DrawableRes icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoLabel(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        modifier = Modifier.padding(start = 14.dp),
        color = colorResource(R.color.kibooz_profile_label),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DetailCard(modifier: Modifier, corner: Dp, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, colorResource(R.color.kibooz_profile_info_border)),
        content = content
    )
}

@Composable
private fun PerformanceHeader(performance: GuruPerformance, onSwitchClass: () -> Unit) {
    val active = resolveSelectedClass(performance.classes, performance.selectedClassId)
    val label = active?.name ?: performance.fallbackClassName?.takeIf { it.isNotBlank() }
    val canSwitch = performance.classes.size > 1
    Row(modifier = Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.detail_profil_statistik),
            modifier = Modifier.weight(1f).semantics { heading() },
            color = colorResource(R.color.kibooz_profile_name),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        if (label != null) {
            Row(
                modifier = Modifier
                    .background(colorResource(R.color.kibooz_lavender), RoundedCornerShape(14.dp))
                    .then(if (canSwitch) Modifier.clickable(role = Role.Button, onClick = onSwitchClass) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_class),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (canSwitch) {
                    Text(
                        text = "▾",
                        modifier = Modifier.padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodChartCard(distribution: DailyMoodDistribution) {
    val percentages = MoodChartMath.percentages(distribution)
    fun percent(emotion: Emotion) = percentages.getValue(emotion).roundToInt()
    DetailCard(modifier = Modifier.padding(top = 10.dp), corner = 20.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.detail_profil_persentase),
                color = colorResource(R.color.kibooz_profile_name),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            AndroidView(
                factory = { context -> DonutMoodChartView(context) },
                update = { chart -> chart.setDistribution(distribution) },
                modifier = Modifier.padding(top = 14.dp).size(140.dp)
            )
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ) {
                Legend(R.string.detail_profil_legenda_happy, percent(Emotion.SENANG), R.color.kibooz_legend_happy)
                Legend(R.string.detail_profil_legenda_bingung, percent(Emotion.BINGUNG), R.color.kibooz_legend_bingung)
                Legend(R.string.detail_profil_legenda_sedih, percent(Emotion.SEDIH), R.color.kibooz_legend_sedih)
                Legend(R.string.detail_profil_legenda_marah, percent(Emotion.MARAH), R.color.kibooz_legend_marah)
            }
        }
    }
}

@Composable
private fun Legend(@StringRes format: Int, percent: Int, @ColorRes color: Int) {
    Text(
        text = stringResource(format, percent),
        modifier = Modifier.padding(horizontal = 6.dp),
        color = colorResource(color),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun EditButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.detail_profil_ubah),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun DetailProfilGuruPreview() {
    KiboozTheme {
        DetailProfilGuruScreen(
            detail = GuruDetail(
                name = "Guru Contoh",
                joinLine = "Bergabung sejak 1 Januari 2026",
                email = "guru@example.org",
                nip = "-",
                phone = "-",
                address = "-"
            ),
            performance = GuruPerformance(fallbackClassName = "Kelas Contoh"),
            avatarLoader = AvatarLoader(okhttp3.OkHttpClient()),
            onBack = {},
            onEdit = {},
            onSwitchClass = {}
        )
    }
}
