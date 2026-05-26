# Security Policy

## Supported Use

Codex Remote Control is intended for trusted private networks. The bridge can control local Codex sessions, read workspace files through `file.read`, and forward approvals that may allow commands or file changes.

Do not expose the bridge directly to the public internet.

## Deployment Guidance

- Bind the bridge to a private interface or private overlay network.
- Keep `~/.config/codex_remote_control/bridge-token.txt` secret.
- Rotate the token if a phone, log, or shell history may have leaked it.
- Prefer temporary locations for `--protocol-log`; it may contain prompts, paths, session ids, and protocol payloads.
- Keep bridge runtime files out of the repository.
- Treat the debug APK as a private development build.

## Reporting

This repository does not currently publish a dedicated vulnerability contact. If it is made public on GitHub, use GitHub issues for non-sensitive security hardening discussion and configure private vulnerability reporting before accepting sensitive reports.
