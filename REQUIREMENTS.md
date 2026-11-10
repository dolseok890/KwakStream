# 곽스트림 GitHub 릴리스 정책 (v0.1.7)

- 저장소: `dolseok890/KwakStream`, 배포 브랜치 `main` (새 소스는 저장소 루트).
- 신규 릴리스에 버전별 GitHub 폴더를 생성하지 않는다.
- `versionName`, iOS `MARKETING_VERSION`과 일치하는 `vX.Y.Z` 태그를 새 커밋에 생성한다.
- 기존 릴리스 태그를 덮어쓰거나 이동하거나 삭제하지 않는다. 이전 버전은 Git 기록/태그로 복구한다.
- 기존 루트의 과거 버전 폴더는 해당 태그 안에 전체 소스가 있다는 확인이 가능할 때만 최신 브랜치에서 삭제한다.
- 실제 앱 아이콘, 카메라 동작, Android/iOS 번들 ID 및 기존 빌드 정책을 보존한다.
- GitHub Actions는 태그의 저장소 루트 iOS 소스를 기준으로 빌드/테스트한다.
- Android는 `kbuild KwakStream.zip` 유지, ZIP/APK 파일명에 버전 미포함.
- Google Drive는 기존 정책에 따라 **버전별 폴더를 유지**하며, GitHub 버전 폴더 정책과 혼동하지 않는다.
- 실패한 업로드·빌드·테스트를 성공으로 보고하지 않는다.
