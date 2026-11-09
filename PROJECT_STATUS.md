# 곽스트림 프로젝트 상태 — v0.1.8

- 작업 상태: 코드 수정 완료 / 정적검사 통과 / 실제 Android·iOS 빌드 및 기기 검증 대기.
- Android: Camera2 기반 단일 세션 미리보기·카메라 API ID 선택·API 줌 최소/기본/최대와 메타데이터 기반 줌.
- iOS: AVFoundation 및 시뮬레이터 가상 카메라 모드 유지.
- 버전: Android 0.1.8 code 9 / iOS 0.1.8 build 9.
- 앱 ID: `live.kwak.stream`. 아이콘 기존 B안 유지.
- 배포: ZIP 파일명 `KwakStream.zip`, GitHub root source + 태그 `v0.1.8`, Drive `프로젝트/KwakStream/v0.1.8/KwakStream.zip` (최종 ZIP 교체 후 실제 파일 메타데이터로 검증).
- 제외: Cloudflare API, 송출, 녹화, 초점/노출. 필요 시 다음 버전으로 순차 개발.
- 실제 빌드 결과와 카메라 오류 복구 결과는 아직 확인되지 않음.

## 2026-11-09 코드 수정 확인
- PASS: 소스 정적 검사 18/18, Swift 구문 파싱, 기존 AVFoundation/시뮬레이터 코드 및 아이콘 SHA256 유지.
- NOT RUN: Android SDK Gradle 컴파일, macOS Xcode CI, 실기기 프레임/렌즈 테스트.
- Drive 파일 ID: `1ZCK4a69s7_VjQwi2_zLggq1Jqmx79s91` (최종 ZIP 교체 검증 대기).
- Drive 다운로드 주소: https://drive.google.com/file/d/1ZCK4a69s7_VjQwi2_zLggq1Jqmx79s91/view?usp=drivesdk
