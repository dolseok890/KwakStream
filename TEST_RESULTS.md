# KwakStream v0.1.13 Test Results

| 항목 | 결과 | 비고 |
|---|---|---|
| 기존 카메라 ID 버튼 제거 확인 | 통과 | UI Kotlin 정적 검사 |
| CameraManager cameraIdList 유지 | 통과 | 코드 정적 검사 |
| 전·후면 전환 버튼 유지 | 통과 | 코드 정적 검사 |
| API 줌 UI 단일 행 | 통과 | 코드 정적 검사 |
| 상태에 camera ID 미표시 / Logcat 진단 유지 | 통과 | 코드 정적 검사 |
| 전체 소스 정적 검사 | 통과 | 36/36 |
| 회전 및 FIT_CENTER Kotlin 함수 테스트 | 통과 | 32 sensor/display 조합 및 기타 수치 테스트 |
| pickPreviewSize Kotlin 함수 테스트 | 통과 | 컴파일 후 5개 케이스 |
| Swift 소스 구문 파싱 | 통과 | 구문 검사이며 iOS 빌드는 아님 |
| Android Gradle assembleDebug | 미실행 | 빌드 도구/Android SDK 환경 없음 |
| iOS Xcode 빌드 및 시뮬레이터 | 미실행 | macOS/Xcode 없음 |
| 전면·후면 실기기, 검은 화면, 줌 동작 | 미실행 | 기기 검증 필요 |
| ZIP CRC/필수 파일/버전 | 미실행 | 파일 생성 후 확인 예정 |

**참고:** 이번 버전은 UI 중복 표시 수정이며, 사진의 검은 미리보기 문제가 해결되었다고 판정하지 않습니다.
