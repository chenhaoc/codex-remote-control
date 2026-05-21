# Android APK

这是自用的 Android 客户端最小实现。工程现在是标准 Gradle Android 项目，使用 Kotlin + Jetpack Compose + OkHttp WebSocket。

## 功能

- 配置 Linux bridge 的 host / port / token
- WebSocket 连接和断线提示
- 会话列表与会话切换
- 发送消息到 `turn.send`
- 流式接收 `item/agentMessage/delta`
- 审批卡片与 `approval.response`
- 聊天 WebView 基于 `conversationItems` 做增量 DOM patch
- 会话恢复时按快照恢复聊天滚动位置
- 键盘弹出和视口变化时尽量保持当前阅读位置稳定

## 构建

```bash
bash android/scripts/build-apk.sh
```

默认产物：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 说明

- 可以直接用 Android Studio 打开 `android/`。
- 已引入 Compose 处理真实移动端布局、输入法和消息界面体验。
- 聊天历史仍以 `session.content` 快照和 Activity 内 `conversationItems` 为准，WebView 只负责渲染与局部更新。
- 暂时没有引入 Room / Hilt，避免自用版本过早变成大工程。
- 网络层使用 OkHttp WebSocket，不再维护手写 WebSocket 协议。
