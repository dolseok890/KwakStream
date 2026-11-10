# 곽스트림 v0.1.7 — GitHub 릴리스 구조 변경

- 기준: v0.1.6의 원본 Android/iOS 소스. 기능 변경 없이 GitHub 릴리스 스크립트와 CI 경로를 수정함.
- Android `versionCode 8` / `versionName 0.1.7`, iOS `MARKETING_VERSION 0.1.7` / build `8`; 앱 ID `live.kwak.stream` 보존.
- GitHub 루트 소스 + 커밋 + 주석 태그(`v0.1.7`), 신규 버전 폴더 생성 제거.
- 기존 v0.1.3~v0.1.6 원본은 역사적 Git 태그에서 확인한 후 루트 버전 폴더만 정리.
- 기존 iOS 카메라/시뮬 진단 및 Android 엔진/아이콘 코드를 변경하지 않음.
- 실제 Android/iOS 빌드 및 기기 동작은 별도 검증 필요.
- GitHub 원격 push는 ZIP 배포 스크립트 실행 전에는 이루어지지 않음.
- 검증: 임시 Git bare 저장소에서 v0.1.3~v0.1.6 태그 보존, 새 v0.1.7 태그·커밋 push, 중복 태그 차단, 잘못된 ZIP 버전 차단 확인.
