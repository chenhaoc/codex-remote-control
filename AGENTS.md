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
  - `app/src/main/java/com/haochen/codexremote/MainActivity.kt`: Android lifecycle, top-level state holders, shared constants, and app setup only.
  - `app/src/main/java/com/haochen/codexremote/MainActivityUi.kt`: Compose app shell, top bar, shared dialogs, and small shared UI primitives.
  - `app/src/main/java/com/haochen/codexremote/*Page.kt`: page-level Compose UI such as chat, connection, code browser, and session history.
  - `app/src/main/java/com/haochen/codexremote/MainActivity*Controller.kt`: focused Activity extension logic for bridge, protocol, sessions, approvals, conversation state, file changes, and code browser state.
  - `app/src/main/java/com/haochen/codexremote/*Models.kt`: data models and parsing/formatting helpers.
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
- Keep code close to its existing responsibility. Do not put new UI, protocol, session, approval, or rendering logic into `MainActivity.kt` just because it is convenient.
- Prefer extending the existing page/controller/model split over creating a new catch-all file.
- Treat `src/generated/` as generated code.

## Code Organization Rules

- `MainActivity.kt` should stay small. It is the lifecycle/state container, not the home for new feature logic.
- Compose UI belongs in page/shell files:
  - app shell/top bar/shared dialog: `MainActivityUi.kt`
  - drawer: `MainActivityDrawer.kt`
  - chat UI: `ChatPage.kt`
  - connection UI: `ConnectionPage.kt`
  - code browser UI: `CodeBrowserPage.kt`
  - session/model UI: `SessionPage.kt`
- Activity-owned behavior belongs in focused extension-controller files:
  - bridge URL, connection history, reconnect, request sending: `MainActivityBridgeController.kt`
  - incoming bridge events and response dispatch: `MainActivityProtocolController.kt`
  - approval presentation/actions: `MainActivityApprovalController.kt`
  - session list/content/model selection/new chat: `MainActivitySessionController.kt`
  - conversation item mutation and live status: `MainActivityConversationController.kt`
  - snapshot restoration from session content: `MainActivitySnapshotController.kt`
  - thread item/tool item parsing: `MainActivityThreadItemController.kt`
  - file-change and diff conversation items: `MainActivityFileChangeController.kt`
  - code-browser state and file-read cache: `MainActivityCodeBrowserController.kt`
  - display labels, notices, and small pure helpers: `MainActivityPresentation.kt`
- Chat HTML/CSS/markdown/code rendering belongs in the `ConversationHistory*`, `ConversationMarkdownHtml.kt`, `ConversationCodeHtml.kt`, and `ConversationHtmlUtils.kt` files, not in Compose page files.
- Code browser syntax/rendering helpers belong in `CodeBrowserModels.kt` and `CodeBrowserRendering.kt`.
- When a file approaches roughly 700-900 lines, pause before adding more and consider a responsibility-based split. Avoid moving a long block into a single new long file; split by page, protocol area, or model/helper boundary in the same change.
- A useful split should make the next edit easier to locate. Avoid tiny arbitrary files, but prefer several cohesive 100-600 line files over one 2000-line file.
- If a refactor requires exposing Activity state to extension functions, keep visibility no broader than needed and document the responsibility through the target filename rather than adding a generic utility bucket.

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

- Compose UI and connection/session behavior are intentionally split across page and controller files. Keep that split intact.
- Chat rendering is partly HTML/CSS inside `ConversationHistoryWebView.kt`; markdown/code-block UI changes usually belong there, not in Compose.
- Chat history rendering may be optimized incrementally, but it must never bypass the session-authoritative `conversationItems` model.
- WebSocket transport lifecycle lives in `BridgeClient.java` plus reconnect/state logic in `MainActivityBridgeController.kt`.
- For UI changes, prefer small targeted updates and keep mobile layout behavior in mind:
  - keyboard/insets
  - scroll restoration
  - dialog vs sheet ergonomics
  - connection feedback

## Conversation History Rules

- The session snapshot returned by `session.content` is the source of truth for conversation history. Treat realtime events as a temporary projection only.
- Realtime events must flow through Activity-owned state such as `conversationItems`, pending approvals, and related item-id maps. Do not write realtime events directly into WebView DOM as an independent message store.
- Snapshot reconciliation must be preserved. When a session snapshot arrives, it must be able to replace, correct, reorder, or remove any realtime-projected UI state.
- Any WebView rendering optimization must keep this rule:
  - initial load and session switch may render the full snapshot;
  - simple tail appends or assistant deltas may use incremental DOM updates only if they are derived from the current `conversationItems`;
  - if item ids, order, count, session id, or rendered item type diverge from the previous rendered state, fall back to a full snapshot render.
- Do not trade correctness for smoother scrolling. If there is doubt, prefer session-authoritative rendering over realtime-only rendering.
- Avoid duplicating message classification, markdown parsing, or code rendering as a separate JavaScript truth layer. Kotlin remains responsible for turning `ConversationItem` data into trusted HTML or HTML fragments.

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
