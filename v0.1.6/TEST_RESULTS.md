# 테스트 결과 v0.1.6

- 통과: 원본 v0.1.5 ZIP 다운로드·압축 해제·소스 및 아이콘 보존 확인.
- 통과: Swift 소스 구문 파싱(리눅스 swiftc -frontend -parse). 이는 iOS 모듈 타입 검사/빌드가 아님.
- 통과: 파일 구조·버전·앱 ID·ZIP 무결성·YAML/JSON 구문·미리보기 비율 계산 정적 검사 (ZIP 생성 후 재검증).
- 미실행: iOS/macOS xcodebuild, iPhone 시뮬레이터 실행 및 UI 테스트, 실기기.
- 미실행: Android SDK/Gradle 빌드, Termux Studio, 실제 기기 카메라·줌·아이콘·설치.
- 확인 필요: iOS 화면 회전 테스트의 최종 합격 여부. 직전 v0.1.4 가로 실측은 1.69:1로 실패했으며 v0.1.6의 새 수치 기준 테스트는 미실행.
- 확인 필요: Android CameraX의 지원 물리렌즈 목록 및 광학 렌즈 전환은 제조사/OS별 차이가 있음.
