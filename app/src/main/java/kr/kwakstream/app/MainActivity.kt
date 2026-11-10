package kr.kwakstream.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.BatteryManager
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        setContent { DashboardTheme { CameraScreen() } }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
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
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var micError by remember { mutableStateOf(false) }
    var micLevels by remember { mutableStateOf<List<Float>>(emptyList()) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        micGranted = allowed
        micError = !allowed
    }
    val meter = remember { MicrophoneMeter(onLevels = { micLevels = it }, onError = { micError = true }) }
    DisposableEffect(micGranted, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (micGranted) meter.start()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> meter.stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (micGranted && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) meter.start()
        onDispose { owner.lifecycle.removeObserver(observer); meter.stop() }
    }
    var front by rememberSaveable { mutableStateOf(false) }
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    var batteryCharging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
                val state = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                batteryCharging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
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
        val availableWidth = (maxWidth - 88.dp).coerceAtLeast(0.dp)
        val availableHeight = maxHeight.coerceAtLeast(1.dp)
        val previewWidth = minOf(availableWidth, availableHeight * aspect)
        val previewHeight = previewWidth / aspect
        val verticalMargin = ((maxHeight - previewHeight) / 2).coerceAtLeast(0.dp)
        val topOverlay = verticalMargin < 28.dp
        val bottomOverlay = verticalMargin < 56.dp
        Box(Modifier.fillMaxSize().padding(end = 88.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(previewWidth).height(previewHeight).background(Color.Black), contentAlignment = Alignment.Center) {
                if (granted) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                PreviewGuides(Modifier.fillMaxSize())
            }
        }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(28.dp)
                .background(if (topOverlay) Color.Black.copy(alpha = 0.65f) else Color.Transparent).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("대기 · ${if (front) "전면" else "후면"}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                BatteryIndicator(batteryPercent, batteryCharging)
            }
            AudioMeter(levels = micLevels, granted = micGranted, error = micError,
                onRequest = { if (!micGranted) micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 5.dp)
                    .background(if (bottomOverlay) Color.Black.copy(alpha = 0.65f) else Color.Transparent))
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 70.dp else 4.dp)
                    .background(if (bottomOverlay) Color.Black.copy(alpha = 0.65f) else Color.Transparent),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (status.startsWith("E_")) {
                    Text(status, color = Color.White, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp))
                }
                if (status.startsWith("E_CAMERA")) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(status)) }) { Text("오류 복사", color = Color.White) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (front && granted && camera != null) {
                    val wide = zoomStops.minOrNull()
                    val normal = zoomStops.maxOrNull()
                    if (wide != null && normal != null) {
                        val hasTwo = normal - wide >= 0.05f
                        val isWide = hasTwo && abs(selectedZoom - wide) < abs(selectedZoom - normal)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FrontLensButton(wide = true, selected = isWide, enabled = hasTwo) { applyZoom(wide) }
                            FrontLensButton(wide = false, selected = !isWide, enabled = true) { applyZoom(normal) }
                        }
                    }
                }
                if (!front) {
                    Row(modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                IconButton(
                    onClick = {
                        (context as? ComponentActivity)?.requestedOrientation =
                            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    },
                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = "화면 방향 전환" }
                ) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color.White)
                }
                }
                if (!granted) Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("권한 허용") }
            }
        }
        RightControlPanel(
            modifier = Modifier.align(Alignment.CenterEnd),
            enabled = granted,
            onSwitchCamera = { front = !front }
        )
    }
}

@Composable
private fun RightControlPanel(modifier: Modifier, enabled: Boolean, onSwitchCamera: () -> Unit) {
    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(88.dp)
        .background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        val size = ((maxHeight - 114.dp) / 5).coerceIn(36.dp, 56.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            ControlButton(Icons.Default.Refresh, "카메라", enabled, size, onSwitchCamera)
            ControlButton(Icons.Default.Mic, "마이크", false, size) { }
            ControlButton(Icons.Default.PlayArrow, "LIVE", false, size + 8.dp) { }
            ControlButton(Icons.Default.RadioButtonChecked, "녹화", false, size) { }
            ControlButton(Icons.Default.Layers, "오버레이", false, size) { }
        }
    }
}

@Composable
private fun ControlButton(symbol: ImageVector, label: String, enabled: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled,
            modifier = Modifier.size(size).background(
                if (label == "LIVE") Color(0xFFD32F2F) else if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape
            ).semantics { contentDescription = if (enabled) label else "$label (준비 중)" }) {
            Icon(symbol, contentDescription = null, modifier = Modifier.size(size * 0.52f),
                tint = if (enabled || label == "LIVE") Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.55f), maxLines = 1)
    }
}

@Composable
private fun AudioMeter(levels: List<Float>, granted: Boolean, error: Boolean, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(165.dp).clickable(enabled = !granted, onClick = onRequest), verticalArrangement = Arrangement.Center) {
        if (!granted || error) {
            Text(if (error) "마이크 사용 불가" else "마이크 허용", color = Color.White, style = MaterialTheme.typography.labelSmall)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = "마이크 레벨", tint = Color.White, modifier = Modifier.size(16.dp))
                Column(Modifier.padding(start = 3.dp)) {
                    val values = if (levels.isEmpty()) listOf(-60f) else levels
                    values.take(2).forEachIndexed { i, db ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (values.size == 1) "M" else if (i == 0) "L" else "R", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            Canvas(Modifier.padding(start = 2.dp).width(126.dp).height(6.dp)) {
                                val segments = 20
                                val step = size.width / segments
                                for (index in 0 until segments) {
                                    val threshold = -60f + 60f * (index + 1) / segments
                                    val color = when { index >= 19 -> Color(0xFFD32F2F); threshold > -12f -> Color(0xFFFBC02D); else -> Color(0xFF388E3C) }
                                    drawRect(if (db >= threshold) color else Color.DarkGray, topLeft = Offset(index * step, 0f), size = Size((step - 1.dp.toPx()).coerceAtLeast(0f), size.height))
                                }
                            }
                        }
                    }
                }
            }
            Text("      -24                  -12             0 dBFS", color = Color.LightGray, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun BatteryIndicator(percent: Int?, charging: Boolean) {
    val ink = when {
        percent == null -> Color.White
        percent <= 10 -> Color(0xFFD32F2F)
        percent <= 20 -> Color(0xFFFBC02D)
        else -> Color.White
    }
    val accessibility = if (percent == null) "배터리 잔량 확인 불가" else
        "배터리 $percent%" + if (charging) ", 충전 중" else ""
    Box(Modifier.width(56.dp).height(24.dp).semantics { contentDescription = accessibility }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val bodyWidth = size.width - 4.dp.toPx()
            drawRoundRect(Color.White, size = Size(bodyWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(stroke))
            drawRect(Color.White, topLeft = Offset(bodyWidth, size.height * 0.32f),
                size = Size(3.dp.toPx(), size.height * 0.36f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(end = 4.dp)) {
            if (charging) Icon(Icons.Default.FlashOn, contentDescription = null,
                modifier = Modifier.size(13.dp), tint = Color(0xFF388E3C))
            Text(percent?.toString() ?: "—", color = ink, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun PreviewGuides(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ink = Color.White.copy(alpha = 0.55f)
        val thin = 1.dp.toPx()
        for (step in 1..2) {
            val x = size.width * step / 3f
            val y = size.height * step / 3f
            drawLine(ink, Offset(x, 0f), Offset(x, size.height), thin)
            drawLine(ink, Offset(0f, y), Offset(size.width, y), thin)
        }
        val cx = size.width / 2f
        val cy = size.height / 2f
        val arm = 8.dp.toPx()
        drawLine(ink, Offset(cx - arm, cy), Offset(cx + arm, cy), thin)
        drawLine(ink, Offset(cx, cy - arm), Offset(cx, cy + arm), thin)
        drawRect(ink, topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
            size = Size(size.width * 0.9f, size.height * 0.9f), style = Stroke(thin))
    }
}

@Composable
private fun DashboardTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF1976D2), onPrimary = Color.White,
        primaryContainer = Color(0xFF153A60), onPrimaryContainer = Color(0xFFCFE8FF),
        background = Color(0xFF25252C), onBackground = Color(0xFFFBFBFB),
        surface = Color(0xFF181A1E), onSurface = Color(0xFFFBFBFB), error = Color(0xFFFF7777)
    ) else lightColorScheme(
        primary = Color(0xFF1976D2), onPrimary = Color.White,
        primaryContainer = Color(0xFFCFE8FF), onPrimaryContainer = Color(0xFF363949),
        background = Color(0xFFEEEEEE), onBackground = Color(0xFF363949),
        surface = Color(0xFFF6F6F9), onSurface = Color(0xFF363949),
        error = Color(0xFFD32F2F), onError = Color.White, errorContainer = Color(0xFFFECDD3)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun FrontLensButton(wide: Boolean, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ink = if (selected) Color.Black else Color.White
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).semantics {
        contentDescription = (if (wide) "넓게" else "일반") + if (selected) " 선택됨" else if (!enabled) " 미지원" else ""
    }, shape = CircleShape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color.White else Color.Black.copy(alpha = 0.4f),
            disabledContainerColor = Color.DarkGray.copy(alpha = 0.3f))) {
        Canvas(Modifier.size(24.dp)) {
            val u = size.width / 24f
            // A frame with outward corners for wide; a smaller frame for normal.
            val lo = if (wide) 3f else 7f
            val hi = if (wide) 21f else 17f
            val segment = 4f
            val stroke = 2f * u
            for (x in listOf(lo, hi)) for (y in listOf(lo, hi)) {
                val dx = if (x == lo) segment else -segment
                val dy = if (y == lo) segment else -segment
                drawLine(ink, Offset(x * u, (y + dy) * u), Offset(x * u, y * u), strokeWidth = stroke)
                drawLine(ink, Offset(x * u, y * u), Offset((x + dx) * u, y * u), strokeWidth = stroke)
            }
        }
    }
}
