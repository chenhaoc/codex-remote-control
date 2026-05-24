# Contributing

This is a small personal remote-control project. Contributions should keep the current shape: a thin Node.js bridge and a lightweight Android client.

## Development Rules

- Prefer focused changes over new frameworks or broad rewrites.
- Keep bridge protocol changes backward-compatible unless the change explicitly updates both bridge and Android client.
- Do not commit bridge state, tokens, logs, APKs, or local planning files.
- Do not hand-edit `src/generated/` unless regenerating protocol types is the task.
- Keep Android code in the existing page/controller/model/rendering split.

## Validation

For bridge or protocol changes:

```bash
npm test
```

For Android changes:

```bash
source ~/.zshrc && cd android && ./gradlew :app:compileDebugKotlin
```

For packaging or install changes:

```bash
source ~/.zshrc && cd android && ./gradlew assembleDebug
```

## Commit Style

Use conventional commits. Keep each commit scoped to one logical change.
