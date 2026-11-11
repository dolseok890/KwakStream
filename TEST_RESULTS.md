# v0.1.33

v0.1.33: Android Termux 빌드·실기기와 iOS 빌드·시뮬레이터·실기기 미실행. 전면 화각 및 방향 동작 미검증.

# v0.1.32

v0.1.32: Android Termux Studio 빌드·실기기와 iOS 빌드·시뮬레이터·실기기 미실행. 16:9 계산 및 ZIP 확인 외 기능 미검증.

# v0.1.31

v0.1.31: Android Termux Studio 빌드·Android 실기기 및 iOS 빌드·시뮬레이터·실기기 미실행. 마이크 신호·권한·수명주기 미검증.

# v0.1.30

v0.1.30: Android Termux Studio 빌드·실기기, 초점 비교, iOS 빌드·시뮬레이터 미실행. 기기 기본값이 연속 동영상 AF일 가능성 있음.

# v0.1.29

v0.1.29: Android Termux Studio 빌드 및 실기기, iOS GitHub Actions 빌드·시뮬레이터 미실행. 시스템 바 스와이프, 권한창, 폴더블 동작 미검증.

# v0.1.28

v0.1.28: Android Termux Studio 빌드·실기기, iOS 빌드·시뮬레이터·실기기 미실행. 실제 충전 전환 및 잔량 변경 미검증.

# v0.1.27

v0.1.27: Android Termux Studio 빌드·Android 실기기 및 iOS 빌드·시뮬레이터·실기기 미실행. 배터리·레이아웃 실측 미검증.

# v0.1.26

v0.1.26: Android Termux Studio 빌드·실기기와 iOS 빌드·시뮬레이터·실기기 미실행 (환경 제한). 화면 크기·카메라 동작 미검증.

# v0.1.25

v0.1.25: Android Termux Studio 빌드 및 실기기, iOS 빌드·시뮬레이터·실기기 미실행 (환경 제한). 화면·카메라 동작 실측 미실행.

# v0.1.24

v0.1.24: Android Termux Studio 빌드, Android 실기기, iOS 빌드·시뮬레이터·실기기, 실제 카메라 및 작은 화면 검증: 미실행. 안내선은 미리보기 UI에만 표시.

# v0.1.23

v0.1.23: Android Termux Studio 빌드, Android 실기기, iOS 빌드·시뮬레이터·실기기: 미실행 (환경 제약). 충전 단자 방향 및 실제 미리보기: 미검증.

# v0.1.22

v0.1.22: Android Termux Studio 빌드, Android 실기기, iOS 빌드 및 시뮬레이터, iOS 실기기: 미실행 (환경 제한). UI 실제 표시 및 카메라 회귀: 미실행.

# v0.1.21 검증
- 미실행: Android/iOS 빌드, 기기 카메라, 권한 및 오류 표시 테스트.
- 기존의 간헐적인 Camera2 Surface 오류는 해결되지 않았고 원인 미확정.
- 통과: 오류 표시 조건 정적 검사, Camera2 제어 코드 및 iOS Swift/테스트/CI 파일 원본 비교, XML/YAML, ZIP 무결성.

## v0.1.34
- Android 빌드: 미실행 (Android SDK/Gradle 환경 없음).
- 실제 기기 화면, 카메라 방향, 오디오 미터: 미실행.
- LIVE/녹화 중 방향 잠금: 확인 불가 (기존 기능 미구현).

## v0.1.35
- 빌드 및 실제 기기 테스트: 미실행.
- LIVE·녹화 진행 중 회전 잠금: 확인 불가, 기존 기능 미구현.

## v0.1.36
- ZIP 무결성과 배포 스크립트 소스·버전 검사: 통과.
- Android 빌드 및 기기 테스트: 미실행.

## v0.1.37
- ZIP 무결성과 배포 스크립트 검사 통과.
- Android 빌드 및 실기기 테스트 미실행.
- LIVE·녹화는 미구현.
