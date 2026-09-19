package com.ljyh.mei.musetag

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 启动策略：磁盘曲库先上，打开后只静默刷新一次。
 * 避免启动全屏转圈 + 期间点歌闪退。
 */
object MusetagBootstrap {
    @Volatile
    var cacheReady: Boolean = false

    @Volatile
    private var silentRefreshScheduled = false

    fun warmUp(app: Context) {
        Thread {
            try {
                MusetagClient.preloadFromDisk()
            } catch (_: Throwable) {
            }
            try {
                MusetagStore.loadCache(app)
            } catch (_: Throwable) {
            }
            cacheReady = true
        }.start()
    }

    /** 最多等 timeoutMs，让首屏能用上磁盘缓存 */
    suspend fun awaitCache(timeoutMs: Long = 2000) {
        val start = System.currentTimeMillis()
        while (!cacheReady && System.currentTimeMillis() - start < timeoutMs) {
            delay(40)
        }
    }

    /** 打开 App 后台静默拉一次，成功才写盘 */
    suspend fun silentRefreshOnce(context: Context) {
        if (silentRefreshScheduled) return
        if (!MusetagClient.isLoggedIn()) return
        silentRefreshScheduled = true
        withContext(Dispatchers.IO) {
            runCatching { MusetagClient.fetchMe() }
            runCatching {
                MusetagClient.fetchLibrary().onSuccess {
                    MusetagStore.saveCache(context)
                }
            }
        }
    }
}
