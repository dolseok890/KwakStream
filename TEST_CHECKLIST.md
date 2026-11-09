# 곽스트림 v0.1.8 검증 체크리스트

## A. 코드/구성 검증
- [ ] Android CameraX 라이브러리/호출 없음
- [ ] Camera2 상태 콜백 세대 검증/중복 open 방지
- [ ] 권한과 세션 생명주기 처리
- [ ] 프레임 정지 복구 제한(무한 반복 방지)
- [ ] 장치 API에 없는 카메라/줌 값 미노출
- [ ] UI FIT_CENTER 처리 및 방향 변경
- [ ] Android/iOS 버전과 앱 ID·아이콘 보존
- [ ] iOS 시뮬레이터 AVFoundation 접근 차단 유지
- [ ] ZIP 내용/압축 검사

## B. 실제 빌드 및 기기 검증
- [ ] Android Termux Studio `kbuild KwakStream.zip` 성공
- [ ] GitHub Actions Android `assembleDebug` 성공
- [ ] GitHub Actions macOS iOS 시뮬레이터 UI 테스트 성공
- [ ] Android 10 이하 crop-zoom 기기 검증
- [ ] Android 11 이상 zoomRatio 기기 검증
- [ ] 후면↔전면 및 카메라 ID 연속 전환 30회
- [ ] 화면 회전 20회, 백그라운드 복귀 20회, 권한 거절/복구
- [ ] 일부 렌즈/전면 카메라가 없는 기기에서 오류 없이 비활성화
- [ ] 검은 화면, TextureView 프레임 정지, 복구 2회 후 오류 표시
- [ ] 실제 장치의 광각/초광각/망원 줌과 화면 왜곡 여부
- [ ] 1시간 이상 동작/발열/메모리 누수 및 다른 앱과 충돌 테스트
- [ ] 기존 iPhone AVFoundation 및 가상 시뮬레이터 동작 회귀
