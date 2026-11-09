# 곽스트림 KwakStream v0.1.8

Android Camera2 + iPhone AVFoundation 기반 카메라 미리보기 안정화 단계.

## 현재 구현
- Android: CameraManager/CameraDevice/CameraCaptureSession 직접 사용, TextureView 프레임 감지, 안전한 세션 해제와 자동/수동 재시도.
- 실제 `CameraManager.cameraIdList`로 열 수 있는 카메라 ID만 표시. API 메타데이터가 제공하는 줌 범위로 줌 버튼 생성.
- 최소 줌·1배·최대 줌은 카메라의 줌 API에서 확인된 값. 추가 물리 카메라 메타데이터에서 유도한 배율은 특정 렌즈 스위치를 보장하지 않음.
- iOS: 기존 AVFoundation의 전후면 전환·줌·세션 유지, 시뮬레이터 전용 테스트 패턴 유지.
- 아직 RTMP, 녹화, 원격 제어가 없는 미리보기 중심 소스.

## 빌드/배포
1. ZIP을 `/sdcard/Download/KwakStream.zip`으로 저장.
2. 기존 Termux Studio `kbuild KwakStream.zip`으로 APK 빌드.
3. GitHub 업로드는 기존 `proot-distro` 명령으로 `scripts/publish-version.sh`를 실행. GitHub 루트 최신 소스 + 커밋/태그를 사용하며, 이전 태그는 변경하지 않음.
4. 태그를 푸시하면 Android 빌드 및 iOS 시뮬레이터 테스트 워크플로를 자동 실행하도록 설정.
5. 설치된 Android 또는 실제 iPhone에서 카메라 전환·프리징·회전·줌을 직접 테스트해야 기능 검증이 완료됨.

## 문제 추적
- 화면 하단 상태 코드 `E_*`가 표시되면 로그캣에서 `KwakStreamCamera2` 검색.
- 프레임이 8초 멈추면 최대 2회 세션을 재시작하며, 계속 실패하면 `E_STALL`을 표시.
- 권한 오류/카메라 장치 오류가 지속되면 카메라 재시도를 누르거나 앱 재실행.
