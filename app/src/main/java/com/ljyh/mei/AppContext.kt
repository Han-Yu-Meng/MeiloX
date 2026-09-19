package com.ljyh.mei

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Interceptor
import okhttp3.OkHttpClient

@HiltAndroidApp
class AppContext : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
        try {
            com.ljyh.mei.musetag.MusetagBootstrap.warmUp(this)
        } catch (_: Throwable) {
        }
    }

    /** 未捕获异常写入本地文件，便于无 adb 时排查闪退 */
    private fun installCrashLogger() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                val text = buildString {
                    appendLine("==== $stamp ====")
                    appendLine("thread=${thread.name}")
                    appendLine(android.util.Log.getStackTraceString(throwable))
                    appendLine()
                }
                val targets = listOfNotNull(
                    filesDir,
                    getExternalFilesDir(null),
                )
                targets.forEach { dir ->
                    try {
                        val file = java.io.File(dir, "crash.log")
                        val prev = if (file.exists() && file.length() < 200_000) file.readText() else ""
                        file.writeText(text + prev)
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
            default?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        @JvmStatic
        lateinit var instance: AppContext

    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val headerInterceptor = Interceptor { chain ->
            val newRequest = chain.request().newBuilder()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0")
                .build()
            chain.proceed(newRequest)
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .build()

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.1)
                    .build()
            }
            .diskCache {
                newDiskCache()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .build()
    }
    private fun newDiskCache(): DiskCache {
        return DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizePercent(0.1)
            .build()
    }
}

