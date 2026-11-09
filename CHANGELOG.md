# CHANGELOG

## v0.1.12 — Camera2 세로 미리보기 90° 회전 문제 수정
- Android `TextureView`가 `SENSOR_ORIENTATION`을 자체 처리하는데 다시 회전하던 중복 변환 제거.
- `TextureView` 변환에는 `Display.rotation` 역회전만 적용하고, 실제 센서 방향은 원본 프레임 비율 산정에만 사용.
- 카메라 실제 출력 해상도/방향별 FIT_CENTER (확대/크롭/왜곡 없음) 및 변환 시점 보강.
- 카메라 프레임 첫 수신 후, 버퍼 크기 설정 후 변환 재적용.
- 폴더블 화면·방향 변경 시 카메라 센서 방향 메타데이터 다시 조회.
- 앱 화면에 `센서/화면/미리보기 보정` 방향 진단 정보 표시, Android 로그 `PREVIEW_APPLIED` 기록.
- 전면/후면 Camera2, 권한·재시도·줌, 기존 아이콘·패키지명 보존.
- iPhone AVFoundation/시뮬레이터 가상 카메라는 코드 변경 없음.
- 버전: Android 0.1.12 (code 13), iOS 0.1.12 (build 13).
- 미구현: MP4 녹화 (변경 범위 아님), RTMP/Cloudflare 연동 (보류).
- **중요:** 수정 코드 정적/단위 테스트 통과 ≠ 실기기 수직 미리보기 확인. Termux 전체 APK 빌드 및 전·후면 실화면 검증 필요.
