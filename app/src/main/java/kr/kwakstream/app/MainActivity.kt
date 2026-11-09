package kr.kwakstream.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        setContent { CameraScreen() }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { Camera2Controller(context.applicationContext) }
    var state by remember { mutableStateOf(Camera2UiState()) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) state = state.copy(status = "E_PERMISSION: 카메라 권한이 필요합니다", error = true)
    }
    val previewView = remember(context) {
        val view = TextureView(context)
        view.isOpaque = true
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    controller.setTexture(view, surface, width, height)
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    controller.setTexture(view, surface, width, height)
                }
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    controller.setTexture(view, null, 0, 0)
                    return true
                }
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { controller.onPreviewFrame() }
            }
        view
    }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(controller, lifecycleOwner) {
        controller.onState = { state = it }
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val nowGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    granted = nowGranted
                    controller.setPermission(nowGranted)
                    controller.setActive(true)
                }
                Lifecycle.Event.ON_PAUSE -> controller.setActive(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        controller.setPermission(granted)
        controller.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        if (previewView.isAvailable) {
            controller.setTexture(previewView, previewView.surfaceTexture, previewView.width, previewView.height)
        }
        onDispose {
            lifecycle.removeObserver(observer)
            controller.onState = null
            controller.release()
        }
    }
    val configuration = LocalConfiguration.current
    val aspect = if (configuration.screenWidthDp > configuration.screenHeightDp) 16f / 9f else 9f / 16f
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center) {
        val fit = if (maxWidth.value / maxHeight.value > aspect)
            Modifier.fillMaxHeight().aspectRatio(aspect) else Modifier.fillMaxWidth().aspectRatio(aspect)
        Box(fit.background(Color.Black), contentAlignment = Alignment.Center) {
            if (granted) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.status, color = if (state.error) Color(0xFFFFB4AB) else Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.78f)).padding(7.dp))
                if (!granted) Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("권한 허용") }
                else if (state.error) OutlinedButton(onClick = { controller.retry() }) { Text("카메라 재시도") }
                val selected = state.cameras.find { it.id == state.selectedId }
                val otherFacing = if (selected?.facing == CameraCharacteristics.LENS_FACING_FRONT)
                    CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
                Button(onClick = { controller.switchFacing() },
                    enabled = granted && state.cameras.any { it.facing == otherFacing }) {
                    Text("카메라 전환: [${if (selected?.facing == CameraCharacteristics.LENS_FACING_FRONT) "전면" else "후면"}]")
                }
                // Each choice is an openable cameraIdList entry, never an assumed hidden physical ID.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.cameras.forEach { choice ->
                        OutlinedButton(onClick = { controller.chooseCamera(choice.id) },
                            enabled = granted && choice.id != state.selectedId) { Text(choice.title) }
                    }
                }
                // A zoom ratio is not a physical lens identifier.
                if (state.zoomStops.isNotEmpty()) Text(
                    "기기 지원 줌 범위 (렌즈 전환점 아님)",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.labelSmall
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.zoomStops.forEach { ratio ->
                        Button(onClick = { controller.zoomTo(ratio) },
                            enabled = granted && state.ready && abs(state.selectedZoom - ratio) > 0.005f) {
                            Text(zoomLabel(ratio))
                        }
                    }
                }
            }
        }
    }
}
