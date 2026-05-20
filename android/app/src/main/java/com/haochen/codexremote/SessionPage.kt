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
internal fun MainActivity.NewChatSheet(
    draft: NewChatDraft,
    onDismiss: () -> Unit,
    onModelChange: (String) -> Unit,
    onReasoningEffortChange: (String) -> Unit,
    onApprovalPolicyChange: (String) -> Unit,
    onSandboxChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    var modelExpanded by remember { mutableStateOf(false) }
    var effortExpanded by remember { mutableStateOf(false) }
    var approvalExpanded by remember { mutableStateOf(false) }
    var sandboxExpanded by remember { mutableStateOf(false) }
    val modelOptions = availableModels.ifEmpty {
        listOfNotNull(
            selectedModel.takeIf { it.isNotBlank() }?.let {
                ModelInfo(
                    id = it,
                    model = it,
                    displayName = it,
                    description = "",
                    hidden = false,
                    isDefault = true,
                    supportedReasoningEfforts = emptyList(),
                    defaultReasoningEffort = "medium",
                )
            },
        )
    }
    val selectedModelInfo =
        modelOptions.firstOrNull { it.model == draft.model || it.id == draft.model }
            ?: modelInfoFor(draft.model)
    val reasoningOptions = reasoningEffortOptionsForModel(draft.model)
    val selectedReasoningOption =
        reasoningOptions.firstOrNull { it.value == draft.reasoningEffort }
            ?: reasoningOptionFor(draft.reasoningEffort)
    val approvalOptions = approvalPolicyMenuOptions()
    val sandboxOptions = sandboxMenuOptions()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "新对话",
                color = uiText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = draft.projectPath ?: "先选项目，再选模型、思考强度、审批策略和沙箱。",
                color = uiMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionMenuField(
                label = "模型",
                selectedLabel = selectedModelInfo?.displayName ?: draft.model.ifBlank { "请选择模型" },
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = it },
                enabled = modelOptions.isNotEmpty(),
                options = modelOptions.map { model ->
                    SelectionMenuOption(
                        value = model.model,
                        label = model.displayName,
                        supporting = model.description.takeIf { it.isNotBlank() },
                    )
                },
                onSelect = onModelChange,
            )
            if (modelOptions.isEmpty()) {
                BodyText("模型列表还没加载完成。")
            } else {
                selectedModelInfo?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        color = uiMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionMenuField(
                label = "思考强度",
                selectedLabel = selectedReasoningOption.label,
                expanded = effortExpanded,
                onExpandedChange = { effortExpanded = it },
                enabled = reasoningOptions.isNotEmpty(),
                options = reasoningOptions,
                onSelect = onReasoningEffortChange,
            )
            Text(
                text = selectedReasoningOption.supporting.orEmpty(),
                color = uiMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionMenuField(
                label = "审批策略",
                selectedLabel = approvalOptions.firstOrNull { it.value == draft.approvalPolicy }?.label ?: draft.approvalPolicy,
                expanded = approvalExpanded,
                onExpandedChange = { approvalExpanded = it },
                options = approvalOptions,
                onSelect = onApprovalPolicyChange,
            )
            Text(
                text = approvalPolicyDescription(draft.approvalPolicy),
                color = uiMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Text(
                text = "permission profile 不单独手填；新对话按当前默认执行，历史对话继续沿用原会话配置。",
                color = uiMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionMenuField(
                label = "沙箱",
                selectedLabel = sandboxOptions.firstOrNull { it.value == draft.sandbox }?.label ?: draft.sandbox,
                expanded = sandboxExpanded,
                onExpandedChange = { sandboxExpanded = it },
                options = sandboxOptions,
                onSelect = onSandboxChange,
            )
            Text(
                text = sandboxDescription(draft.sandbox),
                color = uiMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                enabled = draft.model.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = uiPrimary),
            ) {
                Text("确定")
            }
        }
    }
}

@Composable
internal fun MainActivity.SelectionMenuField(
    label: String,
    selectedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<SelectionMenuOption>,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(label)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onExpandedChange(!expanded) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedLabel,
                        color = uiText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        color = uiMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            DropdownMenu(
                expanded = enabled && expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.fillMaxWidth(),
                containerColor = uiSurface,
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(option.label)
                                option.supporting?.takeIf { it.isNotBlank() }?.let { supporting ->
                                    Text(
                                        text = supporting,
                                        color = uiMuted,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onExpandedChange(false)
                            onSelect(option.value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun MainActivity.SessionInfoSheet(
    state: SessionInfoSheetState,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "会话信息",
                    color = uiText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.title,
                    color = uiMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.rows.forEach { (label, value) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = uiSurfaceAlt,
                    border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = label,
                            color = uiMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = value,
                            color = uiText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }
        }
    }
}


@Composable
internal fun MainActivity.SessionPage() {
    val sessionListState = rememberLazyListState()
    var modelPickerExpanded by remember { mutableStateOf(false) }
    val filteredSessions = sessionsForSelectedWorkspace()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = uiSurface),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("新会话模型")
                    FilledTonalButton(onClick = { modelPickerExpanded = !modelPickerExpanded }) {
                        Text(if (modelPickerExpanded) "收起列表" else "展开列表")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                BodyText(
                    if (selectedModel.isBlank()) {
                        "先选模型，再点新会话。也可以直接在输入框里手填模型 ID。"
                    } else {
                        "当前模型: $selectedModel"
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { selectModel(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text("例如 gpt-5.1") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = uiPrimary,
                        unfocusedBorderColor = uiBorder,
                        focusedTextColor = uiText,
                        unfocusedTextColor = uiText,
                        cursorColor = uiPrimary,
                    ),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { requestModelList() },
                        enabled = connected,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("刷新模型")
                    }
                    Button(
                        onClick = { openNewChatDialog(currentWorkspacePath()) },
                        enabled = connected,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = uiPrimary),
                    ) {
                        Text("新会话")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (availableModels.isEmpty()) {
                    BodyText(if (connected) "连接后会自动加载模型列表。" else "连接后这里会显示可选模型。")
                } else if (modelPickerExpanded) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 148.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = availableModels, key = { it.id }) { model ->
                            ModelCard(
                                model = model,
                                selected = model.model == selectedModel.trim(),
                                onClick = { selectModel(model.model) },
                            )
                        }
                    }
                } else {
                    BodyText("已加载 ${availableModels.size} 个模型，展开后可切换。")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionTitle("会话历史")
                selectedWorkspace?.let {
                    Text(
                        text = workspaceDisplayName(it, fallback = it),
                        color = uiMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            FilledTonalButton(onClick = { requestSessionList() }, enabled = connected) {
                Text("刷新")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = uiSurface),
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
        ) {
            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BodyText(
                        if (connected) {
                            if (selectedWorkspace == null) "暂无会话，先创建一个新的吧。" else "这个 workspace 暂无会话。"
                        } else {
                            "连接后这里会显示会话列表。"
                        },
                    )
                }
            } else {
                LazyColumn(
                    state = sessionListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 12.dp, top = 18.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    items(items = filteredSessions, key = { it.sessionId }) { session ->
                        SessionCard(
                            session = session,
                            selected = session.sessionId == activeSessionId,
                            onClick = {
                                selectSession(session.sessionId, true)
                                selectedTab = AppTab.Chat
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MainActivity.SessionCard(
    session: SessionInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) uiPrimarySoft else uiSurfaceAlt,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, uiPrimary) else androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = session.titleLine(),
                color = uiText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = session.updatedAtLabel(),
                color = uiMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MainActivity.ModelCard(
    model: ModelInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) uiPrimarySoft else uiSurfaceAlt,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, uiPrimary) else androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = model.displayName,
                    color = uiText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.widthIn(min = 8.dp))
                Text(
                    text = if (model.isDefault) "默认" else model.model,
                    color = uiMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (model.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = model.description,
                    color = uiMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
