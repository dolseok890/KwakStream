package kr.kwakstream.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        setContent { CameraScreen() }
    }
}

private fun zoomLabel(value: Float): String =
    if (abs(value - round(value)) < 0.01f) "${round(value).toInt()}×"
    else String.format(Locale.US, "%.1f×", value)

/** CameraX zoom limits contain DIGITAL zoom. Only offer additional stops when
 * Camera2 enumerates actual physical cameras and their focal/sensor sizes.
 * A ratio is relative to the physical wide-angle camera's field of view; the
 * actual lens selected by CameraX still requires device testing.
 */
private fun cameraApiLensStops(context: Context, camera: Camera): List<Float> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return listOf(1f)
    return try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = Camera2CameraInfo.from(camera.cameraInfo).cameraId
        val logical = manager.getCameraCharacteristics(id)
        val ids = logical.physicalCameraIds
        if (ids.isEmpty()) return listOf(1f)
        // Effective focal length (relative field of view) corrects for different sensor sizes.
        val equivalents = ids.flatMap { physicalId ->
            val characteristics = manager.getCameraCharacteristics(physicalId)
            val size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val focals = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            if (size == null || focals == null || size.width <= 0 || size.height <= 0) emptyList()
            else focals.filter { it > 0 }.map { focal ->
                focal.toDouble() * 43.2666 / hypot(size.width.toDouble(), size.height.toDouble())
            }
        }
        if (equivalents.size < 2) return listOf(1f)
        // Standard wide cameras are usually near 24–28 mm full-frame equivalent.
        val baseline = equivalents.minByOrNull { abs(it - 26.0) } ?: return listOf(1f)
        val limits = camera.cameraInfo.zoomState.value
        val minRatio = limits?.minZoomRatio ?: 1f
        val maxRatio = limits?.maxZoomRatio ?: 1f
        val values = equivalents.map { (it / baseline).toFloat() }
            .filter { it >= minRatio - 0.01f && it <= maxRatio + 0.01f }
            .map { round(it * 10f) / 10f }
            .filter { it > 0f }
        (values + 1f).distinct().sorted()
    } catch (t: Exception) {
        Log.w("KwakStream", "[E_LENS_API] Physical camera metadata unavailable", t)
        listOf(1f)
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var front by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("카메라 준비 중") }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomStops by remember { mutableStateOf(listOf(1f)) }
    var selectedZoom by remember { mutableFloatStateOf(1f) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) status = "E_PERMISSION: 카메라 권한이 필요합니다"
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    DisposableEffect(granted, front, owner) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        camera = null
        zoomStops = listOf(1f)
        selectedZoom = 1f
        if (granted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed) {
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        if (!cameraProvider.hasCamera(selector)) {
                            status = "E_NO_CAMERA: ${if (front) "전면" else "후면"} 카메라 없음"
                        } else {
                            cameraProvider.unbindAll()
                            val resolution = ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                                .build()
                            val preview = Preview.Builder().setResolutionSelector(resolution).build()
                                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val bound = cameraProvider.bindToLifecycle(owner, selector, preview)
                            camera = bound
                            zoomStops = cameraApiLensStops(context, bound)
                            status = "${if (front) "전면" else "후면"} 카메라 · API 렌즈 배율 ${zoomStops.size}개"
                            Log.i("KwakStream", "[CAM_OK] front=$front lens stops=$zoomStops")
                        }
                    } catch (e: Exception) {
                        status = "E_CAMERA: ${e.localizedMessage ?: "카메라 시작 실패"}"
                        Log.e("KwakStream", "[E_CAMERA] bindToLifecycle", e)
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            disposed = true
            provider?.unbindAll()
            camera = null
        }
    }

    val configuration = LocalConfiguration.current
    val aspect = if (configuration.screenWidthDp > configuration.screenHeightDp) 16f / 9f else 9f / 16f
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val previewModifier = if (maxWidth.value / maxHeight.value > aspect)
            Modifier.fillMaxHeight().aspectRatio(aspect) else Modifier.fillMaxWidth().aspectRatio(aspect)
        Box(previewModifier.background(Color.Black), contentAlignment = Alignment.Center) {
            if (granted) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(status, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!granted) Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("권한 허용") }
                    Button(onClick = { front = !front }, enabled = granted) {
                        Text("카메라 전환: [${if (front) "전면" else "후면"}]")
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    zoomStops.forEach { factor ->
                        Button(onClick = {
                            val current = camera
                            if (current == null) { status = "E_ZOOM: 카메라 준비 중" }
                            else {
                                try {
                                    val result = current.cameraControl.setZoomRatio(factor)
                                    result.addListener({
                                        try {
                                            result.get()
                                            selectedZoom = factor
                                            status = "${if (front) "전면" else "후면"} · API 줌 ${zoomLabel(factor)}"
                                        } catch (e: Exception) {
                                            status = "E_ZOOM: ${e.localizedMessage ?: "적용 실패"}"
                                            Log.e("KwakStream", "[E_ZOOM]", e)
                                        }
                                    }, ContextCompat.getMainExecutor(context))
                                } catch (e: Exception) {
                                    status = "E_ZOOM: ${e.localizedMessage ?: "줌 범위 오류"}"
                                    Log.e("KwakStream", "[E_ZOOM]", e)
                                }
                            }
                        }, enabled = granted && camera != null && abs(selectedZoom - factor) > 0.01f) {
                            Text(zoomLabel(factor))
                        }
                    }
                }
            }
        }
    }
}
