package com.haochen.codexremote

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.LinkedHashMap
import org.json.JSONObject

internal const val PREFS = "codex_remote_settings"
internal const val KEY_URL = "bridge_url"
internal const val KEY_HOST = "host"
internal const val KEY_PORT = "port"
internal const val KEY_TOKEN = "token"
internal const val KEY_CONNECTION_HISTORY = "connection_history"
internal const val KEY_SESSION = "session"
internal const val KEY_MODEL = "model"
internal const val KEY_AUTO_RECONNECT = "auto_reconnect"
internal const val KEY_AUTO_RECONNECT_MAX_ATTEMPTS = "auto_reconnect_max_attempts"
internal const val KEY_STARTUP_PAGE = "startup_page"
internal const val KEY_SESSION_SYNC_INTERVAL_SECONDS = "session_sync_interval_seconds"
internal const val KEY_SESSION_INCREMENTAL_SYNC = "session_incremental_sync"
internal const val MAX_CONNECTION_HISTORY = 8
internal const val CODE_BROWSER_FILE_CACHE_SIZE = 24
internal const val CODE_BROWSER_RENDER_CACHE_SIZE = 16
internal const val DEFAULT_SESSION_SYNC_INTERVAL_SECONDS = 5

class MainActivity : ComponentActivity() {
    internal val uiBackground = Color(0xFFF6FBF7)
    internal val uiSurface = Color(0xFFFFFFFF)
    internal val uiSurfaceAlt = Color(0xFFF1F8F2)
    internal val uiSurfaceSoft = Color(0xFFE5F2E8)
    internal val uiPrimary = Color(0xFF1A8F55)
    internal val uiPrimarySoft = Color(0xFFDDF3E4)
    internal val uiText = Color(0xFF183326)
    internal val uiMuted = Color(0xFF6D8876)
    internal val uiBorder = Color(0xFFCFE2D3)
    internal val uiOnline = Color(0xFF27A55A)
    internal val uiOffline = Color(0xFFB7C7BB)
    internal val uiScrim = Color(0x551A8F55)

    internal val mainHandler = Handler(Looper.getMainLooper())
    internal val pendingRequests = mutableMapOf<String, ResponseHandler>()
    internal val renderedEventKeys = mutableSetOf<String>()
    internal val sessions = mutableStateListOf<SessionInfo>()
    internal val availableModels = mutableStateListOf<ModelInfo>()
    internal val conversationItems = mutableStateListOf<ConversationItem>()
    internal val assistantItemIds = mutableMapOf<String, String>()
    internal val assistantDeltaBuffers = mutableMapOf<String, String>()
    internal val toolItemIds = mutableMapOf<String, String>()
    internal val fileChangeItemIds = mutableMapOf<String, String>()
    internal val fileChangeTurnIds = mutableMapOf<String, String>()
    internal val turnDiffItemIds = mutableMapOf<String, String>()
    internal val turnDiffs = mutableMapOf<String, String>()
    internal val connectionHistory = mutableStateListOf<BridgeHistoryEntry>()
    internal val pendingApprovals = mutableStateListOf<ApprovalDialogState>()
    internal val projectGroupExpanded = mutableStateMapOf<String, Boolean>()
    internal val sessionContentCache = mutableMapOf<String, JSONObject>()
    internal val sessionContentCacheSignatures = mutableMapOf<String, String>()
    internal val codeBrowserFileCache =
        object : LinkedHashMap<String, CodeBrowserFileContent>(CODE_BROWSER_FILE_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CodeBrowserFileContent>?): Boolean {
                return size > CODE_BROWSER_FILE_CACHE_SIZE
            }
        }
    internal val codeBrowserRenderCache =
        object : LinkedHashMap<String, CodeBrowserRenderedContent>(CODE_BROWSER_RENDER_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CodeBrowserRenderedContent>?): Boolean {
                return size > CODE_BROWSER_RENDER_CACHE_SIZE
            }
        }

    internal lateinit var prefs: SharedPreferences
    internal var bridgeClient: BridgeClient? = null
    internal var connected by mutableStateOf(false)
    internal var currentPage by mutableStateOf(AppPage.Chat)
    internal var selectedWorkspace by mutableStateOf<String?>(null)
    internal var activeSessionId by mutableStateOf<String?>(null)
    internal var activeTurnId by mutableStateOf<String?>(null)
    internal var bridgeUrl by mutableStateOf("")
    internal var selectedModel by mutableStateOf("")
    internal var connectionDetail by mutableStateOf("未连接")
    internal var currentBridgeUrl by mutableStateOf<String?>(null)
    internal var activeSessionCacheKey: String? = null
    internal var connectionRenameState by mutableStateOf<ConnectionRenameDialogState?>(null)
    internal var sessionInfoSheetState by mutableStateOf<SessionInfoSheetState?>(null)
    internal var composerText by mutableStateOf("")
    internal var newChatDraft by mutableStateOf<NewChatDraft?>(null)
    internal var liveTurnStatus by mutableStateOf<String?>(null)
    internal var interruptingTurnId by mutableStateOf<String?>(null)
    internal var chatRestoreScrollY by mutableStateOf<Int?>(null)
    internal var codeBrowserState by mutableStateOf<CodeBrowserState?>(null)
    internal var disconnectRequested = false
    internal var bootSyncRequested = false
    internal var lastSyncedSeq = 0
    internal var sessionSyncCursorReady = false
    internal var syncInFlight = false
    internal var sessionContentDirty = false
    internal var lastSnapshotSignature: String? = null
    internal var lastSnapshotItemCount = 0
    internal var autoReconnectEnabled by mutableStateOf(false)
    internal var autoReconnectMaxAttempts by mutableStateOf(0)
    internal var startupPagePreference by mutableStateOf(AppPage.Chat)
    internal var sessionSyncIntervalSeconds by mutableStateOf(DEFAULT_SESSION_SYNC_INTERVAL_SECONDS)
    internal var sessionIncrementalSyncEnabled by mutableStateOf(true)
    internal var reconnectAttempt = 0
    internal var reconnectScheduled = false
    internal var noticeToast: Toast? = null

    internal val syncRunnable = object : Runnable {
        override fun run() {
            val sessionId = activeSessionId
            if (!connected || sessionId.isNullOrBlank()) {
                syncInFlight = false
                return
            }
            requestSessionRefresh(sessionId)
            mainHandler.postDelayed(this, sessionSyncIntervalMs())
        }
    }

    internal val reconnectRunnable = object : Runnable {
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
        autoReconnectMaxAttempts = loadAutoReconnectMaxAttempts()
        startupPagePreference = loadStartupPagePreference()
        sessionSyncIntervalSeconds = loadSessionSyncIntervalSeconds()
        sessionIncrementalSyncEnabled = loadSessionIncrementalSyncEnabled()
        currentPage = startupPagePreference
        loadLocalSessionCache()

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
        noticeToast?.cancel()
        noticeToast = null
        bridgeClient?.let {
            disconnectRequested = true
            it.close()
        }
        bridgeClient = null
    }
}
