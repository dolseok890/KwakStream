package kr.kwakstream.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

/** Camera2 replacement for the v0.1.7 CameraX preview binding; no new UI. */
internal class Camera2Controller(
    private val context: Context,
    private val previewView: TextureView,
    initialFront: Boolean,
    private val onReady: (List<Float>) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("KwakStreamCamera2").apply { start() }
    private val cameraHandler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private var resumed = false // main thread only
    @Volatile private var front = initialFront
    @Volatile private var disposed = false

    // Camera resources are created and released on cameraHandler's thread only.
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var previewRequest: CaptureRequest.Builder? = null
    @Volatile private var currentCharacteristics: CameraCharacteristics? = null
    @Volatile private var previewSize: Size? = null

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            if (resumed && !disposed) open(texture)
        }
        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            updateTransform()
        }
        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            // CaptureSession must stop using this SurfaceTexture before its next use.
            generation.incrementAndGet()
            cameraHandler.post { closeCamera() }
            return true
        }
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }

    init {
        previewView.surfaceTextureListener = textureListener
    }

    fun resume() {
        if (disposed || resumed) return
        resumed = true
        previewView.surfaceTexture?.let { open(it) }
    }

    fun switchFacing(newFront: Boolean) {
        if (disposed || front == newFront) return
        front = newFront
        if (resumed) {
            val texture = previewView.surfaceTexture
            if (texture != null) open(texture)
        }
    }

    fun pause() {
        if (!resumed) return
        resumed = false
        generation.incrementAndGet()
        cameraHandler.post { closeCamera() }
    }

    fun release() {
        if (disposed) return
        disposed = true
        resumed = false
        generation.incrementAndGet()
        if (previewView.surfaceTextureListener === textureListener) {
            previewView.surfaceTextureListener = null
        }
        cameraHandler.post { closeCamera() }
        thread.quitSafely()
    }

    /** Called on UI thread when a camera surface is available. */
    private fun open(texture: SurfaceTexture) {
        if (disposed || !resumed) return
        val token = generation.incrementAndGet()
        cameraHandler.post {
            closeCamera()
            if (!isCurrent(token)) return@post
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
                reportError(token, "E_PERMISSION: 카메라 권한이 필요합니다")
                return@post
            }
            try {
                val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
                val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing
                }
                if (cameraId == null) {
                    reportError(token, "E_NO_CAMERA: ${if (front) "전면" else "후면"} 카메라 없음")
                    return@post
                }
                val info = cameraManager.getCameraCharacteristics(cameraId)
                val sizes = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(SurfaceTexture::class.java)
                    .orEmpty()
                val size = selectPreviewSize(sizes)
                    ?: throw IllegalStateException("SurfaceTexture preview size unavailable")
                texture.setDefaultBufferSize(size.width, size.height)
                val surface = Surface(texture)
                previewSurface = surface
                previewSize = size
                currentCharacteristics = info
                mainHandler.post { if (isCurrent(token)) updateTransform() }

                // Permission checked above; the callback is serialized on cameraHandler.
                openCameraDevice(cameraId, token, surface, info)
            } catch (e: Exception) {
                reportError(token, "E_CAMERA: ${e.localizedMessage ?: "카메라 시작 실패"}", e)
                closeCamera()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCameraDevice(
        id: String,
        token: Int,
        surface: Surface,
        characteristics: CameraCharacteristics
    ) {
        cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (!isCurrent(token)) { camera.close(); return }
                device = camera
                try {
                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        val modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                        if (modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        }
                    }
                    previewRequest = builder
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(readySession: CameraCaptureSession) {
                            if (!isCurrent(token)) { readySession.close(); return }
                            if (device !== camera || previewSurface !== surface || !surface.isValid) {
                                readySession.close()
                                reportError(token, "E_CAMERA: 미리보기 Surface가 변경되어 연결할 수 없습니다")
                                closeCamera()
                                return
                            }
                            session = readySession
                            try {
                                readySession.setRepeatingRequest(builder.build(), null, cameraHandler)
                                val stops = if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
                                    frontZoomStops(characteristics) else lensStops(id, characteristics)
                                mainHandler.post { if (isCurrent(token)) onReady(stops) }
                            } catch (e: Exception) {
                                reportError(token, "E_CAMERA: ${e.localizedMessage ?: "미리보기 실패"}", e)
                                closeCamera()
                            }
                        }
                        override fun onConfigureFailed(failed: CameraCaptureSession) {
                            failed.close()
                            // A previous generation may finish after a newer camera has opened.
                            // It must never close that newer camera's resources.
                            if (!isCurrent(token) || device !== camera) return
                            reportError(token, "E_CAMERA: 미리보기 구성 실패")
                            closeCamera()
                        }
                    }, cameraHandler)
                } catch (e: Exception) {
                    reportError(token, "E_CAMERA: ${e.localizedMessage ?: "카메라 시작 실패"}", e)
                    closeCamera()
                }
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                if (isCurrent(token)) {
                    reportError(token, "E_CAMERA: 카메라 연결 끊김")
                    closeCamera()
                }
            }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                if (isCurrent(token)) {
                    reportError(token, "E_CAMERA: 카메라 오류 $error")
                    closeCamera()
                }
            }
        }, cameraHandler)
    }

    fun setZoomRatio(value: Float, callback: (Boolean, String?) -> Unit) {
        val token = generation.get()
        cameraHandler.post {
            val capture = previewRequest
            val currentSession = session
            val info = currentCharacteristics
            if (!isCurrent(token) || capture == null || currentSession == null || info == null) {
                mainHandler.post { if (!disposed) callback(false, "카메라 준비 중") }
                return@post
            }
            try {
                val min: Float
                val max: Float
                val range = if (Build.VERSION.SDK_INT >= 30)
                    info.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
                if (range != null) {
                    min = range.lower
                    max = range.upper
                    require(value >= min - 0.001f && value <= max + 0.001f)
                    capture.set(CaptureRequest.CONTROL_ZOOM_RATIO, value)
                } else {
                    min = 1f
                    max = info.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                    require(value >= min - 0.001f && value <= max + 0.001f)
                    val active = info.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                        ?: throw IllegalStateException("Crop region unavailable")
                    val w = (active.width() / value).toInt().coerceAtLeast(2)
                    val h = (active.height() / value).toInt().coerceAtLeast(2)
                    val x = active.left + (active.width() - w) / 2
                    val y = active.top + (active.height() - h) / 2
                    capture.set(CaptureRequest.SCALER_CROP_REGION, Rect(x, y, x + w, y + h))
                }
                currentSession.setRepeatingRequest(capture.build(), null, cameraHandler)
                mainHandler.post { if (isCurrent(token)) callback(true, null) }
            } catch (e: Exception) {
                Log.e("KwakStream", "[E_ZOOM]", e)
                mainHandler.post { if (isCurrent(token)) callback(false, e.localizedMessage ?: "적용 실패") }
            }
        }
    }

    /** Front field-of-view choices; values stay within the camera's reported zoom range. */
    private fun frontZoomStops(info: CameraCharacteristics): List<Float> {
        val range = if (Build.VERSION.SDK_INT >= 30) info.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
        val min = range?.lower ?: 1f
        val max = range?.upper ?: (info.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
        if (!min.isFinite() || !max.isFinite() || min > max) return listOf(1f)
        val wide = min.coerceAtMost(1f).coerceIn(min, max)
        val normal = if (wide < 0.99f) 1f.coerceIn(min, max) else minOf(max, wide * 1.3f)
        return if (normal - wide >= 0.05f) listOf(wide, normal) else listOf(wide)
    }

    /** Preserve v0.1.7's physical metadata lens-stop heuristic, without inventing new buttons. */
    private fun lensStops(id: String, info: CameraCharacteristics): List<Float> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return listOf(1f)
        return try {
            val ids = info.physicalCameraIds
            if (ids.isEmpty()) return listOf(1f)
            val equivalents = ids.flatMap { physicalId ->
                val c = cameraManager.getCameraCharacteristics(physicalId)
                val s = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val f = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                if (s == null || f == null || s.width <= 0 || s.height <= 0) emptyList()
                else f.filter { it > 0f }.map { focal ->
                    focal.toDouble() * 43.2666 / hypot(s.width.toDouble(), s.height.toDouble())
                }
            }
            if (equivalents.size < 2) return listOf(1f)
            val baseline = equivalents.minByOrNull { abs(it - 26.0) } ?: return listOf(1f)
            val range = if (Build.VERSION.SDK_INT >= 30)
                info.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
            val minRatio = range?.lower ?: 1f
            val maxRatio = range?.upper ?: (info.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
            val values = equivalents.map { (it / baseline).toFloat() }
                .filter { it >= minRatio - 0.01f && it <= maxRatio + 0.01f }
                .map { round(it * 10f) / 10f }
                .filter { it > 0f }
            (values + 1f).distinct().sorted()
        } catch (e: Exception) {
            Log.w("KwakStream", "[E_LENS_API] Physical camera metadata unavailable: $id", e)
            listOf(1f)
        }
    }

    /** TextureView normalizes sensor orientation; correct display rotation and letterbox only. */
    private fun updateTransform() {
        val size = previewSize ?: return
        val info = currentCharacteristics ?: return
        if (!previewView.isAvailable || previewView.width == 0 || previewView.height == 0) return
        val sensorDegrees = info.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val displayDegrees = when (previewView.display?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val width = previewView.width.toFloat()
        val height = previewView.height.toFloat()
        val naturalW = if (sensorDegrees % 180 == 0) size.width.toFloat() else size.height.toFloat()
        val naturalH = if (sensorDegrees % 180 == 0) size.height.toFloat() else size.width.toFloat()
        val displayedW = if (displayDegrees % 180 == 0) naturalW else naturalH
        val displayedH = if (displayDegrees % 180 == 0) naturalH else naturalW
        val fit = minOf(width / displayedW, height / displayedH)
        val targetW = displayedW * fit
        val targetH = displayedH * fit
        // A TextureView begins stretched to its dimensions. Undo stretching and center FIT.
        val scaleX = if (displayDegrees % 180 == 0) targetW / width else targetH / width
        val scaleY = if (displayDegrees % 180 == 0) targetH / height else targetW / height
        val matrix = Matrix().apply {
            setScale(scaleX, scaleY, width / 2f, height / 2f)
            postRotate(-displayDegrees.toFloat(), width / 2f, height / 2f)
            if (front) postScale(-1f, 1f, width / 2f, height / 2f)
        }
        previewView.setTransform(matrix)
    }

    private fun isCurrent(token: Int): Boolean = !disposed && token == generation.get()

    private fun reportError(token: Int, message: String, e: Exception? = null) {
        if (e != null) Log.e("KwakStream", message, e) else Log.e("KwakStream", message)
        val surface = previewSurface
        val detail = buildString {
            append(message)
            append("\n단계: ")
            append(e?.stackTrace?.firstOrNull { it.className.contains("KwakStream") || it.className.contains("Camera2Controller") }?.let { "${it.className.substringAfterLast('.')} : ${it.lineNumber}" } ?: "세션 연결")
            append("\n세대: $token / ${generation.get()}")
            append(" · Surface: ${surface?.isValid ?: false}")
            append(" · 세션: ${session != null}")
        }
        mainHandler.post { if (isCurrent(token)) onStatus(detail) }
    }

    private fun closeCamera() {
        try { session?.stopRepeating() } catch (_: Exception) {}
        try { session?.abortCaptures() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        session = null
        try { device?.close() } catch (_: Exception) {}
        device = null
        try { previewSurface?.release() } catch (_: Exception) {}
        previewSurface = null
        previewRequest = null
        currentCharacteristics = null
        previewSize = null
    }

    private fun selectPreviewSize(sizes: Array<out Size>): Size? {
        // v0.1.7 preferred 16:9 and allowed other ratios as fallback.
        if (sizes.isEmpty()) return null
        val targetRatio = 16.0 / 9.0
        val preferred = sizes.filter { abs(it.width.toDouble() / it.height - targetRatio) < 0.025 }
        val available = if (preferred.isNotEmpty()) preferred else sizes.toList()
        return available.minWithOrNull(compareBy<Size> {
            abs(it.width.toLong() * it.height - 1920L * 1080L)
        }.thenBy { abs(it.width.toDouble() / it.height - targetRatio) })
    }
}
