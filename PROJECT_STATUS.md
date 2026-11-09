# 곽스트림 v0.1.9 작업 상태

- 상태: Camera2 보정 로직 수정 작업 / 테스트 및 ZIP 배포 검증 예정.
- Android: Camera2 유지, 초점거리/센서 크기 기반 렌즈 배율 추정 제거, API 카메라 ID/줌 범위만 표시.
- 센서 방향: device `SENSOR_ORIENTATION` 필수, 임의 90도 보정 금지.
- iOS: AVFoundation 및 iPhone 시뮬레이터 코드 변경 없음.
- Android versionCode 10, iOS build 10, 앱 ID `live.kwak.stream`, B안 아이콘 유지.
- GitHub: main 루트 최신 소스 + 불변 태그 방식. 기존 태그/폴더 임의 삭제 없음.
- Cloudflare 연동/MP4 녹화/RTMP/SRT 미구현 또는 보류, 이번 수정에 포함 안 함.
- 빌드 및 실기기 검증은 별도 수행 필요.
