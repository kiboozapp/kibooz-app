package com.nthb.kibooz.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.theme.KiboozTheme

// Warna gelap khusus layar ini, sama dengan layout XML sebelumnya
private val screenColor = Color(0xFF161224)
private val barColor = Color(0xFF1E1930)
private val hintColor = Color(0xFFD8D2EE)
private val rotateStrokeColor = Color(0xFF6E6396)
private val buttonShape = RoundedCornerShape(24.dp)

/**
 * Layar pangkas foto profil. [cropView] adalah `CropOverlayView` milik activity, karena activity yang memasukkan bitmap
 * dan mengekspor hasil pangkasan. Tombol simpan tidak berubah tampilannya saat [saving], seperti tombol XML sebelumnya.
 */
@Composable
internal fun CropAvatarScreen(
    cropView: CropOverlayView,
    saving: Boolean,
    onBack: () -> Unit,
    onRotate: () -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(screenColor)) {
        TopBar(onBack)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { cropView }, modifier = Modifier.fillMaxSize())
            Text(
                text = stringResource(R.string.avatar_crop_petunjuk),
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 14.dp, end = 24.dp),
                color = hintColor,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        BottomActions(saving = saving, onRotate = onRotate, onSave = onSave)
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .statusBarsPadding()
            // 48dp area sentuh dengan ikon 24dp di tempat yang sama seperti tombol 40dp sebelumnya, jadi bilah tetap 68dp
            .padding(start = 12.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.batal),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.avatar_crop_judul),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BottomActions(saving: Boolean, onRotate: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            // Latar bilah sampai ke tepi layar, tombolnya di atas tombol navigasi sistem
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(buttonShape)
                .border(1.dp, rotateStrokeColor, buttonShape)
                .clickable(role = Role.Button, onClick = onRotate),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.avatar_crop_putar),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp
            )
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .weight(1.3f)
                .height(48.dp)
                .clip(buttonShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = !saving, role = Role.Button, onClick = onSave),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.avatar_crop_simpan),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 740)
@Composable
private fun CropAvatarScreenPreview() {
    KiboozTheme {
        CropAvatarScreen(
            cropView = CropOverlayView(LocalContext.current),
            saving = false,
            onBack = {},
            onRotate = {},
            onSave = {}
        )
    }
}
