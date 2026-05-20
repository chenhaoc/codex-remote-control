# AGENTS.md

## Project Snapshot

This repository is a personal remote-control setup for Codex:

- A thin Linux bridge runs on the host machine and exposes a WebSocket API.
- An Android client connects over private networking and provides chat, session history, approvals, and code/diff viewing.

The project is intentionally small. Prefer focused changes over introducing large frameworks or extra layers.

## Repo Map

- `src/`
  - `index.mjs`: Node entrypoint.
  - `bridge-server.mjs`: WebSocket bridge server.
  - `backends/codex.mjs`: real Codex backend integration.
  - `backends/mock.mjs`: local mock backend for tests and UI/dev flows.
  - `generated/`: generated types. Do not hand-edit unless the task is explicitly about regeneration.
- `android/`
  - `app/src/main/java/com/haochen/codexremote/MainActivity.kt`: main Compose UI and most client state.
  - `app/src/main/java/com/haochen/codexremote/ConversationHistoryWebView.kt`: chat HTML/CSS/markdown rendering.
  - `app/src/main/java/com/haochen/codexremote/BridgeClient.java`: OkHttp WebSocket wrapper.
  - `scripts/build-apk.sh`: debug APK build helper.
- `scripts/run-bridge.sh`: local bridge launcher.
- `test/`: Node-side tests for bridge and backend behavior.
- `data/`: local bridge state and token files.

## Working Rules

- Keep the existing architecture: thin bridge, lightweight Android client.
- Preserve the current stack unless the task requires otherwise:
  - Node.js ESM on the bridge side
  - Kotlin + Jetpack Compose on Android
  - OkHttp WebSocket for Android transport
- Avoid adding Room, Hilt, services, or major new dependencies unless the task clearly justifies them.
- Prefer editing existing flows in `MainActivity.kt` over scattering state across many new files.
- Treat `src/generated/` as generated code.

## Common Commands

From repo root:

```bash
npm test
npm run bridge
npm run apk
```

Android build commands usually need the shell environment from `.zshrc`:

```bash
source ~/.zshrc && cd android && ./gradlew :app:compileDebugKotlin
source ~/.zshrc && cd android && ./gradlew assembleDebug
```

Debug APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

If Codex networking needs the local proxy:

```bash
source ~/.zshrc && proxy_on
npm run bridge
```

## Bridge Notes

- WebSocket clients connect with `ws://HOST:PORT/?token=...`.
- The first server message is a `hello` payload.
- The bridge forwards Codex-style events such as:
  - `thread/started`
  - `turn/started`
  - `item/agentMessage/delta`
  - approval-related events
  - `turn/completed`
- When changing bridge behavior, run `npm test`.

## Android Notes

- Compose UI and connection/session state are centralized in `MainActivity.kt`.
- Chat rendering is partly HTML/CSS inside `ConversationHistoryWebView.kt`; markdown/code-block UI changes usually belong there, not in Compose.
- WebSocket transport lifecycle lives in `BridgeClient.java` plus reconnect/state logic in `MainActivity.kt`.
- For UI changes, prefer small targeted updates and keep mobile layout behavior in mind:
  - keyboard/insets
  - scroll restoration
  - dialog vs sheet ergonomics
  - connection feedback

## Validation Expectations

- For bridge or protocol changes: run `npm test`.
- For Android code changes: run at least

```bash
source ~/.zshrc && cd android && ./gradlew :app:compileDebugKotlin
```

- If the task touches packaging or install behavior, prefer:

```bash
source ~/.zshrc && cd android && ./gradlew assembleDebug
```

## Local Context

- The repo may contain local planning files like `findings.md`, `progress.md`, and `task_plan.md`.
- Do not include those in commits unless the task explicitly asks for them.

## Commit Guidance

- Prefer conventional commits.
- Keep commits scoped to one logical change.
- Avoid mixing Android UI work, bridge protocol work, and local notes in the same commit unless they are tightly coupled.
