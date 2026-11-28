package com.nthb.kibooz.ui.camera

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.ScanClaimData
import com.nthb.kibooz.ml.TrashCategory
import com.nthb.kibooz.ml.TrashResult
import com.nthb.kibooz.ui.theme.KiboozTheme

private val FRAME_SIZE = 260.dp
private val FRAME_GAP = 20.dp

/**
 * Layar pemindai sampah: preview kamera, bingkai target dengan lencana kategori, petunjuk tong sampah, dan kartu
 * bawah dengan poin dan tombol klaim. Dialog, toast dan snackbar diatur oleh activity, layar ini hanya menampilkan
 * [state].
 *
 * [cameraPreview] diisi PreviewView milik activity. Di `@Preview` diisi kotak gelap.
 */
@Composable
internal fun CameraScanSampahScreen(
    state: TrashScanState,
    /** Null selama izin belum diketahui: panel izin belum tampil dan tombol klaim nonaktif. */
    permission: CameraPermissionState?,
    lowLight: Boolean,
    placeholderModel: Boolean,
    flashOn: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onFlash: () -> Unit,
    onClaim: () -> Unit,
    onPermissionAction: () -> Unit,
    cameraPreview: @Composable () -> Unit
) {
    val cameraGranted = permission == CameraPermissionState.GRANTED

    Column(Modifier.fillMaxSize().background(ScanBackground)) {
        ScanTopBar(
            title = stringResource(R.string.sampah_pemindai_judul),
            flashOn = flashOn,
            onBack = onBack,
            onFlash = onFlash
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Viewport(state, lowLight, placeholderModel, cameraPreview)
            if (permission != null && !cameraGranted) {
                CameraPermissionPanel(
                    state = permission,
                    reason = stringResource(R.string.izin_kamera_sampah),
                    onAction = onPermissionAction
                )
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
        BottomControls(state, enabled = cameraGranted && !state.claiming && !state.loadingChildren, onClaim = onClaim)
    }
}

@Composable
private fun Viewport(
    state: TrashScanState,
    lowLight: Boolean,
    placeholderModel: Boolean,
    cameraPreview: @Composable () -> Unit
) {
    val detected = state.detected.takeIf { state.claimed == null }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        cameraPreview()

        if (lowLight) {
            ScanTag(
                text = stringResource(R.string.mood_cahaya_rendah),
                background = Color(0xCCE53935),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
            )
        }
        if (placeholderModel) {
            ScanTag(
                text = stringResource(R.string.model_contoh_banner),
                background = Color(0xCCF57C00),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
                fontSize = 12,
                horizontalPadding = 12,
                verticalPadding = 4
            )
        }

        Box(Modifier.align(Alignment.Center).size(FRAME_SIZE)) {
            // Garis bingkai digambar lebih dulu dan lencana di atasnya, seperti latar bingkai di layar XML.
            // Modifier.border menggambar di atas isinya, jadi garis akan melintasi lencana jika dipasang di Box ini
            Box(Modifier.fillMaxSize().border(3.dp, Color(0xE6FFFFFF), RoundedCornerShape(20.dp)))
            if (detected != null) DetectionBadge(detected)
        }

        // Petunjuk berada 20dp di bawah bingkai yang selalu di tengah, seperti di layar XML
        val frameBottom = (maxHeight - FRAME_SIZE) / 2 + FRAME_SIZE
        ScanTag(
            text = hint(detected),
            background = Color(0x99000000),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = frameBottom + FRAME_GAP),
            bold = false
        )
    }
}

/** Lencana di bagian atas bingkai. Warna hanya pelengkap, kategori selalu tertulis. */
@Composable
private fun DetectionBadge(detected: TrashResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(categoryColor(detected.category))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(categoryName(detected.category)),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.sampah_akurasi, (detected.confidence * 100).toInt()),
            color = Color(0xFFFFEBEE),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun BottomControls(state: TrashScanState, enabled: Boolean, onClaim: () -> Unit) {
    val claimed = state.claimed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.kibooz_white), RoundedCornerShape(24.dp))
            .navigationBarsPadding()
            .padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (claimed != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_flame),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = pointsText(claimed),
                    modifier = Modifier.padding(start = 6.dp),
                    color = Color(0xFFF67E02),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
        ScanButton(
            text = claimLabel(state),
            onClick = onClaim,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            enabled = enabled
        )
    }
}

@Composable
private fun hint(detected: TrashResult?): String = when {
    detected == null -> stringResource(R.string.sampah_mencari)
    detected.category == TrashCategory.B3 -> stringResource(R.string.sampah_petunjuk_b3)
    else -> stringResource(R.string.sampah_petunjuk, stringResource(shortName(detected.category)))
}

/** Poin dari server, bukan dihitung di aplikasi. Klaim di atas batas harian tetap dapat poin, tetapi dikurangi. */
@Composable
private fun pointsText(claimed: ScanClaimData): String {
    val points = when {
        claimed.pointsAdded <= 0 -> stringResource(R.string.sampah_tanpa_poin)
        claimed.reducedPoints -> stringResource(R.string.sampah_poin_dikurangi, claimed.pointsAdded)
        else -> stringResource(R.string.sampah_poin_diterima, claimed.pointsAdded)
    }
    val quota = if (claimed.reducedPoints) {
        stringResource(R.string.sampah_lewat_batas)
    } else {
        stringResource(R.string.sampah_sisa_klaim, claimed.remainingDailyScans)
    }
    return stringResource(R.string.sampah_poin_ringkasan, points, quota)
}

@Composable
private fun claimLabel(state: TrashScanState): String {
    val seconds = state.retryInSeconds
    return when {
        state.claimed != null -> stringResource(R.string.sampah_klaim_lagi)
        seconds != null -> stringResource(R.string.batas_laju_menunggu, seconds)
        state.claiming -> stringResource(R.string.sampah_memproses)
        else -> stringResource(R.string.sampah_klaim_tombol)
    }
}

@StringRes
private fun categoryName(category: TrashCategory) = when (category) {
    TrashCategory.ORGANIK -> R.string.sampah_kategori_organik
    TrashCategory.ANORGANIK -> R.string.sampah_kategori_anorganik
    TrashCategory.B3 -> R.string.sampah_kategori_b3
}

@StringRes
private fun shortName(category: TrashCategory) = when (category) {
    TrashCategory.ORGANIK -> R.string.sampah_organik
    TrashCategory.ANORGANIK -> R.string.sampah_anorganik
    TrashCategory.B3 -> R.string.sampah_kategori_b3
}

private fun categoryColor(category: TrashCategory) = when (category) {
    TrashCategory.ORGANIK -> Color(0xD92E7D32)
    TrashCategory.ANORGANIK -> Color(0xD91565C0)
    TrashCategory.B3 -> Color(0xD9C62828)
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun CameraScanSampahDetectedPreview() {
    KiboozTheme {
        CameraScanSampahScreen(
            state = TrashScanState(
                loadingChildren = false,
                childId = "anak-1",
                detected = TrashResult(TrashCategory.ANORGANIK, 0.86f)
            ),
            permission = CameraPermissionState.GRANTED,
            lowLight = true,
            placeholderModel = true,
            flashOn = false,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onFlash = {},
            onClaim = {},
            onPermissionAction = {},
            cameraPreview = { CameraPlaceholder() }
        )
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun CameraScanSampahClaimedPreview() {
    KiboozTheme {
        CameraScanSampahScreen(
            state = TrashScanState(
                loadingChildren = false,
                childId = "anak-1",
                claimed = ScanClaimData(
                    pointsAdded = 10,
                    totalPoints = 120,
                    seasonPoints = 80,
                    newRank = 3,
                    remainingDailyScans = 4
                )
            ),
            permission = CameraPermissionState.GRANTED,
            lowLight = false,
            placeholderModel = false,
            flashOn = true,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onFlash = {},
            onClaim = {},
            onPermissionAction = {},
            cameraPreview = { CameraPlaceholder() }
        )
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun CameraScanSampahPermissionPreview() {
    KiboozTheme {
        CameraScanSampahScreen(
            state = TrashScanState(),
            permission = CameraPermissionState.BLOCKED,
            lowLight = false,
            placeholderModel = false,
            flashOn = false,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onFlash = {},
            onClaim = {},
            onPermissionAction = {},
            cameraPreview = { CameraPlaceholder() }
        )
    }
}
