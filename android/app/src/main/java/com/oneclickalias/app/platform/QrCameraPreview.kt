package com.oneclickalias.app.platform

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oneclickalias.app.ui.theme.OcaSpacing
import com.oneclickalias.app.ui.theme.OcaTheme
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

/**
 * Camera preview that reads QR codes: CameraX `Preview` plus `ImageAnalysis`,
 * each frame decoded on the device by [QrDecoder] and closed right away.
 * Frames are never stored or sent anywhere. [onText] gets every text found,
 * on the main thread; the caller decides whether it is a sign-in code. The
 * camera runs only while this is composed (and the activity is started):
 * leaving composition unbinds it, so the caller stops the camera by removing
 * it, e.g. right after a valid code.
 */
@Composable
fun QrCameraPreview(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnText by rememberUpdatedState(onText)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val decoder = QrDecoder()
        var plane = ByteArray(0)
        analysis.setAnalyzer(executor) { image ->
            val text = image.use {
                val luminance = it.planes[0]
                val bytes = luminance.buffer
                if (plane.size < bytes.remaining()) plane = ByteArray(bytes.remaining())
                bytes.get(plane, 0, bytes.remaining())
                decoder.decode(plane, it.width, it.height, luminance.rowStride)
            }
            if (text != null) mainExecutor.execute { currentOnText(text) }
        }
        try {
            val camera = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            provider.bindToLifecycle(lifecycleOwner, camera, preview, analysis)
            awaitCancellation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No usable camera, or it is taken by another app.
            failed = true
        } finally {
            analysis.clearAnalyzer()
            provider.unbind(preview, analysis)
            executor.shutdown()
            surfaceRequest = null
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val request = surfaceRequest
        when {
            failed -> Text(
                text = "The camera could not be started. Log in with your account number instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = OcaTheme.colors.mutedForeground,
                modifier = Modifier.padding(OcaSpacing.md)
            )

            request != null -> CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
        }
        // Debug builds only: a text sent over adb takes the same way as a scanned one.
        ScanTextInjection(onText = { currentOnText(it) })
    }
}

private inline fun <T> ImageProxy.use(block: (ImageProxy) -> T): T = try {
    block(this)
} finally {
    close()
}
