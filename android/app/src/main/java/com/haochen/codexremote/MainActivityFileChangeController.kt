package com.haochen.codexremote

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal fun MainActivity.handleFileChangeItem(turnKey: String, item: JSONObject) {
        val itemId = item.optString("id", "").trim()
        if (itemId.isBlank()) return
        val changes = item.optJSONArray("changes").toConversationDiffEntries()
        val status = item.optString("status", "").trim()
        upsertFileChangeConversationItem(
            itemId = itemId,
            turnKey = turnKey,
            status = status,
            diffEntries = changes,
            fallbackDiff = turnDiffs[turnKey],
        )
    }

internal fun MainActivity.handleFileChangePatchUpdated(message: JSONObject, payload: JSONObject) {
        val itemId = extractAssistantItemKey(message = message, payload = payload)
        if (itemId.isBlank()) return
        val turnKey = extractTurnKey(message, payload)
        val changes = payload.optJSONArray("changes").toConversationDiffEntries()
        upsertFileChangeConversationItem(
            itemId = itemId,
            turnKey = turnKey,
            status = "inProgress",
            diffEntries = changes,
            fallbackDiff = turnDiffs[turnKey],
        )
    }

internal fun MainActivity.handleTurnDiffUpdated(message: JSONObject, payload: JSONObject) {
        val turnKey = extractTurnKey(message, payload)
        val diff = payload.optString("diff", "").trim()
        if (turnKey.isBlank() || diff.isBlank()) return
        turnDiffs[turnKey] = diff

        val existingFileChangeItemId = fileChangeTurnIds.entries.firstOrNull { it.value == turnKey }?.key
        if (existingFileChangeItemId != null) {
            val conversationItemId = fileChangeItemIds[existingFileChangeItemId]
            if (conversationItemId != null) {
                replaceConversationItem(conversationItemId) { current ->
                    if (current is ConversationItem.FileChange) {
                        current.copy(
                            summary = buildFileChangeSummary(
                                status = current.status,
                                diffEntries = current.diffEntries,
                                fallbackDiff = diff,
                            ),
                            fallbackDiff = diff,
                        )
                    } else {
                        current
                    }
                }
                return
            }
        }

        upsertTurnDiffConversationItem(turnKey, diff)
    }

internal fun MainActivity.upsertFileChangeConversationItem(
        itemId: String,
        turnKey: String,
        status: String,
        diffEntries: List<ConversationDiffEntry>,
        fallbackDiff: String?,
    ) {
        if (itemId.isBlank() || turnKey.isBlank()) return
        fileChangeTurnIds[itemId] = turnKey
        turnDiffItemIds.remove(turnKey)?.let(::removeConversationItem)

        val summary = buildFileChangeSummary(status, diffEntries, fallbackDiff)
        val existingId = fileChangeItemIds[itemId]
        if (existingId == null) {
            val created = ConversationItem.FileChange(
                id = "file_change_${UUID.randomUUID()}",
                title = "文件修改",
                summary = summary,
                status = status,
                diffEntries = diffEntries,
                fallbackDiff = fallbackDiff,
            )
            fileChangeItemIds[itemId] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(existingId) { current ->
            if (current is ConversationItem.FileChange) {
                current.copy(
                    title = "文件修改",
                    summary = summary,
                    status = status,
                    diffEntries = diffEntries,
                    fallbackDiff = fallbackDiff,
                )
            } else {
                current
            }
        }
    }

internal fun MainActivity.upsertTurnDiffConversationItem(turnKey: String, diff: String) {
        val summary = buildFileChangeSummary(status = "inProgress", diffEntries = emptyList(), fallbackDiff = diff)
        val existingId = turnDiffItemIds[turnKey]
        if (existingId == null) {
            val created = ConversationItem.FileChange(
                id = "turn_diff_${UUID.randomUUID()}",
                title = "文件修改中",
                summary = summary,
                status = "inProgress",
                diffEntries = emptyList(),
                fallbackDiff = diff,
            )
            turnDiffItemIds[turnKey] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(existingId) { current ->
            if (current is ConversationItem.FileChange) {
                current.copy(
                    title = "文件修改中",
                    summary = summary,
                    status = "inProgress",
                    fallbackDiff = diff,
                )
            } else {
                current
            }
        }
    }

internal fun MainActivity.buildFileChangeSummary(
        status: String,
        diffEntries: List<ConversationDiffEntry>,
        fallbackDiff: String?,
    ): String {
        val lines = mutableListOf<String>()
        labelPatchStatus(status).takeIf { it.isNotBlank() }?.let(lines::add)

        if (diffEntries.isNotEmpty()) {
            lines.add("${diffEntries.size} 个文件")
            buildDiffStatsLine(diffEntries = diffEntries, fallbackDiff = fallbackDiff)?.let(lines::add)
        } else if (!fallbackDiff.isNullOrBlank()) {
            buildDiffStatsLine(diffEntries = emptyList(), fallbackDiff = fallbackDiff)?.let(lines::add)
        }

        return if (lines.isEmpty()) "点击查看详情" else lines.joinToString("\n")
    }

internal fun MainActivity.buildDiffStatsLine(
        diffEntries: List<ConversationDiffEntry>,
        fallbackDiff: String?,
    ): String? {
        val source =
            if (diffEntries.isNotEmpty()) {
                diffEntries.joinToString("\n") { it.diff }
            } else {
                fallbackDiff.orEmpty()
            }
        return parseDiffStats(source)?.toLabel()
    }

internal fun MainActivity.labelPatchStatus(status: String): String {
        return when (status.trim()) {
            "inProgress" -> "进行中"
            "completed" -> "已完成"
            "failed" -> "失败"
            "declined" -> "已拒绝"
            else -> status.trim()
        }
    }

internal fun JSONArray?.toConversationDiffEntries(): List<ConversationDiffEntry> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.toConversationDiffEntry()?.let(::add)
            }
        }
    }

internal fun JSONObject.toConversationDiffEntry(): ConversationDiffEntry? {
        val path = normalizeNullablePath(opt("path")).orEmpty()
        if (path.isBlank()) return null
        val kindObject = optJSONObject("kind")
        val kindType = firstNonEmpty(
            kindObject?.optString("type", "") ?: "",
            optString("type", ""),
            "update",
        )
        val movePath = firstNonEmpty(
            normalizeNullablePath(kindObject?.opt("move_path")).orEmpty(),
            normalizeNullablePath(opt("move_path")).orEmpty(),
        ).takeIf { it.isNotBlank() }
        return ConversationDiffEntry(
            path = path,
            kind = kindType,
            diff = optString("diff", "").trim(),
            movePath = movePath,
        )
    }

internal fun JSONObject?.toApprovalDiffEntries(): List<ConversationDiffEntry> {
        if (this == null) return emptyList()
        val changes = mutableListOf<ConversationDiffEntry>()
        val keys = keys()
        while (keys.hasNext()) {
            val path = normalizeNullablePath(keys.next()).orEmpty()
            if (path.isBlank()) continue
            val change = optJSONObject(path) ?: continue
            val kind = change.optString("type", "").trim().ifBlank { "update" }
            val diff = when (kind) {
                "update" -> change.optString("unified_diff", "").trim()
                "add" -> change.optString("content", "").trim()
                "delete" -> change.optString("content", "").trim()
                else -> safeJson(change)
            }
            changes.add(
                ConversationDiffEntry(
                    path = path,
                    kind = kind,
                    diff = diff,
                    movePath = normalizeNullablePath(change.opt("move_path")),
                ),
            )
        }
        return changes
    }
