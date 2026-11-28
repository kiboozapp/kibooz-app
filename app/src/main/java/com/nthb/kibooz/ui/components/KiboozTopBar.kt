package com.nthb.kibooz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R

/**
 * Bilah atas ungu dengan tombol kembali, sama dengan `bg_topbar_purple_gradient` setinggi 64dp.
 * [actions] ditaruh di ujung kanan, dan tiap tombolnya memakai area sentuh 48dp dengan ikon 24dp.
 */
@Composable
fun KiboozTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val gradient = Brush.horizontalGradient(
        listOf(colorResource(R.color.kibooz_topbar_start), colorResource(R.color.kibooz_topbar_end))
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(gradient)
            .statusBarsPadding()
            .height(64.dp)
            // 9dp ditambah area sentuh 48dp menaruh ikon 26dp di jarak 20dp seperti layar XML.
            // Di ujung kanan, 8dp ditambah area sentuh 48dp menaruh ikon 24dp di jarak 20dp
            .padding(start = 9.dp, end = if (actions == null) 20.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.kembali),
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(start = 3.dp).semantics { heading() },
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        actions?.invoke(this)
    }
}
