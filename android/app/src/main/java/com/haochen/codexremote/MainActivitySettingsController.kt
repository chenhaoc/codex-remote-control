package com.haochen.codexremote

internal fun MainActivity.openSettingsPage() {
    currentPage = AppPage.Settings
}

internal fun MainActivity.loadStartupPagePreference(): AppPage {
    return appPageFromPreference(prefs.getString(KEY_STARTUP_PAGE, null))
}

internal fun MainActivity.updateStartupPagePreference(page: AppPage) {
    startupPagePreference = page
    prefs.edit().putString(KEY_STARTUP_PAGE, page.preferenceValue()).apply()
}

internal fun MainActivity.appVersionLabel(): String {
    return "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_COMMIT_SHORT}"
}

internal fun MainActivity.startupPageMenuOptions(): List<SelectionMenuOption> {
    return listOf(
        SelectionMenuOption(
            value = AppPage.Chat.preferenceValue(),
            label = "聊天",
            supporting = "启动后直接进入当前会话页面。",
        ),
        SelectionMenuOption(
            value = AppPage.Connection.preferenceValue(),
            label = "连接设置",
            supporting = "先看到 bridge 地址和连接状态。",
        ),
        SelectionMenuOption(
            value = AppPage.Settings.preferenceValue(),
            label = "设置",
            supporting = "先进入本地偏好设置页面。",
        ),
    )
}

internal fun MainActivity.autoReconnectAttemptMenuOptions(): List<SelectionMenuOption> {
    return listOf(
        SelectionMenuOption(
            value = "0",
            label = "不限制",
            supporting = "断线后持续重试，间隔逐步拉长。",
        ),
        SelectionMenuOption(
            value = "3",
            label = "3 次",
            supporting = "最多自动重连 3 次。",
        ),
        SelectionMenuOption(
            value = "5",
            label = "5 次",
            supporting = "最多自动重连 5 次。",
        ),
        SelectionMenuOption(
            value = "10",
            label = "10 次",
            supporting = "最多自动重连 10 次。",
        ),
    )
}

internal fun MainActivity.autoReconnectMaxAttemptsLabel(): String {
    return if (autoReconnectMaxAttempts <= 0) "不限制" else "${autoReconnectMaxAttempts} 次"
}

internal fun MainActivity.autoReconnectMaxAttemptsDescription(): String {
    return if (autoReconnectMaxAttempts <= 0) {
        "断线后会一直自动重试，重连间隔会逐步增加。"
    } else {
        "每次断线后最多自动重试 ${autoReconnectMaxAttempts} 次。"
    }
}

internal fun AppPage.preferenceValue(): String {
    return name.lowercase()
}

internal fun appPageFromPreference(value: String?): AppPage {
    return when (value?.trim()?.lowercase()) {
        AppPage.Connection.preferenceValue() -> AppPage.Connection
        AppPage.Settings.preferenceValue() -> AppPage.Settings
        else -> AppPage.Chat
    }
}
