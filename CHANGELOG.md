# Changelog

## v0.1.11
- 사용자 `kbuild KwakStream.zip` 로그의 `Camera2Controller.kt:500` Kotlin 컴파일 오류 수정.
- `pickPreviewSize()`에서 Android `Size.height` 프로퍼티를 `height()` 함수처럼 부르던 두 곳을 정확히 수정.
- `Rect.height()` 메서드 사용과 카메라 회전·FIT_CENTER·렌즈·줌·iOS 소스는 변경 없음.
- 실제 `pickPreviewSize()` 본문을 Kotlin 컴파일러로 검사하는 회귀 테스트 5개 추가.
- Android `versionName 0.1.11`/`versionCode 12`, iOS `MARKETING_VERSION 0.1.11`/`build 12`.
- 전체 Android 빌드와 실기기 검증은 Termux `kbuild`/CI 결과 확인 필요.


## v0.1.10
- Android Camera2 공식 상대 회전 공식으로 수정: 전면 sensor−display, 후면 sensor+display.
- Camera2 지원 해상도를 16:9에 맞춰 강제로 선택하던 코드를 제거하고 센서 원본 비율에 가까운 크기를 우선 선택.
- Android Compose의 9:16/16:9 강제 미리보기 영역 제거. TextureView는 전체 영역에 배치하고 실제 프레임을 균일 배율 FIT_CENTER로 배치.
- Android 12+ 지원 기기의 HAL 자동 회전 및 크롭 비활성화.
- Display 180° 변경에 대응하도록 프레임 업데이트 시 디스플레이 회전 변경 감지.
- iOS 실기기 AVFoundation `.resizeAspect`에 미리보기 비율 처리를 맡기고, 시뮬레이터 테스트 패턴/레이아웃 유지.
- 기존 Camera2/AVFoundation 렌즈 조회·줌·프리징 감시·권한·오류 로직 유지.
- 새 소스 전용 Kotlin 방향/비율 테스트 추가. Android versionCode 11, iOS build 11.

## v0.1.9
- 35mm 초점거리 보정 기반 합성 줌 값 제거. API 범위 및 실제 카메라 ID만 표시.
