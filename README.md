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

- `model.list`
- `session.list`
- `session.start`
- `session.resume`
- `session.content`
- `turn.send`
- `turn.interrupt`
- `approval.response`
- `session.sync`
- `file.read`

## Session Data

bridge 会同时维护两类会话数据：

- `session.list` 返回的轻量摘要，适合列表页和快速切换
- `session.content` 返回的完整内容快照，包含 `session`、`entries` 和 `pending_approvals`

当前 `session.content` 还承担 Android 客户端的历史恢复与详情展示职责：

- 对 brand-new session，如果只有 `thread/started` 元数据且还没有实际 turn，不会过早触发 `thread.read`
- 如果 thread 还没有内联 turns，会优先使用 bridge 已存 events 和 rollout 文件补历史内容
- 历史会话的 metadata 会先从本地 rollout / events 做只读回填，不触发新的用户消息或远端续跑

本地 metadata backfill 目前会尝试恢复：

- `model`
- `reasoningEffort`
- `approvalPolicy`
- `permissions`
- `sandbox`
- `contextWindow`
- `lastTokenUsage`
- `totalTokenUsage`

数据来源仅限：

- rollout 里的 `session_meta`
- rollout 里的 `turn_context`
- rollout 里的 `event_msg.token_count`
- bridge state 已存的 `thread/tokenUsage/updated` 事件

如果这些本地来源本身不存在，对应字段仍会显示为“未提供”。

## File Reading

bridge 提供了一个轻量 `file.read` 能力，供 Android 代码浏览器按会话目录读取文件：

- 绝对路径会直接读取
- 相对路径会基于当前 session 的 `cwd` 解析
- 响应会返回 `resolved_path`、`content`、`truncated` 和 `bytes`

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

当前 Android 客户端已经支持：

- 会话列表、会话切换和新会话创建
- `model.list` 拉取模型列表
- 新会话时配置模型、思考强度、审批策略和沙箱
- `session.content` 快照恢复聊天历史和待审批项
- 基于 `conversationItems` 的聊天 WebView 增量渲染，避免简单消息追加时整页重载
- 会话切换和历史恢复时按快照回填滚动位置
- 输入法弹出或视口高度变化时保持当前阅读锚点，避免消息区被顶跳
- `file.read` 驱动的代码/补丁浏览
- 会话信息页展示模型、思考强度、审批策略、沙箱、上下文窗口和 token 统计
