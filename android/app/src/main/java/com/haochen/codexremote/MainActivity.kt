package com.haochen.codexremote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.DividerDefaults
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
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS = "codex_remote_settings"
        private const val KEY_URL = "bridge_url"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "token"
        private const val KEY_CONNECTION_HISTORY = "connection_history"
        private const val KEY_SESSION = "session"
        private const val KEY_MODEL = "model"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val MAX_CONNECTION_HISTORY = 8
        private const val CODE_BROWSER_FILE_CACHE_SIZE = 24
        private const val CODE_BROWSER_RENDER_CACHE_SIZE = 16
        private const val CODE_BROWSER_FULL_HIGHLIGHT_MAX_CHARS = 60000
        private const val CODE_BROWSER_FULL_HIGHLIGHT_MAX_LINES = 1200
    }

    private val uiBackground = Color(0xFFF6FBF7)
    private val uiSurface = Color(0xFFFFFFFF)
    private val uiSurfaceAlt = Color(0xFFF1F8F2)
    private val uiSurfaceSoft = Color(0xFFE5F2E8)
    private val uiPrimary = Color(0xFF1A8F55)
    private val uiPrimarySoft = Color(0xFFDDF3E4)
    private val uiText = Color(0xFF183326)
    private val uiMuted = Color(0xFF6D8876)
    private val uiBorder = Color(0xFFCFE2D3)
    private val uiOnline = Color(0xFF27A55A)
    private val uiOffline = Color(0xFFB7C7BB)
    private val uiScrim = Color(0x551A8F55)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingRequests = mutableMapOf<String, ResponseHandler>()
    private val renderedEventKeys = mutableSetOf<String>()
    private val sessions = mutableStateListOf<SessionInfo>()
    private val availableModels = mutableStateListOf<ModelInfo>()
    private val conversationItems = mutableStateListOf<ConversationItem>()
    private val assistantItemIds = mutableMapOf<String, String>()
    private val toolItemIds = mutableMapOf<String, String>()
    private val fileChangeItemIds = mutableMapOf<String, String>()
    private val fileChangeTurnIds = mutableMapOf<String, String>()
    private val turnDiffItemIds = mutableMapOf<String, String>()
    private val turnDiffs = mutableMapOf<String, String>()
    private val connectionHistory = mutableStateListOf<BridgeHistoryEntry>()
    private val pendingApprovals = mutableStateListOf<ApprovalDialogState>()
    private val codeBrowserFileCache =
        object : LinkedHashMap<String, CodeBrowserFileContent>(CODE_BROWSER_FILE_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CodeBrowserFileContent>?): Boolean {
                return size > CODE_BROWSER_FILE_CACHE_SIZE
            }
        }
    private val codeBrowserRenderCache =
        object : LinkedHashMap<String, CodeBrowserRenderedContent>(CODE_BROWSER_RENDER_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CodeBrowserRenderedContent>?): Boolean {
                return size > CODE_BROWSER_RENDER_CACHE_SIZE
            }
        }

    private lateinit var prefs: SharedPreferences
    private var bridgeClient: BridgeClient? = null
    private var connected by mutableStateOf(false)
    private var selectedTab by mutableStateOf(AppTab.Chat)
    private var selectedWorkspace by mutableStateOf<String?>(null)
    private var activeSessionId by mutableStateOf<String?>(null)
    private var activeTurnId by mutableStateOf<String?>(null)
    private var bridgeUrl by mutableStateOf("")
    private var selectedModel by mutableStateOf("")
    private var connectionDetail by mutableStateOf("未连接")
    private var currentBridgeUrl by mutableStateOf<String?>(null)
    private var connectionRenameState by mutableStateOf<ConnectionRenameDialogState?>(null)
    private var sessionInfoSheetState by mutableStateOf<SessionInfoSheetState?>(null)
    private var composerText by mutableStateOf("")
    private var newChatDraft by mutableStateOf<NewChatDraft?>(null)
    private var liveTurnStatus by mutableStateOf<String?>(null)
    private var chatRestoreScrollY by mutableStateOf<Int?>(null)
    private var codeBrowserState by mutableStateOf<CodeBrowserState?>(null)
    private var transientNotice by mutableStateOf<String?>(null)
    private var transientNonce by mutableStateOf(0)
    private var disconnectRequested = false
    private var bootSyncRequested = false
    private var lastSyncedSeq = 0
    private var syncInFlight = false
    private var sessionContentDirty = false
    private var autoReconnectEnabled = false
    private var reconnectAttempt = 0
    private var reconnectScheduled = false

    private val syncRunnable = object : Runnable {
        override fun run() {
            val sessionId = activeSessionId
            if (!connected || sessionId.isNullOrBlank()) {
                syncInFlight = false
                return
            }
            requestSessionContent(sessionId)
            mainHandler.postDelayed(this, 1500L)
        }
    }

    private val reconnectRunnable = object : Runnable {
        override fun run() {
            reconnectScheduled = false
            if (!autoReconnectEnabled || connected || bridgeClient != null) return
            val targetUrl = bridgeUrl.trim().takeIf { it.isNotBlank() } ?: currentBridgeUrl?.takeIf { it.isNotBlank() } ?: return
            connectToBridge(targetUrl, isAutoReconnect = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        bridgeUrl = loadBridgeUrl()
        replaceConnectionHistory(loadConnectionHistory(bridgeUrl))
        persistConnectionHistory()
        activeSessionId = prefs.getString(KEY_SESSION, null)
        selectedModel = prefs.getString(KEY_MODEL, "")?.trim().orEmpty()
        autoReconnectEnabled = prefs.getBoolean(KEY_AUTO_RECONNECT, false)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            RemoteApp()
        }
    }

    override fun onStart() {
        super.onStart()
        resumeBridgeConnectionIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSessionSyncLoop()
        cancelReconnectSchedule(resetAttempt = false)
        bridgeClient?.let {
            disconnectRequested = true
            it.close()
        }
        bridgeClient = null
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RemoteApp() {
        val snackbarHostState = remember { SnackbarHostState() }
        val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
        val codeBrowserSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()

        LaunchedEffect(transientNonce) {
            val message = transientNotice ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message)
            transientNotice = null
        }

        BackHandler(enabled = drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }

        val scheme = lightColorScheme(
            primary = uiPrimary,
            secondary = uiMuted,
            tertiary = uiPrimarySoft,
            background = uiBackground,
            surface = uiSurface,
            surfaceVariant = uiSurfaceAlt,
            onPrimary = Color.White,
            onSecondary = uiText,
            onTertiary = uiText,
            onBackground = uiText,
            onSurface = uiText,
            onSurfaceVariant = uiMuted,
        )

        MaterialTheme(colorScheme = scheme) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                scrimColor = uiScrim,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        drawerContainerColor = uiSurface,
                        drawerContentColor = uiText,
                    ) {
                        AppDrawerContent(
                            onCloseDrawer = {
                                scope.launch {
                                    drawerState.close()
                                }
                            },
                        )
                    }
                },
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = uiBackground,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        AppTopBar(
                            onOpenDrawer = {
                                scope.launch {
                                    drawerState.open()
                                }
                            },
                        )
                    },
                ) { scaffoldPadding ->
                    val horizontalPadding = if (selectedTab == AppTab.Chat) 8.dp else 16.dp
                    val verticalPadding = if (selectedTab == AppTab.Chat) 8.dp else 12.dp
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding)
                            .background(uiBackground),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                        ) {
                            when (selectedTab) {
                                AppTab.Connection -> ConnectionPage()
                                AppTab.Sessions -> SessionPage()
                                AppTab.Chat -> ChatPage()
                            }
                        }
                    }
                }
            }

            codeBrowserState?.let { state ->
                ModalBottomSheet(
                    onDismissRequest = { closeCodeBrowser() },
                    sheetState = codeBrowserSheetState,
                    containerColor = uiSurface,
                    contentColor = uiText,
                ) {
                    CodeBrowserPage(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 360.dp, max = 760.dp)
                            .navigationBarsPadding(),
                    )
                }
            }

            newChatDraft?.let { draft ->
                AppCenteredDialog(onDismiss = { newChatDraft = null }) {
                    NewChatSheet(
                        draft = draft,
                        onDismiss = { newChatDraft = null },
                        onModelChange = { model -> newChatDraft = draft.copy(model = model) },
                        onApprovalPolicyChange = { policy -> newChatDraft = draft.copy(approvalPolicy = policy) },
                        onSandboxChange = { sandbox -> newChatDraft = draft.copy(sandbox = sandbox) },
                        onConfirm = {
                            startNewSession(
                                projectPath = draft.projectPath,
                                model = draft.model,
                                approvalPolicy = draft.approvalPolicy,
                                sandbox = draft.sandbox,
                            )
                            newChatDraft = null
                        },
                    )
                }
            }

            sessionInfoSheetState?.let { state ->
                AppCenteredDialog(onDismiss = { sessionInfoSheetState = null }) {
                    SessionInfoSheet(
                        state = state,
                        onDismiss = { sessionInfoSheetState = null },
                    )
                }
            }

            pendingApprovals.firstOrNull()?.let { approval ->
                ApprovalDialog(
                    approval = approval,
                    onDecision = { action ->
                        sendApproval(approval.requestId, action)
                    },
                    onDismiss = {
                        showNotice("还有待处理审批，请先完成后再继续")
                    },
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppTopBar(onOpenDrawer: () -> Unit) {
        val title =
            when (selectedTab) {
                AppTab.Chat -> activeSession()?.titleLine() ?: "Codex Remote"
                AppTab.Connection -> "连接设置"
                AppTab.Sessions -> selectedWorkspace?.let { workspaceDisplayName(it, fallback = it) } ?: "会话历史"
            }

        TopAppBar(
            title = {
                Text(
                    text = title,
                    color = uiText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                TextButton(onClick = onOpenDrawer) {
                    Text("☰")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = uiSurface,
                titleContentColor = uiText,
                actionIconContentColor = uiText,
                navigationIconContentColor = uiText,
            ),
            actions = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        end = if (selectedTab == AppTab.Chat) 14.dp else 8.dp,
                        top = 6.dp,
                        bottom = 6.dp,
                    ),
                ) {
                    if (selectedTab == AppTab.Chat && activeSession() != null) {
                        IconButton(
                            onClick = { openSessionInfoSheet() },
                        ) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_dialog_info),
                                contentDescription = "会话信息",
                                tint = uiMuted,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                    StatusDot(active = connected)
                }
            },
        )
    }

    @Composable
    private fun AppDrawerContent(onCloseDrawer: () -> Unit) {
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
    private fun ProjectGroupCard(
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
    private fun DrawerWorkspaceChip(
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

    @Composable
    private fun StatusDot(active: Boolean) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = if (active) uiOnline else uiOffline, shape = CircleShape),
        )
    }

    @Composable
    private fun ConnectionStatusBadge(active: Boolean) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (active) uiPrimarySoft else uiSurfaceAlt,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (active) uiPrimary else uiBorder),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(active = active)
                Text(
                    text = if (active) "已连接" else "未连接",
                    color = uiText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    @Composable
    private fun AppCenteredDialog(
        onDismiss: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val maxDialogHeight = screenHeight * 0.84f

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = uiSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .heightIn(max = maxDialogHeight),
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxHeight)
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RenameConnectionDialog(
        state: ConnectionRenameDialogState,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
    ) {
        var draft by remember(state.url, state.currentName) { mutableStateOf(state.currentName) }

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = uiSurface,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "命名历史连接",
                        color = uiText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "给这条 bridge 连接起个更好认的名字，留空会恢复默认地址名。",
                        color = uiMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        placeholder = { Text("例如：家里 Mac mini") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = uiPrimary,
                            unfocusedBorderColor = uiBorder,
                            focusedTextColor = uiText,
                            unfocusedTextColor = uiText,
                            cursorColor = uiPrimary,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Button(
                            onClick = { onConfirm(draft.trim()) },
                            colors = ButtonDefaults.buttonColors(containerColor = uiPrimary),
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NewChatSheet(
        draft: NewChatDraft,
        onDismiss: () -> Unit,
        onModelChange: (String) -> Unit,
        onApprovalPolicyChange: (String) -> Unit,
        onSandboxChange: (String) -> Unit,
        onConfirm: () -> Unit,
    ) {
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
                    )
                },
            )
        }

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
                    text = draft.projectPath ?: "先选项目，再选模型、审批策略和沙箱。",
                    color = uiMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("模型")
                if (modelOptions.isEmpty()) {
                    BodyText("模型列表还没加载完成。")
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        modelOptions.forEach { model ->
                            SelectablePill(
                                label = model.displayName,
                                selected = draft.model == model.model || (draft.model.isBlank() && model.isDefault),
                                onClick = { onModelChange(model.model) },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("审批策略")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "untrusted" to "untrusted",
                        "on-request" to "on-request",
                        "on-failure" to "on-failure",
                        "never" to "never",
                    ).forEach { (policy, label) ->
                        SelectablePill(
                            label = label,
                            selected = draft.approvalPolicy == policy,
                            onClick = { onApprovalPolicyChange(policy) },
                        )
                    }
                }
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
                Label("沙箱")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "read-only" to "read-only",
                        "danger-full-access" to "full-access",
                        "workspace-write" to "workspace-write",
                    ).forEach { (sandbox, label) ->
                        SelectablePill(
                            label = label,
                            selected = draft.sandbox == sandbox,
                            onClick = { onSandboxChange(sandbox) },
                        )
                    }
                }
                Text(
                    text =
                        when (draft.sandbox) {
                            "read-only" -> "只读模式。不能改文件，适合纯查看、检索和解释。"
                            "workspace-write" -> "仅允许写当前项目工作区；更稳妥，适合日常改代码。"
                            else -> "允许完整文件系统访问；能力最强，但风险也最高。"
                        },
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
    private fun SelectablePill(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (selected) uiPrimary else uiSurfaceAlt,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) uiPrimary else uiBorder),
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (selected) Color.White else uiText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun SessionInfoSheet(
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ConnectionPage() {
        val keyboard = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 6.dp)
                .imePadding()
                .navigationBarsPadding()
                .then(Modifier),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .verticalScroll(scrollState),
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
                            SectionTitle("连接 Linux Bridge")
                            ConnectionStatusBadge(active = connected)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        BodyText("直接粘贴 `npm run bridge` 打印出来的完整 ws:// 地址，最好带上端口和 token。")
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = uiSurface),
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Label("Bridge URL")
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = bridgeUrl,
                            onValueChange = { bridgeUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            placeholder = { Text("ws://192.168.31.206:8787/?token=...") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                    toggleConnection()
                                },
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = uiPrimary,
                                unfocusedBorderColor = uiBorder,
                                focusedTextColor = uiText,
                                unfocusedTextColor = uiText,
                                cursorColor = uiPrimary,
                            ),
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    pasteBridgeUrl()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("粘贴")
                            }
                            Button(
                                onClick = {
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                    toggleConnection()
                                },
                                modifier = Modifier.weight(1.4f),
                                colors = ButtonDefaults.buttonColors(containerColor = uiPrimary),
                            ) {
                                Text(if (connected) "断开连接" else "连接")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = uiSurfaceAlt,
                            border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                StatusDot(active = connected)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = connectionStatusHeadline(),
                                        color = uiText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = connectionStatusDetailText(),
                                        color = uiMuted,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                if (connectionHistory.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = uiSurface),
                        shape = RoundedCornerShape(22.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            SectionTitle("历史连接")
                            Spacer(modifier = Modifier.height(8.dp))
                            BodyText("单击回填地址，长按可重命名或移除，也可以直接连接或切换。")
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                connectionHistory.forEach { entry ->
                                    HistoryConnectionCard(
                                        entry = entry,
                                        active = currentBridgeUrl == entry.url,
                                        onApply = { applyHistoryConnection(entry) },
                                        onConnect = { connectToHistory(entry) },
                                        onRename = { openRenameConnectionDialog(entry) },
                                        onDelete = { removeConnectionHistory(entry.url) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        connectionRenameState?.let { state ->
            RenameConnectionDialog(
                state = state,
                onDismiss = { connectionRenameState = null },
                onConfirm = { name ->
                    renameConnectionHistory(state.url, name)
                    val updatedName = connectionHistory.firstOrNull { it.url == state.url }?.displayName()
                        ?: BridgeHistoryEntry.fromUrl(state.url, name = name)?.displayName()
                        ?: state.url
                    if (currentBridgeUrl == state.url && connected) {
                        connectionDetail = "已连接到 $updatedName"
                    } else if (bridgeUrl.trim() == state.url.trim()) {
                        connectionDetail = "已回填 $updatedName，点击连接即可"
                    }
                    connectionRenameState = null
                },
            )
        }
    }

    @Composable
    @OptIn(ExperimentalFoundationApi::class)
    private fun HistoryConnectionCard(
        entry: BridgeHistoryEntry,
        active: Boolean,
        onApply: () -> Unit,
        onConnect: () -> Unit,
        onRename: () -> Unit,
        onDelete: () -> Unit,
    ) {
        val isConnectedState = active && connected
        val isConnectingState = active && !connected
        val connectLabel = when {
            isConnectedState -> "当前"
            connected -> "切换"
            else -> "连接"
        }
        var actionsExpanded by remember(entry.url) { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = if (active) uiPrimarySoft else uiSurfaceAlt,
            border = if (active) androidx.compose.foundation.BorderStroke(1.dp, uiPrimary) else androidx.compose.foundation.BorderStroke(1.dp, uiBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onApply,
                        onLongClick = { actionsExpanded = true },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = entry.displayName(),
                        color = uiText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.subtitleLine(),
                        color = uiMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.lastUsedLabel(),
                        color = uiMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .widthIn(min = 88.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    if (isConnectedState) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "✓",
                                color = uiOnline,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "已连接",
                                color = uiText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        FilledTonalButton(onClick = onConnect, enabled = !isConnectingState) {
                            if (isConnectingState) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        color = uiPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(if (reconnectScheduled || reconnectAttempt > 0) "重连中" else "连接中")
                                }
                            } else {
                                Text(connectLabel)
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            actionsExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("移除") },
                        onClick = {
                            actionsExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun ChatPage() {
        val activeSession = activeSession()
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val shouldFollowBottom = activeTurnId != null || imeBottom > 0

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (conversationItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BodyText(
                            text = if (activeSession == null) "先新建或选择一个项目里的会话，再开始聊天。" else "这里会显示当前会话的消息流。",
                            modifier = Modifier.fillMaxWidth(0.8f),
                        )
                    }
                } else {
                    ConversationHistoryWebView(
                        items = conversationItems,
                        sessionId = activeSessionId,
                        displayBasePath = activeSession?.cwd,
                        followBottom = shouldFollowBottom,
                        restoreScrollY = chatRestoreScrollY,
                        onScrollRestored = {
                            chatRestoreScrollY = null
                        },
                        onOpenCodeBrowser = { itemId, path, scrollY ->
                            openCodeBrowser(itemId, path, scrollY)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                    )
                }
            }

            liveTurnStatus?.takeIf { it.isNotBlank() }?.let { status ->
                Text(
                    text = status,
                    color = uiMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = composerText,
                    onValueChange = { composerText = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    singleLine = false,
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = { Text("输入消息") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendComposerText() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = uiPrimary,
                        unfocusedBorderColor = uiBorder,
                        focusedTextColor = uiText,
                        unfocusedTextColor = uiText,
                        cursorColor = uiPrimary,
                    ),
                )

                Button(
                    onClick = { sendComposerText() },
                    enabled = connected && activeSessionId != null,
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = uiPrimary),
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_send),
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun ApprovalDialog(
        approval: ApprovalDialogState,
        onDecision: (ApprovalAction) -> Unit,
        onDismiss: () -> Unit,
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = uiSurface,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = approval.title,
                        color = uiText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    approval.detail.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(
                            text = detail,
                            color = uiMuted,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    if (approval.diffEntries.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "涉及文件: ${approval.diffEntries.size} 个",
                                color = uiMuted,
                                fontSize = 13.sp,
                            )
                            Column {
                                approval.diffEntries.forEachIndexed { index, entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = entry.displayPath(basePath = currentWorkspacePath(), maxLength = 72),
                                            color = uiText,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = entry.diffStatsLabel().orEmpty(),
                                            color = uiMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    if (index < approval.diffEntries.lastIndex) {
                                        HorizontalDivider(color = uiBorder.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = uiBorder.copy(alpha = 0.8f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        approval.actions.forEach { action ->
                            val accepted = action.isPrimary
                            val buttonColors =
                                if (accepted) {
                                    ButtonDefaults.buttonColors(containerColor = uiPrimary, contentColor = Color.White)
                                } else {
                                    ButtonDefaults.outlinedButtonColors(contentColor = uiText)
                                }
                            if (accepted) {
                                Button(onClick = { onDecision(action) }, colors = buttonColors) {
                                    Text(action.label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onDecision(action) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = uiText),
                                ) {
                                    Text(action.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CodeBrowserPage(
        state: CodeBrowserState,
        modifier: Modifier = Modifier,
    ) {
        val selectedEntry = state.selectedEntry()
        val diffStats = buildDiffStatsLine(diffEntries = state.diffEntries, fallbackDiff = state.fallbackDiff)
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        state.diffEntries.forEachIndexed { index, entry ->
                            CodeBrowserFileRow(
                                label = entry.filenameLabel(),
                                stats = entry.diffStatsLabel(),
                                selected = state.selectedPath == entry.browsePath(),
                                onClick = {
                                    selectCodeBrowserPath(entry.browsePath())
                                    setCodeBrowserMode(CodeBrowserMode.File)
                                },
                            )
                            if (index < state.diffEntries.lastIndex) {
                                HorizontalDivider(color = uiBorder.copy(alpha = 0.75f))
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
    private fun CodeBrowserModeButton(
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
    private fun CodeBrowserFileRow(
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
    private fun DiffStatsText(
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

    private fun readDiffStatsSegmentEnd(
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
    private fun CodeBrowserTextPane(
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

    @Composable
    private fun SessionPage() {
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
    private fun SessionCard(
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
    private fun ModelCard(
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

    private fun toggleConnection() {
        if (bridgeClient?.isOpen() == true) {
            disconnectBridge(detail = "连接已关闭", switchToConnectionPage = true)
            return
        }

        val url = try {
            buildBridgeUrl()
        } catch (error: Exception) {
            connectionDetail = "桥接地址无效: ${error.message}"
            showNotice(connectionDetail)
            return
        }

        connectToBridge(url)
    }

    private fun disconnectBridge(
        detail: String,
        switchToConnectionPage: Boolean,
    ) {
        stopSessionSyncLoop()
        cancelReconnectSchedule(resetAttempt = true)
        setAutoReconnectEnabled(false)
        disconnectRequested = true
        bridgeClient?.close()
        bridgeClient = null
        connected = false
        activeTurnId = null
        sessionContentDirty = false
        pendingApprovals.clear()
        pendingRequests.clear()
        currentBridgeUrl = null
        selectedWorkspace = null
        if (switchToConnectionPage) {
            selectedTab = AppTab.Connection
        }
        connectionDetail = detail
    }

    private fun connectToBridge(url: String, isAutoReconnect: Boolean = false) {
        if (bridgeClient?.isOpen() == true && currentBridgeUrl == url) {
            selectedTab = AppTab.Chat
            connectionDetail = "已连接到 ${connectionHistory.firstOrNull { it.url == url }?.displayName() ?: BridgeHistoryEntry.fromUrl(url)?.displayName() ?: url}"
            return
        }

        if (bridgeClient != null && currentBridgeUrl == url) {
            connectionDetail =
                if (isAutoReconnect || reconnectAttempt > 0) {
                    "自动重连中: $url"
                } else {
                    "连接中: $url"
                }
            return
        }

        if (bridgeClient != null) {
            disconnectBridge(detail = "正在切换连接…", switchToConnectionPage = false)
        }

        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
        saveBridgeSettings(url)
        setAutoReconnectEnabled(true)
        bridgeUrl = url
        disconnectRequested = false
        bootSyncRequested = false
        connected = false
        currentBridgeUrl = url
        connectionDetail =
            if (isAutoReconnect || reconnectAttempt > 0) {
                "自动重连中${reconnectAttempt.takeIf { it > 0 }?.let { "($it)" }.orEmpty()}: $url"
            } else {
                "连接中: $url"
            }

        bridgeClient = BridgeClient(URI.create(url), object : BridgeClient.Listener {
            override fun onOpen() {
                mainHandler.post {
                    connected = true
                    reconnectAttempt = 0
                    cancelReconnectSchedule(resetAttempt = false)
                    setAutoReconnectEnabled(true)
                    rememberConnectionHistory(url)
                    selectedTab = AppTab.Chat
                    connectionDetail = "已连接到 ${connectionHistory.firstOrNull { it.url == url }?.displayName() ?: BridgeHistoryEntry.fromUrl(url)?.displayName() ?: url}"
                }
            }

            override fun onText(text: String) {
                mainHandler.post { handleIncoming(text) }
            }

            override fun onDisconnected(reason: String, error: Throwable?) {
                mainHandler.post {
                    connected = false
                    activeTurnId = null
                    sessionContentDirty = false
                    pendingApprovals.clear()
                    pendingRequests.clear()
                    stopSessionSyncLoop()
                    bridgeClient = null
                    val detailSuffix = describeThrowable(error)
                    val targetUrl = currentBridgeUrl?.takeIf { it.isNotBlank() } ?: bridgeUrl.trim().takeIf { it.isNotBlank() }
                    if (autoReconnectEnabled && !targetUrl.isNullOrBlank()) {
                        scheduleReconnect(
                            url = targetUrl,
                            reason = reason,
                            error = error,
                        )
                    } else {
                        currentBridgeUrl = null
                        selectedWorkspace = null
                        selectedTab = AppTab.Connection
                        connectionDetail = reason + detailSuffix
                    }
                    disconnectRequested = false
                }
            }
        })

        try {
            bridgeClient?.connect()
        } catch (error: RuntimeException) {
            bridgeClient = null
            if (autoReconnectEnabled) {
                scheduleReconnect(url = url, reason = "连接失败", error = error)
            } else {
                currentBridgeUrl = null
                connectionDetail = "连接失败: ${error.message}"
                showNotice(connectionDetail)
            }
        }
    }

    private fun applyHistoryConnection(entry: BridgeHistoryEntry) {
        bridgeUrl = entry.url
        connectionDetail = "已回填 ${entry.displayName()}，点击连接即可"
    }

    private fun connectToHistory(entry: BridgeHistoryEntry) {
        bridgeUrl = entry.url
        connectToBridge(entry.url)
    }

    private fun openRenameConnectionDialog(entry: BridgeHistoryEntry) {
        connectionRenameState =
            ConnectionRenameDialogState(
                url = entry.url,
                currentName = entry.name.orEmpty(),
            )
    }

    private fun handleIncoming(text: String) {
        val message = try {
            JSONObject(text)
        } catch (error: JSONException) {
            appendSystemNote("收到非 JSON 消息: $text")
            return
        }

        when (message.optString("type", "")) {
            "hello" -> {
                connectionDetail = "Bridge 已就绪，正在拉取会话列表"
                selectedTab = AppTab.Chat
                requestSessionList()
                requestModelList()
                startSessionSyncLoop()
            }

            "event" -> handleEvent(message)
            "response", "pong" -> handleResponse(message)
            "error" -> appendSystemNote("错误: ${safeJson(message.optJSONObject("error"))}")
        }
    }

    private fun handleResponse(message: JSONObject) {
        val id = message.optString("id", "")
        val callback = pendingRequests.remove(id)
        val ok = message.optBoolean("ok", true)
        if (callback != null) {
            if (!ok) {
                val errorText = extractErrorText(message.optJSONObject("error")).ifBlank { formatJson(message.optJSONObject("error")) }
                callback.onError(errorText)
                appendSystemNote("请求失败: $errorText")
                showNotice("请求失败: $errorText")
                return
            }
            try {
                callback.onResponse(message)
            } catch (error: JSONException) {
                appendSystemNote("解析响应失败: ${error.message}")
            }
            return
        }
        if (!ok) {
            val errorText = extractErrorText(message.optJSONObject("error")).ifBlank { formatJson(message.optJSONObject("error")) }
            appendSystemNote("请求失败: $errorText")
            showNotice("请求失败: $errorText")
        }
    }

    private fun handleEvent(message: JSONObject) {
        if (shouldIgnoreEvent(message)) return

        val eventName = message.optString("event", "")
        val payload = message.optJSONObject("payload") ?: JSONObject()

        if (eventName == "thread/started") {
            val info = SessionInfo.fromThread(payload)
            if (info != null) {
                upsertSession(info)
                if (activeSessionId == null) {
                    selectSession(info.sessionId, syncHistory = true)
                }
            }
            return
        }

        if (payloadCarriesSessionMetadata(payload)) {
            SessionInfo.fromThread(payload)?.let(::upsertSession)
        }

        if (eventName == "session/changed") {
            val sessionId = message.optString("session_id", "")
            if (sessionId.isNotBlank() && sessionId == activeSessionId) {
                if (syncInFlight) {
                    sessionContentDirty = true
                } else {
                    requestSessionContent(sessionId)
                }
            }
            return
        }

        val sessionId = message.optString("session_id", "")
        if (sessionId.isNotEmpty() && activeSessionId != null && sessionId != activeSessionId) {
            return
        }

        when (eventName) {
            "turn/input" -> {
                Unit
            }

            "turn/started" -> {
                activeTurnId = firstNonEmpty(
                    message.optString("turn_id", ""),
                    payload.optJSONObject("turn")?.optString("id", "") ?: "",
                )
                updateLiveTurnStatus("Codex 正在响应…")
            }

            "item/agentMessage/delta" -> {
                updateLiveTurnStatus("Codex 正在输出…")
            }
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/permissions/requestApproval",
            "applyPatchApproval",
            "execCommandApproval" -> {
                updateLiveTurnStatus("等待审批…")
                handleApprovalRequest(eventName, message, payload)
            }
            "item/fileChange/patchUpdated" -> {
                updateLiveTurnStatus("Codex 正在修改文件…")
            }
            "turn/diff/updated" -> {
                updateLiveTurnStatus("Codex 正在修改文件…")
            }
            "item/started", "item/completed" -> {
                updateLiveTurnStatusFromItem(eventName, payload.optJSONObject("item"))
            }
            "warning" -> updateLiveTurnStatusFromWarning(payload)
            "error" -> updateLiveTurnStatusFromError(payload)
            "thread/status/changed" -> updateLiveTurnStatusFromThreadStatus(payload.optJSONObject("status"))
            "turn/completed" -> {
                removeCompletedTurnApprovalItems(message.optString("turn_id", ""))
                if (activeTurnId != null && activeTurnId == message.optString("turn_id", "")) {
                    activeTurnId = null
                }
                updateLiveTurnStatus(null)
            }

            else -> Unit
        }
    }

    private fun shouldIgnoreEvent(message: JSONObject): Boolean {
        val seq = message.optInt("seq", -1)
        val sessionId = message.optString("session_id", "")
        if (seq < 0 || sessionId.isEmpty()) {
            return false
        }
        val key = "$sessionId:$seq"
        if (renderedEventKeys.contains(key)) return true
        renderedEventKeys.add(key)
        return false
    }

    private fun payloadCarriesSessionMetadata(payload: JSONObject): Boolean {
        return payload.has("thread")
            || payload.has("cwd")
            || payload.has("model")
            || payload.has("modelProvider")
            || payload.has("approvalPolicy")
            || payload.has("sandbox")
            || payload.has("sandboxPolicy")
            || payload.has("permissions")
            || payload.has("activePermissionProfile")
            || payload.has("permissionProfile")
            || payload.has("tokenUsage")
            || payload.has("contextWindow")
            || payload.has("lastTokenUsage")
            || payload.has("totalTokenUsage")
    }

    private fun handleAssistantDelta(message: JSONObject, payload: JSONObject) {
        val turnKey = extractTurnKey(message, payload)
        val itemKey = extractAssistantItemKey(message = message, payload = payload)
        val delta = firstNonEmpty(
            payload.optString("delta", ""),
            payload.optString("text", ""),
            payload.optString("message", ""),
        ).ifBlank { safeJson(payload) }
        appendAssistantDeltaBubble(turnKey, itemKey.ifBlank { "stream" }, delta)
    }

    private fun handleApprovalRequest(eventName: String, message: JSONObject, payload: JSONObject) {
        val requestId = firstNonEmpty(message.optString("request_id", ""), message.optString("id", ""))
        if (requestId.isBlank()) return

        val presentation = buildApprovalPresentation(eventName, payload)
        upsertPendingApproval(
            ApprovalDialogState(
                requestId = requestId,
                title = presentation.title,
                detail = presentation.detail,
                actions = buildApprovalActions(eventName, payload),
                diffEntries = presentation.diffEntries,
                turnId = extractExplicitTurnKey(message, payload).takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun sendApproval(requestId: String, action: ApprovalAction) {
        val payload = JSONObject().apply {
            put("session_id", activeSessionId ?: "")
            put("request_id", requestId)
            action.responsePayload.keys().forEach { key ->
                put(key, cloneJsonValue(action.responsePayload.opt(key)))
            }
        }

        if (!sendRequest("approval.response", payload, object : ResponseHandler {
            override fun onResponse(response: JSONObject) {
                appendSystemNote("审批结果已提交")
                removePendingApproval(requestId)
            }
        })) {
            appendSystemNote("审批发送失败")
        } else {
            appendSystemNote("已发送审批: ${action.label}")
        }
    }

    private fun requestSessionList() {
        if (!ensureConnected()) return
        sendRequest("session.list", JSONObject()) { response ->
            val payload = response.optJSONObject("payload")
            val array = payload?.optJSONArray("sessions")
            val newSessions = mutableListOf<SessionInfo>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val info = array.optJSONObject(i)?.let { SessionInfo.fromSession(it) }
                    if (info != null) newSessions.add(info)
                }
            }
            replaceSessions(newSessions)
            if (activeSessionId != null && sessions.none { it.sessionId == activeSessionId }) {
                activeSessionId = null
                prefs.edit().remove(KEY_SESSION).apply()
            }
            if (!bootSyncRequested) {
                val targetSession = activeSessionId ?: sessions.firstOrNull()?.sessionId
                if (!targetSession.isNullOrBlank()) {
                    bootSyncRequested = true
                    selectSession(targetSession, syncHistory = true)
                }
            }
        }
    }

    private fun requestModelList() {
        if (!ensureConnected()) return
        val payload = JSONObject().apply {
            put("includeHidden", false)
        }

        sendRequest("model.list", payload) { response ->
            val array = response.optJSONObject("payload")?.optJSONArray("data")
            val newModels = mutableListOf<ModelInfo>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { json ->
                        val info = ModelInfo.fromJson(json)
                        if (info != null) newModels.add(info)
                    }
                }
            }
            replaceModels(newModels)
            val defaultModel = newModels.firstOrNull { it.isDefault } ?: newModels.firstOrNull()
            if (!isKnownModel(selectedModel)) {
                defaultModel?.let { selectModel(it.model) }
            }
        }
    }

    private fun startNewSession(
        projectPath: String?,
        model: String,
        approvalPolicy: String,
        sandbox: String,
    ) {
        if (!ensureConnected()) return
        if (model.isBlank()) {
            showNotice("请先选择一个模型")
            return
        }

        val payload = JSONObject().apply {
            put("title", workspaceDisplayName(projectPath, fallback = "新对话"))
            if (!projectPath.isNullOrBlank()) {
                put("cwd", projectPath)
            }
            put("sessionStartSource", "startup")
            put("threadSource", "user")
            put("model", model)
            put("approvalPolicy", approvalPolicy)
            put("sandbox", sandbox)
        }

        sendRequest("session.start", payload) { response ->
            val info = response.optJSONObject("payload")?.optJSONObject("session")?.let { SessionInfo.fromSession(it) }
            if (info != null) {
                upsertSession(info)
                selectSession(info.sessionId, syncHistory = false)
                selectedTab = AppTab.Chat
                selectedWorkspace = info.cwd.takeIf { it.isNotBlank() } ?: projectPath
                if (info.model.isNotBlank()) {
                    selectModel(info.model)
                }
                appendSystemNote("新会话已创建: ${shortId(info.sessionId)}")
                showNotice("新会话已创建")
            }
        }
    }

    private fun sendComposerText() {
        val text = composerText.trim()
        if (text.isEmpty()) return
        if (!ensureConnected()) return
        if (activeSessionId.isNullOrBlank()) {
            appendSystemNote("先创建或选择一个会话")
            return
        }

        val model = resolveModelForSend()
        val payload = JSONObject().apply {
            put("session_id", activeSessionId)
            put("text", text)
            if (model.isNotBlank()) {
                put("model", model)
            }
            activeSession()?.approvalPolicy?.takeIf { it.isNotBlank() }?.let { put("approvalPolicy", it) }
            val sandboxValue = activeSession()?.sandbox
            if (sandboxValue != null) {
                put("sandboxPolicy", cloneJsonValue(sandboxValue))
            }
            activeSession()?.permissions?.let { put("permissions", cloneJsonValue(it)) }
        }

        if (!sendRequest("turn.send", payload) { response ->
            val turn = response.optJSONObject("payload")?.optJSONObject("turn")
            if (turn != null) {
                activeTurnId = turn.optString("id", activeTurnId.orEmpty())
            }
        }) {
            return
        }

        composerText = ""
    }

    private fun interruptCurrentTurn() {
        if (!ensureConnected()) return
        if (activeSessionId.isNullOrBlank() || activeTurnId.isNullOrBlank()) {
            appendSystemNote("当前没有可中断的回合")
            return
        }

        val payload = JSONObject().apply {
            put("session_id", activeSessionId)
            put("turn_id", activeTurnId)
        }

        sendRequest("turn.interrupt", payload) {
            appendSystemNote("已请求中断")
            activeTurnId = null
        }
    }

    private fun requestSessionContent(sessionId: String) {
        if (sessionId.isBlank()) return
        if (!ensureConnected()) return
        if (syncInFlight) {
            sessionContentDirty = true
            return
        }
        syncInFlight = true
        val requestedSessionId = sessionId
        val payload = JSONObject().apply {
            put("session_id", sessionId)
        }

        if (!sendRequest("session.content", payload, object : ResponseHandler {
            override fun onResponse(response: JSONObject) {
                syncInFlight = false
                val responsePayload = response.optJSONObject("payload")
                responsePayload?.optJSONObject("session")
                    ?.let(SessionInfo::fromSession)
                    ?.let(::upsertSession)
                applySessionContentSnapshot(responsePayload)
                flushPendingSessionRefresh(requestedSessionId)
            }

            override fun onError(errorText: String) {
                syncInFlight = false
                flushPendingSessionRefresh(requestedSessionId)
            }
        })) {
            syncInFlight = false
        }
    }

    private fun flushPendingSessionRefresh(sessionId: String) {
        if (!sessionContentDirty) return
        if (!connected || activeSessionId != sessionId) return
        sessionContentDirty = false
        mainHandler.post { requestSessionContent(sessionId) }
    }

    private fun applySessionContentSnapshot(payload: JSONObject?) {
        val snapshotItems = buildConversationItemsFromSnapshot(payload)
        val snapshotApprovals = buildPendingApprovalsFromSnapshot(payload)
        if (snapshotItems.isEmpty() && snapshotApprovals.isEmpty() && conversationItems.isNotEmpty()) {
            return
        }
        assistantItemIds.clear()
        toolItemIds.clear()
        fileChangeItemIds.clear()
        fileChangeTurnIds.clear()
        turnDiffItemIds.clear()
        turnDiffs.clear()

        conversationItems.clear()
        conversationItems.addAll(snapshotItems)
        replacePendingApprovals(snapshotApprovals)

        snapshotItems.forEach { item ->
            when (item) {
                is ConversationItem.FileChange -> {
                    item.sourceItemId?.takeIf { it.isNotBlank() }?.let { sourceId ->
                        fileChangeItemIds[sourceId] = item.id
                    }
                    item.turnId?.takeIf { it.isNotBlank() }?.let { turnId ->
                        item.sourceItemId?.takeIf { it.isNotBlank() }?.let { sourceId -> fileChangeTurnIds[sourceId] = turnId }
                    }
                }
                else -> Unit
            }
        }

        codeBrowserState?.let { state ->
            if (!state.conversationItemId.startsWith("approval_") && snapshotItems.none { it.id == state.conversationItemId }) {
                codeBrowserState = null
            }
        }
    }

    private fun replacePendingApprovals(approvals: List<ApprovalDialogState>) {
        pendingApprovals.clear()
        pendingApprovals.addAll(approvals)
    }

    private fun upsertPendingApproval(approval: ApprovalDialogState) {
        val index = pendingApprovals.indexOfFirst { it.requestId == approval.requestId }
        if (index >= 0) {
            pendingApprovals[index] = approval
        } else {
            pendingApprovals.add(approval)
        }
    }

    private fun removePendingApproval(requestId: String) {
        pendingApprovals.removeAll { it.requestId == requestId }
    }

    private fun selectSession(sessionId: String, syncHistory: Boolean) {
        if (sessionId.isBlank()) return
        activeSessionId = sessionId
        prefs.edit().putString(KEY_SESSION, sessionId).apply()
        sessions.firstOrNull { it.sessionId == sessionId }?.model
            ?.takeIf { it.isNotBlank() && isKnownModel(it) }
            ?.let { selectModel(it) }
        clearConversation()
        if (syncHistory) {
            requestSessionContent(sessionId)
        }
        startSessionSyncLoop()
    }

    private fun replaceSessions(newSessions: List<SessionInfo>) {
        val existingSessions = sessions.associateBy { it.sessionId }
        sessions.clear()
        sessions.addAll(
            newSessions
                .map { it.mergedWith(existingSessions[it.sessionId]) }
                .sortedByDescending { it.updatedAt },
        )
        if (selectedWorkspace != null && sessions.none { it.cwd == selectedWorkspace }) {
            selectedWorkspace = null
        }
    }

    private fun upsertSession(info: SessionInfo) {
        val merged = info.mergedWith(sessions.firstOrNull { it.sessionId == info.sessionId })
        sessions.removeAll { it.sessionId == info.sessionId }
        sessions.add(0, merged)
    }

    private fun clearConversation() {
        conversationItems.clear()
        assistantItemIds.clear()
        toolItemIds.clear()
        fileChangeItemIds.clear()
        fileChangeTurnIds.clear()
        turnDiffItemIds.clear()
        turnDiffs.clear()
        renderedEventKeys.clear()
        activeTurnId = null
        chatRestoreScrollY = null
        codeBrowserState = null
        lastSyncedSeq = 0
        syncInFlight = false
        sessionContentDirty = false
        pendingApprovals.clear()
    }

    private fun appendSystemNote(text: String) {
        conversationItems.add(
            ConversationItem.SystemNote(
                id = "sys_${UUID.randomUUID()}",
                text = text,
            ),
        )
    }

    private fun appendBubble(
        text: String,
        right: Boolean,
        backgroundColor: Int,
        textColor: Int,
        turnKey: String? = null,
    ) {
        val normalizedTurnKey = turnKey?.trim()?.takeIf { it.isNotBlank() }
        if (right) {
            val latestUserBubble = conversationItems.asReversed().firstOrNull { item ->
                (item as? ConversationItem.Bubble)?.right == true
            } as? ConversationItem.Bubble
            if (latestUserBubble != null && normalizeAssistantText(latestUserBubble.text) == normalizeAssistantText(text)) {
                val existingTurnKey = latestUserBubble.turnKey.orEmpty()
                val incomingTurnKey = normalizedTurnKey.orEmpty()
                if (existingTurnKey == incomingTurnKey || existingTurnKey.isBlank() || incomingTurnKey.isBlank()) {
                    replaceConversationItem(latestUserBubble.id) { item ->
                        if (item is ConversationItem.Bubble) {
                            item.copy(turnKey = normalizedTurnKey ?: item.turnKey)
                        } else {
                            item
                        }
                    }
                    return
                }
            }
        }
        conversationItems.add(
            ConversationItem.Bubble(
                id = "msg_${UUID.randomUUID()}",
                right = right,
                text = text,
                backgroundColor = backgroundColor,
                textColor = textColor,
                turnKey = normalizedTurnKey,
            ),
        )
    }

    private fun replaceConversationItem(itemId: String, transform: (ConversationItem) -> ConversationItem) {
        val index = conversationItems.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            conversationItems[index] = transform(conversationItems[index])
        }
    }

    private fun removeConversationItem(itemId: String) {
        val index = conversationItems.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            conversationItems.removeAt(index)
        }
        if (codeBrowserState?.conversationItemId == itemId) {
            codeBrowserState = null
        }
    }

    private fun removeCompletedTurnApprovalItems(turnId: String) {
        val normalizedTurnId = turnId.trim()
        if (pendingApprovals.isEmpty()) return

        if (normalizedTurnId.isBlank()) {
            pendingApprovals.clear()
            return
        }

        pendingApprovals.removeAll { approval -> approval.turnId == normalizedTurnId }
    }

    private fun openCodeBrowser(itemId: String, selectedPath: String?, scrollY: Int) {
        val sessionId = activeSessionId
        val item = conversationItems.firstOrNull { it.id == itemId }
        val state =
            when (item) {
                is ConversationItem.FileChange ->
                    CodeBrowserState(
                        conversationItemId = item.id,
                        sessionId = sessionId,
                        title = item.title,
                        basePath = activeSession()?.cwd,
                        diffEntries = item.diffEntries,
                        fallbackDiff = item.fallbackDiff,
                        selectedPath = selectedPath ?: item.diffEntries.firstOrNull()?.browsePath(),
                        mode = if (selectedPath.isNullOrBlank()) CodeBrowserMode.Diff else CodeBrowserMode.File,
                    )

                else -> null
            }

        if (state == null || (state.diffEntries.isEmpty() && state.fallbackDiff.isNullOrBlank())) {
            showNotice("这个条目暂时没有可浏览的代码内容")
            return
        }
        chatRestoreScrollY = scrollY.coerceAtLeast(0)
        codeBrowserState = state
    }

    private fun closeCodeBrowser() {
        codeBrowserState = null
    }

    private fun selectCodeBrowserPath(path: String) {
        val state = codeBrowserState ?: return
        if (state.selectedPath == path) return
        codeBrowserState = state.copy(
            selectedPath = path,
            mode = CodeBrowserMode.File,
            fileReadState = CodeBrowserFileReadState.Idle,
        )
    }

    private fun setCodeBrowserMode(mode: CodeBrowserMode) {
        val state = codeBrowserState ?: return
        if (state.mode == mode) return
        codeBrowserState = state.copy(
            mode = mode,
            fileReadState = when {
                mode == CodeBrowserMode.File -> CodeBrowserFileReadState.Idle
                else -> state.fileReadState
            },
        )
    }

    private fun loadCodeBrowserFileContent(state: CodeBrowserState) {
        val entry = state.selectedEntry()
        val sessionId = state.sessionId ?: activeSessionId
        if (entry == null || sessionId.isNullOrBlank()) {
            codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Error("没有可读取的文件内容"))
            return
        }
        if (entry.kind.trim() == "delete" && entry.movePath.isNullOrBlank()) {
            codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Error("该文件已经被删除，当前内容无法读取"))
            return
        }

        val candidatePaths = entry.browseCandidates()
        if (candidatePaths.isEmpty()) {
            codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Error("没有可读取的文件路径"))
            return
        }
        findCachedCodeBrowserFileContent(sessionId, candidatePaths)?.let { cached ->
            codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Loaded(cached))
            return
        }
        val browsePath = candidatePaths.first()
        codeBrowserState = state.copy(fileReadState = CodeBrowserFileReadState.Loading)

        fun requestAt(index: Int) {
            val requestedPath = candidatePaths.getOrNull(index)
            if (requestedPath.isNullOrBlank()) {
                val current = codeBrowserState ?: return
                if (current.conversationItemId == state.conversationItemId && current.selectedPath == browsePath) {
                    codeBrowserState = current.copy(fileReadState = CodeBrowserFileReadState.Error("文件读取失败，候选路径都不可用"))
                }
                return
            }

            val payload = JSONObject().apply {
                put("session_id", sessionId)
                put("path", requestedPath)
                put("max_bytes", 200000)
            }

            val sent = sendRequest("file.read", payload, object : ResponseHandler {
                override fun onResponse(response: JSONObject) {
                    val current = codeBrowserState ?: return
                    if (current.conversationItemId != state.conversationItemId || current.selectedPath != browsePath) return

                    val result = response.optJSONObject("payload") ?: JSONObject()
                    val content =
                        CodeBrowserFileContent(
                            requestedPath = requestedPath,
                            resolvedPath = result.optString("resolved_path", requestedPath),
                            content = result.optString("content", ""),
                            truncated = result.optBoolean("truncated", false),
                            bytes = result.optInt("bytes", 0),
                        )
                    rememberCodeBrowserFileContent(sessionId, content)
                    codeBrowserState = current.copy(
                        fileReadState = CodeBrowserFileReadState.Loaded(
                            content,
                        ),
                    )
                }

                override fun onError(errorText: String) {
                    if (index + 1 < candidatePaths.size) {
                        requestAt(index + 1)
                        return
                    }
                    val current = codeBrowserState ?: return
                    if (current.conversationItemId != state.conversationItemId || current.selectedPath != browsePath) return
                    codeBrowserState = current.copy(
                        fileReadState = CodeBrowserFileReadState.Error(
                            buildString {
                                append(if (errorText.isBlank()) "文件读取失败" else errorText)
                                append("\n候选路径: ")
                                append(candidatePaths.joinToString(" | ") { compactDiffDisplayPath(it, state.basePath, maxLength = 64) })
                            },
                        ),
                    )
                }
            })

            if (!sent) {
                if (index + 1 < candidatePaths.size) {
                    requestAt(index + 1)
                    return
                }
                val current = codeBrowserState ?: return
                if (current.conversationItemId == state.conversationItemId && current.selectedPath == browsePath) {
                    codeBrowserState = current.copy(fileReadState = CodeBrowserFileReadState.Error("文件读取请求发送失败"))
                }
            }
        }

        requestAt(0)
    }

    private fun buildBridgeUrl(): String {
        val raw = bridgeUrl.trim()
        if (raw.isEmpty()) {
            throw IllegalArgumentException("Bridge URL 不能为空")
        }

        val normalized = normalizeBridgeUrl(raw, requireToken = true)
        val uri = URI.create(normalized)
        val host = uri.host?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("URL host 不能为空")
        val port = if (uri.port >= 0) uri.port else 8787
        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val query = uri.rawQuery
        val tokenFromUrl = query?.let { extractTokenFromQuery(it) }
        val tokenFromPrefs = prefs.getString(KEY_TOKEN, "")?.trim().orEmpty()
        val token = tokenFromUrl ?: tokenFromPrefs
        if (token.isBlank()) {
            throw IllegalArgumentException("URL 里需要 token，例如 ?token=...")
        }

        val base = "${uri.scheme}://$host:$port$path"
        val finalQuery = when {
            query.isNullOrBlank() -> "token=${encodeToken(token)}"
            query.contains("token=") -> query
            else -> "$query&token=${encodeToken(token)}"
        }
        return "$base?$finalQuery"
    }

    private fun saveBridgeSettings(url: String) {
        val token = extractTokenFromUrl(url)
        val uri = URI.create(url)
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_SESSION, activeSessionId)
            .apply()
        if (!token.isNullOrBlank()) {
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
        if (uri.host != null) {
            prefs.edit().putString(KEY_HOST, uri.host).apply()
        }
        if (uri.port >= 0) {
            prefs.edit().putString(KEY_PORT, uri.port.toString()).apply()
        }
    }

    private fun rememberConnectionHistory(url: String) {
        val existingName = connectionHistory.firstOrNull { it.url == url }?.name
        val entry = BridgeHistoryEntry.fromUrl(url, System.currentTimeMillis(), existingName) ?: return
        replaceConnectionHistory(
            listOf(entry) + connectionHistory.filterNot { it.url == entry.url },
        )
        persistConnectionHistory()
    }

    private fun removeConnectionHistory(url: String) {
        replaceConnectionHistory(connectionHistory.filterNot { it.url == url })
        persistConnectionHistory()
    }

    private fun renameConnectionHistory(url: String, name: String) {
        replaceConnectionHistory(
            connectionHistory.map { entry ->
                if (entry.url == url) entry.withName(name) else entry
            },
        )
        persistConnectionHistory()
    }

    private fun replaceConnectionHistory(entries: List<BridgeHistoryEntry>) {
        val normalized = entries
            .sortedByDescending { it.lastUsedAt }
            .distinctBy { it.url }
            .take(MAX_CONNECTION_HISTORY)
        connectionHistory.clear()
        connectionHistory.addAll(normalized)
    }

    private fun loadConnectionHistory(fallbackUrl: String): List<BridgeHistoryEntry> {
        val raw = prefs.getString(KEY_CONNECTION_HISTORY, "")?.trim().orEmpty()
        val parsed = mutableListOf<BridgeHistoryEntry>()
        if (raw.isNotBlank()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url", "").trim()
                    if (url.isBlank()) continue
                    BridgeHistoryEntry.fromUrl(
                        url = url,
                        lastUsedAt = item.optLong("lastUsedAt", 0L).takeIf { it > 0L } ?: System.currentTimeMillis(),
                        name = item.optString("name", "").trim(),
                    )?.let(parsed::add)
                }
            } catch (_: JSONException) {
                parsed.clear()
            }
        }
        if (parsed.isNotEmpty()) {
            return parsed
        }
        return fallbackUrl.takeIf { it.isNotBlank() }
            ?.let { BridgeHistoryEntry.fromUrl(it, System.currentTimeMillis()) }
            ?.let(::listOf)
            .orEmpty()
    }

    private fun persistConnectionHistory() {
        val array = JSONArray()
        connectionHistory.take(MAX_CONNECTION_HISTORY).forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("url", entry.url)
                    put("lastUsedAt", entry.lastUsedAt)
                    put("name", entry.name.orEmpty())
                },
            )
        }
        prefs.edit().putString(KEY_CONNECTION_HISTORY, array.toString()).apply()
    }

    private fun extractTokenFromUrl(url: String): String? {
        return try {
            extractTokenFromQuery(URI.create(url).rawQuery ?: "")
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTokenFromQuery(query: String): String? {
        if (query.isBlank()) return null
        return query.split('&')
            .mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2 && parts[0] == "token") parts[1] else null
            }
            .firstOrNull()
    }

    private fun pasteBridgeUrlFromClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.firstText()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun pasteBridgeUrl() {
        val pasted = pasteBridgeUrlFromClipboard()
        if (pasted.isNullOrBlank()) {
            connectionDetail = "剪贴板里没有可粘贴的地址"
            return
        }
        bridgeUrl = try {
            normalizeBridgeUrl(pasted, requireToken = false)
        } catch (_: Exception) {
            pasted
        }
        connectionDetail = "已粘贴 Bridge URL，点击连接即可"
    }

    private fun ensureConnected(): Boolean {
        if (bridgeClient?.isOpen() != true) {
            connectionDetail =
                if (autoReconnectEnabled && currentBridgeUrl != null) {
                    "连接已断开，正在自动重连"
                } else {
                    "请先连接 Linux Bridge"
                }
            showNotice(connectionDetail)
            return false
        }
        return true
    }

    private fun sendRequest(type: String, payload: JSONObject, handler: ResponseHandler? = null): Boolean {
        if (!ensureConnected()) return false

        val id = "req_${UUID.randomUUID().toString().replace("-", "")}"
        val request = JSONObject()
        try {
            request.put("id", id)
            request.put("type", type)
            request.put("payload", payload)
        } catch (error: JSONException) {
            appendSystemNote("请求构造失败: ${error.message}")
            return false
        }

        if (handler != null) {
            pendingRequests[id] = handler
        }
        return try {
            bridgeClient?.sendText(request.toString())
            true
        } catch (error: IOException) {
            pendingRequests.remove(id)
            appendSystemNote("发送失败: ${error.message}")
            showNotice("发送失败: ${error.message}")
            false
        }
    }

    private fun loadBridgeUrl(): String {
        prefs.getString(KEY_URL, "")?.trim()?.takeIf { it.isNotBlank() }?.let {
            return try {
                normalizeBridgeUrl(it, requireToken = false)
            } catch (_: Exception) {
                it
            }
        }

        val host = prefs.getString(KEY_HOST, "")?.trim().orEmpty()
        val port = prefs.getString(KEY_PORT, "8787")?.trim().orEmpty()
        val token = prefs.getString(KEY_TOKEN, "")?.trim().orEmpty()
        if (host.isBlank()) return ""
        val tokenPart = if (token.isBlank()) "" else "?token=${encodeToken(token)}"
        return try {
            normalizeBridgeUrl("ws://$host:${if (port.isBlank()) "8787" else port}/$tokenPart", requireToken = false)
        } catch (_: Exception) {
            ""
        }
    }

    private fun setAutoReconnectEnabled(enabled: Boolean) {
        autoReconnectEnabled = enabled
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
    }

    private fun resumeBridgeConnectionIfNeeded() {
        if (!autoReconnectEnabled || connected || bridgeClient != null) return
        val targetUrl = bridgeUrl.trim().takeIf { it.isNotBlank() } ?: return
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
        mainHandler.post {
            if (!connected && bridgeClient == null && autoReconnectEnabled) {
                connectToBridge(targetUrl, isAutoReconnect = true)
            }
        }
    }

    private fun scheduleReconnect(
        url: String,
        reason: String,
        error: Throwable?,
    ) {
        if (!autoReconnectEnabled) return
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return
        if (connected || bridgeClient != null || reconnectScheduled) return
        reconnectAttempt += 1
        val delayMs = reconnectDelayMillis(reconnectAttempt)
        reconnectScheduled = true
        currentBridgeUrl = normalizedUrl
        connectionDetail = buildString {
            append(reason.ifBlank { "连接已断开" })
            append(describeThrowable(error))
            append("，")
            append((delayMs / 1000L).coerceAtLeast(1L))
            append(" 秒后自动重连")
        }
        showNotice(connectionDetail)
        mainHandler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun cancelReconnectSchedule(resetAttempt: Boolean) {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectScheduled = false
        if (resetAttempt) {
            reconnectAttempt = 0
        }
    }

    private fun reconnectDelayMillis(attempt: Int): Long {
        return when {
            attempt <= 1 -> 2_000L
            attempt == 2 -> 4_000L
            attempt == 3 -> 8_000L
            attempt == 4 -> 15_000L
            else -> 30_000L
        }
    }

    private fun normalizeBridgeUrl(input: String, requireToken: Boolean): String {
        val raw = input.trim()
        if (raw.isBlank()) {
            if (requireToken) throw IllegalArgumentException("Bridge URL 不能为空")
            return ""
        }

        val scheme = if (raw.contains("wss://")) "wss" else "ws"
        val withoutScheme = raw.replace(Regex("^(?:wss?://)+"), "")
        val beforeQuery = withoutScheme.substringBefore("?")
        val query = withoutScheme.substringAfter("?", "")
        val slashIndex = beforeQuery.indexOf('/')
        val authority = if (slashIndex >= 0) beforeQuery.take(slashIndex) else beforeQuery
        val path = if (slashIndex >= 0) beforeQuery.substring(slashIndex).ifBlank { "/" } else "/"
        val authorityParts = authority.split(':').filter { it.isNotBlank() }
        val host = authorityParts.firstOrNull()?.trim()
            ?: throw IllegalArgumentException("URL host 不能为空")
        val port = authorityParts.drop(1).firstOrNull { part -> part.all { it.isDigit() } }
            ?: prefs.getString(KEY_PORT, "8787")?.trim()?.takeIf { it.isNotBlank() }
            ?: "8787"
        val token = extractTokenFromQuery(query).orEmpty()
            .ifBlank { prefs.getString(KEY_TOKEN, "")?.trim().orEmpty() }

        if (requireToken && token.isBlank()) {
            throw IllegalArgumentException("URL 里需要 token，例如 ?token=...")
        }

        val queryWithoutToken = query
            .split('&')
            .filter { it.isNotBlank() && !it.startsWith("token=") }
            .joinToString("&")
        val finalQuery = listOfNotNull(
            queryWithoutToken.takeIf { it.isNotBlank() },
            token.takeIf { it.isNotBlank() }?.let { "token=${encodeToken(it)}" },
        ).joinToString("&")

        return buildString {
            append("$scheme://$host:$port")
            append(path.ifBlank { "/" })
            if (finalQuery.isNotBlank()) {
                append('?')
                append(finalQuery)
            }
        }
    }

    private fun buildApprovalPresentation(eventName: String, payload: JSONObject): ApprovalPresentation {
        val lines = mutableListOf<String>()
        val diffEntries = payload.optJSONObject("fileChanges").toApprovalDiffEntries()
        val reason = payload.optString("reason", "")
        val command = payload.optString("command", "")
        val cwd = payload.optString("cwd", "")
        val grantRoot = payload.optString("grantRoot", "")
        if (reason.isNotBlank()) lines.add(reason)
        if (command.isNotBlank()) lines.add("命令: $command")
        if (cwd.isNotBlank()) lines.add("目录: $cwd")
        if (grantRoot.isNotBlank()) lines.add("授权目录: $grantRoot")
        if (diffEntries.isNotEmpty()) lines.add("变更文件: ${diffEntries.size} 个")
        if (lines.isEmpty()) lines.add(safeJson(payload))
        return ApprovalPresentation(
            title = when (eventName) {
                "item/fileChange/requestApproval", "applyPatchApproval" -> "文件修改需要审批"
                "item/permissions/requestApproval" -> "权限提升需要审批"
                "execCommandApproval", "item/commandExecution/requestApproval" -> "命令执行需要审批"
                else -> "需要审批"
            },
            detail = lines.joinToString("\n"),
            diffEntries = diffEntries,
        )
    }

    private fun buildApprovalActions(eventName: String, payload: JSONObject): List<ApprovalAction> {
        if (eventName == "item/permissions/requestApproval") {
            val permissions = payload.optJSONObject("permissions") ?: JSONObject()
            return listOf(
                ApprovalAction(
                    label = "本回合允许",
                    responsePayload = JSONObject().apply {
                        put("permissions", cloneJsonValue(permissions))
                        put("scope", "turn")
                    },
                    isPrimary = true,
                ),
                ApprovalAction(
                    label = "本会话允许",
                    responsePayload = JSONObject().apply {
                        put("permissions", cloneJsonValue(permissions))
                        put("scope", "session")
                    },
                ),
                ApprovalAction(
                    label = "拒绝",
                    responsePayload = JSONObject().apply {
                        put("error", "declined by user")
                    },
                ),
            )
        }

        val available = payload.optJSONArray("availableDecisions")
        val rawActions =
            if (available != null && available.length() > 0) {
                buildList {
                    for (i in 0 until available.length()) {
                        buildApprovalActionFromRawDecision(eventName, available.opt(i))?.let(::add)
                    }
                }
            } else {
                emptyList()
            }
        if (rawActions.isNotEmpty()) return rawActions

        return when (eventName) {
            "execCommandApproval", "applyPatchApproval" -> listOf(
                ApprovalAction(
                    label = "接受",
                    responsePayload = JSONObject().put("decision", "approved"),
                    isPrimary = true,
                ),
                ApprovalAction(
                    label = "拒绝",
                    responsePayload = JSONObject().put("decision", "denied"),
                ),
            )
            else -> listOf(
                ApprovalAction(
                    label = "接受",
                    responsePayload = JSONObject().put("decision", "accept"),
                    isPrimary = true,
                ),
                ApprovalAction(
                    label = "拒绝",
                    responsePayload = JSONObject().put("decision", "decline"),
                ),
            )
        }
    }

    private fun buildApprovalActionFromRawDecision(eventName: String, rawDecision: Any?): ApprovalAction? {
        val label = labelApprovalDecision(eventName, rawDecision) ?: return null
        return ApprovalAction(
            label = label,
            responsePayload = JSONObject().put("decision", cloneJsonValue(rawDecision)),
            isPrimary = isPrimaryApprovalDecision(rawDecision),
        )
    }

    private fun labelApprovalDecision(eventName: String, rawDecision: Any?): String? {
        val decisionKey =
            when (rawDecision) {
                is JSONObject -> rawDecision.keys().asSequence().firstOrNull().orEmpty()
                is String -> rawDecision
                else -> ""
            }
        if (decisionKey.isBlank()) return null
        return when (decisionKey) {
            "accept", "approved" -> "接受"
            "acceptForSession", "approved_for_session" -> "本会话允许"
            "decline", "denied" -> "拒绝"
            "cancel", "abort", "timed_out" -> "取消"
            "acceptWithExecpolicyAmendment", "approved_execpolicy_amendment" -> "接受并记住规则"
            "applyNetworkPolicyAmendment", "network_policy_amendment" -> "应用网络规则"
            else -> if (eventName == "item/permissions/requestApproval") "允许" else decisionKey
        }
    }

    private fun isPrimaryApprovalDecision(rawDecision: Any?): Boolean {
        return when (rawDecision) {
            is String -> rawDecision == "accept" || rawDecision == "approved" || rawDecision == "acceptForSession" || rawDecision == "approved_for_session"
            is JSONObject -> {
                val key = rawDecision.keys().asSequence().firstOrNull().orEmpty()
                key == "acceptWithExecpolicyAmendment"
                    || key == "applyNetworkPolicyAmendment"
                    || key == "approved_execpolicy_amendment"
                    || key == "network_policy_amendment"
            }
            else -> false
        }
    }

    private fun cloneJsonValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is JSONObject -> JSONObject(value.toString())
            is JSONArray -> JSONArray(value.toString())
            else -> value
        }
    }

    private fun buildConversationItemsFromSnapshot(payload: JSONObject?): List<ConversationItem> {
        if (payload == null) return emptyList()
        val items = mutableListOf<ConversationItem>()
        payload.optJSONArray("entries")?.let { entries ->
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                buildConversationItemFromSnapshotEntry(entry)?.let(items::add)
            }
        }
        return items
    }

    private fun buildPendingApprovalsFromSnapshot(payload: JSONObject?): List<ApprovalDialogState> {
        if (payload == null) return emptyList()
        val approvals = mutableListOf<ApprovalDialogState>()
        payload.optJSONArray("pending_approvals")?.let { array ->
            for (i in 0 until array.length()) {
                val approval = array.optJSONObject(i) ?: continue
                buildConversationApprovalFromSnapshot(approval)?.let(approvals::add)
            }
        }
        return approvals
    }

    private fun buildConversationItemFromSnapshotEntry(entry: JSONObject): ConversationItem? {
        return when (entry.optString("type", "")) {
            "item" -> buildConversationSnapshotThreadItem(entry)
            "turn_status" -> buildConversationSnapshotTurnStatus(entry)
            else -> null
        }
    }

    private fun buildConversationSnapshotThreadItem(entry: JSONObject): ConversationItem? {
        val item = entry.optJSONObject("item") ?: return null
        val itemType = item.optString("type", "").trim()
        val turnId = entry.optString("turn_id", "").trim()
        val itemId = item.optString("id", "").trim()
        return when (itemType) {
            "userMessage" -> {
                val text = extractThreadItemText(item)
                if (text.isBlank()) return null
                ConversationItem.Bubble(
                    id = "snapshot_user_${itemId.ifBlank { turnId.ifBlank { "item" } }}",
                    right = true,
                    text = text,
                    backgroundColor = 0xFF1A8F55.toInt(),
                    textColor = AndroidColor.WHITE,
                    turnKey = turnId.takeIf { it.isNotBlank() },
                )
            }
            "agentMessage" -> {
                val text = extractThreadItemText(item)
                if (text.isBlank()) return null
                ConversationItem.Bubble(
                    id = "snapshot_agent_${itemId.ifBlank { turnId.ifBlank { "item" } }}",
                    right = false,
                    text = text,
                    backgroundColor = 0xFFF1F8F2.toInt(),
                    textColor = 0xFF183326.toInt(),
                    turnKey = turnId.ifBlank { "assistant" },
                    assistantKey = itemId.takeIf { it.isNotBlank() },
                )
            }
            "fileChange" -> {
                val diffEntries = item.optJSONArray("changes").toConversationDiffEntries()
                val status = item.optString("status", "").trim()
                ConversationItem.FileChange(
                    id = "snapshot_file_${itemId.ifBlank { turnId.ifBlank { "item" } }}",
                    title = if (status == "inProgress") "文件修改中" else "文件修改",
                    summary = buildFileChangeSummary(status, diffEntries, fallbackDiff = null),
                    status = status,
                    diffEntries = diffEntries,
                    fallbackDiff = null,
                    sourceItemId = itemId.takeIf { it.isNotBlank() },
                    turnId = turnId.takeIf { it.isNotBlank() },
                )
            }
            else -> null
        }
    }

    private fun buildConversationSnapshotTurnStatus(entry: JSONObject): ConversationItem? {
        val turnId = entry.optString("turn_id", "").trim()
        val status = entry.optString("status", "").trim()
        val errorText = extractErrorText(entry.optJSONObject("error"))
        if (status != "failed" && status != "interrupted" && errorText.isBlank()) {
            return null
        }
        return ConversationItem.SystemNote(
            id = "snapshot_turn_status_${turnId.ifBlank { "unknown" }}",
            text = buildString {
                append("当前回合结束: ${if (status.isNotBlank()) status else "unknown"}")
                if (errorText.isNotBlank()) {
                    append('\n')
                    append(errorText)
                }
            },
            itemKey = turnId.takeIf { it.isNotBlank() },
        )
    }

    private fun buildConversationApprovalFromSnapshot(approval: JSONObject): ApprovalDialogState? {
        val requestId = approval.optString("request_id", "").trim()
        if (requestId.isBlank()) return null
        val eventName = approval.optString("request_method", "").trim().ifBlank { "approval" }
        val payload = approval.optJSONObject("payload") ?: JSONObject()
        val presentation = buildApprovalPresentation(eventName, payload)
        return ApprovalDialogState(
            requestId = requestId,
            title = presentation.title,
            detail = presentation.detail,
            actions = buildApprovalActions(eventName, payload),
            diffEntries = presentation.diffEntries,
            turnId = approval.optString("turn_id", "").trim().takeIf { it.isNotBlank() },
        )
    }

    private fun handleThreadItemEvent(eventName: String, payload: JSONObject) {
        val item = payload.optJSONObject("item") ?: return
        val turnKey = extractTurnKey(payload = payload)
        val itemKey = extractAssistantItemKey(payload = payload, item = item)
        when (item.optString("type", "")) {
            "agentMessage" -> {
                if (eventName == "item/completed") {
                    val text = extractThreadItemText(item).ifBlank { item.optString("text", "").trim() }
                    finalizeAssistantBubble(turnKey, itemKey, text.ifBlank { formatJson(item) })
                }
            }
            "fileChange" -> handleFileChangeItem(turnKey, item)
            else -> Unit
        }
    }

    private fun handleToolItem(eventName: String, item: JSONObject) {
        val itemId = item.optString("id", "").trim()
        if (itemId.isBlank()) return
        val title = if (eventName == "item/started") {
            "${labelThreadItem(item)} 开始"
        } else {
            labelThreadItem(item)
        }
        val detail = when (item.optString("type", "")) {
            "plan", "reasoning" -> formatThreadItemDetails(item)
            else -> formatToolItemDetails(item, eventName)
        }.trim()
        val text = if (detail.isBlank()) title else "$title\n$detail"
        val existingId = toolItemIds[itemId]
        if (existingId == null) {
            val created = ConversationItem.SystemNote(
                id = "tool_${UUID.randomUUID()}",
                text = text,
                itemKey = itemId,
            )
            toolItemIds[itemId] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(existingId) { current ->
            if (current is ConversationItem.SystemNote) {
                current.copy(text = text, itemKey = itemId)
            } else {
                current
            }
        }
    }

    private fun extractThreadItemText(item: JSONObject): String {
        return when (item.optString("type", "")) {
            "userMessage" -> extractPlainTextContent(item.optJSONArray("content"))
            "agentMessage" -> firstNonEmpty(
                item.optString("text", "").trim(),
                extractPlainTextContent(item.optJSONArray("content")),
            )
            "plan" -> item.optString("text", "").trim()
            "reasoning" -> {
                val summary = item.optJSONArray("summary")?.let { array ->
                    buildList {
                        for (i in 0 until array.length()) {
                            array.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
                val content = item.optJSONArray("content")?.let { array ->
                    buildList {
                        for (i in 0 until array.length()) {
                            array.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.orEmpty()
                listOfNotNull(
                    summary.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                    content.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                ).joinToString("\n").trim()
            }

            else -> ""
        }
    }

    private fun extractPlainTextContent(content: JSONArray?): String {
        if (content == null) return ""
        val parts = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i)
            if (part?.optString("type", "") == "text") {
                part.optString("text", "").trim().takeIf { it.isNotBlank() }?.let(parts::add)
            }
        }
        return parts.joinToString("\n").trim()
    }

    private fun formatThreadItemDetails(item: JSONObject): String {
        return when (item.optString("type", "")) {
            "reasoning" -> {
                val summary = item.optJSONArray("summary")?.toString(2).orEmpty()
                val content = item.optJSONArray("content")?.toString(2).orEmpty()
                listOf(summary, content).filter { it.isNotBlank() }.joinToString("\n")
            }

            else -> formatJson(item)
        }
    }

    private fun formatToolItemDetails(item: JSONObject, eventName: String): String {
        val lines = mutableListOf<String>()
        when (item.optString("type", "")) {
            "commandExecution" -> {
                if (eventName == "item/started") {
                    item.optString("command", "").takeIf { it.isNotBlank() }?.let { lines.add("命令: $it") }
                    item.optString("cwd", "").takeIf { it.isNotBlank() }?.let { lines.add("目录: $it") }
                    item.optString("source", "").takeIf { it.isNotBlank() }?.let { lines.add("来源: $it") }
                    item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                } else {
                    item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                    item.opt("exitCode").takeIf { it != null }?.let { lines.add("退出码: $it") }
                    item.optString("aggregatedOutput", "").takeIf { it.isNotBlank() }?.let { lines.add("输出:\n$it") }
                    item.opt("durationMs").takeIf { it != null }?.let { lines.add("耗时: ${it}ms") }
                }
            }

            "mcpToolCall" -> {
                item.optString("server", "").takeIf { it.isNotBlank() }?.let { lines.add("服务: $it") }
                item.optString("tool", "").takeIf { it.isNotBlank() }?.let { lines.add("工具: $it") }
                item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                if (eventName == "item/completed") {
                    item.opt("result")?.let { lines.add("结果: $it") }
                    item.opt("error")?.let { lines.add("错误: $it") }
                }
            }

            "dynamicToolCall" -> {
                item.optString("namespace", "").takeIf { it.isNotBlank() }?.let { lines.add("命名空间: $it") }
                item.optString("tool", "").takeIf { it.isNotBlank() }?.let { lines.add("工具: $it") }
                item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                if (eventName == "item/completed") {
                    item.optJSONArray("contentItems")?.let { contentItems ->
                        lines.add("结果条目: ${contentItems.length()} 项")
                        lines.add(contentItems.toString(2))
                    }
                }
            }

            "collabAgentToolCall" -> {
                item.optString("tool", "").takeIf { it.isNotBlank() }?.let { lines.add("工具: $it") }
                item.optString("status", "").takeIf { it.isNotBlank() }?.let { lines.add("状态: $it") }
                item.optString("prompt", "").takeIf { it.isNotBlank() }?.let { lines.add("提示:\n$it") }
            }

            else -> {
                if (eventName == "item/started") {
                    lines.add("开始")
                }
                lines.add(formatJson(item))
            }
        }
        return if (lines.isEmpty()) formatJson(item) else lines.joinToString("\n")
    }

    private fun handleFileChangeItem(turnKey: String, item: JSONObject) {
        val itemId = item.optString("id", "").trim()
        if (itemId.isBlank()) return
        val changes = item.optJSONArray("changes").toConversationDiffEntries()
        val status = item.optString("status", "").trim()
        upsertFileChangeConversationItem(
            itemId = itemId,
            turnKey = turnKey,
            status = status,
            diffEntries = changes,
            fallbackDiff = turnDiffs[turnKey],
        )
    }

    private fun handleFileChangePatchUpdated(message: JSONObject, payload: JSONObject) {
        val itemId = extractAssistantItemKey(message = message, payload = payload)
        if (itemId.isBlank()) return
        val turnKey = extractTurnKey(message, payload)
        val changes = payload.optJSONArray("changes").toConversationDiffEntries()
        upsertFileChangeConversationItem(
            itemId = itemId,
            turnKey = turnKey,
            status = "inProgress",
            diffEntries = changes,
            fallbackDiff = turnDiffs[turnKey],
        )
    }

    private fun handleTurnDiffUpdated(message: JSONObject, payload: JSONObject) {
        val turnKey = extractTurnKey(message, payload)
        val diff = payload.optString("diff", "").trim()
        if (turnKey.isBlank() || diff.isBlank()) return
        turnDiffs[turnKey] = diff

        val existingFileChangeItemId = fileChangeTurnIds.entries.firstOrNull { it.value == turnKey }?.key
        if (existingFileChangeItemId != null) {
            val conversationItemId = fileChangeItemIds[existingFileChangeItemId]
            if (conversationItemId != null) {
                replaceConversationItem(conversationItemId) { current ->
                    if (current is ConversationItem.FileChange) {
                        current.copy(
                            summary = buildFileChangeSummary(
                                status = current.status,
                                diffEntries = current.diffEntries,
                                fallbackDiff = diff,
                            ),
                            fallbackDiff = diff,
                        )
                    } else {
                        current
                    }
                }
                return
            }
        }

        upsertTurnDiffConversationItem(turnKey, diff)
    }

    private fun upsertFileChangeConversationItem(
        itemId: String,
        turnKey: String,
        status: String,
        diffEntries: List<ConversationDiffEntry>,
        fallbackDiff: String?,
    ) {
        if (itemId.isBlank() || turnKey.isBlank()) return
        fileChangeTurnIds[itemId] = turnKey
        turnDiffItemIds.remove(turnKey)?.let(::removeConversationItem)

        val summary = buildFileChangeSummary(status, diffEntries, fallbackDiff)
        val existingId = fileChangeItemIds[itemId]
        if (existingId == null) {
            val created = ConversationItem.FileChange(
                id = "file_change_${UUID.randomUUID()}",
                title = "文件修改",
                summary = summary,
                status = status,
                diffEntries = diffEntries,
                fallbackDiff = fallbackDiff,
            )
            fileChangeItemIds[itemId] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(existingId) { current ->
            if (current is ConversationItem.FileChange) {
                current.copy(
                    title = "文件修改",
                    summary = summary,
                    status = status,
                    diffEntries = diffEntries,
                    fallbackDiff = fallbackDiff,
                )
            } else {
                current
            }
        }
    }

    private fun upsertTurnDiffConversationItem(turnKey: String, diff: String) {
        val summary = buildFileChangeSummary(status = "inProgress", diffEntries = emptyList(), fallbackDiff = diff)
        val existingId = turnDiffItemIds[turnKey]
        if (existingId == null) {
            val created = ConversationItem.FileChange(
                id = "turn_diff_${UUID.randomUUID()}",
                title = "文件修改中",
                summary = summary,
                status = "inProgress",
                diffEntries = emptyList(),
                fallbackDiff = diff,
            )
            turnDiffItemIds[turnKey] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(existingId) { current ->
            if (current is ConversationItem.FileChange) {
                current.copy(
                    title = "文件修改中",
                    summary = summary,
                    status = "inProgress",
                    fallbackDiff = diff,
                )
            } else {
                current
            }
        }
    }

    private fun buildFileChangeSummary(
        status: String,
        diffEntries: List<ConversationDiffEntry>,
        fallbackDiff: String?,
    ): String {
        val lines = mutableListOf<String>()
        labelPatchStatus(status).takeIf { it.isNotBlank() }?.let(lines::add)

        if (diffEntries.isNotEmpty()) {
            lines.add("${diffEntries.size} 个文件")
            buildDiffStatsLine(diffEntries = diffEntries, fallbackDiff = fallbackDiff)?.let(lines::add)
        } else if (!fallbackDiff.isNullOrBlank()) {
            buildDiffStatsLine(diffEntries = emptyList(), fallbackDiff = fallbackDiff)?.let(lines::add)
        }

        return if (lines.isEmpty()) "点击查看详情" else lines.joinToString("\n")
    }

    private fun buildDiffStatsLine(
        diffEntries: List<ConversationDiffEntry>,
        fallbackDiff: String?,
    ): String? {
        val source =
            if (diffEntries.isNotEmpty()) {
                diffEntries.joinToString("\n") { it.diff }
            } else {
                fallbackDiff.orEmpty()
            }
        return parseDiffStats(source)?.toLabel()
    }

    private fun labelPatchStatus(status: String): String {
        return when (status.trim()) {
            "inProgress" -> "进行中"
            "completed" -> "已完成"
            "failed" -> "失败"
            "declined" -> "已拒绝"
            else -> status.trim()
        }
    }

    private fun JSONArray?.toConversationDiffEntries(): List<ConversationDiffEntry> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.toConversationDiffEntry()?.let(::add)
            }
        }
    }

    private fun JSONObject.toConversationDiffEntry(): ConversationDiffEntry? {
        val path = normalizeNullablePath(opt("path")).orEmpty()
        if (path.isBlank()) return null
        val kindObject = optJSONObject("kind")
        val kindType = firstNonEmpty(
            kindObject?.optString("type", "") ?: "",
            optString("type", ""),
            "update",
        )
        val movePath = firstNonEmpty(
            normalizeNullablePath(kindObject?.opt("move_path")).orEmpty(),
            normalizeNullablePath(opt("move_path")).orEmpty(),
        ).takeIf { it.isNotBlank() }
        return ConversationDiffEntry(
            path = path,
            kind = kindType,
            diff = optString("diff", "").trim(),
            movePath = movePath,
        )
    }

    private fun JSONObject?.toApprovalDiffEntries(): List<ConversationDiffEntry> {
        if (this == null) return emptyList()
        val changes = mutableListOf<ConversationDiffEntry>()
        val keys = keys()
        while (keys.hasNext()) {
            val path = normalizeNullablePath(keys.next()).orEmpty()
            if (path.isBlank()) continue
            val change = optJSONObject(path) ?: continue
            val kind = change.optString("type", "").trim().ifBlank { "update" }
            val diff = when (kind) {
                "update" -> change.optString("unified_diff", "").trim()
                "add" -> change.optString("content", "").trim()
                "delete" -> change.optString("content", "").trim()
                else -> safeJson(change)
            }
            changes.add(
                ConversationDiffEntry(
                    path = path,
                    kind = kind,
                    diff = diff,
                    movePath = normalizeNullablePath(change.opt("move_path")),
                ),
            )
        }
        return changes
    }

    private fun appendAssistantDeltaBubble(turnKey: String, itemKey: String, delta: String) {
        if (delta.isBlank()) return
        val bubbleKey = buildAssistantBubbleKey(turnKey, itemKey)
        val bubbleId = assistantItemIds[bubbleKey]
        if (bubbleId == null) {
            val created = createAssistantBubble(turnKey, delta, bubbleKey)
            assistantItemIds[bubbleKey] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(bubbleId) { item ->
            if (item is ConversationItem.Bubble) {
                item.copy(text = item.text + delta)
            } else {
                item
            }
        }
    }

    private fun finalizeAssistantBubble(turnKey: String, itemKey: String, text: String) {
        if (text.isBlank()) return
        if (itemKey.isBlank()) {
            appendStandaloneAssistantBubble(turnKey, text)
            return
        }

        val bubbleKey = buildAssistantBubbleKey(turnKey, itemKey)
        val bubbleId = assistantItemIds[bubbleKey]
        if (bubbleId == null) {
            if (hasEquivalentAssistantBubble(turnKey, text)) return
            val created = createAssistantBubble(turnKey, text, bubbleKey)
            assistantItemIds[bubbleKey] = created.id
            conversationItems.add(created)
            return
        }

        replaceConversationItem(bubbleId) { item ->
            if (item is ConversationItem.Bubble) {
                item.copy(text = preferredAssistantText(item.text, text))
            } else {
                item
            }
        }
    }

    private fun appendStandaloneAssistantBubble(turnKey: String, text: String) {
        if (text.isBlank()) return
        if (hasEquivalentAssistantBubble(turnKey, text)) return
        conversationItems.add(createAssistantBubble(turnKey, text))
    }

    private fun createAssistantBubble(turnKey: String, text: String, assistantKey: String? = null): ConversationItem.Bubble {
        return ConversationItem.Bubble(
            id = "assistant_${UUID.randomUUID()}",
            right = false,
            text = text,
            backgroundColor = 0xFFF1F8F2.toInt(),
            textColor = 0xFF183326.toInt(),
            turnKey = turnKey,
            assistantKey = assistantKey,
        )
    }

    private fun extractTurnKey(message: JSONObject? = null, payload: JSONObject? = null): String {
        return firstNonEmpty(
            message?.optString("turn_id", "") ?: "",
            message?.optString("turnId", "") ?: "",
            payload?.optString("turn_id", "") ?: "",
            payload?.optString("turnId", "") ?: "",
            "assistant",
        )
    }

    private fun extractExplicitTurnKey(message: JSONObject? = null, payload: JSONObject? = null): String {
        return firstNonEmpty(
            message?.optString("turn_id", "") ?: "",
            message?.optString("turnId", "") ?: "",
            payload?.optString("turn_id", "") ?: "",
            payload?.optString("turnId", "") ?: "",
        )
    }

    private fun extractAssistantItemKey(message: JSONObject? = null, payload: JSONObject? = null, item: JSONObject? = null): String {
        return firstNonEmpty(
            message?.optString("item_id", "") ?: "",
            message?.optString("itemId", "") ?: "",
            payload?.optString("item_id", "") ?: "",
            payload?.optString("itemId", "") ?: "",
            item?.optString("id", "") ?: "",
        )
    }

    private fun buildAssistantBubbleKey(turnKey: String, itemKey: String): String {
        return "$turnKey::$itemKey"
    }

    private fun hasEquivalentAssistantBubble(turnKey: String, text: String): Boolean {
        val normalized = normalizeAssistantText(text)
        if (normalized.isBlank()) return false
        return conversationItems.asReversed().any { item ->
            val bubble = item as? ConversationItem.Bubble ?: return@any false
            !bubble.right && bubble.turnKey == turnKey && assistantTextsOverlap(bubble.text, normalized)
        }
    }

    private fun assistantTextsOverlap(left: String, right: String): Boolean {
        val leftNormalized = normalizeAssistantText(left)
        val rightNormalized = normalizeAssistantText(right)
        if (leftNormalized.isBlank() || rightNormalized.isBlank()) return false
        return leftNormalized == rightNormalized ||
            leftNormalized.contains(rightNormalized) ||
            rightNormalized.contains(leftNormalized)
    }

    private fun preferredAssistantText(current: String, incoming: String): String {
        val currentNormalized = normalizeAssistantText(current)
        val incomingNormalized = normalizeAssistantText(incoming)
        return when {
            currentNormalized.isBlank() -> incoming
            incomingNormalized.isBlank() -> current
            currentNormalized == incomingNormalized -> incoming
            incomingNormalized.contains(currentNormalized) -> incoming
            else -> current
        }
    }

    private fun normalizeAssistantText(text: String): String {
        return text.replace("\r\n", "\n").trim()
    }

    private fun updateLiveTurnStatusFromItem(eventName: String, item: JSONObject?) {
        if (item == null) return
        val started = eventName == "item/started"
        val status = when (item.optString("type", "")) {
            "contextCompaction" -> if (started) "Codex 正在整理上下文…" else "Codex 继续响应中…"
            "reasoning" -> if (started) "Codex 正在思考…" else "Codex 继续响应中…"
            "plan" -> if (started) "Codex 正在整理计划…" else "Codex 继续响应中…"
            "commandExecution" -> if (started) "Codex 正在执行命令…" else "Codex 继续响应中…"
            "fileChange" -> if (started) "Codex 正在修改文件…" else "Codex 继续响应中…"
            "mcpToolCall" -> if (started) "Codex 正在调用工具…" else "Codex 继续响应中…"
            "dynamicToolCall" -> if (started) "Codex 正在运行工具…" else "Codex 继续响应中…"
            "collabAgentToolCall" -> if (started) "Codex 正在协调子任务…" else "Codex 继续响应中…"
            "agentMessage" -> "Codex 正在输出…"
            else -> null
        }
        if (status != null) {
            updateLiveTurnStatus(status)
        }
    }

    private fun updateLiveTurnStatusFromWarning(payload: JSONObject) {
        val message = extractWarningText(payload)
        if (message.contains("Long threads", ignoreCase = true)) {
            updateLiveTurnStatus("Codex 正在整理长上下文…")
        }
    }

    private fun updateLiveTurnStatusFromError(payload: JSONObject) {
        val error = payload.optJSONObject("error") ?: return
        val message = cleanDisplayText(error.opt("message"))
        when {
            message.startsWith("Reconnecting...", ignoreCase = true) -> updateLiveTurnStatus("Codex 连接波动，正在重试…")
            payload.optBoolean("willRetry", false) -> updateLiveTurnStatus("Codex 请求异常，正在重试…")
            message.isNotBlank() -> updateLiveTurnStatus("Codex 响应异常")
        }
    }

    private fun updateLiveTurnStatusFromThreadStatus(status: JSONObject?) {
        when (status?.optString("type", "")?.trim()) {
            "active" -> updateLiveTurnStatus("Codex 正在处理中…")
            "idle" -> if (activeTurnId != null) updateLiveTurnStatus("Codex 正在处理中…")
            "systemError" -> updateLiveTurnStatus("Codex 响应异常")
        }
    }

    private fun updateLiveTurnStatus(status: String?) {
        liveTurnStatus = status?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun labelRawResponseRole(role: String): String {
        return when (role) {
            "assistant" -> "助手"
            "developer" -> "开发者"
            "user" -> "用户"
            else -> role.ifBlank { "消息" }
        }
    }

    private fun labelThreadItem(item: JSONObject): String {
        return when (item.optString("type", "")) {
            "plan" -> "计划"
            "reasoning" -> "思考"
            "commandExecution" -> "命令执行"
            "fileChange" -> "文件修改"
            "contextCompaction" -> "上下文压缩"
            else -> item.optString("type", "项目")
        }
    }

    private fun extractWarningText(payload: JSONObject): String {
        val message = payload.optString("message", "").trim()
        return if (message.isNotBlank()) message else formatJson(payload)
    }

    private fun extractErrorText(error: JSONObject?): String {
        if (error == null) return ""
        val nestedMessage = cleanDisplayText(error.opt("message"))
        val parsedNested = nestedMessage.takeIf { it.startsWith("{") && it.endsWith("}") }?.let { raw ->
            try {
                JSONObject(raw)
            } catch (_: JSONException) {
                null
            }
        }
        val parts = mutableListOf<String>()
        cleanDisplayText(error.opt("code")).takeIf { it.isNotBlank() }?.let { parts.add(it) }
        if (parsedNested != null) {
            val nestedText = extractErrorText(parsedNested)
            if (nestedText.isNotBlank()) parts.add(nestedText)
        } else if (nestedMessage.isNotBlank()) {
            parts.add(nestedMessage)
        }
        cleanDisplayText(error.opt("additionalDetails")).takeIf { it.isNotBlank() }?.let { details ->
            if (details != nestedMessage) parts.add(details)
        }
        error.optJSONObject("codexErrorInfo")?.let { nested ->
            val nestedText = extractErrorText(nested)
            if (nestedText.isNotBlank()) parts.add(nestedText)
        }
        return if (parts.isNotEmpty()) parts.joinToString("\n") else error.keys().asSequence().joinToString("\n") { key ->
            val value = cleanDisplayText(error.opt(key))
            if (value.isNotBlank()) "$key: $value" else "$key"
        }
    }

    private fun describeThreadStatus(status: JSONObject?): String {
        if (status == null) return "未知"
        val type = status.optString("type", "").trim()
        return when (type) {
            "active" -> "活跃"
            "idle" -> "空闲"
            "systemError" -> "系统错误"
            "notLoaded" -> "未加载"
            else -> if (type.isNotBlank()) type else formatJson(status)
        }
    }

    private fun cleanDisplayText(value: Any?): String {
        val text = when (value) {
            null -> ""
            is String -> value
            is JSONObject -> value.toString()
            is JSONArray -> value.toString()
            else -> value.toString()
        }.trim()
        return if (text.isBlank() || text.equals("null", ignoreCase = true)) "" else text
    }

    private fun selectModel(modelId: String) {
        selectedModel = modelId.trim()
        prefs.edit().putString(KEY_MODEL, selectedModel).apply()
    }

    private fun replaceModels(newModels: List<ModelInfo>) {
        availableModels.clear()
        availableModels.addAll(newModels.sortedWith(compareByDescending<ModelInfo> { it.isDefault }.thenBy { it.displayName.lowercase(Locale.getDefault()) }))
    }

    private fun currentSessionModel(): String {
        val sessionId = activeSessionId ?: return ""
        return sessions.firstOrNull { it.sessionId == sessionId }?.model.orEmpty().trim()
            .takeIf { isKnownModel(it) }
            .orEmpty()
    }

    private fun resolveModelForSend(): String {
        val candidates = listOf(
            selectedModel.trim(),
            currentSessionModel(),
            availableModels.firstOrNull()?.model.orEmpty(),
            availableModels.firstOrNull()?.id.orEmpty(),
        )
        return candidates.firstOrNull { isKnownModel(it) } ?: ""
    }

    private fun isKnownModel(modelId: String): Boolean {
        val target = modelId.trim()
        if (target.isBlank()) return false
        return availableModels.any { it.model == target || it.id == target }
    }

    private fun currentSessionLabel(): String {
        val sessionId = activeSessionId ?: return "未选择会话"
        val info = sessions.firstOrNull { it.sessionId == sessionId }
        return if (info != null) {
            "当前会话: ${info.titleLine()}"
        } else {
            "当前会话: ${shortId(sessionId)}"
        }
    }

    private fun buildStatusLine(): String {
        val parts = mutableListOf(if (connected) "已连接" else "未连接")
        activeSessionId?.let { parts.add("会话 ${shortId(it)}") }
        activeTurnId?.let { parts.add("回合 ${shortId(it)}") }
        return parts.joinToString(" · ")
    }

    private fun connectionStatusHeadline(): String {
        val entry = currentConnectionEntry()
        if (!connected) {
            val name = entry?.displayName()?.takeIf { !currentBridgeUrl.isNullOrBlank() && it.isNotBlank() }
            return if (name != null) {
                if (reconnectScheduled || reconnectAttempt > 0) "自动重连：$name" else "正在连接：$name"
            } else {
                "当前未连接"
            }
        }
        val name = entry?.displayName()?.takeIf { it.isNotBlank() }
        return if (name != null) {
            "当前连接：$name"
        } else {
            "当前已连接"
        }
    }

    private fun connectionStatusDetailText(): String {
        val entry = currentConnectionEntry()
        if (!connected) {
            if (currentBridgeUrl.isNullOrBlank() || entry == null) return connectionDetail
            return buildList {
                add(if (reconnectScheduled || reconnectAttempt > 0) "正在自动重连" else "正在建立连接")
                entry.maskedUrl.takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString("\n")
        }
        entry ?: return connectionDetail
        return entry.maskedUrl.takeIf { it.isNotBlank() } ?: connectionDetail
    }

    private fun drawerConnectionSummaryText(): String {
        val target = currentBridgeUrl?.takeIf { it.isNotBlank() } ?: bridgeUrl.trim().takeIf { it.isNotBlank() }
        val entry = target?.let { url ->
            connectionHistory.firstOrNull { it.url == url } ?: BridgeHistoryEntry.fromUrl(url)
        }
        val label = entry?.displayName()?.takeIf { it.isNotBlank() } ?: target
        return label?.let { "连接到 $it" } ?: "未连接"
    }

    private fun currentConnectionEntry(): BridgeHistoryEntry? {
        val url = currentBridgeUrl?.takeIf { it.isNotBlank() } ?: bridgeUrl.takeIf { connected && it.isNotBlank() }
        return url?.let { target ->
            connectionHistory.firstOrNull { it.url == target } ?: BridgeHistoryEntry.fromUrl(target)
        }
    }

    private fun activeSession(): SessionInfo? = activeSessionId?.let { sessionId -> sessions.firstOrNull { it.sessionId == sessionId } }

    private fun currentWorkspacePath(): String? {
        return selectedWorkspace
            ?: activeSession()?.cwd?.takeIf { it.isNotBlank() }
            ?: sessions.firstOrNull { it.cwd.isNotBlank() }?.cwd
    }

    private fun workspacePaths(): List<String> {
        return sessions.mapNotNull { it.cwd.takeIf(String::isNotBlank) }.distinct().sorted()
    }

    private fun sessionsForSelectedWorkspace(): List<SessionInfo> {
        val workspace = selectedWorkspace
        return if (workspace.isNullOrBlank()) {
            sessions.sortedByDescending { it.updatedAt }
        } else {
            sessions.filter { it.cwd == workspace }.sortedByDescending { it.updatedAt }
        }
    }

    private fun workspaceDisplayName(path: String?, fallback: String): String {
        val normalized = path?.trim().orEmpty()
        if (normalized.isBlank()) return fallback
        return normalized.substringAfterLast('/').ifBlank { normalized }
    }

    private fun projectBuckets(): List<ProjectGroup> {
        val groups =
            workspacePaths().map { path ->
                ProjectGroup(
                    path = path,
                    sessions = sessions.filter { it.cwd == path }.sortedByDescending { it.updatedAt },
                )
            }
        val uncategorized = sessions.filter { it.cwd.isBlank() }.sortedByDescending { it.updatedAt }
        return buildList {
            addAll(groups.sortedByDescending { it.sessions.firstOrNull()?.updatedAt.orEmpty() })
            if (uncategorized.isNotEmpty()) {
                add(
                    ProjectGroup(
                        path = null,
                        sessions = uncategorized,
                    ),
                )
            }
        }
    }

    private fun openNewChatDialog(projectPath: String?) {
        if (!connected) {
            showNotice("请先连接 Linux Bridge")
            return
        }
        val defaultModel =
            resolveModelForSend().ifBlank {
                availableModels.firstOrNull()?.model.orEmpty()
            }
        newChatDraft =
            NewChatDraft(
                projectPath = projectPath?.takeIf { it.isNotBlank() },
                model = defaultModel,
                approvalPolicy = "never",
                sandbox = "danger-full-access",
            )
    }

    private fun approvalPolicyDescription(policy: String): String {
        return when (policy.trim()) {
            "untrusted" -> "最保守。更广泛地要求审批，适合不信任当前环境时使用。"
            "on-request" -> "默认推荐。Codex 主动请求时再审批。"
            "on-failure" -> "先尝试执行，失败后再请求审批。"
            "never" -> "不进行审批，按当前会话权限直接执行。"
            else -> "将按该 approvalPolicy 原样下发给 Codex。"
        }
    }

    private fun openSessionInfoSheet() {
        val session = activeSession() ?: return
        sessionInfoSheetState =
            SessionInfoSheetState(
                title = session.titleLine(),
                rows =
                    buildList {
                        add("目录" to session.cwd.ifBlank { "未提供" })
                        add("模型" to session.model.ifBlank { "未提供" })
                        add("审批策略" to session.approvalPolicy.ifBlank { "未提供" })
                        add("权限配置" to session.permissionsSummary())
                        add("沙箱" to session.sandboxSummary())
                        add("上下文窗口" to session.contextWindowSummary())
                        add("最近 token" to session.lastTokenUsageSummary())
                        add("累计 token" to session.totalTokenUsageSummary())
                        add("会话 ID" to session.sessionId)
                    },
            )
    }

    private fun showNotice(message: String) {
        transientNotice = message
        transientNonce += 1
    }

    private fun startSessionSyncLoop() {
        stopSessionSyncLoop()
        if (!connected || activeSessionId.isNullOrBlank()) return
        mainHandler.postDelayed(syncRunnable, 1500L)
    }

    private fun stopSessionSyncLoop() {
        mainHandler.removeCallbacks(syncRunnable)
        syncInFlight = false
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = uiText)
    }

    @Composable
    private fun Label(text: String) {
        Text(text = text, fontSize = 12.sp, color = uiMuted)
    }

    @Composable
    private fun BodyText(
        text: String,
        modifier: Modifier = Modifier,
    ) {
        Text(text = text, modifier = modifier, fontSize = 13.sp, color = uiMuted, lineHeight = 20.sp)
    }

    private fun buildCodeBrowserAnnotatedText(
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
            CodeTextMode.File -> {
                val renderedText = buildFileAnnotatedText(normalized, language, syntaxHighlight = !lightweight)
                CodeBrowserRenderedContent(text = renderedText, lightweight = lightweight)
            }
        }
    }

    private fun buildCodeBrowserRenderedContent(
        text: String,
        mode: CodeTextMode,
        pathHint: String? = null,
    ): CodeBrowserRenderedContent {
        return buildCodeBrowserAnnotatedText(text, mode, pathHint)
    }

    private fun buildCodeBrowserRenderCacheKey(
        text: String,
        mode: CodeTextMode,
        pathHint: String?,
    ): String {
        return "${mode.name}|${pathHint.orEmpty()}|${text.length}|${text.hashCode()}"
    }

    private fun countCodeBrowserLines(text: String): Int {
        if (text.isEmpty()) return 1
        var lines = 1
        text.forEach { ch ->
            if (ch == '\n') lines += 1
        }
        return lines
    }

    private fun buildDiffRenderedContent(
        text: String,
        language: CodeLanguage,
        syntaxHighlight: Boolean,
        lightweight: Boolean,
    ): CodeBrowserRenderedContent {
        val lines = text.split('\n')
        val renderedLines =
            lines.map { line ->
                CodeBrowserRenderedLine(
                    text = buildAnnotatedDiffLine(line, language, syntaxHighlight),
                    kind = classifyDiffLine(line),
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
            lightweight = lightweight,
        )
    }

    private fun buildAnnotatedDiffLine(
        line: String,
        language: CodeLanguage,
        syntaxHighlight: Boolean,
    ): AnnotatedString {
        val metaStyle = SpanStyle(color = c(0xFF94A3B8))
        val hunkStyle = SpanStyle(color = c(0xFF7DD3FC), fontWeight = FontWeight.Bold)
        val addPrefixStyle = SpanStyle(color = c(0xFF86EFAC), fontWeight = FontWeight.Bold)
        val deletePrefixStyle = SpanStyle(color = c(0xFFFCA5A5), fontWeight = FontWeight.Bold)
        val contextPrefixStyle = SpanStyle(color = uiMuted)

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

    private fun buildFileAnnotatedText(
        text: String,
        language: CodeLanguage,
        syntaxHighlight: Boolean,
    ): AnnotatedString {
        val lines = text.split('\n')
        val lineNumberWidth = maxOf(2, lines.size.toString().length)
        val lineNumberStyle = SpanStyle(color = uiMuted)

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

    private fun renderCodeBrowserLine(
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

    private fun buildCodeBrowserFileCacheKey(
        sessionId: String,
        path: String,
    ): String {
        return "$sessionId::$path"
    }

    private fun findCachedCodeBrowserFileContent(
        sessionId: String,
        candidatePaths: List<String>,
    ): CodeBrowserFileContent? {
        return candidatePaths.firstNotNullOfOrNull { path ->
            codeBrowserFileCache[buildCodeBrowserFileCacheKey(sessionId, path)]
        }
    }

    private fun rememberCodeBrowserFileContent(
        sessionId: String,
        content: CodeBrowserFileContent,
    ) {
        codeBrowserFileCache[buildCodeBrowserFileCacheKey(sessionId, content.requestedPath)] = content
        codeBrowserFileCache[buildCodeBrowserFileCacheKey(sessionId, content.resolvedPath)] = content
    }

    private fun buildSyntaxHighlightedLine(line: String, language: CodeLanguage): AnnotatedString {
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

    private fun buildMarkdownAnnotatedLine(line: String): AnnotatedString {
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

    private fun buildXmlAnnotatedLine(line: String): AnnotatedString {
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

    private fun buildInlineCodeSegments(text: String, palette: CodeSyntaxPalette): AnnotatedString {
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

    private fun AnnotatedString.Builder.appendHighlightedInlineCodeSegment(
        text: String,
        palette: CodeSyntaxPalette,
    ) {
        val baseStyle = inlineCodeSpanStyle(color = uiText)
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

    private fun inlineCodeSpanStyle(
        color: Color,
        fontWeight: FontWeight? = null,
    ): SpanStyle = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color, fontWeight = fontWeight)

    private fun inlineCodeKeywords(): Set<String> =
        setOf(
            "if", "else", "for", "while", "do", "switch", "case", "when", "try", "catch", "finally",
            "throw", "throws", "return", "break", "continue", "class", "interface", "enum", "object",
            "fun", "function", "def", "lambda", "async", "await", "import", "from", "export", "package",
            "public", "private", "protected", "internal", "static", "final", "abstract", "override",
            "const", "let", "var", "val", "new", "this", "super",
        )

    private fun inlineCodeLiterals(): Set<String> =
        setOf(
            "true", "false", "null", "undefined", "nil", "none", "None", "self",
        )

    private fun detectCodeLanguage(pathHint: String?): CodeLanguage {
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

    private fun classifyDiffLine(line: String): DiffLineKind {
        return when {
            line.startsWith("@@") -> DiffLineKind.Hunk
            line.startsWith("diff --git") || line.startsWith("index ") || line.startsWith("--- ") || line.startsWith("+++ ") -> DiffLineKind.Meta
            line.startsWith("+") && !line.startsWith("+++") -> DiffLineKind.Added
            line.startsWith("-") && !line.startsWith("---") -> DiffLineKind.Deleted
            else -> DiffLineKind.Context
        }
    }

    private fun syntaxPalette(): CodeSyntaxPalette {
        return CodeSyntaxPalette(
            keyword = SpanStyle(color = c(0xFF7DD3FC), fontWeight = FontWeight.SemiBold),
            literal = SpanStyle(color = uiPrimary, fontWeight = FontWeight.SemiBold),
            string = SpanStyle(color = c(0xFFF9A66C)),
            number = SpanStyle(color = c(0xFFC084FC)),
            comment = SpanStyle(color = uiMuted),
            type = SpanStyle(color = c(0xFFD97706), fontWeight = FontWeight.SemiBold),
            annotation = SpanStyle(color = c(0xFFF472B6)),
        )
    }

    private fun isStringStart(ch: Char, spec: CodeLanguageSpec): Boolean {
        return ch == '"' || ch == '\'' || (spec.supportsBackticks && ch == '`')
    }

    private fun readStringEnd(line: String, start: Int, delimiter: Char): Int {
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

    private fun readNumberEnd(line: String, start: Int): Int {
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

    private fun readIdentifierEnd(line: String, start: Int): Int {
        var index = start
        while (index < line.length && isIdentifierPart(line[index])) {
            index += 1
        }
        return index
    }

    private fun isIdentifierStart(ch: Char): Boolean = ch == '_' || ch == '$' || ch.isLetter()

    private fun isIdentifierPart(ch: Char): Boolean = isIdentifierStart(ch) || ch.isDigit()

    private fun c(argb: Int): Color = Color(argb)

    private fun c(argb: Long): Color = Color(argb.toInt())

    private fun shortId(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return if (value.length <= 8) value else value.take(8)
    }

    private fun firstNonEmpty(vararg values: String): String {
        values.forEach { if (it.trim().isNotEmpty()) return it }
        return ""
    }

    private fun normalizeNullablePath(value: Any?): String? {
        val text =
            when (value) {
                null, JSONObject.NULL -> return null
                is String -> value.trim()
                else -> value.toString().trim()
            }
        return when {
            text.isBlank() -> null
            text.equals("null", ignoreCase = true) -> null
            text.equals("undefined", ignoreCase = true) -> null
            else -> text
        }
    }

    private fun formatJson(objectValue: JSONObject?): String = objectValue?.toString(2) ?: "{}"

    private fun formatJson(arrayValue: JSONArray?): String = arrayValue?.toString(2) ?: "[]"

    private fun safeJson(objectValue: JSONObject?): String = formatJson(objectValue)

    private fun describeThrowable(error: Throwable?): String {
        val message = error?.message?.takeIf { it.isNotBlank() } ?: return ""
        return ": $message"
    }

}

private enum class AppTab {
    Connection,
    Sessions,
    Chat,
}

sealed interface ConversationItem {
    val id: String

    data class Bubble(
        override val id: String,
        val right: Boolean,
        val text: String,
        val backgroundColor: Int,
        val textColor: Int,
        val turnKey: String? = null,
        val assistantKey: String? = null,
    ) : ConversationItem

    data class SystemNote(
        override val id: String,
        val text: String,
        val itemKey: String? = null,
    ) : ConversationItem

    data class FileChange(
        override val id: String,
        val title: String,
        val summary: String,
        val status: String,
        val diffEntries: List<ConversationDiffEntry>,
        val fallbackDiff: String? = null,
        val sourceItemId: String? = null,
        val turnId: String? = null,
    ) : ConversationItem
}

data class ConversationDiffEntry(
    val path: String,
    val kind: String,
    val diff: String,
    val movePath: String? = null,
) {
    fun summaryPath(basePath: String? = null): String = displayPath(basePath = basePath, maxLength = Int.MAX_VALUE)

    fun filenameLabel(): String {
        val baseName = normalizedPath().substringAfterLast('/').ifBlank { normalizedPath() }
        val moveName = normalizedMovePath()?.substringAfterLast('/')?.ifBlank { normalizedMovePath().orEmpty() }
        return moveName?.let { "$baseName → $it" } ?: baseName
    }

    fun browseCandidates(): List<String> {
        return listOfNotNull(
            normalizedPath().takeIf { it.isNotBlank() },
            normalizedMovePath(),
        ).distinct()
    }

    fun browsePath(): String = browseCandidates().firstOrNull().orEmpty()

    fun displayPath(basePath: String? = null, maxLength: Int = 44): String {
        val baseLabel = compactDiffDisplayPath(normalizedPath(), basePath, maxLength)
        val moveLabel = normalizedMovePath()?.let { compactDiffDisplayPath(it, basePath, maxLength) }
        val combined = moveLabel?.let { "$baseLabel → $it" } ?: baseLabel
        if (combined.length <= maxLength) return combined
        val compactBase = baseLabel.substringAfterLast('/').ifBlank { baseLabel }
        val compactMove = moveLabel?.substringAfterLast('/')?.ifBlank { moveLabel }
        return compactMove?.let { "$compactBase → $it" } ?: compactBase
    }

    fun diffStatsLabel(): String? = buildDiffStatsLabel(diff, kind)

    private fun normalizedPath(): String = sanitizeDiffPath(path).orEmpty()

    private fun normalizedMovePath(): String? = sanitizeDiffPath(movePath)
}

private fun sanitizeDiffPath(value: String?): String? {
    val text = value?.trim().orEmpty()
    return when {
        text.isBlank() -> null
        text.equals("null", ignoreCase = true) -> null
        text.equals("undefined", ignoreCase = true) -> null
        else -> text
    }
}

private fun compactDiffDisplayPath(
    rawPath: String,
    basePath: String?,
    maxLength: Int,
): String {
    val normalizedPath = rawPath.replace('\\', '/').removePrefix("./")
    val normalizedBase = sanitizeDiffPath(basePath)?.replace('\\', '/')?.trimEnd('/')
    val relativePath =
        when {
            normalizedBase.isNullOrBlank() -> normalizedPath.takeIf { !it.startsWith('/') } ?: normalizedPath.substringAfterLast('/')
            normalizedPath == normalizedBase -> normalizedPath.substringAfterLast('/')
            normalizedPath.startsWith("$normalizedBase/") -> normalizedPath.removePrefix("$normalizedBase/")
            normalizedPath.startsWith('/') -> normalizedPath.substringAfterLast('/')
            else -> normalizedPath
        }.ifBlank { normalizedPath.substringAfterLast('/') }
    return if (relativePath.length <= maxLength) relativePath else relativePath.substringAfterLast('/').ifBlank { relativePath }
}

private fun buildDiffStatsLabel(
    diffText: String,
    kind: String,
): String? {
    val stats = parseDiffStats(diffText, kind)
    return stats?.toLabel() ?: when (kind.trim()) {
        "add" -> "新增"
        "delete" -> "删除"
        "update" -> "修改"
        else -> null
    }
}

private data class DiffStats(
    val additions: Int,
    val deletions: Int,
) {
    fun toLabel(): String {
        return when {
            additions > 0 && deletions > 0 -> "+$additions / -$deletions"
            additions > 0 -> "+$additions"
            deletions > 0 -> "-$deletions"
            else -> "修改"
        }
    }
}

private fun parseDiffStats(
    diffText: String,
    kind: String? = null,
): DiffStats? {
    if (diffText.isBlank()) return null
    var additions = 0
    var deletions = 0
    diffText.replace("\r\n", "\n").lineSequence().forEach { line ->
        when {
            line.startsWith("+") && !line.startsWith("+++") -> additions += 1
            line.startsWith("-") && !line.startsWith("---") -> deletions += 1
        }
    }
    if (additions > 0 || deletions > 0) {
        return DiffStats(additions = additions, deletions = deletions)
    }
    val nonEmptyLines = diffText.lineSequence().count { it.isNotBlank() }
    return when (kind?.trim()) {
        "add" -> DiffStats(additions = nonEmptyLines.coerceAtLeast(1), deletions = 0)
        "delete" -> DiffStats(additions = 0, deletions = nonEmptyLines.coerceAtLeast(1))
        else -> null
    }
}

private data class ApprovalPresentation(
    val title: String,
    val detail: String,
    val diffEntries: List<ConversationDiffEntry>,
)

private data class ApprovalAction(
    val label: String,
    val responsePayload: JSONObject,
    val isPrimary: Boolean = false,
)

private data class ApprovalDialogState(
    val requestId: String,
    val title: String,
    val detail: String,
    val actions: List<ApprovalAction>,
    val diffEntries: List<ConversationDiffEntry>,
    val turnId: String? = null,
)

private data class ConnectionRenameDialogState(
    val url: String,
    val currentName: String,
)

private data class BridgeHistoryEntry(
    val url: String,
    val title: String,
    val pathLabel: String,
    val maskedToken: String?,
    val maskedUrl: String,
    val lastUsedAt: Long,
    val name: String? = null,
) {
    fun displayName(): String = name?.trim()?.takeIf { it.isNotBlank() } ?: title

    fun subtitleLine(): String {
        val parts = mutableListOf<String>()
        if (displayName() != title) parts.add(title)
        parts.add(pathLabel)
        return parts.distinct().joinToString(" · ")
    }

    fun withName(value: String?): BridgeHistoryEntry = copy(name = value?.trim()?.takeIf { it.isNotBlank() })

    fun lastUsedLabel(): String {
        return formatBeijingDateTimeLabel(lastUsedAt, fallback = "刚刚", pattern = "yyyy-MM-dd HH:mm")
    }

    companion object {
        fun fromUrl(
            url: String,
            lastUsedAt: Long = System.currentTimeMillis(),
            name: String? = null,
        ): BridgeHistoryEntry? {
            return try {
                val uri = URI.create(url)
                val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: "ws"
                val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
                val port = if (uri.port >= 0) uri.port else 8787
                val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
                val token = (uri.rawQuery ?: "")
                    .split('&')
                    .mapNotNull { segment ->
                        val parts = segment.split('=', limit = 2)
                        if (parts.size == 2 && parts[0] == "token") parts[1] else null
                    }
                    .firstOrNull()
                val maskedToken = token?.let(::maskToken)
                val title = buildString {
                    append(host)
                    append(':')
                    append(port)
                    if (path != "/") {
                        append(path)
                    }
                }
                val maskedUrl = if (token.isNullOrBlank()) {
                    url
                } else {
                    url.replace(token, maskedToken ?: "****")
                }
                BridgeHistoryEntry(
                    url = url,
                    title = title,
                    pathLabel = if (path == "/") scheme else "$scheme $path",
                    maskedToken = maskedToken,
                    maskedUrl = maskedUrl,
                    lastUsedAt = lastUsedAt,
                    name = name?.trim()?.takeIf { it.isNotBlank() },
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun maskToken(token: String): String {
            if (token.isBlank()) return "****"
            if (token.length <= 6) return "****"
            return "${token.take(3)}...${token.takeLast(2)}"
        }
    }
}

private data class CodeBrowserState(
    val conversationItemId: String,
    val sessionId: String?,
    val title: String,
    val basePath: String?,
    val diffEntries: List<ConversationDiffEntry>,
    val fallbackDiff: String?,
    val selectedPath: String?,
    val mode: CodeBrowserMode = CodeBrowserMode.Diff,
    val fileReadState: CodeBrowserFileReadState = CodeBrowserFileReadState.Idle,
) {
    fun selectedEntry(): ConversationDiffEntry? {
        if (diffEntries.isEmpty()) return null
        return diffEntries.firstOrNull { it.browsePath() == selectedPath } ?: diffEntries.first()
    }
}

private enum class CodeBrowserMode {
    Diff,
    File,
}

private enum class CodeTextMode {
    Diff,
    File,
}

private enum class CodeLanguage {
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

private enum class DiffLineKind {
    Meta,
    Hunk,
    Added,
    Deleted,
    Context,
}

private data class CodeSyntaxPalette(
    val keyword: SpanStyle,
    val literal: SpanStyle,
    val string: SpanStyle,
    val number: SpanStyle,
    val comment: SpanStyle,
    val type: SpanStyle,
    val annotation: SpanStyle,
)

private data class CodeLanguageSpec(
    val lineCommentPrefixes: List<String>,
    val blockCommentStart: String? = null,
    val blockCommentEnd: String? = null,
    val supportsBackticks: Boolean = false,
    val supportsTripleQuotes: Boolean = false,
    val supportsAnnotationPrefix: Boolean = false,
    val keywords: Set<String> = emptySet(),
    val literals: Set<String> = emptySet(),
)

private fun CodeLanguage.syntaxSpec(): CodeLanguageSpec {
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

private sealed interface CodeBrowserFileReadState {
    data object Idle : CodeBrowserFileReadState

    data object Loading : CodeBrowserFileReadState

    data class Loaded(
        val content: CodeBrowserFileContent,
    ) : CodeBrowserFileReadState

    data class Error(
        val message: String,
    ) : CodeBrowserFileReadState
}

private data class CodeBrowserFileContent(
    val requestedPath: String,
    val resolvedPath: String,
    val content: String,
    val truncated: Boolean,
    val bytes: Int,
)

private data class CodeBrowserRenderedContent(
    val text: AnnotatedString,
    val lines: List<CodeBrowserRenderedLine> = emptyList(),
    val lightweight: Boolean,
)

private data class CodeBrowserRenderedLine(
    val text: AnnotatedString,
    val kind: DiffLineKind?,
)

private data class ProjectGroup(
    val path: String?,
    val sessions: List<SessionInfo>,
) {
    val displayName: String
        get() = path?.let { it.substringAfterLast('/').ifBlank { it } } ?: "未分类历史"

    fun key(): String = path ?: "__uncategorized__"
}

private data class NewChatDraft(
    val projectPath: String?,
    val model: String,
    val approvalPolicy: String,
    val sandbox: String,
)

private data class SessionInfoSheetState(
    val title: String,
    val rows: List<Pair<String, String>>,
)

private data class ModelInfo(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String,
    val hidden: Boolean,
    val isDefault: Boolean,
) {
    companion object {
        fun fromJson(objectValue: JSONObject): ModelInfo? {
            val id = objectValue.optString("id", "").trim()
            if (id.isBlank()) return null
            return ModelInfo(
                id = id,
                model = objectValue.optString("model", "").trim().ifBlank { id },
                displayName = objectValue.optString("displayName", "").trim().ifBlank { id },
                description = objectValue.optString("description", "").trim(),
                hidden = objectValue.optBoolean("hidden", false),
                isDefault = objectValue.optBoolean("isDefault", false),
            )
        }
    }
}

private data class SessionInfo(
    val sessionId: String,
    val title: String,
    val preview: String,
    val model: String,
    val backend: String,
    val cwd: String,
    val updatedAt: String,
    val approvalPolicy: String,
    val sandbox: Any?,
    val permissions: JSONObject?,
    val contextWindow: Int?,
    val lastTokenUsage: TokenUsageSnapshot?,
    val totalTokenUsage: TokenUsageSnapshot?,
) {
    companion object {
        private fun firstNonEmpty(vararg values: String): String {
            values.forEach { if (it.trim().isNotEmpty()) return it }
            return ""
        }

        private fun copyJsonObject(value: JSONObject?): JSONObject? = value?.let { JSONObject(it.toString()) }

        private fun normalizeTimestamp(raw: Long): String {
            if (raw <= 0L) return ""
            return if (raw < 1_000_000_000_000L) {
                (raw * 1000L).toString()
            } else {
                raw.toString()
            }
        }

        private fun extractUpdatedAt(envelope: JSONObject, threadObject: JSONObject): String {
            return firstNonEmpty(
                envelope.optString("updatedAt", ""),
                threadObject.optString("updatedAt", ""),
                normalizeTimestamp(envelope.optLong("updatedAt", 0L)),
                normalizeTimestamp(threadObject.optLong("updatedAt", 0L)),
            )
        }

        private fun extractPermissions(envelope: JSONObject, threadObject: JSONObject): JSONObject? {
            return copyJsonObject(
                envelope.optJSONObject("permissions")
                    ?: envelope.optJSONObject("activePermissionProfile")
                    ?: envelope.optJSONObject("permissionProfile")
                    ?: threadObject.optJSONObject("permissions")
                    ?: threadObject.optJSONObject("activePermissionProfile")
                    ?: threadObject.optJSONObject("permissionProfile"),
            )
        }

        private fun extractSandbox(envelope: JSONObject, threadObject: JSONObject): Any? {
            return cloneJsonValue(
                envelope.opt("sandbox")
                    ?: envelope.opt("sandboxPolicy")
                    ?: threadObject.opt("sandbox")
                    ?: threadObject.opt("sandboxPolicy"),
            )
        }

        private fun extractContextWindow(envelope: JSONObject, threadObject: JSONObject): Int? {
            return envelope.optInt("contextWindow").takeIf { it > 0 }
                ?: threadObject.optInt("contextWindow").takeIf { it > 0 }
                ?: envelope.optJSONObject("tokenUsage")?.optInt("modelContextWindow")?.takeIf { it > 0 }
                ?: threadObject.optJSONObject("tokenUsage")?.optInt("modelContextWindow")?.takeIf { it > 0 }
        }

        private fun extractTokenUsageSnapshot(
            envelope: JSONObject,
            threadObject: JSONObject,
            directKey: String,
            nestedKey: String,
        ): TokenUsageSnapshot? {
            return envelope.optJSONObject(directKey)?.let { TokenUsageSnapshot.fromJson(it) }
                ?: threadObject.optJSONObject(directKey)?.let { TokenUsageSnapshot.fromJson(it) }
                ?: envelope.optJSONObject("tokenUsage")?.optJSONObject(nestedKey)?.let { TokenUsageSnapshot.fromJson(it) }
                ?: threadObject.optJSONObject("tokenUsage")?.optJSONObject(nestedKey)?.let { TokenUsageSnapshot.fromJson(it) }
        }

        fun fromSession(objectValue: JSONObject): SessionInfo? {
            val threadObject = objectValue.optJSONObject("thread") ?: objectValue
            val sessionId = firstNonEmpty(
                objectValue.optString("session_id", ""),
                objectValue.optString("thread_id", ""),
                objectValue.optString("sessionId", ""),
                threadObject.optString("sessionId", ""),
                objectValue.optString("id", ""),
                threadObject.optString("id", ""),
            )
            if (sessionId.isBlank()) return null
            val title = firstNonEmpty(
                objectValue.optString("title", ""),
                objectValue.optString("name", ""),
                objectValue.optString("preview", ""),
                threadObject.optString("name", ""),
                threadObject.optString("title", ""),
                threadObject.optString("preview", ""),
            )
            return SessionInfo(
                sessionId = sessionId,
                title = title,
                preview = firstNonEmpty(objectValue.optString("preview", ""), threadObject.optString("preview", "")),
                model = firstNonEmpty(objectValue.optString("model", ""), threadObject.optString("model", "")),
                backend = firstNonEmpty(
                    objectValue.optString("backend", ""),
                    objectValue.optString("modelProvider", ""),
                    threadObject.optString("modelProvider", ""),
                ),
                cwd = firstNonEmpty(
                    objectValue.optString("cwd", ""),
                    threadObject.optString("cwd", ""),
                    threadObject.optString("path", ""),
                ),
                updatedAt = extractUpdatedAt(objectValue, threadObject),
                approvalPolicy = firstNonEmpty(
                    objectValue.optString("approvalPolicy", ""),
                    threadObject.optString("approvalPolicy", ""),
                ),
                sandbox = extractSandbox(objectValue, threadObject),
                permissions = extractPermissions(objectValue, threadObject),
                contextWindow = extractContextWindow(objectValue, threadObject),
                lastTokenUsage = extractTokenUsageSnapshot(objectValue, threadObject, "lastTokenUsage", "last"),
                totalTokenUsage = extractTokenUsageSnapshot(objectValue, threadObject, "totalTokenUsage", "total"),
            )
        }

        fun fromThread(objectValue: JSONObject): SessionInfo? {
            val threadObject = objectValue.optJSONObject("thread") ?: objectValue
            val sessionId = firstNonEmpty(
                objectValue.optString("session_id", ""),
                objectValue.optString("thread_id", ""),
                objectValue.optString("sessionId", ""),
                threadObject.optString("sessionId", ""),
                objectValue.optString("id", ""),
                threadObject.optString("id", ""),
            )
            if (sessionId.isBlank()) return null
            val title = firstNonEmpty(
                objectValue.optString("title", ""),
                threadObject.optString("name", ""),
                objectValue.optString("name", ""),
                objectValue.optString("preview", ""),
                threadObject.optString("preview", ""),
                threadObject.optString("title", ""),
            )
            return SessionInfo(
                sessionId = sessionId,
                title = title,
                preview = firstNonEmpty(objectValue.optString("preview", ""), threadObject.optString("preview", "")),
                model = firstNonEmpty(objectValue.optString("model", ""), threadObject.optString("model", "")),
                backend = firstNonEmpty(objectValue.optString("backend", ""), objectValue.optString("modelProvider", ""), threadObject.optString("modelProvider", "")),
                cwd = firstNonEmpty(
                    objectValue.optString("cwd", ""),
                    threadObject.optString("cwd", ""),
                    threadObject.optString("path", ""),
                ),
                updatedAt = extractUpdatedAt(objectValue, threadObject),
                approvalPolicy = firstNonEmpty(
                    objectValue.optString("approvalPolicy", ""),
                    threadObject.optString("approvalPolicy", ""),
                ),
                sandbox = extractSandbox(objectValue, threadObject),
                permissions = extractPermissions(objectValue, threadObject),
                contextWindow = extractContextWindow(objectValue, threadObject),
                lastTokenUsage = extractTokenUsageSnapshot(objectValue, threadObject, "lastTokenUsage", "last"),
                totalTokenUsage = extractTokenUsageSnapshot(objectValue, threadObject, "totalTokenUsage", "total"),
            )
        }
    }

    fun mergedWith(previous: SessionInfo?): SessionInfo {
        if (previous == null) return this
        return copy(
            title = title.ifBlank { previous.title },
            preview = preview.ifBlank { previous.preview },
            model = model.ifBlank { previous.model },
            backend = backend.ifBlank { previous.backend },
            cwd = cwd.ifBlank { previous.cwd },
            updatedAt = updatedAt.ifBlank { previous.updatedAt },
            approvalPolicy = approvalPolicy.ifBlank { previous.approvalPolicy },
            sandbox = sandbox ?: cloneJsonValue(previous.sandbox),
            permissions = permissions?.let { JSONObject(it.toString()) } ?: previous.permissions?.let { JSONObject(it.toString()) },
            contextWindow = contextWindow ?: previous.contextWindow,
            lastTokenUsage = lastTokenUsage ?: previous.lastTokenUsage,
            totalTokenUsage = totalTokenUsage ?: previous.totalTokenUsage,
        )
    }

    fun titleLine(): String {
        if (title.isNotBlank()) return title
        if (preview.isNotBlank()) return preview
        return sessionId.take(8)
    }

    fun previewLine(): String {
        if (preview.isNotBlank() && preview != title) return preview
        if (cwd.isNotBlank()) return cwd
        return "没有预览内容"
    }

    fun updatedAtLabel(): String {
        val raw = updatedAt.trim()
        val timestamp =
            raw.toLongOrNull()
                ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                ?: return raw.ifBlank { "刚刚" }
        return formatBeijingDateTimeLabel(timestamp, fallback = "刚刚")
    }

    fun metaLine(): String {
        val parts = mutableListOf<String>()
        if (model.isNotBlank()) parts.add(model)
        if (backend.isNotBlank()) parts.add(backend)
        if (approvalPolicy.isNotBlank()) parts.add("审批 ${approvalPolicy.trim()}")
        parts.add(sessionId.take(8))
        if (updatedAt.isNotBlank()) parts.add(updatedAtLabel())
        return parts.joinToString(" · ")
    }

    fun permissionsSummary(): String {
        val id = firstNonEmpty(
            permissions?.optString("id", "") ?: "",
            permissions?.optString("type", "") ?: "",
        ).trim()
        return id.ifBlank { "默认" }
    }

    fun sandboxSummary(): String {
        val raw = sandbox
        val type =
            when (raw) {
                is String -> raw
                is JSONObject -> raw.optString("type", "").ifBlank { raw.toString() }
                else -> raw?.toString().orEmpty()
            }.trim()
        return when (type) {
            "danger-full-access", "dangerFullAccess" -> "full-access"
            "workspace-write", "workspaceWrite" -> "workspace-write"
            "read-only", "readOnly" -> "read-only"
            "externalSandbox" -> "external sandbox"
            else -> raw?.toString().orEmpty().ifBlank { "未提供" }
        }
    }

    fun contextWindowSummary(): String = contextWindow?.let(::formatCountLabel) ?: "未提供"

    fun lastTokenUsageSummary(): String = lastTokenUsage?.summary() ?: "未提供"

    fun totalTokenUsageSummary(): String = totalTokenUsage?.summary() ?: "未提供"
}

private data class TokenUsageSnapshot(
    val totalTokens: Int,
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int,
    val reasoningOutputTokens: Int,
) {
    companion object {
        fun fromJson(objectValue: JSONObject): TokenUsageSnapshot {
            return TokenUsageSnapshot(
                totalTokens = objectValue.optInt("totalTokens", 0),
                inputTokens = objectValue.optInt("inputTokens", 0),
                cachedInputTokens = objectValue.optInt("cachedInputTokens", 0),
                outputTokens = objectValue.optInt("outputTokens", 0),
                reasoningOutputTokens = objectValue.optInt("reasoningOutputTokens", 0),
            )
        }
    }

    fun summary(): String {
        return buildList {
            add("总 ${formatCountLabel(totalTokens)}")
            if (inputTokens > 0) add("入 ${formatCountLabel(inputTokens)}")
            if (cachedInputTokens > 0) add("缓存 ${formatCountLabel(cachedInputTokens)}")
            if (outputTokens > 0) add("出 ${formatCountLabel(outputTokens)}")
            if (reasoningOutputTokens > 0) add("推理 ${formatCountLabel(reasoningOutputTokens)}")
        }.joinToString(" · ").ifBlank { "0" }
    }
}

private fun formatCountLabel(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000f)
        else -> value.toString()
    }
}

private fun interface ResponseHandler {
    fun onResponse(response: JSONObject)

    fun onError(errorText: String) {}
}

private fun ClipData.firstText(): String? {
    for (i in 0 until itemCount) {
        val text = getItemAt(i).text?.toString()?.trim().orEmpty()
        if (text.isNotBlank()) {
            val line = text.lineSequence().firstOrNull { it.startsWith("ws://") || it.startsWith("wss://") }
            return line ?: text.lineSequence().firstOrNull()?.trim()
        }
    }
    return null
}

private fun cloneJsonValue(value: Any?): Any? {
    return when (value) {
        null -> null
        is JSONObject -> JSONObject(value.toString())
        is JSONArray -> JSONArray(value.toString())
        else -> value
    }
}

private fun encodeToken(token: String): String = URLEncoder.encode(token, "UTF-8")

private fun formatBeijingDateTimeLabel(
    epochMillis: Long,
    fallback: String = "刚刚",
    pattern: String = "MM-dd HH:mm",
): String {
    if (epochMillis <= 0L) return fallback
    return runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern(pattern))
    }.getOrDefault(fallback)
}
