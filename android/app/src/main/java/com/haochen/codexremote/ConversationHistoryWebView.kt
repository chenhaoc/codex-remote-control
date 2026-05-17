package com.haochen.codexremote

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.text.TextUtils
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView

private val conversationMarkdownCodeFenceRegex = Regex("""^```([a-zA-Z0-9_+-]+)?\s*$""")
private val conversationMarkdownHeadingRegex = Regex("""^(#{1,6})\s+(.*)$""")
private val conversationMarkdownUnorderedListRegex = Regex("""^(\s*)([-*+])\s+(.*)$""")
private val conversationMarkdownOrderedListRegex = Regex("""^(\s*)(\d+)\.\s+(.*)$""")
private val conversationMarkdownTaskListRegex = Regex("""^\[( |x|X)]\s+(.*)$""")
private val conversationMarkdownBlockQuoteRegex = Regex("""^\s*>\s?(.*)$""")
private val conversationMarkdownDividerRegex = Regex("""^\s{0,3}([-*_])(?:\s*\1){2,}\s*$""")
private val conversationMarkdownTableSeparatorRegex = Regex("""^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*:?-{3,}:?\s*\|?\s*$""")

private sealed interface ConversationMarkdownBlock {
    data class Heading(
        val level: Int,
        val content: String,
    ) : ConversationMarkdownBlock

    data class Paragraph(
        val content: String,
    ) : ConversationMarkdownBlock

    data class ListBlock(
        val items: List<ConversationMarkdownListItem>,
    ) : ConversationMarkdownBlock

    data class Quote(
        val lines: List<String>,
    ) : ConversationMarkdownBlock

    data class CodeBlock(
        val language: String?,
        val code: String,
    ) : ConversationMarkdownBlock

    data object Divider : ConversationMarkdownBlock

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : ConversationMarkdownBlock
}

private data class ConversationMarkdownListItem(
    val marker: String,
    val content: String,
    val depth: Int = 0,
    val isTask: Boolean = false,
)

private data class ConversationMarkdownListNode(
    val item: ConversationMarkdownListItem,
    val children: MutableList<ConversationMarkdownListNode> = mutableListOf(),
)

private enum class ConversationMarkdownHtmlListType {
    Ordered,
    Unordered,
}

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
    followBottom: Boolean,
    onApprovalDecision: (requestId: String, decision: String, itemId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val pageHtml = remember(items) { buildConversationPageHtml(items) }
    var pendingScrollMode by remember(sessionId) { mutableStateOf<ConversationScrollMode>(ConversationScrollMode.Bottom) }
    var lastLoadedHtml by remember(sessionId) { mutableStateOf<String?>(null) }
    var expectedHtmlTag by remember(sessionId) { mutableStateOf<String?>(null) }

    val webView = remember(sessionId) {
        buildConversationWebView(
            context = context,
            onOpenLink = { url -> uriHandler.openUri(url) },
            onApprovalDecision = onApprovalDecision,
            onPageFinished = { view ->
                val targetMode = pendingScrollMode
                listOf(0L, 48L, 160L).forEach { delayMs ->
                    view.postDelayed(
                        {
                            if (view.tag != expectedHtmlTag) return@postDelayed
                            applyConversationScrollMode(view, targetMode)
                        },
                        delayMs,
                    )
                }
            },
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { webView },
        update = { view ->
            if (lastLoadedHtml == pageHtml) return@AndroidView
            val sessionChanged = lastLoadedHtml == null
            val wasAtBottom = !view.canScrollVertically(1)
            pendingScrollMode =
                when {
                    sessionChanged -> ConversationScrollMode.Bottom
                    followBottom && wasAtBottom -> ConversationScrollMode.Bottom
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

private fun buildConversationWebView(
    context: Context,
    onOpenLink: (String) -> Unit,
    onApprovalDecision: (requestId: String, decision: String, itemId: String) -> Unit,
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
                    return handleConversationUrl(url, onOpenLink, onApprovalDecision)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?,
                ): Boolean {
                    if (url.isNullOrBlank()) return false
                    return handleConversationUrl(url, onOpenLink, onApprovalDecision)
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
    onOpenLink: (String) -> Unit,
    onApprovalDecision: (requestId: String, decision: String, itemId: String) -> Unit,
): Boolean {
    val uri = Uri.parse(rawUrl)
    if (uri.scheme == "codex-approval") {
        val requestId = uri.getQueryParameter("requestId").orEmpty()
        val decision = uri.getQueryParameter("decision").orEmpty()
        val itemId = uri.getQueryParameter("itemId").orEmpty()
        if (requestId.isNotBlank() && decision.isNotBlank() && itemId.isNotBlank()) {
            onApprovalDecision(requestId, decision, itemId)
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

private fun buildConversationPageHtml(items: List<ConversationItem>): String {
    val bodyHtml = buildConversationItemsHtml(items)
    val style = buildConversationCss()
    return """
        <!DOCTYPE html>
        <html dir="auto">
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="initial-scale=1, minimum-scale=1, maximum-scale=1, user-scalable=no, width=device-width, viewport-fit=cover" />
            <style>
            $style
            </style>
        </head>
        <body>
            <main>
                <article>
                    <section class="cr-conversation">
                        $bodyHtml
                    </section>
                </article>
            </main>
        </body>
        </html>
    """.trimIndent()
}

private fun buildConversationItemsHtml(items: List<ConversationItem>): String =
    buildString {
        items.forEach { item ->
            when (item) {
                is ConversationItem.Bubble -> append(item.toHtml())
                is ConversationItem.SystemNote -> append(item.toHtml())
                is ConversationItem.Approval -> append(item.toHtml())
            }
        }
    }

private fun ConversationItem.Bubble.toHtml(): String {
    return if (right) {
        """
        <div class="cr-message user">
            <div class="cr-bubble user">${buildPlainTextHtml(text)}</div>
        </div>
        """.trimIndent()
    } else {
        """
        <div class="cr-message assistant">
            <div class="cr-bubble assistant">${buildConversationMarkdownHtml(text)}</div>
        </div>
        """.trimIndent()
    }
}

private fun ConversationItem.SystemNote.toHtml(): String =
    """
    <div class="cr-note-row">
        <div class="cr-note">${buildPlainTextHtml(text)}</div>
    </div>
    """.trimIndent()

private fun ConversationItem.Approval.toHtml(): String {
    val actions =
        availableDecisions.joinToString("") { decision ->
            val label =
                when (decision) {
                    "accept" -> "接受"
                    "decline" -> "拒绝"
                    "cancel" -> "取消"
                    else -> decision
                }
            val href =
                buildString {
                    append("codex-approval://respond?")
                    append("requestId=${encodeUrlComponent(requestId)}")
                    append("&decision=${encodeUrlComponent(decision)}")
                    append("&itemId=${encodeUrlComponent(id)}")
                }
            """<a class="cr-approval-action" href="$href">${escapeConversationHtml(label)}</a>"""
        }
    return """
        <div class="cr-message assistant">
            <div class="cr-bubble approval">
                <div class="cr-approval-title">${escapeConversationHtml(title)}</div>
                <div class="cr-approval-detail">${buildPlainTextHtml(detail)}</div>
                <div class="cr-approval-actions">$actions</div>
            </div>
        </div>
    """.trimIndent()
}

private fun buildConversationMarkdownHtml(markdown: String): String {
    val blocks = parseConversationMarkdownBlocks(markdown)
    if (blocks.isEmpty()) {
        val content = markdown.replace("\r\n", "\n").trim()
        return if (content.isBlank()) "" else """<section class="cr-md"><p>${buildConversationInlineHtml(content)}</p></section>"""
    }

    return buildString {
        append("""<section class="cr-md">""")
        blocks.forEach { block ->
            append(block.toHtml())
        }
        append("</section>")
    }
}

private fun buildPlainTextHtml(text: String): String =
    text.replace("\r\n", "\n")
        .split('\n')
        .joinToString("<br />") { line -> escapeConversationHtml(line) }

private fun buildConversationCss(): String =
    """
    html, body, main, article {
        margin: 0 !important;
        padding: 0 !important;
        background: transparent !important;
        color: #FFFFFF !important;
    }

    body {
        font-size: 15px !important;
        line-height: 1.28 !important;
        -webkit-text-size-adjust: 100%;
        word-break: break-word;
        overflow-wrap: anywhere;
    }

    .cr-conversation {
        display: flex;
        flex-direction: column;
        gap: 14px;
        padding: 14px;
        box-sizing: border-box;
    }

    .cr-message {
        display: flex;
        width: 100%;
    }

    .cr-message.user {
        justify-content: flex-end;
    }

    .cr-message.assistant {
        justify-content: flex-start;
    }

    .cr-bubble {
        box-sizing: border-box;
        max-width: 320px;
        min-width: 48px;
        padding: 10px 14px;
        border-radius: 18px;
        overflow: hidden;
    }

    .cr-bubble.user {
        background: #0EA5E9 !important;
        color: #FFFFFF !important;
    }

    .cr-bubble.assistant {
        background: #172033 !important;
        color: #FFFFFF !important;
    }

    .cr-bubble.approval {
        background: #2E245C !important;
        color: #FFFFFF !important;
    }

    .cr-note-row {
        display: flex;
        justify-content: flex-start;
        width: 100%;
    }

    .cr-note {
        background: #0F172A !important;
        color: #98A8C2 !important;
        border-radius: 14px;
        padding: 10px 12px;
        font-size: 12px !important;
        line-height: 1.5 !important;
        max-width: 320px;
        box-sizing: border-box;
    }

    .cr-approval-title {
        font-weight: 700 !important;
        margin-bottom: 8px;
    }

    .cr-approval-detail {
        font-size: 13px !important;
        line-height: 1.46 !important;
    }

    .cr-approval-actions {
        display: flex;
        gap: 8px;
        margin-top: 12px;
        flex-wrap: wrap;
    }

    .cr-approval-action {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 72px;
        padding: 9px 12px;
        border-radius: 12px;
        background: #0F6D99 !important;
        color: #FFFFFF !important;
        text-decoration: none !important;
        font-weight: 600 !important;
    }

    .cr-md, .cr-md * {
        color: inherit;
    }

    .cr-md > :first-child {
        margin-top: 0 !important;
    }

    .cr-md > :last-child {
        margin-bottom: 0 !important;
    }

    .cr-md p,
    .cr-md h1,
    .cr-md h2,
    .cr-md h3,
    .cr-md h4,
    .cr-md h5,
    .cr-md h6,
    .cr-md blockquote,
    .cr-md pre,
    .cr-md hr,
    .cr-md table {
        margin-left: 0 !important;
        margin-right: 0 !important;
    }

    .cr-md p {
        margin: 0.45em 0 !important;
    }

    .cr-md h1, .cr-md h2, .cr-md h3, .cr-md h4, .cr-md h5, .cr-md h6 {
        font-weight: 700 !important;
        line-height: 1.3 !important;
    }

    .cr-md h1 { font-size: 1.4em !important; margin: 0.65em 0 0.38em !important; }
    .cr-md h2 { font-size: 1.26em !important; margin: 0.62em 0 0.34em !important; }
    .cr-md h3 { font-size: 1.16em !important; margin: 0.58em 0 0.3em !important; }
    .cr-md h4, .cr-md h5, .cr-md h6 { font-size: 1.05em !important; margin: 0.55em 0 0.28em !important; }

    .cr-md a {
        color: #7DD3FC !important;
        text-decoration: none !important;
    }

    .cr-md strong, .cr-md b {
        font-weight: 700 !important;
    }

    .cr-md em, .cr-md i {
        font-style: italic !important;
    }

    .cr-md mark {
        background-color: rgba(125, 211, 252, 0.22) !important;
        color: inherit !important;
        padding: 0 2px;
        border-radius: 3px;
    }

    .cr-md del {
        text-decoration: line-through !important;
    }

    .cr-md code {
        color: #7DD3FC !important;
        background: rgba(255, 255, 255, 0.08) !important;
        border-radius: 6px;
        padding: 0.1em 0.35em;
        font-family: "SFMono-Regular", "JetBrains Mono", "Fira Code", monospace;
        font-size: 0.92em !important;
        white-space: pre-wrap !important;
    }

    .cr-md pre {
        background: rgba(255, 255, 255, 0.08) !important;
        border-radius: 12px;
        padding: 10px 12px !important;
        overflow-x: auto !important;
        white-space: pre !important;
    }

    .cr-md pre code {
        background: transparent !important;
        color: inherit !important;
        padding: 0 !important;
        border-radius: 0 !important;
        white-space: pre !important;
        display: block;
    }

    .cr-md blockquote {
        margin: 0.45em 0 !important;
        padding: 0.1em 0 0.1em 0.85em !important;
        border-left: 3px solid rgba(125, 211, 252, 0.72) !important;
        opacity: 0.95;
    }

    .cr-md ul, .cr-md ol {
        margin-top: 0.45em !important;
        margin-bottom: 0.45em !important;
        padding-left: 1.3em !important;
    }

    .cr-md li {
        margin: 0.16em 0 !important;
    }

    .cr-md hr {
        border: 0 !important;
        border-top: 1px solid rgba(255, 255, 255, 0.16) !important;
        margin: 0.75em 0 !important;
    }

    .cr-md img {
        max-width: 100% !important;
        height: auto !important;
        border-radius: 12px;
    }

    .cr-md-table-wrap {
        width: 100%;
        overflow-x: auto;
        margin: 0.45em 0 !important;
        border: 1px solid rgba(255, 255, 255, 0.18);
        border-radius: 10px;
    }

    .cr-md-table-wrap table {
        display: table !important;
        width: max-content !important;
        min-width: 100% !important;
        border-collapse: collapse !important;
        table-layout: auto !important;
        margin: 0 !important;
    }

    .cr-md-table-wrap th,
    .cr-md-table-wrap td {
        min-width: 88px;
        padding: 8px 10px !important;
        font-size: 0.92em !important;
        border: 1px solid rgba(255, 255, 255, 0.18) !important;
        vertical-align: top !important;
        text-align: left !important;
        white-space: pre-wrap !important;
        word-break: break-word;
    }

    .cr-md-table-wrap th {
        background-color: rgba(255, 255, 255, 0.08) !important;
        font-weight: 600 !important;
    }

    .cr-md-table-wrap tbody tr:nth-child(even) td {
        background-color: rgba(255, 255, 255, 0.04) !important;
    }

    .cr-md-code-language {
        margin: 0 0 6px !important;
        color: #7DD3FC !important;
        font-size: 0.8em !important;
        font-weight: 600 !important;
        letter-spacing: 0.02em !important;
    }

    .cr-md-task-marker {
        font-weight: 600 !important;
    }
    """.trimIndent()

private fun parseConversationMarkdownBlocks(markdown: String): List<ConversationMarkdownBlock> {
    val normalized = markdown.replace("\r\n", "\n").trim()
    if (normalized.isBlank()) return emptyList()

    val lines = normalized.lines()
    val blocks = mutableListOf<ConversationMarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index += 1
            continue
        }

        conversationMarkdownCodeFenceRegex.matchEntire(line)?.let { match ->
            val language = match.groupValues[1].ifBlank { null }
            index += 1
            val codeLines = mutableListOf<String>()
            while (index < lines.size && !conversationMarkdownCodeFenceRegex.matches(lines[index])) {
                codeLines += lines[index]
                index += 1
            }
            if (index < lines.size && conversationMarkdownCodeFenceRegex.matches(lines[index])) {
                index += 1
            }
            blocks += ConversationMarkdownBlock.CodeBlock(language = language, code = codeLines.joinToString("\n"))
            continue
        }

        if (conversationMarkdownDividerRegex.matches(line)) {
            blocks += ConversationMarkdownBlock.Divider
            index += 1
            continue
        }

        conversationMarkdownHeadingRegex.matchEntire(line)?.let { match ->
            blocks += ConversationMarkdownBlock.Heading(
                level = match.groupValues[1].length.coerceIn(1, 6),
                content = match.groupValues[2].trim(),
            )
            index += 1
            continue
        }

        if (
            isConversationMarkdownTableHeaderLine(line) &&
            index + 1 < lines.size &&
            conversationMarkdownTableSeparatorRegex.matches(lines[index + 1])
        ) {
            val headers = splitConversationMarkdownTableRow(line)
            index += 2
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && isConversationMarkdownTableBodyLine(lines[index])) {
                rows += splitConversationMarkdownTableRow(lines[index])
                index += 1
            }
            blocks += ConversationMarkdownBlock.Table(headers = headers, rows = rows)
            continue
        }

        if (conversationMarkdownUnorderedListRegex.matches(line) || conversationMarkdownOrderedListRegex.matches(line)) {
            val items = mutableListOf<ConversationMarkdownListItem>()
            while (index < lines.size) {
                val current = lines[index]
                val unordered = conversationMarkdownUnorderedListRegex.matchEntire(current)
                val ordered = conversationMarkdownOrderedListRegex.matchEntire(current)
                val match = unordered ?: ordered ?: break
                val indent = match.groupValues[1].replace("\t", "    ").length
                val depth = (indent / 2).coerceAtLeast(0)
                val marker = if (unordered != null) "\u2022" else "${match.groupValues[2]}."
                val rawContent = match.groupValues[3]
                val taskMatch = conversationMarkdownTaskListRegex.matchEntire(rawContent.trim())
                items += if (taskMatch != null) {
                    ConversationMarkdownListItem(
                        marker = if (taskMatch.groupValues[1].equals("x", ignoreCase = true)) "☑" else "☐",
                        content = taskMatch.groupValues[2].trim(),
                        depth = depth,
                        isTask = true,
                    )
                } else {
                    ConversationMarkdownListItem(
                        marker = marker,
                        content = rawContent.trim(),
                        depth = depth,
                    )
                }
                index += 1
            }
            blocks += ConversationMarkdownBlock.ListBlock(items = items)
            continue
        }

        if (conversationMarkdownBlockQuoteRegex.matches(line)) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size) {
                val match = conversationMarkdownBlockQuoteRegex.matchEntire(lines[index]) ?: break
                quoteLines += match.groupValues[1].trim()
                index += 1
            }
            blocks += ConversationMarkdownBlock.Quote(lines = quoteLines)
            continue
        }

        val paragraphLines = mutableListOf<String>()
        while (index < lines.size) {
            val current = lines[index]
            if (current.isBlank()) break
            if (
                conversationMarkdownCodeFenceRegex.matches(current) ||
                conversationMarkdownDividerRegex.matches(current) ||
                conversationMarkdownHeadingRegex.matches(current) ||
                (isConversationMarkdownTableHeaderLine(current) &&
                    index + 1 < lines.size &&
                    conversationMarkdownTableSeparatorRegex.matches(lines[index + 1])) ||
                conversationMarkdownUnorderedListRegex.matches(current) ||
                conversationMarkdownOrderedListRegex.matches(current) ||
                conversationMarkdownBlockQuoteRegex.matches(current)
            ) {
                break
            }
            paragraphLines += current.trim()
            index += 1
        }
        blocks += ConversationMarkdownBlock.Paragraph(content = paragraphLines.joinToString(separator = "\n"))
    }

    return blocks
}

private fun ConversationMarkdownBlock.toHtml(): String =
    when (this) {
        is ConversationMarkdownBlock.Heading ->
            buildConversationHtmlTag(
                tag = "h$level",
                content = buildConversationInlineHtml(content),
            )

        is ConversationMarkdownBlock.Paragraph ->
            buildConversationHtmlTag(
                tag = "p",
                content = buildConversationInlineHtml(content),
            )

        is ConversationMarkdownBlock.ListBlock -> buildConversationMarkdownListHtml(items)

        is ConversationMarkdownBlock.Quote ->
            buildConversationHtmlTag(
                tag = "blockquote",
                content = buildConversationHtmlTag("p", buildConversationInlineHtml(lines.joinToString("\n"))),
            )

        is ConversationMarkdownBlock.CodeBlock ->
            buildString {
                language?.takeIf { it.isNotBlank() }?.let {
                    append(buildConversationHtmlTag("""div class="cr-md-code-language"""", escapeConversationHtml(it)))
                }
                append(
                    "<pre><code" +
                        language?.takeIf { it.isNotBlank() }?.let {
                            " class=\"language-${escapeConversationHtmlAttribute(it)}\""
                        }.orEmpty() +
                        ">${escapeConversationHtml(code)}</code></pre>",
                )
            }

        is ConversationMarkdownBlock.Divider -> "<hr />"

        is ConversationMarkdownBlock.Table ->
            buildString {
                append("""<div class="cr-md-table-wrap"><table><thead><tr>""")
                headers.forEach { header ->
                    append(buildConversationHtmlTag("th", buildConversationInlineHtml(header)))
                }
                append("</tr></thead><tbody>")
                rows.forEach { row ->
                    append("<tr>")
                    row.forEach { cell ->
                        append(buildConversationHtmlTag("td", buildConversationInlineHtml(cell)))
                    }
                    append("</tr>")
                }
                append("</tbody></table></div>")
            }
    }

private fun buildConversationMarkdownListHtml(items: List<ConversationMarkdownListItem>): String {
    if (items.isEmpty()) return ""

    val roots = mutableListOf<ConversationMarkdownListNode>()
    val stack = mutableListOf<ConversationMarkdownListNode>()

    items.forEach { item ->
        val normalizedDepth = item.depth.coerceAtLeast(0).coerceAtMost(stack.size)
        while (stack.size > normalizedDepth) {
            stack.removeLast()
        }

        val node = ConversationMarkdownListNode(item = item)
        if (stack.isEmpty()) {
            roots += node
        } else {
            stack.last().children += node
        }
        stack += node
    }

    return renderConversationMarkdownListNodes(roots)
}

private fun renderConversationMarkdownListNodes(nodes: List<ConversationMarkdownListNode>): String =
    buildString {
        var index = 0
        while (index < nodes.size) {
            val listType = nodes[index].item.toHtmlListType()
            append(openConversationMarkdownListTag(listType, nodes[index].item))

            while (index < nodes.size && nodes[index].item.toHtmlListType() == listType) {
                val node = nodes[index]
                append("<li>")
                if (node.item.isTask) {
                    append(buildConversationHtmlTag("""span class="cr-md-task-marker"""", escapeConversationHtml(node.item.marker)))
                    append(' ')
                }
                append(buildConversationInlineHtml(node.item.content))
                if (node.children.isNotEmpty()) {
                    append(renderConversationMarkdownListNodes(node.children))
                }
                append("</li>")
                index += 1
            }

            append(closeConversationMarkdownListTag(listType))
        }
    }

private fun ConversationMarkdownListItem.toHtmlListType(): ConversationMarkdownHtmlListType =
    if (marker.endsWith(".") && marker.dropLast(1).all(Char::isDigit)) {
        ConversationMarkdownHtmlListType.Ordered
    } else {
        ConversationMarkdownHtmlListType.Unordered
    }

private fun openConversationMarkdownListTag(
    type: ConversationMarkdownHtmlListType,
    item: ConversationMarkdownListItem,
): String =
    when (type) {
        ConversationMarkdownHtmlListType.Ordered -> {
            val start = item.marker.removeSuffix(".").toIntOrNull()
            if (start != null && start > 1) {
                """<ol start="$start">"""
            } else {
                "<ol>"
            }
        }

        ConversationMarkdownHtmlListType.Unordered -> "<ul>"
    }

private fun closeConversationMarkdownListTag(type: ConversationMarkdownHtmlListType): String =
    when (type) {
        ConversationMarkdownHtmlListType.Ordered -> "</ol>"
        ConversationMarkdownHtmlListType.Unordered -> "</ul>"
    }

private fun buildConversationInlineHtml(text: String): String {
    fun parseSegment(
        source: String,
        allowAutolinks: Boolean = true,
    ): String =
        buildString {
            var index = 0
            while (index < source.length) {
                when {
                    allowAutolinks &&
                        (source.startsWith("<http://", index) || source.startsWith("<https://", index)) -> {
                        val urlEnd = source.indexOf('>', startIndex = index + 1)
                        if (urlEnd != -1) {
                            val url = source.substring(index + 1, urlEnd)
                            append("""<a href="${escapeConversationHtmlAttribute(url)}">${escapeConversationHtml(url)}</a>""")
                            index = urlEnd + 1
                            continue
                        }
                    }

                    allowAutolinks &&
                        (source.startsWith("http://", index) || source.startsWith("https://", index)) -> {
                        val end = findConversationInlineUrlEnd(source, index)
                        val candidate = source.substring(index, end)
                        val url = trimConversationTrailingUrlPunctuation(candidate)
                        if (url.isNotBlank()) {
                            val suffix = candidate.substring(url.length)
                            append("""<a href="${escapeConversationHtmlAttribute(url)}">${escapeConversationHtml(url)}</a>""")
                            append(escapeConversationHtml(suffix))
                            index = end
                            continue
                        }
                    }

                    source.startsWith("***", index) || source.startsWith("___", index) -> {
                        val delimiter = source.substring(index, index + 3)
                        val end = source.indexOf(delimiter, startIndex = index + 3)
                        if (end != -1) {
                            append("<strong><em>")
                            append(parseSegment(source.substring(index + 3, end)))
                            append("</em></strong>")
                            index = end + 3
                            continue
                        }
                    }

                    source.startsWith("**", index) || source.startsWith("__", index) -> {
                        val delimiter = source.substring(index, index + 2)
                        val end = source.indexOf(delimiter, startIndex = index + 2)
                        if (end != -1) {
                            append(buildConversationHtmlTag("strong", parseSegment(source.substring(index + 2, end))))
                            index = end + 2
                            continue
                        }
                    }

                    source.startsWith("~~", index) -> {
                        val end = source.indexOf("~~", startIndex = index + 2)
                        if (end != -1) {
                            append(buildConversationHtmlTag("del", parseSegment(source.substring(index + 2, end))))
                            index = end + 2
                            continue
                        }
                    }

                    source.startsWith("==", index) -> {
                        val end = source.indexOf("==", startIndex = index + 2)
                        if (end != -1) {
                            append(buildConversationHtmlTag("mark", parseSegment(source.substring(index + 2, end))))
                            index = end + 2
                            continue
                        }
                    }

                    source.startsWith("`", index) -> {
                        val end = source.indexOf('`', startIndex = index + 1)
                        if (end != -1) {
                            append(buildConversationHtmlTag("code", escapeConversationHtml(source.substring(index + 1, end))))
                            index = end + 1
                            continue
                        }
                    }

                    source.startsWith("![", index) -> {
                        val labelEnd = source.indexOf(']', startIndex = index + 2)
                        val urlStart = labelEnd.takeIf { it != -1 }?.plus(1)
                        if (
                            labelEnd != -1 &&
                            urlStart != null &&
                            urlStart < source.length &&
                            source[urlStart] == '('
                        ) {
                            val urlEnd = source.indexOf(')', startIndex = urlStart + 1)
                            if (urlEnd != -1) {
                                val alt = escapeConversationHtmlAttribute(source.substring(index + 2, labelEnd))
                                val url = escapeConversationHtmlAttribute(source.substring(urlStart + 1, urlEnd))
                                append("""<img src="$url" alt="$alt" />""")
                                index = urlEnd + 1
                                continue
                            }
                        }
                    }

                    source.startsWith("[", index) -> {
                        val labelEnd = source.indexOf(']', startIndex = index + 1)
                        val urlStart = labelEnd.takeIf { it != -1 }?.plus(1)
                        if (
                            labelEnd != -1 &&
                            urlStart != null &&
                            urlStart < source.length &&
                            source[urlStart] == '('
                        ) {
                            val urlEnd = source.indexOf(')', startIndex = urlStart + 1)
                            if (urlEnd != -1) {
                                val label = parseSegment(source.substring(index + 1, labelEnd), allowAutolinks = false)
                                val url = escapeConversationHtmlAttribute(source.substring(urlStart + 1, urlEnd))
                                append("""<a href="$url">$label</a>""")
                                index = urlEnd + 1
                                continue
                            }
                        }
                    }

                    source.startsWith("*", index) || source.startsWith("_", index) -> {
                        val delimiter = source[index]
                        val end = source.indexOf(delimiter, startIndex = index + 1)
                        if (end != -1) {
                            append(buildConversationHtmlTag("em", parseSegment(source.substring(index + 1, end))))
                            index = end + 1
                            continue
                        }
                    }
                }

                append(escapeConversationHtml(source[index].toString()))
                index += 1
            }
        }

    return text.split('\n').joinToString("<br />") { line -> parseSegment(line) }
}

private fun isConversationMarkdownTableHeaderLine(line: String): Boolean = line.contains('|')

private fun isConversationMarkdownTableBodyLine(line: String): Boolean =
    line.isNotBlank() && line.contains('|') && !conversationMarkdownTableSeparatorRegex.matches(line)

private fun splitConversationMarkdownTableRow(line: String): List<String> =
    line.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split('|')
        .map { it.trim() }

private fun buildConversationHtmlTag(
    tag: String,
    content: String,
): String = "<$tag>$content</${tag.substringBefore(' ')}>"

private fun escapeConversationHtml(text: String): String = TextUtils.htmlEncode(text)

private fun escapeConversationHtmlAttribute(text: String): String = TextUtils.htmlEncode(text)

private fun encodeUrlComponent(value: String): String = Uri.encode(value)

private fun trimConversationTrailingUrlPunctuation(candidate: String): String {
    var url = candidate
    while (url.isNotEmpty()) {
        val trailing = url.last()
        url =
            when (trailing) {
                ',', '.', ';', ':', '!', '?', '，', '。', '；', '：', '！', '？' -> url.dropLast(1)
                ')', ']', '}', '）', '】', '》' ->
                    if (shouldTrimConversationClosingDelimiter(url, trailing)) {
                        url.dropLast(1)
                    } else {
                        return url
                    }
                else -> return url
            }
    }
    return url
}

private fun shouldTrimConversationClosingDelimiter(
    url: String,
    closingDelimiter: Char,
): Boolean {
    val openingDelimiter =
        when (closingDelimiter) {
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            '）' -> '（'
            '】' -> '【'
            '》' -> '《'
            else -> return false
        }
    return url.count { it == closingDelimiter } > url.count { it == openingDelimiter }
}

private fun findConversationInlineUrlEnd(
    text: String,
    startIndex: Int,
): Int {
    var index = startIndex
    while (index < text.length && !text[index].isWhitespace()) {
        index += 1
    }
    return index
}
