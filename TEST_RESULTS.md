# 곽스트림 v0.1.12 TEST_RESULTS

- **실패 확인:** 사용자의 v0.1.11 실기기 스크린샷에서 세로 UI에 전면 영상이 옆으로 누움.
- **원인 분석:** Camera2 `TextureView`의 시스템 자동 센서 회전을 무시하고 앱에서 센서 상대 회전을 중복 적용한 로직 확인. Android 공식 Camera2 가이드 참고.
- **통과:** Kotlin 순수 로직 회전 테스트; front/back + sensor + display 총 32조합. 전면 sensor=270°, 화면 0°에서 추가 미리보기 회전=0° 확인.
- **통과:** FIT_CENTER 사각형 bounds/균일 배율 및 미리보기 크기/방향 산식 검증.
- **통과:** 정적 소스 검사 32/32, Swift 구문 파싱, YAML/JSON/XML 구문.
- **통과:** 실 Kotlin `pickPreviewSize()` 함수 추출 컴파일, 다섯 가지 입력 검증.
- **미실행:** 전체 Android APK/Gradle 빌드 (현재 환경에 Android SDK 없음).
- **미실행:** Android 실기기 전·후면 미리보기 방향, 폴더블 회전, 줌/카메라 전환 회귀.
- **미실행:** macOS/Xcode iOS 시뮬레이터 빌드 및 iPhone 실기기.
- **해당 없음:** MP4 녹화/RTMP/Cloudflare는 이번 수정 범위 밖.
