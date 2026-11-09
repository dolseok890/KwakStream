# 곽스트림 PROJECT_STATUS — v0.1.12

- 작업: `v0.1.11` 세로 화면에서 전면 카메라 미리보기가 약 90° 누운 오류 재현 이미지 기반 분석.
- 원인: Camera2 `TextureView`가 센서 방향을 이미 처리함에도 앱에서 `cameraRelativeRotation(sensor,display,front)` 전체를 `TextureView.setTransform`에 다시 적용하여 중복 회전.
- 해결: `TextureView.setTransform`은 디스플레이 역회전만 적용, 센서 방향은 프레임 크기 및 장치 진단에만 사용. 디스플레이/프레임 변경 및 스트림 첫 프레임에서 변환 재적용.
- 원본 FIT_CENTER 유지. 카메라 전·후면, 줌 선택, 권한 및 오류 복구 로직 보존.
- Android versionName=0.1.12, versionCode=13; iOS MARKETING_VERSION=0.1.12/build=13. 앱 ID `live.kwak.stream` 변경 없음.
- 자체 테스트: Kotlin PreviewGeometry 및 화면 변환 32개 조합 통과, 소스 정적 검사 32/32 통과, Kotlin `pickPreviewSize` 추출 컴파일 5/5 통과.
- 검증되지 않음: Android 전체 Gradle APK, 실기기 카메라 화면·회전, iOS Xcode 빌드/실행.
- GitHub 커밋/태그는 실행하지 않음. 기존 버전의 ZIP과 태그 보존.
- 다음 실기기 검사: 세로 전면/후면, 90°/180°/270° 회전, 폴더블 상태 변경 및 재진입. 방향 진단값과 로그 확인.
