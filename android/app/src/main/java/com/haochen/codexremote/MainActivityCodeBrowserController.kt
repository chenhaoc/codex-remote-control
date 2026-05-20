package com.haochen.codexremote

import org.json.JSONObject

internal fun MainActivity.openCodeBrowser(itemId: String, selectedPath: String?, scrollY: Int) {
        val sessionId = activeSessionId
        val item = conversationItems.firstOrNull { it.id == itemId }
        val state =
            when (item) {
                is ConversationItem.FileChange ->
                    CodeBrowserState(
                        conversationItemId = item.id,
                        sessionId = sessionId,
                        title = item.title,
                        basePath = activeSession()?.cwd,
                        diffEntries = item.diffEntries,
                        fallbackDiff = item.fallbackDiff,
                        selectedPath = selectedPath ?: item.diffEntries.firstOrNull()?.browsePath(),
                        mode = CodeBrowserMode.Diff,
                    )

                else -> null
            }

        if (state == null || (state.diffEntries.isEmpty() && state.fallbackDiff.isNullOrBlank())) {
            showNotice("这个条目暂时没有可浏览的代码内容")
            return
        }
        chatRestoreScrollY = scrollY.coerceAtLeast(0)
        codeBrowserState = state
    }

internal fun MainActivity.closeCodeBrowser() {
        codeBrowserState = null
    }

internal fun MainActivity.selectCodeBrowserPath(path: String) {
        val state = codeBrowserState ?: return
        if (state.selectedPath == path) return
        codeBrowserState = state.copy(
            selectedPath = path,
            mode = CodeBrowserMode.Diff,
            fileReadState = CodeBrowserFileReadState.Idle,
        )
    }

internal fun MainActivity.setCodeBrowserMode(mode: CodeBrowserMode) {
        val state = codeBrowserState ?: return
        if (state.mode == mode) return
        codeBrowserState = state.copy(
            mode = mode,
            fileReadState = when {
                mode == CodeBrowserMode.File -> CodeBrowserFileReadState.Idle
                else -> state.fileReadState
            },
        )
    }

internal fun MainActivity.loadCodeBrowserFileContent(state: CodeBrowserState) {
        val entry = state.selectedEntry()
        val sessionId = state.sessionId ?: activeSessionId
        if (entry == null || sessionId.isNullOrBlank()) {
            codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Error("没有可读取的文件内容"))
            return
        }
        if (entry.kind.trim() == "delete" && entry.movePath.isNullOrBlank()) {
            codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Error("该文件已经被删除，当前内容无法读取"))
            return
        }

        val candidatePaths = entry.browseCandidates()
        if (candidatePaths.isEmpty()) {
            codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Error("没有可读取的文件路径"))
            return
        }
        findCachedCodeBrowserFileContent(sessionId, candidatePaths)?.let { cached ->
            codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Loaded(cached))
            return
        }
        val browsePath = candidatePaths.first()
        codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Loading)

        fun requestAt(index: Int) {
            val requestedPath = candidatePaths.getOrNull(index)
            if (requestedPath.isNullOrBlank()) {
                val current = codeBrowserState ?: return
                if (current.conversationItemId == state.conversationItemId && current.selectedPath == browsePath) {
                    codeBrowserState = current.copy(fileReadState = CodeBrowserFileReadState.Error("文件读取失败，候选路径都不可用"))
                }
                return
            }

            val payload = JSONObject().apply {
                put("session_id", sessionId)
                put("path", requestedPath)
                put("max_bytes", 200000)
            }

            val sent = sendRequest("file.read", payload, object : ResponseHandler {
                override fun onResponse(response: JSONObject) {
                    val current = codeBrowserState ?: return
                    if (current.conversationItemId != state.conversationItemId || current.selectedPath != browsePath) return

                    val result = response.optJSONObject("payload") ?: JSONObject()
                    val content =
                        CodeBrowserFileContent(
                            requestedPath = requestedPath,
                            resolvedPath = result.optString("resolved_path", requestedPath),
                            content = result.optString("content", ""),
                            truncated = result.optBoolean("truncated", false),
                            bytes = result.optInt("bytes", 0),
                        )
                    rememberCodeBrowserFileContent(sessionId, content)
                    codeBrowserState = current.copy(
                        fileReadState = CodeBrowserFileReadState.Loaded(
                            content,
                        ),
                    )
                }

                override fun onError(errorText: String) {
                    if (index + 1 < candidatePaths.size) {
                        requestAt(index + 1)
                        return
                    }
                    val current = codeBrowserState ?: return
                    if (current.conversationItemId != state.conversationItemId || current.selectedPath != browsePath) return
                    codeBrowserState = current.copy(
                        fileReadState = CodeBrowserFileReadState.Error(
                            buildString {
                                append(if (errorText.isBlank()) "文件读取失败" else errorText)
                                append("\n候选路径: ")
                                append(candidatePaths.joinToString(" | ") { compactDiffDisplayPath(it, state.basePath, maxLength = 64) })
                            },
                        ),
                    )
                }
            })

            if (!sent) {
                if (index + 1 < candidatePaths.size) {
                    requestAt(index + 1)
                    return
                }
                val current = codeBrowserState ?: return
                if (current.conversationItemId == state.conversationItemId && current.selectedPath == browsePath) {
                    codeBrowserState = current.copy(fileReadState = CodeBrowserFileReadState.Error("文件读取请求发送失败"))
                }
            }
        }

        requestAt(0)
    }


internal fun MainActivity.buildCodeBrowserFileCacheKey(
        sessionId: String,
        path: String,
    ): String {
        return "$sessionId::$path"
    }

internal fun MainActivity.findCachedCodeBrowserFileContent(
        sessionId: String,
        candidatePaths: List<String>,
    ): CodeBrowserFileContent? {
        return candidatePaths.firstNotNullOfOrNull { path ->
            codeBrowserFileCache[buildCodeBrowserFileCacheKey(sessionId, path)]
        }
    }

internal fun MainActivity.rememberCodeBrowserFileContent(
        sessionId: String,
        content: CodeBrowserFileContent,
    ) {
        codeBrowserFileCache[buildCodeBrowserFileCacheKey(sessionId, content.requestedPath)] = content
        codeBrowserFileCache[buildCodeBrowserFileCacheKey(sessionId, content.resolvedPath)] = content
    }
