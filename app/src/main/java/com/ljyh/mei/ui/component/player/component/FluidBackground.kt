package com.ljyh.mei.ui.component.player.component

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.PixelCopy
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ljyh.mei.constants.MeshFlowSpeedKey
import com.ljyh.mei.constants.MeshLowFreqVolumeKey
import com.ljyh.mei.constants.MeshPlayingKey
import com.ljyh.mei.constants.MeshRenderScaleKey
import com.ljyh.mei.constants.MeshStaticModeKey
import com.ljyh.mei.constants.MeshSubdivisionKey
import com.ljyh.mei.ui.component.player.LocalPlayerBackdropFrame
import com.ljyh.mei.ui.component.player.component.mesh.AlbumTextureProcessor
import com.ljyh.mei.ui.component.player.component.mesh.MeshBackgroundView
import com.ljyh.mei.ui.component.utils.rememberLifecycleStarted
import com.ljyh.mei.ui.glass.trackBackdropPosition
import com.ljyh.mei.utils.audio.AudioVisualizerManager
import com.ljyh.mei.utils.rememberPreference
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val BackdropCaptureShortSide = 360
private const val BackdropCaptureLongSide = 720
private const val BackdropCaptureIntervalMillis = 67L
private const val StaticBackdropCaptureAttempts = 18


@Composable
fun FluidBackground(
    imageUrl: String?,
    audioVisualizerManager: AudioVisualizerManager,
    isPlaying: Boolean = true,
    alpha: Float = 1f,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleStarted by rememberLifecycleStarted()
    val backgroundVisible = alpha > 0.01f
    val backgroundActive = lifecycleStarted && backgroundVisible
    val bass by produceState(0f, audioVisualizerManager, backgroundActive) {
        if (backgroundActive) {
            audioVisualizerManager.bassValue.collect { value = it }
        }
    }

    val (flowSpeed) = rememberPreference(MeshFlowSpeedKey, defaultValue = 0.25f)
    val (renderScale) = rememberPreference(MeshRenderScaleKey, defaultValue = 0.75f)
    val (staticMode) = rememberPreference(MeshStaticModeKey, defaultValue = true)
    val (meshPlaying) = rememberPreference(MeshPlayingKey, defaultValue = false)
    val (volumeScale) = rememberPreference(MeshLowFreqVolumeKey, defaultValue = 0.1f)
    val (subdivision) = rememberPreference(MeshSubdivisionKey, defaultValue = 50)

    LaunchedEffect(audioVisualizerManager, backgroundActive) {
        audioVisualizerManager.setCaptureEnabled(backgroundActive)
    }

    // 1. 将图片加载逻辑独立出来，只负责把 Bitmap 提取出来
    // 使用 produceState 是处理这种“异步数据转同步状态”的最佳实践
    val albumBitmap by produceState<Bitmap?>(null, imageUrl) {
        if (imageUrl.isNullOrEmpty()) {
            value = null
            return@produceState
        }
        withContext(Dispatchers.IO) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(256)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                // Detach from Coil's bitmap pool: the GL renderer owns this instance and
                // recycles it on track change, which must never corrupt a pooled bitmap.
                val decoded = result.image.toBitmap()
                value = decoded.copy(android.graphics.Bitmap.Config.ARGB_8888, false) ?: decoded
            }
        }
    }

    var meshView by remember { mutableStateOf<MeshBackgroundView?>(null) }

    // Push the album exactly once per bitmap change. Calling setAlbum from AndroidView's
    // update block re-fires on every recomposition (sheet animation ~60Hz, bass ~10Hz),
    // and each call restarts the renderer's cross-fade with a new random mesh preset,
    // which is the visible flicker/dark-dip source.
    LaunchedEffect(meshView, albumBitmap) {
        val view = meshView ?: return@LaunchedEffect
        val bitmap = albumBitmap ?: return@LaunchedEffect
        view.setAlbum(bitmap)
    }

    // Publish the cover as the player backdrop's recording stand-in: this GL surface's
    // pixels cannot be captured by a Compose layer recording, so sheets sample this instead.
    val backdropFrame = LocalPlayerBackdropFrame.current
    LaunchedEffect(backdropFrame, albumBitmap) {
        backdropFrame?.value = withContext(Dispatchers.Default) {
            // The mesh renders AlbumTextureProcessor's heavily blurred, darkened output;
            // keep this as the immediate fallback until the first PixelCopy frame arrives.
            albumBitmap?.let(AlbumTextureProcessor::process)
        }?.asImageBitmap()
    }

    // 2. 状态：静态背景仍渲染封面，但不循环动画/采样
    val shouldAnimate = false
    val pixelCopyHandler = remember { Handler(Looper.getMainLooper()) }
    val captureBuffers = remember { arrayOfNulls<Bitmap>(3) }
    val captureState = remember { IntArray(3) }

    // Configuration changes are infrequent compared with sheet/bass recompositions. Apply
    // renderer settings from their state boundary instead of queueing GL work from every
    // AndroidView update pass.
    LaunchedEffect(meshView, flowSpeed, renderScale, subdivision) {
        val view = meshView ?: return@LaunchedEffect
        view.setFlowSpeed(flowSpeed)
        view.setRenderScale(renderScale)
        view.setSubdivision(subdivision)
        view.setStaticMode(true)
        view.setPlaying(false)
    }

    // 静态封面背景仍需要 GL 绘制；仅禁止持续动画
    // 旋转/回到前台时重新 setAlbum + setRenderingRequested，避免背景变黑
    LaunchedEffect(meshView, backgroundActive, lifecycleStarted, albumBitmap) {
        val view = meshView ?: return@LaunchedEffect
        view.setHostStarted(lifecycleStarted)
        view.setStaticMode(true)
        view.setPlaying(false)
        view.setRenderingRequested(backgroundActive)
        albumBitmap?.let { view.setAlbum(it) }
    }

    // 静态背景：采样几次 backdrop 供玻璃层使用
    LaunchedEffect(meshView, backdropFrame, albumBitmap, backgroundActive) {
        if (!backgroundActive) return@LaunchedEffect
        val view = meshView ?: return@LaunchedEffect
        val target = backdropFrame ?: return@LaunchedEffect
        delay(120)
        var attempts = 0
        while (isActive && attempts < StaticBackdropCaptureAttempts) {
            val sourceWidth = view.width
            val sourceHeight = view.height
            if (view.isAttachedToWindow && sourceWidth > 0 && sourceHeight > 0) {
                val shortSide = minOf(sourceWidth, sourceHeight).toFloat()
                val longSide = maxOf(sourceWidth, sourceHeight).toFloat()
                val scale = minOf(
                    1f,
                    BackdropCaptureShortSide / shortSide,
                    BackdropCaptureLongSide / longSide,
                )
                val width = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
                val height = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                if (captureBuffers[0] == null ||
                    width != captureState[0] ||
                    height != captureState[1]
                ) {
                    captureState[0] = width
                    captureState[1] = height
                    captureBuffers.indices.forEach { index ->
                        captureBuffers[index] =
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    }
                    captureState[2] = 0
                }
                val bitmap = captureBuffers[captureState[2]] ?: break
                captureState[2] = (captureState[2] + 1) % captureBuffers.size
                if (copySurfaceFrame(view, bitmap, pixelCopyHandler)) {
                    target.value = bitmap.asImageBitmap()
                    break
                }
                attempts++
            }
            delay(80)
        }
        // 采样失败时用处理过的封面作 backdrop，避免玻璃层空白
        if (target.value == null) {
            albumBitmap?.let { bmp ->
                target.value = AlbumTextureProcessor.process(bmp).asImageBitmap()
            }
        }
    }

    // 3. 去掉过于严格的版本限制 (只要设备存在就能初始化，低端机 GLES 3.0 兼容性极好)
    // 如果你想绝对保险，可以写 >= Build.VERSION_CODES.LOLLIPOP (21)
    Box(modifier.fillMaxSize()) {
        // Compose 侧静态封面兜底：即使 mesh 未及时绘制，也能看到封面背景
        val fallbackFrame = albumBitmap?.asImageBitmap()
        if (fallbackFrame != null && backdropFrame?.value == null) {
            androidx.compose.foundation.Image(
                bitmap = fallbackFrame,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    Color.Black.copy(alpha = 0.35f),
                    androidx.compose.ui.graphics.BlendMode.Darken,
                ),
            )
        }
        // This empty Compose node owns the recording coordinates. Its custom Backdrop draw
        // reads only [backdropFrame], so the native GL Surface is never re-drawn or re-clipped.
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .trackBackdropPosition(backdrop),
        )
        AndroidView(
            factory = { ctx ->
                MeshBackgroundView(ctx).apply {
                    meshView = this
                    this.alpha = alpha.coerceIn(0f, 1f)
                    setFlowSpeed(flowSpeed)
                    setRenderScale(renderScale)
                    setSubdivision(subdivision)
                    setStaticMode(true)
                    setPlaying(false)
                    setPreserveEGLContextOnPause(true)
                    setHostStarted(lifecycleStarted)
                    setRenderingRequested(true)
                }
            },
            update = { view ->
                view.alpha = alpha.coerceIn(0f, 1f)
                view.updateVolume(0f)
                view.setStaticMode(true)
                view.setPlaying(false)
                view.setRenderingRequested(backgroundActive)
                albumBitmap?.let { view.setAlbum(it) }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private suspend fun copySurfaceFrame(
    source: MeshBackgroundView,
    destination: Bitmap,
    callbackHandler: Handler,
): Boolean = suspendCancellableCoroutine { continuation ->
    try {
        PixelCopy.request(
            source,
            destination,
            { result ->
                if (continuation.isActive) {
                    continuation.resume(result == PixelCopy.SUCCESS)
                }
            },
            callbackHandler,
        )
    } catch (_: IllegalArgumentException) {
        continuation.resume(false)
    }
}
