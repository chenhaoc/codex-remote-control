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
internal fun MainActivity.AppDrawerContent(onCloseDrawer: () -> Unit) {
    val projectGroups = projectBuckets()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("侧边栏")
                IconButton(onClick = onCloseDrawer) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                        contentDescription = "收起侧边栏",
                        tint = uiMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = uiSurface),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "连接设置",
                                color = uiText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = drawerConnectionSummaryText(),
                                color = uiMuted,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        StatusDot(active = connected)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                selectedTab = AppTab.Connection
                                onCloseDrawer()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("连接设置")
                        }
                        Button(
                            onClick = { openNewChatDialog(currentWorkspacePath()) },
                            enabled = connected,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = uiPrimary),
                        ) {
                            Text("新对话")
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("项目")
        }

        if (projectGroups.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = uiSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
                ) {
                    BodyText(
                        text = if (connected) "连接后，项目和历史会话会显示在这里。" else "先连接 bridge，再来创建新对话。",
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(items = projectGroups, key = { it.key() }) { group ->
                ProjectGroupCard(
                    group = group,
                    onNewChat = {
                        openNewChatDialog(group.path)
                        onCloseDrawer()
                    },
                    onSelectSession = { session ->
                        selectSession(session.sessionId, true)
                        selectedWorkspace = group.path
                        selectedTab = AppTab.Chat
                        onCloseDrawer()
                    },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun MainActivity.ProjectGroupCard(
    group: ProjectGroup,
    onNewChat: () -> Unit,
    onSelectSession: (SessionInfo) -> Unit,
) {
    val containsActiveSession = group.sessions.any { it.sessionId == activeSessionId }
    var expanded by remember(group.key()) { mutableStateOf(containsActiveSession) }

    LaunchedEffect(containsActiveSession) {
        if (containsActiveSession) expanded = true
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = uiSurface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = group.displayName,
                        color = uiText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${group.sessions.size}",
                        color = uiMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = if (expanded) "▴" else "▾",
                        color = uiMuted,
                        fontSize = 16.sp,
                    )
                }
                TextButton(onClick = onNewChat) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_input_add),
                        contentDescription = "新对话",
                        tint = uiPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (expanded) {
                if (group.sessions.isEmpty()) {
                    BodyText("这个项目暂时还没有历史会话。")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.sessions.forEach { session ->
                            SessionCard(
                                session = session,
                                selected = session.sessionId == activeSessionId,
                                onClick = { onSelectSession(session) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MainActivity.DrawerWorkspaceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) uiPrimary else uiSurfaceAlt,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, uiPrimary) else androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = if (selected) Color.White else uiText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
