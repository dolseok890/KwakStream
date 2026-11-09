# 곽스트림 v0.1.8 검증 결과

## 정적 검사
- 정적 검증은 실행한 항목과 결과만 기록. 컴파일 성공/실기기 작동과 구분한다.

## 미실행/환경 제약
- Android 앱 컴파일 및 APK 생성: 미실행 (현재 컨테이너에 Android SDK/Gradle 캐시가 없음).
- iOS Xcode/macOS 기기·시뮬레이터 실행: 미실행 (현재 환경은 Linux).
- 카메라 단말 전환/실제 줌/미리보기 프레임 복구/발열 검증: 미실행 (실제 Android/iOS 카메라 없음).
- GitHub 배포 및 CI 실행: 미실행 (사용자 Termux 배포 후 가능).

결론: 코드 수정 완료. 빌드 성공/동작 검증 완료로 간주하지 않음.

## 이번 실제 실행 검증 (2026-11-09)
- 통과: `python tests/verify_sources.py` — 18/18 정적 검사 성공.
- 통과: Linux `swiftc -frontend -parse` — Swift 구문 검증 (iOS SDK 빌드 아님).
- 통과: Android 카메라 소스 및 Gradle의 CameraX 참조 제거 검사.
- 통과: `v0.1.7` 원본 대비 iOS 카메라/시뮬레이터 Swift 소스, Android 및 iOS 아이콘, GitHub 배포 스크립트 SHA256 동일 확인.
- 확인 불가: Android Kotlin/Gradle 컴파일 — Android SDK 및 Android Gradle 라이브러리 미설치. 외부 모듈 누락 상태의 kotlinc 진단은 빌드 결과로 간주하지 않음.
- 확인 불가: Xcode iOS 빌드·시뮬레이터 UI 테스트 — macOS/Xcode 없음.
- 미실행: 전후면 전환, 줌 렌즈, 실제 Surface 화면 회전, 미리보기 중단 복구 실기기 테스트.
- 통과: 배포 스크립트가 새 `android-build.yml`을 복사/추적하고 ZIP 필수 파일로 검증하도록 수정.
