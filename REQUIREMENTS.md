# KwakStream v0.1.11 — 카메라 원본 방향·비율 유지

확정: 카메라가 세로 방향 영상을 생성하면 세로 미리보기가 보여야 하며, 강제로 가로 비율에 맞추거나 화면을 채우기 위해 자르지 않는다.

- Android Camera2 / iOS AVFoundation 엔진 유지.
- Android: SENSOR_ORIENTATION, LENS_FACING, 현재 Display 회전으로 상대 회전 공식 계산(임의 고정 회전값 금지).
- Android: Camera2에서 허용하는 SurfaceTexture 출력 크기만 사용하며 가능하면 센서 원본 종횡비에 가까운 것을 선택한다. 16:9 강제 선택 제거.
- Android: TextureView를 화면 전체 뷰포트로 사용하되 **카메라 프레임 전체**를 균일 배율로 FIT_CENTER 표시. 화면이 남으면 검은 여백 유지.
- Android 12+: SCALER_ROTATE_AND_CROP_NONE 지원 시 HAL의 자동 회전·크롭 비활성화.
- iOS: 실기기 AVCaptureVideoPreviewLayer.resizeAspect가 비율을 관리하며 강제 9:16 프레임을 제거. 시뮬레이터 기존 비율 검사와 가상 카메라 유지.
- 전후면 전환, 실제 API ID/줌 범위, 오류 코드, 재시도, 앱 복귀, 상태바/내비게이션바 동작을 유지.
- Cloudflare/RTMP/녹화(MP4)는 추가하지 않음. 특히 MP4는 아직 미구현.
- GitHub main 루트 최신 소스 + 변경 커밋/불변 태그. 기존 태그 및 Drive 버전 보호.

## v0.1.11 수정 범위
- 변경 목적은 `Camera2Controller.kt` 500행 Kotlin 컴파일 오류만 수정하는 것.
- `android.util.Size`의 `width`/`height` 속성 참조와 `android.graphics.Rect`의 `width()`/`height()` 메서드 호출을 혼동하지 않는다.
- 카메라 원본 방향·FIT_CENTER·전/후면 전환·줌 및 iOS 시뮬레이터 기능을 변경하지 않는다.
- Android APK와 iOS 시뮬레이터에서 실제 빌드 성공 여부를 별도로 확인한다.
