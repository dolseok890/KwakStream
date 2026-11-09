# 곽스트림 — 새 카메라 미리보기 v0.1.7

기존 프로젝트를 사용하지 않은 새 프로젝트. Android는 CameraX의 Camera2 백엔드와 PreviewView FIT_CENTER, iOS는 AVFoundation resizeAspect를 사용합니다. 녹화·오디오·송출 기능은 포함하지 않습니다.

## Android
Termux Studio의 기존 명령 `kbuild KwakStream.zip` 사용. 앱 모듈은 `app/`입니다. SDK 35, Java 17이 필요합니다. 실제 빌드는 미검증입니다.

## iOS GitHub Actions
ZIP의 **내용물**을 GitHub 저장소 루트에 올리면 `.github/workflows/ios-simulator.yml`이 push 또는 수동 실행에서 macOS 시뮬레이터 빌드와 UI 테스트를 실행합니다. Actions의 iOS Simulator 실행 기록에서 로그와 ios-test-results를 확인합니다. XcodeGen이 프로젝트를 생성합니다. 시뮬레이터 빌드에는 Apple 서명이 필요하지 않습니다. 실제 iPhone 설치에는 별도 서명 설정이 필요합니다.

iOS 시뮬레이터는 실제 카메라를 제공하지 않습니다. 시뮬레이터에서 전/후면 전환은 실제 장치 호출 없이 [전면]/[후면] 상태를 바꾸며, API 줌은 가상 1×만 표시합니다. 가로·세로 테스트는 화면에 렌더링된 패턴의 실제 프레임을 측정합니다. 이는 실제 센서 영상의 회전·렌즈 검증이 아닙니다.

가로 16:9, 세로 9:16 영역을 사용합니다. Android는 16:9 해상도를 우선하고, iOS는 1280×720 프리셋을 지원할 때 우선합니다. 불가능한 경우 다른 입력 비율을 보존하여 검은 여백이 생길 수 있습니다. 실기기 검증 전입니다.

상태 문구와 전후면 전환 버튼을 미리보기 위에 겹쳐 표시하여 사용 가능한 화면에서 미리보기를 최대화합니다. 기기 검증은 아직 수행하지 않았습니다.

## iPhone 시뮬레이터 테스트
시뮬레이터에서만 원, 격자, 중앙선과 방향·모서리가 표시됩니다. 이는 테스트 그림이며 카메라 영상이 아닙니다. 실제 카메라 경로의 회전, 미러링, 크롭은 iPhone 실기기로 확인해야 합니다.

## GitHub 배포: 루트 최신 소스 + 커밋/태그 (v0.1.7부터)

GitHub `dolseok890/KwakStream`의 `main` 브랜치 **루트**에는 최신 소스만 보관합니다. 각 릴리스는 하나의 새 커밋과 변경 불가능한 주석 태그(`v0.1.7`, `v0.1.8` …)로 보존합니다. 새 `vX.Y.Z/` 폴더를 만들지 않습니다.

과거 `v0.1.3`–`v0.1.6` 폴더는 기존 **태그에서 원본 소스가 복구되는지 검증**한 후 최신 브랜치에서만 제거합니다. 커밋 기록과 과거 태그는 손대지 않습니다. 버전 태그가 누락되었으면 안전을 위해 배포를 중단합니다.

`KwakStream.zip`을 Android 다운로드 폴더에 저장한 뒤 아래의 **기존 한 줄 명령 그대로** 실행할 수 있습니다. ZIP에 포함된 `scripts/publish-version.sh`가 새 커밋/태그 방식을 처리합니다.

```bash
proot-distro login ubuntu --bind /sdcard:/sdcard -- bash -lc 'set -e; Z=/sdcard/Download/KwakStream.zip; test -f "$Z" || { echo "다운로드 폴더에 KwakStream.zip이 없습니다."; exit 1; }; V=$(unzip -p "$Z" app/build.gradle.kts | sed -n "s/.*versionName[[:space:]]*=[[:space:]]*\"\([0-9][0-9.]*\)\".*/v\1/p" | head -n 1); [[ "$V" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "소스 버전을 확인할 수 없습니다."; exit 1; }; S=$(mktemp); trap '\''rm -f "$S"'\'' EXIT; unzip -p "$Z" scripts/publish-version.sh > "$S"; cd /root/github/KwakStream; bash "$S" "$Z" "$V"' && kbuild KwakStream.zip
```

실행 순서: ZIP 무결성·양 플랫폼 버전 검사 → 원격 Git 최신화 → 과거 태그 보존 확인 → 최신 소스 루트 반영 → **1회 commit + annotated tag** → `main` push → 태그 push → (모두 성공한 경우에만) Android `kbuild`. 원격 태그가 이미 있으면 덮어쓰지 않고 중단하므로 새 버전 번호가 필요합니다. `.github/workflows/ios-simulator.yml`은 태그가 가리키는 루트 소스를 빌드합니다. GitHub Actions가 실제 통과했는지는 실행 결과에서 따로 확인해야 합니다.

`kbuild`는 Android 빌드 전용입니다. GitHub Actions는 서명되지 않은 iOS 시뮬레이터 테스트 결과를 업로드하며, iPhone 실기기 설치용 IPA를 만들지는 않습니다.

Android는 내비게이션 바를 숨기고 가장자리 스와이프로 일시 표시합니다. iOS는 홈 인디케이터 숨김을 시스템에 요청하며 실제 동작은 OS가 결정합니다. 실기기 검증이 필요합니다.

앱 ID: live.kwak.stream. 이전 kr.kwakstream.app 설치본과는 별도 앱입니다. iOS 실제 기기 설치는 서명과 별도 빌드가 필요합니다.

## v0.1.6 카메라 오류 및 API 줌 (기능 유지)
- iOS: 세로 9:16/가로 16:9를 실제 사용 가능한 크기 안에 명시적 폭·높이로 맞추고, 측정용 접근성 요소를 분리합니다. 오류는 `E_PERMISSION`, `E_INPUT`, `E_SWITCH`, `E_START`, `E_RUNTIME`, `E_INTERRUPTED`, `E_ZOOM` 형태로 화면·로그에 기록합니다.
- iOS 실기기: `AVCaptureDevice.virtualDeviceSwitchOverVideoZoomFactors`에서 보고한 줌 전환점과 1×만 표시합니다. 기기별 배율은 다르며 고정된 0.6/2/3×를 강제로 만들지 않습니다. 전면에 전환점이 없다면 1×만 표시됩니다.
- Android: Camera2가 공개하는 물리 카메라 렌즈 초점거리·센서 크기로 환산 가능한 광학 배율 후보만 표시합니다. 물리 렌즈 정보가 없으면 1×만 표시합니다. CameraX의 실제 광학 렌즈 선택 여부는 기기 테스트가 필요합니다.
- 모듈은 기존 `app`과 `ios`로 유지하며, 0.1.5 아이콘 및 앱 ID `live.kwak.stream`을 그대로 유지합니다. 녹화/송출 기능은 추가하지 않았습니다.
- 실제 Android 빌드는 기존 Termux Studio의 `kbuild KwakStream.zip`을 사용합니다. 현재 제작 환경은 iOS/macOS 및 Android SDK가 없어 정적 점검 외에는 미실행입니다.
