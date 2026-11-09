# 곽스트림 변경 이력

## v0.1.8 (이번 수정)
- Android CameraX -> Camera2 미리보기 엔진으로 전환. CameraX 종속성 제거.
- 선택 가능한 공개 카메라 ID 및 장치 보고 초점거리/줌 한계 표시.
- Android 11+ zoomRatio / 10 이하 cropRegion 적용, API 미지원 값을 자동 숨김.
- Camera2 콜백 세대 검증, 중복 open 방지, 전후면 전환, 일시중지 시 자원 해제.
- TextureView 화면 프레임 감지 + 8초 스톨 감시 + 최대 2회 자동 재시도, 오류 코드와 수동 재시도.
- 실제 카메라 출력에 맞추어 FIT_CENTER 변환. 기존 아이콘·패키지명·iOS AVFoundation/가상 시뮬레이터 유지.
- Android GitHub Actions 빌드 체크 워크플로 추가; 기존 iOS UI 테스트 유지.
- Android versionCode 9 / iOS build 9 / versionName 0.1.8.

## 이전 버전
- v0.1.7: GitHub 루트 소스 + 커밋·태그 배포 구조. 카메라 미리보기 중심 기능 유지.
- v0.1.6: 카메라 줌/전환 및 오류 진단 개선.
- 배포 스크립트에 Android CI 파일 보존/커밋 처리 추가.
