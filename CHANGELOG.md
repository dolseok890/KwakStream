# CHANGELOG

## v0.1.9 — 2026-11-09
- Android Camera2 카메라 센서 크기·초점거리로 광각/망원 배율을 역산하던 잘못된 `supportedZoomStops` 로직 제거.
- 노출 카메라 ID는 cameraIdList 그대로, 줌 값은 카메라가 보고한 지원 범위의 하한/1×/상한으로 제한.
- 물리 렌즈와 디지털 줌 구별 안내 텍스트 추가. 카메라 목록의 mm 표기를 제거.
- 센서 방향 미조회 시 무조건 90°로 보정하지 않고 `E_ORIENTATION` 오류 처리.
- Android versionCode 10, versionName 0.1.9 / iOS build 10, version 0.1.9.
- iOS 코드/시뮬레이터/아이콘 기존 상태 유지.
- 이번 버전에서 MP4 녹화는 추가되지 않음.

## v0.1.8
- Android Camera2 전환/미리보기/카메라 선택, API 범위 및 계산 기반 줌, 오류 복구 초기 구현.
