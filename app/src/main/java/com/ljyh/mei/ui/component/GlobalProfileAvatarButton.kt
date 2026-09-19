package com.ljyh.mei.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ljyh.mei.R
import com.ljyh.mei.constants.MusetagSessionKey
import com.ljyh.mei.constants.MusetagUsernameKey
import com.ljyh.mei.ui.glass.GlassIconButton
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.utils.rememberPreference

/** Shared account entry shown on every primary tab's pinned navigation bar. */
@Composable
fun GlobalProfileAvatarButton(modifier: Modifier = Modifier) {
    val navController = LocalNavController.current
    val (session) = rememberPreference(MusetagSessionKey, "")
    val (nickname) = rememberPreference(MusetagUsernameKey, "")
    val accountDescription = stringResource(R.string.account_home)

    GlassIconButton(
        onClick = {
            if (session.isBlank()) {
                Screen.MusetagLogin.navigate(navController)
            } else {
                Screen.Home.navigate(navController)
            }
        },
        modifier = modifier,
    ) {
        if (nickname.isBlank()) {
            SfIcon(
                systemName = "person.crop.circle",
                contentDescription = accountDescription,
                size = 27.dp,
            )
        } else {
            Text(
                text = nickname.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
