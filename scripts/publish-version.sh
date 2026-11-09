#!/usr/bin/env bash
# KwakStream root-source publishing. One commit and immutable tag per release.
# Usage: bash publish-version.sh /sdcard/Download/KwakStream.zip v0.1.7
set -euo pipefail

fail() { printf '[ERROR] %s\n' "$*" >&2; exit 1; }
info() { printf '[KwakStream] %s\n' "$*"; }

(( $# == 2 )) || fail 'Usage: publish-version.sh /path/KwakStream.zip v0.1.7'
command -v git >/dev/null || fail 'git is required'
command -v python3 >/dev/null || fail 'python3 is required'
zipfile=$(realpath -- "$1")
version=$2
[[ "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Invalid tag: $version"
[[ -f "$zipfile" ]] || fail "ZIP not found: $zipfile"

repo=$(git rev-parse --show-toplevel 2>/dev/null) || fail 'Run from a Git repository'
cd "$repo"
[[ "$(basename "$repo")" == 'KwakStream' ]] || fail 'Git repository folder must be KwakStream'
branch=$(git symbolic-ref --short HEAD 2>/dev/null) || fail 'Checkout a branch (not detached HEAD)'
origin=$(git remote get-url origin) || fail 'origin remote is missing'
case "$origin" in
  https://github.com/dolseok890/KwakStream|https://github.com/dolseok890/KwakStream.git|\
  git@github.com:dolseok890/KwakStream|git@github.com:dolseok890/KwakStream.git|\
  ssh://git@github.com/dolseok890/KwakStream|ssh://git@github.com/dolseok890/KwakStream.git)
    ;;
  *) fail "Unexpected origin (not dolseok890/KwakStream): $origin" ;;
esac

[[ -z "$(git status --porcelain --untracked-files=all)" ]] || fail 'Uncommitted/untracked files found; commit, stash or move them first'

# Validate the archive in an isolated temp directory BEFORE touching the checkout.
staging=$(mktemp -d)
trap 'rm -rf -- "$staging"' EXIT
python3 - "$zipfile" "$version" "$staging" <<'PY'
import pathlib, re, stat, sys, zipfile
zf, tag, root = sys.argv[1:]
root = pathlib.Path(root)
try:
    z = zipfile.ZipFile(zf)
    bad = z.testzip()
    if bad:
        raise ValueError(f'Corrupted ZIP entry: {bad}')
    names = []
    for entry in z.infolist():
        name = entry.filename
        p = pathlib.PurePosixPath(name)
        if p.is_absolute() or '..' in p.parts or '\\' in name or not p.parts:
            raise ValueError(f'Unsafe ZIP path: {name}')
        if p.parts[0] in ('.git', '.gradle', 'build') or 'build' in p.parts[1:]:
            raise ValueError(f'Build/git files are not allowed: {name}')
        mode = entry.external_attr >> 16
        if stat.S_ISLNK(mode):
            raise ValueError(f'Symlinks not accepted: {name}')
        names.append(name)
    must_have = ['app/build.gradle.kts', 'ios/project.yml', 'scripts/publish-version.sh',
                 '.github/workflows/ios-simulator.yml', '.github/workflows/android-build.yml', 'settings.gradle.kts', 'gradlew']
    if any(m not in names for m in must_have):
        raise ValueError('Missing required sources: ' + str([m for m in must_have if m not in names]))
    gradle = z.read('app/build.gradle.kts').decode('utf-8')
    ios = z.read('ios/project.yml').decode('utf-8')
    expected = tag[1:]
    if not re.search(r'versionName\s*=\s*"' + re.escape(expected) + r'"', gradle):
        raise ValueError('Android versionName does not match tag')
    if not re.search(r'MARKETING_VERSION:\s*["\']?' + re.escape(expected) + r'(?=["\'\s]|$)', ios, re.M):
        raise ValueError('iOS MARKETING_VERSION does not match tag')
    if 'live.kwak.stream' not in gradle or 'PRODUCT_BUNDLE_IDENTIFIER: live.kwak.stream' not in ios:
        raise ValueError('Unexpected application identifier')
    z.extractall(root)
except (OSError, ValueError, zipfile.BadZipFile) as e:
    raise SystemExit(f'[ERROR] ZIP validation failed: {e}')
print(f'[KwakStream] Archive verified for {tag}: {len(names)} entries')
PY

info "Fetching latest $branch and tags"
git fetch --tags origin "$branch"
git merge --ff-only "origin/$branch" || fail 'Local and remote branches diverged; manual reconciliation required'

if git show-ref --verify --quiet "refs/tags/$version"; then
  fail "Tag $version already exists. Do not overwrite a released version; increment the app version."
fi

# The old publisher put snapshots in vX.Y.Z/ folders. Only remove those folders
# from HEAD when a matching *published* tag holds the complete historical snapshot.
# The old versions remain available through their commits/tags without rewriting history.
mapfile -t legacy_dirs < <(git ls-files | awk -F/ '$1 ~ /^v[0-9]+\.[0-9]+\.[0-9]+$/ {print $1}' | LC_ALL=C sort -u)
for old in "${legacy_dirs[@]}"; do
  git show-ref --verify --quiet "refs/tags/$old" || fail "Cannot migrate $old: historical tag missing"
  git ls-remote --exit-code --tags origin "refs/tags/$old" >/dev/null 2>&1 || fail "Cannot migrate $old: remote historical tag missing"
  git cat-file -e "refs/tags/$old:$old/app/build.gradle.kts" 2>/dev/null || fail "Cannot migrate $old: tag does not contain that version's app sources"
  git diff --quiet "refs/tags/$old" HEAD -- "$old" || fail "Cannot migrate $old: version folder changed after the historical tag"
  info "Verified full historical snapshot for $old (published tag preserved)"
done

# Manage only KwakStream's explicit source paths. Preserve other workflow files
# or unrelated repository content. Deleted source files under these known paths
# are removed, so the root reflects the new release instead of merging stale code.
managed_dirs=(app ios gradle scripts)
managed_files=(build.gradle.kts settings.gradle.kts gradle.properties gradlew \
               README.md CHANGELOG.md PROJECT_STATUS.md REQUIREMENTS.md \
               TEST_CHECKLIST.md TEST_RESULTS.md .gitignore \
               .github/workflows/ios-simulator.yml .github/workflows/android-build.yml)

python3 - "$repo" "$staging" <<'PY'
import pathlib, shutil, sys
repo, stage = map(pathlib.Path, sys.argv[1:])
managed_dirs = ['app', 'ios', 'gradle', 'scripts']
managed_files = ['build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew',
                 'README.md', 'CHANGELOG.md', 'PROJECT_STATUS.md', 'REQUIREMENTS.md',
                 'TEST_CHECKLIST.md', 'TEST_RESULTS.md', '.gitignore',
                 '.github/workflows/ios-simulator.yml', '.github/workflows/android-build.yml']
for folder in managed_dirs:
    dest = repo / folder
    src = stage / folder
    if dest.exists():
        # Source tree is replaced, not mingled with stale source files.
        shutil.rmtree(dest)
    if src.is_dir():
        shutil.copytree(src, dest)
for name in managed_files:
    src = stage / name
    dst = repo / name
    if src.is_file():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
    elif dst.is_file():
        dst.unlink()
PY

for old in "${legacy_dirs[@]}"; do
  git rm -rq -- "$old"
done

git add -A -- "${managed_dirs[@]}" "${managed_files[@]}"
if git diff --cached --quiet; then
  fail 'Nothing changed: refusing to create an empty release'
fi

git diff --cached --stat
git commit -m "Release KwakStream $version (root source; commit and tag)"
git tag -a "$version" -m "KwakStream $version"

info "Pushing branch $branch"
git push origin "HEAD:refs/heads/$branch" || fail 'Branch push failed (local commit/tag preserved; do not regenerate version)'
info "Pushing immutable tag $version"
git push origin "refs/tags/$version" || fail 'Tag push failed (retry: git push origin refs/tags/'"$version"')'

info "Published $version: root source + commit + tag; previous tags preserved."
info 'GitHub Actions will test the tagged iOS source; Android build can run next.'
