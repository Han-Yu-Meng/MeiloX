package com.ljyh.mei.ui.screen.setting

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.ljyh.mei.R
import com.ljyh.mei.ui.glass.IosPinnedListPage
import com.ljyh.mei.ui.local.LocalPlayerAwareWindowInsets

/** 通用设置：已移除网易云 Cookie / 剪贴板识别等无关项 */
@Composable
fun GeneralSettings() {
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    IosPinnedListPage(
        title = stringResource(R.string.general_settings),
        bottomPadding = insets.calculateBottomPadding(),
    ) {
        item {
            Text(
                text = "当前为 Musetag 本地曲库模式，无更多通用选项。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
