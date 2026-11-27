package com.nthb.kibooz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.data.remote.OutageKind
import com.nthb.kibooz.ui.common.ConnectionStatusView
import com.nthb.kibooz.util.formatWaitSeconds

private val cardShape = RoundedCornerShape(16.dp)

/**
 * Kartu gangguan server (FE-12) untuk layar Compose, pasangan dari [ConnectionStatusView]. Pemanggil yang memutuskan
 * kapan kartu tampil. [secondsLeft] null untuk layar yang tidak mencoba lagi sendiri.
 */
@Composable
fun ConnectionStatusCard(kind: OutageKind, onRetry: () -> Unit, modifier: Modifier = Modifier, secondsLeft: Int? = null) {
    val wait = secondsLeft?.let(::formatWaitSeconds)
    val title = when (kind) {
        OutageKind.OFFLINE -> stringResource(R.string.outage_offline_title)
        OutageKind.UNAVAILABLE -> stringResource(R.string.outage_unavailable_title)
    }
    val message = when (kind) {
        OutageKind.OFFLINE ->
            if (wait != null) {
                stringResource(R.string.outage_offline_message, wait)
            } else {
                stringResource(R.string.outage_offline_manual)
            }
        OutageKind.UNAVAILABLE ->
            if (wait != null) {
                stringResource(R.string.outage_unavailable_message, wait)
            } else {
                stringResource(R.string.outage_unavailable_manual)
            }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colorResource(R.color.kibooz_outage_surface), cardShape)
            .border(1.dp, colorResource(R.color.kibooz_outage_border), cardShape)
            .padding(16.dp)
    ) {
        // Hanya judul yang diumumkan, agar hitung mundur tidak dibacakan setiap detik
        Text(
            text = title,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.coba_lagi),
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onRetry)
                    .minimumInteractiveComponentSize()
                    .background(colorResource(R.color.kibooz_lavender), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            // Halaman status hanya berguna jika masalahnya ada di server, bukan di ponsel
            if (kind == OutageKind.UNAVAILABLE) {
                val uriHandler = LocalUriHandler.current
                Text(
                    text = stringResource(R.string.outage_status_link),
                    modifier = Modifier
                        // openUri melempar jika tidak ada peramban, tombol coba lagi tetap bisa dipakai
                        .clickable(role = Role.Button) {
                            runCatching { uriHandler.openUri(ConnectionStatusView.STATUS_PAGE_URL) }
                        }
                        .minimumInteractiveComponentSize(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
