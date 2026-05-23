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
import androidx.compose.runtime.key
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

private val CONVERSATION_VIEWPORT_RESIZE_RESTORE_DELAYS_MS = longArrayOf(0L, 16L, 48L, 120L)
private val CONVERSATION_INCREMENTAL_SCROLL_DELAYS_MS = longArrayOf(0L, 16L, 48L, 120L)

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
    val pendingScrollModes = remember(sessionId) { mutableMapOf<String, ConversationScrollMode>() }
    val pendingRenderSnapshots = remember(sessionId) { mutableMapOf<String, ConversationRenderSnapshot>() }
    var lastLoadedHtml by remember(sessionId) { mutableStateOf<String?>(null) }
    var lastRenderSnapshot by remember(sessionId) { mutableStateOf<ConversationRenderSnapshot?>(null) }
    var expectedHtmlTag by remember(sessionId) { mutableStateOf<String?>(null) }
    var pendingRenderTag by remember(sessionId) { mutableStateOf<String?>(null) }
    var lastScrollMode by remember(sessionId) { mutableStateOf<ConversationScrollMode>(ConversationScrollMode.Bottom) }
    var fullRenderPending by remember(sessionId) { mutableStateOf(false) }
    var restoreAnnounced by remember(sessionId, restoreScrollY) { mutableStateOf(false) }
    var lastViewportHeight by remember(sessionId) { mutableStateOf<Int?>(null) }
    var viewportResizeAnchorY by remember(sessionId) { mutableStateOf<Int?>(null) }
    var viewportResizeSequence by remember(sessionId) { mutableStateOf(0) }

    val webView = remember(sessionId) {
        buildConversationWebView(
            context = context,
            onOpenLink = { url -> uriHandler.openUri(url) },
            onOpenCodeBrowser = onOpenCodeBrowser,
            onPageFinished = { view ->
                val targetTag = view.tag as? String ?: return@buildConversationWebView
                val targetMode = pendingScrollModes[targetTag] ?: return@buildConversationWebView
                view.evaluateJavascript(
                    "document.body ? document.body.getAttribute('data-cr-render-tag') : null;",
                ) { loadedTag ->
                    if (view.tag != targetTag || expectedHtmlTag != targetTag) return@evaluateJavascript
                    if (loadedTag != JSONObject.quote(targetTag)) return@evaluateJavascript
                    pendingRenderSnapshots[targetTag]?.let { snapshot ->
                        lastLoadedHtml = targetTag
                        lastRenderSnapshot = snapshot
                    }
                    pendingRenderSnapshots.clear()
                    pendingRenderTag = null
                    val restoreDelays = listOf(0L, 48L, 160L)
                    restoreDelays.forEach { delayMs ->
                        view.postDelayed(
                            {
                                if (view.tag != targetTag || expectedHtmlTag != targetTag) return@postDelayed
                                applyConversationScrollMode(view, targetMode)
                                if (delayMs == restoreDelays.last()) {
                                    fullRenderPending = false
                                    lastViewportHeight = view.height.takeIf { it > 0 } ?: lastViewportHeight
                                    viewportResizeAnchorY = null
                                }
                                if (targetMode is ConversationScrollMode.Restore && !restoreAnnounced) {
                                    restoreAnnounced = true
                                    onScrollRestored()
                                }
                            },
                            delayMs,
                        )
                    }
                }
            },
        )
    }

    key(sessionId) {
        AndroidView(
            modifier =
                modifier.onSizeChanged { size ->
                    val previousHeight = lastViewportHeight
                    if (previousHeight == null || previousHeight <= 0 || size.height <= 0) {
                        if (size.height > 0) {
                            lastViewportHeight = size.height
                        }
                        return@onSizeChanged
                    }
                    if (previousHeight == size.height) return@onSizeChanged
                    if (restoreScrollY != null || lastLoadedHtml == null || fullRenderPending) return@onSizeChanged
                    lastViewportHeight = size.height
                    val anchorY = viewportResizeAnchorY ?: (webView.scrollY + previousHeight)
                    viewportResizeAnchorY = anchorY
                    viewportResizeSequence += 1
                    val sequence = viewportResizeSequence
                    CONVERSATION_VIEWPORT_RESIZE_RESTORE_DELAYS_MS.forEachIndexed { index, delayMs ->
                        webView.postDelayed(
                            {
                                if (sequence != viewportResizeSequence) return@postDelayed
                                val viewportHeight = webView.height.takeIf { it > 0 } ?: size.height
                                webView.scrollTo(0, (anchorY - viewportHeight).coerceAtLeast(0))
                                if (index == CONVERSATION_VIEWPORT_RESIZE_RESTORE_DELAYS_MS.lastIndex) {
                                    viewportResizeAnchorY = null
                                }
                            },
                            delayMs,
                        )
                    }
                },
            factory = { webView },
            update = { view ->
                val renderTag = renderSnapshot.tag()
                if (pendingRenderTag == null && lastRenderSnapshot == renderSnapshot) return@AndroidView
                if (pendingRenderTag == renderTag) return@AndroidView
                if (fullRenderPending) return@AndroidView
                val previousSnapshot = lastRenderSnapshot
                val sessionChanged = previousSnapshot == null && pendingRenderTag == null
                val wasAtBottom = isConversationNearBottom(view)
                val scrollY = view.scrollY
                val scrollMode =
                    when {
                        restoreScrollY != null && previousSnapshot == null -> ConversationScrollMode.Restore(restoreScrollY)
                        sessionChanged -> ConversationScrollMode.Bottom
                        fullRenderPending -> lastScrollMode
                        wasAtBottom -> ConversationScrollMode.Bottom
                        else -> ConversationScrollMode.Restore(scrollY)
                    }
                pendingScrollModes.clear()
                pendingScrollModes[renderTag] = scrollMode
                pendingRenderSnapshots.clear()
                pendingRenderSnapshots[renderTag] = renderSnapshot
                lastScrollMode = scrollMode
                val patchCheck = previousSnapshot?.let { explainConversationIncrementalPatch(it, renderSnapshot) }
                if (
                    !fullRenderPending &&
                    previousSnapshot != null &&
                    patchCheck?.canPatch == true
                ) {
                    expectedHtmlTag = renderTag
                    fullRenderPending = false
                    pendingRenderTag = renderTag
                    view.tag = renderTag
                    applyConversationIncrementalPatch(
                        webView = view,
                        renderTag = renderTag,
                        fallbackHtml = { buildConversationRenderedPageHtml(renderedItems, renderTag) },
                        onPatchApplied = {
                            lastLoadedHtml = renderTag
                            lastRenderSnapshot = renderSnapshot
                            pendingRenderSnapshots.remove(renderTag)
                            pendingRenderTag = null
                        },
                        onFallbackRenderStarted = {
                            fullRenderPending = true
                        },
                        previous = previousSnapshot,
                        current = renderSnapshot,
                        scrollMode = scrollMode,
                    )
                } else {
                    val pageHtml = buildConversationRenderedPageHtml(renderedItems, renderTag)
                    expectedHtmlTag = renderTag
                    fullRenderPending = true
                    pendingRenderTag = renderTag
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
    val bottomY = conversationBottomScrollY(webView)
    if (bottomY <= 0) return true
    return bottomY - webView.scrollY <= 96
}

private fun canPatchConversationIncrementally(
    previous: ConversationRenderSnapshot,
    current: ConversationRenderSnapshot,
): Boolean = explainConversationIncrementalPatch(previous, current).canPatch

private data class ConversationPatchCheck(
    val canPatch: Boolean,
)

private fun explainConversationIncrementalPatch(
    previous: ConversationRenderSnapshot,
    current: ConversationRenderSnapshot,
): ConversationPatchCheck {
    if (previous.ids.size > current.ids.size) return ConversationPatchCheck(false)
    val currentIndexById = current.ids.withIndex().associate { it.value to it.index }
    var lastMatchedIndex = -1
    previous.ids.forEachIndexed { index, id ->
        val currentIndex = currentIndexById[id] ?: return ConversationPatchCheck(false)
        if (current.types.getOrNull(currentIndex) != previous.types[index]) {
            return ConversationPatchCheck(false)
        }
        if (currentIndex <= lastMatchedIndex) {
            return ConversationPatchCheck(false)
        }
        lastMatchedIndex = currentIndex
    }
    return ConversationPatchCheck(true)
}

private fun applyConversationIncrementalPatch(
    webView: WebView,
    renderTag: String,
    fallbackHtml: () -> String,
    onPatchApplied: () -> Unit,
    onFallbackRenderStarted: () -> Unit,
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
                    put("insertBeforeId", current.ids.firstExistingIdAfter(index, previous.htmlById))
                    put("html", current.htmlById[id].orEmpty())
                },
            )
        }
    }
    if (updates.length() == 0) {
        onPatchApplied()
        return
    }
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
              if (update.insertBeforeId) {
                var anchor = root.querySelector('[data-cr-item-id="' + update.insertBeforeId + '"]');
                if (!anchor) return false;
                anchor.insertAdjacentHTML('beforebegin', update.html);
              } else {
                root.insertAdjacentHTML('beforeend', update.html);
              }
            }
          }
          return true;
        })();
        """.trimIndent()
    webView.evaluateJavascript(script) { result ->
        if (webView.tag != renderTag) return@evaluateJavascript
        if (result == "true") {
            onPatchApplied()
            applyConversationScrollModeAfterPatch(webView, scrollMode)
        } else {
            onFallbackRenderStarted()
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

private fun List<String>.firstExistingIdAfter(
    index: Int,
    existingHtmlById: Map<String, String>,
): String {
    for (candidateIndex in index + 1 until size) {
        val id = this[candidateIndex]
        if (existingHtmlById[id] != null) return id
    }
    return ""
}

private fun applyConversationScrollModeAfterPatch(
    webView: WebView,
    mode: ConversationScrollMode,
) {
    when (mode) {
        ConversationScrollMode.Bottom -> {
            CONVERSATION_INCREMENTAL_SCROLL_DELAYS_MS.forEach { delayMs ->
                webView.postDelayed(
                    { scrollConversationToBottom(webView) },
                    delayMs,
                )
            }
        }
        is ConversationScrollMode.Restore -> webView.scrollTo(0, mode.y.coerceAtLeast(0))
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
    when (mode) {
        ConversationScrollMode.Bottom -> scrollConversationToBottom(webView)
        is ConversationScrollMode.Restore -> webView.scrollTo(0, mode.y.coerceAtLeast(0))
    }
}

private fun scrollConversationToBottom(webView: WebView) {
    webView.scrollTo(0, conversationBottomScrollY(webView))
}

@Suppress("DEPRECATION")
private fun conversationBottomScrollY(webView: WebView): Int {
    val contentHeight = (webView.contentHeight * webView.scale).toInt()
    return (contentHeight - webView.height).coerceAtLeast(0)
}
