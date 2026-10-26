package app.drift.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import app.drift.R
import app.drift.ui.MainActivity
import app.drift.vision.AttentionMetrics
import app.drift.vision.FatigueStateMachine
import app.drift.vision.FatigueStatus
import app.drift.vision.FaceExpression
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** User-started camera monitor; the foreground service keeps analysis active over other apps. */
class FatigueMonitorService : LifecycleService() {
    private var cameraProvider: ProcessCameraProvider? = null
    private var landmarker: FaceLandmarker? = null
    private var analyzerExecutor: ExecutorService? = null
    private var lastAlertAt = 0L
    private var lastAlert: FatigueStatus? = null
    private var lastExpression: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return Service.START_NOT_STICKY
        }
        createChannels()
        val notification = ongoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(this, SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            ServiceCompat.startForeground(this, SERVICE_NOTIFICATION_ID, notification, 0)
        }
        if (landmarker == null) startMonitoring()
        return Service.START_STICKY
    }

    private fun startMonitoring() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        analyzerExecutor = Executors.newSingleThreadExecutor()
        val stateMachine = FatigueStateMachine()
        try {
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setOutputFaceBlendshapes(true)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, inputImage ->
                    val face = result.faceLandmarks().firstOrNull()
                    val expression = result.faceBlendshapes().orElse(emptyList()).firstOrNull().orEmpty().takeIf { face != null }?.let { categories ->
                        FaceExpression.estimate(categories.associate { it.categoryName() to it.score() })
                    }
                    if (expression != lastExpression) {
                        lastExpression = expression
                        getSystemService(NotificationManager::class.java).notify(SERVICE_NOTIFICATION_ID, ongoingNotification())
                    }
                    val status = if (face == null) {
                        stateMachine.update(SystemClock.elapsedRealtime(), null, null)
                    } else {
                        val points = face.map { AttentionMetrics.Point(it.x(), it.y()) }
                        val earA = AttentionMetrics.ear(points, intArrayOf(33, 160, 158, 133, 153, 144))
                        val earB = AttentionMetrics.ear(points, intArrayOf(362, 385, 387, 263, 373, 380))
                        val ear = if (earA != null && earB != null) (earA + earB) / 2f else earA ?: earB
                        val mar = AttentionMetrics.mar(points.getOrElse(13) { AttentionMetrics.Point(0f, 0f) }, points.getOrElse(14) { AttentionMetrics.Point(0f, 0f) }, points.getOrElse(61) { AttentionMetrics.Point(0f, 0f) }, points.getOrElse(291) { AttentionMetrics.Point(0f, 0f) })
                        stateMachine.update(SystemClock.elapsedRealtime(), ear, mar)
                    }
                    maybeAlert(status)
                    inputImage.close()
                }
                .setErrorListener { showAlert("Face monitoring paused", it.message ?: "Camera analysis encountered an error.") }
                .build()
            landmarker = FaceLandmarker.createFromOptions(this, options)

            val providerFuture = ProcessCameraProvider.getInstance(this)
            providerFuture.addListener({
                if (landmarker == null) return@addListener
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                    analysis.setAnalyzer(analyzerExecutor!!) { image ->
                        try {
                            val bitmap = rgbaBitmap(image.width, image.height, image.planes[0].buffer, image.planes[0].rowStride, image.planes[0].pixelStride)
                            landmarker?.detectAsync(BitmapImageBuilder(bitmap).build(), ImageProcessingOptions.builder().setRotationDegrees(image.imageInfo.rotationDegrees).build(), SystemClock.uptimeMillis())
                        } catch (e: Exception) {
                            showAlert("Face monitoring paused", e.message ?: "Camera analysis encountered an error.")
                        } finally {
                            image.close()
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                } catch (e: Exception) {
                    showAlert("Camera unavailable", e.message ?: "Unable to start background monitoring.")
                    stopSelf()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            showAlert("Face model unavailable", e.message ?: "Unable to load the face model.")
            stopSelf()
        }
    }

    private fun maybeAlert(status: FatigueStatus) {
        val title = when (status) {
            FatigueStatus.POSSIBLE_DROWSINESS -> "Possible drowsiness detected"
            FatigueStatus.POSSIBLE_YAWN -> "Possible fatigue signal"
            FatigueStatus.ELEVATED_INDICATORS -> "Possible drowsiness detected"
            else -> return
        }
        val now = SystemClock.elapsedRealtime()
        if (status != lastAlert || now - lastAlertAt >= ALERT_COOLDOWN_MS) {
            lastAlert = status
            lastAlertAt = now
            showAlert(title, "Consider pausing Reels and resting your eyes.")
        }
    }

    private fun showAlert(title: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build())
    }

    private fun ongoingNotification(): Notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher)
        .setContentTitle("Drift fatigue monitoring is on")
        .setContentText("Expression: ${lastExpression ?: "detecting"} · Tap Stop to end monitoring.")
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, "Stop", PendingIntent.getService(this, 1, Intent(this, FatigueMonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(SERVICE_CHANNEL_ID, "Fatigue monitoring", NotificationManager.IMPORTANCE_LOW).apply { description = "Shows while background face monitoring is active." })
        manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL_ID, "Fatigue alerts", NotificationManager.IMPORTANCE_HIGH).apply { description = "Heads-up alerts for possible drowsiness or fatigue." })
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        try { landmarker?.close() } catch (_: Exception) { }
        landmarker = null
        analyzerExecutor?.shutdownNow()
        analyzerExecutor = null
        getSystemService(NotificationManager::class.java).cancel(ALERT_NOTIFICATION_ID)
        super.onDestroy()
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

    companion object {
        const val ACTION_STOP = "app.drift.action.STOP_FATIGUE_MONITOR"
        const val EXTRA_START = "app.drift.extra.START_FATIGUE_MONITOR"
        private const val SERVICE_CHANNEL_ID = "fatigue_monitoring"
        private const val ALERT_CHANNEL_ID = "fatigue_alerts"
        private const val SERVICE_NOTIFICATION_ID = 4101
        private const val ALERT_NOTIFICATION_ID = 4102
        private const val ALERT_COOLDOWN_MS = 60_000L
    }
}
