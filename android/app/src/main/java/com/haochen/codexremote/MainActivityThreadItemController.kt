package com.haochen.codexremote

import org.json.JSONArray
import org.json.JSONObject

internal fun MainActivity.handleThreadItemEvent(eventName: String, payload: JSONObject) {
        val item = payload.optJSONObject("item") ?: return
        val turnKey = extractTurnKey(payload = payload)
        val itemKey = extractAssistantItemKey(payload = payload, item = item)
        when (item.optString("type", "")) {
            "agentMessage" -> {
                if (eventName == "item/completed") {
                    discardBufferedAssistantText(turnKey, itemKey)
                    activeSessionId?.takeIf { it.isNotBlank() }?.let { sessionId ->
                        requestSessionRefresh(sessionId)
                    }
                }
            }
            "fileChange" -> handleFileChangeItem(turnKey, item)
            else -> Unit
        }
    }

internal fun MainActivity.handleToolItem(eventName: String, item: JSONObject) {
        val itemId = item.optString("id", "").trim()
        if (itemId.isBlank()) return
        val title = if (eventName == "item/started") {
            "${labelThreadItem(item)} 开始"
        } else {
            labelThreadItem(item)
        }
        val detail = when (item.optString("type", "")) {
            "plan", "reasoning" -> formatThreadItemDetails(item)
            else -> formatToolItemDetails(item, eventName)
        }.trim()
        val text = if (detail.isBlank()) title else "$title\n$detail"
        val existingId = toolItemIds[itemId]
        if (existingId == null) {
            val created = ConversationItem.SystemNote(
                id = buildConversationItemId("tool", itemId, item.optString("type", "").takeIf { it.isNotBlank() }),
                text = text,
                itemKey = itemId,
            )
            toolItemIds[itemId] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(existingId) { current ->
            if (current is ConversationItem.SystemNote) {
                current.copy(text = text, itemKey = itemId)
            } else {
                current
            }
        }
    }

internal fun MainActivity.extractThreadItemText(item: JSONObject): String {
        return when (item.optString("type", "")) {
            "userMessage" -> extractPlainTextContent(item.optJSONArray("content"))
            "agentMessage" -> stripClientDirectivesFromAssistantText(
                firstNonEmpty(
                    item.optString("text", "").trim(),
                    extractPlainTextContent(item.optJSONArray("content")),
                ),
            )
            "plan" -> item.optString("text", "").trim()
            "reasoning" -> {
                val summary = item.optJSONArray("summary")?.let { array ->
                    buildList {
                        for (i in 0 until array.length()) {
                            array.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
                val content = item.optJSONArray("content")?.let { array ->
                    buildList {
                        for (i in 0 until array.length()) {
                            array.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
                listOfNotNull(
                    summary.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                    content.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                ).joinToString("\n").trim()
            }

            else -> ""
        }
    }

internal fun MainActivity.extractPlainTextContent(content: JSONArray?): String {
        if (content == null) return ""
        val parts = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i)
            if (part?.optString("type", "") == "text") {
                part.optString("text", "").trim().takeIf { it.isNotBlank() }?.let(parts::add)
            }
        }
        return parts.joinToString("\n").trim()
    }

internal fun MainActivity.formatThreadItemDetails(item: JSONObject): String {
        return when (item.optString("type", "")) {
            "reasoning" -> {
                val summary = item.optJSONArray("summary")?.toString(2).orEmpty()
                val content = item.optJSONArray("content")?.toString(2).orEmpty()
                listOf(summary, content).filter { it.isNotBlank() }.joinToString("\n")
            }

            else -> formatJson(item)
        }
    }

internal fun MainActivity.formatToolItemDetails(item: JSONObject, eventName: String): String {
        val lines = mutableListOf<String>()
        when (item.optString("type", "")) {
            "commandExecution" -> {
                if (eventName == "item/started") {
                    item.optString("command", "").takeIf { it.isNotBlank() }?.let { lines.add("命令: $it") }
                    item.optString("cwd", "").takeIf { it.isNotBlank() }?.let { lines.add("目录: $it") }
                    item.optString("source", "").takeIf { it.isNotBlank() }?.let { lines.add("来源: $it") }
                    item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                } else {
                    item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                    item.opt("exitCode").takeIf { it != null }?.let { lines.add("退出码: $it") }
                    item.optString("aggregatedOutput", "").takeIf { it.isNotBlank() }?.let { lines.add("输出:\n$it") }
                    item.opt("durationMs").takeIf { it != null }?.let { lines.add("耗时: ${it}ms") }
                }
            }

            "mcpToolCall" -> {
                item.optString("server", "").takeIf { it.isNotBlank() }?.let { lines.add("服务: $it") }
                item.optString("tool", "").takeIf { it.isNotBlank() }?.let { lines.add("工具: $it") }
                item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                if (eventName == "item/completed") {
                    item.opt("result")?.let { lines.add("结果: $it") }
                    item.opt("error")?.let { lines.add("错误: $it") }
                }
            }

            "dynamicToolCall" -> {
                item.optString("namespace", "").takeIf { it.isNotBlank() }?.let { lines.add("命名空间: $it") }
                item.optString("tool", "").takeIf { it.isNotBlank() }?.let { lines.add("工具: $it") }
                item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                if (eventName == "item/completed") {
                    item.optJSONArray("contentItems")?.let { contentItems ->
                        lines.add("结果条目: ${contentItems.length()} 项")
                        lines.add(contentItems.toString(2))
                    }
                }
            }

            "collabAgentToolCall" -> {
                item.optString("tool", "").takeIf { it.isNotBlank() }?.let { lines.add("工具: $it") }
                item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                item.optString("prompt", "").takeIf { it.isNotBlank() }?.let { lines.add("提示:\n$it") }
            }

            else -> {
                if (eventName == "item/started") {
                    lines.add("开始")
                }
                lines.add(formatJson(item))
            }
        }
        return if (lines.isEmpty()) formatJson(item) else lines.joinToString("\n")
    }
