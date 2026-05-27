#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KEYSTORE_FILE="$ROOT_DIR/android/release.keystore"
SIGNING_CONFIG="$ROOT_DIR/android/release-signing.properties"
KEY_ALIAS="codexremote"

if [[ -e "$KEYSTORE_FILE" || -e "$SIGNING_CONFIG" ]]; then
  echo "Release signing files already exist; leaving them unchanged." >&2
  echo "Keystore: $KEYSTORE_FILE" >&2
  echo "Config: $SIGNING_CONFIG" >&2
  exit 1
fi

STORE_PASSWORD="$(openssl rand -hex 32)"

keytool -genkeypair \
  -v \
  -storetype PKCS12 \
  -keystore "$KEYSTORE_FILE" \
  -storepass "$STORE_PASSWORD" \
  -keypass "$STORE_PASSWORD" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=Codex Remote Control, OU=Personal, O=Codex Remote Control, L=Local, ST=Local, C=US" \
  >/dev/null

umask 077
{
  printf 'storeFile=release.keystore\n'
  printf 'storePassword=%s\n' "$STORE_PASSWORD"
  printf 'keyAlias=%s\n' "$KEY_ALIAS"
  printf 'keyPassword=%s\n' "$STORE_PASSWORD"
} >"$SIGNING_CONFIG"

chmod 600 "$KEYSTORE_FILE" "$SIGNING_CONFIG"

echo "Created release keystore: $KEYSTORE_FILE"
echo "Created release signing config: $SIGNING_CONFIG"
echo "Keep both files backed up securely; losing them prevents installing upgrades over existing release builds."
