package com.haochen.codexremote

import org.json.JSONException
import org.json.JSONObject

internal fun MainActivity.handleIncoming(text: String) {
        val message = try {
            JSONObject(text)
        } catch (error: JSONException) {
            appendSystemNote("收到非 JSON 消息: $text")
            return
        }

        when (message.optString("type", "")) {
            "hello" -> {
                connectionDetail = "Bridge 已就绪，正在拉取会话列表"
                currentPage = AppPage.Chat
                requestSessionList()
                requestModelList()
            }

            "event" -> handleEvent(message)
            "response", "pong" -> handleResponse(message)
            "error" -> appendSystemNote("错误: ${safeJson(message.optJSONObject("error"))}")
        }
    }

internal fun MainActivity.handleResponse(message: JSONObject) {
        val id = message.optString("id", "")
        val callback = pendingRequests.remove(id)
        val ok = message.optBoolean("ok", true)
        if (callback != null) {
            if (!ok) {
                val errorText = extractErrorText(message.optJSONObject("error")).ifBlank { formatJson(message.optJSONObject("error")) }
                callback.onError(errorText)
                if (!callback.suppressDefaultErrorUi()) {
                    appendSystemNote("请求失败: $errorText")
                    showNotice("请求失败: $errorText")
                }
                return
            }
            try {
                callback.onResponse(message)
            } catch (error: JSONException) {
                appendSystemNote("解析响应失败: ${error.message}")
            }
            return
        }
        if (!ok) {
            val errorText = extractErrorText(message.optJSONObject("error")).ifBlank { formatJson(message.optJSONObject("error")) }
            appendSystemNote("请求失败: $errorText")
            showNotice("请求失败: $errorText")
        }
    }

internal fun MainActivity.handleEvent(message: JSONObject) {
        if (shouldIgnoreEvent(message)) return

        val eventName = message.optString("event", "")
        val payload = message.optJSONObject("payload") ?: JSONObject()

        if (eventName == "thread/started") {
            val info = SessionInfo.fromThread(payload)
            if (info != null) {
                upsertSession(info)
                if (activeSessionId == null) {
                    selectSession(info.sessionId, syncHistory = false)
                }
            }
            return
        }

        if (payloadCarriesSessionMetadata(payload)) {
            SessionInfo.fromThread(payload)?.let(::upsertSession)
        }

        if (eventName == "session/changed") {
            val sessionId = message.optString("session_id", "")
            if (sessionId.isNotBlank() && sessionId == activeSessionId) {
                if (syncInFlight) {
                    sessionContentDirty = true
                } else {
                    requestSessionContent(sessionId)
                }
            }
            return
        }

        val sessionId = message.optString("session_id", "")
        if (sessionId.isNotEmpty() && activeSessionId != null && sessionId != activeSessionId) {
            return
        }

        when (eventName) {
            "turn/input" -> {
                handleTurnInput(message, payload)
            }

            "turn/started" -> {
                activeTurnId = firstNonEmpty(
                    message.optString("turn_id", ""),
                    payload.optJSONObject("turn")?.optString("id", "") ?: "",
                )
                updateLiveTurnStatus("Codex 正在响应…")
            }

            "item/agentMessage/delta" -> {
                updateLiveTurnStatus("Codex 正在输出…")
                handleAssistantDelta(message, payload)
            }
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/permissions/requestApproval",
            "applyPatchApproval",
            "execCommandApproval" -> {
                updateLiveTurnStatus("等待审批…")
                handleApprovalRequest(eventName, message, payload)
            }
            "item/fileChange/patchUpdated" -> {
                updateLiveTurnStatus("Codex 正在修改文件…")
                handleFileChangePatchUpdated(message, payload)
            }
            "turn/diff/updated" -> {
                updateLiveTurnStatus("Codex 正在修改文件…")
                handleTurnDiffUpdated(message, payload)
            }
            "item/started", "item/completed" -> {
                val item = payload.optJSONObject("item")
                updateLiveTurnStatusFromItem(eventName, item)
                if (item != null) {
                    handleThreadItemEvent(eventName, payload)
                }
            }
            "warning" -> updateLiveTurnStatusFromWarning(payload)
            "error" -> updateLiveTurnStatusFromError(payload)
            "thread/status/changed" -> updateLiveTurnStatusFromThreadStatus(payload.optJSONObject("status"))
            "turn/completed" -> {
                removeCompletedTurnApprovalItems(message.optString("turn_id", ""))
                if (activeTurnId != null && activeTurnId == message.optString("turn_id", "")) {
                    activeTurnId = null
                }
                updateLiveTurnStatus(null)
            }

            else -> Unit
        }
    }

internal fun MainActivity.handleTurnInput(message: JSONObject, payload: JSONObject) {
        val text = firstNonEmpty(
            payload.optString("text", ""),
            payload.optString("message", ""),
        )
        val turnKey = firstNonEmpty(
            message.optString("turn_id", ""),
            payload.optString("turn_id", ""),
            message.optString("request_id", ""),
            payload.optString("request_id", ""),
        )
        val itemKey = firstNonEmpty(
            payload.optString("itemId", ""),
            payload.optString("item_id", ""),
            message.optString("itemId", ""),
            message.optString("item_id", ""),
        )
        appendUserInputBubble(turnKey, itemKey, text)
    }

internal fun MainActivity.shouldIgnoreEvent(message: JSONObject): Boolean {
        val seq = message.optInt("seq", -1)
        val sessionId = message.optString("session_id", "")
        if (seq < 0 || sessionId.isEmpty()) {
            return false
        }
        val key = "$sessionId:$seq"
        if (renderedEventKeys.contains(key)) return true
        renderedEventKeys.add(key)
        return false
    }

internal fun MainActivity.payloadCarriesSessionMetadata(payload: JSONObject): Boolean {
        return payload.has("thread")
            || payload.has("cwd")
            || payload.has("model")
            || payload.has("modelProvider")
            || payload.has("approvalPolicy")
            || payload.has("sandbox")
            || payload.has("sandboxPolicy")
            || payload.has("permissions")
            || payload.has("activePermissionProfile")
            || payload.has("permissionProfile")
            || payload.has("tokenUsage")
            || payload.has("contextWindow")
            || payload.has("lastTokenUsage")
            || payload.has("totalTokenUsage")
    }

internal fun MainActivity.handleAssistantDelta(message: JSONObject, payload: JSONObject) {
        val turnKey = extractTurnKey(message, payload)
        val itemKey = extractAssistantItemKey(message = message, payload = payload)
        val delta = firstNonEmpty(
            payload.optString("delta", ""),
            payload.optString("text", ""),
            payload.optString("message", ""),
        )
        appendAssistantDeltaBubble(turnKey, itemKey.ifBlank { "stream" }, delta)
    }

internal fun MainActivity.handleApprovalRequest(eventName: String, message: JSONObject, payload: JSONObject) {
        val requestId = firstNonEmpty(message.optString("request_id", ""), message.optString("id", ""))
        if (requestId.isBlank()) return

        val presentation = buildApprovalPresentation(eventName, payload)
        upsertPendingApproval(
            ApprovalDialogState(
                requestId = requestId,
                title = presentation.title,
                detail = presentation.detail,
                actions = buildApprovalActions(eventName, payload),
                diffEntries = presentation.diffEntries,
                turnId = extractExplicitTurnKey(message, payload).takeIf { it.isNotBlank() },
            ),
        )
    }
