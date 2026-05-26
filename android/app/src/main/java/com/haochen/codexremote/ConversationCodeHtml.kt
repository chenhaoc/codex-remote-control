package com.haochen.codexremote

internal enum class ConversationCodeLanguage {
    Plain,
    Kotlin,
    Java,
    TypeScript,
    JavaScript,
    Python,
    Shell,
    Go,
    Rust,
    Json,
    Yaml,
    Xml,
    Markdown,
    Sql,
    Css,
}

internal fun detectConversationCodeLanguage(pathHint: String?): ConversationCodeLanguage {
    val path = pathHint?.trim().orEmpty().lowercase()
    return when {
        path.endsWith(".kt") || path.endsWith(".kts") -> ConversationCodeLanguage.Kotlin
        path.endsWith(".java") -> ConversationCodeLanguage.Java
        path.endsWith(".ts") || path.endsWith(".tsx") || path.endsWith(".mts") || path.endsWith(".cts") -> ConversationCodeLanguage.TypeScript
        path.endsWith(".js") || path.endsWith(".jsx") || path.endsWith(".mjs") || path.endsWith(".cjs") -> ConversationCodeLanguage.JavaScript
        path.endsWith(".py") -> ConversationCodeLanguage.Python
        path.endsWith(".sh") || path.endsWith(".bash") || path.endsWith(".zsh") || path.endsWith(".env") || path.endsWith("dockerfile") -> ConversationCodeLanguage.Shell
        path.endsWith(".go") -> ConversationCodeLanguage.Go
        path.endsWith(".rs") -> ConversationCodeLanguage.Rust
        path.endsWith(".json") || path.endsWith(".jsonc") || path.endsWith(".json5") -> ConversationCodeLanguage.Json
        path.endsWith(".yml") || path.endsWith(".yaml") -> ConversationCodeLanguage.Yaml
        path.endsWith(".xml") || path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".svg") -> ConversationCodeLanguage.Xml
        path.endsWith(".md") || path.endsWith(".markdown") -> ConversationCodeLanguage.Markdown
        path.endsWith(".sql") -> ConversationCodeLanguage.Sql
        path.endsWith(".css") || path.endsWith(".scss") -> ConversationCodeLanguage.Css
        else -> ConversationCodeLanguage.Plain
    }
}

internal fun buildConversationHighlightedCodeHtml(
    text: String,
    language: ConversationCodeLanguage,
): String =
    buildString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("//", index) && (index == 0 || text[index - 1].isWhitespace()) -> {
                    append(wrapConversationDiffCodeToken("comment", text.substring(index)))
                    return@buildString
                }

                text[index] == '"' || text[index] == '\'' || text[index] == '`' -> {
                    val end = readConversationInlineCodeStringEnd(text, index, text[index])
                    append(wrapConversationDiffCodeToken("string", text.substring(index, end)))
                    index = end
                }

                text[index].isDigit() -> {
                    val end = readConversationInlineCodeNumberEnd(text, index)
                    append(wrapConversationDiffCodeToken("number", text.substring(index, end)))
                    index = end
                }

                isConversationInlineCodeIdentifierStart(text[index]) -> {
                    val end = readConversationInlineCodeIdentifierEnd(text, index)
                    val token = text.substring(index, end)
                    val kind =
                        when {
                            token in conversationCodeKeywords(language) -> "keyword"
                            token in conversationInlineCodeLiterals() -> "literal"
                            token.firstOrNull()?.isUpperCase() == true && token.length > 1 -> "type"
                            else -> null
                        }
                    if (kind == null) {
                        append(escapeConversationHtml(token))
                    } else {
                        append(wrapConversationDiffCodeToken(kind, token))
                    }
                    index = end
                }

                else -> {
                    append(escapeConversationHtml(text[index].toString()))
                    index += 1
                }
            }
        }
    }

internal fun wrapConversationDiffCodeToken(
    kind: String,
    text: String,
): String = """<span class="cr-diff-code-$kind">${escapeConversationHtml(text)}</span>"""

internal fun conversationCodeKeywords(language: ConversationCodeLanguage): Set<String> =
    when (language) {
        ConversationCodeLanguage.Kotlin ->
            setOf(
                "package", "import", "class", "interface", "object", "fun", "val", "var", "if", "else",
                "when", "for", "while", "return", "null", "true", "false", "data", "sealed", "private",
                "public", "internal", "override", "companion", "suspend", "try", "catch",
            )

        ConversationCodeLanguage.Java ->
            setOf(
                "package", "import", "class", "interface", "enum", "public", "private", "protected",
                "static", "final", "void", "new", "if", "else", "for", "while", "return", "null",
                "true", "false", "try", "catch", "throws", "extends", "implements",
            )

        ConversationCodeLanguage.TypeScript, ConversationCodeLanguage.JavaScript ->
            setOf(
                "import", "export", "from", "class", "interface", "type", "const", "let", "var",
                "function", "return", "if", "else", "for", "while", "switch", "case", "new", "await",
                "async", "null", "undefined", "true", "false", "try", "catch",
            )

        ConversationCodeLanguage.Python ->
            setOf(
                "import", "from", "class", "def", "return", "if", "elif", "else", "for", "while",
                "try", "except", "finally", "with", "as", "pass", "None", "True", "False", "async", "await",
            )

        ConversationCodeLanguage.Shell ->
            setOf(
                "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "case", "esac",
                "while", "until", "function", "export", "local", "readonly",
            )

        ConversationCodeLanguage.Go ->
            setOf(
                "package", "import", "func", "type", "struct", "interface", "if", "else", "for",
                "range", "return", "go", "defer", "nil", "true", "false",
            )

        ConversationCodeLanguage.Rust ->
            setOf(
                "use", "mod", "fn", "let", "mut", "pub", "struct", "enum", "impl", "trait", "if",
                "else", "match", "for", "while", "loop", "return", "Self", "self", "true", "false",
            )

        ConversationCodeLanguage.Json ->
            setOf("true", "false", "null")

        ConversationCodeLanguage.Yaml ->
            setOf("true", "false", "null")

        ConversationCodeLanguage.Xml, ConversationCodeLanguage.Markdown, ConversationCodeLanguage.Sql, ConversationCodeLanguage.Css, ConversationCodeLanguage.Plain ->
            setOf()
    }

internal fun buildConversationInlineCodeHtml(text: String): String {
    val fileTarget = conversationFileTargetFromLinkTarget(text)?.withFallbackLine(text)
    val displayText = fileTarget?.appendLineToLabel(text) ?: text
    val codeHtml =
        buildString {
            append("""<code class="cr-md-inline-code">""")
            append(highlightConversationInlineCode(displayText))
            append("</code>")
        }
    return fileTarget?.let {
        """<a class="cr-file-path-link" href="${it.browserHref()}">$codeHtml</a>"""
    } ?: codeHtml
}

internal fun highlightConversationInlineCode(text: String): String =
    buildString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("//", index) && (index == 0 || text[index - 1].isWhitespace()) -> {
                    append(wrapConversationInlineCodeToken("comment", text.substring(index)))
                    return@buildString
                }

                text[index] == '"' || text[index] == '\'' || text[index] == '`' -> {
                    val end = readConversationInlineCodeStringEnd(text, index, text[index])
                    append(wrapConversationInlineCodeToken("string", text.substring(index, end)))
                    index = end
                }

                text[index].isDigit() -> {
                    val end = readConversationInlineCodeNumberEnd(text, index)
                    append(wrapConversationInlineCodeToken("number", text.substring(index, end)))
                    index = end
                }

                isConversationInlineCodeIdentifierStart(text[index]) -> {
                    val end = readConversationInlineCodeIdentifierEnd(text, index)
                    val token = text.substring(index, end)
                    val kind =
                        when {
                            token in conversationInlineCodeKeywords() -> "keyword"
                            token in conversationInlineCodeLiterals() -> "literal"
                            token.firstOrNull()?.isUpperCase() == true && token.length > 1 -> "type"
                            else -> null
                        }
                    if (kind == null) {
                        append(escapeConversationHtml(token))
                    } else {
                        append(wrapConversationInlineCodeToken(kind, token))
                    }
                    index = end
                }

                else -> {
                    append(escapeConversationHtml(text[index].toString()))
                    index += 1
                }
            }
        }
    }

internal fun wrapConversationInlineCodeToken(
    kind: String,
    text: String,
): String = """<span class="cr-md-inline-code-$kind">${escapeConversationHtml(text)}</span>"""

internal fun conversationInlineCodeKeywords(): Set<String> =
    setOf(
        "if", "else", "for", "while", "do", "switch", "case", "when", "try", "catch", "finally",
        "throw", "throws", "return", "break", "continue", "class", "interface", "enum", "object",
        "fun", "function", "def", "lambda", "async", "await", "import", "from", "export", "package",
        "public", "private", "protected", "internal", "static", "final", "abstract", "override",
        "const", "let", "var", "val", "new", "this", "super",
    )

internal fun conversationInlineCodeLiterals(): Set<String> =
    setOf(
        "true", "false", "null", "undefined", "nil", "none", "None", "self",
    )

internal fun isConversationInlineCodeIdentifierStart(ch: Char): Boolean = ch == '_' || ch == '$' || ch.isLetter()

internal fun readConversationInlineCodeIdentifierEnd(
    text: String,
    start: Int,
): Int {
    var index = start + 1
    while (index < text.length) {
        val ch = text[index]
        if (!(ch == '_' || ch == '$' || ch.isLetterOrDigit())) break
        index += 1
    }
    return index
}

internal fun readConversationInlineCodeNumberEnd(
    text: String,
    start: Int,
): Int {
    var index = start + 1
    while (index < text.length) {
        val ch = text[index]
        if (!(ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == 'x' || ch == 'X')) break
        index += 1
    }
    return index
}

internal fun readConversationInlineCodeStringEnd(
    text: String,
    start: Int,
    delimiter: Char,
): Int {
    var index = start + 1
    var escaped = false
    while (index < text.length) {
        val ch = text[index]
        if (escaped) {
            escaped = false
        } else if (ch == '\\') {
            escaped = true
        } else if (ch == delimiter) {
            return index + 1
        }
        index += 1
    }
    return text.length
}

internal fun isConversationMarkdownTableHeaderLine(line: String): Boolean = line.contains('|')

internal fun isConversationMarkdownTableBodyLine(line: String): Boolean =
    line.isNotBlank() && line.contains('|') && !conversationMarkdownTableSeparatorRegex.matches(line)

internal fun splitConversationMarkdownTableRow(line: String): List<String> =
    line.trim()
        .removePrefix("|")
        .removeSuffix("|")
        .split('|')
        .map { it.trim() }
