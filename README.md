# KwakStream v0.1.13 — 카메라 ID/줌 UI 단순화

- 실제 카메라 ID는 전·후면 전환과 진단을 위한 값이며, 줌 배율과 혼동하지 않도록 기본 화면에는 표시하지 않습니다.
- 화면에는 `카메라 전환`과 Camera2에서 보고한 줌 범위 값만 표시합니다. `8×`는 최대 지원 줌값이며 반드시 별도의 물리 렌즈가 존재한다는 뜻은 아닙니다.
- 카메라 화면이 검어도 프레임 콜백이 올 수 있어 수신 상태를 `CAM_FRAME`이라고 표현합니다. 영상 내용 정상 여부는 추가 검증이 필요합니다.
- 기존 세로 미리보기 회전 이슈는 이번 UI 수정으로 해결됐다고 보장하지 않으며, 실제 기기에서 확인해야 합니다.
- Android Camera2 / iOS AVFoundation / iOS 시뮬레이터 / 기존 아이콘 / 기존 패키지명 유지.
- 빌드: `kbuild KwakStream.zip`.
- Google Drive 버전별 ZIP 보존 + GitHub 루트 커밋/태그 배포 방식 유지.

---

# KwakStream v0.1.12 — Android 카메라 컴파일 오류 수정

- v0.1.10에서 `Camera2Controller.kt:500`의 `Size.height()`를 호출하여 Kotlin 컴파일이 실패하던 문제를 고쳤습니다.
- 카메라 영상의 원본 회전/FIT_CENTER, Camera2·AVFoundation, 렌즈/줌, iOS 시뮬레이터를 변경하지 않았습니다.
- 테스트: `python3 tests/verify_sources.py`, `python3 tests/verify_preview_size_compile.py`.
- 다운로드한 ZIP은 기존과 같은 `KwakStream.zip` 이름을 유지합니다.
- Android 기기 빌드: `kbuild KwakStream.zip`.
- GitHub 게시: 기존 `scripts/publish-version.sh`로 단일 소스 루트 커밋 및 불변 `v0.1.12` 태그.
- 실제 APK 빌드, 설치 및 iOS 시뮬레이터 실행은 별도 검증해야 합니다.

---

# KwakStream v0.1.9 — 카메라 줌 보정 로직 제거

- Android Camera2: `CameraManager.cameraIdList`가 보고한 전·후면 카메라 ID만 표시합니다.
- 줌 버튼: `CONTROL_ZOOM_RATIO_RANGE`(Android 11+) 또는 `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM`으로 확인한 최솟값/기본 1×/최댓값만 표시합니다. 임의의 광각·망원 렌즈 배율 계산은 사용하지 않습니다.
- **줌 배율은 물리 렌즈 ID가 아닙니다.** 실제 물리 렌즈 전환 여부는 제조사 카메라 구현이 결정하며 별도로 확인해야 합니다.
- 방향: Camera2 센서 방향 메타데이터 + 디스플레이 회전값. `90°` 고정 대체값을 제거했습니다.
- Android FIT_CENTER, 전·후면 전환, 오류 상태 및 재시도, iOS AVFoundation과 시뮬레이터 모드는 보존했습니다.
- MP4 녹화는 아직 구현되지 않았습니다. 다음 별도 개발 단계입니다.
- Cloudflare API와 RTMP/SRT 등 송출 기능은 이번 버전에서 변경하지 않습니다.

# 곽스트림 KwakStream v0.1.8

Android Camera2 + iPhone AVFoundation 기반 카메라 미리보기 안정화 단계.

## 현재 구현
- Android: CameraManager/CameraDevice/CameraCaptureSession 직접 사용, TextureView 프레임 감지, 안전한 세션 해제와 자동/수동 재시도.
- 실제 `CameraManager.cameraIdList`로 열 수 있는 카메라 ID만 표시. API 메타데이터가 제공하는 줌 범위로 줌 버튼 생성.
- 최소 줌·1배·최대 줌은 카메라의 줌 API에서 확인된 값. 추가 물리 카메라 메타데이터에서 유도한 배율은 특정 렌즈 스위치를 보장하지 않음.
- iOS: 기존 AVFoundation의 전후면 전환·줌·세션 유지, 시뮬레이터 전용 테스트 패턴 유지.
- 아직 RTMP, 녹화, 원격 제어가 없는 미리보기 중심 소스.

## 빌드/배포
1. ZIP을 `/sdcard/Download/KwakStream.zip`으로 저장.
2. 기존 Termux Studio `kbuild KwakStream.zip`으로 APK 빌드.
3. GitHub 업로드는 기존 `proot-distro` 명령으로 `scripts/publish-version.sh`를 실행. GitHub 루트 최신 소스 + 커밋/태그를 사용하며, 이전 태그는 변경하지 않음.
4. 태그를 푸시하면 Android 빌드 및 iOS 시뮬레이터 테스트 워크플로를 자동 실행하도록 설정.
5. 설치된 Android 또는 실제 iPhone에서 카메라 전환·프리징·회전·줌을 직접 테스트해야 기능 검증이 완료됨.

## 문제 추적
- 화면 하단 상태 코드 `E_*`가 표시되면 로그캣에서 `KwakStreamCamera2` 검색.
- 프레임이 8초 멈추면 최대 2회 세션을 재시작하며, 계속 실패하면 `E_STALL`을 표시.
- 권한 오류/카메라 장치 오류가 지속되면 카메라 재시도를 누르거나 앱 재실행.

## v0.1.10 수정: 센서 원본 방향·비율 그대로 미리보기
- Android는 더 이상 화면을 강제로 9:16/16:9로 제한하거나 출력 해상도를 무조건 16:9로 선택하지 않습니다.
- Camera2의 `SENSOR_ORIENTATION`, 전후면, 현재 디스플레이 회전값으로 공식 상대 회전값만 적용합니다. 보정 각도를 임의로 추가하지 않습니다.
- TextureView의 실제 카메라 출력 비율로 FIT_CENTER 축척을 계산합니다. 검은 여백은 정상이며 인위적인 크롭이나 늘림은 없습니다.
- API 31 이상 지원되는 장치에서는 HAL `SCALER_ROTATE_AND_CROP_NONE`을 설정해 중복 크롭을 방지합니다.
- iPhone 실기기는 원래 AVFoundation `.resizeAspect`가 실제 화면 안에 원본 비율을 맞춥니다. 시뮬레이터는 기존 가상 패턴/화면비 테스트를 유지합니다.
- 스마트폰과 폴더블의 센서·회전 조합은 실제 기기에서 반드시 검증해야 합니다. `PREVIEW sensor=... display=... relative=... buffer=... oriented=...` 로그로 원인 확인 가능합니다.
- MP4 녹화는 여전히 미구현입니다. Cloudflare/RTMP 기능은 추가하지 않았습니다.


## v0.1.12 미리보기 회전 수정
- Android `TextureView`는 카메라 센서 방향을 이미 처리합니다. 기존 SENSOR_ORIENTATION 재회전은 제거하고 디스플레이 회전 역보정만 합니다.
- 화면 하단에 `방향: 센서 x° · 화면 y° · 미리보기 보정 z°`가 표시됩니다. 세로 화면에서 0°가 기대되는 정상 케이스를 판별하는 데 사용합니다.
- 자세한 결과는 Android `KwakStreamCamera2` 태그의 `PREVIEW_APPLIED` 로그에 기록됩니다.
- 빌드: `kbuild KwakStream.zip` (기존 Termux Studio 방식). 전체 빌드/실기기 테스트는 별도로 필요합니다.
