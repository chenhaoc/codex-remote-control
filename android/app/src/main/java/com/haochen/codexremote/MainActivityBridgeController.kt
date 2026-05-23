package com.haochen.codexremote

import android.content.ClipboardManager
import android.content.Context
import java.io.IOException
import java.net.URI
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun MainActivity.toggleConnection() {
        if (bridgeClient?.isOpen() == true) {
            disconnectBridge(detail = "连接已关闭", switchToConnectionPage = true)
            return
        }

        val url = try {
            buildBridgeUrl()
        } catch (error: Exception) {
            connectionDetail = "桥接地址无效: ${error.message}"
            showNotice(connectionDetail)
            return
        }

        connectToBridge(url)
    }

internal fun MainActivity.disconnectBridge(
        detail: String,
        switchToConnectionPage: Boolean,
    ) {
        stopSessionSyncLoop()
        cancelReconnectSchedule(resetAttempt = true)
        disconnectRequested = true
        bridgeClient?.close()
        bridgeClient = null
        connected = false
        activeTurnId = null
        sessionContentDirty = false
        assistantDeltaBuffers.clear()
        pendingApprovals.clear()
        pendingRequests.clear()
        currentBridgeUrl = null
        selectedWorkspace = null
        if (switchToConnectionPage) {
            currentPage = AppPage.Connection
        }
        connectionDetail = detail
    }

internal fun MainActivity.connectToBridge(url: String, isAutoReconnect: Boolean = false) {
        if (bridgeClient?.isOpen() == true && currentBridgeUrl == url) {
            currentPage = AppPage.Chat
            connectionDetail = "已连接到 ${connectionHistory.firstOrNull { it.url == url }?.displayName() ?: BridgeHistoryEntry.fromUrl(url)?.displayName() ?: url}"
            return
        }

        if (bridgeClient != null && currentBridgeUrl == url) {
            connectionDetail =
                if (isAutoReconnect || reconnectAttempt > 0) {
                    "自动重连中: $url"
                } else {
                    "连接中: $url"
                }
            return
        }

        if (bridgeClient != null) {
            disconnectBridge(detail = "正在切换连接…", switchToConnectionPage = false)
        }

        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
        if (!isAutoReconnect) {
            reconnectAttempt = 0
        }
        saveBridgeSettings(url)
        bridgeUrl = url
        switchLocalSessionCache(url)
        disconnectRequested = false
        bootSyncRequested = false
        connected = false
        currentBridgeUrl = url
        connectionDetail =
            if (isAutoReconnect || reconnectAttempt > 0) {
                "自动重连中${reconnectAttempt.takeIf { it > 0 }?.let { "($it)" }.orEmpty()}: $url"
            } else {
                "连接中: $url"
            }

        bridgeClient = BridgeClient(URI.create(url), object : BridgeClient.Listener {
            override fun onOpen() {
                mainHandler.post {
                    connected = true
                    reconnectAttempt = 0
                    cancelReconnectSchedule(resetAttempt = false)
                    rememberConnectionHistory(url)
                    currentPage = AppPage.Chat
                    connectionDetail = "已连接到 ${connectionHistory.firstOrNull { it.url == url }?.displayName() ?: BridgeHistoryEntry.fromUrl(url)?.displayName() ?: url}"
                }
            }

            override fun onText(text: String) {
                mainHandler.post { handleIncoming(text) }
            }

            override fun onDisconnected(reason: String, error: Throwable?) {
                mainHandler.post {
                    connected = false
                    activeTurnId = null
                    sessionContentDirty = false
                    assistantDeltaBuffers.clear()
                    pendingApprovals.clear()
                    pendingRequests.clear()
                    stopSessionSyncLoop()
                    bridgeClient = null
                    val detailSuffix = describeThrowable(error)
                    val targetUrl = currentBridgeUrl?.takeIf { it.isNotBlank() } ?: bridgeUrl.trim().takeIf { it.isNotBlank() }
                    if (shouldScheduleReconnect(targetUrl)) {
                        scheduleReconnect(
                            url = targetUrl.orEmpty(),
                            reason = reason,
                            error = error,
                        )
                    } else {
                        currentBridgeUrl = null
                        if (sessions.isEmpty()) {
                            selectedWorkspace = null
                            currentPage = AppPage.Connection
                        } else {
                            showCachedHistoryAfterConnectionFailure()
                        }
                        connectionDetail = buildReconnectExhaustedDetail(reason + detailSuffix)
                    }
                    disconnectRequested = false
                }
            }
        })

        try {
            bridgeClient?.connect()
        } catch (error: RuntimeException) {
            bridgeClient = null
            if (shouldScheduleReconnect(url)) {
                scheduleReconnect(url = url, reason = "连接失败", error = error)
            } else {
                currentBridgeUrl = null
                showCachedHistoryAfterConnectionFailure()
                connectionDetail = buildReconnectExhaustedDetail("连接失败: ${error.message}")
                showNotice(connectionDetail)
            }
        }
    }

internal fun MainActivity.applyHistoryConnection(entry: BridgeHistoryEntry) {
        bridgeUrl = entry.url
        switchLocalSessionCache(entry.url)
        connectionDetail = "已回填 ${entry.displayName()}，点击连接即可"
    }

internal fun MainActivity.connectToHistory(entry: BridgeHistoryEntry) {
        bridgeUrl = entry.url
        connectToBridge(entry.url)
    }

internal fun MainActivity.openRenameConnectionDialog(entry: BridgeHistoryEntry) {
        connectionRenameState =
            ConnectionRenameDialogState(
                url = entry.url,
                currentName = entry.name.orEmpty(),
            )
    }


internal fun MainActivity.buildBridgeUrl(): String {
        val raw = bridgeUrl.trim()
        if (raw.isEmpty()) {
            throw IllegalArgumentException("Bridge URL 不能为空")
        }

        val normalized = normalizeBridgeUrl(raw, requireToken = true)
        val uri = URI.create(normalized)
        val host = uri.host?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("URL host 不能为空")
        val port = if (uri.port >= 0) uri.port else 8787
        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val query = uri.rawQuery
        val tokenFromUrl = query?.let { extractTokenFromQuery(it) }
        val tokenFromPrefs = prefs.getString(KEY_TOKEN, "")?.trim().orEmpty()
        val token = tokenFromUrl ?: tokenFromPrefs
        if (token.isBlank()) {
            throw IllegalArgumentException("URL 里需要 token，例如 ?token=...")
        }

        val base = "${uri.scheme}://$host:$port$path"
        val finalQuery = when {
            query.isNullOrBlank() -> "token=${encodeToken(token)}"
            query.contains("token=") -> query
            else -> "$query&token=${encodeToken(token)}"
        }
        return "$base?$finalQuery"
    }

internal fun MainActivity.saveBridgeSettings(url: String) {
        val token = extractTokenFromUrl(url)
        val uri = URI.create(url)
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_SESSION, activeSessionId)
            .apply()
        if (!token.isNullOrBlank()) {
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
        if (uri.host != null) {
            prefs.edit().putString(KEY_HOST, uri.host).apply()
        }
        if (uri.port >= 0) {
            prefs.edit().putString(KEY_PORT, uri.port.toString()).apply()
        }
    }

internal fun MainActivity.rememberConnectionHistory(url: String) {
        val existingName = connectionHistory.firstOrNull { it.url == url }?.name
        val entry = BridgeHistoryEntry.fromUrl(url, System.currentTimeMillis(), existingName) ?: return
        replaceConnectionHistory(
            listOf(entry) + connectionHistory.filterNot { it.url == entry.url },
        )
        persistConnectionHistory()
    }

internal fun MainActivity.removeConnectionHistory(url: String) {
        replaceConnectionHistory(connectionHistory.filterNot { it.url == url })
        persistConnectionHistory()
    }

internal fun MainActivity.renameConnectionHistory(url: String, name: String) {
        replaceConnectionHistory(
            connectionHistory.map { entry ->
                if (entry.url == url) entry.withName(name) else entry
            },
        )
        persistConnectionHistory()
    }

internal fun MainActivity.replaceConnectionHistory(entries: List<BridgeHistoryEntry>) {
        val normalized = entries
            .sortedByDescending { it.lastUsedAt }
            .distinctBy { it.url }
            .take(MAX_CONNECTION_HISTORY)
        connectionHistory.clear()
        connectionHistory.addAll(normalized)
    }

internal fun MainActivity.loadConnectionHistory(fallbackUrl: String): List<BridgeHistoryEntry> {
        val raw = prefs.getString(KEY_CONNECTION_HISTORY, "")?.trim().orEmpty()
        val parsed = mutableListOf<BridgeHistoryEntry>()
        if (raw.isNotBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url", "").trim()
                    if (url.isBlank()) continue
                    BridgeHistoryEntry.fromUrl(
                        url = url,
                        lastUsedAt = item.optLong("lastUsedAt", 0L).takeIf { it > 0L } ?: System.currentTimeMillis(),
                        name = item.optString("name", "").trim(),
                    )?.let(parsed::add)
                }
            } catch (_: JSONException) {
                parsed.clear()
            }
        }
        if (parsed.isNotEmpty()) {
            return parsed
        }
        return fallbackUrl.takeIf { it.isNotBlank() }
            ?.let { BridgeHistoryEntry.fromUrl(it, System.currentTimeMillis()) }
            ?.let(::listOf)
            .orEmpty()
    }

internal fun MainActivity.persistConnectionHistory() {
        val array = JSONArray()
        connectionHistory.take(MAX_CONNECTION_HISTORY).forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("url", entry.url)
                    put("lastUsedAt", entry.lastUsedAt)
                    put("name", entry.name.orEmpty())
                },
            )
        }
        prefs.edit().putString(KEY_CONNECTION_HISTORY, array.toString()).apply()
    }

internal fun MainActivity.extractTokenFromUrl(url: String): String? {
        return try {
            extractTokenFromQuery(URI.create(url).rawQuery ?: "")
        } catch (_: Exception) {
            null
        }
    }

internal fun MainActivity.extractTokenFromQuery(query: String): String? {
        if (query.isBlank()) return null
        return query.split('&')
            .mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2 && parts[0] == "token") parts[1] else null
            }
            .firstOrNull()
    }

internal fun MainActivity.pasteBridgeUrlFromClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.firstText()?.trim()?.takeIf { it.isNotBlank() }
    }

internal fun MainActivity.pasteBridgeUrl() {
        val pasted = pasteBridgeUrlFromClipboard()
        if (pasted.isNullOrBlank()) {
            connectionDetail = "剪贴板里没有可粘贴的地址"
            return
        }
        bridgeUrl = try {
            normalizeBridgeUrl(pasted, requireToken = false)
        } catch (_: Exception) {
            pasted
        }
        connectionDetail = "已粘贴 Bridge URL，点击连接即可"
    }

internal fun MainActivity.ensureConnected(): Boolean {
        if (bridgeClient?.isOpen() != true) {
            connectionDetail =
                if (autoReconnectEnabled && currentBridgeUrl != null) {
                    "连接已断开，正在自动重连"
                } else {
                    "请先连接 Linux Bridge"
                }
            showNotice(connectionDetail)
            return false
        }
        return true
    }

internal fun MainActivity.sendRequest(type: String, payload: JSONObject, handler: ResponseHandler? = null): Boolean {
        if (!ensureConnected()) return false

        val id = "req_${UUID.randomUUID().toString().replace("-", "")}"
        val request = JSONObject()
        try {
            request.put("id", id)
            request.put("type", type)
            request.put("payload", payload)
        } catch (error: JSONException) {
            appendSystemNote("请求构造失败: ${error.message}")
            return false
        }

        if (handler != null) {
            pendingRequests[id] = handler
        }
        return try {
            bridgeClient?.sendText(request.toString())
            true
        } catch (error: IOException) {
            pendingRequests.remove(id)
            appendSystemNote("发送失败: ${error.message}")
            showNotice("发送失败: ${error.message}")
            false
        }
    }

internal fun MainActivity.loadBridgeUrl(): String {
        prefs.getString(KEY_URL, "")?.trim()?.takeIf { it.isNotBlank() }?.let {
            return try {
                normalizeBridgeUrl(it, requireToken = false)
            } catch (_: Exception) {
                it
            }
        }

        val host = prefs.getString(KEY_HOST, "")?.trim().orEmpty()
        val port = prefs.getString(KEY_PORT, "8787")?.trim().orEmpty()
        val token = prefs.getString(KEY_TOKEN, "")?.trim().orEmpty()
        if (host.isBlank()) return ""
        val tokenPart = if (token.isBlank()) "" else "?token=${encodeToken(token)}"
        return try {
            normalizeBridgeUrl("ws://$host:${if (port.isBlank()) "8787" else port}/$tokenPart", requireToken = false)
        } catch (_: Exception) {
            ""
        }
    }

internal fun MainActivity.updateAutoReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
        if (!enabled) {
            cancelReconnectSchedule(resetAttempt = true)
        }
    }

internal fun MainActivity.loadAutoReconnectMaxAttempts(): Int {
        return prefs.getInt(KEY_AUTO_RECONNECT_MAX_ATTEMPTS, 0).coerceAtLeast(0)
    }

internal fun MainActivity.updateAutoReconnectMaxAttempts(maxAttempts: Int) {
        autoReconnectMaxAttempts = maxAttempts.coerceAtLeast(0)
        prefs.edit().putInt(KEY_AUTO_RECONNECT_MAX_ATTEMPTS, autoReconnectMaxAttempts).apply()
        if (reconnectScheduled && !canScheduleReconnectAttempt(reconnectAttempt)) {
            cancelReconnectSchedule(resetAttempt = true)
            currentBridgeUrl = null
            selectedWorkspace = null
            currentPage = AppPage.Connection
            connectionDetail = "已达到最大自动重连次数"
            showNotice(connectionDetail)
        }
    }

internal fun MainActivity.resumeBridgeConnectionIfNeeded() {
        if (!autoReconnectEnabled || connected || bridgeClient != null) return
        val targetUrl = bridgeUrl.trim().takeIf { it.isNotBlank() } ?: return
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
        mainHandler.post {
            if (!connected && bridgeClient == null && autoReconnectEnabled) {
                connectToBridge(targetUrl, isAutoReconnect = true)
            }
        }
    }

internal fun MainActivity.scheduleReconnect(
        url: String,
        reason: String,
        error: Throwable?,
    ) {
        if (!autoReconnectEnabled) return
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return
        if (connected || bridgeClient != null || reconnectScheduled) return
        reconnectAttempt += 1
        val delayMs = reconnectDelayMillis(reconnectAttempt)
        reconnectScheduled = true
        currentBridgeUrl = normalizedUrl
        connectionDetail = buildString {
            append(reason.ifBlank { "连接已断开" })
            append(describeThrowable(error))
            append("，")
            append((delayMs / 1000L).coerceAtLeast(1L))
            append(" 秒后自动重连")
        }
        showNotice(connectionDetail)
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

internal fun MainActivity.cancelReconnectSchedule(resetAttempt: Boolean) {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
        if (resetAttempt) {
            reconnectAttempt = 0
        }
    }

internal fun MainActivity.reconnectDelayMillis(attempt: Int): Long {
        return when {
            attempt <= 1 -> 2_000L
            attempt == 2 -> 4_000L
            attempt == 3 -> 8_000L
            attempt == 4 -> 15_000L
            else -> 30_000L
        }
    }

internal fun MainActivity.shouldScheduleReconnect(targetUrl: String?): Boolean {
        if (!autoReconnectEnabled || targetUrl.isNullOrBlank()) return false
        return canScheduleReconnectAttempt(reconnectAttempt + 1)
    }

internal fun MainActivity.canScheduleReconnectAttempt(attempt: Int): Boolean {
        return autoReconnectMaxAttempts <= 0 || attempt <= autoReconnectMaxAttempts
    }

internal fun MainActivity.buildReconnectExhaustedDetail(baseDetail: String): String {
        if (autoReconnectEnabled && autoReconnectMaxAttempts > 0 && reconnectAttempt >= autoReconnectMaxAttempts) {
            return "$baseDetail，已达到最大自动重连次数"
        }
        return baseDetail
    }

internal fun MainActivity.normalizeBridgeUrl(input: String, requireToken: Boolean): String {
        val raw = input.trim()
        if (raw.isBlank()) {
            if (requireToken) throw IllegalArgumentException("Bridge URL 不能为空")
            return ""
        }

        val scheme = if (raw.contains("wss://")) "wss" else "ws"
        val withoutScheme = raw.replace(Regex("^(?:wss?://)+"), "")
        val beforeQuery = withoutScheme.substringBefore("?")
        val query = withoutScheme.substringAfter("?", "")
        val slashIndex = beforeQuery.indexOf('/')
        val authority = if (slashIndex >= 0) beforeQuery.take(slashIndex) else beforeQuery
        val path = if (slashIndex >= 0) beforeQuery.substring(slashIndex).ifBlank { "/" } else "/"
        val authorityParts = authority.split(':').filter { it.isNotBlank() }
        val host = authorityParts.firstOrNull()?.trim()
            ?: throw IllegalArgumentException("URL host 不能为空")
        val port = authorityParts.drop(1).firstOrNull { part -> part.all { it.isDigit() } }
            ?: prefs.getString(KEY_PORT, "8787")?.trim()?.takeIf { it.isNotBlank() }
            ?: "8787"
        val token = extractTokenFromQuery(query).orEmpty()
            .ifBlank { prefs.getString(KEY_TOKEN, "")?.trim().orEmpty() }

        if (requireToken && token.isBlank()) {
            throw IllegalArgumentException("URL 里需要 token，例如 ?token=...")
        }

        val queryWithoutToken = query
            .split('&')
            .filter { it.isNotBlank() && !it.startsWith("token=") }
            .joinToString("&")
        val finalQuery = listOfNotNull(
            queryWithoutToken.takeIf { it.isNotBlank() },
            token.takeIf { it.isNotBlank() }?.let { "token=${encodeToken(it)}" },
        ).joinToString("&")

        return buildString {
            append("$scheme://$host:$port")
            append(path.ifBlank { "/" })
            if (finalQuery.isNotBlank()) {
                append('?')
                append(finalQuery)
            }
        }
    }
