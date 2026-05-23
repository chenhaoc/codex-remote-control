package com.haochen.codexremote

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal const val SYNC_LOG_TAG = "CodexRemoteSync"

internal fun MainActivity.requestSessionRefresh(sessionId: String) {
        if (sessionIncrementalSyncEnabled) {
            requestSessionSync(sessionId)
        } else {
            requestSessionContent(sessionId)
        }
    }

internal fun MainActivity.requestSessionSync(sessionId: String) {
        if (sessionId.isBlank()) {
            Log.d(SYNC_LOG_TAG, "session.sync skip blank session")
            return
        }
        if (!ensureConnected()) {
            Log.d(SYNC_LOG_TAG, "session.sync skip disconnected sessionId=$sessionId")
            return
        }
        if (!sessionSyncCursorReady) {
            Log.d(
                SYNC_LOG_TAG,
                "session.sync fallback content cursor_not_ready sessionId=$sessionId lastSyncedSeq=$lastSyncedSeq conversationItems=${conversationItems.size}",
            )
            requestSessionContent(sessionId)
            return
        }
        if (syncInFlight) {
            sessionContentDirty = true
            Log.d(
                SYNC_LOG_TAG,
                "session.sync defer in_flight sessionId=$sessionId lastSyncedSeq=$lastSyncedSeq dirty=$sessionContentDirty",
            )
            return
        }

        syncInFlight = true
        val requestedSessionId = sessionId
        val sinceSeq = lastSyncedSeq
        val payload = JSONObject().apply {
            put("session_id", sessionId)
            put("since_seq", sinceSeq)
            put("incremental", true)
        }
        Log.d(
            SYNC_LOG_TAG,
            "session.sync request sessionId=$requestedSessionId since=$sinceSeq conversationItems=${conversationItems.size} pendingApprovals=${pendingApprovals.size}",
        )

        if (!sendRequest("session.sync", payload, object : ResponseHandler {
            override fun onResponse(response: JSONObject) {
                syncInFlight = false
                if (activeSessionId != requestedSessionId) {
                    sessionContentDirty = false
                    Log.d(
                        SYNC_LOG_TAG,
                        "session.sync ignore inactive requested=$requestedSessionId active=$activeSessionId",
                    )
                    return
                }

                val responsePayload = response.optJSONObject("payload")
                if (responsePayload == null) {
                    Log.d(SYNC_LOG_TAG, "session.sync fallback content missing_payload sessionId=$requestedSessionId")
                    requestSessionContent(requestedSessionId)
                    return
                }

                val changedTurnIds = responsePayload.optJSONArray("changed_turn_ids").toStringList()
                val needsFullSync = responsePayload.optBoolean("needs_full_sync", false)
                Log.d(
                    SYNC_LOG_TAG,
                    "sessionId=$requestedSessionId since=$sinceSeq ready=$sessionSyncCursorReady last=${responsePayload.optInt("last_seq", lastSyncedSeq)} entries=${responsePayload.optJSONArray("entries")?.length() ?: 0} turns=$changedTurnIds needsFull=$needsFullSync reason=${responsePayload.optString("fallback_reason", "")}",
                )
                logSessionSyncEntries("session.sync response entries", responsePayload)

                if (needsFullSync) {
                    Log.d(
                        SYNC_LOG_TAG,
                        "session.sync fallback content needs_full_sync sessionId=$requestedSessionId reason=${responsePayload.optString("fallback_reason", "")}",
                    )
                    requestSessionContent(requestedSessionId)
                    return
                }

                val result = applySessionSyncSnapshot(responsePayload, changedTurnIds)
                if (!result) {
                    Log.d(SYNC_LOG_TAG, "session.sync fallback content apply_failed sessionId=$requestedSessionId")
                    requestSessionContent(requestedSessionId)
                    return
                }

                updateSessionSyncCursor(responsePayload)
                val mergedPayload = buildCurrentSessionCachePayload(requestedSessionId, responsePayload)
                persistLocalSessionContent(requestedSessionId, mergedPayload)
                updateLiveTurnStatusFromSnapshot(mergedPayload)
                Log.d(
                    SYNC_LOG_TAG,
                    "session.sync applied sessionId=$requestedSessionId conversationItems=${conversationItems.size} cacheEntries=${mergedPayload.optJSONArray("entries")?.length() ?: 0}",
                )
                flushPendingSessionRefresh(requestedSessionId)
            }

            override fun onError(errorText: String) {
                syncInFlight = false
                if (activeSessionId != requestedSessionId) {
                    sessionContentDirty = false
                    Log.d(
                        SYNC_LOG_TAG,
                        "session.sync error ignored inactive requested=$requestedSessionId active=$activeSessionId error=$errorText",
                    )
                    return
                }
                Log.d(SYNC_LOG_TAG, "session.sync fallback content error sessionId=$requestedSessionId error=$errorText")
                requestSessionContent(requestedSessionId)
            }

            override fun suppressDefaultErrorUi(): Boolean = true
        })) {
            syncInFlight = false
            Log.d(SYNC_LOG_TAG, "session.sync send_failed sessionId=$requestedSessionId")
        }
    }

internal fun MainActivity.applySessionSyncSnapshot(payload: JSONObject, changedTurnIds: List<String>): Boolean {
        val entries = payload.optJSONArray("entries") ?: JSONArray()
        val snapshotApprovals = buildPendingApprovalsFromSnapshot(payload)
        if (changedTurnIds.isEmpty()) {
            replacePendingApprovals(snapshotApprovals)
            Log.d(
                SYNC_LOG_TAG,
                "session.sync apply approvals_only entries=${entries.length()} approvals=${snapshotApprovals.size}",
            )
            return true
        }

        if (changedTurnIds.any { it.isBlank() }) {
            Log.d(SYNC_LOG_TAG, "session.sync apply reject blank_changed_turn turns=$changedTurnIds")
            return false
        }
        if (!entriesMatchChangedTurns(entries, changedTurnIds)) {
            Log.d(
                SYNC_LOG_TAG,
                "session.sync apply reject entries_mismatch turns=$changedTurnIds entryTurns=${entries.turnIdSummary()}",
            )
            return false
        }

        val incrementalItems = buildConversationItemsFromSnapshot(payload)
        Log.d(
            SYNC_LOG_TAG,
            "session.sync apply parsed entries=${entries.length()} incrementalItems=${incrementalItems.size} items=${incrementalItems.itemSummary()}",
        )
        if (incrementalItems.isEmpty() && entries.length() > 0 && !entriesContainOnlyTurnStatus(entries)) {
            Log.d(SYNC_LOG_TAG, "session.sync apply reject empty_items_with_entries")
            return false
        }
        if (incrementalItems.any { it.snapshotTurnKey().isNullOrBlank() }) {
            Log.d(SYNC_LOG_TAG, "session.sync apply reject blank_item_turn items=${incrementalItems.itemSummary()}")
            return false
        }

        val changedTurnSet = changedTurnIds.toSet()
        if (!canReplaceConversationTurns(changedTurnSet, incrementalItems)) {
            Log.d(
                SYNC_LOG_TAG,
                "session.sync apply reject id_conflict changedTurns=$changedTurnIds incoming=${incrementalItems.itemSummary()} existing=${conversationItems.itemSummary()}",
            )
            return false
        }

        val nextItems = conversationItems.filterNot { item -> item.snapshotTurnKey()?.let(changedTurnSet::contains) == true } + incrementalItems
        if (nextItems.distinctBy { it.id }.size != nextItems.size) {
            Log.d(SYNC_LOG_TAG, "session.sync apply reject duplicate_next_ids next=${nextItems.itemSummary()}")
            return false
        }

        assistantItemIds.clear()
        toolItemIds.clear()
        fileChangeItemIds.clear()
        fileChangeTurnIds.clear()
        turnDiffItemIds.clear()
        turnDiffs.clear()

        conversationItems.clear()
        conversationItems.addAll(nextItems)
        replacePendingApprovals(snapshotApprovals)
        rebuildConversationIndexes(nextItems)

        codeBrowserState?.let { state ->
            if (!state.conversationItemId.startsWith("approval_") && nextItems.none { it.id == state.conversationItemId }) {
                codeBrowserState = null
            }
        }
        Log.d(
            SYNC_LOG_TAG,
            "session.sync apply success changedTurns=$changedTurnIds conversationItems=${conversationItems.size} approvals=${pendingApprovals.size}",
        )
        return true
    }

internal fun MainActivity.updateSessionSyncCursor(payload: JSONObject?) {
        val lastSeq = payload?.optInt("last_seq", -1) ?: -1
        if (lastSeq >= 0) {
            val previousSeq = lastSyncedSeq
            lastSyncedSeq = lastSeq
            sessionSyncCursorReady = true
            Log.d(SYNC_LOG_TAG, "cursor advanced previous=$previousSeq next=$lastSyncedSeq")
        } else {
            Log.d(SYNC_LOG_TAG, "cursor not advanced missing_last_seq current=$lastSyncedSeq")
        }
    }

internal fun MainActivity.buildCurrentSessionCachePayload(sessionId: String, syncPayload: JSONObject): JSONObject {
        val changedTurnIds = syncPayload.optJSONArray("changed_turn_ids").toStringList().toSet()
        val previousPayload = sessionContentCache[sessionId]?.let { JSONObject(it.toString()) } ?: JSONObject()
        val previousEntries = previousPayload.optJSONArray("entries") ?: JSONArray()
        val syncEntries = syncPayload.optJSONArray("entries") ?: JSONArray()
        val mergedEntries = JSONArray()

        if (changedTurnIds.isEmpty()) {
            for (index in 0 until previousEntries.length()) {
                previousEntries.optJSONObject(index)?.let { mergedEntries.put(JSONObject(it.toString())) }
            }
        } else {
            for (index in 0 until previousEntries.length()) {
                val entry = previousEntries.optJSONObject(index) ?: continue
                val turnId = entry.optString("turn_id", "").trim()
                if (turnId.isBlank() || !changedTurnIds.contains(turnId)) {
                    mergedEntries.put(JSONObject(entry.toString()))
                }
            }
            for (index in 0 until syncEntries.length()) {
                syncEntries.optJSONObject(index)?.let { mergedEntries.put(JSONObject(it.toString())) }
            }
        }

        return JSONObject(previousPayload.toString()).apply {
            put("session_id", syncPayload.optString("session_id", optString("session_id", sessionId)))
            put("thread_id", syncPayload.optString("thread_id", optString("thread_id", sessionId)))
            put("last_seq", syncPayload.optInt("last_seq", optInt("last_seq", lastSyncedSeq)))
            put("entries", mergedEntries)
            syncPayload.optJSONArray("active_turns")?.let { put("active_turns", JSONArray(it.toString())) }
            syncPayload.optJSONArray("pending_approvals")?.let { put("pending_approvals", JSONArray(it.toString())) }
            if (!has("session")) {
                activeSession()?.let { put("session", it.toJson()) }
            }
        }
    }

internal fun MainActivity.logSessionSyncEntries(prefix: String, payload: JSONObject?) {
    val entries = payload?.optJSONArray("entries") ?: JSONArray()
    val summary = buildList {
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val item = entry.optJSONObject("item")
            add(
                listOf(
                    entry.optString("turn_id", ""),
                    item?.optString("type", "").orEmpty(),
                    item?.optString("id", "").orEmpty(),
                    (item?.optString("text", "")?.length ?: item?.optJSONArray("content")?.optJSONObject(0)?.optString("text", "")?.length ?: 0).toString(),
                    entry.optString("status", ""),
                ).joinToString(":"),
            )
        }
    }
    Log.d(SYNC_LOG_TAG, "$prefix count=${entries.length()} tail=${summary.takeLast(8)}")
}

private fun MainActivity.entriesMatchChangedTurns(entries: JSONArray, changedTurnIds: List<String>): Boolean {
    val changedTurnSet = changedTurnIds.toSet()
    for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: return false
        val turnId = entry.optString("turn_id", "").trim()
        if (turnId.isBlank() || !changedTurnSet.contains(turnId)) return false
    }
    return true
}

private fun entriesContainOnlyTurnStatus(entries: JSONArray): Boolean {
    if (entries.length() == 0) return false
    for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: return false
        if (entry.optString("type", "") != "turn_status") return false
    }
    return true
}

private fun MainActivity.canReplaceConversationTurns(
    changedTurnIds: Set<String>,
    incrementalItems: List<ConversationItem>,
): Boolean {
    val incomingIds = incrementalItems.map { it.id }
    if (incomingIds.distinct().size != incomingIds.size) return false

    val changedIds = conversationItems
        .filter { item -> item.snapshotTurnKey()?.let(changedTurnIds::contains) == true }
        .map { it.id }
        .toSet()
    val unchangedIds = conversationItems
        .filterNot { item -> item.snapshotTurnKey()?.let(changedTurnIds::contains) == true }
        .map { it.id }
        .toSet()

    return incomingIds.none { id -> unchangedIds.contains(id) && !changedIds.contains(id) }
}

private fun MainActivity.rebuildConversationIndexes(items: List<ConversationItem>) {
    items.forEach { item ->
        when (item) {
            is ConversationItem.Bubble -> {
                item.assistantKey?.takeIf { it.isNotBlank() }?.let { assistantKey ->
                    val bubbleKey = buildAssistantBubbleKey(item.turnKey.orEmpty(), assistantKey)
                    assistantItemIds[bubbleKey] = item.id
                    assistantDeltaBuffers.remove(bubbleKey)
                }
            }
            is ConversationItem.FileChange -> {
                item.sourceItemId?.takeIf { it.isNotBlank() }?.let { sourceId ->
                    fileChangeItemIds[sourceId] = item.id
                }
                item.turnId?.takeIf { it.isNotBlank() }?.let { turnId ->
                    item.sourceItemId?.takeIf { it.isNotBlank() }?.let { sourceId -> fileChangeTurnIds[sourceId] = turnId }
                }
            }
            else -> Unit
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index, "").trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun JSONArray.turnIdSummary(): List<String> {
    return buildList {
        for (index in 0 until length()) {
            val entry = optJSONObject(index) ?: continue
            add("${entry.optString("turn_id", "")}:${entry.optJSONObject("item")?.optString("type", "").orEmpty()}:${entry.optJSONObject("item")?.optString("id", "").orEmpty()}")
        }
    }
}

private fun List<ConversationItem>.itemSummary(): List<String> {
    return takeLast(12).map { item ->
        when (item) {
            is ConversationItem.Bubble -> "${item.id}:${if (item.right) "user" else "assistant"}:${item.turnKey.orEmpty()}:${item.assistantKey.orEmpty()}:${item.text.length}"
            is ConversationItem.FileChange -> "${item.id}:fileChange:${item.turnId.orEmpty()}:${item.sourceItemId.orEmpty()}"
            is ConversationItem.SystemNote -> "${item.id}:system:${item.itemKey.orEmpty()}:${item.text.length}"
        }
    }
}

private fun ConversationItem.snapshotTurnKey(): String? {
    return when (this) {
        is ConversationItem.Bubble -> turnKey?.trim()?.takeIf { it.isNotBlank() }
        is ConversationItem.FileChange -> turnId?.trim()?.takeIf { it.isNotBlank() }
        is ConversationItem.SystemNote -> itemKey?.trim()?.takeIf { it.isNotBlank() }
    }
}
