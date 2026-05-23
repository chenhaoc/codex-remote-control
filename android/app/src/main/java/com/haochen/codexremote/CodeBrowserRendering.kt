package com.haochen.codexremote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import java.util.Locale

private val codeBrowserPrimary = Color(0xFF1A8F55)
private val codeBrowserText = Color(0xFF183326)
private val codeBrowserMuted = Color(0xFF6D8876)
private const val CODE_BROWSER_FULL_HIGHLIGHT_MAX_CHARS = 60000
private const val CODE_BROWSER_FULL_HIGHLIGHT_MAX_LINES = 1200

internal fun buildCodeBrowserAnnotatedText(
    text: String,
    mode: CodeTextMode,
    pathHint: String? = null,
): CodeBrowserRenderedContent {
    val normalized = text.replace("\r\n", "\n")
    val language = detectCodeLanguage(pathHint)
    val lightweight =
        normalized.length > CODE_BROWSER_FULL_HIGHLIGHT_MAX_CHARS ||
            countCodeBrowserLines(normalized) > CODE_BROWSER_FULL_HIGHLIGHT_MAX_LINES
    return when (mode) {
        CodeTextMode.Diff -> buildDiffRenderedContent(normalized, language, syntaxHighlight = !lightweight, lightweight = lightweight)
        CodeTextMode.File -> buildFileRenderedContent(normalized, language, syntaxHighlight = !lightweight, lightweight = lightweight)
    }
}

internal fun buildCodeBrowserRenderedContent(
    text: String,
    mode: CodeTextMode,
    pathHint: String? = null,
): CodeBrowserRenderedContent {
    return buildCodeBrowserAnnotatedText(text, mode, pathHint)
}

internal fun buildCodeBrowserRenderCacheKey(
    text: String,
    mode: CodeTextMode,
    pathHint: String?,
): String {
    return "${mode.name}|${pathHint.orEmpty()}|${text.length}|${text.hashCode()}"
}

internal fun countCodeBrowserLines(text: String): Int {
    if (text.isEmpty()) return 1
    var lines = 1
    text.forEach { ch ->
        if (ch == '\n') lines += 1
    }
    return lines
}

internal fun buildDiffRenderedContent(
    text: String,
    language: CodeLanguage,
    syntaxHighlight: Boolean,
    lightweight: Boolean,
): CodeBrowserRenderedContent {
    val lines = text.split('\n')
    val diffLineNumbers = computeDiffLineNumbers(lines)
    val maxLineNumber = diffLineNumbers.filterNotNull().maxOrNull() ?: 0
    val lineNumberWidth = maxOf(2, maxLineNumber.toString().length)
    val renderedLines =
        lines.mapIndexed { index, line ->
            CodeBrowserRenderedLine(
                text = buildAnnotatedDiffLine(line, language, syntaxHighlight),
                kind = classifyDiffLine(line),
                lineNumber = diffLineNumbers.getOrNull(index),
            )
        }
    val renderedText =
        buildAnnotatedString {
            renderedLines.forEachIndexed { index, renderedLine ->
                append(renderedLine.text)
                if (index < renderedLines.lastIndex) append('\n')
            }
        }
    return CodeBrowserRenderedContent(
        text = renderedText,
        lines = renderedLines,
        lineNumberWidth = lineNumberWidth,
        lightweight = lightweight,
    )
}

internal fun computeDiffLineNumbers(lines: List<String>): List<Int?> {
    val result = ArrayList<Int?>(lines.size)
    var oldLine: Int? = null
    var newLine: Int? = null

    lines.forEach { line ->
        val hunk = parseDiffHunkHeader(line)
        if (hunk != null) {
            oldLine = hunk.oldStart
            newLine = hunk.newStart
            result += null
            return@forEach
        }

        when (classifyDiffLine(line)) {
            DiffLineKind.Added -> {
                val current = newLine
                result += current
                if (current != null) newLine = current + 1
            }

            DiffLineKind.Deleted -> {
                val current = oldLine
                result += current
                if (current != null) oldLine = current + 1
            }

            DiffLineKind.Context -> {
                val current = newLine
                result += current
                if (oldLine != null) oldLine = oldLine!! + 1
                if (current != null) newLine = current + 1
            }

            DiffLineKind.Meta,
            DiffLineKind.Hunk -> result += null
        }
    }

    return result
}

internal data class DiffHunkHeader(
    val oldStart: Int,
    val newStart: Int,
)

internal fun parseDiffHunkHeader(line: String): DiffHunkHeader? {
    if (!line.startsWith("@@")) return null
    val oldMarkerStart = line.indexOf('-')
    if (oldMarkerStart < 0) return null
    val oldMarkerEnd = line.indexOf(' ', startIndex = oldMarkerStart)
    if (oldMarkerEnd < 0) return null
    val newMarkerStart = line.indexOf('+', startIndex = oldMarkerEnd)
    if (newMarkerStart < 0) return null
    val newMarkerEnd = line.indexOf(' ', startIndex = newMarkerStart).let { if (it < 0) line.length else it }

    val oldStart = parseDiffHunkStart(line.substring(oldMarkerStart + 1, oldMarkerEnd)) ?: return null
    val newStart = parseDiffHunkStart(line.substring(newMarkerStart + 1, newMarkerEnd)) ?: return null
    return DiffHunkHeader(oldStart = oldStart, newStart = newStart)
}

internal fun parseDiffHunkStart(marker: String): Int? {
    val startText = marker.substringBefore(',').trim()
    return startText.toIntOrNull()
}

internal fun buildFileRenderedContent(
    text: String,
    language: CodeLanguage,
    syntaxHighlight: Boolean,
    lightweight: Boolean,
): CodeBrowserRenderedContent {
    val rawLines = text.split('\n')
    val lineNumberWidth = maxOf(2, rawLines.size.toString().length)
    if (lightweight) {
        return CodeBrowserRenderedContent(
            text = buildFileAnnotatedText(text, language, syntaxHighlight),
            lineNumberWidth = lineNumberWidth,
            lightweight = true,
        )
    }
    val renderedLines =
        rawLines.map { line ->
            CodeBrowserRenderedLine(
                text = renderCodeBrowserLine(line, language, syntaxHighlight),
                kind = null,
            )
        }
    val renderedText =
        buildAnnotatedString {
            renderedLines.forEachIndexed { index, renderedLine ->
                withStyle(SpanStyle(color = codeBrowserMuted)) {
                    append((index + 1).toString().padStart(lineNumberWidth, ' '))
                    append(" | ")
                }
                append(renderedLine.text)
                if (index < renderedLines.lastIndex) append('\n')
            }
        }
    return CodeBrowserRenderedContent(
        text = renderedText,
        lines = renderedLines,
        lineNumberWidth = lineNumberWidth,
        lightweight = lightweight,
    )
}

internal fun buildAnnotatedDiffLine(
    line: String,
    language: CodeLanguage,
    syntaxHighlight: Boolean,
): AnnotatedString {
    val metaStyle = SpanStyle(color = c(0xFF94A3B8))
    val hunkStyle = SpanStyle(color = c(0xFF7DD3FC), fontWeight = FontWeight.Bold)
    val addPrefixStyle = SpanStyle(color = c(0xFF86EFAC), fontWeight = FontWeight.Bold)
    val deletePrefixStyle = SpanStyle(color = c(0xFFFCA5A5), fontWeight = FontWeight.Bold)
    val contextPrefixStyle = SpanStyle(color = codeBrowserMuted)

    return when (classifyDiffLine(line)) {
        DiffLineKind.Meta ->
            buildAnnotatedString {
                withStyle(metaStyle) { append(line) }
            }

        DiffLineKind.Hunk ->
            buildAnnotatedString {
                withStyle(hunkStyle) { append(line) }
            }

        DiffLineKind.Added ->
            buildAnnotatedString {
                withStyle(addPrefixStyle) { append("+") }
                append(renderCodeBrowserLine(line.drop(1), language, syntaxHighlight))
            }

        DiffLineKind.Deleted ->
            buildAnnotatedString {
                withStyle(deletePrefixStyle) { append("-") }
                append(renderCodeBrowserLine(line.drop(1), language, syntaxHighlight))
            }

        DiffLineKind.Context ->
            buildAnnotatedString {
                if (line.startsWith(" ")) {
                    withStyle(contextPrefixStyle) { append(" ") }
                    append(renderCodeBrowserLine(line.drop(1), language, syntaxHighlight))
                } else {
                    append(renderCodeBrowserLine(line, language, syntaxHighlight))
                }
            }
    }
}

internal fun buildFileAnnotatedText(
    text: String,
    language: CodeLanguage,
    syntaxHighlight: Boolean,
): AnnotatedString {
    val lines = text.split('\n')
    val lineNumberWidth = maxOf(2, lines.size.toString().length)
    val lineNumberStyle = SpanStyle(color = codeBrowserMuted)

    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            withStyle(lineNumberStyle) {
                append((index + 1).toString().padStart(lineNumberWidth, ' '))
                append(" | ")
            }
            append(renderCodeBrowserLine(line, language, syntaxHighlight))
            if (index < lines.lastIndex) append('\n')
        }
    }
}

internal fun renderCodeBrowserLine(
    line: String,
    language: CodeLanguage,
    syntaxHighlight: Boolean,
): AnnotatedString {
    return if (syntaxHighlight) {
        buildSyntaxHighlightedLine(line, language)
    } else {
        AnnotatedString(line)
    }
}

internal fun buildSyntaxHighlightedLine(line: String, language: CodeLanguage): AnnotatedString {
    if (line.isEmpty()) return AnnotatedString("")

    if (language == CodeLanguage.Markdown) {
        return buildMarkdownAnnotatedLine(line)
    }
    if (language == CodeLanguage.Xml) {
        return buildXmlAnnotatedLine(line)
    }

    val palette = syntaxPalette()
    val spec = language.syntaxSpec()

    return buildAnnotatedString {
        var index = 0
        while (index < line.length) {
            val commentPrefix = spec.lineCommentPrefixes.firstOrNull { prefix -> line.startsWith(prefix, index) }
            if (commentPrefix != null) {
                withStyle(palette.comment) { append(line.substring(index)) }
                break
            }

            if (spec.blockCommentStart != null && spec.blockCommentEnd != null && line.startsWith(spec.blockCommentStart, index)) {
                val end = line.indexOf(spec.blockCommentEnd, startIndex = index + spec.blockCommentStart.length)
                val blockEnd = if (end >= 0) end + spec.blockCommentEnd.length else line.length
                withStyle(palette.comment) { append(line.substring(index, blockEnd)) }
                index = blockEnd
                continue
            }

            if (spec.supportsTripleQuotes) {
                val triple = when {
                    line.startsWith("\"\"\"", index) -> "\"\"\""
                    line.startsWith("'''", index) -> "'''"
                    else -> null
                }
                if (triple != null) {
                    val end = line.indexOf(triple, startIndex = index + triple.length)
                    val blockEnd = if (end >= 0) end + triple.length else line.length
                    withStyle(palette.string) { append(line.substring(index, blockEnd)) }
                    index = blockEnd
                    continue
                }
            }

            val ch = line[index]
            when {
                spec.supportsAnnotationPrefix && ch == '@' -> {
                    val end = readIdentifierEnd(line, index + 1)
                    if (end > index + 1) {
                        withStyle(palette.annotation) { append(line.substring(index, end)) }
                        index = end
                    } else {
                        append(ch)
                        index += 1
                    }
                }

                isStringStart(ch, spec) -> {
                    val end = readStringEnd(line, index, ch)
                    withStyle(palette.string) { append(line.substring(index, end)) }
                    index = end
                }

                ch.isDigit() -> {
                    val end = readNumberEnd(line, index)
                    withStyle(palette.number) { append(line.substring(index, end)) }
                    index = end
                }

                isIdentifierStart(ch) -> {
                    val end = readIdentifierEnd(line, index)
                    val token = line.substring(index, end)
                    val style =
                        when {
                            token in spec.keywords -> palette.keyword
                            token in spec.literals -> palette.literal
                            token.firstOrNull()?.isUpperCase() == true && token.length > 1 -> palette.type
                            else -> null
                        }
                    if (style != null) {
                        withStyle(style) { append(token) }
                    } else {
                        append(token)
                    }
                    index = end
                }

                else -> {
                    append(ch)
                    index += 1
                }
            }
        }
    }
}

internal fun buildMarkdownAnnotatedLine(line: String): AnnotatedString {
    val palette = syntaxPalette()
    return buildAnnotatedString {
        when {
            line.startsWith("```") -> withStyle(palette.keyword) { append(line) }
            line.startsWith("#") -> withStyle(palette.type) { append(line) }
            line.startsWith(">") -> withStyle(palette.comment) { append(line) }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                withStyle(palette.keyword) { append(line.take(2)) }
                append(buildInlineCodeSegments(line.drop(2), palette))
            }
            else -> append(buildInlineCodeSegments(line, palette))
        }
    }
}

internal fun buildXmlAnnotatedLine(line: String): AnnotatedString {
    val palette = syntaxPalette()
    return buildAnnotatedString {
        var index = 0
        while (index < line.length) {
            when {
                line.startsWith("<!--", index) -> {
                    val end = line.indexOf("-->", startIndex = index + 4).let { if (it >= 0) it + 3 else line.length }
                    withStyle(palette.comment) { append(line.substring(index, end)) }
                    index = end
                }
                line[index] == '<' -> {
                    val end = line.indexOf('>', startIndex = index + 1).let { if (it >= 0) it + 1 else line.length }
                    val tag = line.substring(index, end)
                    withStyle(palette.keyword) { append(tag) }
                    index = end
                }
                else -> {
                    append(line[index])
                    index += 1
                }
            }
        }
    }
}

internal fun buildInlineCodeSegments(text: String, palette: CodeSyntaxPalette): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            if (text[index] == '`') {
                val end = text.indexOf('`', startIndex = index + 1)
                if (end > index) {
                    appendHighlightedInlineCodeSegment(text.substring(index + 1, end), palette)
                    index = end + 1
                    continue
                }
            }
            append(text[index])
            index += 1
        }
    }
}

internal fun AnnotatedString.Builder.appendHighlightedInlineCodeSegment(
    text: String,
    palette: CodeSyntaxPalette,
) {
        val baseStyle = inlineCodeSpanStyle(color = codeBrowserText)
    val keywordStyle = inlineCodeSpanStyle(color = palette.keyword.color, fontWeight = palette.keyword.fontWeight)
    val literalStyle = inlineCodeSpanStyle(color = palette.literal.color, fontWeight = palette.literal.fontWeight)
    val stringStyle = inlineCodeSpanStyle(color = palette.string.color, fontWeight = palette.string.fontWeight)
    val numberStyle = inlineCodeSpanStyle(color = palette.number.color, fontWeight = palette.number.fontWeight)
    val commentStyle = inlineCodeSpanStyle(color = palette.comment.color, fontWeight = palette.comment.fontWeight)
    val typeStyle = inlineCodeSpanStyle(color = palette.type.color, fontWeight = palette.type.fontWeight)

    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("//", index) && (index == 0 || text[index - 1].isWhitespace()) -> {
                withStyle(commentStyle) { append(text.substring(index)) }
                return
            }

            text[index] == '"' || text[index] == '\'' || text[index] == '`' -> {
                val end = readStringEnd(text, index, text[index])
                withStyle(stringStyle) { append(text.substring(index, end)) }
                index = end
            }

            text[index].isDigit() -> {
                val end = readNumberEnd(text, index)
                withStyle(numberStyle) { append(text.substring(index, end)) }
                index = end
            }

            isIdentifierStart(text[index]) -> {
                val end = readIdentifierEnd(text, index)
                val token = text.substring(index, end)
                val style =
                    when {
                        token in inlineCodeKeywords() -> keywordStyle
                        token in inlineCodeLiterals() -> literalStyle
                        token.firstOrNull()?.isUpperCase() == true && token.length > 1 -> typeStyle
                        else -> baseStyle
                    }
                withStyle(style) { append(token) }
                index = end
            }

            else -> {
                withStyle(baseStyle) { append(text[index]) }
                index += 1
            }
        }
    }
}

internal fun inlineCodeSpanStyle(
    color: Color,
    fontWeight: FontWeight? = null,
): SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color, fontWeight = fontWeight)

internal fun inlineCodeKeywords(): Set<String> =
    setOf(
        "if", "else", "for", "while", "do", "switch", "case", "when", "try", "catch", "finally",
        "throw", "throws", "return", "break", "continue", "class", "interface", "enum", "object",
        "fun", "function", "def", "lambda", "async", "await", "import", "from", "export", "package",
        "public", "private", "protected", "internal", "static", "final", "abstract", "override",
        "const", "let", "var", "val", "new", "this", "super",
    )

internal fun inlineCodeLiterals(): Set<String> =
    setOf(
        "true", "false", "null", "undefined", "nil", "none", "None", "self",
    )

internal fun detectCodeLanguage(pathHint: String?): CodeLanguage {
    val path = pathHint?.trim().orEmpty().lowercase(Locale.getDefault())
    return when {
        path.endsWith(".kt") || path.endsWith(".kts") -> CodeLanguage.Kotlin
        path.endsWith(".java") -> CodeLanguage.Java
        path.endsWith(".ts") || path.endsWith(".tsx") || path.endsWith(".mts") || path.endsWith(".cts") -> CodeLanguage.TypeScript
        path.endsWith(".js") || path.endsWith(".jsx") || path.endsWith(".mjs") || path.endsWith(".cjs") -> CodeLanguage.JavaScript
        path.endsWith(".py") -> CodeLanguage.Python
        path.endsWith(".sh") || path.endsWith(".bash") || path.endsWith(".zsh") || path.endsWith(".env") || path.endsWith("dockerfile") -> CodeLanguage.Shell
        path.endsWith(".go") -> CodeLanguage.Go
        path.endsWith(".rs") -> CodeLanguage.Rust
        path.endsWith(".json") || path.endsWith(".jsonc") || path.endsWith(".json5") -> CodeLanguage.Json
        path.endsWith(".yml") || path.endsWith(".yaml") -> CodeLanguage.Yaml
        path.endsWith(".xml") || path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".svg") -> CodeLanguage.Xml
        path.endsWith(".md") || path.endsWith(".markdown") -> CodeLanguage.Markdown
        path.endsWith(".sql") -> CodeLanguage.Sql
        path.endsWith(".css") || path.endsWith(".scss") -> CodeLanguage.Css
        else -> CodeLanguage.Plain
    }
}

internal fun classifyDiffLine(line: String): DiffLineKind {
    return when {
        line.startsWith("@@") -> DiffLineKind.Hunk
        line.startsWith("diff --git") || line.startsWith("index ") || line.startsWith("--- ") || line.startsWith("+++ ") -> DiffLineKind.Meta
        line.startsWith("+") && !line.startsWith("+++") -> DiffLineKind.Added
        line.startsWith("-") && !line.startsWith("---") -> DiffLineKind.Deleted
        else -> DiffLineKind.Context
    }
}

internal fun syntaxPalette(): CodeSyntaxPalette {
    return CodeSyntaxPalette(
        keyword = SpanStyle(color = c(0xFF7DD3FC), fontWeight = FontWeight.SemiBold),
            literal = SpanStyle(color = codeBrowserPrimary, fontWeight = FontWeight.SemiBold),
        string = SpanStyle(color = c(0xFFF9A66C)),
        number = SpanStyle(color = c(0xFFC084FC)),
            comment = SpanStyle(color = codeBrowserMuted),
        type = SpanStyle(color = c(0xFFD97706), fontWeight = FontWeight.SemiBold),
        annotation = SpanStyle(color = c(0xFFF472B6)),
    )
}

internal fun isStringStart(ch: Char, spec: CodeLanguageSpec): Boolean {
    return ch == '"' || ch == '\'' || (spec.supportsBackticks && ch == '`')
}

internal fun readStringEnd(line: String, start: Int, delimiter: Char): Int {
    var index = start + 1
    var escaped = false
    while (index < line.length) {
        val current = line[index]
        if (escaped) {
            escaped = false
        } else if (current == '\\') {
            escaped = true
        } else if (current == delimiter) {
            return index + 1
        }
        index += 1
    }
    return line.length
}

internal fun readNumberEnd(line: String, start: Int): Int {
    var index = start
    while (index < line.length) {
        val ch = line[index]
        if (ch.isDigit() || ch in listOf('.', '_', 'x', 'X', 'o', 'O', 'b', 'B', 'a', 'A', 'c', 'C', 'd', 'D', 'e', 'E', 'f', 'F')) {
            index += 1
        } else {
            break
        }
    }
    return index
}

internal fun readIdentifierEnd(line: String, start: Int): Int {
    var index = start
    while (index < line.length && isIdentifierPart(line[index])) {
        index += 1
    }
    return index
}

internal fun isIdentifierStart(ch: Char): Boolean = ch == '_' || ch == '$' || ch.isLetter()

internal fun isIdentifierPart(ch: Char): Boolean = isIdentifierStart(ch) || ch.isDigit()

internal fun c(argb: Int): Color = Color(argb)

internal fun c(argb: Long): Color = Color(argb.toInt())
