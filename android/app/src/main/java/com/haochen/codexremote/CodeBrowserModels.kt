package com.haochen.codexremote

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString

internal enum class CodeTextMode {
    Diff,
    File,
}

internal enum class CodeLanguage {
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

internal enum class DiffLineKind {
    Meta,
    Hunk,
    Added,
    Deleted,
    Context,
}

internal data class CodeSyntaxPalette(
    val keyword: SpanStyle,
    val literal: SpanStyle,
    val string: SpanStyle,
    val number: SpanStyle,
    val comment: SpanStyle,
    val type: SpanStyle,
    val annotation: SpanStyle,
)

internal data class CodeLanguageSpec(
    val lineCommentPrefixes: List<String>,
    val blockCommentStart: String? = null,
    val blockCommentEnd: String? = null,
    val supportsBackticks: Boolean = false,
    val supportsTripleQuotes: Boolean = false,
    val supportsAnnotationPrefix: Boolean = false,
    val keywords: Set<String> = emptySet(),
    val literals: Set<String> = emptySet(),
)

internal fun CodeLanguage.syntaxSpec(): CodeLanguageSpec {
    return when (this) {
        CodeLanguage.Kotlin -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("//"),
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            supportsTripleQuotes = true,
            supportsAnnotationPrefix = true,
            keywords = setOf(
                "package", "import", "class", "interface", "object", "fun", "val", "var", "when", "if", "else",
                "for", "while", "do", "return", "break", "continue", "try", "catch", "finally", "throw",
                "private", "protected", "public", "internal", "open", "override", "suspend", "inline", "reified",
                "operator", "infix", "tailrec", "data", "sealed", "enum", "annotation", "companion", "init",
                "constructor", "this", "super", "in", "is", "as", "by", "where", "typealias", "get", "set",
            ),
            literals = setOf("true", "false", "null"),
        )

        CodeLanguage.Java -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("//"),
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            supportsAnnotationPrefix = true,
            keywords = setOf(
                "package", "import", "class", "interface", "enum", "record", "public", "private", "protected",
                "static", "final", "abstract", "volatile", "synchronized", "transient", "native", "strictfp",
                "void", "new", "return", "if", "else", "switch", "case", "default", "for", "while", "do",
                "break", "continue", "try", "catch", "finally", "throw", "throws", "extends", "implements",
                "instanceof", "this", "super",
            ),
            literals = setOf("true", "false", "null"),
        )

        CodeLanguage.TypeScript -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("//"),
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            supportsBackticks = true,
            keywords = setOf(
                "import", "export", "from", "as", "type", "interface", "namespace", "declare", "module", "class",
                "extends", "implements", "new", "function", "const", "let", "var", "return", "if", "else",
                "switch", "case", "default", "for", "while", "do", "break", "continue", "try", "catch",
                "finally", "throw", "async", "await", "yield", "in", "of", "instanceof", "typeof", "keyof",
                "readonly", "public", "private", "protected", "abstract", "override",
            ),
            literals = setOf("true", "false", "null", "undefined"),
        )

        CodeLanguage.JavaScript -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("//"),
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            supportsBackticks = true,
            keywords = setOf(
                "import", "export", "from", "class", "extends", "new", "function", "const", "let", "var",
                "return", "if", "else", "switch", "case", "default", "for", "while", "do", "break", "continue",
                "try", "catch", "finally", "throw", "async", "await", "yield", "in", "of", "instanceof", "typeof",
                "this", "super",
            ),
            literals = setOf("true", "false", "null", "undefined"),
        )

        CodeLanguage.Python -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("#"),
            supportsTripleQuotes = true,
            keywords = setOf(
                "def", "class", "if", "elif", "else", "for", "while", "try", "except", "finally", "return",
                "yield", "lambda", "with", "as", "from", "import", "pass", "break", "continue", "match", "case",
                "async", "await", "raise", "global", "nonlocal", "assert", "del", "in", "is", "not", "and", "or",
            ),
            literals = setOf("True", "False", "None"),
        )

        CodeLanguage.Shell -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("#"),
            keywords = setOf(
                "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "case", "esac", "while", "until",
                "function", "select", "time", "coproc", "local", "readonly", "export", "source",
            ),
            literals = setOf("true", "false"),
        )

        CodeLanguage.Go -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("//"),
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            keywords = setOf(
                "package", "import", "func", "type", "struct", "interface", "map", "chan", "var", "const",
                "return", "if", "else", "switch", "case", "default", "for", "range", "go", "defer", "select",
                "break", "continue", "fallthrough",
            ),
            literals = setOf("true", "false", "nil"),
        )

        CodeLanguage.Rust -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("//"),
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            keywords = setOf(
                "use", "mod", "pub", "crate", "super", "self", "fn", "let", "mut", "struct", "enum", "trait",
                "impl", "match", "if", "else", "loop", "while", "for", "in", "return", "break", "continue",
                "async", "await", "where", "const", "static", "type", "unsafe", "move", "dyn", "ref", "as",
            ),
            literals = setOf("true", "false", "None", "Some"),
        )

        CodeLanguage.Json -> CodeLanguageSpec(
            lineCommentPrefixes = emptyList(),
            literals = setOf("true", "false", "null"),
        )

        CodeLanguage.Yaml -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("#"),
            literals = setOf("true", "false", "null", "yes", "no", "on", "off"),
        )

        CodeLanguage.Sql -> CodeLanguageSpec(
            lineCommentPrefixes = listOf("--"),
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            keywords = setOf(
                "select", "from", "where", "group", "order", "by", "join", "left", "right", "inner", "outer",
                "on", "insert", "into", "values", "update", "delete", "create", "table", "alter", "drop", "union",
                "limit", "offset", "having", "distinct", "case", "when", "then", "else", "end", "and", "or", "not",
            ),
            literals = setOf("true", "false", "null"),
        )

        CodeLanguage.Css -> CodeLanguageSpec(
            lineCommentPrefixes = emptyList(),
            blockCommentStart = "/*",
            blockCommentEnd = "*/",
            literals = setOf("true", "false", "null"),
        )

        else -> CodeLanguageSpec(
            lineCommentPrefixes = emptyList(),
            literals = setOf("true", "false", "null"),
        )
    }
}

internal sealed interface CodeBrowserFileReadState {
    data object Idle : CodeBrowserFileReadState

    data object Loading : CodeBrowserFileReadState

    data class Loaded(
        val content: CodeBrowserFileContent,
    ) : CodeBrowserFileReadState

    data class Error(
        val message: String,
    ) : CodeBrowserFileReadState
}

internal data class CodeBrowserFileContent(
    val requestedPath: String,
    val resolvedPath: String,
    val content: String,
    val truncated: Boolean,
    val bytes: Int,
)

internal data class CodeBrowserRenderedContent(
    val text: AnnotatedString,
    val lines: List<CodeBrowserRenderedLine> = emptyList(),
    val lightweight: Boolean,
)

internal data class CodeBrowserRenderedLine(
    val text: AnnotatedString,
    val kind: DiffLineKind?,
)
