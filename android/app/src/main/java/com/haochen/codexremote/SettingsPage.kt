package com.haochen.codexremote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MainActivity.SettingsPage() {
    val scrollState = rememberScrollState()
    var startupPageExpanded by remember { mutableStateOf(false) }
    var reconnectAttemptsExpanded by remember { mutableStateOf(false) }
    var sessionSyncIntervalExpanded by remember { mutableStateOf(false) }
    val startupPageOptions = startupPageMenuOptions()
    val reconnectAttemptOptions = autoReconnectAttemptMenuOptions()
    val sessionSyncIntervalOptions = sessionSyncIntervalMenuOptions()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 6.dp)
            .navigationBarsPadding()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = uiSurface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, uiBorder),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("自动重连")
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = uiSurfaceAlt,
                    border = BorderStroke(1.dp, uiBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Label("自动重连")
                            BodyText("连接意外断开时，按设置自动重试连接。")
                        }
                        Switch(
                            checked = autoReconnectEnabled,
                            onCheckedChange = { updateAutoReconnectEnabled(it) },
                        )
                    }
                }

                SelectionMenuField(
                    label = "最大重连次数",
                    selectedLabel = autoReconnectMaxAttemptsLabel(),
                    expanded = reconnectAttemptsExpanded,
                    onExpandedChange = { reconnectAttemptsExpanded = it },
                    options = reconnectAttemptOptions,
                    enabled = autoReconnectEnabled,
                    onSelect = { value ->
                        updateAutoReconnectMaxAttempts(value.toIntOrNull() ?: 0)
                    },
                )
                BodyText(autoReconnectMaxAttemptsDescription())
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = uiSurface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, uiBorder),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("同步")
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = uiSurfaceAlt,
                    border = BorderStroke(1.dp, uiBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Label("增量同步")
                            BodyText("开启后只同步有变化的回合；关闭后每次轮询拉取完整快照。")
                        }
                        Switch(
                            checked = sessionIncrementalSyncEnabled,
                            onCheckedChange = { updateSessionIncrementalSyncEnabled(it) },
                        )
                    }
                }
                SelectionMenuField(
                    label = "会话同步间隔",
                    selectedLabel = sessionSyncIntervalLabel(),
                    expanded = sessionSyncIntervalExpanded,
                    onExpandedChange = { sessionSyncIntervalExpanded = it },
                    options = sessionSyncIntervalOptions,
                    onSelect = { value ->
                        updateSessionSyncIntervalSeconds(value.toIntOrNull() ?: DEFAULT_SESSION_SYNC_INTERVAL_SECONDS)
                    },
                )
                BodyText(sessionSyncIntervalDescription())
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = uiSurface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, uiBorder),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("启动")
                SelectionMenuField(
                    label = "默认页面",
                    selectedLabel = startupPageOptions.firstOrNull {
                        it.value == startupPagePreference.preferenceValue()
                    }?.label ?: "聊天",
                    expanded = startupPageExpanded,
                    onExpandedChange = { startupPageExpanded = it },
                    options = startupPageOptions,
                    onSelect = { value ->
                        updateStartupPagePreference(appPageFromPreference(value))
                    },
                )
                BodyText("应用冷启动时默认先打开这个页面；连接成功后仍会按当前流程进入聊天页。")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = uiSurface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, uiBorder),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("缓存")
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = uiSurfaceAlt,
                    border = BorderStroke(1.dp, uiBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Label("清除全部缓存")
                        BodyText("删除所有本地会话缓存，包括旧版本残留。执行后会先清空界面；如果当前在线，会立即从 Bridge 重新拉取。")
                    }
                }
                OutlinedButton(
                    onClick = { clearAllCacheDialogVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("清除全部缓存")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = uiSurface),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, uiBorder),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("关于")
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = uiSurfaceAlt,
                    border = BorderStroke(1.dp, uiBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Label("应用版本")
                        Text(
                            text = appVersionLabel(),
                            color = uiText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }

    if (clearAllCacheDialogVisible) {
        AppCenteredDialog(onDismiss = { clearAllCacheDialogVisible = false }) {
            Text(
                text = "清除全部缓存",
                color = uiText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            BodyText("会删除当前版本和旧版本残留的本地缓存。界面会立即清空；如果当前在线，会随后从 Bridge 重新加载。")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = { clearAllCacheDialogVisible = false }) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        clearAllCacheDialogVisible = false
                        clearAllSessionCaches()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = uiPrimary),
                ) {
                    Text("确认清除")
                }
            }
        }
    }
}
