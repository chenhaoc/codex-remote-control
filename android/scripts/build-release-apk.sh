#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR/android"
./gradlew assembleRelease
echo "Release APK ready: $ROOT_DIR/android/app/build/outputs/apk/release/app-release-unsigned.apk"
