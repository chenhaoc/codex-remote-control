# Development

## Repository Layout

```text
src/
  index.mjs
  bridge-server.mjs
  backends/
  bridge/
  generated/
android/
  app/src/main/java/com/haochen/codexremote/
  scripts/build-apk.sh
docs/
test/
scripts/
```

Important boundaries:

- Keep the bridge thin and avoid adding large frameworks.
- Do not hand-edit `src/generated/` unless the task is explicitly about regenerated protocol types.
- Keep Android lifecycle and top-level state in `MainActivity.kt`.
- Put Compose UI in page or shell files.
- Put Activity-owned behavior in focused `MainActivity*Controller.kt` files.
- Keep chat HTML, markdown, and code rendering in the `Conversation*` rendering files.

## Commands

Run Node tests:

```bash
npm test
```

Run the bridge:

```bash
npm run bridge
```

Build the Android debug APK:

```bash
npm run apk
```

Build the Android release APK:

```bash
npm run apk:release
```

Compile Android Kotlin:

```bash
source ~/.zshrc && cd android && ./gradlew :app:compileDebugKotlin
```

Build Android debug:

```bash
source ~/.zshrc && cd android && ./gradlew assembleDebug
```

## Validation Expectations

- Bridge or protocol changes: run `npm test`.
- Android code changes: run `source ~/.zshrc && cd android && ./gradlew :app:compileDebugKotlin`.
- Packaging or install behavior changes: run `source ~/.zshrc && cd android && ./gradlew assembleDebug`.
- Release packaging changes: run `source ~/.zshrc && cd android && ./gradlew assembleRelease`.

Docs-only changes do not require Android compilation, but this repository is small enough that running the bridge tests is usually cheap.

## Local Runtime Files

These files and directories are local-only:

- `data/`
- `~/.config/codex_remote_control/`
- `node_modules/`
- `android/build/`
- `android/app/build/`
- `.gradle/`
- `android/.gradle/`
- `android/.kotlin/`
- `android/debug.keystore`
- `task_plan.md`
- `findings.md`
- `progress.md`
- `HANDOFF.md`
- `handoff.md`
- `handoff-*.md`

Do not commit logs, bridge state, token files, APK outputs, or local planning files.
