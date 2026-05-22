package com.haochen.codexremote

import android.graphics.Color as AndroidColor
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun MainActivity.appendSystemNote(text: String) {
        conversationItems.add(
            ConversationItem.SystemNote(
                id = "sys_${UUID.randomUUID()}",
                text = text,
            ),
        )
    }

internal fun MainActivity.appendBubble(
        text: String,
        right: Boolean,
        backgroundColor: Int,
        textColor: Int,
        turnKey: String? = null,
    ) {
        val normalizedTurnKey = turnKey?.trim()?.takeIf { it.isNotBlank() }
        if (right) {
            val latestUserBubble = conversationItems.asReversed().firstOrNull { item ->
                (item as? ConversationItem.Bubble)?.right == true
            } as? ConversationItem.Bubble
            if (latestUserBubble != null && normalizeAssistantText(latestUserBubble.text) == normalizeAssistantText(text)) {
                val existingTurnKey = latestUserBubble.turnKey.orEmpty()
                val incomingTurnKey = normalizedTurnKey.orEmpty()
                if (existingTurnKey == incomingTurnKey || existingTurnKey.isBlank() || incomingTurnKey.isBlank()) {
                    replaceConversationItem(latestUserBubble.id) { item ->
                        if (item is ConversationItem.Bubble) {
                            item.copy(turnKey = normalizedTurnKey ?: item.turnKey)
                        } else {
                            item
                        }
                    }
                    return
                }
            }
        }
        conversationItems.add(
            ConversationItem.Bubble(
                id = "msg_${UUID.randomUUID()}",
                right = right,
                text = text,
                backgroundColor = backgroundColor,
                textColor = textColor,
                turnKey = normalizedTurnKey,
            ),
        )
    }

internal fun MainActivity.replaceConversationItem(itemId: String, transform: (ConversationItem) -> ConversationItem) {
        val index = conversationItems.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            conversationItems[index] = transform(conversationItems[index])
        }
    }

internal fun MainActivity.removeConversationItem(itemId: String) {
        val index = conversationItems.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            conversationItems.removeAt(index)
        }
        if (codeBrowserState?.conversationItemId == itemId) {
            codeBrowserState = null
        }
    }

internal fun MainActivity.removeCompletedTurnApprovalItems(turnId: String) {
        val normalizedTurnId = turnId.trim()
        if (pendingApprovals.isEmpty()) return

        if (normalizedTurnId.isBlank()) {
            pendingApprovals.clear()
            return
        }

        pendingApprovals.removeAll { approval -> approval.turnId == normalizedTurnId }
    }


internal fun MainActivity.appendAssistantDeltaBubble(turnKey: String, itemKey: String, delta: String) {
        if (delta.isBlank()) return
        val bubbleKey = buildAssistantBubbleKey(turnKey, itemKey)
        val bubbleId = assistantItemIds[bubbleKey]
        if (bubbleId == null) {
            val created = createAssistantBubble(turnKey, delta, itemKey)
            assistantItemIds[bubbleKey] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(bubbleId) { item ->
            if (item is ConversationItem.Bubble) {
                item.copy(text = item.text + delta)
            } else {
                item
            }
        }
    }

internal fun MainActivity.finalizeAssistantBubble(turnKey: String, itemKey: String, text: String) {
        if (text.isBlank()) return
        if (itemKey.isBlank()) {
            appendStandaloneAssistantBubble(turnKey, text)
            return
        }

        val bubbleKey = buildAssistantBubbleKey(turnKey, itemKey)
        val bubbleId = assistantItemIds[bubbleKey]
        if (bubbleId == null) {
            if (hasEquivalentAssistantBubble(turnKey, text)) return
            val created = createAssistantBubble(turnKey, text, itemKey)
            assistantItemIds[bubbleKey] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(bubbleId) { item ->
            if (item is ConversationItem.Bubble) {
                item.copy(text = preferredAssistantText(item.text, text))
            } else {
                item
            }
        }
    }

internal fun MainActivity.appendStandaloneAssistantBubble(turnKey: String, text: String) {
        if (text.isBlank()) return
        if (hasEquivalentAssistantBubble(turnKey, text)) return
        conversationItems.add(createAssistantBubble(turnKey, text))
    }

internal fun MainActivity.appendUserInputBubble(turnKey: String, text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        val existing = conversationItems.asReversed().firstOrNull { item ->
            val bubble = item as? ConversationItem.Bubble ?: return@firstOrNull false
            bubble.right && bubble.turnKey == turnKey && normalizeAssistantText(bubble.text) == normalizeAssistantText(normalizedText)
        }
        if (existing != null) return
        conversationItems.add(
            ConversationItem.Bubble(
                id = buildConversationItemId("user", turnKey, normalizedText.take(24)),
                right = true,
                text = normalizedText,
                backgroundColor = 0xFF1A8F55.toInt(),
                textColor = AndroidColor.WHITE,
                turnKey = turnKey,
            ),
        )
    }

internal fun MainActivity.createAssistantBubble(turnKey: String, text: String, assistantKey: String? = null): ConversationItem.Bubble {
        return ConversationItem.Bubble(
            id = buildConversationItemId("assistant", assistantKey, turnKey),
            right = false,
            text = stripClientDirectivesFromAssistantText(text),
            backgroundColor = 0xFFF1F8F2.toInt(),
            textColor = 0xFF183326.toInt(),
            turnKey = turnKey,
            assistantKey = assistantKey,
        )
    }

internal fun MainActivity.buildConversationItemId(
        prefix: String,
        primaryKey: String?,
        secondaryKey: String? = null,
    ): String {
        val parts = buildList {
            add(prefix.trim().ifBlank { "item" })
            primaryKey?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
            secondaryKey?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
        return parts.joinToString("_") { sanitizeConversationItemIdPart(it) }
    }

internal fun sanitizeConversationItemIdPart(value: String): String {
        val normalized = value.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_")
        return normalized.trim('_').ifBlank { "item" }
    }

internal fun MainActivity.extractTurnKey(message: JSONObject? = null, payload: JSONObject? = null): String {
        return firstNonEmpty(
            message?.optString("turn_id", "") ?: "",
            message?.optString("turnId", "") ?: "",
            payload?.optString("turn_id", "") ?: "",
            payload?.optString("turnId", "") ?: "",
            "assistant",
        )
    }

internal fun MainActivity.extractExplicitTurnKey(message: JSONObject? = null, payload: JSONObject? = null): String {
        return firstNonEmpty(
            message?.optString("turn_id", "") ?: "",
            message?.optString("turnId", "") ?: "",
            payload?.optString("turn_id", "") ?: "",
            payload?.optString("turnId", "") ?: "",
        )
    }

internal fun MainActivity.extractAssistantItemKey(message: JSONObject? = null, payload: JSONObject? = null, item: JSONObject? = null): String {
        return firstNonEmpty(
            message?.optString("item_id", "") ?: "",
            message?.optString("itemId", "") ?: "",
            payload?.optString("item_id", "") ?: "",
            payload?.optString("itemId", "") ?: "",
            item?.optString("id", "") ?: "",
        )
    }

internal fun MainActivity.buildAssistantBubbleKey(turnKey: String, itemKey: String): String {
        return "$turnKey::$itemKey"
    }

internal fun MainActivity.hasEquivalentAssistantBubble(turnKey: String, text: String): Boolean {
        val normalized = normalizeAssistantText(text)
        if (normalized.isBlank()) return false
        return conversationItems.asReversed().any { item ->
            val bubble = item as? ConversationItem.Bubble ?: return@any false
            !bubble.right && bubble.turnKey == turnKey && assistantTextsOverlap(bubble.text, normalized)
        }
    }

internal fun MainActivity.assistantTextsOverlap(left: String, right: String): Boolean {
        val leftNormalized = normalizeAssistantText(left)
        val rightNormalized = normalizeAssistantText(right)
        if (leftNormalized.isBlank() || rightNormalized.isBlank()) return false
        return leftNormalized == rightNormalized ||
            leftNormalized.contains(rightNormalized) ||
            rightNormalized.contains(leftNormalized)
    }

internal fun MainActivity.preferredAssistantText(current: String, incoming: String): String {
        val currentNormalized = normalizeAssistantText(current)
        val incomingNormalized = normalizeAssistantText(incoming)
        return when {
            currentNormalized.isBlank() -> incoming
            incomingNormalized.isBlank() -> current
            currentNormalized == incomingNormalized -> incoming
            incomingNormalized.contains(currentNormalized) -> incoming
            else -> current
        }
    }

internal fun MainActivity.normalizeAssistantText(text: String): String {
        return stripClientDirectivesFromAssistantText(text).replace("\r\n", "\n").trim()
    }

internal fun MainActivity.stripClientDirectivesFromAssistantText(text: String): String {
        if (text.isBlank()) return text
        val filteredLines =
            text.replace("\r\n", "\n")
                .lines()
                .filterNot { line ->
                    isClientDirectiveLine(line.trim())
                }
        return filteredLines.joinToString("\n").trim()
    }

internal fun MainActivity.isClientDirectiveLine(line: String): Boolean {
        if (!line.startsWith("::")) return false
        val braceIndex = line.indexOf('{')
        if (braceIndex <= 2 || !line.endsWith("}")) return false
        val name = line.substring(2, braceIndex)
        if (name.isBlank()) return false
        if (!name.first().isLowerCase()) return false
        return name.all { it.isLowerCase() || it.isDigit() || it == '-' }
    }

internal fun MainActivity.updateLiveTurnStatusFromItem(eventName: String, item: JSONObject?) {
        if (item == null) return
        val started = eventName == "item/started"
        val status = when (item.optString("type", "")) {
            "contextCompaction" -> if (started) "Codex 正在整理上下文…" else "Codex 继续响应中…"
            "reasoning" -> if (started) "Codex 正在思考…" else "Codex 继续响应中…"
            "plan" -> if (started) "Codex 正在整理计划…" else "Codex 继续响应中…"
            "commandExecution" -> if (started) "Codex 正在执行命令…" else "Codex 继续响应中…"
            "fileChange" -> if (started) "Codex 正在修改文件…" else "Codex 继续响应中…"
            "mcpToolCall" -> if (started) "Codex 正在调用工具…" else "Codex 继续响应中…"
            "dynamicToolCall" -> if (started) "Codex 正在运行工具…" else "Codex 继续响应中…"
            "collabAgentToolCall" -> if (started) "Codex 正在协调子任务…" else "Codex 继续响应中…"
            "agentMessage" -> "Codex 正在输出…"
            else -> null
        }
        if (status != null) {
            updateLiveTurnStatus(status)
        }
    }

internal fun MainActivity.updateLiveTurnStatusFromWarning(payload: JSONObject) {
        val message = extractWarningText(payload)
        if (message.contains("Long threads", ignoreCase = true)) {
            updateLiveTurnStatus("Codex 正在整理长上下文…")
        }
    }

internal fun MainActivity.updateLiveTurnStatusFromError(payload: JSONObject) {
        val error = payload.optJSONObject("error") ?: return
        val message = cleanDisplayText(error.opt("message"))
        when {
            message.startsWith("Reconnecting...", ignoreCase = true) -> updateLiveTurnStatus("Codex 连接波动，正在重试…")
            payload.optBoolean("willRetry", false) -> updateLiveTurnStatus("Codex 请求异常，正在重试…")
            message.isNotBlank() -> updateLiveTurnStatus("Codex 响应异常")
        }
    }

internal fun MainActivity.updateLiveTurnStatusFromThreadStatus(status: JSONObject?) {
        when (status?.optString("type", "")?.trim()) {
            "active" -> updateLiveTurnStatus("Codex 正在处理中…")
            "idle" -> if (activeTurnId != null) updateLiveTurnStatus("Codex 正在处理中…")
            "systemError" -> updateLiveTurnStatus("Codex 响应异常")
        }
    }

internal fun MainActivity.updateLiveTurnStatus(status: String?) {
        liveTurnStatus = status?.trim()?.takeIf { it.isNotBlank() }
    }

internal fun MainActivity.labelRawResponseRole(role: String): String {
        return when (role) {
            "assistant" -> "助手"
            "developer" -> "开发者"
            "user" -> "用户"
            else -> role.ifBlank { "消息" }
        }
    }

internal fun MainActivity.labelThreadItem(item: JSONObject): String {
        return when (item.optString("type", "")) {
            "plan" -> "计划"
            "reasoning" -> "思考"
            "commandExecution" -> "命令执行"
            "fileChange" -> "文件修改"
            "contextCompaction" -> "上下文压缩"
            else -> item.optString("type", "项目")
        }
    }

internal fun MainActivity.extractWarningText(payload: JSONObject): String {
        val message = payload.optString("message", "").trim()
        return if (message.isNotBlank()) message else formatJson(payload)
    }

internal fun MainActivity.extractErrorText(error: JSONObject?): String {
        if (error == null) return ""
        val nestedMessage = cleanDisplayText(error.opt("message"))
        val parsedNested = nestedMessage.takeIf { it.startsWith("{") && it.endsWith("}") }?.let { raw ->
            try {
                JSONObject(raw)
            } catch (_: JSONException) {
                null
            }
        }
        val parts = mutableListOf<String>()
        cleanDisplayText(error.opt("code")).takeIf { it.isNotBlank() }?.let { parts.add(it) }
        if (parsedNested != null) {
            val nestedText = extractErrorText(parsedNested)
            if (nestedText.isNotBlank()) parts.add(nestedText)
        } else if (nestedMessage.isNotBlank()) {
            parts.add(nestedMessage)
        }
        cleanDisplayText(error.opt("additionalDetails")).takeIf { it.isNotBlank() }?.let { details ->
            if (details != nestedMessage) parts.add(details)
        }
        error.optJSONObject("codexErrorInfo")?.let { nested ->
            val nestedText = extractErrorText(nested)
            if (nestedText.isNotBlank()) parts.add(nestedText)
        }
        return if (parts.isNotEmpty()) parts.joinToString("\n") else error.keys().asSequence().joinToString("\n") { key ->
            val value = cleanDisplayText(error.opt(key))
            if (value.isNotBlank()) "$key: $value" else "$key"
        }
    }

internal fun MainActivity.describeThreadStatus(status: JSONObject?): String {
        if (status == null) return "未知"
        val type = status.optString("type", "").trim()
        return when (type) {
            "active" -> "活跃"
            "idle" -> "空闲"
            "systemError" -> "系统错误"
            "notLoaded" -> "未加载"
            else -> if (type.isNotBlank()) type else formatJson(status)
        }
    }

internal fun MainActivity.cleanDisplayText(value: Any?): String {
        val text = when (value) {
            null -> ""
            is String -> value
            is JSONObject -> value.toString()
            is JSONArray -> value.toString()
            else -> value.toString()
        }.trim()
        return if (text.isBlank() || text.equals("null", ignoreCase = true)) "" else text
    }
