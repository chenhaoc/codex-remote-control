package com.haochen.codexremote

import android.graphics.Color as AndroidColor
import org.json.JSONArray
import org.json.JSONObject

internal fun MainActivity.buildConversationItemsFromSnapshot(payload: JSONObject?): List<ConversationItem> {
        if (payload == null) return emptyList()
        val items = mutableListOf<ConversationItem>()
        payload.optJSONArray("entries")?.let { entries ->
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                buildConversationItemFromSnapshotEntry(entry)?.let(items::add)
            }
        }
        return items
    }

internal fun MainActivity.buildPendingApprovalsFromSnapshot(payload: JSONObject?): List<ApprovalDialogState> {
        if (payload == null) return emptyList()
        val approvals = mutableListOf<ApprovalDialogState>()
        payload.optJSONArray("pending_approvals")?.let { array ->
            for (i in 0 until array.length()) {
                val approval = array.optJSONObject(i) ?: continue
                buildConversationApprovalFromSnapshot(approval)?.let(approvals::add)
            }
        }
        return approvals
    }

internal fun MainActivity.buildConversationItemFromSnapshotEntry(entry: JSONObject): ConversationItem? {
        return when (entry.optString("type", "")) {
            "item" -> buildConversationSnapshotThreadItem(entry)
            "turn_status" -> buildConversationSnapshotTurnStatus(entry)
            else -> null
        }
    }

internal fun MainActivity.buildConversationSnapshotThreadItem(entry: JSONObject): ConversationItem? {
        val item = entry.optJSONObject("item") ?: return null
        val itemType = item.optString("type", "").trim()
        val turnId = entry.optString("turn_id", "").trim()
        val itemId = item.optString("id", "").trim()
        return when (itemType) {
            "userMessage" -> {
                val text = extractThreadItemText(item)
                if (text.isBlank()) return null
                ConversationItem.Bubble(
                    id = buildConversationItemId("user", itemId.ifBlank { turnId }, turnId),
                    right = true,
                    text = text,
                    backgroundColor = 0xFF1A8F55.toInt(),
                    textColor = AndroidColor.WHITE,
                    turnKey = turnId.takeIf { it.isNotBlank() },
                )
            }
            "agentMessage" -> {
                val text = extractThreadItemText(item)
                if (text.isBlank()) return null
                ConversationItem.Bubble(
                    id = buildConversationItemId("assistant", itemId.ifBlank { turnId }, turnId),
                    right = false,
                    text = text,
                    backgroundColor = 0xFFF1F8F2.toInt(),
                    textColor = 0xFF183326.toInt(),
                    turnKey = turnId.ifBlank { "assistant" },
                    assistantKey = itemId.takeIf { it.isNotBlank() },
                )
            }
            "fileChange" -> {
                val diffEntries = item.optJSONArray("changes").toConversationDiffEntries()
                val status = item.optString("status", "").trim()
                ConversationItem.FileChange(
                    id = buildConversationItemId("file_change", itemId.ifBlank { turnId }, turnId),
                    title = if (status == "inProgress") "文件修改中" else "文件修改",
                    summary = buildFileChangeSummary(status, diffEntries, fallbackDiff = null),
                    status = status,
                    diffEntries = diffEntries,
                    fallbackDiff = null,
                    sourceItemId = itemId.takeIf { it.isNotBlank() },
                    turnId = turnId.takeIf { it.isNotBlank() },
                )
            }
            else -> null
        }
    }

internal fun MainActivity.buildConversationSnapshotTurnStatus(entry: JSONObject): ConversationItem? {
        val turnId = entry.optString("turn_id", "").trim()
        val status = entry.optString("status", "").trim()
        val errorText = extractErrorText(entry.optJSONObject("error"))
        if (status == "interrupted" && errorText.isBlank()) {
            return null
        }
        if (status != "failed" && status != "interrupted" && errorText.isBlank()) {
            return null
        }
        return ConversationItem.SystemNote(
            id = buildConversationItemId("turn_status", turnId.ifBlank { "unknown" }),
            text = buildString {
                append("当前回合结束: ${if (status.isNotBlank()) status else "unknown"}")
                if (errorText.isNotBlank()) {
                    append('\n')
                    append(errorText)
                }
            },
            itemKey = turnId.takeIf { it.isNotBlank() },
        )
    }

internal fun MainActivity.buildConversationApprovalFromSnapshot(approval: JSONObject): ApprovalDialogState? {
        val requestId = approval.optString("request_id", "").trim()
        if (requestId.isBlank()) return null
        val eventName = approval.optString("request_method", "").trim().ifBlank { "approval" }
        val payload = approval.optJSONObject("payload") ?: JSONObject()
        val presentation = buildApprovalPresentation(eventName, payload)
        return ApprovalDialogState(
            requestId = requestId,
            title = presentation.title,
            detail = presentation.detail,
            actions = buildApprovalActions(eventName, payload),
            diffEntries = presentation.diffEntries,
            turnId = approval.optString("turn_id", "").trim().takeIf { it.isNotBlank() },
        )
    }

internal fun MainActivity.updateLiveTurnStatusFromSnapshot(payload: JSONObject?) {
        val status = inferLiveTurnStatusFromSnapshot(payload)
        when {
            status != null -> updateLiveTurnStatus(status)
            activeTurnId == null -> updateLiveTurnStatus(null)
            else -> Unit
        }
    }

internal fun MainActivity.inferLiveTurnStatusFromSnapshot(payload: JSONObject?): String? {
        val session = payload?.optJSONObject("session")
        inferLiveTurnStatusFromSession(session)?.let { return it }
        val entries = payload?.optJSONArray("entries") ?: return null
        return inferLiveTurnStatusFromSnapshotEntries(entries)
    }

internal fun MainActivity.inferLiveTurnStatusFromSession(session: JSONObject?): String? {
        if (session == null) return null
        val thread = session.optJSONObject("thread") ?: session
        inferLiveTurnStatusFromThreadTurns(thread)?.let { return it }
        return when (threadStatusType(thread.opt("status"))) {
            "active", "running" -> "Codex 正在处理中…"
            "systemError" -> "Codex 响应异常"
            else -> null
        }
    }

internal fun MainActivity.inferLiveTurnStatusFromThreadTurns(thread: JSONObject): String? {
        val turns = thread.optJSONArray("turns") ?: return null
        for (index in turns.length() - 1 downTo 0) {
            val turn = turns.optJSONObject(index) ?: continue
            val status = turn.optString("status", "").trim()
            val errorText = extractErrorText(turn.optJSONObject("error"))
            return when {
                errorText.isNotBlank() -> "Codex 响应异常"
                status == "inProgress" -> "Codex 正在处理中…"
                status == "failed" -> "Codex 响应异常"
                status == "completed" -> null
                status == "interrupted" -> null
                status.isNotBlank() -> "Codex 正在处理中…"
                else -> null
            }
        }
        return null
    }

internal fun threadStatusType(value: Any?): String {
        return when (value) {
            is JSONObject -> value.optString("type", "").trim()
            is String -> value.trim()
            else -> ""
        }
    }

internal fun MainActivity.inferLiveTurnStatusFromSnapshotEntries(entries: JSONArray): String? {
        for (index in entries.length() - 1 downTo 0) {
            val entry = entries.optJSONObject(index) ?: continue
            when (entry.optString("type", "")) {
                "turn_status" -> {
                    val status = entry.optString("status", "").trim()
                    val errorText = extractErrorText(entry.optJSONObject("error"))
                    return when {
                        errorText.isNotBlank() -> "Codex 响应异常"
                        status == "failed" -> "Codex 响应异常"
                        status == "interrupted" -> null
                        else -> null
                    }
                }
                "item" -> {
                    val item = entry.optJSONObject("item") ?: continue
                    val itemStatus = item.optString("status", "").trim()
                    if (itemStatus == "inProgress") {
                        return when (item.optString("type", "")) {
                            "fileChange" -> "Codex 正在修改文件…"
                            "commandExecution" -> "Codex 正在执行命令…"
                            "reasoning" -> "Codex 正在思考…"
                            "plan" -> "Codex 正在整理计划…"
                            else -> "Codex 正在处理中…"
                        }
                    }
                }
            }
        }
        return null
    }
