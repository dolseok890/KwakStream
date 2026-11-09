package kr.kwakstream.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

/** Only IDs returned by CameraManager.cameraIdList are selectable/openable.
 * Physical IDs belonging to a logical camera are metadata, not guaranteed openable IDs.
 */
data class CameraChoice(val id: String, val facing: Int, val title: String)

data class Camera2UiState(
    val status: String = "카메라 준비 중",
    val cameras: List<CameraChoice> = emptyList(),
    val selectedId: String? = null,
    val zoomStops: List<Float> = emptyList(),
    val selectedZoom: Float = 1f,
    val previewInfo: String = "",
    val ready: Boolean = false,
    val error: Boolean = false
)

/** All Camera2 calls and mutable camera state live on a single HandlerThread.
 * The UI receives snapshots on the main thread only. Failed sessions never kill the Activity.
 */
class Camera2Controller(private val context: Context) {
    companion object { private const val TAG = "KwakStreamCamera2" }

    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("KwakStream-Camera2").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    var onState: ((Camera2UiState) -> Unit)? = null
    private var snapshot = Camera2UiState()
    private var released = false
    private var active = false
    private var permission = false
    private var textureView: TextureView? = null
    private var texture: SurfaceTexture? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var generation = 0
    private var opening = false
    private var device: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private var selectedId: String? = null
    private var selectedZoom = 1f
    private var selectedSize: Size? = null
    private var sensorOrientation = 0
    private var displayRotation = Surface.ROTATION_0
    private var isFront = false
    private var lastFrameAt = 0L
    private var awaitingFirstFrame = false
    private var retryCount = 0
    private var watchdogToken = 0

    private fun update(state: Camera2UiState) {
        snapshot = state
        main.post { if (!released) onState?.invoke(state) }
    }

    private fun info(text: String, ready: Boolean = false) {
        Log.i(TAG, text)
        update(snapshot.copy(status = text, ready = ready, error = false))
    }

    private fun error(code: String, message: String, problem: Throwable? = null) {
        Log.e(TAG, "[$code] $message", problem)
        update(snapshot.copy(status = "$code: $message", ready = false, error = true))
    }

    fun setPermission(allowed: Boolean) = worker.post {
        if (released) return@post
        if (permission == allowed) return@post
        permission = allowed
        if (allowed) {
            discoverCameras()
            openIfPossible()
        } else {
            closeCamera()
            update(snapshot.copy(status = "E_PERMISSION: 카메라 권한이 필요합니다", ready = false, error = true))
        }
    }

    fun setActive(enabled: Boolean) = worker.post {
        if (released || active == enabled) return@post
        active = enabled
        if (enabled) openIfPossible()
        else { closeCamera(); info("일시 중지 · 카메라 해제") }
    }

    fun setTexture(view: TextureView, surfaceTexture: SurfaceTexture?, width: Int, height: Int) = worker.post {
        if (released) return@post
        val changed = texture !== surfaceTexture || textureView !== view
        textureView = view
        texture = surfaceTexture
        surfaceWidth = width
        surfaceHeight = height
        if (surfaceTexture == null) { closeCamera(); info("화면 Surface 대기 중") }
        else if (changed) { closeCamera(); openIfPossible() }
        else {
            refreshCameraMetadataForFoldable()
            applyPreviewTransform()
        }
    }

    /** Some foldables can swap an active physical sensor without changing the logical ID. */
    private fun refreshCameraMetadataForFoldable() {
        val id = selectedId ?: return
        try {
            val meta = manager.getCameraCharacteristics(id)
            val currentSensor = meta.get(CameraCharacteristics.SENSOR_ORIENTATION)
            if (currentSensor != null && currentSensor != sensorOrientation) {
                Log.i(TAG, "PREVIEW_SENSOR_CHANGED camera=$id old=$sensorOrientation new=$currentSensor")
                sensorOrientation = currentSensor
            }
            isFront = meta.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } catch (e: CameraAccessException) {
            Log.w(TAG, "PREVIEW_SENSOR_REFRESH_FAILED", e)
        }
    }

    /** UI-thread display rotation notification; also handles 180° turns with no size change. */
    fun onDisplayRotationChanged(rotation: Int) = worker.post {
        if (released || displayRotation == rotation) return@post
        displayRotation = rotation
        refreshCameraMetadataForFoldable()
        applyPreviewTransform()
    }

    fun onPreviewFrame() = worker.post {
        if (released || captureSession == null) return@post
        lastFrameAt = SystemClock.elapsedRealtime()
        if (awaitingFirstFrame) {
            awaitingFirstFrame = false
            applyPreviewTransform() // The actual camera stream has now reached TextureView.
            retryCount = 0
            info("CAM_OK · ${cameraLabel(selectedId)} · 미리보기 정상", ready = true)
        }
    }

    fun switchFacing() = worker.post {
        if (released) return@post
        val currentFace = snapshot.cameras.find { it.id == selectedId }?.facing
        val target = if (currentFace == CameraCharacteristics.LENS_FACING_FRONT)
            CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
        val candidate = snapshot.cameras.firstOrNull { it.facing == target }
        if (candidate == null) { error("E_NO_CAMERA", "${if (target == CameraCharacteristics.LENS_FACING_FRONT) "전면" else "후면"} 카메라가 없습니다"); return@post }
        chooseOnWorker(candidate.id)
    }

    fun chooseCamera(cameraId: String) = worker.post {
        if (!released) chooseOnWorker(cameraId)
    }

    private fun chooseOnWorker(cameraId: String) {
        if (snapshot.cameras.none { it.id == cameraId }) {
            error("E_CAMERA_ID", "기기가 제공하지 않는 카메라 ID"); return
        }
        if (selectedId == cameraId) return
        selectedId = cameraId
        selectedZoom = 1f
        selectedSize = null // do not apply the previous camera's shape to the new camera.
        update(snapshot.copy(previewInfo = ""))
        retryCount = 0
        update(snapshot.copy(selectedId = cameraId, zoomStops = emptyList(), selectedZoom = 1f, ready = false))
        closeCamera()
        openIfPossible()
    }

    /** Explicit retry after permission/device failures; no unbounded auto-retry loop. */
    fun retry() = worker.post {
        if (released) return@post
        closeCamera()
        if (permission) discoverCameras()
        retryCount = 0
        openIfPossible()
    }

    fun zoomTo(value: Float) = worker.post {
        if (released) return@post
        if (snapshot.zoomStops.none { abs(it - value) < 0.005f }) {
            error("E_ZOOM_RANGE", "API에서 확인되지 않은 줌 배율"); return@post
        }
        if (device == null || captureSession == null || previewBuilder == null) {
            error("E_ZOOM", "카메라가 아직 준비되지 않았습니다"); return@post
        }
        try {
            val characteristics = manager.getCameraCharacteristics(selectedId ?: return@post)
            setRequestZoom(previewBuilder ?: return@post, characteristics, value)
            val current = captureSession ?: return@post
            current.setRepeatingRequest((previewBuilder ?: return@post).build(), frameCallback, worker)
            selectedZoom = value
            update(snapshot.copy(selectedZoom = value, status = "줌 요청 ${zoomLabel(value)} · 카메라 응답 대기", error = false))
        } catch (e: Exception) { error("E_ZOOM", "줌 변경 실패", e) }
    }

    private fun discoverCameras() {
        if (!permission) return
        try {
            val choices = manager.cameraIdList.mapNotNull { id ->
                val meta = manager.getCameraCharacteristics(id)
                val face = meta.get(CameraCharacteristics.LENS_FACING) ?: return@mapNotNull null
                if (face != CameraCharacteristics.LENS_FACING_FRONT && face != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
                val dir = if (face == CameraCharacteristics.LENS_FACING_FRONT) "전면" else "후면"
                // List only camera IDs that CameraManager actually reports as openable.
                // Focal-length (mm) metadata is NOT a camera selector or a zoom stop.
                CameraChoice(id, face, "$dir ID $id")
            }
            val default = choices.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK } ?: choices.firstOrNull()
            if (choices.none { it.id == selectedId }) selectedId = default?.id
            update(snapshot.copy(cameras = choices, selectedId = selectedId))
            if (choices.isEmpty()) error("E_NO_CAMERA", "사용 가능한 전·후면 카메라 없음")
        } catch (e: Exception) { error("E_ENUM", "카메라 목록 조회 실패", e) }
    }

    @SuppressLint("MissingPermission") // permission is rechecked immediately before openCamera.
    private fun openIfPossible() {
        if (released || !active || !permission || texture == null || device != null || opening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permission = false
            error("E_PERMISSION", "카메라 권한이 해제되었습니다")
            return
        }
        if (snapshot.cameras.isEmpty()) discoverCameras()
        val id = selectedId ?: return
        val requestGeneration = ++generation
        try {
            val meta = manager.getCameraCharacteristics(id)
            // Never assume a generic 90-degree sensor: orientation is device metadata.
            sensorOrientation = meta.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: run {
                error("E_ORIENTATION", "기기의 카메라 센서 방향 정보가 없습니다")
                return
            }
            isFront = meta.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            val sizes = meta.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
            val size = pickPreviewSize(sizes, meta.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE))
            if (size == null) { error("E_SIZE", "지원되는 미리보기 해상도가 없습니다"); return }
            selectedSize = size
            applyPreviewTransform()
            info("CAM_OPEN · ${cameraLabel(id)} · ${size.width}×${size.height}")
            opening = true
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (!valid(requestGeneration)) { camera.close(); return }
                    opening = false
                    device = camera
                    createSession(camera, meta, size, requestGeneration)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (!valid(requestGeneration)) return
                    closeCamera()
                    error("E_DISCONNECTED", "카메라 연결이 끊겼습니다")
                }
                override fun onError(camera: CameraDevice, errorCode: Int) {
                    camera.close()
                    if (!valid(requestGeneration)) return
                    closeCamera()
                    error("E_DEVICE_$errorCode", "카메라 장치 오류")
                }
            }, worker)
            worker.postDelayed({
                if (valid(requestGeneration) && opening) {
                    closeCamera()
                    error("E_OPEN_TIMEOUT", "카메라 연결 응답 시간 초과")
                }
            }, 8000)
        } catch (e: SecurityException) { opening = false; error("E_PERMISSION", "카메라 접근 권한 없음", e) }
        catch (e: CameraAccessException) { opening = false; error("E_ACCESS_${e.reason}", "카메라 열기 실패", e) }
        catch (e: Exception) { opening = false; error("E_OPEN", "카메라 초기화 실패", e) }
    }

    private fun createSession(camera: CameraDevice, meta: CameraCharacteristics, size: Size, token: Int) {
        if (!valid(token)) return
        val tex = texture ?: return
        try {
            tex.setDefaultBufferSize(size.width, size.height)
            // Buffer size is now final. Apply geometry again after SurfaceTexture setup.
            applyPreviewTransform()
            val surface = Surface(tex)
            previewSurface = surface
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            // Android 12+ may auto-rotate AND CROP the HAL output in portrait apps.
            // We apply orientation ourselves and require a full FIT_CENTER image.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val modes = meta.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES)
                    ?: intArrayOf()
                if (modes.contains(CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)) {
                    builder.set(CaptureRequest.SCALER_ROTATE_AND_CROP,
                        CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)
                }
            }
            val afModes = meta.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            if (afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO))
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            val stops = supportedZoomStops(meta)
            selectedZoom = if (stops.any { abs(it - 1f) < 0.005f }) 1f else stops.firstOrNull() ?: 1f
            setRequestZoom(builder, meta, selectedZoom)
            previewBuilder = builder
            update(snapshot.copy(zoomStops = stops, selectedZoom = selectedZoom, ready = false))
            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (!valid(token)) { session.close(); return }
                    captureSession = session
                    try {
                        awaitingFirstFrame = true
                        lastFrameAt = SystemClock.elapsedRealtime()
                        session.setRepeatingRequest(builder.build(), frameCallback, worker)
                        info("CAM_PREVIEW · 첫 프레임 대기")
                        startWatchdog(token)
                    } catch (e: Exception) {
                        error("E_REPEAT", "미리보기 반복 요청 실패", e)
                        closeCamera()
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    session.close()
                    if (!valid(token)) return
                    closeCamera()
                    error("E_SESSION", "카메라 세션 설정 실패")
                }
            }, worker)
            worker.postDelayed({
                if (valid(token) && captureSession == null && device != null) {
                    closeCamera()
                    error("E_SESSION_TIMEOUT", "카메라 세션 설정 시간 초과")
                }
            }, 8000)
        } catch (e: Exception) {
            error("E_SESSION_CREATE", "미리보기 세션 생성 실패", e)
            closeCamera()
        }
    }

    private val frameCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            // TextureView frame notification (not capture callback) confirms visible preview.
            val applied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else null
            if (applied != null && abs(applied - selectedZoom) < 0.01f && !awaitingFirstFrame && snapshot.ready && snapshot.status.startsWith("줌 요청")) {
                info("CAM_OK · 줌 ${zoomLabel(applied)} 적용", ready = true)
            }
        }
        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            error("E_CAPTURE_${failure.reason}", "프레임 캡처 실패")
        }
    }

    private fun startWatchdog(token: Int) {
        val currentWatchdog = ++watchdogToken
        worker.postDelayed(object : Runnable {
            override fun run() {
                if (!valid(token) || currentWatchdog != watchdogToken || captureSession == null) return
                val gap = SystemClock.elapsedRealtime() - lastFrameAt
                if (gap > 8000) {
                    if (retryCount < 2) {
                        retryCount++
                        Log.w(TAG, "[E_STALL] No preview frames $gap ms. Restart $retryCount/2")
                        closeCamera()
                        openIfPossible()
                    } else {
                        closeCamera()
                        error("E_STALL", "미리보기 중단 · 복구 2회 실패")
                    }
                } else worker.postDelayed(this, 2000)
            }
        }, 8000)
    }

    private fun valid(token: Int) = !released && active && permission && generation == token && texture != null

    private fun closeCamera() {
        generation++
        opening = false
        watchdogToken++
        awaitingFirstFrame = false
        try { captureSession?.stopRepeating() } catch (_: Exception) { }
        try { captureSession?.close() } catch (_: Exception) { }
        captureSession = null
        try { device?.close() } catch (_: Exception) { }
        device = null
        previewBuilder = null
        try { previewSurface?.release() } catch (_: Exception) { }
        previewSurface = null
        update(snapshot.copy(ready = false))
    }

    fun release() {
        worker.post {
            if (!released) { closeCamera(); released = true; thread.quitSafely() }
        }
    }

    private fun cameraLabel(id: String?): String = snapshot.cameras.firstOrNull { it.id == id }?.title ?: "ID ${id ?: "?"}"

    private fun applyPreviewTransform() {
        val view = textureView ?: return
        val size = selectedSize ?: return
        val width = surfaceWidth
        val height = surfaceHeight
        if (width <= 0 || height <= 0) return
        val displayDegrees = when (displayRotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        // TextureView already applies SENSOR_ORIENTATION at the SurfaceTexture
        // layer. The old code applied cameraRelativeRotation() here as well,
        // resulting in a sideways picture on portrait devices (especially
        // front cameras with SENSOR_ORIENTATION=270). Only the residual
        // display rotation belongs in TextureView.setTransform().
        val fit = texturePreviewFit(
            size.width, size.height, sensorOrientation, displayDegrees, width, height
        )
        val src = floatArrayOf(
            0f, 0f, width.toFloat(), 0f,
            width.toFloat(), height.toFloat(), 0f, height.toFloat()
        )
        val token = generation
        main.post {
            if (released || view !== textureView || token != generation) return@post
            val matrix = Matrix()
            if (matrix.setPolyToPoly(src, 0, fit.destinationCorners, 0, 4)) {
                view.setTransform(matrix)
                Log.i(TAG, "PREVIEW_APPLIED camera=$selectedId sensor=$sensorOrientation " +
                    "display=$displayDegrees correction=${fit.displayCorrection} " +
                    "natural=${fit.sensorOrientedSize.first}x${fit.sensorOrientedSize.second} " +
                    "fitted=${fit.displayedSize.first}x${fit.displayedSize.second} " +
                    "view=${width}x$height FIT_CENTER")
            } else {
                Log.e(TAG, "[E_PREVIEW_MATRIX] Matrix.setPolyToPoly failed")
            }
        }
        update(snapshot.copy(previewInfo = "방향: 센서 ${sensorOrientation}° · 화면 ${displayDegrees}° · " +
            "미리보기 보정 ${fit.displayCorrection}°"))
    }

}

/**
 * Display only the operating system's zoom RANGE endpoints and the neutral 1× value.
 * These are legal digital/logical zoom values, NOT guaranteed physical lens positions.
 * Do not infer any "lens stop" from focal lengths, sensor sizes, 35mm-equivalent
 * values or hidden physical IDs. Camera2 exposes no universal lens-switch table.
 */
internal fun supportedZoomStops(meta: CameraCharacteristics): List<Float> {
    val range = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        meta.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
    val minRatio = range?.lower ?: 1f
    val maxRatio = range?.upper ?: (meta.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
    if (!minRatio.isFinite() || !maxRatio.isFinite() || minRatio <= 0f || maxRatio < minRatio) {
        Log.w("KwakStreamCamera2", "Invalid zoom capability range; hiding zoom controls")
        return emptyList()
    }
    val stops = mutableListOf(minRatio)
    if (1f in minRatio..maxRatio) stops.add(1f)
    stops.add(maxRatio)
    // No rounding of capability values; remove only nearly identical endpoints.
    return stops.sorted().fold(mutableListOf<Float>()) { result, value ->
        if (result.none { abs(it - value) < 0.0001f }) result.add(value)
        result
    }
}

private fun setRequestZoom(builder: CaptureRequest.Builder, meta: CameraCharacteristics, zoom: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && meta.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) != null) {
        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
    } else {
        val rect: Rect = meta.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val effective = zoom.coerceAtLeast(1f)
        val w = (rect.width() / effective).toInt().coerceAtLeast(2)
        val h = (rect.height() / effective).toInt().coerceAtLeast(2)
        val left = rect.left + (rect.width() - w) / 2
        val top = rect.top + (rect.height() - h) / 2
        builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + w, top + h))
    }
}

/** Prefer the camera's native sensor aspect ratio instead of forcing 16:9.
 * Only choose an actual Camera2-supported SurfaceTexture output size.
 */
private fun pickPreviewSize(sizes: Array<Size>?, sensorRect: Rect?): Size? {
    val available = sizes?.toList().orEmpty()
    if (available.isEmpty()) return null
    val eligible = available.filter { it.width >= 320 && it.height >= 240 }
        .ifEmpty { available }
    val nativeAspect = sensorRect?.takeIf { it.width() > 0 && it.height() > 0 }
        ?.let { maxOf(it.width(), it.height()).toDouble() / minOf(it.width(), it.height()) }
    return eligible.minWithOrNull(
        compareBy<Size> {
            if (nativeAspect == null) 0.0
            else abs(maxOf(it.width, it.height).toDouble() / minOf(it.width, it.height) - nativeAspect)
        }.thenBy { abs(it.width.toLong() * it.height - 1280L * 960L) }
    )
}

internal fun zoomLabel(value: Float): String {
    return if (abs(value - round(value)) < .005f) "${round(value).toInt()}×"
    else String.format(Locale.US, "%.2g×", value)
}
