package com.nthb.kibooz.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R

/** Satu baris menu profil. [danger] mewarnai baris merah dan menghilangkan panah. */
internal data class ProfileMenuItem(
    @DrawableRes val icon: Int,
    val label: String,
    val onClick: () -> Unit,
    val danger: Boolean = false
)

/** Kartu putih berisi baris menu setinggi 52dp yang dipisah garis, sama dengan kartu menu di layar XML. */
@Composable
internal fun ProfileMenuCard(items: List<ProfileMenuItem>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, colorResource(R.color.kibooz_profile_card_border))
    ) {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colorResource(R.color.kibooz_profile_divider))
                    )
                }
                MenuRow(item)
            }
        }
    }
}

@Composable
private fun MenuRow(item: ProfileMenuItem) {
    val tint = if (item.danger) colorResource(R.color.kibooz_profile_danger) else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(role = Role.Button, onClick = item.onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
        Text(
            text = item.label,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            color = if (item.danger) colorResource(R.color.kibooz_profile_danger) else colorResource(R.color.kibooz_profile_menu_text),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        if (!item.danger) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorResource(R.color.kibooz_profile_chevron)
            )
        }
    }
}

