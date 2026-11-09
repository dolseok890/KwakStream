# 곽스트림 v0.1.14 — 컴파일 오류 수정

## 수정 내역
- Android `MainActivity.kt`에 Compose `horizontalScroll` / `rememberScrollState` import 두 개 추가.
- 과거 Termux 빌드 오류: `MainActivity.kt:150:34` 및 `150:51`의 `Unresolved reference` 해결 목적.
- 빌드 오류 수정 외 Camera2 및 iPhone 동작/설정/앱 ID/아이콘 변경 없음.
- Android `versionName=0.1.14`, `versionCode=15`; iOS `MARKETING_VERSION=0.1.14`, `CURRENT_PROJECT_VERSION=15`.

## 검증 현황
- 통과: `tests/verify_sources.py` 38/38 (새 import 검사 포함).
- 통과: 로컬 ZIP CRC·버전·필수 파일 정적 검증.
- 통과: `tests/verify_preview_size_compile.py` Kotlin 컴파일 및 5개 테스트.
- 통과: `PreviewGeometryTest.kt` 회전 32조합, TextureView 중복 회전 방지 및 FIT_CENTER 테스트.
- 미실행: Android `:app:assembleDebug` 전체 APK 빌드, iOS Xcode 및 시뮬레이터, 실제 단말 카메라 동작 검증.
- 미해결: 실제 단말 미리보기 검은 화면 여부, MP4 녹화 미구현 (이번 수정 범위 아님).

## 배포
- 변경 소스 ZIP은 `KwakStream.zip`으로 제작하며 Google Drive는 `프로젝트/KwakStream/v0.1.14/`에 신규 저장한다.
- GitHub 새 커밋/태그는 아직 만들지 않으며 기존 태그/버전 파일을 보존한다.


---

# KwakStream v0.1.13 — 카메라 선택 UI 정리

## 작업 완료 사항 (소스 단계)
- Android 사용자 화면의 CameraManager ID 버튼 행 제거. 전면/후면 전환 버튼은 유지.
- CameraManager.cameraIdList 기반 실제 카메라 조회와 내부 선택, 오류 진단 로그 보존.
- 지원 줌 범위의 최소/1배/최대 값은 별도의 단일 줌 행으로 유지. 물리 렌즈 전환점 아님.
- CAM_FRAME은 프레임 수신이라는 뜻만 표현; 화면의 실제 영상 내용 검증으로 간주하지 않음.
- 회전/FIT_CENTER, 아이콘, 패키지명, iOS AVFoundation·시뮬레이터 기능 변경 없음.
- Android versionCode=14/versionName=0.1.13, iOS build=14/marketing=0.1.13.

## 테스트 상태
- 통과: Python 정적 검사 36/36, Kotlin PreviewGeometry 회전 32조합 및 FIT_CENTER 산식 검사, Kotlin 미리보기 크기 함수 컴파일/5케이스, Swift 구문 파싱, ZIP 검증(별도).
- 미실행: 전체 Android Gradle APK 빌드, iOS Xcode 빌드, 실기기 카메라/검은 화면/회전/줌 동작.

## 남은 문제
- 사용자가 첨부한 이미지의 검은 미리보기는 아직 원인 미확정. TextureView 프레임 콜백 수신만으로 영상 출력 정상 여부를 보장할 수 없음.
- MP4 녹화는 미구현; Cloudflare/RTMP는 보류.
- 기존 v0.1.12 및 GitHub 기존 태그는 변경하지 않음. GitHub 신규 커밋/태그는 미등록.
