#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SIGNING_CONFIG="$ROOT_DIR/android/release-signing.properties"
RELEASE_DIR="$ROOT_DIR/android/app/build/outputs/apk/release"
VERSION="$(node -p "require('$ROOT_DIR/package.json').version")"
SIGNED_APK="$RELEASE_DIR/codex-remote-control-v${VERSION}-release.apk"

if [[ ! -f "$SIGNING_CONFIG" ]]; then
  echo "Missing release signing config: $SIGNING_CONFIG" >&2
  echo "Run: bash android/scripts/create-release-keystore.sh" >&2
  exit 1
fi

cd "$ROOT_DIR/android"
./gradlew assembleRelease

if [[ ! -f "$SIGNED_APK" ]]; then
  echo "Expected signed release APK was not produced: $SIGNED_APK" >&2
  exit 1
fi

echo "Release APK ready: $SIGNED_APK"
