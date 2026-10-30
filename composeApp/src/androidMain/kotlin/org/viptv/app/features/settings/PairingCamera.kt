package org.viptv.app.features.settings

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun QrCamera(modifier: Modifier, onPayload: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val delivering = remember { AtomicBoolean(false) }
    AndroidView(factory = { preview }, modifier = modifier)
    DisposableEffect(owner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
        val ready = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        ready.addListener({
            if (disposed) return@addListener
            val camera = try { ready.get() } catch (_: Exception) { null } ?: return@addListener
            provider = camera
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(analysisExecutor) { image -> read(image, scanner, delivering, onPayload) }
            val usesPreview = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            try { camera.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, usesPreview, analysis) } catch (_: Exception) { }
        }, mainExecutor)
        onDispose {
            disposed = true
            ready.cancel(true)
            provider?.unbindAll()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }
}

@OptIn(markerClass = [ExperimentalGetImage::class])
private fun read(image: ImageProxy, scanner: BarcodeScanner, delivering: AtomicBoolean, onPayload: (String) -> Unit) {
    val media = image.image
    if (media == null || delivering.get()) { image.close(); return }
    val task = try { scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)) }
    catch (_: Exception) { image.close(); return }
    task
        .addOnSuccessListener { codes ->
            val payload = codes.firstNotNullOfOrNull { it.rawValue } ?: return@addOnSuccessListener
            if (delivering.compareAndSet(false, true)) onPayload(payload)
        }
        .addOnCompleteListener { image.close() }
}
