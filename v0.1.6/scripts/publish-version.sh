#!/usr/bin/env bash
# Run inside a clean Git repository with an existing origin and checked-out branch.
set -euo pipefail
if [ "$#" -ne 2 ]; then echo 'Usage: bash publish-version.sh /path/KwakStream.zip v0.1.3' >&2; exit 1; fi
zipfile=$(realpath "$1")
version=$2
[[ "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'Invalid version' >&2; exit 1; }
repo=$(git rev-parse --show-toplevel)
cd "$repo"
branch=$(git symbolic-ref --short HEAD)
git remote get-url origin >/dev/null
[ -z "$(git status --porcelain)" ] || { echo 'Commit or stash your changes first.' >&2; exit 1; }
[ ! -e "$version" ] || { echo 'Version folder exists; preserved.' >&2; exit 1; }
! git rev-parse -q --verify "refs/tags/$version" >/dev/null || { echo 'Tag exists; preserved.' >&2; exit 1; }
[ -z "$(git ls-remote --tags origin "refs/tags/$version")" ] || { echo 'Remote tag exists; preserved.' >&2; exit 1; }
python3 - "$zipfile" "$version" <<'PY'
import sys,zipfile,pathlib,re
z=zipfile.ZipFile(sys.argv[1]); root=pathlib.Path(sys.argv[2]); expected=sys.argv[2][1:]
for name in z.namelist():
 p=pathlib.PurePosixPath(name)
 if p.is_absolute() or '..' in p.parts: raise SystemExit('Unsafe ZIP path')
config=z.read('app/build.gradle.kts').decode()
if not re.search(r'versionName\s*=\s*"'+re.escape(expected)+r'"',config): raise SystemExit('ZIP version does not match tag')
root.mkdir(); z.extractall(root)
PY
mkdir -p .github/workflows
cp "$version/.github/workflows/ios-simulator.yml" .github/workflows/ios-simulator.yml
git add -- "$version" .github/workflows/ios-simulator.yml
git commit -m "Add KwakStream $version"
git tag "$version"
git push origin "$branch"
git push origin "refs/tags/$version"
printf 'Published %s. Check GitHub Actions.\n' "$version"
