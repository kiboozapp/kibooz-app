package com.nthb.kibooz.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nthb.kibooz.R
import com.nthb.kibooz.ui.components.AvatarImage
import com.nthb.kibooz.util.AvatarLoader

/**
 * Kepala profil Guru dan Wali, yaitu pita ungu bersudut bulat dengan judul dan foto bulat yang bisa diketuk untuk
 * ganti foto. [below] diletakkan di bawah foto.
 */
@Composable
internal fun ProfileHeader(
    title: String,
    photoDescription: String,
    avatarUrl: String?,
    avatarVersion: Int,
    avatarLoader: AvatarLoader,
    onAvatar: () -> Unit,
    below: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(colorResource(R.color.kibooz_topbar_start), colorResource(R.color.kibooz_topbar_end))
                    ),
                    RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
                )
        )
        Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .semantics { contentDescription = photoDescription }
                    .clickable(
                        onClickLabel = stringResource(R.string.profil_ganti_foto),
                        role = Role.Button,
                        onClick = onAvatar
                    ),
                shape = CircleShape,
                shadowElevation = 8.dp,
                border = BorderStroke(4.dp, MaterialTheme.colorScheme.surface)
            ) {
                AvatarImage(
                    url = avatarUrl,
                    placeholder = R.drawable.img_avatar_guru,
                    loader = avatarLoader,
                    modifier = Modifier.fillMaxSize(),
                    version = avatarVersion
                )
            }
            below()
        }
    }
}

/** Tombol ungu penuh dengan ikon kartu identitas, sama dengan tombol detail di layar XML. */
@Composable
internal fun ProfileDetailButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_badge_id),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
