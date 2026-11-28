package com.nthb.kibooz.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R

/** Menarik isi ke atas sejauh [amount] dan ikut memendekkan tempatnya, seperti margin negatif di layar XML. */
fun Modifier.pullUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val up = amount.roundToPx()
    layout(placeable.width, placeable.height - up) { placeable.place(0, -up) }
}

/** Kartu putih bersudut bulat dengan bayangan, pengganti `CardView` tanpa garis tepi di layar XML. */
fun Modifier.plainCard(radius: Dp, elevation: Dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.shadow(elevation, shape).background(Color.White, shape)
}

/** Kartu dengan garis tepi 1dp, pengganti `MaterialCardView`. Tanpa `strokeColor` di XML, garisnya `outlineVariant`. */
fun Modifier.outlinedCard(radius: Dp, elevation: Dp, fill: Color, outline: Color): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.shadow(elevation, shape).background(fill, shape).border(1.dp, outline, shape)
}

/**
 * Dua ikon putih di ujung kanan kepala portal, [startIcon] di kiri [endIcon]. Ikon 26dp berjarak [iconGap] dan
 * ikon kanan 20dp dari tepi layar, dengan area sentuh 48dp yang saling bertumpuk sedikit.
 */
@Composable
fun PortalHeaderActions(
    @DrawableRes startIcon: Int,
    startDescription: String,
    onStart: () -> Unit,
    @DrawableRes endIcon: Int,
    endDescription: String,
    onEnd: () -> Unit,
    iconGap: Dp,
    modifier: Modifier = Modifier
) {
    val centerDistance = 26.dp + iconGap
    Box(modifier = modifier.offset(x = 11.dp).size(width = 48.dp + centerDistance, height = 48.dp)) {
        HeaderIcon(startIcon, startDescription, onStart, Modifier.align(Alignment.CenterStart))
        HeaderIcon(endIcon, endDescription, onEnd, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun HeaderIcon(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(26.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
    }
}

/** Tombol lebar berlatar lavender untuk mencoba lagi memuat layar portal yang masih kosong. */
@Composable
fun PortalRetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(start = 20.dp, top = 12.dp, end = 20.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colorResource(R.color.kibooz_lavender))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.coba_lagi),
            color = colorResource(R.color.kibooz_primary_purple),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
