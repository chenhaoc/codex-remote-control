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
    val pageHtml = buildConversationPageHtml(items, displayBasePath)
    var pendingScrollMode by remember(sessionId, restoreScrollY) {
        mutableStateOf<ConversationScrollMode>(
            restoreScrollY?.let { ConversationScrollMode.Restore(it) } ?: ConversationScrollMode.Bottom,
        )
    }
    var lastLoadedHtml by remember(sessionId) { mutableStateOf<String?>(null) }
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
            if (lastLoadedHtml == pageHtml) return@AndroidView
            val sessionChanged = lastLoadedHtml == null
            val wasAtBottom = isConversationNearBottom(view)
            pendingScrollMode =
                when {
                    restoreScrollY != null && sessionChanged -> ConversationScrollMode.Restore(restoreScrollY)
                    sessionChanged -> ConversationScrollMode.Bottom
                    wasAtBottom -> ConversationScrollMode.Bottom
                    else -> ConversationScrollMode.Restore(view.scrollY)
                }
            lastLoadedHtml = pageHtml
            expectedHtmlTag = pageHtml
            view.tag = pageHtml
            view.loadDataWithBaseURL(
                null,
                pageHtml,
                "text/html",
                "utf-8",
                null,
            )
        },
    )
}

private fun isConversationNearBottom(webView: WebView): Boolean {
    val scaledContentHeight = (webView.contentHeight * webView.scale).toInt()
    if (scaledContentHeight <= 0) return true
    val viewportBottom = webView.scrollY + webView.height
    val distanceToBottom = scaledContentHeight - viewportBottom
    return distanceToBottom <= 96
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
