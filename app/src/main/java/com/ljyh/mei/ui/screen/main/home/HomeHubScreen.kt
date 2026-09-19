package com.ljyh.mei.ui.screen.main.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ljyh.mei.musetag.MusetagClient
import com.ljyh.mei.ui.screen.musetag.MusetagLibraryScreen
import com.ljyh.mei.ui.screen.musetag.MusetagLoginScreen
import kotlinx.coroutines.delay

/** MeloX musetag 模式：首页 = 登录 / 本地曲库 */
@Composable
fun HomeHubScreen() {
    var session by remember { mutableStateOf(MusetagClient.currentSession()) }
    LaunchedEffect(Unit) {
        // 等待后台 preload / 磁盘快照
        if (session.isBlank()) {
            delay(80)
            session = MusetagClient.currentSession()
        }
        if (session.isBlank()) {
            delay(400)
            session = MusetagClient.currentSession()
        }
    }
    if (session.isBlank()) {
        MusetagLoginScreen()
    } else {
        MusetagLibraryScreen()
    }
}
