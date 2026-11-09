# 곽스트림 v0.1.11 테스트 결과

- **통과:** Kotlin 순수 미리보기 회전·FIT_CENTER 로직 컴파일 및 실행, 32개 전후면·센서·디스플레이 방향 조합.
- **통과:** 세로/가로/4:3 소스 크기 및 FIT_CENTER 비율 테스트.
- **통과:** 소스 계약·버전·Android manifest·아이콘 JSON·YAML 정적 검사 28/28.
- **통과:** Swift 소스 파서 구문 검사 (iOS 실제 컴파일 아님).
- **통과:** 기존 PNG 이미지·앱 아이콘 6개 원본과 바이트 일치.
- **미실행:** Android `:app:assembleDebug` — 현재 환경에 Android SDK/Gradle 배포본 없음.
- **미실행:** iOS Xcode 빌드·UI Test — macOS/Xcode 환경 아님.
- **미실행:** Android 실기기에서 카메라 방향·영상·API 줌·프리징·앱 복귀 반복.
- **미실행:** iPhone 실기기와 iOS 시뮬레이터 실제 UI 회귀 테스트.
- **해당 없음:** MP4 녹화(아직 미구현), Cloudflare/RTMP/SRT 기능(이번 버전 범위 제외).

**중요:** 회전 공식의 정적 정확성과 실제 화면/기기별 정상 동작은 다른 검증 단계입니다. APK 빌드 또는 실기기 통과를 주장하지 않습니다.
- **통과:** 배포 ZIP 압축 및 필수 소스/버전/아이콘 검증 (최종 ZIP 생성 시 수행).

## v0.1.11 Android 컴파일 오류 수정 검증
- **실패 확인 (v0.1.10, 사용자 실기기 Termux 로그):** `:app:compileDebugKotlin FAILED` / `Camera2Controller.kt:500:41` 및 `:500:83` — Kotlin `Int` 속성 `height`를 함수로 호출.
- **수정:** `pickPreviewSize`의 Android `Size.height()` 사용 두 곳을 `Size.height` 프로퍼티로 변경. Android `Rect.height()` 메서드는 정상 호출이므로 보존.
- **통과:** 실제 `pickPreviewSize()` Kotlin 함수 본문을 추출해 `Size`/`Rect` 형식에 맞는 스텁으로 컴파일 및 5가지 선택 테스트 실행.
- **통과:** `PreviewGeometry` 실제 Kotlin 컴파일·실행 — 전후면/센서/디스플레이 32가지 방향 조합, FIT_CENTER 및 4:3 확인.
- **통과:** `tests/verify_sources.py` 정적 검사 29/29, iOS Swift 구문 파싱 포함 (실제 Xcode 빌드 아님).
- **미실행:** v0.1.11 Android 전체 Gradle `assembleDebug` / Termux Studio 빌드 (현재 환경에 Android SDK 없음).
- **미실행:** iPhone 시뮬레이터 UI와 실제 Android 카메라 회귀 동작.
- **검증 요청:** 사용자의 기존 `kbuild KwakStream.zip`으로 v0.1.11 APK 빌드 및 실제 기기 실행 확인.
