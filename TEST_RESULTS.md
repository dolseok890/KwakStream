# KwakStream v0.1.14 테스트 결과

| 테스트 항목 | 상태 | 내용 |
|---|---|---|
| 소스 정적 검사 | 통과 | 38/38 (`horizontalScroll` 및 `rememberScrollState` import 검사 신규 추가) |
| Kotlin 미리보기 크기 함수 컴파일 | 통과 | 5/5 테스트 |
| PreviewGeometry 회전·FIT_CENTER 계산 | 통과 | 32개 회전 조합 및 크기/영역 검사 |
| Swift 구문 파싱 | 통과 | Swift 파서만 확인, iOS 빌드 아님 |
| Android 전체 Gradle 빌드 | 미실행 | Android SDK/Gradle 빌드 환경 없음 |
| Android 실기기 기능 테스트 | 미실행 | 사용자 단말 확인 필요 |
| iOS Xcode/시뮬레이터 | 미실행 | macOS/Xcode 환경 없음 |
| ZIP 압축 무결성 | 통과 | 시험 ZIP에서 CRC/파일/버전 검사 통과, 최종 재검사 예정 |
| Google Drive 업로드 | 미실행 | ZIP 제작 후 검증 예정 |

*현재 실제 Android 빌드 성공이나 카메라 오류 해결을 의미하지 않습니다.*


---

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
