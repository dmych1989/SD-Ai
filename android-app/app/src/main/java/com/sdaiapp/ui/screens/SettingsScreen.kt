// ui/screens/SettingsScreen.kt
// 设置页 — 已支持中英文切换
package com.sdaiapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdaiapp.ui.i18n.AppLang
import com.sdaiapp.ui.i18n.rememberText
import com.sdaiapp.ui.theme.*
import com.sdaiapp.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenLogViewer: () -> Unit = {},
    onExportLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val t = rememberText(uiState)
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var localInference by remember { mutableStateOf(uiState.useLocalInference) }
    var editingUrl by remember { mutableStateOf(uiState.pcBackendUrl) }
    var showUrlEditor by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    // 导出日志状态
    var isExportingLog by remember { mutableStateOf(false) }
    var exportLogStatus by remember { mutableStateOf<String?>(null) }

    val doExportLog: () -> Unit = {
        if (!isExportingLog) {
            isExportingLog = true
            exportLogStatus = "正在收集日志..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    // 先调用外部回调（如有）—— 用于自定义分享/上传
                    if (onExportLog !== ::DefaultExportLog) {
                        withContext(Dispatchers.Main) { onExportLog() }
                    }
                    // 默认实现：dump logcat 到文件 + 启动系统分享面板
                    val file = com.sdaiapp.utils.CrashLogger.dumpFullLogcat()
                    withContext(Dispatchers.Main) {
                        if (file == null) {
                            exportLogStatus = "❌ 日志导出失败（无权限或无 logcat 缓存）"
                            isExportingLog = false
                            return@withContext
                        }
                        exportLogStatus = "✓ 日志已保存：${file.absolutePath}"
                        try {
                            val intent = com.sdaiapp.utils.CrashLogger.shareLog(file)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            exportLogStatus = "✓ 日志已保存但无法启动分享：${e.message}"
                        }
                        isExportingLog = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        exportLogStatus = "❌ 导出失败：${e.message}"
                        isExportingLog = false
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(t.appName, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        // ========= 推理模式 =========
        SettingsSection(title = t.tabParams) {
            InferenceModeSwitch(
                useLocal = localInference,
                onToggle = { newValue ->
                    localInference = newValue
                    viewModel.toggleInferenceMode()
                },
                t = t
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (localInference) t.localMnn else t.remotePc,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ========= 连接（仅远程 PC 模式显示）=========
        if (!localInference) {
            SettingsSection(title = t.connection) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Computer,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(t.pcAddress, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (uiState.pcBackendUrl.isNotEmpty()) uiState.pcBackendUrl else t.connectionFailed,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                IconButton(onClick = { showUrlEditor = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = t.save, tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (uiState.isConnected) SuccessGreen else ErrorRed)
                )
                Text(
                    text = if (uiState.isConnected) t.connected else t.disconnected,
                    color = if (uiState.isConnected) SuccessGreen else TextSecondary,
                    fontSize = 12.sp
                )
                if (uiState.connectionError != null) {
                    Text("(${uiState.connectionError})", color = ErrorRed, fontSize = 11.sp)
                }
            }

            if (testResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testResult!!.first)
                            SuccessGreen.copy(alpha = 0.15f)
                        else ErrorRed.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (testResult!!.first) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (testResult!!.first) SuccessGreen else ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(testResult!!.second, color = TextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }
        } // end if (!localInference)

        Spacer(modifier = Modifier.height(20.dp))

        // ========= 生成设置 =========
        SettingsSection(title = t.autoSave) {
            SettingsItem(
                icon = Icons.Filled.Save,
                title = t.autoSave,
                subtitle = t.saveHistory,
                onClick = { viewModel.toggleAutoSave() },
                trailing = {
                    Switch(
                        checked = uiState.autoSaveImages,
                        onCheckedChange = { viewModel.toggleAutoSave() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryIndigo,
                            checkedTrackColor = PrimaryIndigo.copy(alpha = 0.5f)
                        )
                    )
                }
            )
            Divider(color = DividerDark, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Filled.History,
                title = t.saveHistory,
                subtitle = t.tabGallery,
                onClick = { viewModel.toggleSaveHistory() },
                trailing = {
                    Switch(
                        checked = uiState.saveHistory,
                        onCheckedChange = { viewModel.toggleSaveHistory() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryIndigo,
                            checkedTrackColor = PrimaryIndigo.copy(alpha = 0.5f)
                        )
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ========= 语言 =========
        SettingsSection(title = t.language) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LangButton(
                    label = t.languageZh,
                    isSelected = uiState.language == AppLang.ZH,
                    onClick = {
                        if (uiState.language != AppLang.ZH) viewModel.toggleLanguage()
                    },
                    modifier = Modifier.weight(1f)
                )
                LangButton(
                    label = t.languageEn,
                    isSelected = uiState.language == AppLang.EN,
                    onClick = {
                        if (uiState.language != AppLang.EN) viewModel.toggleLanguage()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ========= 日志 =========
        SettingsSection(title = "日志") {
            SettingsItem(
                icon = Icons.Filled.Terminal,
                title = "查看运行日志",
                subtitle = "实时查看 APP 运行过程中的 Debug 信息",
                onClick = onOpenLogViewer
            )
            Divider(color = DividerDark, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Filled.Delete,
                title = "清空日志",
                subtitle = "清除内存中的日志缓冲区",
                onClick = { com.sdaiapp.utils.AppLog.clear() }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ========= 关于 =========
        SettingsSection(title = t.appName) {
            SettingsItem(
                icon = Icons.Filled.Info,
                title = t.appVersion,
                subtitle = "1.0.0 (${uiState.buildTime})",
                onClick = { }
            )
            Divider(color = DividerDark, modifier = Modifier.padding(start = 52.dp))
            SettingsItem(
                icon = Icons.Filled.Code,
                title = t.exportLogs,
                subtitle = if (isExportingLog) "导出中..." else exportLogStatus ?: "导出 / 分享日志文件",
                onClick = { doExportLog() }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = { viewModel.resetSettings() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(ErrorRed.copy(alpha = 0.5f))
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Text(t.cancel, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    // ========= 地址编辑对话框 =========
    if (showUrlEditor) {
        AlertDialog(
            onDismissRequest = { showUrlEditor = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text(t.pcAddress, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t.connection, color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = editingUrl,
                        onValueChange = { editingUrl = it },
                        label = { Text(t.portLabel) },
                        placeholder = { Text("http://192.168.1.100:8080") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Button(
                        onClick = {
                            isTesting = true
                            testResult = null
                            viewModel.testConnection(editingUrl) { success, msg ->
                                isTesting = false
                                testResult = success to msg
                            }
                        },
                        enabled = editingUrl.isNotBlank() && !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isTesting) t.connecting else t.testConnection, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateServerUrl(editingUrl)
                        viewModel.connectToServer(editingUrl)
                        showUrlEditor = false
                        testResult = null
                    },
                    enabled = editingUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                ) { Text(t.save) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlEditor = false }) { Text(t.cancel) }
            }
        )
    }
}

@Composable
private fun LangButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) PrimaryIndigo.copy(alpha = 0.15f) else SurfaceDark
    val borderColor = if (isSelected) PrimaryIndigo else BorderDark
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(containerColor = bg),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier.height(44.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (isSelected) PrimaryIndigo else TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun InferenceModeSwitch(
    useLocal: Boolean,
    onToggle: (Boolean) -> Unit,
    t: com.sdaiapp.ui.i18n.AppText
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceLight)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (!useLocal) PrimaryIndigo else Color.Transparent)
                .clickable { if (useLocal) onToggle(false) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Computer,
                    contentDescription = null,
                    tint = if (!useLocal) Color.White else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    t.remotePc,
                    color = if (!useLocal) Color.White else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (!useLocal) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (useLocal) PrimaryIndigo else Color.Transparent)
                .clickable { if (!useLocal) onToggle(true) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = if (useLocal) Color.White else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    t.localMnn,
                    color = if (useLocal) Color.White else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (useLocal) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(BackgroundDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextTertiary, fontSize = 13.sp)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * 默认导出日志实现 —— 由 SettingsScreen 默认调用
 * 当外部 onExportLog 引用 == 这个函数时，说明是默认实现；否则走外部自定义
 */
internal fun DefaultExportLog() {
    // 占位：实际逻辑在 SettingsScreen 的 doExportLog lambda 里
}