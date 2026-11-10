# 변경 이력

## v0.1.7 — GitHub 릴리스 모델 변경
- 버전별 GitHub 폴더 대신 루트에 최신 소스를 커밋하고 변경 불가능한 Git 주석 태그로 보존.
- `scripts/publish-version.sh`: 아카이브 검사, 기존 태그 검사, 안전한 레거시 폴더 정리, 루트 소스 동기화, 원격 브랜치+태그 push.
- `ios-simulator.yml`: 태그가 가리키는 루트 iOS 프로젝트만 사용하고 태그/버전 일치 검사.
- 기존 Android/iOS 코드와 B안 아이콘은 보존. Android versionCode 8 / iOS build 8, versionName 0.1.7.
- Google Drive의 앱별 버전 폴더 정책은 유지.

## v0.1.6 — 카메라 오류 및 API 줌
- 이전 버전에서 도입한 iPhone 시뮬레이터 가상 카메라 전환 및 테스트 프레임 유지.
- iOS 오류 코드, 실기기 AVFoundation 줌 후보, Android Camera2 줌 후보 유지.
