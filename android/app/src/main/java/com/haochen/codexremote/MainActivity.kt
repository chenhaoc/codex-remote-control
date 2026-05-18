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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
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
        private const val MAX_CONNECTION_HISTORY = 8
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingRequests = mutableMapOf<String, ResponseHandler>()
    private val renderedEventKeys = mutableSetOf<String>()
    private val sessions = mutableStateListOf<SessionInfo>()
    private val availableModels = mutableStateListOf<ModelInfo>()
    private val conversationItems = mutableStateListOf<ConversationItem>()
    private val assistantItemIds = mutableMapOf<String, String>()
    private val approvalItemIds = mutableMapOf<String, String>()
    private val toolItemIds = mutableMapOf<String, String>()
    private val fileChangeItemIds = mutableMapOf<String, String>()
    private val fileChangeTurnIds = mutableMapOf<String, String>()
    private val turnDiffItemIds = mutableMapOf<String, String>()
    private val turnDiffs = mutableMapOf<String, String>()
    private val connectionHistory = mutableStateListOf<BridgeHistoryEntry>()

    private lateinit var prefs: SharedPreferences
    private var bridgeClient: BridgeClient? = null
    private var connected by mutableStateOf(false)
    private var selectedTab by mutableStateOf(AppTab.Connection)
    private var activeSessionId by mutableStateOf<String?>(null)
    private var activeTurnId by mutableStateOf<String?>(null)
    private var bridgeUrl by mutableStateOf("")
    private var selectedModel by mutableStateOf("")
    private var connectionDetail by mutableStateOf("未连接")
    private var currentBridgeUrl by mutableStateOf<String?>(null)
    private var composerText by mutableStateOf("")
    private var liveTurnStatus by mutableStateOf<String?>(null)
    private var chatRestoreScrollY by mutableStateOf<Int?>(null)
    private var codeBrowserState by mutableStateOf<CodeBrowserState?>(null)
    private var transientNotice by mutableStateOf<String?>(null)
    private var transientNonce by mutableStateOf(0)
    private var disconnectRequested = false
    private var bootSyncRequested = false
    private var lastSyncedSeq = 0
    private var syncInFlight = false

    private val syncRunnable = object : Runnable {
        override fun run() {
            val sessionId = activeSessionId
            if (!connected || sessionId.isNullOrBlank()) {
                syncInFlight = false
                return
            }
            requestSessionSync(sessionId, incremental = true)
            mainHandler.postDelayed(this, 1500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        bridgeUrl = loadBridgeUrl()
        replaceConnectionHistory(loadConnectionHistory(bridgeUrl))
        persistConnectionHistory()
        activeSessionId = prefs.getString(KEY_SESSION, null)
        selectedModel = prefs.getString(KEY_MODEL, "")?.trim().orEmpty()

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

    override fun onDestroy() {
        super.onDestroy()
        stopSessionSyncLoop()
        bridgeClient?.let {
            disconnectRequested = true
            it.close()
        }
        bridgeClient = null
    }

    @Composable
    private fun RemoteApp() {
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(transientNonce) {
            val message = transientNotice ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message)
            transientNotice = null
        }

        val scheme = darkColorScheme(
            primary = c(0xFF38BDF8),
            secondary = c(0xFF94A3B8),
            tertiary = c(0xFF7C3AED),
            background = c(0xFF0B1220),
            surface = c(0xFF111A2E),
            surfaceVariant = c(0xFF1D2A3D),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White,
        )

        MaterialTheme(colorScheme = scheme) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { scaffoldPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .background(
                            Brush.verticalGradient(
                                listOf(c(0xFF0B1220), c(0xFF101A2B), c(0xFF0B1220)),
                            ),
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Header()
                        Spacer(modifier = Modifier.height(12.dp))
                        TabSwitcher()
                        Spacer(modifier = Modifier.height(12.dp))
                        when (selectedTab) {
                            AppTab.Connection -> ConnectionPage()
                            AppTab.Sessions -> SessionPage()
                            AppTab.Chat -> ChatPage()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header() {
        val pillColor = if (connected) c(0xFF22C55E) else c(0xFF334155)
        val pillText = if (connected) "在线" else "离线"
        val statusLine = buildStatusLine()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Codex Remote",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusLine,
                    color = c(0xFF98A8C2),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = pillColor,
                contentColor = c(0xFF0B1220),
            ) {
                Text(
                    text = pillText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    @Composable
    private fun TabSwitcher() {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            TabButton(
                text = "连接",
                selected = selectedTab == AppTab.Connection,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = AppTab.Connection },
            )
            TabButton(
                text = "会话",
                selected = selectedTab == AppTab.Sessions,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = AppTab.Sessions },
            )
            TabButton(
                text = "对话",
                selected = selectedTab == AppTab.Chat,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = AppTab.Chat },
            )
        }
    }

    @Composable
    private fun TabButton(
        text: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        val color = if (selected) c(0xFF0F6D99) else c(0xFF1D2A3D)
        Surface(
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(16.dp),
            color = color,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
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
                    colors = CardDefaults.cardColors(containerColor = c(0xFF111A2E)),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        SectionTitle("连接 Linux Bridge")
                        Spacer(modifier = Modifier.height(8.dp))
                        BodyText("直接粘贴 `npm run bridge` 打印出来的完整 ws:// 地址，最好带上端口和 token。")
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = c(0xFF111A2E)),
                    shape = RoundedCornerShape(22.dp),
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
                                focusedBorderColor = c(0xFF38BDF8),
                                unfocusedBorderColor = c(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = c(0xFF38BDF8),
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
                                colors = ButtonDefaults.buttonColors(containerColor = c(0xFF0F6D99)),
                            ) {
                                Text(if (connected) "断开连接" else "连接")
                            }
                        }
                    }
                }

                if (connectionHistory.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = c(0xFF111A2E)),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionTitle("历史连接")
                                OutlinedButton(onClick = { clearConnectionHistory() }) {
                                    Text("清空")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            BodyText("点卡片回填地址，点右侧按钮直接连接或切换。")
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                connectionHistory.forEach { entry ->
                                    HistoryConnectionCard(
                                        entry = entry,
                                        active = currentBridgeUrl == entry.url,
                                        onApply = { applyHistoryConnection(entry) },
                                        onConnect = { connectToHistory(entry) },
                                        onDelete = { removeConnectionHistory(entry.url) },
                                    )
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = c(0xFF0F172A)),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        SectionTitle("连接状态")
                        Spacer(modifier = Modifier.height(8.dp))
                        BodyText(connectionDetail)
                    }
                }
            }
        }
    }

    @Composable
    private fun HistoryConnectionCard(
        entry: BridgeHistoryEntry,
        active: Boolean,
        onApply: () -> Unit,
        onConnect: () -> Unit,
        onDelete: () -> Unit,
    ) {
        val connectLabel = when {
            active && connected -> "当前"
            connected -> "切换"
            else -> "连接"
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onApply),
            shape = RoundedCornerShape(18.dp),
            color = if (active) c(0xFF123B57) else c(0xFF0F172A),
            border = if (active) androidx.compose.foundation.BorderStroke(1.dp, c(0xFF38BDF8)) else null,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.detailLine(),
                            color = c(0xFF98A8C2),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "最近使用 ${entry.lastUsedLabel()}",
                            color = c(0xFF64748B),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    FilledTonalButton(
                        onClick = onConnect,
                        enabled = !(active && connected),
                    ) {
                        Text(connectLabel)
                    }
                }

                Text(
                    text = entry.maskedUrl,
                    color = c(0xFF64748B),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onApply,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("填入")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("移除")
                    }
                }
            }
        }
    }

    @Composable
    private fun ChatPage() {
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = c(0xFF111A2E),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentSessionLabel(),
                        color = c(0xFF98A8C2),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = { selectedTab = AppTab.Sessions }) {
                        Text("切换会话")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = c(0xFF111A2E)),
                shape = RoundedCornerShape(22.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (conversationItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BodyText("选中一个会话后，消息和审批会在这里出现。")
                        }
                    } else {
                        ConversationHistoryWebView(
                            items = conversationItems,
                            sessionId = activeSessionId,
                            followBottom = activeTurnId != null,
                            onApprovalDecision = { requestId, decision, itemId ->
                                sendApproval(requestId, decision, itemId)
                            },
                            restoreScrollY = chatRestoreScrollY,
                            onScrollRestored = {
                                chatRestoreScrollY = null
                            },
                            onOpenCodeBrowser = { itemId, scrollY ->
                                openCodeBrowser(itemId, scrollY)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    codeBrowserState?.let { state ->
                        CodeBrowserPage(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            liveTurnStatus?.takeIf { it.isNotBlank() }?.let { status ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = c(0xFF0F172A),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "\u25cf",
                            color = c(0xFF38BDF8),
                            fontSize = 10.sp,
                        )
                        Text(
                            text = status,
                            color = c(0xFF98A8C2),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = c(0xFF111A2E)),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = composerText,
                        onValueChange = { composerText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (imeVisible) 72.dp else 110.dp),
                        placeholder = { Text("输入给 Codex 的消息") },
                        minLines = if (imeVisible) 1 else 3,
                        maxLines = if (imeVisible) 3 else 6,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendComposerText() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c(0xFF38BDF8),
                            unfocusedBorderColor = c(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = c(0xFF38BDF8),
                        ),
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { sendComposerText() },
                            enabled = connected && activeSessionId != null,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = c(0xFF0F6D99)),
                        ) {
                            Text("发送")
                        }
                        OutlinedButton(
                            onClick = { interruptCurrentTurn() },
                            enabled = connected && activeTurnId != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("中断")
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

        LaunchedEffect(state.conversationItemId, state.mode, state.selectedPath, state.fileReadState) {
            if (state.mode == CodeBrowserMode.File && state.fileReadState is CodeBrowserFileReadState.Idle) {
                loadCodeBrowserFileContent(state)
            }
        }

        Box(
            modifier = modifier
                .background(c(0xFF111A2E))
                .padding(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = c(0xFF0F172A),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { closeCodeBrowser() }) {
                            Text("返回对话")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = when {
                                    selectedEntry != null -> selectedEntry.displayPath()
                                    state.diffEntries.isNotEmpty() -> "共 ${state.diffEntries.size} 个文件"
                                    else -> "聚合 diff"
                                },
                                color = c(0xFF98A8C2),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (state.diffEntries.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.diffEntries.forEach { entry ->
                            CodeBrowserFileChip(
                                label = entry.displayPath(),
                                selected = state.selectedPath == entry.browsePath(),
                                onClick = { selectCodeBrowserPath(entry.browsePath()) },
                            )
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
                    color = c(0xFF0B1220),
                ) {
                    when (state.mode) {
                        CodeBrowserMode.Diff -> {
                            val diffText = selectedEntry?.diff ?: state.fallbackDiff.orEmpty()
                            if (diffText.isBlank()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    BodyText("这个条目暂时没有可展示的 diff。")
                                }
                            } else {
                                CodeBrowserTextPane(
                                    text = diffText,
                                    mode = CodeTextMode.Diff,
                                    pathHint = selectedEntry?.browsePath(),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        CodeBrowserMode.File -> {
                            when (val fileState = state.fileReadState) {
                                CodeBrowserFileReadState.Idle -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        BodyText("正在准备读取文件内容…")
                                    }
                                }

                                CodeBrowserFileReadState.Loading -> {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = c(0xFF38BDF8))
                                    }
                                }

                                is CodeBrowserFileReadState.Error -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(18.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BodyText(fileState.message)
                                    }
                                }

                                is CodeBrowserFileReadState.Loaded -> {
                                    CodeBrowserTextPane(
                                        text = fileState.content.content,
                                        mode = CodeTextMode.File,
                                        pathHint = fileState.content.resolvedPath,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }

                when (val fileState = state.fileReadState) {
                    is CodeBrowserFileReadState.Loaded ->
                        if (state.mode == CodeBrowserMode.File) {
                            Text(
                                text = buildString {
                                    append(fileState.content.resolvedPath)
                                    if (fileState.content.truncated) {
                                        append(" · 已截断到 ${fileState.content.bytes} bytes")
                                    }
                                },
                                color = c(0xFF98A8C2),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                    else -> Unit
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
                colors = ButtonDefaults.buttonColors(containerColor = c(0xFF0F6D99)),
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
    private fun CodeBrowserFileChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (selected) c(0xFF123B57) else c(0xFF0F172A),
            border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, c(0xFF38BDF8)) else null,
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun CodeBrowserTextPane(
        text: String,
        mode: CodeTextMode,
        pathHint: String? = null,
        modifier: Modifier = Modifier,
    ) {
        val verticalScroll = rememberScrollState()
        val horizontalScroll = rememberScrollState()
        val content = remember(text, mode, pathHint) { buildCodeBrowserAnnotatedText(text, mode, pathHint) }

        SelectionContainer {
            Box(
                modifier = modifier
                    .background(c(0xFF0B1220))
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll)
                    .padding(14.dp),
            ) {
                Text(
                    text = content,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }

    @Composable
    private fun SessionPage() {
        val sessionListState = rememberLazyListState()
        var modelPickerExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = c(0xFF111A2E)),
                shape = RoundedCornerShape(22.dp),
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
                            focusedBorderColor = c(0xFF38BDF8),
                            unfocusedBorderColor = c(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = c(0xFF38BDF8),
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
                            onClick = { startNewSession() },
                            enabled = connected,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = c(0xFF0F6D99)),
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
                SectionTitle("会话历史")
                FilledTonalButton(onClick = { requestSessionList() }, enabled = connected) {
                    Text("刷新")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = c(0xFF111A2E)),
                shape = RoundedCornerShape(22.dp),
            ) {
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BodyText(if (connected) "暂无会话，先创建一个新的吧。" else "连接后这里会显示会话列表。")
                    }
                } else {
                    LazyColumn(
                        state = sessionListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        items(items = sessions, key = { it.sessionId }) { session ->
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
            color = if (selected) c(0xFF123B57) else c(0xFF0F172A),
            border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, c(0xFF38BDF8)) else null,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = session.titleLine(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.previewLine(),
                    color = c(0xFF98A8C2),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = session.metaLine(),
                    color = c(0xFF98A8C2),
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
            color = if (selected) c(0xFF123B57) else c(0xFF0F172A),
            border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, c(0xFF38BDF8)) else null,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = model.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.widthIn(min = 8.dp))
                    Text(
                        text = if (model.isDefault) "默认" else model.model,
                        color = c(0xFF98A8C2),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (model.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = model.description,
                        color = c(0xFF98A8C2),
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
        disconnectRequested = true
        bridgeClient?.close()
        bridgeClient = null
        connected = false
        activeTurnId = null
        pendingRequests.clear()
        currentBridgeUrl = null
        if (switchToConnectionPage) {
            selectedTab = AppTab.Connection
        }
        connectionDetail = detail
    }

    private fun connectToBridge(url: String) {
        if (bridgeClient?.isOpen() == true && currentBridgeUrl == url) {
            selectedTab = AppTab.Chat
            connectionDetail = "已连接到 ${BridgeHistoryEntry.fromUrl(url)?.title ?: url}"
            return
        }

        if (bridgeClient != null) {
            disconnectBridge(detail = "正在切换连接…", switchToConnectionPage = false)
        }

        saveBridgeSettings(url)
        bridgeUrl = url
        disconnectRequested = false
        bootSyncRequested = false
        connected = false
        currentBridgeUrl = url
        connectionDetail = "连接中: $url"

        bridgeClient = BridgeClient(URI.create(url), object : BridgeClient.Listener {
            override fun onOpen() {
                mainHandler.post {
                    connected = true
                    rememberConnectionHistory(url)
                    selectedTab = AppTab.Chat
                    connectionDetail = "已连接，等待 bridge hello..."
                }
            }

            override fun onText(text: String) {
                mainHandler.post { handleIncoming(text) }
            }

            override fun onDisconnected(reason: String, error: Throwable?) {
                mainHandler.post {
                    connected = false
                    activeTurnId = null
                    pendingRequests.clear()
                    stopSessionSyncLoop()
                    bridgeClient = null
                    currentBridgeUrl = null
                    selectedTab = AppTab.Connection
                    connectionDetail = reason + describeThrowable(error)
                    disconnectRequested = false
                }
            }
        })

        try {
            bridgeClient?.connect()
        } catch (error: RuntimeException) {
            bridgeClient = null
            currentBridgeUrl = null
            connectionDetail = "连接失败: ${error.message}"
            showNotice(connectionDetail)
        }
    }

    private fun applyHistoryConnection(entry: BridgeHistoryEntry) {
        bridgeUrl = entry.url
        connectionDetail = "已回填 ${entry.title}，点击连接即可"
    }

    private fun connectToHistory(entry: BridgeHistoryEntry) {
        bridgeUrl = entry.url
        connectToBridge(entry.url)
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
            val thread = payload.optJSONObject("thread")
            val info = thread?.let { SessionInfo.fromThread(it) }
            if (info != null) {
                upsertSession(info)
                if (activeSessionId == null) {
                    selectSession(info.sessionId, syncHistory = false)
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
                val text = payload.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    appendBubble(text, right = true, backgroundColor = 0xFF0EA5E9.toInt(), textColor = AndroidColor.WHITE)
                }
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
                handleAssistantDelta(message, payload)
            }
            "rawResponseItem/completed" -> {
                handleRawResponseItem(message, payload)
            }
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/permissions/requestApproval",
            "applyPatchApproval",
            "execCommandApproval" -> handleApprovalRequest(eventName, message, payload)
            "item/fileChange/patchUpdated" -> {
                updateLiveTurnStatus("Codex 正在修改文件…")
                handleFileChangePatchUpdated(message, payload)
            }
            "turn/diff/updated" -> {
                updateLiveTurnStatus("Codex 正在修改文件…")
                handleTurnDiffUpdated(message, payload)
            }
            "item/started", "item/completed" -> {
                updateLiveTurnStatusFromItem(eventName, payload.optJSONObject("item"))
                handleThreadItemEvent(eventName, payload)
            }
            "warning" -> updateLiveTurnStatusFromWarning(payload)
            "error" -> updateLiveTurnStatusFromError(payload)
            "thread/status/changed" -> updateLiveTurnStatusFromThreadStatus(payload.optJSONObject("status"))
            "turn/completed" -> {
                val turn = payload.optJSONObject("turn")
                val status = turn?.optString("status", "completed") ?: payload.optString("status", "completed")
                val errorText = extractErrorText(turn?.optJSONObject("error"))
                if (status == "failed" || status == "interrupted" || errorText.isNotBlank()) {
                    appendSystemNote(
                        buildString {
                            append("当前回合结束: $status")
                            if (errorText.isNotBlank()) {
                                append('\n')
                                append(errorText)
                            }
                        },
                    )
                }
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
        if (requestId.isBlank() || approvalItemIds.containsKey(requestId)) return

        val decisions = payload.optJSONArray("availableDecisions").toDecisionList()
        val presentation = buildApprovalPresentation(eventName, payload)
        val item = ConversationItem.Approval(
            id = "approval_${UUID.randomUUID()}",
            requestId = requestId,
            title = presentation.title,
            detail = presentation.detail,
            availableDecisions = decisions.ifEmpty { listOf("accept", "decline") },
            diffEntries = presentation.diffEntries,
        )
        approvalItemIds[requestId] = item.id
        conversationItems.add(item)
    }

    private fun sendApproval(requestId: String, decision: String, itemId: String) {
        val payload = JSONObject().apply {
            put("session_id", activeSessionId ?: "")
            put("request_id", requestId)
            put("decision", decision)
        }

        if (!sendRequest("approval.response", payload, object : ResponseHandler {
            override fun onResponse(response: JSONObject) {
                appendSystemNote("审批结果已提交")
                approvalItemIds.remove(requestId)
                removeConversationItem(itemId)
            }
        })) {
            appendSystemNote("审批发送失败")
        } else {
            appendSystemNote("已发送审批: $decision")
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

    private fun startNewSession() {
        if (!ensureConnected()) return
        val model = resolveModelForSend()
        if (model.isBlank()) {
            showNotice("请先选择一个模型")
            return
        }

        val payload = JSONObject().apply {
            put("title", "Android 手机")
            put("sessionStartSource", "startup")
            put("threadSource", "user")
            put("model", model)
        }

        sendRequest("session.start", payload) { response ->
            val info = response.optJSONObject("payload")?.optJSONObject("session")?.let { SessionInfo.fromSession(it) }
            if (info != null) {
                upsertSession(info)
                selectSession(info.sessionId, syncHistory = false)
                selectedTab = AppTab.Chat
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

    private fun requestSessionSync(sessionId: String) {
        requestSessionSync(sessionId, incremental = false)
    }

    private fun requestSessionSync(sessionId: String, incremental: Boolean) {
        if (sessionId.isBlank()) return
        if (!ensureConnected()) return
        if (incremental && syncInFlight) return
        if (!incremental) {
            clearConversation()
        }
        syncInFlight = incremental
        val payload = JSONObject().apply {
            put("session_id", sessionId)
            put("since_seq", if (incremental) lastSyncedSeq else 0)
        }

        sendRequest("session.sync", payload) { response ->
            syncInFlight = false
            val responsePayload = response.optJSONObject("payload")
            lastSyncedSeq = maxOf(lastSyncedSeq, responsePayload?.optInt("last_seq", lastSyncedSeq) ?: lastSyncedSeq)
            val events = responsePayload?.optJSONArray("events")
            if (events == null || events.length() == 0) {
                if (!incremental) {
                    appendSystemNote("这个会话暂时没有本地同步消息")
                }
                return@sendRequest
            }
            for (i in 0 until events.length()) {
                events.optJSONObject(i)?.let { renderHistoricalEvent(it) }
            }
        }
    }

    private fun renderHistoricalEvent(event: JSONObject) {
        when (event.optString("event", "")) {
            "turn/input",
            "turn/started",
            "item/agentMessage/delta",
            "rawResponseItem/completed",
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/permissions/requestApproval",
            "applyPatchApproval",
            "execCommandApproval",
            "item/fileChange/patchUpdated",
            "turn/diff/updated",
            "turn/completed",
            "thread/started",
            "item/started",
            "item/completed",
            "warning",
            "error",
            "thread/status/changed" -> handleEvent(event)
        }
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
            requestSessionSync(sessionId)
        }
        startSessionSyncLoop()
    }

    private fun replaceSessions(newSessions: List<SessionInfo>) {
        sessions.clear()
        sessions.addAll(newSessions.sortedByDescending { it.updatedAt })
    }

    private fun upsertSession(info: SessionInfo) {
        sessions.removeAll { it.sessionId == info.sessionId }
        sessions.add(0, info)
    }

    private fun clearConversation() {
        conversationItems.clear()
        assistantItemIds.clear()
        approvalItemIds.clear()
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
    }

    private fun appendSystemNote(text: String) {
        conversationItems.add(
            ConversationItem.SystemNote(
                id = "sys_${UUID.randomUUID()}",
                text = text,
            ),
        )
    }

    private fun appendBubble(text: String, right: Boolean, backgroundColor: Int, textColor: Int) {
        conversationItems.add(
            ConversationItem.Bubble(
                id = "msg_${UUID.randomUUID()}",
                right = right,
                text = text,
                backgroundColor = backgroundColor,
                textColor = textColor,
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

    private fun openCodeBrowser(itemId: String, scrollY: Int) {
        val sessionId = activeSessionId
        val item = conversationItems.firstOrNull { it.id == itemId }
        val state =
            when (item) {
                is ConversationItem.FileChange ->
                    CodeBrowserState(
                        conversationItemId = item.id,
                        sessionId = sessionId,
                        title = item.title,
                        diffEntries = item.diffEntries,
                        fallbackDiff = item.fallbackDiff,
                        selectedPath = item.diffEntries.firstOrNull()?.browsePath(),
                    )

                is ConversationItem.Approval ->
                    CodeBrowserState(
                        conversationItemId = item.id,
                        sessionId = sessionId,
                        title = item.title,
                        diffEntries = item.diffEntries,
                        fallbackDiff = null,
                        selectedPath = item.diffEntries.firstOrNull()?.browsePath(),
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
                    codeBrowserState = current.copy(
                        fileReadState = CodeBrowserFileReadState.Loaded(
                            CodeBrowserFileContent(
                                requestedPath = requestedPath,
                                resolvedPath = result.optString("resolved_path", requestedPath),
                                content = result.optString("content", ""),
                                truncated = result.optBoolean("truncated", false),
                                bytes = result.optInt("bytes", 0),
                            ),
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
                                append(candidatePaths.joinToString(" | "))
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
        val entry = BridgeHistoryEntry.fromUrl(url, System.currentTimeMillis()) ?: return
        replaceConnectionHistory(
            listOf(entry) + connectionHistory.filterNot { it.url == entry.url },
        )
        persistConnectionHistory()
    }

    private fun removeConnectionHistory(url: String) {
        replaceConnectionHistory(connectionHistory.filterNot { it.url == url })
        persistConnectionHistory()
    }

    private fun clearConnectionHistory() {
        connectionHistory.clear()
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
            connectionDetail = "请先连接 Linux Bridge"
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
            "commandExecution",
            "mcpToolCall",
            "dynamicToolCall",
            "collabAgentToolCall",
            "plan",
            "reasoning",
            "contextCompaction" -> handleToolItem(eventName, item)
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

    private fun handleRawResponseItem(message: JSONObject, payload: JSONObject) {
        val item = payload.optJSONObject("item") ?: return
        if (item.optString("type", "") != "message") return

        val role = item.optString("role", "").trim()
        val text = extractResponseItemText(item).ifBlank { formatJson(item) }
        if (text.isBlank()) return

        when (role) {
            "assistant" -> {
                updateLiveTurnStatus("Codex 正在输出…")
                appendStandaloneAssistantBubble(extractTurnKey(message, payload), text)
            }
            "system" -> appendSystemNote("系统消息:\n\n$text")
            else -> Unit
        }
    }

    private fun extractThreadItemText(item: JSONObject): String {
        return when (item.optString("type", "")) {
            "userMessage" -> {
                val content = item.optJSONArray("content") ?: return ""
                val parts = mutableListOf<String>()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i)
                    if (part?.optString("type", "") == "text") {
                        part.optString("text", "").trim().takeIf { it.isNotBlank() }?.let(parts::add)
                    }
                }
                parts.joinToString("\n")
            }

            "agentMessage", "plan" -> item.optString("text", "").trim()
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

    private fun extractResponseItemText(item: JSONObject): String {
        if (item.optString("type", "") != "message") return ""
        val content = item.optJSONArray("content") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i) ?: continue
            val text = when (part.optString("type", "")) {
                "input_text", "output_text" -> part.optString("text", "")
                else -> part.optString("text", part.optString("content", ""))
            }.trim()
            if (text.isNotBlank()) {
                parts.add(text)
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
        val parts = mutableListOf<String>()
        labelPatchStatus(status).takeIf { it.isNotBlank() }?.let(parts::add)

        if (diffEntries.isNotEmpty()) {
            val paths = diffEntries.map { it.summaryPath() }.distinct()
            parts.add("${paths.size} 个文件")
            val preview = paths.take(2).joinToString("、")
            if (preview.isNotBlank()) {
                parts.add(if (paths.size > 2) "$preview 等" else preview)
            }
        } else if (!fallbackDiff.isNullOrBlank()) {
            parts.add("点击查看 diff")
        }

        return if (parts.isEmpty()) "点击查看详情" else parts.joinToString(" · ")
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
        ).ifBlank { null }
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
            backgroundColor = 0xFF172033.toInt(),
            textColor = AndroidColor.WHITE,
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
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }

    @Composable
    private fun Label(text: String) {
        Text(text = text, fontSize = 12.sp, color = c(0xFF98A8C2))
    }

    @Composable
    private fun BodyText(text: String) {
        Text(text = text, fontSize = 13.sp, color = c(0xFF98A8C2), lineHeight = 20.sp)
    }

    private fun buildCodeBrowserAnnotatedText(
        text: String,
        mode: CodeTextMode,
        pathHint: String? = null,
    ): AnnotatedString {
        val normalized = text.replace("\r\n", "\n")
        val language = detectCodeLanguage(pathHint)
        return when (mode) {
            CodeTextMode.Diff -> buildDiffAnnotatedText(normalized, language)
            CodeTextMode.File -> buildFileAnnotatedText(normalized, language)
        }
    }

    private fun buildDiffAnnotatedText(text: String, language: CodeLanguage): AnnotatedString {
        val lines = text.split('\n')
        val addBackground = SpanStyle(background = c(0x1E16351F))
        val deleteBackground = SpanStyle(background = c(0x1E3F1616))

        return buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                when (classifyDiffLine(line)) {
                    DiffLineKind.Added -> withStyle(addBackground) { append(buildAnnotatedDiffLine(line, language)) }
                    DiffLineKind.Deleted -> withStyle(deleteBackground) { append(buildAnnotatedDiffLine(line, language)) }
                    else -> append(buildAnnotatedDiffLine(line, language))
                }
                if (index < lines.lastIndex) append('\n')
            }
        }
    }

    private fun buildAnnotatedDiffLine(line: String, language: CodeLanguage): AnnotatedString {
        val metaStyle = SpanStyle(color = c(0xFF94A3B8))
        val hunkStyle = SpanStyle(color = c(0xFF7DD3FC), fontWeight = FontWeight.Bold)
        val addPrefixStyle = SpanStyle(color = c(0xFF86EFAC), fontWeight = FontWeight.Bold)
        val deletePrefixStyle = SpanStyle(color = c(0xFFFCA5A5), fontWeight = FontWeight.Bold)
        val contextPrefixStyle = SpanStyle(color = c(0xFF64748B))

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
                    append(buildSyntaxHighlightedLine(line.drop(1), language))
                }

            DiffLineKind.Deleted ->
                buildAnnotatedString {
                    withStyle(deletePrefixStyle) { append("-") }
                    append(buildSyntaxHighlightedLine(line.drop(1), language))
                }

            DiffLineKind.Context ->
                buildAnnotatedString {
                    if (line.startsWith(" ")) {
                        withStyle(contextPrefixStyle) { append(" ") }
                        append(buildSyntaxHighlightedLine(line.drop(1), language))
                    } else {
                        append(buildSyntaxHighlightedLine(line, language))
                    }
                }
        }
    }

    private fun buildFileAnnotatedText(text: String, language: CodeLanguage): AnnotatedString {
        val lines = text.split('\n')
        val lineNumberWidth = maxOf(2, lines.size.toString().length)
        val lineNumberStyle = SpanStyle(color = c(0xFF64748B))

        return buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                withStyle(lineNumberStyle) {
                    append((index + 1).toString().padStart(lineNumberWidth, ' '))
                    append(" | ")
                }
                append(buildSyntaxHighlightedLine(line, language))
                if (index < lines.lastIndex) append('\n')
            }
        }
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
                        withStyle(palette.string) { append(text.substring(index, end + 1)) }
                        index = end + 1
                        continue
                    }
                }
                append(text[index])
                index += 1
            }
        }
    }

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
            literal = SpanStyle(color = c(0xFF38BDF8), fontWeight = FontWeight.SemiBold),
            string = SpanStyle(color = c(0xFFF9A66C)),
            number = SpanStyle(color = c(0xFFC084FC)),
            comment = SpanStyle(color = c(0xFF64748B)),
            type = SpanStyle(color = c(0xFFFDE68A)),
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

    private fun JSONArray?.toDecisionList(): List<String> {
        if (this == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until length()) {
            optString(i).takeIf { it.isNotBlank() }?.let(out::add)
        }
        return out
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

    data class Approval(
        override val id: String,
        val requestId: String,
        val title: String,
        val detail: String,
        val availableDecisions: List<String>,
        val diffEntries: List<ConversationDiffEntry> = emptyList(),
    ) : ConversationItem

    data class FileChange(
        override val id: String,
        val title: String,
        val summary: String,
        val status: String,
        val diffEntries: List<ConversationDiffEntry>,
        val fallbackDiff: String? = null,
    ) : ConversationItem
}

data class ConversationDiffEntry(
    val path: String,
    val kind: String,
    val diff: String,
    val movePath: String? = null,
) {
    fun summaryPath(): String = normalizedMovePath()?.let { "${normalizedPath()} → $it" } ?: normalizedPath()

    fun browseCandidates(): List<String> {
        return listOfNotNull(
            normalizedPath().takeIf { it.isNotBlank() },
            normalizedMovePath(),
        ).distinct()
    }

    fun browsePath(): String = browseCandidates().firstOrNull().orEmpty()

    fun displayPath(): String = normalizedMovePath()?.let { "${normalizedPath()} → $it" } ?: normalizedPath()

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

private data class ApprovalPresentation(
    val title: String,
    val detail: String,
    val diffEntries: List<ConversationDiffEntry>,
)

private data class BridgeHistoryEntry(
    val url: String,
    val title: String,
    val pathLabel: String,
    val maskedToken: String?,
    val maskedUrl: String,
    val lastUsedAt: Long,
) {
    fun detailLine(): String {
        val parts = mutableListOf(pathLabel)
        maskedToken?.takeIf { it.isNotBlank() }?.let { parts.add("token $it") }
        return parts.joinToString(" · ")
    }

    fun lastUsedLabel(): String {
        if (lastUsedAt <= 0L) return "刚刚"
        return android.text.format.DateFormat.format("MM-dd HH:mm", lastUsedAt).toString()
    }

    companion object {
        fun fromUrl(url: String, lastUsedAt: Long = System.currentTimeMillis()): BridgeHistoryEntry? {
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
) {
    companion object {
        private fun firstNonEmpty(vararg values: String): String {
            values.forEach { if (it.trim().isNotEmpty()) return it }
            return ""
        }

        fun fromSession(objectValue: JSONObject): SessionInfo? {
            val sessionId = firstNonEmpty(
                objectValue.optString("session_id", ""),
                objectValue.optString("thread_id", ""),
                objectValue.optString("id", ""),
            )
            if (sessionId.isBlank()) return null
            val title = firstNonEmpty(
                objectValue.optString("title", ""),
                objectValue.optString("name", ""),
                objectValue.optString("preview", ""),
            )
            return SessionInfo(
                sessionId = sessionId,
                title = title,
                preview = objectValue.optString("preview", ""),
                model = objectValue.optString("model", ""),
                backend = objectValue.optString("backend", ""),
                cwd = objectValue.optString("cwd", ""),
                updatedAt = objectValue.optString("updatedAt", ""),
            )
        }

        fun fromThread(objectValue: JSONObject): SessionInfo? {
            val sessionId = firstNonEmpty(
                objectValue.optString("sessionId", ""),
                objectValue.optString("id", ""),
            )
            if (sessionId.isBlank()) return null
            val title = firstNonEmpty(
                objectValue.optString("name", ""),
                objectValue.optString("preview", ""),
                objectValue.optString("title", ""),
            )
            return SessionInfo(
                sessionId = sessionId,
                title = title,
                preview = objectValue.optString("preview", ""),
                model = objectValue.optString("model", ""),
                backend = objectValue.optString("modelProvider", ""),
                cwd = objectValue.optString("cwd", ""),
                updatedAt = objectValue.optLong("updatedAt", 0L).toString(),
            )
        }
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

    fun metaLine(): String {
        val parts = mutableListOf<String>()
        if (model.isNotBlank()) parts.add(model)
        if (backend.isNotBlank()) parts.add(backend)
        parts.add(sessionId.take(8))
        if (updatedAt.isNotBlank()) parts.add(updatedAt)
        return parts.joinToString(" · ")
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

private fun encodeToken(token: String): String = URLEncoder.encode(token, "UTF-8")
