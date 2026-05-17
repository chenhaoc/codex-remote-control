# Codex Remote Control 交接文档

更新时间：2026-05-17

## 当前结论

这个项目仍然坚持最初判断：因为已经有贝锐蒲公英异地组网，最合适的是自写一个薄 Linux Bridge + Android 客户端，不引入 `ccpocket` 那套 relay/多平台重架构。

Android 端已经从原生 View 迁移到 Kotlin + Jetpack Compose，主要是为了解决真实手机上的布局和输入法遮挡问题。网络层仍然是 OkHttp WebSocket，Linux bridge 不需要改大架构。

## 项目位置

```text
/Users/hao.chen/工作文档/Work/codex-remote-control
```

主要文件：

```text
src/bridge-server.mjs
src/backends/codex.mjs
src/backends/mock.mjs
android/app/src/main/java/com/haochen/codexremote/MainActivity.kt
android/app/src/main/java/com/haochen/codexremote/BridgeClient.java
android/app/build.gradle.kts
android/build.gradle.kts
android/gradle.properties
README.md
android/README.md
codex-remote-control-plan.md
```

## 已完成状态

- Linux bridge 已实现并通过 `npm test`。
- Android 已改为标准 Gradle 工程。
- Android UI 已迁移到 Kotlin + Jetpack Compose。
- Android WebSocket 使用 OkHttp，不再手写 WebSocket 协议。
- 连接页已支持完整 `ws://host:port/?token=...` URL。
- 修复了历史配置导致 URL 被拼成 `ws://ws://...:8787:8787` 的问题。
- 会话页能拉取真实 Codex 历史会话。
- 消息页能显示当前会话、用户消息和消息输入区。
- 输入法弹出后，composer 会抬到键盘上方，并隐藏会话历史区，已在真机截图验证。
- 文档已从“Java 原生 View/不引入 Compose”同步为当前 Compose 方案。

## 当前验证记录

Linux 测试通过：

```bash
npm test
```

Android 构建通过：

```bash
cd android
./gradlew assembleDebug
```

最近一次构建产物：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

真机无线调试设备：

```text
192.168.31.230:37681
```

安装命令：

```bash
adb -s 192.168.31.230:37681 install -r -g android/app/build/outputs/apk/debug/app-debug.apk
```

启动命令：

```bash
adb -s 192.168.31.230:37681 shell am start -n com.haochen.codexremote/.MainActivity -W
```

截图命令：

```bash
adb -s 192.168.31.230:37681 exec-out screencap -p > /tmp/codex-remote.png
```

## Bridge 状态

上一次检查时，Linux bridge 正在监听：

```text
0.0.0.0:8787
```

token 文件：

```text
data/bridge-token.txt
```

手机可用 URL 格式：

```text
ws://192.168.31.206:8787/?token=<data/bridge-token.txt 中的 token>
```

如果 bridge 不在了，重新启动：

```bash
npm run bridge
```

如果 Codex 需要代理：

```bash
source ~/.zshrc && proxy_on
npm run bridge
```

## 重要说明

最后一次代码构建成功后，没有再覆盖安装最新 APK。原因是最后只做了小修：

- 修复 `activeTurnId` 的 Kotlin nullable warning。
- 更新文档。

如果继续调试手机，请先重新安装最新 APK：

```bash
adb -s 192.168.31.230:37681 install -r -g android/app/build/outputs/apk/debug/app-debug.apk
```

如果想完全重来并清掉旧配置：

```bash
adb -s 192.168.31.230:37681 shell pm clear com.haochen.codexremote
adb -s 192.168.31.230:37681 install -r -g android/app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.31.230:37681 shell am start -n com.haochen.codexremote/.MainActivity -W
```

## 已知问题和下一步

- 会话历史目前仍然是 MVP 列表，能用但还不够像成熟远控 App。
- 消息流只做了基本 bubble/system note/approval card，后续要补更完整的 Codex event 渲染。
- 审批卡片结构已存在，但真实复杂审批类型还需要继续测。
- 没有本地持久化消息数据库，历史主要依赖 bridge 的 `session.sync`。
- 没有前台服务和自动重连策略，当前更像“打开 App 连接使用”的自用 MVP。
- 还没有文件上传、diff 视图、命令输出折叠、通知推送。

推荐下一轮优先级：

1. 补自动重连和连接状态 toast/snackbar。
2. 优化会话页：会话列表改为更紧凑的顶部 sheet 或单独页面。
3. 补真实审批事件测试，确保 accept/decline/cancel 都能回到 Codex。
4. 增强消息渲染：命令输出、diff、系统事件分组折叠。
5. 视情况加前台服务，解决锁屏/后台断线问题。

## 继续时的最短路径

```bash
cd /Users/hao.chen/工作文档/Work/codex-remote-control
npm test
cd android
./gradlew assembleDebug
adb -s 192.168.31.230:37681 install -r -g app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.31.230:37681 shell am start -n com.haochen.codexremote/.MainActivity -W
```
