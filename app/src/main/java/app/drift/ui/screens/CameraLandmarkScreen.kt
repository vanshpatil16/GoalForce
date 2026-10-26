package app.drift.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import app.drift.vision.AttentionMetrics
import app.drift.vision.FatigueStateMachine
import app.drift.vision.FatigueStatus
import app.drift.vision.FaceExpression
import app.drift.service.FatigueMonitorService
import java.nio.ByteBuffer
import java.util.concurrent.Executors

private data class FaceOverlay(val points: List<Offset>, val ear: Float?, val mar: Float?, val gazeX: Float?, val gazeY: Float?, val message: String, val status: FatigueStatus = FatigueStatus.MONITORING_UNAVAILABLE, val rotationDegrees: Int = 0, val imageWidth: Int = 0, val imageHeight: Int = 0, val expression: String? = null)

@Composable
fun CameraLandmarkScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var running by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var frame by remember { mutableStateOf(FaceOverlay(emptyList(), null, null, null, null, "Monitoring unavailable")) }
    var failure by remember { mutableStateOf<String?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var stateCalibrating by remember { mutableStateOf(true) }
    var backgroundMonitoring by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> granted = allowed; if (allowed) running = true }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startBackgroundMonitor(context) { backgroundMonitoring = it }
    }

    DisposableEffect(running, granted, lifecycleOwner, previewView) {
        if (!running || !granted) return@DisposableEffect onDispose { }
        val executor = Executors.newSingleThreadExecutor()
        var landmarker: FaceLandmarker? = null
        var provider: ProcessCameraProvider? = null
        val stateMachine = FatigueStateMachine()
        try {
            val base = BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(base).setRunningMode(RunningMode.LIVE_STREAM).setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .setMinFaceDetectionConfidence(0.5f).setMinFacePresenceConfidence(0.5f).setMinTrackingConfidence(0.5f)
                .setResultListener { result, inputImage ->
                    val face = result.faceLandmarks().firstOrNull()
                    if (face == null) frame = frame.copy(points = emptyList(), ear = null, mar = null, gazeX = null, gazeY = null, message = "No face detected", status = stateMachine.update(SystemClock.elapsedRealtime(), null, null))
                    else {
                        val pts = face.map { AttentionMetrics.Point(it.x(), it.y()) }
                        fun point(i: Int) = pts.getOrElse(i) { AttentionMetrics.Point(0f, 0f) }
                        // MediaPipe eye contours in the six-point EAR convention; mirrored view handled for overlay below.
                        val earA = AttentionMetrics.ear(pts, intArrayOf(33, 160, 158, 133, 153, 144))
                        val earB = AttentionMetrics.ear(pts, intArrayOf(362, 385, 387, 263, 373, 380))
                        val ear = if (earA != null && earB != null) (earA + earB) / 2f else earA ?: earB
                        val mar = AttentionMetrics.mar(point(13), point(14), point(61), point(291))
                        val gaze = if (pts.size >= 478) {
                            val a = AttentionMetrics.normalizeIris(point(468), point(33), point(133), point(159), point(145))
                            val b = AttentionMetrics.normalizeIris(point(473), point(362), point(263), point(386), point(374))
                            if (a != null && b != null) AttentionMetrics.Point((a.x + b.x) / 2f, (a.y + b.y) / 2f) else a ?: b
                        } else null
                        val status = stateMachine.update(SystemClock.elapsedRealtime(), ear, mar)
                        stateCalibrating = stateMachine.isCalibrating
                        val blendshapes = result.faceBlendshapes().orElse(emptyList()).firstOrNull().orEmpty()
                        val expression = if (blendshapes.isEmpty()) null else FaceExpression.estimate(blendshapes.associate { it.categoryName() to it.score() })
                        frame = frame.copy(points = pts.map { Offset(it.x, it.y) }, ear = ear, mar = mar, gazeX = gaze?.x, gazeY = gaze?.y, message = "Face tracked · ${pts.size} landmarks", status = status, expression = expression)
                    }
                    inputImage.close()
                }.setErrorListener { e -> failure = e.message ?: "Face tracking failed" }
                .build()
            landmarker = FaceLandmarker.createFromOptions(context, options)
            val cameraFuture = ProcessCameraProvider.getInstance(context)
            cameraFuture.addListener({
                try {
                    provider = cameraFuture.get()
                    val pv = previewView ?: return@addListener
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                    analysis.setAnalyzer(executor) { image ->
                        try {
                            val rotationDegrees = image.imageInfo.rotationDegrees
                            frame = frame.copy(rotationDegrees = rotationDegrees, imageWidth = image.width, imageHeight = image.height)
                            val bmp = rgbaBitmap(image.width, image.height, image.planes[0].buffer, image.planes[0].rowStride, image.planes[0].pixelStride)
                            val mp = BitmapImageBuilder(bmp).build()
                            landmarker?.detectAsync(mp, ImageProcessingOptions.builder().setRotationDegrees(rotationDegrees).build(), SystemClock.uptimeMillis())
                        } catch (e: Exception) { failure = e.message ?: "Frame processing failed" }
                        finally { image.close() }
                    }
                    provider?.unbindAll()
                    provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                    frame = frame.copy(message = "Camera active · on-device processing")
                } catch (e: Exception) { failure = e.message ?: "Unable to start camera"; running = false }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) { failure = "Model unavailable: ${e.message ?: "initialization failed"}"; running = false }
        onDispose {
            provider?.unbindAll()
            try { landmarker?.close() } catch (_: Exception) { }
            executor.shutdownNow()
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Camera landmark test", style = MaterialTheme.typography.headlineMedium)
        Text("Live front-camera preview. Face landmarks are processed on this device; frames and landmark streams are not saved.", style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.fillMaxWidth().height(390.dp).background(Color(0xFF10141C)), contentAlignment = Alignment.Center) {
            if (running && granted && failure == null) {
                AndroidView(factory = { ctx -> PreviewView(ctx).also { it.scaleType = PreviewView.ScaleType.FILL_CENTER; previewView = it } }, modifier = Modifier.fillMaxSize())
                LandmarkOverlay(frame.points, frame.rotationDegrees, frame.imageWidth, frame.imageHeight, Modifier.fillMaxSize())
            } else Text(if (failure != null) "Camera/model error" else "Camera preview is off", color = Color.White)
        }
        Text(frame.message, fontWeight = FontWeight.SemiBold)
        Text("Estimated state: ${if (running && frame.points.isNotEmpty() && stateCalibrating) "calibrating baseline" else frame.status.name.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.bodyMedium)
        Text("Estimated expression: ${frame.expression ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("EAR ${frame.ear?.let { "%.3f".format(it) } ?: "—"}   MAR ${frame.mar?.let { "%.3f".format(it) } ?: "—"}", style = MaterialTheme.typography.bodyLarge)
        Text("Relative iris offset  X ${frame.gazeX?.let { "%+.2f".format(it) } ?: "—"}  Y ${frame.gazeY?.let { "%+.2f".format(it) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Colored dots show the detected face mesh. Labels identify face, eyes, iris, and mouth landmarks. Relative iris position is not absolute screen gaze.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {
            failure = null
            if (!running) {
                stateCalibrating = true
                if (granted) running = true else permission.launch(Manifest.permission.CAMERA)
            } else { running = false; frame = frame.copy(points = emptyList(), message = "Monitoring stopped") }
        }) { Text(if (running) "Stop monitoring" else "Start camera test") }
        Button(onClick = {
            if (backgroundMonitoring) {
                context.startService(Intent(context, FatigueMonitorService::class.java).setAction(FatigueMonitorService.ACTION_STOP))
                backgroundMonitoring = false
            } else if (!granted) {
                permission.launch(Manifest.permission.CAMERA)
            } else {
                // Release the screen-bound camera first; the service then owns it as the user leaves for Instagram.
                running = false
                Handler(Looper.getMainLooper()).postDelayed({
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startBackgroundMonitor(context) { backgroundMonitoring = it }
                    }
                }, 500)
            }
        }) { Text(if (backgroundMonitoring) "Stop background monitoring" else "Monitor while using other apps") }
        Text("Background monitoring keeps the front camera active. Drowsiness alerts use notifications; allow notifications for pop-up banners.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun startBackgroundMonitor(context: android.content.Context, onResult: (Boolean) -> Unit) {
    try {
        ContextCompat.startForegroundService(context, Intent(context, FatigueMonitorService::class.java))
        onResult(true)
    } catch (_: Exception) {
        onResult(false)
    }
}

@Composable
private fun LandmarkOverlay(points: List<Offset>, rotationDegrees: Int, imageWidth: Int, imageHeight: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        val rotation = ((rotationDegrees % 360) + 360) % 360
        val rotatedWidth = if (rotation == 90 || rotation == 270) imageHeight else imageWidth
        val rotatedHeight = if (rotation == 90 || rotation == 270) imageWidth else imageHeight
        val imageAspect = if (rotatedHeight > 0) rotatedWidth.toFloat() / rotatedHeight else size.width / size.height
        val viewAspect = size.width / size.height
        // PreviewView.ScaleType.FILL_CENTER crops the long axis to fill the view. Apply that same crop to landmarks.
        val cropX = if (imageAspect > viewAspect) (1f - viewAspect / imageAspect) / 2f else 0f
        val cropY = if (imageAspect < viewAspect) (1f - imageAspect / viewAspect) / 2f else 0f
        fun map(p: Offset): Offset {
            val rotated = when (rotation) {
                90 -> Offset(1f - p.y, p.x)
                180 -> Offset(1f - p.x, 1f - p.y)
                270 -> Offset(p.y, 1f - p.x)
                else -> p
            }
            // The front-camera PreviewView is mirrored for a natural selfie view.
            val mirroredX = 1f - rotated.x
            return Offset((mirroredX - cropX) / (1f - 2f * cropX), (rotated.y - cropY) / (1f - 2f * cropY))
        }
        val mappedPoints = points.map(::map)
        // The green face mesh dots are all visible landmarks. Warm labels call out the main feature groups.
        mappedPoints.forEachIndexed { i, p ->
            drawCircle(if (i in 468..477) Color(0xFFFFC857) else Color(0xFF48E0B0), 2.2.dp.toPx(), Offset(p.x * size.width, p.y * size.height))
        }
        fun label(index: Int, title: String, color: Color) {
            mappedPoints.getOrNull(index)?.let { p ->
                val at = Offset(p.x * size.width + 7.dp.toPx(), p.y * size.height - 7.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(title, at.x, at.y, android.graphics.Paint().apply { this.color = color.toArgb(); textSize = 12.dp.toPx(); isFakeBoldText = true; setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK) })
            }
        }
        label(1, "FACE", Color.White); label(33, "EYE L", Color.Cyan); label(362, "EYE R", Color.Cyan)
        label(468, "IRIS", Color.Yellow); label(13, "MOUTH", Color(0xFFFF9B85))
    }
}

private fun rgbaBitmap(width: Int, height: Int, buffer: ByteBuffer, rowStride: Int, pixelStride: Int): Bitmap {
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val pixels = IntArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
        val i = y * rowStride + x * pixelStride
        val r = bytes[i].toInt() and 0xff; val g = bytes[i + 1].toInt() and 0xff; val b = bytes[i + 2].toInt() and 0xff; val a = bytes[i + 3].toInt() and 0xff
        pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
