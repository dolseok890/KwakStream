# v0.1.11 테스트 체크리스트

- [ ] Android 전후면 카메라 최초 오픈 및 전환 반복, 검은 화면/프리징 없음
- [ ] 세로 렌즈 영상은 세로 표시, 가로 영상은 가로 표시, 0/90/180/270°에서 정방향 확인
- [ ] 기기별 센서 방향 0/90/180/270° 및 전면/후면 회전 부호 확인
- [ ] Camera2 4:3/16:9 해상도별 비율 보존 및 FIT_CENTER 검은 여백 확인
- [ ] Android 12+ HAL AUTO 회전·크롭이 지원되는 기기에서 꺼지는지 확인
- [ ] 화면/폴더블 접기·펼치기·멀티윈도 크기 변경 대응
- [ ] 전후면 전환 시 잘못된 이전 프레임의 화면 변형 없음
- [ ] iPhone 실기기 AVFoundation 세로/가로·전후면 전환 확인
- [ ] iOS 시뮬레이터 패턴, 카메라 전환과 9:16/16:9 기하 테스트 유지
- [ ] Android :app:assembleDebug 빌드 성공
- [ ] macOS Xcode iOS Simulator 빌드·UI 테스트 성공
- [ ] 앱 권한 거부·재허용, 백그라운드 복귀, 재시도·오류 표시 정상
- [ ] 기존 API 줌·카메라 ID 조회 정상
- [ ] 패키지 ID/버전/아이콘·배포 ZIP 검증

## 컴파일 오류 회귀 검증
- [ ] `pickPreviewSize()` Kotlin 실제 본문 추출 컴파일 성공
- [ ] `Size.height` 속성 참조, `Rect.height()` 메서드 호출을 구분
- [ ] 4:3/16:9/세로 3:4 미리보기 크기 선택 및 null 입력 처리 정상
- [ ] Android Gradle `:app:compileDebugKotlin` 및 `:app:assembleDebug` 성공 (Termux 실기기에서 확인)
- [ ] `kbuild KwakStream.zip` 성공 후 이전 APK 대체, 실패 시 기존 APK·ZIP 보존 확인
- [ ] iOS Simulator GitHub Actions 회귀 테스트 통과
