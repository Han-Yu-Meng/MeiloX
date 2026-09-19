package com.ljyh.mei.ui.screen.main.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ljyh.mei.R
import com.ljyh.mei.constants.MusetagSessionKey
import com.ljyh.mei.ui.glass.GlassCard
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.local.LocalNavController
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets
import com.ljyh.mei.ui.navigation.LibraryPage
import com.ljyh.mei.ui.screen.Screen
import com.ljyh.mei.ui.screen.musetag.MusetagLibraryScreen
import com.ljyh.mei.utils.rememberPreference

/** 媒体库：musetag 模式，不再依赖网易云 Cookie */
@Composable
fun LibraryScreen(
    isNavigationTab: Boolean = false,
    category: LibraryPage? = null,
) {
    val (musetagSession) = rememberPreference(MusetagSessionKey, "")
    if (musetagSession.isNotBlank()) {
        MusetagLibraryScreen()
        return
    }

    val navController = LocalNavController.current
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    IosPinnedListPage(
        title = stringResource(category?.titleRes ?: R.string.app_tab_library),
        bottomPadding = insets.calculateBottomPadding(),
        onNavigateBack = if (isNavigationTab) null else ({ navController.navigateUp() }),
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                GlassCard(
                    modifier = Modifier.padding(24.dp),
                    onClick = { Screen.MusetagLogin.navigate(navController) },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SfIcon("person.crop.circle", null, size = 42.dp)
                        Text(
                            stringResource(R.string.musetag_library_sign_in),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 兼容旧调用点 */
@Composable
fun EmptyLoginState(
    navController: com.ljyh.mei.ui.navigation.MeiNavigator,
    isNavigationTab: Boolean = false,
    category: LibraryPage? = null,
) {
    LibraryScreen(isNavigationTab = isNavigationTab, category = category)
}
