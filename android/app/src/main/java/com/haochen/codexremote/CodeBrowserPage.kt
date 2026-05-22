package com.haochen.codexremote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
internal fun MainActivity.CodeBrowserPage(
    state: CodeBrowserState,
    modifier: Modifier = Modifier,
) {
    val selectedEntry = state.selectedEntry()
    val diffStats = buildDiffStatsLine(diffEntries = state.diffEntries, fallbackDiff = state.fallbackDiff)
    var fileListExpanded by remember(state.conversationItemId) { mutableStateOf(false) }
    val selectedPathLabel =
        selectedEntry?.displayPath(basePath = state.basePath, maxLength = 96)
            ?: state.selectedPath?.takeIf { it.isNotBlank() }?.let {
                compactDiffDisplayPath(it, state.basePath, maxLength = 96)
            }
            ?: "未选择文件"

    LaunchedEffect(state.conversationItemId, state.mode, state.selectedPath, state.fileReadState) {
        if (state.mode == CodeBrowserMode.File && selectedEntry != null && state.fileReadState is CodeBrowserFileReadState.Idle) {
            loadCodeBrowserFileContent(state)
        }
    }

    Box(
        modifier = modifier
            .background(uiBackground)
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "文件修改",
                    color = uiText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                diffStats?.let {
                    DiffStatsText(
                        label = it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (state.diffEntries.isNotEmpty()) {
                val visibleEntries =
                    if (fileListExpanded || state.diffEntries.size <= 4) {
                        state.diffEntries
                    } else {
                        state.diffEntries.take(4)
                    }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    visibleEntries.forEachIndexed { index, entry ->
                        CodeBrowserFileRow(
                            label = entry.filenameLabel(),
                            stats = entry.diffStatsLabel(),
                            selected = state.selectedPath == entry.browsePath(),
                            onClick = {
                                selectCodeBrowserPath(entry.browsePath())
                                setCodeBrowserMode(CodeBrowserMode.Diff)
                            },
                        )
                        if (index < visibleEntries.lastIndex) {
                            HorizontalDivider(color = uiBorder.copy(alpha = 0.75f))
                        }
                    }
                    if (state.diffEntries.size > 4) {
                        HorizontalDivider(color = uiBorder.copy(alpha = 0.75f))
                        TextButton(
                            onClick = { fileListExpanded = !fileListExpanded },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (fileListExpanded) {
                                    "收起文件列表"
                                } else {
                                    "展开剩余 ${state.diffEntries.size - visibleEntries.size} 个文件"
                                },
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CodeBrowserModeButton(
                    text = "Diff",
                    selected = state.mode == CodeBrowserMode.Diff,
                    modifier = Modifier.weight(1f),
                    onClick = { setCodeBrowserMode(CodeBrowserMode.Diff) },
                )
                CodeBrowserModeButton(
                    text = "文件",
                    selected = state.mode == CodeBrowserMode.File,
                    enabled = selectedEntry != null,
                    modifier = Modifier.weight(1f),
                    onClick = { setCodeBrowserMode(CodeBrowserMode.File) },
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = uiSurfaceAlt,
                border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
            ) {
                when (state.mode) {
                    CodeBrowserMode.Diff -> {
                        val diffText = selectedEntry?.diff ?: state.fallbackDiff.orEmpty()
                        if (diffText.isBlank()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                BodyText("这个条目暂时没有可展示的 diff。")
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (selectedEntry != null) {
                                    Text(
                                        text = selectedPathLabel,
                                        color = uiMuted,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                CodeBrowserTextPane(
                                    text = diffText,
                                    mode = CodeTextMode.Diff,
                                    pathHint = selectedEntry?.browsePath(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            }
                        }
                    }

                    CodeBrowserMode.File -> {
                        when (val fileState = state.fileReadState) {
                            CodeBrowserFileReadState.Idle -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = selectedPathLabel,
                                        color = uiMuted,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        BodyText("正在准备读取文件内容…")
                                    }
                                }
                            }

                            CodeBrowserFileReadState.Loading -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = selectedPathLabel,
                                        color = uiMuted,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = uiPrimary)
                                    }
                                }
                            }

                            is CodeBrowserFileReadState.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = selectedPathLabel,
                                        color = uiMuted,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BodyText(fileState.message)
                                    }
                                }
                            }

                            is CodeBrowserFileReadState.Loaded -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = compactDiffDisplayPath(fileState.content.resolvedPath, state.basePath, maxLength = 96),
                                        color = uiMuted,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    CodeBrowserTextPane(
                                        text = fileState.content.content,
                                        mode = CodeTextMode.File,
                                        pathHint = fileState.content.resolvedPath,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MainActivity.CodeBrowserModeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = uiPrimary),
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(text)
        }
    }
}

@Composable
internal fun MainActivity.CodeBrowserFileRow(
    label: String,
    stats: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) uiPrimarySoft else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = uiText,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        stats?.let {
            DiffStatsText(
                label = it,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun MainActivity.DiffStatsText(
    label: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    val annotated =
        remember(label, fontSize, fontWeight) {
            buildAnnotatedString {
                var index = 0
                while (index < label.length) {
                    when {
                        label[index] == '+' -> {
                            val end = readDiffStatsSegmentEnd(label, index + 1)
                            withStyle(SpanStyle(color = uiPrimary, fontWeight = fontWeight)) {
                                append(label.substring(index, end))
                            }
                            index = end
                        }

                        label[index] == '-' -> {
                            val end = readDiffStatsSegmentEnd(label, index + 1)
                            withStyle(SpanStyle(color = Color(0xFFDC2626), fontWeight = fontWeight)) {
                                append(label.substring(index, end))
                            }
                            index = end
                        }

                        else -> {
                            withStyle(SpanStyle(color = uiMuted, fontWeight = fontWeight)) {
                                append(label[index])
                            }
                            index += 1
                        }
                    }
                }
            }
        }
    Text(
        text = annotated,
        color = uiMuted,
        fontSize = fontSize,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun MainActivity.readDiffStatsSegmentEnd(
    label: String,
    start: Int,
): Int {
    var index = start
    while (index < label.length && label[index].isDigit()) {
        index += 1
    }
    return index
}

@Composable
internal fun MainActivity.CodeBrowserTextPane(
    text: String,
    mode: CodeTextMode,
    pathHint: String? = null,
    modifier: Modifier = Modifier,
) {
    val verticalScroll = rememberScrollState()
    val cacheKey = remember(mode, pathHint, text.length, text.hashCode()) {
        buildCodeBrowserRenderCacheKey(text, mode, pathHint)
    }
    val rendered by produceState<CodeBrowserRenderedContent?>(
        initialValue = codeBrowserRenderCache[cacheKey],
        key1 = cacheKey,
    ) {
        codeBrowserRenderCache[cacheKey]?.let { cached ->
            value = cached
            return@produceState
        }
        value = null
        val computed =
            withContext(Dispatchers.Default) {
                buildCodeBrowserRenderedContent(text, mode, pathHint)
            }
        codeBrowserRenderCache[cacheKey] = computed
        value = computed
    }

    if (rendered == null) {
        Box(
            modifier = modifier
                .background(uiSurfaceAlt),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = uiPrimary)
        }
        return
    }

    SelectionContainer {
        Box(
            modifier = modifier
                .background(uiSurfaceAlt)
                .verticalScroll(verticalScroll)
                .padding(14.dp),
        ) {
            val renderedContent = rendered
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (renderedContent?.lightweight == true) {
                    Text(
                        text = "大文件已切换到轻量渲染，优先保证打开速度。",
                        color = uiMuted,
                        fontSize = 11.sp,
                    )
                }
                if (mode == CodeTextMode.Diff && !renderedContent?.lines.isNullOrEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.fillMaxWidth()) {
                        renderedContent?.lines.orEmpty().forEach { line ->
                            val lineBackground =
                                when (line.kind) {
                                    DiffLineKind.Added -> c(0x3427A55A)
                                    DiffLineKind.Deleted -> c(0x34DC2626)
                                    else -> Color.Transparent
                                }
                            Text(
                                text = line.text,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(lineBackground, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                color = uiText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                } else {
                    Text(
                        text = rendered?.text ?: AnnotatedString(""),
                        color = uiText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}
