# Codex Remote Control

自用的远程 Codex 控制项目：Linux 端跑一个薄桥，Android 端通过蒲公英私网直连，发送消息、接收流式输出并处理审批。

## Run

```bash
npm install
npm run bridge
```

如果 Codex 访问网络需要代理，先在当前 shell 执行：

```bash
source ~/.zshrc && proxy_on
npm run bridge
```

启动脚本会打印可从手机粘贴的 `ws://.../?token=...` 地址。

## Backends

- `mock`: 本地假后端，适合开发和测试。
- `codex`: 拉起真实 `codex app-server`，把 JSON 消息桥接过去。

## HTTP

- `GET /healthz`
- `GET /v1/sessions`

## WebSocket

连接时需要带 token：

```text
ws://HOST:PORT/?token=YOUR_TOKEN
```

首条消息会是：

```json
{ "type": "hello", "ok": true, "payload": { ... } }
```

## Client Messages

- `session.list`
- `session.start`
- `session.resume`
- `turn.send`
- `turn.interrupt`
- `approval.response`
- `session.sync`

## Events

后台消息会原样透传成 `event` 消息，`event` 字段就是 Codex 原始 method，例如：

- `thread/started`
- `turn/started`
- `item/agentMessage/delta`
- `item/commandExecution/requestApproval`
- `turn/completed`

## Dev Notes

- 默认状态文件在 `data/bridge-state.json`
- 默认 token 文件在 `data/bridge-token.txt`
- `codex` 模式会调用本机 `codex app-server --listen stdio://`

## Android APK

Android 客户端在 `android/` 下，是标准 Gradle 项目：

```bash
bash android/scripts/build-apk.sh
```

产物会输出到：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Android 侧使用 Kotlin + Jetpack Compose + OkHttp WebSocket。UI 采用 Compose 是为了解决键盘遮挡、会话列表和消息流布局这类真实体验问题；仍暂时不引入 Room / Hilt，以保持自用最小工程。
