package com.haochen.codexremote

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

private sealed interface ConversationScrollMode {
    data object Bottom : ConversationScrollMode

    data class Restore(
        val y: Int,
    ) : ConversationScrollMode
}

@Composable
internal fun ConversationHistoryWebView(
    items: List<ConversationItem>,
    sessionId: String?,
    displayBasePath: String?,
    followBottom: Boolean,
    restoreScrollY: Int? = null,
    onScrollRestored: () -> Unit = {},
    onOpenCodeBrowser: (itemId: String, path: String?, scrollY: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val renderCache = remember(sessionId) { mutableMapOf<String, ConversationRenderCacheEntry>() }
    val renderedItems = buildCachedConversationRenderedItems(items, displayBasePath, renderCache)
    val renderSnapshot = ConversationRenderSnapshot.from(renderedItems)
    var pendingScrollMode by remember(sessionId, restoreScrollY) {
        mutableStateOf<ConversationScrollMode>(
            restoreScrollY?.let { ConversationScrollMode.Restore(it) } ?: ConversationScrollMode.Bottom,
        )
    }
    var lastLoadedHtml by remember(sessionId) { mutableStateOf<String?>(null) }
    var lastRenderSnapshot by remember(sessionId) { mutableStateOf<ConversationRenderSnapshot?>(null) }
    var expectedHtmlTag by remember(sessionId) { mutableStateOf<String?>(null) }
    var restoreAnnounced by remember(sessionId, restoreScrollY) { mutableStateOf(false) }

    val webView = remember(sessionId) {
        buildConversationWebView(
            context = context,
            onOpenLink = { url -> uriHandler.openUri(url) },
            onOpenCodeBrowser = onOpenCodeBrowser,
            onPageFinished = { view ->
                val targetMode = pendingScrollMode
                listOf(0L, 48L, 160L).forEach { delayMs ->
                    view.postDelayed(
                        {
                            if (view.tag != expectedHtmlTag) return@postDelayed
                            applyConversationScrollMode(view, targetMode)
                            if (targetMode is ConversationScrollMode.Restore && !restoreAnnounced) {
                                restoreAnnounced = true
                                onScrollRestored()
                            }
                        },
                        delayMs,
                    )
                }
            },
        )
    }
    var lastViewportHeight by remember(sessionId) { mutableStateOf<Int?>(null) }

    AndroidView(
        modifier =
            modifier.onSizeChanged { size ->
                val previousHeight = lastViewportHeight
                lastViewportHeight = size.height
                if (previousHeight == null || previousHeight <= 0 || size.height <= 0) return@onSizeChanged
                if (!followBottom) return@onSizeChanged
                if (restoreScrollY != null || lastLoadedHtml == null) return@onSizeChanged
                val delta = previousHeight - size.height
                if (delta == 0) return@onSizeChanged
                webView.post {
                    webView.scrollBy(0, delta)
                }
            },
        factory = { webView },
        update = { view ->
            if (lastRenderSnapshot == renderSnapshot) return@AndroidView
            val renderTag = renderSnapshot.tag()
            val sessionChanged = lastLoadedHtml == null
            val wasAtBottom = isConversationNearBottom(view)
            val scrollY = view.scrollY
            pendingScrollMode =
                when {
                    restoreScrollY != null && sessionChanged -> ConversationScrollMode.Restore(restoreScrollY)
                    sessionChanged -> ConversationScrollMode.Bottom
                    wasAtBottom -> ConversationScrollMode.Bottom
                    else -> ConversationScrollMode.Restore(scrollY)
                }
            val lastSnapshot = lastRenderSnapshot
            if (
                !sessionChanged &&
                lastSnapshot != null &&
                canPatchConversationIncrementally(lastSnapshot, renderSnapshot)
            ) {
                lastLoadedHtml = renderTag
                lastRenderSnapshot = renderSnapshot
                expectedHtmlTag = renderTag
                view.tag = renderTag
                applyConversationIncrementalPatch(
                    webView = view,
                    fallbackHtml = { buildConversationPageHtml(renderedItems) },
                    previous = lastSnapshot,
                    current = renderSnapshot,
                    scrollMode = pendingScrollMode,
                )
            } else {
                val pageHtml = buildConversationPageHtml(renderedItems)
                lastLoadedHtml = renderTag
                lastRenderSnapshot = renderSnapshot
                expectedHtmlTag = renderTag
                view.tag = renderTag
                view.loadDataWithBaseURL(
                    null,
                    pageHtml,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

private fun buildCachedConversationRenderedItems(
    items: List<ConversationItem>,
    displayBasePath: String?,
    cache: MutableMap<String, ConversationRenderCacheEntry>,
): List<ConversationRenderedItem> {
    val liveIds = items.mapTo(mutableSetOf()) { it.id }
    cache.keys.removeAll { it !in liveIds }
    return items.map { item ->
        val signature = buildConversationRenderSignature(item, displayBasePath)
        val cached = cache[item.id]
        if (cached != null && cached.signature == signature) {
            cached.renderedItem
        } else {
            val renderedItem = buildConversationRenderedItem(item, displayBasePath)
            cache[item.id] = ConversationRenderCacheEntry(
                signature = signature,
                renderedItem = renderedItem,
            )
            renderedItem
        }
    }
}

private data class ConversationRenderSnapshot(
    val ids: List<String>,
    val types: List<String>,
    val htmlById: Map<String, String>,
) {
    companion object {
        fun from(items: List<ConversationRenderedItem>): ConversationRenderSnapshot =
            ConversationRenderSnapshot(
                ids = items.map { it.id },
                types = items.map { it.type },
                htmlById = items.associate { item -> item.id to item.html },
            )
    }

    fun tag(): String = "${ids.size}:${ids.hashCode()}:${types.hashCode()}:${htmlById.hashCode()}"
}

private fun isConversationNearBottom(webView: WebView): Boolean {
    return !webView.canScrollVertically(1)
}

private fun canPatchConversationIncrementally(
    previous: ConversationRenderSnapshot,
    current: ConversationRenderSnapshot,
): Boolean {
    if (previous.ids.size > current.ids.size) return false
    previous.ids.forEachIndexed { index, id ->
        if (current.ids.getOrNull(index) != id) return false
        if (current.types.getOrNull(index) != previous.types[index]) return false
    }
    val changedIndexes =
        current.ids.mapIndexedNotNull { index, id ->
            if (previous.htmlById[id] != current.htmlById[id]) index else null
        }
    if (changedIndexes.isEmpty()) return true
    val firstNewIndex = previous.ids.size
    val lastExistingIndex = previous.ids.lastIndex
    if (changedIndexes.any { index -> index < lastExistingIndex }) return false
    if (changedIndexes.any { index -> index < firstNewIndex && index != lastExistingIndex }) return false
    return true
}

private fun applyConversationIncrementalPatch(
    webView: WebView,
    fallbackHtml: () -> String,
    previous: ConversationRenderSnapshot,
    current: ConversationRenderSnapshot,
    scrollMode: ConversationScrollMode,
) {
    val updates = JSONArray()
    current.ids.forEachIndexed { index, id ->
        if (previous.htmlById[id] != current.htmlById[id]) {
            updates.put(
                JSONObject().apply {
                    put("id", id)
                    put("type", current.types[index])
                    put("append", index >= previous.ids.size)
                    put("html", current.htmlById[id].orEmpty())
                },
            )
        }
    }
    if (updates.length() == 0) return
    val script =
        """
        (function() {
          var updates = $updates;
          var root = document.querySelector('.cr-conversation');
          if (!root) return false;
          for (var i = 0; i < updates.length; i++) {
            var update = updates[i];
            var existing = null;
            var renderedItems = root.querySelectorAll('[data-cr-item-id]');
            for (var j = 0; j < renderedItems.length; j++) {
              if (renderedItems[j].getAttribute('data-cr-item-id') === update.id) {
                existing = renderedItems[j];
                break;
              }
            }
            if (existing) {
              if (existing.getAttribute('data-cr-item-type') !== update.type) return false;
              existing.outerHTML = update.html;
            } else {
              if (!update.append) return false;
              root.insertAdjacentHTML('beforeend', update.html);
            }
          }
          return true;
        })();
        """.trimIndent()
    webView.evaluateJavascript(script) { result ->
        if (result == "true") {
            applyConversationScrollMode(webView, scrollMode)
        } else {
            webView.loadDataWithBaseURL(
                null,
                fallbackHtml(),
                "text/html",
                "utf-8",
                null,
            )
        }
    }
}

private fun buildConversationWebView(
    context: Context,
    onOpenLink: (String) -> Unit,
    onOpenCodeBrowser: (itemId: String, path: String?, scrollY: Int) -> Unit,
    onPageFinished: (WebView) -> Unit,
): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(AndroidColor.TRANSPARENT)
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isScrollbarFadingEnabled = false
        settings.javaScriptEnabled = true
        settings.loadsImagesAutomatically = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            settings.isAlgorithmicDarkeningAllowed = false
        }
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return handleConversationUrl(url, view?.scrollY ?: 0, onOpenLink, onOpenCodeBrowser)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?,
                ): Boolean {
                    if (url.isNullOrBlank()) return false
                    return handleConversationUrl(url, view?.scrollY ?: 0, onOpenLink, onOpenCodeBrowser)
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?,
                ) {
                    view ?: return
                    onPageFinished(view)
                }
            }
    }

private fun handleConversationUrl(
    rawUrl: String,
    currentScrollY: Int,
    onOpenLink: (String) -> Unit,
    onOpenCodeBrowser: (itemId: String, path: String?, scrollY: Int) -> Unit,
): Boolean {
    val uri = Uri.parse(rawUrl)
    if (uri.scheme == "codex-code-browser") {
        val itemId = uri.getQueryParameter("itemId").orEmpty()
        if (itemId.isNotBlank()) {
            onOpenCodeBrowser(itemId, uri.getQueryParameter("path"), currentScrollY)
        }
        return true
    }
    onOpenLink(rawUrl)
    return true
}

private fun applyConversationScrollMode(
    webView: WebView,
    mode: ConversationScrollMode,
) {
    val script =
        when (mode) {
            ConversationScrollMode.Bottom ->
                """
                window.scrollTo(0, Math.max(
                  document.documentElement.scrollHeight || 0,
                  document.body ? document.body.scrollHeight : 0
                ));
                """.trimIndent()

            is ConversationScrollMode.Restore ->
                "window.scrollTo(0, ${mode.y.coerceAtLeast(0)});"
        }
    webView.evaluateJavascript(script, null)
}
