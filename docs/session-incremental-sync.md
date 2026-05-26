# 会话增量快照同步

本文记录 APK 会话增量快照同步的当前实现。目标是降低 5s 轮询时的桥端通信量和 APK 端处理成本，同时保持聊天历史、审批状态和 live status 的行为与全量快照一致。

## 核心原则

- `session.content` 仍是完整会话快照接口，适合首次进入、会话切换、手动重建历史和增量失败 fallback。
- `session.sync` 只返回发生变化的 turn 快照片段，但 `entries` 结构必须与 `session.content.entries` 完全一致。
- APK 端不重放 `session.sync` 里的事件，也不从 delta 或 `item/completed` 合成最终助手消息。
- `conversationItems` 仍是聊天 UI 的唯一 Kotlin 数据源，WebView 只消费它。
- 增量合并后必须构造出等价于完整快照的本地 payload，再走统一的 snapshot 消费链路。

## 协议

`session.sync` request:

```json
{
  "type": "session.sync",
  "payload": {
    "session_id": "SESSION_ID",
    "since_seq": 123,
    "incremental": true
  }
}
```

`session.sync` response:

```json
{
  "session_id": "SESSION_ID",
  "thread_id": "THREAD_ID",
  "last_seq": 130,
  "entries": [],
  "active_turns": [],
  "pending_approvals": [],
  "changed_turn_ids": [],
  "needs_full_sync": false,
  "fallback_reason": ""
}
```

字段语义：

- `last_seq`: bridge 当前 store cursor。APK 只有成功应用本次响应后才能推进本地 cursor。
- `changed_turn_ids`: 本次 `entries` 完整覆盖的 turn id 集合。
- `entries`: 与 `session.content.entries` 同构的 snapshot entries，只包含 changed turns。
- `active_turns`: 当前仍在进行中的 turns。若权威 snapshot 已包含终态 `turn_status`，对应 turn 不应再出现在这里。
- `pending_approvals`: 当前待审批项。
- `needs_full_sync`: 增量无法安全应用时置为 `true`，APK 必须改拉 `session.content`。
- `fallback_reason`: fallback 的原因，便于日志定位。

## Bridge 实现

入口在 `src/bridge-server.mjs` 的 `session.sync` 分支。bridge 会归一化 `since_seq`，然后调用增量构建流程。

增量构建主要步骤：

1. 根据 store 中 `seq > since_seq` 的相关事件计算 changed turns。
2. 如果本地事件不足，调用历史 hydrate，从权威 thread/items 补齐尾部 turn 的新内容。
3. 对 changed turns 读取权威 items，优先使用已有 full turn 或 `listTurnItems`，再转换为 snapshot entries。
4. 对终态 turn 追加 `turn_status` entry，包括 `completed`、`failed`、`interrupted`，跳过 `inProgress`。
5. 用 entries 中的终态 `turn_status` 校正 `active_turns`，避免 store 漏掉 `turn/completed` 时 live status 卡住。
6. 如果某个 turn 曾出现 assistant delta 或最终 assistant 事件，但权威 items 里拿不到最终 `agentMessage`，返回 `needs_full_sync=true`，不使用 delta 合成最终消息。

changed turn 粒度是 turn 级别，不追求 item 级精确替换。这样合并简单，也能保证同一 turn 的用户消息、助手消息、文件修改和终态状态一起对齐。

### 用户输入临时回显与绑定

`turn.send` 会先写入并广播一条 `turn/input`，用于 APK 立即显示用户输入。此时 Codex 可能尚未返回真实 turn id，因此该事件允许 `turn_id` 为空，但必须带稳定的输入 item id，例如 `input_${request_id}`。

当 `startTurn()` 返回真实 turn id 后，bridge 必须再记录并广播同一 `item.id` 的绑定版 `turn/input`：

- `turn_id` 更新为真实 turn id。
- `payload.itemId` / `payload.item_id` 保持不变。
- `replaces_seq` 指向被绑定的临时输入 seq，便于日志定位和实时客户端识别这是同一输入的修正版。
- 绑定版事件需要分配新的 `seq`，这样只靠轮询的客户端也能在下一次 `session.sync` 中看到变化。

因此，`session.content` 和 `session.sync` 构造 snapshot entries 时必须按用户输入 item id 去重，不能把同一个 `input_${request_id}` 同时作为空 `turn_id` 临时项和真实 turn 项暴露成两条消息。

## APK 实现

设置页提供“增量同步”开关，默认开启。轮询入口通过 `requestSessionRefresh(sessionId)` 分流：

- 开启增量时调用 `requestSessionSync(sessionId)`。
- 关闭增量时调用 `requestSessionContent(sessionId)`。
- cursor 未 ready 时先走完整 `session.content`。

`requestSessionSync()` 收到响应后：

1. `needs_full_sync=true` 时直接 fallback 到 `requestSessionContent()`，不推进 cursor。
2. `applySessionSyncSnapshot()` 校验 `changed_turn_ids`、entry turn id、item id 冲突和解析结果。
3. 用 changed turns 删除本地旧 items，再追加本次增量 items。
4. 更新 pending approvals 并重建本地索引。
5. 推进 `lastSyncedSeq`。
6. 通过 `buildCurrentSessionCachePayload()` 合并出当前完整 cache payload。
7. 持久化 cache，并调用 `updateLiveTurnStatusFromSnapshot(mergedPayload)`。

用户输入气泡会保留 snapshot item id 作为稳定 `sourceItemId`。如果本地已有空 `turnKey` 的临时用户输入，而本次增量带来了相同 `sourceItemId` 且绑定到真实 turn 的用户输入，APK 必须删除临时气泡和 cache 中对应临时 entry，再追加真实 turn 的 snapshot item。这个规则只用于同一用户输入的身份替换，不改变 turn 级覆盖语义。

live status 没有增量专用消费逻辑。全量和增量合并后都进入 `updateLiveTurnStatusFromSnapshot()`，由 snapshot 的 `active_turns`、`session.thread` 和 `entries` 统一推断状态。若 `active_turns` 明确为空，会清空 `activeTurnId` 和 live status，避免停在“正在同步”或“正在输出”。

## Cursor 与 Fallback

cursor 规则：

- 完整 `session.content` 成功应用后，使用响应里的 `last_seq` 初始化 cursor。
- 增量 `session.sync` 成功合并后，推进到响应里的 `last_seq`。
- `needs_full_sync=true`、解析失败、turn id 不匹配、item id 冲突时，不推进 cursor。
- 空增量可以推进到 bridge 返回的 `last_seq`，前提是响应成功且不要求 full sync。

常见 fallback 场景：

- changed events 没有可用 turn id。
- 权威 turn/items 无法读取。
- assistant 最终消息不权威，无法安全替代 delta。
- APK 本地合并校验失败。

## 日志

APK 端同步调试日志默认关闭，不进入设置页，也不写文件。需要定位问题时通过 logcat tag 临时打开：

```bash
adb shell setprop log.tag.CodexRemoteSync DEBUG
adb logcat -s CodexRemoteSync:D
```

关闭：

```bash
adb shell setprop log.tag.CodexRemoteSync INFO
```

代码侧使用 `Log.isLoggable("CodexRemoteSync", Log.DEBUG)` 保护日志。默认运行时不会构造同步调试字符串，`logSessionSyncEntries()` 也会直接返回，避免为了日志遍历快照 entries。

开启后，APK 会记录 `session.content` / `session.sync` 的请求、响应、fallback、cursor 推进和合并结果。

Bridge 端同步文件日志默认关闭，避免正常运行持续写文件。需要定位问题时启用：

```bash
CODEX_REMOTE_SYNC_LOG=1 npm run bridge
```

默认日志路径是 `~/.config/codex_remote_control/bridge-sync.log`，也可以自定义：

```bash
CODEX_REMOTE_SYNC_LOG=1 CODEX_REMOTE_SYNC_LOG_FILE=/tmp/bridge-sync.log npm run bridge
```

也可以直接传 CLI 参数：

```bash
npm start -- --sync-log ~/.config/codex_remote_control/bridge-sync.log
```

日志时间使用本地时区 ISO 格式，例如 `2026-05-23T21:12:43.559+08:00`。

## 验证

Bridge:

```bash
npm test
```

Android Kotlin:

```bash
source ~/.zshrc && cd android && ./gradlew :app:compileDebugKotlin
```

重点测试覆盖：

- 增量只返回 changed turns，不混入旧历史。
- tail turn 只存在于权威快照时可以被 hydrate 到增量。
- 后续出现的 assistant items 可以按 turn 重新同步。
- assistant delta 不会作为最终消息暴露。
- 缺少权威最终 assistant 消息时 fallback 到全量。
- 全量和增量都包含终态 `turn_status`，并能清掉已结束 turn 的 `active_turns`。
- 临时 `turn/input` 与绑定真实 turn 的 `turn/input` 使用同一 item id，增量合并后不会显示两条用户输入。

## 注意事项

- 增量粒度是 turn 级。changed turn 会整体替换，不做 item 级 patch；如果单个 turn 很大，本次增量仍可能较大。
- 增量主要面向尾部和活跃 turn。历史中间段出现异常修正时，应优先走 `session.content` 完整快照。
- `turn_status` 可能是 status-only entry。它通常不渲染成可见消息，但必须参与 cache 合并和 live status 清理。
- 响应里存在 `active_turns` 时以它为准。空数组表示 bridge 明确判断当前没有活跃 turn。
- cursor 不能提前推进。只有完整快照成功应用，或增量快照成功合并后，才能更新 `lastSyncedSeq`。
- `changed_turn_ids=[]` 不代表响应完全无效。pending approvals、`active_turns` 或 `last_seq` 仍可能需要消费。
- bridge 和 APK 需要使用同一版协议。当前 `session.sync` 返回 snapshot entries，不再返回可重放 events。
- 用户输入的稳定身份是 snapshot `item.id`，不是 `turn_id`。`turn_id` 可以从空值补齐为真实值，但同一输入的 `item.id` 必须保持不变。
- fallback 是正常保护路径。权威 items 不完整、合并冲突、cursor 不可信时，应退回 `session.content`，不应强行应用增量。
- `completed` 的 `turn_status` 主要用于状态同步，通常不会显示系统消息；失败、被中断且带 error 的状态才可能显示提示。
- APK 同步调试日志默认关闭。需要定位问题时用 `adb shell setprop log.tag.CodexRemoteSync DEBUG` 临时开启，结束后改回 `INFO`。
- Bridge 文件日志默认关闭。开启 `CODEX_REMOTE_SYNC_LOG=1` 仅用于定位问题，日志可能包含 session id、turn id、文件路径和数量统计。
