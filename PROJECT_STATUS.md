# 곽스트림 v0.1.10

상태: **카메라 원본 방향·비율 보존 소스 수정 완료 / 기기 동작 검증 전**

- Android Camera2 미리보기의 임의 16:9 선택 및 화면 강제 9:16/16:9 레이아웃 제거.
- 공식 Camera2 센서-디스플레이 상대 회전식 적용; 화면 회전 180° 변경 감지.
- Camera2 센서 원본 비율과 실제 지원 해상도 사용; 균등 배율 FIT_CENTER.
- iPhone 실제 카메라는 AVFoundation `.resizeAspect` 사용. 시뮬레이터 가상 패턴 유지.
- Android versionName 0.1.10 / versionCode 11, iOS build 11, 앱 ID live.kwak.stream.
- 아이콘, 카메라/줌, 권한, 오류, 세션 복귀 코드 유지.
- MP4 녹화 및 송출/Cloudflare는 미구현 또는 보류.
- Android/iOS 빌드와 실제 미리보기 확인은 미실행 (빌드 환경 및 기기 필요).
- 기존 GitHub 태그·Drive 버전 삭제 금지, 새 ZIP 별도 버전 폴더에 저장.
- 기하 공식 테스트 32/32 방향 조합, FIT_CENTER·4:3 소스, 소스 정적 검사 28/28 통과. 전체 앱 빌드/실기기는 미실행.
