package kr.kwakstream.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
   super.onCreate(savedInstanceState)
   val controller = WindowCompat.getInsetsController(window, window.decorView)
   controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
   controller.hide(WindowInsetsCompat.Type.navigationBars())
   setContent { CameraScreen() }
 }
}

@Composable fun CameraScreen() {
 val context = LocalContext.current
 val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
 var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
 var front by remember { mutableStateOf(false) }
 var status by remember { mutableStateOf("카메라 준비 중") }
 val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (!it) status = "카메라 권한이 필요합니다" }
 LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
 val previewView = remember(context) { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE; setBackgroundColor(android.graphics.Color.BLACK) } }
 DisposableEffect(granted, front, owner) {
   var disposed = false
   var provider: ProcessCameraProvider? = null
   if (granted) {
     val future = ProcessCameraProvider.getInstance(context)
     future.addListener({
       if (!disposed) try {
         val cameraProvider = future.get(); provider = cameraProvider
         val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
         if (!cameraProvider.hasCamera(selector)) { status = "선택한 카메라를 사용할 수 없습니다" }
         else {
           cameraProvider.unbindAll()
           val selector16x9 = ResolutionSelector.Builder()
             .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()
           val preview = Preview.Builder().setResolutionSelector(selector16x9).build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
           cameraProvider.bindToLifecycle(owner, selector, preview)
           status = "카메라 미리보기"
         }
       } catch (e: Exception) { status = "카메라 오류: ${e.localizedMessage ?: "다시 시도하세요"}" }
     }, ContextCompat.getMainExecutor(context))
   }
   onDispose { disposed = true; provider?.unbindAll() }
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
       horizontalAlignment = Alignment.CenterHorizontally
     ) {
       Text(status, color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 8.dp, vertical = 4.dp))
       Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
         if (!granted) Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("권한 허용") }
         Button(onClick = { front = !front }, enabled = granted) { Text("전후면 전환") }
       }
     }
   }
 }
}
