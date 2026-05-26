# Changelog

## v0.1.0 - 2026-05-27

First formal release of Codex Remote Control.

### Added

- Android client for connecting to a private-network Codex bridge.
- Session list, session resume, new chat, message sending, and streaming assistant output.
- Approval request display and response forwarding from Android.
- Session-authoritative conversation rendering with snapshot reconciliation and incremental updates.
- Code browser and diff/file-change viewing from active Codex workspaces.
- Shared bridge cache, connection history, reconnect feedback, and settings controls.
- Node.js WebSocket bridge for `codex app-server --listen stdio://`.
- Persistent bridge identity, state storage, token handling, and configurable runtime paths.
- Mock backend and Node-side tests for bridge and Android UI development flows.
- Debug and release APK build helpers.

### Release Artifacts

- Android debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Android release APK: `android/app/build/outputs/apk/release/app-release-unsigned.apk`

### Notes

- The release APK is unsigned by default. Sign it before distributing outside local/private use.
- The bridge and app are designed for trusted private networks, not direct public internet exposure.
