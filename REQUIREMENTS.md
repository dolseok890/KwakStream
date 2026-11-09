# 곽스트림 v0.1.8 — 확정 요구사항

## 이 버전의 구현 범위

1. Android CameraX 의존성을 제거하고 Camera2 API(`CameraManager`, `CameraDevice`, `CameraCaptureSession`)로 직접 미리보기 구동.
2. 메인 카메라 세션 단일 스레드 직렬화, 콜백 세대 번호(generation) 검증, 중복 열기 방지, 화면이 사라질 때 세션/Surface 종료.
3. TextureView 실제 프레임 감지, 8초 이상 프레임 정지 시 최대 2회 제한적 재시작, 오류 코드 및 수동 재시도 버튼.
4. 권한 거절/허용, 앱 포그라운드/백그라운드 전환, 전면/후면 및 카메라 ID 선택 지원.
5. `cameraIdList`에 실제로 포함된 공개 카메라 ID만 선택 가능. 논리 카메라 내부 physical ID는 임의로 열지 않음.
6. API가 제공하는 줌 범위의 최소/1×/최대와 조건에 맞는 카메라 메타데이터 유래 배율만 표시; 숫자를 임의로 생성하지 않음. Android 11 이상 CONTROL_ZOOM_RATIO, 이전은 SCALER_CROP_REGION.
7. 화면 비율: 가로 16:9, 세로 9:16 UI 영역, TextureView FIT_CENTER 행렬로 프레임 크롭/왜곡 억제.
8. iOS 기존 AVFoundation 카메라와 시뮬레이터 가상 카메라 모드 및 UI 테스트 유지. 버전만 동기화.
9. 앱 ID `live.kwak.stream`, B안 아이콘, 루트 Git 커밋 + 불변 태그 배포 스크립트 유지.

## 의도적인 제외

- Cloudflare 명령 API, RTMP/SRT, 마이크 음량, 녹화, 터치 초점/노출은 다음 단계.
- 물리 카메라 전환/렌즈 스위치의 실제 작동은 제조사 기기에서 검증 필요. 메타데이터 배율이 특정 물리 렌즈 전환을 보장하지 않음.
