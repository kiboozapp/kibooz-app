package com.nthb.kibooz.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R

/** Latar layar pemindai di belakang preview kamera. */
internal val ScanBackground = Color(0xFF121212)

private val buttonShape = RoundedCornerShape(25.dp)

/**
 * Bilah atas transparan gelap di atas preview kamera, dengan tombol kembali, judul dan tombol senter. Area sentuh
 * kedua tombol 48dp, dan jarak 9dp membuat ikon 26dp tetap 20dp dari tepi seperti layar XML.
 */
@Composable
internal fun ScanTopBar(title: String, flashOn: Boolean, onBack: () -> Unit, onFlash: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x80000000))
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.kembali),
                modifier = Modifier.size(26.dp),
                tint = Color.White
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 3.dp)
                .semantics { heading() },
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onFlash) {
            Icon(
                painter = painterResource(if (flashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off),
                contentDescription = stringResource(if (flashOn) R.string.senter_matikan else R.string.senter_nyalakan),
                modifier = Modifier.size(26.dp),
                tint = Color.White
            )
        }
    }
}

/** Label kecil berlatar di atas preview, seperti peringatan cahaya rendah dan penanda model contoh. */
@Composable
internal fun ScanTag(
    text: String,
    background: Color,
    modifier: Modifier = Modifier,
    fontSize: Int = 13,
    bold: Boolean = true,
    horizontalPadding: Int = 16,
    verticalPadding: Int = 6
) {
    Text(
        text = text,
        modifier = modifier
            .background(background)
            .padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
        color = Color.White,
        fontSize = fontSize.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
}

/**
 * Tombol ungu polos 50dp bersudut 25dp, sama dengan MaterialButton di layar pemindai XML. Saat nonaktif warnanya
 * tetap, hanya diredupkan menjadi 40% seperti layar lama.
 */
@Composable
internal fun ScanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalPadding: Int = 24
) {
    val purple = colorResource(R.color.kibooz_primary_purple)
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 50.dp)
            .alpha(if (enabled) 1f else 0.4f),
        enabled = enabled,
        shape = buttonShape,
        contentPadding = PaddingValues(horizontal = horizontalPadding.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = purple,
            contentColor = Color.White,
            disabledContainerColor = purple,
            disabledContentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/**
 * Panel yang menutupi preview selama izin kamera belum diberikan. Saat sistem tidak akan bertanya lagi, pesannya
 * berganti dan tombolnya membuka Pengaturan.
 */
@Composable
internal fun CameraPermissionPanel(
    state: CameraPermissionState,
    reason: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val blocked = state == CameraPermissionState.BLOCKED
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScanBackground)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (blocked) stringResource(R.string.izin_kamera_ditolak) else reason,
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        ScanButton(
            text = stringResource(if (blocked) R.string.izin_kamera_pengaturan else R.string.izin_kamera_beri),
            onClick = onAction,
            horizontalPadding = 28
        )
    }
}

/** Kotak gelap pengganti preview kamera, untuk fungsi `@Preview`. */
@Composable
internal fun CameraPlaceholder() {
    Box(Modifier.fillMaxSize().background(Color(0xFF3A3A3A)))
}
