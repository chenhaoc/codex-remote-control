package com.haochen.codexremote

import java.io.File
import java.net.URI
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val SESSION_CACHE_FILE = "session_cache_index.json"
private const val SESSION_CACHE_CONTENT_DIR = "session_cache"
private const val SESSION_CACHE_VERSION = 1

internal fun MainActivity.loadLocalSessionCache() {
    val cacheKey = sessionCacheKeyForCurrentConnection()
    activeSessionCacheKey = cacheKey
    loadLocalSessionCacheForKey(cacheKey)
}

internal fun MainActivity.switchLocalSessionCache(url: String?) {
    activeSessionCacheKey = sessionCacheKeyForUrl(url)
    stopSessionSyncLoop()
    clearConversation()
    sessions.clear()
    sessionContentCache.clear()
    sessionContentCacheSignatures.clear()
    selectedWorkspace = null
    bootSyncRequested = false
    activeSessionId = null
    val cachedSessionId = loadLocalSessionCacheForKey(activeSessionCacheKey, applyContent = false)
    if (cachedSessionId == null) {
        prefs.edit().remove(KEY_SESSION).apply()
    }
}

internal fun MainActivity.persistLocalSessionList() {
    writeSessionCacheRoot(buildUpdatedSessionListCacheRoot())
}

internal fun MainActivity.persistLocalSessionContent(sessionId: String, payload: JSONObject?) {
    if (sessionId.isBlank() || payload == null) return
    val signature = buildSessionContentCacheSignature(payload)
    if (sessionContentCacheSignatures[sessionId] == signature) return
    sessionContentCacheSignatures[sessionId] = signature
    sessionContentCache[sessionId] = payload
    writeSessionContentCache(sessionId, payload)
}

internal fun MainActivity.applyCachedSessionContent(sessionId: String): Boolean {
    val payload = sessionContentCache[sessionId] ?: readSessionContentCache(sessionId)
        ?.also { sessionContentCache[sessionId] = it }
        ?: return false
    payload.optJSONObject("session")?.let(SessionInfo::fromSession)?.let(::upsertSessionFromCache)
    sessionContentCacheSignatures[sessionId] = buildSessionContentCacheSignature(payload)
    applySessionContentSnapshot(JSONObject(payload.toString()))
    updateLiveTurnStatus(null)
    return true
}

internal fun MainActivity.showCachedHistoryAfterConnectionFailure() {
    if (sessions.isEmpty()) return
    val sessionId = activeSessionId?.takeIf { current -> sessions.any { it.sessionId == current } }
        ?: sessions.firstOrNull()?.sessionId
    if (!sessionId.isNullOrBlank()) {
        activeSessionId = sessionId
        prefs.edit().putString(KEY_SESSION, sessionId).apply()
        if (conversationItems.isEmpty()) {
            applyCachedSessionContent(sessionId)
        }
    }
    if (currentPage == AppPage.Connection) {
        currentPage = AppPage.Chat
    }
}

private fun MainActivity.loadLocalSessionCacheForKey(cacheKey: String?): String? {
    return loadLocalSessionCacheForKey(cacheKey, applyContent = true)
}

private fun MainActivity.loadLocalSessionCacheForKey(
    cacheKey: String?,
    applyContent: Boolean,
): String? {
    val connectionCache = readConnectionCache(cacheKey) ?: return null
    replaceSessions(parseCachedSessions(connectionCache.optJSONArray("sessions")))
    sessionContentCache.clear()
    sessionContentCacheSignatures.clear()
    val selectedSessionId = connectionCache.optString("selectedSessionId", "").trim()
    val cachedSessionId = selectedSessionId.takeIf { sessionId -> sessionId.isNotBlank() && sessions.any { it.sessionId == sessionId } }
        ?: activeSessionId?.takeIf { sessionId -> sessions.any { it.sessionId == sessionId } }
        ?: sessions.firstOrNull()?.sessionId
    if (!cachedSessionId.isNullOrBlank()) {
        activeSessionId = cachedSessionId
        prefs.edit().putString(KEY_SESSION, cachedSessionId).apply()
        if (applyContent) {
            applyCachedSessionContent(cachedSessionId)
        }
    }
    return cachedSessionId
}

private fun MainActivity.upsertSessionFromCache(info: SessionInfo) {
    val index = sessions.indexOfFirst { it.sessionId == info.sessionId }
    val merged = info.mergedWith(sessions.firstOrNull { it.sessionId == info.sessionId })
    if (index >= 0) {
        sessions[index] = merged
    } else {
        sessions.add(merged)
    }
}

private fun MainActivity.parseCachedSessions(array: JSONArray?): List<SessionInfo> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let(SessionInfo::fromSession)?.let(::add)
        }
    }
}

private fun MainActivity.buildUpdatedSessionListCacheRoot(): JSONObject {
    val root = readSessionCacheRoot() ?: JSONObject()
    val connections = root.optJSONObject("connections") ?: JSONObject()
    val cacheKey = activeSessionCacheKey ?: sessionCacheKeyForCurrentConnection()
    connections.put(cacheKey, buildConnectionSessionListCache())
    return JSONObject().apply {
        put("version", SESSION_CACHE_VERSION)
        put("updatedAt", System.currentTimeMillis())
        put("connections", connections)
    }
}

private fun MainActivity.buildConnectionSessionListCache(): JSONObject {
    return JSONObject().apply {
        put("updatedAt", System.currentTimeMillis())
        activeSessionId?.takeIf { it.isNotBlank() }?.let { put("selectedSessionId", it) }
        put(
            "sessions",
            JSONArray().also { array ->
                sessions.forEach { session -> array.put(session.toJson()) }
            },
        )
    }
}

private fun buildSessionContentCacheSignature(payload: JSONObject): String {
    val session = payload.optJSONObject("session")
    val entries = payload.optJSONArray("entries")
    val approvals = payload.optJSONArray("pending_approvals")
    val tail = buildList {
        if (entries != null) {
            for (index in (entries.length() - 4).coerceAtLeast(0) until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val item = entry.optJSONObject("item")
                add(
                    listOf(
                        entry.optString("type", ""),
                        entry.optString("turn_id", ""),
                        item?.optString("id", "").orEmpty(),
                        item?.optString("type", "").orEmpty(),
                        item?.optString("status", "").orEmpty(),
                        entry.optString("status", ""),
                    ).joinToString(":"),
                )
            }
        }
    }
    return listOf(
        session?.optString("updatedAt", "").orEmpty(),
        entries?.length()?.toString().orEmpty(),
        approvals?.length()?.toString().orEmpty(),
        tail.joinToString("|"),
    ).joinToString("#")
}

private fun MainActivity.readConnectionCache(cacheKey: String?): JSONObject? {
    val root = readSessionCacheRoot() ?: return null
    val key = cacheKey ?: sessionCacheKeyForCurrentConnection()
    return root.optJSONObject("connections")?.optJSONObject(key)
}

private fun MainActivity.readSessionCacheRoot(): JSONObject? {
    val file = sessionCacheFile()
    if (!file.exists()) return null
    return try {
        JSONObject(file.readText())
    } catch (_: JSONException) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun MainActivity.writeSessionCacheRoot(root: JSONObject) {
    try {
        val file = sessionCacheFile()
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    } catch (_: Exception) {
        // Cache failures should not affect the bridge connection or chat flow.
    }
}

private fun MainActivity.sessionCacheFile(): File = File(filesDir, SESSION_CACHE_FILE)

private fun MainActivity.readSessionContentCache(sessionId: String): JSONObject? {
    val file = sessionContentCacheFile(activeSessionCacheKey, sessionId)
    if (!file.exists()) return null
    return try {
        JSONObject(file.readText())
    } catch (_: JSONException) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun MainActivity.writeSessionContentCache(sessionId: String, payload: JSONObject) {
    val file = sessionContentCacheFile(activeSessionCacheKey, sessionId)
    Thread {
        try {
            file.parentFile?.mkdirs()
            file.writeText(payload.toString())
        } catch (_: Exception) {
            // Cache failures should not affect the bridge connection or chat flow.
        }
    }.start()
}

private fun MainActivity.sessionContentCacheFile(cacheKey: String?, sessionId: String): File {
    val resolvedCacheKey = cacheKey ?: sessionCacheKeyForCurrentConnection()
    return File(
        File(File(filesDir, SESSION_CACHE_CONTENT_DIR), resolvedCacheKey),
        "${stableCacheDigest(sessionId)}.json",
    )
}

private fun MainActivity.sessionCacheKeyForCurrentConnection(): String {
    return sessionCacheKeyForUrl(currentBridgeUrl ?: bridgeUrl)
}

private fun MainActivity.sessionCacheKeyForUrl(url: String?): String {
    val normalized = normalizeCacheUrl(url)
    if (normalized.isBlank()) return "bridge_unconfigured"
    return "bridge_${stableCacheDigest(normalized)}"
}

private fun stableCacheDigest(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(16)

private fun MainActivity.normalizeCacheUrl(url: String?): String {
    val raw = url?.trim().orEmpty()
    if (raw.isBlank()) return ""
    return try {
        val normalized = normalizeBridgeUrl(raw, requireToken = false)
        val uri = URI.create(normalized)
        val scheme = uri.scheme?.lowercase().orEmpty().ifBlank { "ws" }
        val host = uri.host?.lowercase().orEmpty()
        val port = if (uri.port >= 0) uri.port else 8787
        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val queryWithoutToken = (uri.rawQuery ?: "")
            .split('&')
            .filter { it.isNotBlank() && !it.startsWith("token=") }
            .sorted()
            .joinToString("&")
        buildString {
            append("$scheme://$host:$port$path")
            if (queryWithoutToken.isNotBlank()) {
                append('?')
                append(queryWithoutToken)
            }
        }
    } catch (_: Exception) {
        raw
    }
}
