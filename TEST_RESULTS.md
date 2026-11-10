# v0.1.7 테스트 결과

## 통과 (실제 실행)
- `bash -n` 배포 스크립트 구문 검사.
- 임시 Git bare 원격 저장소 테스트: 이전 4개 버전 폴더를 안전하게 정리하고 최신 루트 소스를 커밋·태그 `v0.1.7`로 원격 push.
- 이전 `v0.1.3`~`v0.1.6` 태그가 그대로 남고 과거 파일에 접근 가능함을 확인.
- 이미 존재하는 태그 재배포 시 중단하고 작업 트리를 변경하지 않음.
- ZIP 버전 불일치 시 기존 소스를 변경하기 전에 중단.

## 확인 불가 / 미실행
- GitHub 실제 원격 `dolseok890/KwakStream`에 쓰기 작업: 미실행 (사용자 Termux 실행 전).
- GitHub Actions iOS 시뮬레이터 실제 실행/테스트: 미실행.
- Android Termux Studio `kbuild` 및 실제 기기 카메라 기능: 미실행.
- iPhone 실기기 카메라 전환/줌 및 시뮬레이터 실행: 미실행.
- Google Drive 업로드: 파일 ID 및 링크 검증 후 별도 보고.

## v0.1.16 테스트
- 통과: Camera2Controller.kt 지정 오류 2개 수정 및 Kotlin 컴파일러를 이용한 타입 관련 단위 검증
- 통과: ZIP 무결성, v0.1.15 기반 소스 diff, 기존 UI/Swift/아이콘 바이트 동일성
- 미실행: Android 전체 assembleDebug, 실기기 카메라 미리보기/전환, iOS CI 빌드
