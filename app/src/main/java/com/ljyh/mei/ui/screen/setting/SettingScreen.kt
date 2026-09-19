package com.ljyh.mei.ui.screen.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ljyh.mei.R
import com.ljyh.mei.ui.glass.IosGroupedList
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.glass.IosListRow
import com.ljyh.mei.ui.glass.IosTypography
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.utils.rememberPreference
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val navController = LocalNavController.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    IosPinnedListPage(
        title = stringResource(R.string.settings),
        bottomPadding = insets.calculateBottomPadding(),
    ) {
        item { SettingsSectionTitle(stringResource(R.string.settings_account)) }
        item {
            IosGroupedList {
                val musetagSession by rememberPreference(com.ljyh.mei.constants.MusetagSessionKey, "")
                val musetagUser by rememberPreference(com.ljyh.mei.constants.MusetagUsernameKey, "")
                if (musetagSession.isBlank()) {
                    SettingsEntry(stringResource(R.string.musetag_login_title), "person.crop.circle", false) {
                        Screen.MusetagLogin.navigate(navController)
                    }
                } else {
                    SettingsEntry(
                        musetagUser.ifBlank { stringResource(R.string.musetag_library) },
                        "person.crop.circle",
                        false,
                    ) { Screen.Home.navigate(navController) }
                    SettingsEntry(stringResource(R.string.musetag_logout), "rectangle.portrait.and.arrow.forward") {
                        MainScope().launch {
                            com.ljyh.mei.musetag.MusetagClient.clearSession()
                            Screen.MusetagLogin.navigate(navController)
                        }
                    }
                }
            }
        }
        item { SettingsSectionTitle(stringResource(R.string.settings_application)) }
        item {
            IosGroupedList {
                // 仅保留与 musetag 播放相关的设置
                SettingsEntry(stringResource(R.string.settings_appearance), "paintbrush") { Screen.AppearanceSettings.navigate(navController) }
                SettingsEntry(stringResource(R.string.settings_playback), "waveform") { Screen.PlaySettings.navigate(navController) }
                SettingsEntry(stringResource(R.string.lyrics_settings), "quote.bubble") { Screen.LyricsSettings.navigate(navController) }
                SettingsEntry(stringResource(R.string.settings_downloads), "arrow.down.circle") { Screen.DownloadSettings.navigate(navController) }
            }
        }
        item { SettingsSectionTitle(stringResource(R.string.settings_information)) }
        item {
            IosGroupedList {
                SettingsEntry(stringResource(R.string.settings_about), "info.circle", false) { Screen.About.navigate(navController) }
            }
        }
    }
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = IosTypography.subheadline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, start = 16.dp),
    )
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(title)
        IosGroupedList(content = content)
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    systemName: String,
    showTopSeparator: Boolean = true,
    onClick: () -> Unit,
) = IosListRow(
    title = title,
    systemName = systemName,
    showTopSeparator = showTopSeparator,
    onClick = onClick,
)
