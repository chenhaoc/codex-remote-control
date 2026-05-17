#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR/android"
./gradlew assembleDebug
echo "APK ready: $ROOT_DIR/android/app/build/outputs/apk/debug/app-debug.apk"
