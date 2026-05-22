package com.haochen.codexremote

import android.graphics.Color as AndroidColor
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
