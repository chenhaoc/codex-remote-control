package com.haochen.codexremote

import android.net.Uri
import android.text.TextUtils

internal fun buildConversationHtmlTag(
    tag: String,
    content: String,
): String = "<$tag>$content</${tag.substringBefore(' ')}>"

internal fun escapeConversationHtml(text: String): String = TextUtils.htmlEncode(text)

internal fun escapeConversationHtmlAttribute(text: String): String = TextUtils.htmlEncode(text)

internal fun encodeUrlComponent(value: String): String = Uri.encode(value)

internal fun trimConversationTrailingUrlPunctuation(candidate: String): String {
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

internal fun shouldTrimConversationClosingDelimiter(
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

internal fun findConversationInlineUrlEnd(
    text: String,
    startIndex: Int,
): Int {
    var index = startIndex
    while (index < text.length && !text[index].isWhitespace()) {
        index += 1
    }
    return index
}
