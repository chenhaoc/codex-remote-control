# Usage

This guide covers the local bridge and Android debug APK workflow.

## Requirements

- Node.js 20 or newer
- npm
- Codex CLI available as `codex` when using the real backend
- Android SDK and a JDK for APK builds
- A trusted private network between the phone and bridge host

The checked-in Gradle wrapper uses Gradle 8.13. The Android app targets SDK 36 and has a minimum SDK of 26.

## Install

```bash
npm install
```

## Run The Bridge

The default bridge launcher starts the real Codex backend and listens on all interfaces:

```bash
npm run bridge
```

It writes local state and token files under `data/`, then prints WebSocket URLs that can be pasted into the Android app.

Default files:

- `data/bridge-state.json`
- `data/bridge-token.txt`

The bridge URL format is:

```text
ws://HOST:PORT/?token=TOKEN
```

If Codex needs a local proxy, enable it before starting the bridge:

```bash
source ~/.zshrc && proxy_on
npm run bridge
```

## Bridge Environment Variables

`scripts/run-bridge.sh` accepts these environment variables:

| Variable | Default | Description |
| --- | --- | --- |
| `CODEX_REMOTE_HOST` | `0.0.0.0` | Bridge listen host |
| `CODEX_REMOTE_PORT` | `8787` | Bridge listen port |
| `CODEX_REMOTE_BACKEND` | `codex` | `codex` or `mock` |
| `CODEX_REMOTE_TOKEN_FILE` | `data/bridge-token.txt` | Token file path |
| `CODEX_REMOTE_STATE_FILE` | `data/bridge-state.json` | State file path |
| `CODEX_REMOTE_SYNC_LOG` | `0` | Set `1`, `true`, or `yes` to enable sync logging |
| `CODEX_REMOTE_SYNC_LOG_FILE` | `data/bridge-sync.log` | Sync log file path |

Example using the mock backend:

```bash
CODEX_REMOTE_BACKEND=mock npm run bridge
```

Example using another port:

```bash
CODEX_REMOTE_PORT=8790 npm run bridge
```

## Direct Bridge Command

The Node entrypoint can be run directly:

```bash
node src/index.mjs \
  --backend codex \
  --listen 0.0.0.0:8787 \
  --state data/bridge-state.json \
  --token-file data/bridge-token.txt
```

Useful options:

- `--backend mock|codex`
- `--listen HOST:PORT`
- `--state PATH`
- `--token-file PATH`
- `--sync-log PATH`
- `--protocol-log PATH`
- `--codex-bin PATH`
- `--codex-arg VALUE`
- `--codex-config KEY=VALUE`

`--protocol-log` records raw bridge-to-Codex protocol lines and may contain sensitive content. Prefer a temporary path and delete it after debugging.

## Android APK

Build the debug APK:

```bash
npm run apk
```

Output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

You can also open `android/` in Android Studio.

The debug app uses `ws://` cleartext traffic so it can connect to a private-network bridge. Do not treat the debug APK as a hardened public release build.

## Debug Logs

Android session sync logs are disabled by default:

```bash
adb shell setprop log.tag.CodexRemoteSync DEBUG
adb logcat -s CodexRemoteSync:D
```

Disable them again:

```bash
adb shell setprop log.tag.CodexRemoteSync INFO
```

Android approval logs:

```bash
adb shell setprop log.tag.CodexRemoteApproval DEBUG
adb logcat -s CodexRemoteApproval:D
```

Disable:

```bash
adb shell setprop log.tag.CodexRemoteApproval INFO
```

Bridge sync logging:

```bash
CODEX_REMOTE_SYNC_LOG=1 npm run bridge
```

Bridge protocol logging:

```bash
node src/index.mjs \
  --backend codex \
  --listen 0.0.0.0:8787 \
  --state data/bridge-state.json \
  --token-file data/bridge-token.txt \
  --protocol-log /tmp/codex-remote-protocol.log
```
