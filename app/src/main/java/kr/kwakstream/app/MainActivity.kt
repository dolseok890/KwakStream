package kr.kwakstream.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.view.TextureView
import java.util.Locale
import kotlin.math.abs
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
    if (abs(value - round(value)) < 0.01f) "${round(value).toInt()}"
    else String.format(Locale.US, "%.1f", value)

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var front by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("카메라 준비 중") }
    var camera by remember { mutableStateOf<Camera2Controller?>(null) }
    var engine by remember { mutableStateOf<Camera2Controller?>(null) }
    var zoomStops by remember { mutableStateOf(listOf(1f)) }
    var selectedZoom by remember { mutableFloatStateOf(1f) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) status = "E_PERMISSION: 카메라 권한이 필요합니다"
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    val previewView = remember(context) {
        TextureView(context).apply { isOpaque = false }
    }

    DisposableEffect(granted, owner) {
        var disposed = false
        var current: Camera2Controller? = null
        camera = null
        engine = null
        zoomStops = listOf(1f)
        selectedZoom = 1f
        if (granted) {
            val controller = Camera2Controller(context, previewView, front,
                onReady = { stops ->
                    if (!disposed) {
                        camera = current
                        zoomStops = stops
                        status = "${if (front) "전면" else "후면"} 카메라 · API 렌즈 배율 ${stops.size}개"
                        Log.i("KwakStream", "[CAM_OK] front=$front lens stops=$stops")
                    }
                },
                onStatus = { message -> if (!disposed) status = message })
            current = controller
            engine = controller
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> controller.resume()
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> controller.pause()
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                controller.resume()
            }
            onDispose {
                disposed = true
                owner.lifecycle.removeObserver(observer)
                controller.release()
                engine = null
                camera = null
            }
        } else {
            onDispose {
                disposed = true
                camera = null
            }
        }
    }

    LaunchedEffect(front, engine) {
        val activeEngine = engine
        if (activeEngine != null) {
            camera = null
            zoomStops = listOf(1f)
            selectedZoom = 1f
            activeEngine.switchFacing(front)
        }
    }

    fun applyZoom(factor: Float) {
        val current = camera
        if (current == null) { status = "E_ZOOM: 카메라 준비 중"; return }
        try {
            current.setZoomRatio(factor) { succeeded, error ->
                if (succeeded) { selectedZoom = factor; status = "${if (front) "전면" else "후면"} · API 줌 ${zoomLabel(factor)}" }
                else status = "E_ZOOM: ${error ?: "적용 실패"}"
            }
        } catch (e: Exception) { status = "E_ZOOM: ${e.localizedMessage ?: "줌 범위 오류"}"; Log.e("KwakStream", "[E_ZOOM]", e) }
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
            if (front && granted && camera != null) {
                Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val normal = zoomStops.firstOrNull { abs(it - 1f) < 0.01f }
                    val wide = zoomStops.filter { it < 0.99f }.minOrNull()
                    if (normal != null) FrontLensButton(false, abs(selectedZoom - normal) < 0.01f) { applyZoom(normal) }
                    if (wide != null) FrontLensButton(true, abs(selectedZoom - wide) < 0.01f) { applyZoom(wide) }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(status, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 4.dp))
                if (status.startsWith("E_CAMERA")) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(status)) }) { Text("오류 복사", color = Color.White) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!granted) Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("권한 허용") }
                    Button(onClick = { front = !front }, enabled = granted) {
                        Text("카메라 전환: [${if (front) "전면" else "후면"}]")
                    }
                }
                if (!front) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        zoomStops.forEach { factor ->
                            val selected = abs(selectedZoom - factor) < 0.01f
                            Button(onClick = { applyZoom(factor) }, enabled = granted && camera != null,
                                modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color.White else Color.Black.copy(alpha = 0.65f), contentColor = if (selected) Color.Black else Color.White)) {
                                Text(zoomLabel(factor))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrontLensButton(wide: Boolean, selected: Boolean, onClick: () -> Unit) {
    val ink = if (selected) Color.Black else Color.White
    Button(onClick = onClick, modifier = Modifier.size(48.dp).semantics { contentDescription = if (wide) "넓게" else "일반" },
        shape = CircleShape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color.White else Color.Black.copy(alpha = 0.65f))) {
        Canvas(Modifier.size(24.dp)) {
            val u = size.width / 24f
            val xs = if (wide) listOf(8f, 16f) else listOf(12f)
            xs.forEach { x ->
                drawCircle(ink, radius = 2.5f * u, center = Offset(x * u, 7f * u))
                drawLine(ink, Offset((x - 3f) * u, 17f * u), Offset((x + 3f) * u, 17f * u), strokeWidth = 3f * u)
            }
        }
    }
}
