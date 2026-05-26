# 审批实现与排障链路

本文记录 APK 审批弹窗、bridge pending approval 状态、以及真实 Codex app-server JSON-RPC 审批响应之间的实现约定。

## 核心链路

审批请求由 Codex app-server 通过 JSON-RPC request 发给 bridge。bridge 会把请求转换成 APK 可消费的事件，并把待审批项记录到会话状态里。

```text
Codex app-server
  -> bridge request: item/commandExecution/requestApproval
  -> bridge store: pending_approvals[request_id]
  -> APK event: item/commandExecution/requestApproval
  -> APK approval.response
  -> bridge respondRequest(original_backend_request_id)
  -> Codex app-server: serverRequest/resolved
  -> bridge event: turn/completed
  -> APK session.sync/session.content clears active turn
```

## Request Id 规则

Codex JSON-RPC request id 可能是数字，也可能是字符串。APK 和 bridge WebSocket 协议为了稳定序列化，会把对外 `request_id` 当字符串使用；但 bridge 回 Codex 时必须使用 Codex 原始 id 类型。

当前约定：

- `CodexBackend` 收到 Codex request 时保留原始 `id`，不强制转字符串。
- bridge 广播给 APK 时使用 `String(id)` 作为 `request_id`。
- `StateStore.pending_approvals` 以字符串 key 存储，兼容 APK 回传。
- pending approval 记录保留 `backend_request_id`，用于回 Codex。
- APK 回 `approval.response` 后，bridge 根据 `session_id + request_id` 找回 `backend_request_id`，再调用 `backend.respondRequest()`。

这个规则避免了数字 id 被字符串化后 Codex 不认响应的问题。

## APK 侧

APK 审批 UI 在聊天页弹出 `ApprovalDialog`。按钮按纵向排列，每个 action 独占一行，避免窄屏下第三个按钮被挤出可见区域。

审批点击后：

1. `sendApproval()` 构造 `approval.response` payload。
2. payload 保留 action 的完整 `responsePayload`，包括复杂 decision 对象。
3. bridge 返回 `{ ok: true }` 后，本地移除 pending approval 并显示“审批结果已提交”。
4. 后续是否结束回合，以 `turn/completed` 或 `session.sync/session.content` 的 `active_turns` 为准。

APK 审批调试日志默认关闭：

```bash
adb shell setprop log.tag.CodexRemoteApproval DEBUG
adb logcat -s CodexRemoteApproval:D
```

关闭：

```bash
adb shell setprop log.tag.CodexRemoteApproval INFO
```

日志使用 `Log.isLoggable("CodexRemoteApproval", Log.DEBUG)` 保护，默认不会构造审批 payload 字符串。

## Bridge 侧

bridge 处理 `approval.response` 时先记录摘要日志，然后把结果转发给 Codex：

1. 读取客户端 `request_id` 和 `session_id`。
2. 从 store 读取 pending approval。
3. 使用 pending approval 里的 `backend_request_id` 作为 Codex JSON-RPC response id。
4. 调用 `backend.respondRequest(backendRequestId, result, error)`。
5. 标记 pending approval resolved。
6. 广播 `session/changed`，让 APK 触发同步。

如果客户端没有传 `result`，bridge 会从 `decision` 编译出 `{ decision }`。如果客户端传的是完整复杂 decision，bridge 会原样包进 result，避免丢失 Codex 需要的执行策略修正。

## 日志策略

真实日志文件不应提交。排障日志可能包含会话 id、turn id、用户文本、命令、文件路径和完整 Codex 协议帧。

保留的诊断开关：

- Android `CodexRemoteApproval`: 默认关闭，通过 logcat tag 临时打开。
- bridge `--sync-log PATH`: 默认关闭，记录 WebSocket 消息摘要、审批摘要、`session.sync` 状态。
- bridge `--protocol-log PATH`: 默认关闭，记录 bridge 与 Codex app-server 的原始 JSON-RPC 行。

`--protocol-log` 是高敏感日志，只适合一次性定位真实 Codex 协议问题。它可能包含完整会话快照和消息内容，建议写到 `/tmp` 或 `/private/tmp`，复现结束后删除。

示例：

```bash
node src/index.mjs \
  --backend codex \
  --listen 0.0.0.0:8787 \
  --state ~/.config/codex_remote_control/bridge-state.json \
  --token-file ~/.config/codex_remote_control/bridge-token.txt \
  --sync-log /private/tmp/codex-approval-debug/sync.log \
  --protocol-log /private/tmp/codex-approval-debug/protocol.log
```

## 排障检查点

判断审批是否闭环时，按以下顺序看日志：

1. `approval.request.received`: bridge 收到 Codex request，并记录 `backend_request_id` 及其类型。
2. APK `sendApproval requestId=...`: 用户点击了审批 action。
3. `approval.response.received`: bridge 收到 APK response。
4. `approval.response.forwarded`: bridge 已用原始 backend id 回 Codex。
5. `serverRequest/resolved`: Codex 认可该审批响应。
6. `turn/completed`: Codex 完成本轮。
7. `session.sync.response active_turns=0 pending_approvals=0`: APK 同步后应清掉进行中状态和审批状态。

若第 4 步存在但第 5 步不存在，优先检查 Codex JSON-RPC id 类型和 result 结构。若第 5、6 步存在但 APK 仍像卡住，优先检查 APK 是否收到 `session.sync`，以及 `active_turns` 是否明确变为 0。
