#!/bin/sh
set -eu
VERSION=8.9
ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/kwakstream-boot"
EXEC="$ROOT/gradle-$VERSION/bin/gradle"
if [ ! -x "$EXEC" ]; then
 mkdir -p "$ROOT"
 ZIP="$ROOT/gradle-$VERSION-bin.zip"
 curl --fail --location --retry 2 "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
 unzip -q -o "$ZIP" -d "$ROOT"
fi
exec "$EXEC" "$@"
