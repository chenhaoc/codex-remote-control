# Android APK

这是自用的 Android 客户端最小实现。工程现在是标准 Gradle Android 项目，使用 Kotlin + Jetpack Compose + OkHttp WebSocket。

## 功能

- 配置 Linux bridge 的 host / port / token
- WebSocket 连接和断线提示
- 会话列表与会话切换
- 发送消息到 `turn.send`
- 流式接收 `item/agentMessage/delta`
- 审批卡片与 `approval.response`

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
- 暂时没有引入 Room / Hilt，避免自用版本过早变成大工程。
- 网络层使用 OkHttp WebSocket，不再维护手写 WebSocket 协议。
