# 곽스트림 v0.1.11

상태: **v0.1.10 Android 컴파일 오류 수정 / 전체 Android·iOS 빌드 재검증 필요**

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

## v0.1.11 컴파일 오류 수정
- 사용자 제공 Termux 로그: `:app:compileDebugKotlin FAILED`; `Camera2Controller.kt:500:41` 및 `500:83`의 `Size.height()` 호출 오류 2개.
- 조치: `pickPreviewSize()` 함수의 `it.height()` 두 곳 → `it.height`. Rect.height()는 유지.
- 관련 카메라 방향/비율/줌 코드는 변경하지 않았음.
- Android versionName `0.1.11`, versionCode `12`; iOS marketing version `0.1.11`, build `12`.
- 테스트 상태: 함수만 추출한 Kotlin 컴파일·호출 회귀 테스트 통과, 전체 APK 빌드 미실행 (Android SDK 없음).
- GitHub 커밋·태그 및 Drive 업로드는 별도 완료 확인 필요. 기존 태그/ZIP 보존.
