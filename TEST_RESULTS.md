# 곽스트림 v0.1.9 테스트 결과

## 통과 — 실제 실행
- `python tests/verify_sources.py`: 23/23 정적 코드 및 소스 계약 검사 통과.
- `swiftc -frontend -parse` iOS Swift 소스 구문 검사 통과 (Xcode 빌드는 아님).
- Kotlin 독립 테스트: Android Camera2 줌 범위 변환 함수의 소스 본문을 사용해 JVM 단독 컴파일 및 6/6 사례 통과 (API 30/29, 최소/최대, 1×, 부정확한 범위, 값 중복).
- iOS 카메라 Swift 코드와 Android 앱 아이콘 SHA256 비교: v0.1.8과 동일.

## 확인 불가/미실행
- Android Gradle/APK: 미실행 (현재 컨테이너에 Android SDK 미설치).
- iOS Xcode 시뮬레이터 빌드/실행: 확인 불가 (macOS/Xcode 필요).
- 실기기 카메라/줌/회전/오류복구: 미실행.
- GitHub 업로드·CI: 미실행 (사용자 Termux 배포 시 별도 확인).

## 기능 미구현 (이번 작업 제외)
- MP4 영상·음성 녹화는 미구현. Camera2와 기존 iOS 시뮬레이터 코드 보존만 확인.

완료된 것은 보정값 제거 소스 수정과 위 검사이며, 실제 카메라 정상 작동까지 검증된 상태는 아닙니다.
