# 곽스트림 — 새 카메라 미리보기 v0.1.6

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

## Termux에서 버전별 업로드
기존 GitHub 저장소를 Termux 내부에 clone하고 사용자 인증을 설정한 뒤, 다운로드한 ZIP 안의 `scripts/publish-version.sh`를 꺼내 실행하세요. 예시:

```sh
unzip -p /sdcard/Download/KwakStream.zip scripts/publish-version.sh > ~/kwak-publish.sh
cd ~/github/KwakStream
bash ~/kwak-publish.sh /sdcard/Download/KwakStream.zip v0.1.6
```

명령은 작업 트리가 깨끗하고 origin 인증이 되어 있어야 합니다. 버전 폴더와 태그가 이미 있으면 중단합니다. 버전 폴더 및 Actions 파일을 커밋하고 현재 브랜치와 태그를 순서대로 push합니다. 태그 push가 실패하면 폴더 커밋은 남을 수 있으므로 오류를 확인하세요. 태그가 올라간 후 GitHub Actions → iOS Simulator → 해당 실행 → ios-simulator-results에서 결과를 내려받습니다. 보관 기간은 30일입니다. workflow_dispatch에서는 version 입력에 v0.1.6을 지정합니다. GitHub 호스팅 macOS 환경·권한·사용량에 따라 실행이 제한될 수 있습니다.

Android는 내비게이션 바를 숨기고 가장자리 스와이프로 일시 표시합니다. iOS는 홈 인디케이터 숨김을 시스템에 요청하며 실제 동작은 OS가 결정합니다. 실기기 검증이 필요합니다.

앱 ID: live.kwak.stream. 이전 kr.kwakstream.app 설치본과는 별도 앱입니다. iOS 실제 기기 설치는 서명과 별도 빌드가 필요합니다.

## v0.1.6 카메라 오류 및 API 줌
- iOS: 세로 9:16/가로 16:9를 실제 사용 가능한 크기 안에 명시적 폭·높이로 맞추고, 측정용 접근성 요소를 분리합니다. 오류는 `E_PERMISSION`, `E_INPUT`, `E_SWITCH`, `E_START`, `E_RUNTIME`, `E_INTERRUPTED`, `E_ZOOM` 형태로 화면·로그에 기록합니다.
- iOS 실기기: `AVCaptureDevice.virtualDeviceSwitchOverVideoZoomFactors`에서 보고한 줌 전환점과 1×만 표시합니다. 기기별 배율은 다르며 고정된 0.6/2/3×를 강제로 만들지 않습니다. 전면에 전환점이 없다면 1×만 표시됩니다.
- Android: Camera2가 공개하는 물리 카메라 렌즈 초점거리·센서 크기로 환산 가능한 광학 배율 후보만 표시합니다. 물리 렌즈 정보가 없으면 1×만 표시합니다. CameraX의 실제 광학 렌즈 선택 여부는 기기 테스트가 필요합니다.
- 모듈은 기존 `app`과 `ios`로 유지하며, 0.1.5 아이콘 및 앱 ID `live.kwak.stream`을 그대로 유지합니다. 녹화/송출 기능은 추가하지 않았습니다.
- 실제 Android 빌드는 기존 Termux Studio의 `kbuild KwakStream.zip`을 사용합니다. 현재 제작 환경은 iOS/macOS 및 Android SDK가 없어 정적 점검 외에는 미실행입니다.
