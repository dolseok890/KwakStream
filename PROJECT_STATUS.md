# 곽스트림 v0.1.17

- 기준: 사용자가 언급한 v0.1.16 Camera2 포팅. Android versionCode 18 / versionName 0.1.17; iOS build 18 / version 0.1.17.
- 확인: Android 의존성과 소스에 CameraX 모듈 없음. 기존 Camera2 유지.
- 수정: TextureView의 지원되지 않는 setBackgroundColor 호출을 없애고 Compose 부모 검정 배경 유지. 앱 크래시의 유력 원인이지만 사용자 기기 스택 트레이스 없어 확정 불가.
- iOS 앱 Swift 및 시뮬레이터 테스트/워크플로 코드는 변경하지 않았으며 iOS project.yml의 버전만 동기화. v0.1.5로 무조건 되돌리지 않음: 사용자 요청의 되돌릴 범위 불명확 및 성공한 시뮬레이터 설정 보호.
- 자동 GitHub 업로드 스크립트는 v0.1.16에서 계승했으며 기존 버전 폴더를 정리하는 기능이 있어 사용자 정책과 충돌할 수 있음. 실행하지 말 것.
- Android 실제 빌드, 기기 실행 및 iOS 시뮬레이터 재실행 미검증.
