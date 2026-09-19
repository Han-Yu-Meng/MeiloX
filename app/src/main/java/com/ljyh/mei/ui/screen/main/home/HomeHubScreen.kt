package com.ljyh.mei.ui.screen.main.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ljyh.mei.R
import com.ljyh.mei.musetag.MusetagBootstrap
import com.ljyh.mei.musetag.MusetagClient
import com.ljyh.mei.ui.screen.musetag.MusetagHomeScreen
import com.ljyh.mei.ui.screen.musetag.MusetagLoginScreen
import kotlinx.coroutines.delay

/** MeloX musetag：首页 = 个人喜爱；音乐库 = 全部歌曲 */
@Composable
fun HomeHubScreen() {
    var session by remember { mutableStateOf(runCatching { MusetagClient.currentSession() }.getOrDefault("")) }
    var bootError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            MusetagBootstrap.awaitCache(2500)
            if (session.isBlank()) {
                delay(100)
                session = MusetagClient.currentSession()
            }
            if (session.isBlank()) {
                delay(500)
                session = MusetagClient.currentSession()
            }
        }.onFailure {
            bootError = it.message ?: "startup failed"
        }
    }

    when {
        bootError != null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = bootError ?: "",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        session.isBlank() -> MusetagLoginScreen()
        else -> {
            // 首帧先给缓存列表，静默刷新在屏幕内完成
            MusetagHomeScreen()
        }
    }
}

@Composable
private fun BootSplash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
